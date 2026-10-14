package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TableData
import com.example.data.model.VocabularyItem
import com.example.ui.theme.BanglaFontFamily
import com.example.ui.theme.PoppinsFontFamily
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

fun hasBengaliChars(text: String): Boolean {
    return text.any { it in '\u0980'..'\u09FF' }
}

@Composable
fun BanglaAwareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val isBangla = hasBengaliChars(text)
    val chosenFamily = if (isBangla) BanglaFontFamily else PoppinsFontFamily

    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontFamily = chosenFamily,
            fontWeight = fontWeight ?: style.fontWeight,
            letterSpacing = if (isBangla) 0.sp else style.letterSpacing
        ),
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow
    )
}

@Composable
fun LessonProgressIndicator(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (total == 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    val percentage = (fraction * 100).toInt()

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$completed/$total completed",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = SubtleBorder,
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
fun VocabularySection(
    items: List<VocabularyItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Vocabulary",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(end = 6.dp)
            )
            Text(
                text = "Key Vocabulary & Bengali Meanings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = item.term,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    BanglaAwareText(
                        text = item.meaningBn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    item.note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }
            if (index < items.size - 1) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun LessonTableView(
    table: TableData,
    modifier: Modifier = Modifier
) {
    if (table.headers.isEmpty()) return

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
    ) {
        Row(modifier = Modifier.horizontalScroll(scrollState)) {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                ) {
                    table.headers.forEach { header ->
                        BanglaAwareText(
                            text = header,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .width(150.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                // Data rows
                table.rows.forEachIndexed { index, row ->
                    val bgColor = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
                    Row(
                        modifier = Modifier
                            .background(bgColor)
                            .padding(vertical = 8.dp, horizontal = 12.dp)
                    ) {
                        row.forEachIndexed { colIdx, cell ->
                            BanglaAwareText(
                                text = cell,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier
                                    .width(150.dp)
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
