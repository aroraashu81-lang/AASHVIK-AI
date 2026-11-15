package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.AashvikAiService
import com.example.ai.configureAppCheck
import com.example.auth.authStateFlow
import com.example.data.model.AppPolicies
import com.example.data.repository.ChatRepository
import com.example.ui.admin.AdminPanelScreen
import com.example.ui.admin.AdminViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.MainChatScreen
import com.example.ui.policy.BannedAccountScreen
import com.example.ui.policy.TermsAndViolationsScreen
import com.example.ui.theme.AashvikTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AuthenticatedRoute {
    CHAT,
    TERMS,
    ADMIN
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        // STEP 2: Enable Edge-to-Edge display
        enableEdgeToEdge()

        // STEP 3: Render Auth-gated Compose Navigation
        setContent {
            AppNavigation()
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var showPreAuthTerms by rememberSaveable { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        SyncSystemBarsWithTheme(themeMode = "system")
        AashvikTheme(themeMode = "system") {
            if (showPreAuthTerms) {
                TermsAndViolationsScreen(
                    isAuthenticated = false,
                    userProfile = null,
                    ownReports = emptyList(),
                    isMandatoryAcceptance = false,
                    onBack = { showPreAuthTerms = false }
                )
            } else {
                AuthScreen(
                    onAuthSuccess = { showPreAuthTerms = false },
                    onOpenTerms = { showPreAuthTerms = true }
                )
            }
        }
    } else {
        val uid = user.uid
        val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Friend"
        val isVerifiedAdmin = AppPolicies.isVerifiedAdmin(user)
        key(uid) {
            AuthenticatedChatHost(
                currentUserId = uid,
                userDisplayName = displayName,
                isVerifiedAdmin = isVerifiedAdmin
            )
        }
    }
}

@Composable
private fun AuthenticatedChatHost(
    currentUserId: String,
    userDisplayName: String,
    isVerifiedAdmin: Boolean
) {
    val viewModel: ChatViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChatViewModel(
                    repository = ChatRepository(db = db, auth = Firebase.auth),
                    aiService = AashvikAiService(),
                    currentUserId = currentUserId,
                    displayName = userDisplayName,
                    isVerifiedAdmin = isVerifiedAdmin
                )
            }
        }
    )

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val ownReports by viewModel.ownReports.collectAsStateWithLifecycle()
    val themeMode = userProfile?.themeMode ?: "system"
    var currentRoute by rememberSaveable(currentUserId) { mutableStateOf(AuthenticatedRoute.CHAT) }

    SyncSystemBarsWithTheme(themeMode = themeMode)

    AashvikTheme(themeMode = themeMode) {
        val profile = userProfile
        val isBanned = !isVerifiedAdmin && profile?.moderationStatus == "banned"
        val mustAcceptTerms = profile != null && !profile.termsAccepted && !isBanned

        when {
            isBanned -> {
                val bannedProfile = profile!!
                if (currentRoute == AuthenticatedRoute.TERMS) {
                    TermsAndViolationsScreen(
                        isAuthenticated = true,
                        userProfile = bannedProfile,
                        ownReports = ownReports,
                        isMandatoryAcceptance = false,
                        onBack = { currentRoute = AuthenticatedRoute.CHAT },
                        onAcceptTerms = null,
                        onSubmitReport = null
                    )
                } else {
                    BannedAccountScreen(
                        userProfile = bannedProfile,
                        onViewPolicy = { currentRoute = AuthenticatedRoute.TERMS }
                    )
                }
            }
            mustAcceptTerms -> {
                TermsAndViolationsScreen(
                    isAuthenticated = true,
                    userProfile = profile,
                    ownReports = ownReports,
                    isMandatoryAcceptance = true,
                    onBack = {},
                    onAcceptTerms = { viewModel.acceptTerms() },
                    onSubmitReport = { category, description ->
                        viewModel.submitViolationReport(category, description)
                    }
                )
            }
            currentRoute == AuthenticatedRoute.TERMS -> {
                TermsAndViolationsScreen(
                    isAuthenticated = true,
                    userProfile = profile,
                    ownReports = ownReports,
                    isMandatoryAcceptance = false,
                    onBack = { currentRoute = AuthenticatedRoute.CHAT },
                    onAcceptTerms = if (profile?.termsAccepted != true) {
                        { viewModel.acceptTerms() }
                    } else {
                        null
                    },
                    onSubmitReport = { category, description ->
                        viewModel.submitViolationReport(category, description)
                    }
                )
            }
            currentRoute == AuthenticatedRoute.ADMIN && isVerifiedAdmin -> {
                val adminViewModel: AdminViewModel = viewModel(
                    key = "admin_$currentUserId",
                    factory = viewModelFactory {
                        initializer {
                            val app = checkNotNull(this[APPLICATION_KEY]) {
                                "APPLICATION_KEY missing from CreationExtras"
                            }
                            val databaseId = app.getString(R.string.firestore_database_id)
                            val db = FirebaseFirestore.getInstance(databaseId)
                            AdminViewModel(
                                repository = ChatRepository(db = db, auth = Firebase.auth),
                                auth = Firebase.auth
                            )
                        }
                    }
                )
                AdminPanelScreen(
                    viewModel = adminViewModel,
                    onBack = { currentRoute = AuthenticatedRoute.CHAT }
                )
            }
            else -> {
                MainChatScreen(
                    viewModel = viewModel,
                    userDisplayName = userDisplayName,
                    isAdmin = isVerifiedAdmin,
                    onOpenTerms = { currentRoute = AuthenticatedRoute.TERMS },
                    onOpenAdmin = {
                        if (isVerifiedAdmin) {
                            currentRoute = AuthenticatedRoute.ADMIN
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncSystemBarsWithTheme(themeMode: String) {
    val activity = LocalActivity.current as? ComponentActivity
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }

    DisposableEffect(activity, useDark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (useDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            },
            navigationBarStyle = if (useDark) {
                SystemBarStyle.dark(Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            }
        )
        onDispose {}
    }
}
