package com.kosi.lessonlog

import android.app.KeyguardManager
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * The screen the notification's full screen intent launches. This is the thing a
 * PWA cannot do: it shows over the lock screen and stays there until dealt with,
 * rather than sitting quietly in the status bar.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val session = Session.fromKey(intent.getStringExtra(Reminders.EXTRA_SESSION)) ?: Session.MORNING
        val date = intent.getStringExtra(Reminders.EXTRA_DATE) ?: Rules.today().toString()
        val snoozesLeft = intent.getIntExtra("snoozesLeft", Rules.MAX_SNOOZES)

        getSystemService(NotificationManager::class.java).cancel(session.notificationId())

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AlarmScreen(
                    session = session,
                    snoozesLeft = snoozesLeft,
                    onAction = { what -> submit(session, date, what) },
                )
            }
        }
    }

    private fun submit(session: Session, date: String, what: String) {
        lifecycleScope.launch {
            val result = LessonApi.act(date, session, what)
            if (what == "snooze" && result.status == "pending") {
                Reminders.snooze(this@AlarmActivity, session, date)
            } else {
                Reminders.cancel(this@AlarmActivity, session, date)
                Reminders.schedule(this@AlarmActivity, session, Reminders.nextOccurrence(session))
            }
            finish()
        }
    }
}

@Composable
private fun AlarmScreen(session: Session, snoozesLeft: Int, onAction: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            session.label,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(6.dp))
        Text(session.detail, fontSize = 14.sp, color = Color(0xFF9A9A9A))
        Spacer(Modifier.height(20.dp))
        Text(
            if (snoozesLeft > 0) "Not logged yet. $snoozesLeft snooze${if (snoozesLeft == 1) "" else "s"} left."
            else "Last call. No snoozes left - this goes down as incomplete.",
            fontSize = 16.sp,
            color = Color(0xFFE0E0E0),
        )
        Spacer(Modifier.height(36.dp))

        Button(
            onClick = { busy = true; onAction("done") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Mark done", fontSize = 16.sp) }

        Spacer(Modifier.height(12.dp))

        if (snoozesLeft > 0) {
            OutlinedButton(
                onClick = { busy = true; onAction("snooze") },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Snooze ${Rules.SNOOZE_MINUTES} min", fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
        }

        TextButton(
            onClick = { busy = true; onAction("cancel") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mark incomplete", color = Color(0xFFD85A30)) }
    }
}
