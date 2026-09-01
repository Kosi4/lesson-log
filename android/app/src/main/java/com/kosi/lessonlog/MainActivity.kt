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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val colors = LessonTheme.colors
    var month by remember { mutableStateOf(YearMonth.from(Rules.today())) }
    val today = Rules.today()
    val todayRow = rows[today.toString()]

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.ground).padding(horizontal = 16.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("Lesson log", style = MaterialTheme.typography.titleLarge, color = colors.ink)
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
                Stat("Total", done.toString(), Modifier.weight(1f))
            }
        }

        item { SectionTitle("Today") }

        if (!Rules.isStudyDay(today)) {
            item {
                Panel {
                    Text(
                        "No sessions scheduled today. Enjoy it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
                    )
                }
            }
        } else {
            val pending = Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") == "pending" }
            val settled = Session.entries.filter { (todayRow?.statusOf(it) ?: "pending") != "pending" }

            items(pending) { session ->
                PendingCard(session, todayRow?.noteOf(session).orEmpty(), onAction)
                Spacer(Modifier.height(12.dp))
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
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Editable until 23:59 today, then locked for good.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkMuted,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        item { SectionTitle("Register") }
        item {
            Panel {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StepButton("‹", true) { month = month.minusMonths(1) }
                        StepButton("›", month < YearMonth.from(today)) { month = month.plusMonths(1) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                MonthGrid(month, rows, today)
                Spacer(Modifier.height(14.dp))
                Legend()
            }
            Spacer(Modifier.height(12.dp))
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
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkMuted,
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
                Text(
                    "Reminder late? Check alarm permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/* ---------- building blocks ---------- */

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LessonTheme.colors.sheet)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LessonTheme.colors
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(colors.sheet).padding(12.dp)) {
        Text(label.uppercase(Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = colors.ink)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = LessonTheme.colors.inkMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun PendingCard(session: Session, initialNote: String, onAction: (Session, String, String?) -> Unit) {
    val colors = LessonTheme.colors
    var note by remember(session, initialNote) { mutableStateOf(initialNote) }
    Panel {
        Text(session.label, style = MaterialTheme.typography.titleMedium)
        Text(session.detail, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            placeholder = {
                Text(
                    "What did you cover? (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = colors.rule,
                focusedBorderColor = colors.ink,
                unfocusedContainerColor = colors.sheet,
                focusedContainerColor = colors.sheet,
                unfocusedTextColor = colors.ink,
                focusedTextColor = colors.ink,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAction(session, "done", note) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.ground),
            ) { Text("Mark done", style = MaterialTheme.typography.labelLarge) }

            OutlinedButton(
                onClick = { onAction(session, "cancel", null) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.rule),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
            ) { Text("Mark incomplete", style = MaterialTheme.typography.labelLarge) }
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
    val colors = LessonTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(session.label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            note?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
        Pill(status)
        TextButton(onClick = onEdit, contentPadding = PaddingValues(start = 10.dp, end = 0.dp)) {
            Text("Edit", style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
    }
    if (divider) HorizontalDivider(color = colors.rule)
}

@Composable
private fun Pill(status: String) {
    val colors = LessonTheme.colors
    val done = status == "done"
    Text(
        if (done) "Done" else "Incomplete",
        style = MaterialTheme.typography.labelSmall,
        color = if (done) colors.pillDoneInk else colors.pillMissInk,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (done) colors.pillDoneBg else colors.pillMissBg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun CompletedRow(row: LogRow, session: Session, divider: Boolean) {
    val colors = LessonTheme.colors
    val pretty = LocalDate.parse(row.logDate)
        .format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("$pretty · ${session.label}", style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            row.noteOf(session)?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
        }
        Pill("done")
    }
    if (divider) HorizontalDivider(color = colors.rule)
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LessonTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(width = 40.dp, height = 36.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.rule),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
    ) { Text(glyph, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun MonthGrid(month: YearMonth, rows: Map<String, LogRow>, today: LocalDate) {
    val colors = LessonTheme.colors
    val firstTracked = rows.keys.minOrNull()
    val lead = (month.atDay(1).dayOfWeek.value + 6) % 7
    val weeks = (lead + month.lengthOfMonth() + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FiraMono),
                    color = colors.inkMuted,
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

/**
 * One day is one cell split in two: morning above, evening below. Colouring the
 * halves separately keeps which session was missed, which a single blended
 * colour threw away.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    rows: Map<String, LogRow>,
    today: LocalDate,
    firstTracked: String?,
    modifier: Modifier,
) {
    val colors = LessonTheme.colors
    val key = date.toString()
    val shape = RoundedCornerShape(8.dp)

    // Nothing was tracked before the first logged day, so nothing was missed.
    val untracked = date > today || (firstTracked != null && key < firstTracked)
    val offday = !Rules.isStudyDay(date)

    val row = rows[key]
    val am = row?.statusOf(Session.MORNING) ?: "pending"
    val pm = row?.statusOf(Session.EVENING) ?: "pending"

    fun fill(status: String) = when (status) {
        "done" -> colors.done
        "incomplete" -> colors.missed
        else -> colors.empty
    }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            // An off-day has no slot at all rather than a slightly different
            // grey one: filled against unfilled is legible where two near
            // identical neutrals are not.
            .background(if (offday) Color.Transparent else colors.empty)
            .then(if (date == today) Modifier.border(2.dp, colors.ink, shape) else Modifier),
    ) {
        if (!offday && !untracked) {
            Column(Modifier.matchParentSize()) {
                Box(Modifier.weight(1f).fillMaxWidth().background(fill(am)))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ground))
                Box(Modifier.weight(1f).fillMaxWidth().background(fill(pm)))
            }
        }
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FiraMono),
            // The numeral sits over the morning half, so its contrast is decided
            // by that half alone rather than by whatever the two blend into.
            color = when {
                offday -> colors.inkMuted.copy(alpha = 0.45f)
                untracked -> colors.inkMuted
                am == "done" || am == "incomplete" -> colors.onStatus
                else -> colors.inkMuted
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 3.dp),
        )
    }
}

@Composable
private fun Legend() {
    val colors = LessonTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendItem(colors.done, "Done")
        LegendItem(colors.missed, "Missed")
        LegendItem(colors.empty, "Not logged")
        LegendItem(Color.Transparent, "No sessions", outlined = true)
    }
}

@Composable
private fun LegendItem(colour: Color, label: String, outlined: Boolean = false) {
    val colors = LessonTheme.colors
    val shape = RoundedCornerShape(3.dp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier.size(10.dp).clip(shape).background(colour)
                .then(if (outlined) Modifier.border(1.dp, colors.rule, shape) else Modifier)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
    }
}
