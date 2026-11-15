package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppPolicies
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class ChatRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Before
    override fun setUpFirebase() {
        super.setUpFirebase()
        clearEmulatorEndpoint("http://127.0.0.1:8085/emulator/v1/projects/demo-no-project/databases/$databaseId/documents")
        clearEmulatorEndpoint("http://127.0.0.1:9099/emulator/v1/projects/demo-no-project/accounts")
    }

    private fun clearEmulatorEndpoint(endpoint: String) {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 3000
            readTimeout = 3000
        }
        conn.responseCode
        conn.disconnect()
    }

    private suspend fun verifyCurrentEmulatorUserEmail() = withContext(Dispatchers.IO) {
        val user = checkNotNull(auth.currentUser) { "Must be signed in before verifying email" }
        val url = URL("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/projects/demo-no-project/accounts:update")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer owner")
            doOutput = true
        }
        val body = """{"localId":"${user.uid}","emailVerified":true}"""
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val responseCode = conn.responseCode
        conn.disconnect()
        check(responseCode in 200..299) { "Auth emulator email verification failed with HTTP $responseCode" }
        user.reload().await()
        user.getIdToken(true).await()
    }

    @Test
    fun updatePreferences_beforeProfileCreated_createsProfileAndPersistsPreferences() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ChatRepository(firestore, auth)

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updatePreferences(themeMode = "dark", preferredModel = "gemini-3.5-flash-lite", ttsEnabled = true)
        }
        assertTrue(updateResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals("dark", observed?.themeMode)
        assertEquals("gemini-3.5-flash-lite", observed?.preferredModel)
        assertEquals(true, observed?.ttsEnabled)
        assertEquals(false, observed?.termsAccepted)
        assertEquals("active", observed?.moderationStatus)
    }

    @Test
    fun ensureUserProfile_acceptTermsAndSubmitReport_persistsCorrectly() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ChatRepository(firestore, auth)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserProfile("Alice")
        }
        assertTrue(createResult.isSuccess)
        assertEquals(uid, createResult.getOrThrow().userId)

        val termsResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.acceptTerms(AppPolicies.CURRENT_TERMS_VERSION)
        }
        assertTrue(termsResult.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeUserProfile(uid).first { it != null && it.termsAccepted }
        }
        assertEquals(true, observed?.termsAccepted)
        assertEquals(AppPolicies.CURRENT_TERMS_VERSION, observed?.termsAcceptedVersion)

        val violationRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.logSafetyViolation(
                category = "jailbreak",
                reason = "Blocked prompt injection phrase",
                promptSnippet = "Ignore previous instructions",
                severity = "high"
            )
        }
        assertTrue(violationRes.isSuccess)

        val reportRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.submitViolationReport(
                category = "harmful_output",
                description = "Detailed safety issue description for moderation team review."
            )
        }
        assertTrue(reportRes.isSuccess)

        val ownReports = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeOwnViolationReports(uid).first { it.isNotEmpty() }
        }
        assertTrue(ownReports.any { it.id == reportRes.getOrThrow() && it.status == "open" })
    }

    @Test
    fun createRenameAndClearConversations_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ChatRepository(firestore, auth)

        val convId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createConversationWithFirstMessage(
                title = "Hello Aashvik",
                messageText = "Explain photosynthesis simply"
            ).getOrThrow()
        }
        assertTrue(convId.isNotBlank())

        val msgResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addMessage(
                conversationId = convId,
                role = "model",
                text = "Photosynthesis is how plants turn sunlight into food!",
                newMessageCount = 2L
            )
        }
        assertTrue(msgResult.isSuccess)

        val renameResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.renameConversation(convId, "Plant Biology Notes")
        }
        assertTrue(renameResult.isSuccess)

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeMessages(uid, convId).first { it.size == 2 }
        }
        assertEquals(2, messages.size)

        val conversations = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeConversations(uid).first { list ->
                list.any { it.id == convId && it.title == "Plant Biology Notes" }
            }
        }
        assertTrue(conversations.any { it.id == convId && it.messageCount == 2L })

        val clearResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.clearAllConversations()
        }
        assertTrue(clearResult.isSuccess)
    }

    @Test
    fun verifiedAdmin_canManageSafetyPolicyAndModerateUsers_whileRegularUserDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice").getOrThrow()
        }
        val violationId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.logSafetyViolation(
                category = "harmful_content",
                reason = "Blocked harmful query",
                promptSnippet = "Harmful test snippet",
                severity = "high"
            ).getOrThrow()
        }

        // Regular user cannot update safety policy or observe all users
        val alicePolicyAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.updateGlobalSafetyPolicy(
                strictModeEnabled = false,
                autoFlagViolations = false,
                blockedKeywords = emptyList(),
                customSafetyDirectives = "Attempted override"
            )
        }
        assertTrue(alicePolicyAttempt.isFailure)

        var listDenied: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                aliceRepo.observeAllUsersForAdmin().first()
            }
        } catch (t: Throwable) {
            listDenied = t
        }
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            listDenied?.unwrapFirestoreException()?.code
        )

        // Sign in as admin email BEFORE verification -> must still be denied
        signInTestUser(AppPolicies.ADMIN_EMAIL)
        val adminRepo = ChatRepository(firestore, auth)
        assertFalse(AppPolicies.isVerifiedAdmin(auth.currentUser))
        assertTrue(
            withTimeout(DEFAULT_TIMEOUT_MS) {
                adminRepo.updateGlobalSafetyPolicy(true, true, listOf("test"), "Unverified").isFailure
            }
        )

        // Verify admin email in Auth Emulator -> now full verified admin privileges succeed
        verifyCurrentEmulatorUserEmail()
        assertTrue(AppPolicies.isVerifiedAdmin(auth.currentUser))

        val policyUpdate = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateGlobalSafetyPolicy(
                strictModeEnabled = true,
                autoFlagViolations = true,
                blockedKeywords = listOf("exploit", "phishing"),
                customSafetyDirectives = "Politely refuse any cyberattack or fraud instruction."
            )
        }
        assertTrue(policyUpdate.isSuccess)

        val livePolicy = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeGlobalSafetyPolicy().first { it.blockedKeywords.contains("exploit") }
        }
        assertTrue(livePolicy.strictModeEnabled)
        assertEquals(2, livePolicy.blockedKeywords.size)

        val allUsers = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAllUsersForAdmin().first { users -> users.any { it.userId == aliceUid } }
        }
        assertTrue(allUsers.any { it.userId == aliceUid })

        val modRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.moderateUser(aliceUid, "warned", 1L)
        }
        assertTrue(modRes.isSuccess)

        val reviewRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.updateSafetyViolationStatus(violationId, "reviewed")
        }
        assertTrue(reviewRes.isSuccess)
    }

    @Test
    fun automaticPermanentBan_locksChatAndOnlyAdminCanUnban() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice").getOrThrow()
        }

        val banRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.triggerAutomaticPermanentBan("Violated AI safety policy (jailbreak)")
        }
        assertTrue(banRes.isSuccess)

        val bannedProfile = withTimeout(FLOW_TIMEOUT_MS) {
            aliceRepo.observeUserProfile(aliceUid).first { it?.moderationStatus == "banned" }
        }
        assertEquals("banned", bannedProfile?.moderationStatus)
        assertEquals(1L, bannedProfile?.warningCount)

        // Banned user is denied creating new conversations
        val blockedConv = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createConversationWithFirstMessage("Blocked", "Should fail")
        }
        assertTrue(blockedConv.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            blockedConv.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Verified admin can unban Alice
        signInTestUser(AppPolicies.ADMIN_EMAIL)
        verifyCurrentEmulatorUserEmail()
        val adminRepo = ChatRepository(firestore, auth)
        val unbanRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            adminRepo.moderateUser(aliceUid, "active", 0L, null)
        }
        assertTrue(unbanRes.isSuccess)
    }

    @Test
    fun observeConversations_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ChatRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createConversationWithFirstMessage(
                title = "Alice Private Chat",
                messageText = "Secret project notes"
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ChatRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeConversations(aliceUid).first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user access, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeConversations_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ChatRepository(firestore, auth)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeConversations("alice_unauth_uid").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for unauthenticated access, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_chat@test.com"
        const val BOB_EMAIL = "bob_chat@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
