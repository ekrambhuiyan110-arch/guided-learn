package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ErrorBg
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SubtleBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningBg
import kotlinx.coroutines.delay

@Composable
fun SectionTimerControl(
    initialSeconds: Int,
    modifier: Modifier = Modifier,
    onTimerFinished: () -> Unit = {}
) {
    if (initialSeconds <= 0) return

    var remainingSeconds by remember(initialSeconds) { mutableIntStateOf(initialSeconds) }
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }

    LaunchedEffect(isRunning, remainingSeconds) {
        if (isRunning && remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
            if (remainingSeconds == 0) {
                isRunning = false
                isFinished = true
                onTimerFinished()
            }
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val formattedTime = String.format("%02d:%02d", minutes, seconds)

    val timerColor = when {
        isFinished -> ErrorRed
        remainingSeconds <= 60 && isRunning -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }

    val containerBg = when {
        isFinished -> ErrorBg
        remainingSeconds <= 60 && isRunning -> WarningBg
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(1.dp, SubtleBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Timer",
                tint = timerColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
                    color = timerColor
                )
                if (isFinished) {
                    Text(
                        text = "Time is up! You can keep writing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorRed
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isRunning) {
                Button(
                    onClick = {
                        if (remainingSeconds == 0) remainingSeconds = initialSeconds
                        isFinished = false
                        isRunning = true
                    },
                    modifier = Modifier.testTag("timer_start_button"),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (remainingSeconds < initialSeconds && !isFinished) "Resume" else "Start")
                }
            } else {
                OutlinedButton(
                    onClick = { isRunning = false },
                    modifier = Modifier.testTag("timer_pause_button"),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pause")
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    isRunning = false
                    remainingSeconds = initialSeconds
                    isFinished = false
                },
                modifier = Modifier.size(36.dp).testTag("timer_reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Timer",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
