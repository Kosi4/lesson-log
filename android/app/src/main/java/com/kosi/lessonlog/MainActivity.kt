package com.kosi.lessonlog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val rows = MutableStateFlow<Map<String, LogRow>>(emptyMap())
    private val loading = MutableStateFlow(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        Reminders.scheduleAll(this)

        setContent {
            MaterialTheme {
                val data by rows.collectAsStateWithLifecycle()
                val busy by loading.collectAsStateWithLifecycle()
                HomeScreen(
                    rows = data,
                    loading = busy,
                    onAction = ::submit,
                    onFixAlarms = ::openExactAlarmSettings,
                )
            }
        }

        askForNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        lifecycleScope.launch {
            loading.value = true
            runCatching { LessonApi.fetchRows() }
                .onSuccess { list -> rows.value = list.associateBy { it.logDate } }
            loading.value = false
        }
    }

    private fun submit(session: Session, action: String, note: String?) {
        lifecycleScope.launch {
            val date = Rules.today().toString()
            LessonApi.act(date, session, action, note)
            if (action == "done" || action == "cancel") {
                Reminders.cancel(this@MainActivity, session, date)
            }
            refresh()
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Android 12+ can revoke exact alarms; without them a nudge drifts. */
    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
            )
        }
    }
}

private val GREEN = Color(0xFF1D9E75)
private val AMBER = Color(0xFFE8B84B)
private val RED = Color(0xFFD85A30)
private val MUTED = Color(0xFFF0EFEB)

@Composable
private fun HomeScreen(
    rows: Map<String, LogRow>,
    loading: Boolean,
    onAction: (Session, String, String?) -> Unit,
    onFixAlarms: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(Rules.today())) }
    val today = Rules.today()
    val todayRow = rows[today.toString()]

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Lesson log", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Streak", Rules.computeStreak(rows).toString(), Modifier.weight(1f))
                Stat("Done", rows.values.sumOf { r ->
                    Session.entries.count { r.statusOf(it) == "done" }
                }.toString(), Modifier.weight(1f))
            }
        }

        item { SectionTitle(if (Rules.isStudyDay(today)) "Today" else "Today - nothing scheduled") }

        if (Rules.isStudyDay(today)) {
            items(Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") == "pending" }) { session ->
                PendingCard(session, todayRow?.noteOf(session).orEmpty(), onAction)
            }
            items(Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") != "pending" }) { session ->
                SettledRow(session, todayRow!!.statusOf(session), todayRow.noteOf(session)) {
                    onAction(session, "edit", null)
                }
            }
        }

        item { SectionTitle("Streaks - ${month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))}") }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { month = month.minusMonths(1) }) { Text("Previous") }
                OutlinedButton(
                    onClick = { month = month.plusMonths(1) },
                    enabled = month < YearMonth.from(today),
                ) { Text("Next") }
            }
        }
        item { MonthGrid(month, rows, today) }

        item { SectionTitle("Completed lessons") }
        val completed = rows.values
            .flatMap { r -> Session.entries.filter { r.statusOf(it) == "done" }.map { r to it } }
            .sortedByDescending { it.first.logDate }
        if (completed.isEmpty()) {
            item { Text("Nothing completed yet.", color = Color.Gray, fontSize = 13.sp) }
        } else {
            items(completed) { (row, session) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${row.logDate} - ${session.label}", fontSize = 14.sp)
                    row.noteOf(session)?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onFixAlarms) { Text("Reminder not arriving on time? Check alarm permission") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun PendingCard(session: Session, initialNote: String, onAction: (Session, String, String?) -> Unit) {
    var note by remember(session) { mutableStateOf(initialNote) }
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(session.label, fontWeight = FontWeight.SemiBold)
            Text(session.detail, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                placeholder = { Text("What did you cover? (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAction(session, "done", note) }, modifier = Modifier.weight(1f)) {
                    Text("Mark done")
                }
                OutlinedButton(onClick = { onAction(session, "cancel", null) }, modifier = Modifier.weight(1f)) {
                    Text("Incomplete")
                }
            }
        }
    }
}

@Composable
private fun SettledRow(session: Session, status: String, note: String?, onEdit: () -> Unit) {
    Card {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(session.label, fontWeight = FontWeight.Medium)
                note?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = Color.Gray)
                }
                Text(
                    if (status == "done") "Done" else "Incomplete",
                    fontSize = 12.sp,
                    color = if (status == "done") GREEN else RED,
                )
            }
            TextButton(onClick = onEdit) { Text("Edit") }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, rows: Map<String, LogRow>, today: LocalDate) {
    val firstTracked = rows.keys.minOrNull()
    val lead = (month.atDay(1).dayOfWeek.value + 6) % 7

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth().height(((month.lengthOfMonth() + lead + 6) / 7 * 44).dp),
        userScrollEnabled = false,
    ) {
        items(lead) { Box(Modifier.padding(2.dp).height(40.dp)) }
        items((1..month.lengthOfMonth()).toList()) { day ->
            val date = month.atDay(day)
            val key = date.toString()
            val row = rows[key]
            val colour = when {
                date > today -> MUTED
                firstTracked != null && key < firstTracked -> MUTED
                !Rules.isStudyDay(date) -> MUTED
                row == null -> RED
                Session.entries.all { row.statusOf(it) == "done" } -> GREEN
                date == today && Session.entries.any { row.statusOf(it) == "pending" } -> MUTED
                Session.entries.count { row.statusOf(it) == "done" } == 1 -> AMBER
                else -> RED
            }
            Box(
                Modifier.padding(2.dp).height(40.dp).clip(RoundedCornerShape(6.dp)).background(colour),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.toString(),
                    fontSize = 12.sp,
                    color = if (colour == MUTED) Color.Gray else Color.White,
                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
