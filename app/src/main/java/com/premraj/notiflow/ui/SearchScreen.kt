package com.premraj.notiflow.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.premraj.notiflow.data.NotificationCategory
import com.premraj.notiflow.data.NotificationItem
import com.premraj.notiflow.data.NotificationPriority
import com.premraj.notiflow.data.NotificationState
import com.premraj.notiflow.intelligence.LocalIntelligence
import com.premraj.notiflow.intelligence.SearchIntent
import com.premraj.notiflow.intelligence.SearchInterpreter
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    notifications: List<NotificationItem>,
    hideSensitive: Boolean,
    onBack: () -> Unit,
    onOpenNotification: (NotificationItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var manualCategory by remember { mutableStateOf<NotificationCategory?>(null) }
    var manualPriority by remember { mutableStateOf<NotificationPriority?>(null) }
    var manualState by remember { mutableStateOf<NotificationState?>(null) }
    var manualApp by remember { mutableStateOf<String?>(null) }

    val intent = remember(query) { SearchInterpreter.interpret(query) }
    val apps = remember(notifications) {
        notifications.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
            .map { it.packageName to it.appName }
    }
    val hasManualFilters = manualCategory != null || manualPriority != null || manualState != null || manualApp != null
    val results = remember(query, notifications, manualCategory, manualPriority, manualState, manualApp) {
        if (query.isBlank() && !hasManualFilters) notifications.take(40)
        else notifications.filter {
            matchesSearch(it, intent, manualCategory, manualPriority, manualState, manualApp)
        }.sortedByDescending { it.postedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = { Text("Search Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(start = 8.dp).size(40.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        placeholder = { Text("Try “payments from yesterday” or “Uber OTP”") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    if (query.isNotBlank()) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            intent.category?.let { MetaPill(it.label, emphasized = true) }
                            intent.priority?.let { MetaPill("${it.label} priority", emphasized = true) }
                            intent.state?.let { MetaPill(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
                            if (intent.from != null) MetaPill("Time understood")
                            intent.amount?.let { MetaPill("Amount ≈ ${it.toInt()}") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Filters", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (hasManualFilters) {
                            TextButton(onClick = {
                                manualCategory = null
                                manualPriority = null
                                manualState = null
                                manualApp = null
                            }) { Text("Clear all") }
                        }
                    }

                    LazyRow(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = manualPriority == NotificationPriority.HIGH,
                                onClick = { manualPriority = if (manualPriority == NotificationPriority.HIGH) null else NotificationPriority.HIGH },
                                label = { Text("Important") },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = manualPriority == NotificationPriority.LOW,
                                onClick = { manualPriority = if (manualPriority == NotificationPriority.LOW) null else NotificationPriority.LOW },
                                label = { Text("Low priority") },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = manualState == NotificationState.LATER,
                                onClick = { manualState = if (manualState == NotificationState.LATER) null else NotificationState.LATER },
                                label = { Text("Later") },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = manualState == NotificationState.ARCHIVED,
                                onClick = { manualState = if (manualState == NotificationState.ARCHIVED) null else NotificationState.ARCHIVED },
                                label = { Text("Archived") },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = manualState == NotificationState.DONE,
                                onClick = { manualState = if (manualState == NotificationState.DONE) null else NotificationState.DONE },
                                label = { Text("Done") },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val categories = listOf(
                            NotificationCategory.OTP,
                            NotificationCategory.PAYMENT,
                            NotificationCategory.DELIVERY,
                            NotificationCategory.MESSAGE,
                            NotificationCategory.WORK_STUDY,
                            NotificationCategory.PROMOTION
                        )
                        items(categories) { category ->
                            FilterChip(
                                selected = manualCategory == category,
                                onClick = { manualCategory = if (manualCategory == category) null else category },
                                label = { Text(category.label) },
                                shape = RoundedCornerShape(100.dp)
                            )
                        }
                    }

                    if (apps.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(apps.take(20)) { (packageName, appName) ->
                                FilterChip(
                                    selected = manualApp == packageName,
                                    onClick = { manualApp = if (manualApp == packageName) null else packageName },
                                    label = { Text(appName) },
                                    shape = RoundedCornerShape(100.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = if (query.isBlank() && !hasManualFilters) "Recent notifications"
                    else "${results.size} result${if (results.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (results.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing matched",
                        message = "Try searching for an app name, sender, OTP code, or clearing selected filters.",
                        icon = Icons.Outlined.Search
                    )
                }
            } else {
                items(results, key = { it.id }) { item ->
                    NotificationCard(
                        item = item,
                        hideSensitive = hideSensitive,
                        onClick = { onOpenNotification(item) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

private fun matchesSearch(
    item: NotificationItem,
    intent: SearchIntent,
    manualCategory: NotificationCategory?,
    manualPriority: NotificationPriority?,
    manualState: NotificationState?,
    manualApp: String?
): Boolean {
    if (intent.from != null && item.postedAt < intent.from) return false
    if (intent.until != null && item.postedAt >= intent.until) return false
    if (intent.category != null && item.category != intent.category) return false
    if (intent.priority != null && LocalIntelligence.effectivePriority(item) != intent.priority) return false
    if (intent.state != null && item.state != intent.state) return false
    if (manualCategory != null && item.category != manualCategory) return false
    if (manualPriority != null && LocalIntelligence.effectivePriority(item) != manualPriority) return false
    if (manualState != null && item.state != manualState) return false
    if (manualApp != null && item.packageName != manualApp) return false
    if (intent.amount != null) {
        val actual = item.amountHint ?: return false
        val tolerance = max(2.0, intent.amount * 0.05)
        if (abs(actual - intent.amount) > tolerance) return false
    }

    val tokens = intent.freeText.split(' ').map { it.trim() }.filter { it.length >= 2 }
    if (tokens.isEmpty()) return true
    val haystack = listOfNotNull(
        item.appName,
        item.packageName,
        item.title,
        item.body,
        item.sender,
        item.category.label
    ).joinToString(" ").lowercase()
    return tokens.all { it in haystack }
}

