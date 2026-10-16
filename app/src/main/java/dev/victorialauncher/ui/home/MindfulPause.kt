// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.victorialauncher.R
import dev.victorialauncher.data.AppInfo
import dev.victorialauncher.data.MindfulDay
import dev.victorialauncher.data.MindfulTally
import dev.victorialauncher.ui.common.AppIcon
import kotlinx.coroutines.delay

/** Enough of the home screen dimmed away that the pause reads as a step back from it. */
private const val SCRIM_ALPHA = 0.72f

private const val FADE_IN_MS = 220
private const val ICON_SIZE_DP = 56
private val RING_SIZE = 112.dp
private val RING_STROKE = 6.dp

/** One a day, in turn: the same words every time would soon stop being read at all. */
private val LINES = listOf(
    R.string.mindful_line_breath,
    R.string.mindful_line_still_want,
    R.string.mindful_line_plan,
    R.string.mindful_line_nothing_new,
)

/**
 * What stands in front of an app marked "Pause before opening": the app, a line, a countdown,
 * how today has gone with it, and the choice.
 *
 * A speed bump, not a lock. Open is there from the start, only not yet pressable, so the pause
 * never hides what it is waiting for; Never mind is pressable at once, because changing your
 * mind should never be the slower of the two. Drawn inside the home destination rather than as
 * a dialog window, so the home screen's own BACK and HOME handling reach it like everything
 * else there, and the countdown is plain composition — nothing runs once it has gone.
 *
 * [day] is today's tally for this app, and [epochDay] the day it is, which picks the line.
 */
@Composable
fun MindfulPause(
    app: AppInfo,
    label: String,
    seconds: Int,
    day: MindfulDay,
    epochDay: Long,
    scrimColor: Color,
    onNeverMind: () -> Unit,
    onOpen: () -> Unit,
) {
    BackHandler(onBack = onNeverMind)

    // The number and the button count whole seconds by the clock. The ring is only there to be
    // looked at, so it may follow the system's animation speed, even down to none at all,
    // without the wait itself getting any shorter.
    var remaining by remember(app.key) { mutableIntStateOf(seconds) }
    LaunchedEffect(app.key) {
        while (remaining > 0) {
            delay(1000L)
            remaining -= 1
        }
    }
    val ring = remember(app.key) { Animatable(1f) }
    LaunchedEffect(app.key) {
        ring.animateTo(0f, tween(durationMillis = seconds * 1000, easing = LinearEasing))
    }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(FADE_IN_MS)) }

    val line = stringResource(LINES[MindfulTally.lineFor(epochDay, LINES.size)])
    val openedText = if (day.opened > 0) {
        pluralStringResource(R.plurals.mindful_tally_opened, day.opened, day.opened)
    } else {
        stringResource(R.string.mindful_tally_not_opened)
    }
    val tally = when {
        day.opened == 0 && day.resisted == 0 -> stringResource(R.string.mindful_tally_first)
        day.resisted == 0 -> openedText
        else -> stringResource(R.string.mindful_tally_resisted, openedText, day.resisted)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown.value },
    ) {
        // Takes every touch that misses the card, so nothing underneath can be opened past the
        // pause. A tap out here is not taken as an answer either: the choice is the two
        // buttons, or BACK.
        //
        // Named as a pane, which TalkBack announces as it appears, and which as a node of its own
        // covering the whole window hides from TalkBack what it hides from a finger. Nothing else:
        // a click action here would offer the very answer a tap is kept from being.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor.copy(alpha = SCRIM_ALPHA))
                .semantics { paneTitle = label }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    }
                },
        )
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            modifier = Modifier
                .align(Alignment.Center)
                .systemBarsPadding()
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(
                // A phone on its side is not tall enough for all of this at once.
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(app = app, sizeDp = ICON_SIZE_DP)
                Spacer(Modifier.height(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                val ringColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                Box(
                    modifier = Modifier
                        .size(RING_SIZE)
                        // Read while drawing, so the ring moves every frame without the card
                        // being composed again for it.
                        .drawBehind {
                            val stroke = RING_STROKE.toPx()
                            val topLeft = Offset(stroke / 2f, stroke / 2f)
                            val arc = Size(size.width - stroke, size.height - stroke)
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arc,
                                style = Stroke(width = stroke),
                            )
                            val sweep = 360f * ring.value
                            if (sweep > 0f) {
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arc,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (remaining > 0) {
                        Text(remaining.toString(), style = MaterialTheme.typography.displaySmall)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    tally,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onNeverMind) {
                        Text(stringResource(R.string.mindful_never_mind))
                    }
                    Button(
                        onClick = onOpen,
                        enabled = remaining == 0,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Text(
                            stringResource(R.string.mindful_open, label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
