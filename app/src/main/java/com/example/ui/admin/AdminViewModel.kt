package com.example.ui.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppPolicies
import com.example.data.model.SafetyPolicy
import com.example.data.model.SafetyViolation
import com.example.data.model.UserProfile
import com.example.data.model.ViolationReport
import com.example.data.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AdminViewModel"

class AdminViewModel(
    private val repository: ChatRepository,
    auth: FirebaseAuth
) : ViewModel() {

    val isAuthorizedAdmin: Boolean = AppPolicies.isVerifiedAdmin(auth.currentUser)

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _isSavingPolicy = MutableStateFlow(false)
    val isSavingPolicy: StateFlow<Boolean> = _isSavingPolicy.asStateFlow()

    val safetyPolicy: StateFlow<SafetyPolicy> = if (isAuthorizedAdmin) {
        repository.observeGlobalSafetyPolicy()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = SafetyPolicy()
            )
    } else {
        MutableStateFlow(SafetyPolicy()).asStateFlow()
    }

    val allUsers: StateFlow<List<UserProfile>> = if (isAuthorizedAdmin) {
        repository.observeAllUsersForAdmin()
            .catch { error ->
                if (error is CancellationException && error !is FirebaseFirestoreException) throw error
                Log.w(TAG, "Admin users stream error", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )
    } else {
        flowOf(emptyList<UserProfile>()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    val safetyViolations: StateFlow<List<SafetyViolation>> = if (isAuthorizedAdmin) {
        repository.observeAllSafetyViolationsForAdmin()
            .catch { error ->
                if (error is CancellationException && error !is FirebaseFirestoreException) throw error
                Log.w(TAG, "Admin violations stream error", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )
    } else {
        flowOf(emptyList<SafetyViolation>()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    val violationReports: StateFlow<List<ViolationReport>> = if (isAuthorizedAdmin) {
        repository.observeAllViolationReportsForAdmin()
            .catch { error ->
                if (error is CancellationException && error !is FirebaseFirestoreException) throw error
                Log.w(TAG, "Admin reports stream error", error)
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = emptyList()
            )
    } else {
        flowOf(emptyList<ViolationReport>()).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    fun dismissNotice() {
        _notice.value = null
    }

    fun saveSafetyPolicy(
        strictModeEnabled: Boolean,
        autoFlagViolations: Boolean,
        blockedKeywords: List<String>,
        customSafetyDirectives: String
    ) {
        if (!isAuthorizedAdmin) return
        _isSavingPolicy.value = true
        viewModelScope.launch {
            val res = repository.updateGlobalSafetyPolicy(
                strictModeEnabled = strictModeEnabled,
                autoFlagViolations = autoFlagViolations,
                blockedKeywords = blockedKeywords,
                customSafetyDirectives = customSafetyDirectives
            )
            _isSavingPolicy.value = false
            _notice.value = res.fold(
                onSuccess = { "AI safety training policy updated live." },
                onFailure = { it.localizedMessage ?: "Failed to update safety policy." }
            )
        }
    }

    fun warnUser(user: UserProfile) {
        if (!isAuthorizedAdmin) return
        val nextWarnings = (user.warningCount + 1L).coerceAtMost(100L)
        val nextStatus = if (nextWarnings >= 3L) "suspended" else "warned"
        viewModelScope.launch {
            val res = repository.moderateUser(user.userId, nextStatus, nextWarnings)
            _notice.value = res.fold(
                onSuccess = {
                    if (nextStatus == "suspended") {
                        "${user.displayName} reached 3 warnings and is now suspended."
                    } else {
                        "Warning #$nextWarnings issued to ${user.displayName}."
                    }
                },
                onFailure = { it.localizedMessage ?: "Failed to warn user." }
            )
        }
    }

    fun banUserPermanently(
        userId: String,
        displayName: String,
        currentWarnings: Long,
        reason: String
    ) {
        if (!isAuthorizedAdmin) return
        viewModelScope.launch {
            val res = repository.moderateUser(
                targetUserId = userId,
                moderationStatus = "banned",
                warningCount = (currentWarnings + 1L).coerceAtMost(100L),
                banReason = reason
            )
            _notice.value = res.fold(
                onSuccess = { "$displayName permanently banned from Aashvik AI." },
                onFailure = { it.localizedMessage ?: "Failed to ban user." }
            )
        }
    }

    fun suspendUser(userId: String, displayName: String, currentWarnings: Long) {
        if (!isAuthorizedAdmin) return
        viewModelScope.launch {
            val res = repository.moderateUser(userId, "suspended", currentWarnings, null)
            _notice.value = res.fold(
                onSuccess = { "$displayName suspended from Aashvik AI." },
                onFailure = { it.localizedMessage ?: "Failed to suspend user." }
            )
        }
    }

    fun restoreUser(userId: String, displayName: String) {
        if (!isAuthorizedAdmin) return
        viewModelScope.launch {
            val res = repository.moderateUser(userId, "active", 0L, null)
            _notice.value = res.fold(
                onSuccess = { "$displayName unbanned and restored to active standing." },
                onFailure = { it.localizedMessage ?: "Failed to restore user." }
            )
        }
    }

    fun updateSafetyViolationStatus(violationId: String, status: String) {
        if (!isAuthorizedAdmin) return
        viewModelScope.launch {
            val res = repository.updateSafetyViolationStatus(violationId, status)
            _notice.value = res.fold(
                onSuccess = { "Violation marked as $status." },
                onFailure = { it.localizedMessage ?: "Failed to update violation." }
            )
        }
    }

    fun updateViolationReportStatus(reportId: String, status: String, adminNotes: String?) {
        if (!isAuthorizedAdmin) return
        viewModelScope.launch {
            val res = repository.updateViolationReportStatus(reportId, status, adminNotes)
            _notice.value = res.fold(
                onSuccess = { "Report marked as $status." },
                onFailure = { it.localizedMessage ?: "Failed to update report." }
            )
        }
    }
}
