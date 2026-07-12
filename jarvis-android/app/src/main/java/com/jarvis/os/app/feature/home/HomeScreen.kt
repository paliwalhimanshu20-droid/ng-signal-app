package com.jarvis.os.app.feature.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jarvis.os.app.data.settings.DashboardCardId
import com.jarvis.os.app.designsystem.JarvisSpacing
import com.jarvis.os.app.designsystem.JarvisStatusColors
import com.jarvis.os.app.designsystem.components.JarvisCard
import com.jarvis.os.app.designsystem.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showCustomizeSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        DailyBriefingHeader(
            connectedCount = state.connectedCount,
            pendingApprovalCount = state.pendingApprovalCount,
            activeProjectCount = state.activeProjectCount,
            onCustomizeClick = { showCustomizeSheet = true },
        )

        ReorderableCardList(
            visibleCards = state.layout.cards.filter { it.visible },
            onMove = viewModel::moveCard,
        )
    }

    if (showCustomizeSheet) {
        ModalBottomSheet(onDismissRequest = { showCustomizeSheet = false }) {
            Column(modifier = Modifier.padding(JarvisSpacing.md)) {
                Text("Customize Dashboard", style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.padding(top = JarvisSpacing.sm)) {
                    state.layout.cards.forEach { card ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = JarvisSpacing.xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(card.id.displayName(), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = card.visible, onCheckedChange = { viewModel.setCardVisible(card.id, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyBriefingHeader(
    connectedCount: Int,
    pendingApprovalCount: Int,
    activeProjectCount: Int,
    onCustomizeClick: () -> Unit,
) {
    JarvisCard(modifier = Modifier.fillMaxWidth().padding(JarvisSpacing.md)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Today's Briefing", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onCustomizeClick) {
                    Icon(Icons.Filled.Tune, contentDescription = "Customize dashboard")
                }
            }
            Text(
                "$connectedCount connection(s) active · $pendingApprovalCount approval(s) waiting · $activeProjectCount project(s) tracked",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = JarvisSpacing.xs),
            )
        }
    }
}

/**
 * Hand-rolled long-press drag reorder — no third-party reorder library
 * dependency, per this codebase's "boring technology, no dependency I
 * can't verify resolves" preference. A long press on the drag handle
 * starts tracking; vertical offset past a fixed threshold (half a
 * typical card's height, in dp -> px via LocalDensity, not measured
 * per-item — a reasonable fixed value rather than false precision) swaps
 * the dragged card with its neighbor in that direction, once per
 * crossing, then resets — the standard "index swap on threshold
 * crossing" reorder algorithm, proportionate to what Part 4 asks for.
 */
@Composable
private fun ReorderableCardList(
    visibleCards: List<com.jarvis.os.app.data.settings.DashboardCardState>,
    onMove: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyColumn(contentPadding = PaddingValues(horizontal = JarvisSpacing.md, vertical = JarvisSpacing.sm)) {
        items(visibleCards, key = { it.id.name }) { card ->
            val index = visibleCards.indexOf(card)
            JarvisCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = JarvisSpacing.xs)
                    .graphicsLayer {
                        translationY = if (draggingIndex == index) dragOffset else 0f
                    },
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DashboardCardContent(card.id)
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder ${card.id.displayName()}",
                        modifier = Modifier
                            .pointerInput(card.id) {
                                var currentIndex = index
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = currentIndex
                                        dragOffset = 0f
                                    },
                                    onDragEnd = {
                                        draggingIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, offset ->
                                        change.consume()
                                        dragOffset += offset.y
                                        if (dragOffset > thresholdPx && currentIndex < visibleCards.lastIndex) {
                                            onMove(currentIndex, currentIndex + 1)
                                            currentIndex += 1
                                            dragOffset = 0f
                                        } else if (dragOffset < -thresholdPx && currentIndex > 0) {
                                            onMove(currentIndex, currentIndex - 1)
                                            currentIndex -= 1
                                            dragOffset = 0f
                                        }
                                    },
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardCardContent(id: DashboardCardId) {
    Column {
        Text(id.displayName(), style = MaterialTheme.typography.titleMedium)
        when (id) {
            DashboardCardId.AI_STATUS -> StatusPill("HEALTHY", JarvisStatusColors.Healthy, modifier = Modifier.padding(top = JarvisSpacing.xs))
            DashboardCardId.CONNECTIONS -> Text("See Connections tab for full detail", style = MaterialTheme.typography.bodySmall)
            else -> Text(placeholderBodyFor(id), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun placeholderBodyFor(id: DashboardCardId): String = when (id) {
    DashboardCardId.TODAYS_BRIEFING -> "Summary of today's activity."
    DashboardCardId.TODAYS_TASKS -> "Pending tasks across all projects."
    DashboardCardId.PROJECTS -> "JARVIS OS, NG Signal Pro, ProjectOS."
    DashboardCardId.NG_SIGNAL_PRO -> "Latest scan and research status."
    DashboardCardId.CALENDAR -> "Upcoming events."
    DashboardCardId.WEATHER -> "Local forecast."
    DashboardCardId.MEMORY_SUMMARY -> "Recent memory activity."
    DashboardCardId.APPROVALS -> "Items awaiting your decision."
    DashboardCardId.QUICK_ACTIONS -> "Shortcuts to common actions."
    else -> ""
}

private fun DashboardCardId.displayName(): String = when (this) {
    DashboardCardId.TODAYS_BRIEFING -> "Today's Briefing"
    DashboardCardId.TODAYS_TASKS -> "Today's Tasks"
    DashboardCardId.PROJECTS -> "Projects"
    DashboardCardId.NG_SIGNAL_PRO -> "NG Signal Pro"
    DashboardCardId.CALENDAR -> "Calendar"
    DashboardCardId.WEATHER -> "Weather"
    DashboardCardId.AI_STATUS -> "AI Status"
    DashboardCardId.MEMORY_SUMMARY -> "Memory Summary"
    DashboardCardId.CONNECTIONS -> "Connections"
    DashboardCardId.APPROVALS -> "Approvals"
    DashboardCardId.QUICK_ACTIONS -> "Quick Actions"
}
