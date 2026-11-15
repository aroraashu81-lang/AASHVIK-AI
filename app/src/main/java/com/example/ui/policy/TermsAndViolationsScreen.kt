package com.example.ui.policy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AppPolicies
import com.example.data.model.UserProfile
import com.example.data.model.ViolationReport
import kotlinx.coroutines.launch

private val ReportCategories = listOf(
    "harmful_output" to "Harmful Output",
    "harassment_abuse" to "Harassment / Abuse",
    "privacy_security" to "Privacy / Security",
    "misinformation" to "Misinformation",
    "policy_bug" to "Safety Filter Issue",
    "other" to "Other"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TermsAndViolationsScreen(
    isAuthenticated: Boolean,
    userProfile: UserProfile?,
    ownReports: List<ViolationReport>,
    isMandatoryAcceptance: Boolean = false,
    onBack: () -> Unit,
    onAcceptTerms: (() -> Unit)? = null,
    onSubmitReport: (suspend (category: String, description: String) -> Result<String>)? = null
) {
    BackHandler(enabled = !isMandatoryAcceptance) {
        onBack()
    }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedCategory by rememberSaveable { mutableStateOf("harmful_output") }
    var reportDescription by rememberSaveable { mutableStateOf("") }
    var isSubmittingReport by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    if (!isMandatoryAcceptance) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("terms_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Policy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Terms & Violation Policy",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Version ${AppPolicies.CURRENT_TERMS_VERSION} • Safety & Ethics Charter",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (isAuthenticated && userProfile?.termsAccepted != true && onAcceptTerms != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onAcceptTerms,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("accept_terms_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "I Agree to Terms & Safety Policy",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isAuthenticated && userProfile != null) {
                    AccountStandingCard(userProfile = userProfile)
                }

                PolicySectionCard(
                    icon = Icons.Rounded.VerifiedUser,
                    title = "1. Terms of Service & Acceptable Use",
                    bullets = listOf(
                        "Aashvik AI is an intelligent assistant built to support learning, writing, coding, analysis, and creativity.",
                        "You are responsible for the prompts, images, and files you submit and for verifying critical outputs before acting on them.",
                        "Conversations and settings are synced securely to your Google account via Firebase."
                    )
                )

                PolicySectionCard(
                    icon = Icons.Rounded.Shield,
                    title = "2. AI Safety Training & Zero-Tolerance Rules",
                    bullets = listOf(
                        "Harm & Violence: Requests promoting self-harm, violence, weapons, explosives, poisons, or physical danger are strictly blocked.",
                        "Cybersecurity & Fraud: Generating malware, ransomware, phishing kits, credential theft tools, or exploit payloads is prohibited.",
                        "Abuse & Exploitation: Hate speech, harassment, CSAM/exploitation, and sexually explicit content are strictly refused.",
                        "No Jailbreaks: Attempts to bypass, disable, or override Aashvik AI's safety instructions (such as prompt injection or roleplay bypasses) are automatically blocked and logged."
                    )
                )

                PolicySectionCard(
                    icon = Icons.Rounded.Gavel,
                    title = "3. Zero-Tolerance Automatic Permanent Ban Policy",
                    bullets = listOf(
                        "Immediate Automatic Ban: Any user who violates Aashvik AI's safety rules, attempts jailbreaks/prompt injections, uses restricted harmful keywords, or triggers AI safety protection filters is automatically and permanently banned from Aashvik AI.",
                        "Account Lockout: Permanently banned accounts are immediately locked out of sending prompts, creating chats, or using AI tools across all devices.",
                        "Administrator Review: All safety violations are logged to the verified administrator (${AppPolicies.ADMIN_EMAIL}), who is the sole authority able to review or lift a ban."
                    )
                )

                if (isAuthenticated && userProfile?.moderationStatus != "banned" && onSubmitReport != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Report a Violation or Safety Issue",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }

                            Text(
                                text = "Notice an unsafe response, abuse, privacy concern, or false safety block? Send a direct report to the Aashvik AI safety admin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ReportCategories.forEach { (key, label) ->
                                    FilterChip(
                                        selected = selectedCategory == key,
                                        onClick = { selectedCategory = key },
                                        label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = reportDescription,
                                onValueChange = { reportDescription = it.take(2000) },
                                label = { Text("Describe the violation or issue (min 10 chars)") },
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("violation_report_input")
                            )

                            Button(
                                onClick = {
                                    if (reportDescription.trim().length < 10) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Please write at least 10 characters before submitting.")
                                        }
                                        return@Button
                                    }
                                    isSubmittingReport = true
                                    scope.launch {
                                        val res = onSubmitReport(selectedCategory, reportDescription.trim())
                                        isSubmittingReport = false
                                        res.onSuccess {
                                            reportDescription = ""
                                            snackbarHostState.showSnackbar("Report submitted to the Aashvik AI admin team.")
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar(
                                                err.localizedMessage ?: "Failed to submit violation report."
                                            )
                                        }
                                    }
                                },
                                enabled = !isSubmittingReport && reportDescription.trim().length >= 10,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("submit_violation_report_button")
                            ) {
                                if (isSubmittingReport) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Submit Safety Report")
                            }
                        }
                    }

                    if (ownReports.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Your Submitted Reports (${ownReports.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                ownReports.forEachIndexed { idx, report ->
                                    if (idx > 0) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = report.category.replace("_", " ").uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            StatusPill(status = report.status)
                                        }
                                        Text(
                                            text = report.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!report.adminNotes.isNullOrBlank()) {
                                            Text(
                                                text = "Admin Note: ${report.adminNotes}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AccountStandingCard(userProfile: UserProfile) {
    val isBanned = userProfile.moderationStatus == "banned"
    val isSuspended = userProfile.moderationStatus == "suspended"
    val isWarned = userProfile.moderationStatus == "warned"
    val containerColor = when {
        isBanned || isSuspended -> MaterialTheme.colorScheme.errorContainer
        isWarned -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
    }
    val contentColor = when {
        isBanned || isSuspended -> MaterialTheme.colorScheme.onErrorContainer
        isWarned -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val icon = when {
        isBanned || isSuspended -> Icons.Rounded.ReportProblem
        isWarned -> Icons.Rounded.Security
        else -> Icons.Rounded.VerifiedUser
    }
    val standingTitle = when {
        isBanned -> "Permanently Banned"
        isSuspended -> "Account Suspended"
        isWarned -> "Account Warning (${userProfile.warningCount} warning${if (userProfile.warningCount == 1L) "" else "s"})"
        else -> "Account in Good Standing"
    }
    val standingBody = when {
        isBanned -> "Your account was automatically permanently banned from Aashvik AI. Reason: ${userProfile.banReason ?: "Policy & Safety Violation"}."
        isSuspended -> "Sending new AI messages is disabled due to policy violations. You may submit an appeal via the report form below."
        isWarned -> "Your account has ${userProfile.warningCount} policy warning(s). Any further violation triggers an automatic permanent ban."
        else -> if (userProfile.termsAccepted) {
            "Terms v${userProfile.termsAcceptedVersion.ifBlank { AppPolicies.CURRENT_TERMS_VERSION }} accepted • Zero violations."
        } else {
            "Please review and accept the Terms & Safety Policy below to continue chatting."
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = standingTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = contentColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = standingBody,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun PolicySectionCard(
    icon: ImageVector,
    title: String,
    bullets: List<String>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            bullets.forEach { bullet ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = bullet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (bg, fg) = when (status) {
        "resolved" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "dismissed" -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg
    ) {
        Text(
            text = status.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
