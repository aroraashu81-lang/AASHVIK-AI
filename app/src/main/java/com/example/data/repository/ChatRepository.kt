package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppPolicies
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.SafetyPolicy
import com.example.data.model.SafetyViolation
import com.example.data.model.UserProfile
import com.example.data.model.ViolationReport
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun requireVerifiedAdminEmail(): String {
        val user = auth.currentUser
        if (!AppPolicies.isVerifiedAdmin(user)) {
            throw SecurityException("Admin access is restricted to verified administrator account.")
        }
        return user?.email ?: AppPolicies.ADMIN_EMAIL
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String): Result<UserProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val currentEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val docRef = db.collection("users").document(uid)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val profile = existing.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(id = uid, userId = uid, displayName = displayName, email = currentEmail)
                Result.success(profile)
            } else {
                val cleanName = displayName.trim().ifBlank { "Friend" }.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to currentEmail,
                    "themeMode" to "system",
                    "preferredModel" to "gemini-3.8-flash",
                    "ttsEnabled" to false,
                    "termsAccepted" to false,
                    "termsAcceptedVersion" to "",
                    "moderationStatus" to "active",
                    "warningCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
                Result.success(
                    UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = cleanName,
                        email = currentEmail
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updatePreferences(
        themeMode: String? = null,
        preferredModel: String? = null,
        ttsEnabled: Boolean? = null
    ): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "updatedAt" to FieldValue.serverTimestamp()
        )
        themeMode?.let { updates["themeMode"] = it }
        preferredModel?.let { updates["preferredModel"] = it }
        ttsEnabled?.let { updates["ttsEnabled"] = it }

        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val fallbackName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Friend"
                val currentEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to fallbackName.take(100),
                    "email" to currentEmail,
                    "themeMode" to (themeMode ?: "system"),
                    "preferredModel" to (preferredModel ?: "gemini-3.8-flash"),
                    "ttsEnabled" to (ttsEnabled ?: false),
                    "termsAccepted" to false,
                    "termsAcceptedVersion" to "",
                    "moderationStatus" to "active",
                    "warningCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            } else {
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun acceptTerms(version: String = AppPolicies.CURRENT_TERMS_VERSION): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid)
        val safeVersion = version.trim().ifBlank { AppPolicies.CURRENT_TERMS_VERSION }.take(32)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val fallbackName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Friend"
                val currentEmail = auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to fallbackName.take(100),
                    "email" to currentEmail,
                    "themeMode" to "system",
                    "preferredModel" to "gemini-3.8-flash",
                    "ttsEnabled" to false,
                    "termsAccepted" to true,
                    "termsAcceptedVersion" to safeVersion,
                    "moderationStatus" to "active",
                    "warningCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            } else {
                docRef.update(
                    mapOf(
                        "termsAccepted" to true,
                        "termsAcceptedVersion" to safeVersion,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun triggerAutomaticPermanentBan(reason: String): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        if (AppPolicies.isVerifiedAdmin(user)) {
            return Result.success(Unit)
        }
        val uid = user.uid
        val docRef = db.collection("users").document(uid)
        val safeReason = reason.trim().ifBlank { "Policy & Safety Violation" }.take(300)
        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val fallbackName = user.displayName?.takeIf { it.isNotBlank() } ?: "User"
                val currentEmail = user.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to fallbackName.take(100),
                    "email" to currentEmail,
                    "themeMode" to "system",
                    "preferredModel" to "gemini-3.8-flash",
                    "ttsEnabled" to false,
                    "termsAccepted" to false,
                    "termsAcceptedVersion" to "",
                    "moderationStatus" to "active",
                    "warningCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(createPayload).await()
            }
            val currentWarnings = snap.getLong("warningCount") ?: 0L
            val nextWarnings = (currentWarnings + 1L).coerceAtMost(100L)
            docRef.update(
                mapOf(
                    "moderationStatus" to "banned",
                    "banReason" to safeReason,
                    "warningCount" to nextWarnings,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeConversations(userId: String): Flow<List<Conversation>> = flow {
        val colRef = db.collection("users").document(userId).collection("conversations")
        emitAll(
            colRef.orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun getConversationsOnce(): Result<List<Conversation>> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val colRef = db.collection("users").document(uid).collection("conversations")
        return try {
            val snap = colRef.orderBy("updatedAt", Query.Direction.DESCENDING).get().await()
            Result.success(
                snap.toObjects(
                    Conversation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeMessages(userId: String, conversationId: String): Flow<List<ChatMessage>> = flow {
        val colRef = db.collection("users")
            .document(userId)
            .collection("conversations")
            .document(conversationId)
            .collection("messages")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ChatMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun createConversationWithFirstMessage(
        title: String,
        messageText: String,
        attachmentName: String? = null,
        attachmentType: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val convRef = db.collection("users").document(uid).collection("conversations").document()
        val msgRef = convRef.collection("messages").document()

        val safeTitle = title.trim().ifBlank { "New Conversation" }.take(160)
        val safeText = messageText.trim().ifBlank { "Shared an attachment" }.take(60000)
        val preview = safeText.replace("\n", " ").take(240)

        val convPayload = mapOf(
            "userId" to uid,
            "title" to safeTitle,
            "lastMessagePreview" to preview,
            "messageCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val rawMsgPayload = mapOf(
            "userId" to uid,
            "conversationId" to convRef.id,
            "role" to "user",
            "text" to safeText,
            "attachmentName" to attachmentName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            "attachmentType" to attachmentType?.takeIf { it == "image" || it == "data" },
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            val batch = db.batch()
            batch.set(convRef, convPayload)
            batch.set(msgRef, rawMsgPayload)
            batch.commit().await()
            Result.success(convRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun addMessage(
        conversationId: String,
        role: String,
        text: String,
        newMessageCount: Long,
        attachmentName: String? = null,
        attachmentType: String? = null
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val convRef = db.collection("users")
            .document(uid)
            .collection("conversations")
            .document(conversationId)
        val msgRef = convRef.collection("messages").document()

        val safeText = text.trim().ifBlank { "Shared an attachment" }.take(60000)
        val preview = safeText.replace("\n", " ").take(240)

        val msgPayload = mapOf(
            "userId" to uid,
            "conversationId" to conversationId,
            "role" to role,
            "text" to safeText,
            "attachmentName" to attachmentName?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
            "attachmentType" to attachmentType?.takeIf { it == "image" || it == "data" },
            "createdAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val convUpdates = mapOf(
            "lastMessagePreview" to preview,
            "messageCount" to newMessageCount,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        return try {
            val batch = db.batch()
            batch.set(msgRef, msgPayload)
            batch.update(convRef, convUpdates)
            batch.commit().await()
            Result.success(msgRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun renameConversation(conversationId: String, newTitle: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val convRef = db.collection("users")
            .document(uid)
            .collection("conversations")
            .document(conversationId)
        val safeTitle = newTitle.trim().ifBlank { "Untitled Chat" }.take(160)
        return try {
            convRef.update(
                mapOf(
                    "title" to safeTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteConversation(conversationId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val convRef = db.collection("users")
            .document(uid)
            .collection("conversations")
            .document(conversationId)
        val messagesRef = convRef.collection("messages")
        return try {
            val msgDocs = messagesRef.get().await().documents
            msgDocs.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc -> batch.delete(doc.reference) }
                batch.commit().await()
            }
            convRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun clearAllConversations(): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val convsRef = db.collection("users").document(uid).collection("conversations")
        return try {
            val convDocs = convsRef.get().await().documents
            for (convDoc in convDocs) {
                val msgDocs = convDoc.reference.collection("messages").get().await().documents
                msgDocs.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { doc -> batch.delete(doc.reference) }
                    batch.commit().await()
                }
                convDoc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, convsRef.path, auth)
            Result.failure(e)
        }
    }

    // ---- Safety Policy, Violation Logging & Reporting ----

    fun observeGlobalSafetyPolicy(): Flow<SafetyPolicy> = flow {
        val docRef = db.collection("safety_policies").document("global")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SafetyPolicy::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: SafetyPolicy()
                    } else {
                        SafetyPolicy()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(SafetyPolicy())
                }
        )
    }

    suspend fun logSafetyViolation(
        category: String,
        reason: String,
        promptSnippet: String,
        severity: String = "medium"
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val user = auth.currentUser
        val safeName = user?.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(100) ?: "User"
        val safeEmail = user?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val safeCategory = if (category in setOf("harmful_content", "jailbreak", "blocked_keyword", "model_safety_filter", "other")) {
            category
        } else {
            "other"
        }
        val safeSeverity = if (severity in setOf("low", "medium", "high")) severity else "medium"
        val safeReason = reason.trim().ifBlank { "Safety guardrail triggered" }.take(500)
        val safeSnippet = promptSnippet.trim().take(300)

        val docRef = db.collection("safety_violations").document()
        val payload = mapOf(
            "userId" to uid,
            "userDisplayName" to safeName,
            "userEmail" to safeEmail,
            "category" to safeCategory,
            "reason" to safeReason,
            "promptSnippet" to safeSnippet,
            "severity" to safeSeverity,
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun submitViolationReport(
        category: String,
        description: String
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val trimmedDesc = description.trim()
        if (trimmedDesc.length < 10) {
            return Result.failure(IllegalArgumentException("Please provide at least 10 characters describing the issue."))
        }
        val user = auth.currentUser
        val safeName = user?.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(100) ?: "User"
        val safeEmail = user?.email?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val safeCategory = if (category in setOf("harmful_output", "harassment_abuse", "privacy_security", "misinformation", "policy_bug", "other")) {
            category
        } else {
            "other"
        }

        val docRef = db.collection("violation_reports").document()
        val payload = mapOf(
            "userId" to uid,
            "reporterName" to safeName,
            "reporterEmail" to safeEmail,
            "category" to safeCategory,
            "description" to trimmedDesc.take(2000),
            "status" to "open",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeOwnViolationReports(userId: String): Flow<List<ViolationReport>> = flow {
        val colRef = db.collection("violation_reports")
        emitAll(
            colRef.whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ViolationReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    // ---- Verified Admin Operations (aroraashu81@gmail.com only) ----

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val colRef = db.collection("users")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeAllSafetyViolationsForAdmin(): Flow<List<SafetyViolation>> = flow {
        val colRef = db.collection("safety_violations")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SafetyViolation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeAllViolationReportsForAdmin(): Flow<List<ViolationReport>> = flow {
        val colRef = db.collection("violation_reports")
        emitAll(
            colRef.orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ViolationReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun updateGlobalSafetyPolicy(
        strictModeEnabled: Boolean,
        autoFlagViolations: Boolean,
        blockedKeywords: List<String>,
        customSafetyDirectives: String
    ): Result<Unit> {
        val adminEmail = try {
            requireVerifiedAdminEmail()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("safety_policies").document("global")
        val cleanedKeywords = blockedKeywords
            .map { it.trim().lowercase().take(80) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(50)
        val payload = mapOf(
            "strictModeEnabled" to strictModeEnabled,
            "autoFlagViolations" to autoFlagViolations,
            "blockedKeywords" to cleanedKeywords,
            "customSafetyDirectives" to customSafetyDirectives.trim().take(2000),
            "updatedBy" to adminEmail.take(200),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun moderateUser(
        targetUserId: String,
        moderationStatus: String,
        warningCount: Long,
        banReason: String? = null
    ): Result<Unit> {
        try {
            requireVerifiedAdminEmail()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeStatus = if (moderationStatus in setOf("active", "warned", "suspended", "banned")) {
            moderationStatus
        } else {
            "active"
        }
        val safeWarnings = warningCount.coerceIn(0L, 100L)
        val docRef = db.collection("users").document(targetUserId)
        val updates = mutableMapOf<String, Any?>(
            "moderationStatus" to safeStatus,
            "warningCount" to safeWarnings,
            "banReason" to if (safeStatus == "banned") {
                banReason?.trim()?.ifBlank { "Permanently banned by administrator." }?.take(300)
                    ?: "Permanently banned by administrator."
            } else {
                null
            },
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateSafetyViolationStatus(
        violationId: String,
        status: String
    ): Result<Unit> {
        try {
            requireVerifiedAdminEmail()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeStatus = if (status in setOf("open", "reviewed", "dismissed")) status else "reviewed"
        val docRef = db.collection("safety_violations").document(violationId)
        return try {
            docRef.update(
                mapOf(
                    "status" to safeStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun updateViolationReportStatus(
        reportId: String,
        status: String,
        adminNotes: String?
    ): Result<Unit> {
        try {
            requireVerifiedAdminEmail()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val safeStatus = if (status in setOf("open", "resolved", "dismissed")) status else "resolved"
        val docRef = db.collection("violation_reports").document(reportId)
        val updates = mutableMapOf<String, Any>(
            "status" to safeStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        adminNotes?.trim()?.take(1000)?.let { updates["adminNotes"] = it }
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }
}
