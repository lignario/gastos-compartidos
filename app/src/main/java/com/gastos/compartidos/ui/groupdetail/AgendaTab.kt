package com.gastos.compartidos.ui.groupdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.R
import com.gastos.compartidos.data.HouseTask
import com.gastos.compartidos.data.RECURRENCE_OPTIONS
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

private fun formatDay(date: Date): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(date)

/** (año, mes 0-based, día) de un Date, para comparar por día. */
private fun Date.ymd(): Triple<Int, Int, Int> {
    val c = Calendar.getInstance().apply { time = this@ymd }
    return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}

@Composable
fun AgendaTab(
    tasks: List<HouseTask>,
    group: Group?,
    onComplete: (HouseTask) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (HouseTask) -> Unit,
) {
    val today = remember { Calendar.getInstance() }
    var shownYear by rememberSaveable { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var shownMonth by rememberSaveable { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    val monthNames = remember { DateFormatSymbols.getInstance().months }

    val now = System.currentTimeMillis()
    val overdue = tasks
        .filter { it.nextDue != null && it.nextDue.toDate().time <= now }
        .sortedBy { it.nextDue }
    val upcoming = tasks
        .filter {
            val due = it.nextDue?.toDate()?.time
            due != null && due > now && due <= now + 30L * 86_400_000
        }
        .sortedBy { it.nextDue }

    // Actividades hechas en el mes mostrado, por día.
    val doneByDay = tasks
        .mapNotNull { t -> t.date?.toDate()?.ymd()?.let { it to t } }
        .filter { (ymd, _) -> ymd.first == shownYear && ymd.second == shownMonth }
        .groupBy({ it.first.third }, { it.second })
    // Días del mes mostrado en los que vence algo.
    val dueDays = tasks
        .mapNotNull { it.nextDue?.toDate()?.ymd() }
        .filter { it.first == shownYear && it.second == shownMonth }
        .map { it.third }
        .toSet()

    val memberName: (String) -> String = { id -> group?.memberNames?.get(id) ?: "?" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (overdue.isNotEmpty()) {
            item(key = "overdue-title") {
                Text(stringResource(R.string.overdue_tasks), style = MaterialTheme.typography.titleMedium)
            }
            items(overdue, key = { "over-${it.id}" }) { task ->
                DueTaskCard(
                    task = task,
                    memberName = memberName,
                    overdue = true,
                    onComplete = { onComplete(task) },
                    onDelete = { onDelete(task.id) },
                    onEdit = { onEdit(task) },
                )
            }
        }
        if (upcoming.isNotEmpty()) {
            item(key = "upcoming-title") {
                Text(
                    stringResource(R.string.upcoming_30_days),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(upcoming, key = { "up-${it.id}" }) { task ->
                DueTaskCard(
                    task = task,
                    memberName = memberName,
                    overdue = false,
                    onComplete = { onComplete(task) },
                    onDelete = { onDelete(task.id) },
                    onEdit = { onEdit(task) },
                )
            }
        }

        item(key = "calendar") {
            MonthCalendar(
                year = shownYear,
                month = shownMonth,
                markedDays = doneByDay.keys,
                dueDays = dueDays,
                selectedDay = selectedDay,
                onDaySelected = { selectedDay = it },
                onPrev = {
                    selectedDay = null
                    if (shownMonth == 0) { shownMonth = 11; shownYear-- } else shownMonth--
                },
                onNext = {
                    selectedDay = null
                    if (shownMonth == 11) { shownMonth = 0; shownYear++ } else shownMonth++
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        val listed = selectedDay?.let { day -> doneByDay[day].orEmpty() }
            ?: doneByDay.values.flatten().sortedByDescending { it.date }
        item(key = "history-title") {
            Text(
                selectedDay?.let { stringResource(R.string.activities_on_day, it, monthNames[shownMonth].lowercase()) }
                    ?: stringResource(R.string.activities_in_month, monthNames[shownMonth].lowercase()),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (listed.isEmpty()) {
            item(key = "history-empty") {
                Text(
                    stringResource(R.string.no_activities_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        items(listed, key = { "hist-${it.id}" }) { task ->
            HistoryTaskCard(
                task = task,
                memberName = memberName,
                onDelete = { onDelete(task.id) },
                onEdit = { onEdit(task) },
            )
        }
    }
}

@Composable
private fun DueTaskCard(
    task: HouseTask,
    memberName: (String) -> String,
    overdue: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        onClick = onEdit,
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                task.nextDue?.toDate()?.let {
                    Text(
                        if (overdue) stringResource(R.string.was_due_on, formatDay(it))
                        else stringResource(R.string.due_on, formatDay(it)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(
                        R.string.last_time,
                        memberName(task.memberId),
                        task.date?.toDate()?.let { " · ${formatDay(it)}" } ?: "",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(onClick = onComplete) { Text(stringResource(R.string.done_today)) }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun HistoryTaskCard(
    task: HouseTask,
    memberName: (String) -> String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall)
                if (task.notes.isNotBlank()) {
                    Text(task.notes, style = MaterialTheme.typography.bodySmall)
                }
                val recurrenceText = if (task.recurDays > 0) {
                    " · ${stringResource(R.string.repeats_every_days, task.recurDays)}"
                } else ""
                Text(
                    memberName(task.memberId) +
                        (task.date?.toDate()?.let { " · ${formatDay(it)}" } ?: "") +
                        recurrenceText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(
    year: Int,
    month: Int,
    markedDays: Set<Int>,
    dueDays: Set<Int>,
    selectedDay: Int?,
    onDaySelected: (Int?) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cal = Calendar.getInstance().apply { clear(); set(year, month, 1) }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // Lunes = 0 ... Domingo = 6
    val firstOffset = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val todayCal = Calendar.getInstance()
    val todayDay =
        if (todayCal.get(Calendar.YEAR) == year && todayCal.get(Calendar.MONTH) == month)
            todayCal.get(Calendar.DAY_OF_MONTH) else -1
    val dateSymbols = remember { DateFormatSymbols.getInstance() }
    val monthNames = dateSymbols.months
    val weekDays = listOf(2, 3, 4, 5, 6, 7, 1).map { index ->
        dateSymbols.shortWeekdays[index].take(1).uppercase()
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    "${monthNames[month]} $year",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_month))
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val totalCells = firstOffset + daysInMonth
            val weeks = (totalCells + 6) / 7
            var day = 1
            repeat(weeks) { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = week * 7 + col
                        if (cellIndex < firstOffset || day > daysInMonth) {
                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val thisDay = day
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            thisDay == selectedDay -> MaterialTheme.colorScheme.primary
                                            thisDay == todayDay -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .clickable {
                                        onDaySelected(if (selectedDay == thisDay) null else thisDay)
                                    },
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$thisDay",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (thisDay == selectedDay) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (thisDay in markedDays) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (thisDay == selectedDay) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary
                                                    ),
                                            )
                                        }
                                        if (thisDay in dueDays) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error),
                                            )
                                        }
                                    }
                                }
                            }
                            day++
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text(" ${stringResource(R.string.done_legend)}   ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                Text(" ${stringResource(R.string.due_legend)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTaskDialog(
    group: Group,
    currentMemberId: String,
    existing: HouseTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        notes: String,
        memberId: String,
        dateMillis: Long,
        recurDays: Long,
        nextDueMillis: Long?,
    ) -> Unit,
) {
    val presetDays = remember { RECURRENCE_OPTIONS.map { it.second }.toSet() }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(existing?.notes ?: "") }
    var memberId by rememberSaveable {
        mutableStateOf(
            existing?.memberId
                ?: currentMemberId.ifBlank { group.memberNames.keys.firstOrNull().orEmpty() }
        )
    }
    var dateMillis by rememberSaveable {
        mutableLongStateOf(existing?.date?.toDate()?.time ?: System.currentTimeMillis())
    }
    var recurDays by rememberSaveable {
        mutableLongStateOf(
            if (existing != null && existing.recurDays in presetDays) existing.recurDays else 0L
        )
    }
    // Fecha personalizada de la próxima vez que toca (null si se usa un ciclo fijo).
    var customDueMillis by rememberSaveable {
        mutableStateOf(
            if (existing != null && existing.recurDays !in presetDays)
                existing.nextDue?.toDate()?.time
            else null
        )
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDuePicker by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing != null) stringResource(R.string.edit_activity) else stringResource(R.string.add_activity)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.activity_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.activity_detail_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.who),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.memberNames.forEach { (id, name) ->
                        FilterChip(
                            selected = id == memberId,
                            onClick = { memberId = id },
                            label = { Text(name) },
                        )
                    }
                }
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(stringResource(R.string.date_format, formatDay(Date(dateMillis))))
                }
                Text(
                    stringResource(R.string.when_repeats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val recurrenceLabels = listOf(
                        R.string.does_not_repeat,
                        R.string.every_week,
                        R.string.every_10_days,
                        R.string.every_15_days,
                        R.string.every_month,
                        R.string.every_3_months,
                        R.string.every_6_months,
                        R.string.every_year,
                    )
                    RECURRENCE_OPTIONS.forEachIndexed { index, (_, days) ->
                        FilterChip(
                            selected = recurDays == days && customDueMillis == null,
                            onClick = {
                                recurDays = days
                                customDueMillis = null
                            },
                            label = { Text(stringResource(recurrenceLabels[index])) },
                        )
                    }
                    FilterChip(
                        selected = customDueMillis != null,
                        onClick = { showDuePicker = true },
                        label = {
                            Text(
                                customDueMillis?.let { stringResource(R.string.next_date_format, formatDay(Date(it))) }
                                    ?: stringResource(R.string.choose_date)
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank() && memberId.isNotBlank()) {
                    onConfirm(
                        title.trim(), notes.trim(), memberId, dateMillis,
                        if (customDueMillis != null) 0L else recurDays,
                        customDueMillis,
                    )
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateMillis = it }
                    showDatePicker = false
                }) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDuePicker) {
        val duePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customDueMillis
                ?: (System.currentTimeMillis() + 7L * 86_400_000)
        )
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    duePickerState.selectedDateMillis?.let { customDueMillis = it }
                    showDuePicker = false
                }) { Text(stringResource(R.string.accept)) }
            },
            dismissButton = {
                TextButton(onClick = { showDuePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = duePickerState)
        }
    }
}
