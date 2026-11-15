package com.example.ui.admin

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AppPolicies
import com.example.data.model.SafetyPolicy
import com.example.data.model.SafetyViolation
import com.example.data.model.UserProfile
import com.example.data.model.ViolationReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: AdminViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val snackbarHostState = remember { SnackbarHostState() }
    val notice by viewModel.notice.collectAsStateWithLifecycle()

    LaunchedEffect(notice) {
        notice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissNotice()
        }
    }

    if (!viewModel.isAuthorizedAdmin) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = { Text("Restricted Access") }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Admin Access Denied",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "This panel is strictly reserved for the verified administrator (${AppPolicies.ADMIN_EMAIL}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
        return
    }

    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val violations by viewModel.safetyViolations.collectAsStateWithLifecycle()
    val reports by viewModel.violationReports.collectAsStateWithLifecycle()
    val policy by viewModel.safetyPolicy.collectAsStateWithLifecycle()
    val isSavingPolicy by viewModel.isSavingPolicy.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Violations & Reports", "Users", "AI Safety Training")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to chat"
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Aashvik AI Admin Panel",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = AppPolicies.ADMIN_EMAIL,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag("admin_tab_$idx")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 880.dp)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> AdminOverviewTab(
                        users = allUsers,
                        violations = violations,
                        reports = reports,
                        policy = policy,
                        onNavigateTab = { selectedTab = it }
                    )
                    1 -> AdminViolationsAndReportsTab(
                        users = allUsers,
                        violations = violations,
                        reports = reports,
                        onUpdateViolationStatus = viewModel::updateSafetyViolationStatus,
                        onUpdateReportStatus = viewModel::updateViolationReportStatus,
                        onWarnUser = viewModel::warnUser,
                        onBanUser = { uid, name, warnings, reason ->
                            viewModel.banUserPermanently(uid, name, warnings, reason)
                        }
                    )
                    2 -> AdminUsersTab(
                        users = allUsers,
                        onWarnUser = viewModel::warnUser,
                        onBanUser = { user ->
                            viewModel.banUserPermanently(
                                user.userId,
                                user.displayName,
                                user.warningCount,
                                "Permanently banned by administrator for policy violation."
                            )
                        },
                        onRestoreUser = { user ->
                            viewModel.restoreUser(user.userId, user.displayName)
                        }
                    )
                    3 -> AdminSafetyTrainingTab(
                        currentPolicy = policy,
                        isSaving = isSavingPolicy,
                        onSavePolicy = viewModel::saveSafetyPolicy
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminOverviewTab(
    users: List<UserProfile>,
    violations: List<SafetyViolation>,
    reports: List<ViolationReport>,
    policy: SafetyPolicy,
    onNavigateTab: (Int) -> Unit
) {
    val openViolations = violations.count { it.status == "open" }
    val openReports = reports.count { it.status == "open" }
    val bannedCount = users.count { it.moderationStatus == "banned" }
    val suspendedCount = users.count { it.moderationStatus == "suspended" }
    val warnedCount = users.count { it.moderationStatus == "warned" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (policy.strictModeEnabled) "Strict AI Safety Active" else "Standard AI Safety Active",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${policy.blockedKeywords.size} custom blocked keywords • Auto-flagging ${if (policy.autoFlagViolations) "ON" else "OFF"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricCard(
                icon = Icons.Rounded.Group,
                label = "Total Users",
                value = users.size.toString(),
                subtitle = "$bannedCount banned • $warnedCount warned • $suspendedCount suspended",
                onClick = { onNavigateTab(2) },
                modifier = Modifier.weight(1f, fill = false).widthIn(min = 160.dp)
            )
            MetricCard(
                icon = Icons.Rounded.ReportProblem,
                label = "Open Safety Flags",
                value = openViolations.toString(),
                subtitle = "${violations.size} total logged",
                onClick = { onNavigateTab(1) },
                modifier = Modifier.weight(1f, fill = false).widthIn(min = 160.dp)
            )
            MetricCard(
                icon = Icons.Rounded.Flag,
                label = "Open User Reports",
                value = openReports.toString(),
                subtitle = "${reports.size} total reports",
                onClick = { onNavigateTab(1) },
                modifier = Modifier.weight(1f, fill = false).widthIn(min = 160.dp)
            )
            MetricCard(
                icon = Icons.Rounded.Psychology,
                label = "Safety Directives",
                value = if (policy.customSafetyDirectives.isNotBlank()) "Custom" else "Core",
                subtitle = "Tap to tune AI training",
                onClick = { onNavigateTab(3) },
                modifier = Modifier.weight(1f, fill = false).widthIn(min = 160.dp)
            )
        }

        if (policy.customSafetyDirectives.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Active Custom Training Directives",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = policy.customSafetyDirectives,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminViolationsAndReportsTab(
    users: List<UserProfile>,
    violations: List<SafetyViolation>,
    reports: List<ViolationReport>,
    onUpdateViolationStatus: (String, String) -> Unit,
    onUpdateReportStatus: (String, String, String?) -> Unit,
    onWarnUser: (UserProfile) -> Unit,
    onBanUser: (userId: String, displayName: String, warningCount: Long, reason: String) -> Unit
) {
    var showAutoFlags by rememberSaveable { mutableStateOf(true) }
    var reportToResolve by remember { mutableStateOf<ViolationReport?>(null) }
    var adminNoteDraft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = showAutoFlags,
                onClick = { showAutoFlags = true },
                label = { Text("AI Safety Flags (${violations.size})") }
            )
            FilterChip(
                selected = !showAutoFlags,
                onClick = { showAutoFlags = false },
                label = { Text("User Reports (${reports.size})") }
            )
        }

        if (showAutoFlags) {
            if (violations.isEmpty()) {
                EmptyAdminState(
                    icon = Icons.Rounded.VerifiedUser,
                    message = "No automated AI safety violations flagged yet."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(violations, key = { it.id }) { vio ->
                        val matchedUser = users.firstOrNull { it.userId == vio.userId }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${vio.userDisplayName} • ${vio.category.replace("_", " ").uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    AdminBadge(text = "${vio.severity} • ${vio.status}")
                                }
                                if (!vio.userEmail.isNullOrBlank()) {
                                    Text(
                                        text = vio.userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = vio.reason,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (vio.promptSnippet.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "\"${vio.promptSnippet}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (vio.status == "open") {
                                        OutlinedButton(
                                            onClick = { onUpdateViolationStatus(vio.id, "reviewed") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Mark Reviewed", maxLines = 1)
                                        }
                                        TextButton(
                                            onClick = { onUpdateViolationStatus(vio.id, "dismissed") }
                                        ) {
                                            Text("Dismiss")
                                        }
                                    }
                                    if (matchedUser != null && matchedUser.moderationStatus != "banned") {
                                        TextButton(onClick = { onWarnUser(matchedUser) }) {
                                            Text("Warn", color = MaterialTheme.colorScheme.tertiary)
                                        }
                                        TextButton(
                                            onClick = {
                                                onBanUser(
                                                    matchedUser.userId,
                                                    matchedUser.displayName,
                                                    matchedUser.warningCount,
                                                    vio.reason
                                                )
                                            }
                                        ) {
                                            Text("Ban", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (reports.isEmpty()) {
                EmptyAdminState(
                    icon = Icons.Rounded.Flag,
                    message = "No user violation reports submitted yet."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports, key = { it.id }) { report ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${report.reporterName} • ${report.category.replace("_", " ").uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    AdminBadge(text = report.status)
                                }
                                if (!report.reporterEmail.isNullOrBlank()) {
                                    Text(
                                        text = report.reporterEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = report.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!report.adminNotes.isNullOrBlank()) {
                                    Text(
                                        text = "Admin Note: ${report.adminNotes}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (report.status == "open") {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                reportToResolve = report
                                                adminNoteDraft = report.adminNotes.orEmpty()
                                            }
                                        ) {
                                            Text("Resolve")
                                        }
                                        TextButton(
                                            onClick = {
                                                onUpdateReportStatus(report.id, "dismissed", null)
                                            }
                                        ) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    reportToResolve?.let { report ->
        AlertDialog(
            onDismissRequest = { reportToResolve = null },
            title = { Text("Resolve Violation Report") },
            text = {
                OutlinedTextField(
                    value = adminNoteDraft,
                    onValueChange = { adminNoteDraft = it.take(1000) },
                    label = { Text("Optional admin resolution note") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdateReportStatus(
                            report.id,
                            "resolved",
                            adminNoteDraft.takeIf { it.isNotBlank() }
                        )
                        reportToResolve = null
                    }
                ) {
                    Text("Mark Resolved")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportToResolve = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminUsersTab(
    users: List<UserProfile>,
    onWarnUser: (UserProfile) -> Unit,
    onBanUser: (UserProfile) -> Unit,
    onRestoreUser: (UserProfile) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(users, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) users else users.filter {
            it.displayName.lowercase().contains(q) ||
                (it.email?.lowercase()?.contains(q) == true) ||
                it.moderationStatus.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            placeholder = { Text("Search users by name, email, or status…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        if (filtered.isEmpty()) {
            EmptyAdminState(
                icon = Icons.Rounded.Group,
                message = "No matching users found."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.userId }) { user ->
                    val isAdminRow = user.email.equals(AppPolicies.ADMIN_EMAIL, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isAdminRow) "${user.displayName} (Admin)" else user.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (!user.email.isNullOrBlank()) {
                                        Text(
                                            text = user.email,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                AdminBadge(
                                    text = "${user.moderationStatus.uppercase()} • ${user.warningCount} warns"
                                )
                            }

                            if (user.moderationStatus == "banned" && !user.banReason.isNullOrBlank()) {
                                Text(
                                    text = "Ban Reason: ${user.banReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Text(
                                text = if (user.termsAccepted) {
                                    "Terms v${user.termsAcceptedVersion.ifBlank { AppPolicies.CURRENT_TERMS_VERSION }} accepted"
                                } else {
                                    "Terms not yet accepted"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (!isAdminRow) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (user.moderationStatus != "banned") {
                                        OutlinedButton(onClick = { onWarnUser(user) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Warning,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Warn (+1)")
                                        }
                                        Button(
                                            onClick = { onBanUser(user) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            )
                                        ) {
                                            Text("Ban Permanently")
                                        }
                                    }
                                    if (user.moderationStatus != "active" || user.warningCount > 0L) {
                                        TextButton(onClick = { onRestoreUser(user) }) {
                                            Text(if (user.moderationStatus == "banned") "Unban / Restore" else "Restore Active")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminSafetyTrainingTab(
    currentPolicy: SafetyPolicy,
    isSaving: Boolean,
    onSavePolicy: (
        strictModeEnabled: Boolean,
        autoFlagViolations: Boolean,
        blockedKeywords: List<String>,
        customSafetyDirectives: String
    ) -> Unit
) {
    var strictMode by remember(currentPolicy) { mutableStateOf(currentPolicy.strictModeEnabled) }
    var autoFlag by remember(currentPolicy) { mutableStateOf(currentPolicy.autoFlagViolations) }
    var keywords by remember(currentPolicy) { mutableStateOf(currentPolicy.blockedKeywords) }
    var directives by remember(currentPolicy) { mutableStateOf(currentPolicy.customSafetyDirectives) }
    var newKeywordDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict AI Safety Mode",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Enforces LOW_AND_ABOVE harm thresholds across Harassment, Hate, Sexual, Dangerous Content & Civic Integrity.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = strictMode,
                        onCheckedChange = { strictMode = it },
                        modifier = Modifier.testTag("admin_strict_mode_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Flag Violations",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Automatically record blocked prompts and safety filter triggers into the Admin moderation queue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoFlag,
                        onCheckedChange = { autoFlag = it },
                        modifier = Modifier.testTag("admin_auto_flag_switch")
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Blocked Keywords & Topics (${keywords.size}/50)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newKeywordDraft,
                        onValueChange = { newKeywordDraft = it.take(80) },
                        label = { Text("Add restricted word or phrase") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_keyword_input")
                    )
                    Button(
                        onClick = {
                            val clean = newKeywordDraft.trim().lowercase()
                            if (clean.isNotEmpty() && !keywords.contains(clean) && keywords.size < 50) {
                                keywords = keywords + clean
                                newKeywordDraft = ""
                            }
                        },
                        enabled = newKeywordDraft.isNotBlank() && keywords.size < 50,
                        modifier = Modifier.height(52.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add keyword")
                    }
                }

                if (keywords.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keywords.forEach { kw ->
                            InputChip(
                                selected = false,
                                onClick = { keywords = keywords.filterNot { it == kw } },
                                label = { Text(kw) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Remove $kw",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
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
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Custom AI Safety Training Directives",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                Text(
                    text = "These rules are injected directly into Aashvik AI's system instructions for all users in real time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = directives,
                    onValueChange = { directives = it.take(2000) },
                    placeholder = {
                        Text("Example: Never provide betting advice, always encourage safe academic integrity, and politely decline offensive humor.")
                    },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_directives_input")
                )
            }
        }

        Button(
            onClick = {
                onSavePolicy(strictMode, autoFlag, keywords, directives)
            },
            enabled = !isSaving,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_safety_policy_button")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Save & Train Aashvik AI")
        }
    }
}

@Composable
private fun EmptyAdminState(
    icon: ImageVector,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AdminBadge(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}
