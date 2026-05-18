package com.aro.music.presentation.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
// Data models
// ─────────────────────────────────────────────────────────────────────────────

enum class DebugStatus { IDLE, LOADING, SUCCESS, ERROR, EMPTY }

data class DebugApiEvent(
    val source: String,
    val status: DebugStatus,
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Full pipeline debug state.
 *
 * LAYER 1 — Network config
 * LAYER 2 — JioSaavn raw API
 * LAYER 3 — iTunes raw API (fallback)
 * LAYER 4 — StreamingRepository merge
 * LAYER 5 — DailyMixStateHolder (separate parallel call!)
 * LAYER 6 — PlayerViewModel HomeMix job
 * LAYER 7 — UI observed state
 */
data class DebugInfo(
    // ── Layer 1: Network ────────────────────────────────────────────────────
    val jioBaseUrl: String = "https://jiosavan-api2.vercel.app/",
    val itunesBaseUrl: String = "https://itunes.apple.com/",
    val okHttpConnectTimeoutSec: Int = 30,
    val okHttpReadTimeoutSec: Int = 30,
    val userAgent: String = "Aro/1.0 (Android; Music Player)",
    val cleartextAllowed: Boolean = true,

    // ── Layer 2: JioSaavn API ───────────────────────────────────────────────
    val jioLastQuery: String = "-",
    val jioLastHttpStatus: Int? = null,        // null = not called yet
    val jioRawSongsReturned: Int? = null,
    val jioLastError: String? = null,
    val jioStatus: DebugStatus = DebugStatus.IDLE,

    // ── Layer 3: iTunes API ─────────────────────────────────────────────────
    val itunesLastQuery: String = "-",
    val itunesRawSongsReturned: Int? = null,
    val itunesLastError: String? = null,
    val itunesStatus: DebugStatus = DebugStatus.IDLE,

    // ── Layer 4: StreamingRepository ────────────────────────────────────────
    val streamingMergedCount: Int? = null,     // jio + itunes after merge
    val streamingUsedFallback: Boolean = false,

    // ── Layer 5: DailyMixStateHolder ────────────────────────────────────────
    val dailyMixAttempt: Int = 0,
    val dailyMixSongsCount: Int = 0,
    val yourMixSongsCount: Int = 0,
    val dailyMixJobActive: Boolean = false,
    val dailyMixLastError: String? = null,

    // ── Layer 6: PlayerViewModel HomeMix ────────────────────────────────────
    val homeMixAttempt: Int = 0,
    val homeMixMaxAttempts: Int = 5,
    val homeMixSongsLoaded: Int = 0,
    val homeMixJobActive: Boolean = false,
    val homeMixLastError: String? = null,

    // ── Layer 7: UI observed state ──────────────────────────────────────────
    val uiHomeMixPreviewCount: Int = 0,
    val uiYourMixCount: Int = 0,
    val uiDailyMixCount: Int = 0,
    val uiShouldShowLoading: Boolean = false,
    val uiShouldShowEmpty: Boolean = false,
    val uiMinimumElapsed: Boolean = false,

    // ── Event log ────────────────────────────────────────────────────────────
    val events: List<DebugApiEvent> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// Main composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DebugOverlay(
    debugInfo: DebugInfo,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF0101020))
            .border(1.dp, Color(0xFF00FF88).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.BugReport, null, tint = Color(0xFF00FF88), modifier = Modifier.size(14.dp))
                MonoText("DEBUG PANEL", Color(0xFF00FF88), 10, bold = true)

                // Overall status pill
                val (pillColor, pillText) = when {
                    debugInfo.homeMixJobActive || debugInfo.dailyMixJobActive -> Color(0xFFFFAA00) to "LOADING"
                    debugInfo.uiHomeMixPreviewCount > 0 -> Color(0xFF00FF88) to "OK ${debugInfo.uiHomeMixPreviewCount} songs"
                    debugInfo.jioLastError != null -> Color(0xFFFF4444) to "JIO ERR"
                    debugInfo.homeMixLastError != null -> Color(0xFFFF4444) to "MIX ERR"
                    else -> Color(0xFF888888) to "IDLE"
                }
                StatusPill(pillText, pillColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRetry, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Rounded.Refresh, "Retry", tint = Color(0xFF88AAFF), modifier = Modifier.size(12.dp))
                }
                MonoText(if (expanded) "▲" else "▼", Color(0xFF666666), 9)
            }
        }

        // ── Body ─────────────────────────────────────────────────────────────
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                // ── Layer 1: Network ─────────────────────────────────────────
                Section("LAYER 1 — NETWORK CONFIG") {
                    KV("JioSaavn URL", debugInfo.jioBaseUrl, scrollable = true)
                    KV("iTunes URL", debugInfo.itunesBaseUrl, scrollable = true)
                    KV("Connect timeout", "${debugInfo.okHttpConnectTimeoutSec}s")
                    KV("Read timeout", "${debugInfo.okHttpReadTimeoutSec}s")
                    KV("User-Agent", debugInfo.userAgent, scrollable = true)
                    KV("Cleartext", if (debugInfo.cleartextAllowed) "✓ allowed" else "✗ blocked",
                        if (debugInfo.cleartextAllowed) Color(0xFF00FF88) else Color(0xFFFF4444))
                }

                // ── Layer 2: JioSaavn ────────────────────────────────────────
                Section("LAYER 2 — JIOSAAVN API", statusColor(debugInfo.jioStatus)) {
                    KV("Status", statusLabel(debugInfo.jioStatus), statusColor(debugInfo.jioStatus))
                    KV("Last query", debugInfo.jioLastQuery)
                    KV("HTTP code", debugInfo.jioLastHttpStatus?.toString() ?: "—",
                        if (debugInfo.jioLastHttpStatus == 200) Color(0xFF00FF88) else Color(0xFFFF8800))
                    KV("Songs raw", debugInfo.jioRawSongsReturned?.toString() ?: "—",
                        if ((debugInfo.jioRawSongsReturned ?: 0) > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    debugInfo.jioLastError?.let { KV("Error", it, Color(0xFFFF4444), scrollable = true) }
                }

                // ── Layer 3: iTunes ──────────────────────────────────────────
                Section("LAYER 3 — ITUNES API (fallback)", statusColor(debugInfo.itunesStatus)) {
                    KV("Status", statusLabel(debugInfo.itunesStatus), statusColor(debugInfo.itunesStatus))
                    KV("Last query", debugInfo.itunesLastQuery)
                    KV("Songs raw", debugInfo.itunesRawSongsReturned?.toString() ?: "not called yet",
                        if ((debugInfo.itunesRawSongsReturned ?: 0) > 0) Color(0xFF00FF88) else Color(0xFF888888))
                    debugInfo.itunesLastError?.let { KV("Error", it, Color(0xFFFF4444), scrollable = true) }
                }

                // ── Layer 4: StreamingRepository ─────────────────────────────
                Section("LAYER 4 — STREAMING REPO MERGE") {
                    KV("Merged songs", debugInfo.streamingMergedCount?.toString() ?: "—",
                        if ((debugInfo.streamingMergedCount ?: 0) > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    KV("Used fallback?", if (debugInfo.streamingUsedFallback) "YES (iTunes)" else "No (JioSaavn ok)",
                        if (debugInfo.streamingUsedFallback) Color(0xFFFFAA00) else Color(0xFF888888))
                }

                // ── Layer 5: DailyMixStateHolder ─────────────────────────────
                Section("LAYER 5 — DAILYMIX STATE HOLDER") {
                    KV("Job active", debugInfo.dailyMixJobActive.toString(),
                        if (debugInfo.dailyMixJobActive) Color(0xFFFFAA00) else Color(0xFF888888))
                    KV("Attempt", "${debugInfo.dailyMixAttempt}/5")
                    KV("Daily mix songs", debugInfo.dailyMixSongsCount.toString(),
                        if (debugInfo.dailyMixSongsCount > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    KV("Your mix songs", debugInfo.yourMixSongsCount.toString(),
                        if (debugInfo.yourMixSongsCount > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    debugInfo.dailyMixLastError?.let { KV("Error", it, Color(0xFFFF4444), scrollable = true) }
                }

                // ── Layer 6: HomeMix job ──────────────────────────────────────
                Section("LAYER 6 — HOMEMIX JOB (PlayerVM)") {
                    KV("Job active", debugInfo.homeMixJobActive.toString(),
                        if (debugInfo.homeMixJobActive) Color(0xFFFFAA00) else Color(0xFF888888))
                    KV("Attempt", "${debugInfo.homeMixAttempt}/${debugInfo.homeMixMaxAttempts}")
                    KV("Songs loaded", debugInfo.homeMixSongsLoaded.toString(),
                        if (debugInfo.homeMixSongsLoaded > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    debugInfo.homeMixLastError?.let { KV("Last error", it, Color(0xFFFF4444), scrollable = true) }
                }

                // ── Layer 7: UI state ─────────────────────────────────────────
                Section("LAYER 7 — UI OBSERVED STATE") {
                    KV("homeMixPreview", "${debugInfo.uiHomeMixPreviewCount} songs",
                        if (debugInfo.uiHomeMixPreviewCount > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    KV("yourMixSongs", "${debugInfo.uiYourMixCount} songs",
                        if (debugInfo.uiYourMixCount > 0) Color(0xFF00FF88) else Color(0xFFFF4444))
                    KV("dailyMixSongs", "${debugInfo.uiDailyMixCount} songs",
                        if (debugInfo.uiDailyMixCount > 0) Color(0xFF00FF88) else Color(0xFF888888))
                    KV("showing loading?", debugInfo.uiShouldShowLoading.toString(),
                        if (debugInfo.uiShouldShowLoading) Color(0xFFFFAA00) else Color(0xFF888888))
                    KV("showing empty?", debugInfo.uiShouldShowEmpty.toString(),
                        if (debugInfo.uiShouldShowEmpty) Color(0xFFFF4444) else Color(0xFF888888))
                    KV("minElapsed?", debugInfo.uiMinimumElapsed.toString())
                }

                // ── Event Log ─────────────────────────────────────────────────
                if (debugInfo.events.isNotEmpty()) {
                    Section("EVENT LOG (latest first)") {
                        debugInfo.events.asReversed().take(12).forEach { evt ->
                            val (color, prefix) = when (evt.status) {
                                DebugStatus.SUCCESS -> Color(0xFF00FF88) to "✓"
                                DebugStatus.ERROR   -> Color(0xFFFF4444) to "✗"
                                DebugStatus.LOADING -> Color(0xFFFFAA00) to "⟳"
                                DebugStatus.EMPTY   -> Color(0xFFFFFF44) to "∅"
                                DebugStatus.IDLE    -> Color(0xFF666666) to "·"
                            }
                            val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(evt.timestamp))
                            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                                MonoText("$prefix ", color, 8)
                                MonoText("[$ts] ${evt.source}: ${evt.detail}", Color(0xFFBBBBBB), 8,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Section(
    title: String,
    titleAccent: Color = Color(0xFF666666),
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        MonoText(title, titleAccent, 8, bold = true, modifier = Modifier.padding(bottom = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x1AFFFFFF))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            content = content
        )
    }
}

@Composable
private fun KV(
    key: String,
    value: String,
    valueColor: Color = Color(0xFFDDDDDD),
    scrollable: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonoText("$key:", Color(0xFF777777), 9, modifier = Modifier.widthIn(min = 90.dp))
        if (scrollable) {
            Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                MonoText(value, valueColor, 9, maxLines = 1)
            }
        } else {
            MonoText(value, valueColor, 9, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        MonoText(text, color, 8)
    }
}

@Composable
private fun MonoText(
    text: String,
    color: Color,
    size: Int,
    bold: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines,
        modifier = modifier
    )
}

private fun statusColor(status: DebugStatus) = when (status) {
    DebugStatus.SUCCESS -> Color(0xFF00FF88)
    DebugStatus.ERROR   -> Color(0xFFFF4444)
    DebugStatus.LOADING -> Color(0xFFFFAA00)
    DebugStatus.EMPTY   -> Color(0xFFFFFF44)
    DebugStatus.IDLE    -> Color(0xFF666666)
}

private fun statusLabel(status: DebugStatus) = when (status) {
    DebugStatus.SUCCESS -> "SUCCESS"
    DebugStatus.ERROR   -> "ERROR"
    DebugStatus.LOADING -> "LOADING"
    DebugStatus.EMPTY   -> "EMPTY (0 songs)"
    DebugStatus.IDLE    -> "IDLE (not called)"
}
