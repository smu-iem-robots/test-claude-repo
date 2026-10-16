// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.applist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.R
import dev.victorialauncher.data.HomeAlignment

/** An answer put into words: [value] shown large and copied, [detail] shown small under it. */
internal class ShownAnswer(val value: String, val detail: String, val rerolls: Boolean)

/** [this] in words, from the resources, for showing and for copying. */
@Composable
internal fun SearchAnswer.shown(): ShownAnswer {
    val again = stringResource(R.string.search_answer_again)
    return when (this) {
        is SearchAnswer.Value ->
            ShownAnswer(value, if (rerolls) "$detail · $again" else detail, rerolls)
        is SearchAnswer.DividedByZero ->
            ShownAnswer(stringResource(R.string.search_answer_divided_by_zero), expression, false)
        is SearchAnswer.Wind -> {
            val name = stringArrayResource(R.array.search_beaufort_names).getOrElse(force) { "" }
            val scale = "$force Bft · $name"
            if (onScale) ShownAnswer(scale, speed, false) else ShownAnswer(speed, scale, false)
        }
        is SearchAnswer.Coin -> ShownAnswer(
            stringResource(if (heads) R.string.search_answer_heads else R.string.search_answer_tails),
            "${stringResource(R.string.search_answer_coin)} · $again",
            true,
        )
        is SearchAnswer.EightBall -> ShownAnswer(
            stringArrayResource(R.array.search_eight_ball).getOrElse(index) { "" },
            "${stringResource(R.string.search_answer_eight_ball)} · $again",
            true,
        )
        is SearchAnswer.Hello -> ShownAnswer(
            stringArrayResource(R.array.search_hello).getOrElse(index) { "" },
            "${stringResource(R.string.app_name)} · $again",
            true,
        )
        is SearchAnswer.MeaningOfLife ->
            ShownAnswer("42", stringResource(R.string.search_answer_meaning_of_life), false)
    }
}

/** What an action's row says it will do. */
@Composable
internal fun SearchAction.label(): String = when (this) {
    is SearchAction.Call -> stringResource(R.string.search_action_call, shown)
    is SearchAction.Text -> stringResource(R.string.search_action_text, shown)
    is SearchAction.OpenLink -> stringResource(R.string.search_action_open, host)
    is SearchAction.Email -> stringResource(R.string.search_action_email, address)
    is SearchAction.Timer -> stringResource(R.string.search_action_timer, formatDuration(seconds))
    is SearchAction.Alarm -> stringResource(R.string.search_action_alarm, formatClock(hour, minute))
}

private fun SearchAction.icon(): ImageVector = when (this) {
    is SearchAction.Call -> Icons.Filled.Call
    is SearchAction.Text -> Icons.Filled.Sms
    is SearchAction.OpenLink -> Icons.Filled.Language
    is SearchAction.Email -> Icons.Filled.Email
    is SearchAction.Timer -> Icons.Filled.Timer
    is SearchAction.Alarm -> Icons.Filled.Alarm
}

/** As tall as an app row at its smallest, so the two read as one list. */
private val EXTRA_ROW_HEIGHT = 48.dp

/**
 * The answers and actions for a query, between the search field and the results.
 *
 * Not rows of the list itself. The scrub works from row positions, and anything put ahead of the
 * apps there would move every one of them by however many answers a query happened to have. So
 * this sits outside it, lined up with the rows by the same insets and the same padding, and
 * empty — no height at all — when the query answers nothing.
 */
@Composable
internal fun SearchExtras(
    answers: List<ShownAnswer>,
    actions: List<SearchAction>,
    contentColor: Color,
    alignment: HomeAlignment,
    labelSizeSp: Int,
    /** The strip's room on either side, which the list's rows keep clear of too. */
    startInset: Dp,
    endInset: Dp,
    onAnswer: (ShownAnswer) -> Unit,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(start = startInset, end = endInset)) {
        answers.forEach { answer ->
            AnswerRow(answer, contentColor, alignment, labelSizeSp) { onAnswer(answer) }
        }
        actions.forEach { action ->
            ActionRow(action.label(), action.icon(), contentColor, alignment, labelSizeSp) { onAction(action) }
        }
    }
}

/** The press treatment of the list's own rows: a tint behind, since a ripple vanishes on a wallpaper. */
private fun Modifier.extraRow(
    contentColor: Color,
    interaction: MutableInteractionSource,
    pressed: Boolean,
    onClick: () -> Unit,
): Modifier =
    this
        .fillMaxWidth()
        .padding(horizontal = 20.dp)
        .background(
            color = if (pressed) contentColor.copy(alpha = 0.15f) else Color.Transparent,
            shape = RoundedCornerShape(18.dp),
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .heightIn(min = EXTRA_ROW_HEIGHT)
        .padding(horizontal = 8.dp, vertical = 6.dp)

@Composable
private fun AnswerRow(
    answer: ShownAnswer,
    contentColor: Color,
    alignment: HomeAlignment,
    labelSizeSp: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val textAlign = when (alignment) {
        HomeAlignment.LEFT -> TextAlign.Start
        HomeAlignment.CENTER -> TextAlign.Center
        HomeAlignment.RIGHT -> TextAlign.End
    }
    Column(
        modifier = Modifier.extraRow(contentColor, interaction, pressed, onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = when (alignment) {
            HomeAlignment.LEFT -> Alignment.Start
            HomeAlignment.CENTER -> Alignment.CenterHorizontally
            HomeAlignment.RIGHT -> Alignment.End
        },
    ) {
        Text(
            answer.value,
            color = contentColor,
            fontSize = (labelSizeSp + 6).sp,
            fontWeight = FontWeight.Medium,
            textAlign = textAlign,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (answer.detail.isNotEmpty()) {
            Text(
                answer.detail,
                color = contentColor.copy(alpha = 0.7f),
                fontSize = (labelSizeSp - 3).coerceAtLeast(10).sp,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    contentColor: Color,
    alignment: HomeAlignment,
    labelSizeSp: Int,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier.extraRow(contentColor, interaction, pressed, onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when (alignment) {
            HomeAlignment.LEFT -> Arrangement.Start
            HomeAlignment.CENTER -> Arrangement.Center
            HomeAlignment.RIGHT -> Arrangement.End
        },
    ) {
        // The icon on the side the text starts from, as the settings row at the end of the list
        // does, so a right-aligned list reads from its edge inward.
        if (alignment == HomeAlignment.RIGHT) {
            Text(
                label,
                color = contentColor,
                fontSize = labelSizeSp.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(16.dp))
            Icon(icon, contentDescription = null, tint = contentColor)
        } else {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                color = contentColor,
                fontSize = labelSizeSp.sp,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
