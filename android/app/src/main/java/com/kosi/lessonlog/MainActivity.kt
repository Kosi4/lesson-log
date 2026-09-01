package com.kosi.lessonlog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val rows = MutableStateFlow<Map<String, LogRow>>(emptyMap())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.ensureChannels(this)
        Reminders.scheduleAll(this)

        setContent {
            LessonLogTheme {
                val data by rows.collectAsState()
                HomeScreen(data, ::submit, ::openExactAlarmSettings)
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
            runCatching { LessonApi.fetchRows() }
                .onSuccess { list -> rows.value = list.associateBy { it.logDate } }
        }
    }

    private fun submit(session: Session, action: String, note: String?) {
        lifecycleScope.launch {
            val date = Rules.today().toString()
            LessonApi.act(date, session, action, note)
            if (action == "done" || action == "cancel") Reminders.cancel(this@MainActivity, session, date)
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

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }
}

@Composable
private fun HomeScreen(
    rows: Map<String, LogRow>,
    onAction: (Session, String, String?) -> Unit,
    onFixAlarms: () -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(Rules.today())) }
    val today = Rules.today()
    val todayRow = rows[today.toString()]

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 16.dp),
    ) {
        item {
            Spacer(Modifier.height(20.dp))
            Text("Lesson log", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
            Spacer(Modifier.height(20.dp))
        }

        item {
            val done = rows.values.sumOf { r -> Session.entries.count { r.statusOf(it) == "done" } }
            val monthKey = today.toString().substring(0, 7)
            val thisMonth = rows.values
                .filter { it.logDate.startsWith(monthKey) }
                .sumOf { r -> Session.entries.count { r.statusOf(it) == "done" } }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat("Streak", Rules.computeStreak(rows).toString(), Modifier.weight(1f))
                Stat("This month", thisMonth.toString(), Modifier.weight(1f))
                Stat("Total done", done.toString(), Modifier.weight(1f))
            }
        }

        item { SectionTitle("Today") }

        if (!Rules.isStudyDay(today)) {
            item { Panel { Text("No sessions scheduled today. Enjoy it.", fontSize = 13.sp, color = Palette.Muted) } }
        } else {
            val pending = Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") == "pending" }
            val settled = Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") != "pending" }

            items(pending) { session ->
                PendingCard(session, todayRow?.noteOf(session).orEmpty(), onAction)
                Spacer(Modifier.height(14.dp))
            }

            if (settled.isNotEmpty()) {
                item {
                    Panel {
                        settled.forEachIndexed { index, session ->
                            SettledRow(
                                session = session,
                                status = todayRow!!.statusOf(session),
                                note = todayRow.noteOf(session),
                                divider = index < settled.size - 1,
                                onEdit = { onAction(session, "edit", null) },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Editable until 23:59 today, then locked for good.",
                            fontSize = 12.sp,
                            color = Palette.Faint,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        item { SectionTitle("Streaks") }
        item {
            Panel {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StepButton("‹", true) { month = month.minusMonths(1) }
                        StepButton("›", month < YearMonth.from(today)) { month = month.plusMonths(1) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                MonthGrid(month, rows, today)
                Spacer(Modifier.height(12.dp))
                Legend()
            }
            Spacer(Modifier.height(14.dp))
        }

        item { SectionTitle("Completed lessons") }
        val completed = rows.values
            .flatMap { r -> Session.entries.filter { r.statusOf(it) == "done" }.map { r to it } }
            .sortedByDescending { it.first.logDate }

        item {
            Panel {
                if (completed.isEmpty()) {
                    Text(
                        "Nothing completed yet. Tick a session off and it lands here.",
                        fontSize = 13.sp,
                        color = Palette.Muted,
                    )
                } else {
                    completed.forEachIndexed { index, (row, session) ->
                        CompletedRow(row, session, divider = index < completed.size - 1)
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onFixAlarms, contentPadding = PaddingValues(0.dp)) {
                Text("Reminder late? Check alarm permission", fontSize = 12.sp, color = Palette.Muted)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/* ---------- building blocks, matching the web app's cards ---------- */

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Surface)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(Palette.Surface).padding(12.dp),
    ) {
        Text(label, fontSize = 12.sp, color = Palette.Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(
        text.uppercase(Locale.getDefault()),
        fontSize = 12.sp,
        color = Palette.Muted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PendingCard(session: Session, initialNote: String, onAction: (Session, String, String?) -> Unit) {
    var note by remember(session, initialNote) { mutableStateOf(initialNote) }
    Panel {
        Text(session.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(session.detail, fontSize = 12.sp, color = Palette.Faint)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = { Text("What did you cover? (optional)", fontSize = 14.sp, color = Palette.Faint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Palette.Border,
                focusedBorderColor = Palette.Ink,
                unfocusedContainerColor = Palette.Surface,
                focusedContainerColor = Palette.Surface,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAction(session, "done", note) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink, contentColor = Color.White),
            ) { Text("Mark done", fontSize = 13.sp, fontWeight = FontWeight.Medium) }

            OutlinedButton(
                onClick = { onAction(session, "cancel", null) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink),
            ) { Text("Mark incomplete", fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

@Composable
private fun SettledRow(
    session: Session,
    status: String,
    note: String?,
    divider: Boolean,
    onEdit: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(session.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 12.sp, color = Palette.Faint)
            }
        }
        Pill(status)
        TextButton(onClick = onEdit, contentPadding = PaddingValues(start = 10.dp, end = 0.dp)) {
            Text("Edit", fontSize = 12.sp, color = Palette.Muted)
        }
    }
    if (divider) HorizontalDivider(color = Palette.Hairline)
}

@Composable
private fun Pill(status: String) {
    val done = status == "done"
    Text(
        if (done) "Done" else "Incomplete",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (done) Palette.PillDoneInk else Palette.PillMissInk,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (done) Palette.PillDoneBg else Palette.PillMissBg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun CompletedRow(row: LogRow, session: Session, divider: Boolean) {
    val pretty = LocalDate.parse(row.logDate)
        .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$pretty · ${session.label}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            row.noteOf(session)?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, fontSize = 12.sp, color = Palette.Faint)
            }
        }
        Pill("done")
    }
    if (divider) HorizontalDivider(color = Palette.Hairline)
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 36.dp, height = 32.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.Ink),
    ) { Text(glyph, fontSize = 15.sp) }
}

@Composable
private fun MonthGrid(month: YearMonth, rows: Map<String, LogRow>, today: LocalDate) {
    val firstTracked = rows.keys.minOrNull()
    val lead = (month.atDay(1).dayOfWeek.value + 6) % 7
    val cells = lead + month.lengthOfMonth()
    val weeks = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    fontSize = 10.sp,
                    color = Palette.Faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in 0 until weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (slot in 0 until 7) {
                    val dayNumber = week * 7 + slot - lead + 1
                    if (dayNumber < 1 || dayNumber > month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(month.atDay(dayNumber), rows, today, firstTracked, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    rows: Map<String, LogRow>,
    today: LocalDate,
    firstTracked: String?,
    modifier: Modifier,
) {
    val key = date.toString()
    val row = rows[key]
    val doneCount = row?.let { r -> Session.entries.count { r.statusOf(it) == "done" } } ?: 0
    val anyPending = row?.let { r -> Session.entries.any { r.statusOf(it) == "pending" } } ?: true

    val background = when {
        date > today -> Palette.DayEmpty
        // Nothing was tracked before the first logged day, so nothing was missed.
        firstTracked != null && key < firstTracked -> Palette.DayEmpty
        !Rules.isStudyDay(date) -> Palette.DayOff
        doneCount == 2 -> Palette.Green
        date == today && anyPending -> Palette.DayEmpty
        doneCount == 1 -> Palette.Amber
        else -> Palette.Red
    }
    val ink = when (background) {
        Palette.Green, Palette.Red -> Color.White
        Palette.Amber -> Color(0xFF4A3A08)
        else -> Color(0xFFB0ACA4)
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .then(
                if (date == today) Modifier.border(2.dp, Palette.Ink, RoundedCornerShape(7.dp))
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(date.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(Palette.Green, "Both done")
        LegendItem(Palette.Amber, "One done")
        LegendItem(Palette.Red, "Missed")
        LegendItem(Palette.DayOff, "No sessions")
    }
}

@Composable
private fun LegendItem(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(colour))
        Text(label, fontSize = 11.sp, color = Palette.Muted)
    }
}
