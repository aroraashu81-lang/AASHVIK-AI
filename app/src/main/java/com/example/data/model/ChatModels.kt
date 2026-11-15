package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

object AppPolicies {
    const val ADMIN_EMAIL = "aroraashu81@gmail.com"
    const val CURRENT_TERMS_VERSION = "1.0"

    fun isVerifiedAdmin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        return user.email.equals(ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified
    }

    fun isVerifiedAdmin(email: String?, isEmailVerified: Boolean): Boolean {
        return email.equals(ADMIN_EMAIL, ignoreCase = true) && isEmailVerified
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Friend",
    val email: String? = null,
    val themeMode: String = "system",
    val preferredModel: String = "gemini-3.8-flash",
    val ttsEnabled: Boolean = false,
    val termsAccepted: Boolean = false,
    val termsAcceptedVersion: String = "",
    val moderationStatus: String = "active",
    val banReason: String? = null,
    val warningCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val lastMessagePreview: String = "",
    val messageCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val conversationId: String = "",
    val role: String = "user",
    val text: String = "",
    val attachmentName: String? = null,
    val attachmentType: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SafetyPolicy(
    @DocumentId val id: String = "global",
    val strictModeEnabled: Boolean = true,
    val autoFlagViolations: Boolean = true,
    val blockedKeywords: List<String> = emptyList(),
    val customSafetyDirectives: String = "",
    val updatedBy: String = AppPolicies.ADMIN_EMAIL,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SafetyViolation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "User",
    val userEmail: String? = null,
    val category: String = "harmful_content",
    val reason: String = "",
    val promptSnippet: String = "",
    val severity: String = "medium",
    val status: String = "open",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ViolationReport(
    @DocumentId val id: String = "",
    val userId: String = "",
    val reporterName: String = "User",
    val reporterEmail: String? = null,
    val category: String = "harmful_output",
    val description: String = "",
    val status: String = "open",
    val adminNotes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
