package com.gastos.compartidos.ui.groupdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.R
import com.gastos.compartidos.data.Entry
import com.gastos.compartidos.data.EntryType
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupType
import com.gastos.compartidos.data.GroupTotals
import com.gastos.compartidos.data.HouseTask
import com.gastos.compartidos.data.Note
import com.gastos.compartidos.data.MemberBalance
import com.gastos.compartidos.domain.BalanceCalculator
import kotlinx.coroutines.launch
import com.gastos.compartidos.ui.common.CurrencyDropdown
import com.gastos.compartidos.ui.common.MemberDropdown
import com.gastos.compartidos.ui.common.formatAmount
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (String) -> Unit,
    viewModel: GroupDetailViewModel = viewModel(key = groupId) { GroupDetailViewModel(groupId) },
) {
    val group by viewModel.group.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val balances by viewModel.balances.collectAsState()
    val totals by viewModel.totals.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettleUp by rememberSaveable { mutableStateOf(false) }
    var showPeople by rememberSaveable { mutableStateOf(false) }
    var showRenameGroup by rememberSaveable { mutableStateOf(false) }
    var showAddTask by rememberSaveable { mutableStateOf(false) }
    // Tarea en edición (no saveable: se pierde al rotar, aceptable).
    var taskToEdit by remember { mutableStateOf<HouseTask?>(null) }
    var showAddNote by rememberSaveable { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<Note?>(null) }
    val notes by viewModel.notes.collectAsState()
    val isAgenda = group?.type == GroupType.ACTIVITIES
    val isNotes = group?.type == GroupType.NOTES
    // Cotizaciones por moneda para convertir saldos a la moneda base.
    val rateInputs = remember { mutableStateMapOf<String, String>() }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showRenameGroup = true }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_group))
                    }
                    IconButton(onClick = { showPeople = true }) {
                        Icon(Icons.Default.Person, contentDescription = stringResource(R.string.people))
                    }
                    IconButton(onClick = {
                        val g = group ?: return@IconButton
                        val link = "https://lignario.github.io/join?code=${g.inviteCode}"
                        val sendIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                context.getString(
                                    when (g.type) {
                                        GroupType.ACTIVITIES -> R.string.invite_agenda_text
                                        GroupType.NOTES -> R.string.invite_notes_text
                                        else -> R.string.invite_expense_text
                                    },
                                    g.name,
                                    link,
                                ),
                            )
                            type = "text/plain"
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(sendIntent, context.getString(R.string.invite_group))
                        )
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.invite))
                    }
                },
            )
        },
        floatingActionButton = {
            if (isNotes) {
                ExtendedFloatingActionButton(
                    onClick = { showAddNote = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.new_note)) },
                )
            } else if (isAgenda) {
                ExtendedFloatingActionButton(
                    onClick = { showAddTask = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_activity)) },
                )
            } else if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.add_expense)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            if (isNotes) {
                // Los blocs de notas solo tienen la lista de notas.
                NotesTab(
                    notes = notes,
                    group = group,
                    onOpen = { noteToEdit = it },
                    onDelete = viewModel::deleteNote,
                )
            } else if (isAgenda) {
                // Las agendas de actividades solo tienen el calendario.
                AgendaTab(
                    tasks = tasks,
                    group = group,
                    onComplete = viewModel::completeTask,
                    onDelete = viewModel::deleteTask,
                    onEdit = { taskToEdit = it },
                )
            } else {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.expenses)) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.balances)) })
                    Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.totals)) })
                }
                when (tab) {
                    0 -> EntriesTab(
                        entries = entries,
                        group = group,
                        onDelete = viewModel::deleteEntry,
                        onEdit = onEditExpense,
                    )
                    1 -> BalancesTab(
                        balances = balances,
                        currentMemberId = AuthRepository.currentUser?.uid?.let { group?.memberIdOf(it) },
                        baseCurrency = group?.defaultCurrency ?: "UYU",
                        rateInputs = rateInputs,
                        onSettleUp = { showSettleUp = true },
                    )
                    2 -> TotalsTab(
                        totals = totals,
                        baseCurrency = group?.defaultCurrency ?: "UYU",
                        rateInputs = rateInputs,
                    )
                }
            }
        }
    }

    val g = group
    if (showSettleUp && g != null) {
        SettleUpDialog(
            group = g,
            onDismiss = { showSettleUp = false },
            onConfirm = { amount, currency, from, to ->
                showSettleUp = false
                viewModel.registerPayment(amount, currency, from, to)
            },
        )
    }
    if (showPeople && g != null) {
        PeopleDialog(
            group = g,
            onDismiss = { showPeople = false },
            onAddPerson = viewModel::addPerson,
            onRenamePerson = viewModel::renamePerson,
        )
    }
    if (showAddNote || noteToEdit != null) {
        val editingNote = noteToEdit
        NoteEditorDialog(
            existing = editingNote,
            onDismiss = {
                showAddNote = false
                noteToEdit = null
            },
            onConfirm = { title, content ->
                if (editingNote != null) {
                    viewModel.updateNote(editingNote.id, title, content)
                } else {
                    viewModel.addNote(title, content)
                }
                showAddNote = false
                noteToEdit = null
            },
        )
    }
    if ((showAddTask || taskToEdit != null) && g != null) {
        val editing = taskToEdit
        AddTaskDialog(
            group = g,
            currentMemberId = AuthRepository.currentUser?.uid?.let { g.memberIdOf(it) }.orEmpty(),
            existing = editing,
            onDismiss = {
                showAddTask = false
                taskToEdit = null
            },
            onConfirm = { title, notes, memberId, dateMillis, recurDays, nextDueMillis ->
                if (editing != null) {
                    viewModel.updateTask(editing, title, notes, memberId, dateMillis, recurDays, nextDueMillis)
                } else {
                    viewModel.addTask(title, notes, memberId, dateMillis, recurDays, nextDueMillis)
                }
                showAddTask = false
                taskToEdit = null
            },
        )
    }
    if (showRenameGroup && g != null) {
        GroupSettingsDialog(
            initialName = g.name,
            initialCurrency = g.defaultCurrency,
            showCurrency = !isAgenda && !isNotes,
            onDismiss = { showRenameGroup = false },
            onConfirm = { name, currency ->
                viewModel.updateGroupSettings(name, currency)
                showRenameGroup = false
            },
        )
    }
}

/** Diálogo para editar el nombre y la moneda por defecto del grupo. */
@Composable
private fun GroupSettingsDialog(
    initialName: String,
    initialCurrency: String,
    showCurrency: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (name: String, currency: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var currency by rememberSaveable { mutableStateOf(initialCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name)) },
                    singleLine = true,
                )
                if (showCurrency) {
                    com.gastos.compartidos.ui.common.CurrencyChips(
                        selected = currency,
                        onSelected = { currency = it },
                        label = stringResource(R.string.default_currency),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim(), currency) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

/** Diálogo genérico para editar un nombre. */
@Composable
private fun RenameDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (value.isNotBlank()) onConfirm(value.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun PeopleDialog(
    group: Group,
    onDismiss: () -> Unit,
    onAddPerson: (String) -> Unit,
    onRenamePerson: (memberId: String, name: String) -> Unit,
) {
    var newName by rememberSaveable { mutableStateOf("") }
    // memberId de la persona que se está renombrando, o null.
    var personToRename by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_people)) },
        text = {
            Column {
                group.memberNames.forEach { (memberId, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        Text(
                            if (memberId in group.memberClaims) stringResource(R.string.with_account) else stringResource(R.string.without_account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        IconButton(onClick = { personToRename = memberId }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.rename),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.add_person)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onAddPerson(newName)
                                newName = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                    }
                }
                Text(
                    stringResource(R.string.guest_people_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )

    personToRename?.let { memberId ->
        RenameDialog(
            title = stringResource(R.string.rename_person),
            label = stringResource(R.string.name),
            initialValue = group.memberNames[memberId].orEmpty(),
            onDismiss = { personToRename = null },
            onConfirm = {
                onRenamePerson(memberId, it)
                personToRename = null
            },
        )
    }
}

@Composable
private fun EntriesTab(
    entries: List<Entry>,
    group: Group?,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_expenses_add_first))
        }
        return
    }
    var entryToDelete by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(entries, key = { it.id }) { entry ->
            EntryCard(
                entry = entry,
                memberName = { memberId -> group?.memberNames?.get(memberId) ?: "?" },
                onDelete = { entryToDelete = entry.id },
                onEdit = { onEdit(entry.id) },
            )
        }
    }

    entryToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text(stringResource(R.string.delete_entry)) },
            text = { Text(stringResource(R.string.delete_entry_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(id)
                    entryToDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun EntryCard(
    entry: Entry,
    memberName: (String) -> String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val isExpense = entry.type == EntryType.EXPENSE
    Card(
        onClick = { if (isExpense) onEdit() },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (entry.type == EntryType.PAYMENT) {
                    Text(
                        stringResource(R.string.payment_to, memberName(entry.paidBy), memberName(entry.paidTo)),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else {
                    Text(entry.description, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.paid_by_count, memberName(entry.paidBy), entry.participants.size),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                entry.createdAt?.toDate()?.let {
                    Text(
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            Text(
                formatAmount(entry.amount, entry.currency),
                style = MaterialTheme.typography.titleMedium,
            )
            if (isExpense) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
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
private fun BalancesTab(
    balances: Map<String, List<MemberBalance>>,
    currentMemberId: String?,
    baseCurrency: String,
    rateInputs: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    onSettleUp: () -> Unit,
) {
    val currencies = balances.keys.toList()
    // Convertimos siempre que haya gastos fuera de la moneda base del grupo.
    val needsConversion = currencies.any { it != baseCurrency }
    // Cuando hay varias monedas, convertimos a la base (default del grupo).
    val otherCurrencies = if (needsConversion) currencies.filter { it != baseCurrency }.sorted() else emptyList()
    val rates = otherCurrencies.mapNotNull { c ->
        rateInputs[c]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }?.let { c to it }
    }.toMap()
    val missingRates = otherCurrencies.filter { it !in rates }
    val converted =
        if (needsConversion && missingRates.isEmpty())
            BalanceCalculator.convertedBalances(balances, baseCurrency, rates)
        else emptyList()
    val convertedSettlements = BalanceCalculator.suggestedSettlements(converted)

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loadingRates by remember { mutableStateOf(false) }
    var ratesError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (balances.isEmpty()) {
            item { Text(stringResource(R.string.no_movements)) }
        }

        // Total unificado en la moneda base, solo cuando hay varias monedas.
        if (needsConversion) {
            item(key = "convert-title") {
                Text(
                    stringResource(R.string.total_to_settle, baseCurrency),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "convert-rates") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.rate_explanation, baseCurrency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        otherCurrencies.forEach { currency ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.rate_equation, currency), modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = rateInputs[currency] ?: "",
                                    onValueChange = { rateInputs[currency] = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.rate_example)) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                    ),
                                    suffix = { Text(baseCurrency) },
                                    modifier = Modifier.width(160.dp),
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                ratesError = null
                                loadingRates = true
                                scope.launch {
                                    val fetched = com.gastos.compartidos.data.ExchangeRateService
                                        .fetchRatesToBase(baseCurrency, otherCurrencies)
                                    if (fetched == null) {
                                        ratesError = context.getString(R.string.rate_fetch_failed)
                                    } else {
                                        fetched.forEach { (c, r) ->
                                            rateInputs[c] = formatRate(r)
                                        }
                                    }
                                    loadingRates = false
                                }
                            },
                            enabled = !loadingRates,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(if (loadingRates) stringResource(R.string.searching) else stringResource(R.string.fetch_daily_rate))
                        }
                        ratesError?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (missingRates.isNotEmpty()) {
                item(key = "convert-hint") {
                    Text(
                        stringResource(R.string.missing_rates_hint, missingRates.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                settlementCards(convertedSettlements, baseCurrency, keyPrefix = "cs", totalPhrase = true)
            }
            item(key = "detail-title") {
                Text(
                    stringResource(R.string.detail_by_currency),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        balances.forEach { (currency, memberBalances) ->
            item(key = "header-$currency") {
                Text(
                    currency,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "balances-$currency") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        memberBalances.forEach { balance ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(
                                    if (balance.uid == currentMemberId) stringResource(R.string.you_suffix, balance.name) else balance.name,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatAmount(balance.amount, currency),
                                    color = when {
                                        balance.amount > 0.005 -> MaterialTheme.colorScheme.primary
                                        balance.amount < -0.005 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "settle-title-$currency") {
                Text(
                    stringResource(R.string.how_to_settle),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            settlementCards(
                BalanceCalculator.suggestedSettlements(memberBalances),
                currency,
                keyPrefix = "s-$currency",
            )
        }

        // Saldo total por persona en la moneda base, sumando todas las monedas.
        if (needsConversion && missingRates.isEmpty() && converted.isNotEmpty()) {
            item(key = "converted-balances-title") {
                Text(
                    stringResource(R.string.total_balance_in, baseCurrency),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
            item(key = "converted-balances") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        converted.forEach { balance ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(
                                    if (balance.uid == currentMemberId) stringResource(R.string.you_suffix, balance.name) else balance.name,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    formatAmount(balance.amount, baseCurrency),
                                    color = when {
                                        balance.amount > 0.005 -> MaterialTheme.colorScheme.primary
                                        balance.amount < -0.005 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        item(key = "settle-up-button") {
            OutlinedButton(
                onClick = onSettleUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.register_payment))
            }
        }
    }
}

/**
 * Lista de transferencias sugeridas como frases destacadas
 * ("X le debe a Y / monto"), o un cartel de "saldado" si no hay deudas.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.settlementCards(
    settlements: List<com.gastos.compartidos.data.Settlement>,
    currency: String,
    keyPrefix: String,
    totalPhrase: Boolean = false,
) {
    if (settlements.isEmpty()) {
        item(key = "$keyPrefix-settled") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.accounts_settled),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    } else {
        items(settlements, key = { "$keyPrefix-${it.fromUid}-${it.toUid}" }) { s ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (totalPhrase) stringResource(R.string.owes_total, s.fromName, s.toName)
                        else stringResource(R.string.owes, s.fromName, s.toName),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        formatAmount(s.amount, currency),
                        style = if (totalPhrase) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Formatea una tasa de cambio con hasta 4 decimales, sin ceros sobrantes. */
private fun formatRate(rate: Double): String {
    val s = String.format(java.util.Locale.US, "%.4f", rate).trimEnd('0').trimEnd('.')
    return s.ifEmpty { "0" }
}

@Composable
private fun TotalsTab(
    totals: Map<String, GroupTotals>,
    baseCurrency: String,
    rateInputs: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
) {
    val currencies = totals.keys.toList()
    val otherCurrencies = currencies.filter { it != baseCurrency }.sorted()
    val rates = otherCurrencies.mapNotNull { currency ->
        rateInputs[currency]?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it > 0 }
            ?.let { currency to it }
    }.toMap()
    val missingRates = otherCurrencies.filter { it !in rates }
    val converted = if (otherCurrencies.isNotEmpty() && missingRates.isEmpty()) {
        BalanceCalculator.convertedTotals(totals, baseCurrency, rates)
    } else null
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var loadingRates by remember { mutableStateOf(false) }
    var ratesError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (totals.isEmpty()) {
            item { Text(stringResource(R.string.no_expenses)) }
        }

        if (otherCurrencies.isNotEmpty()) {
            item(key = "totals-convert-title") {
                Text(
                    stringResource(R.string.total_spending_in, baseCurrency),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item(key = "totals-convert-rates") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.rate_explanation, baseCurrency),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        otherCurrencies.forEach { currency ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.rate_equation, currency), modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = rateInputs[currency] ?: "",
                                    onValueChange = { rateInputs[currency] = it },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.rate_example)) },
                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                                    ),
                                    suffix = { Text(baseCurrency) },
                                    modifier = Modifier.width(160.dp),
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                ratesError = null
                                loadingRates = true
                                scope.launch {
                                    val fetched = com.gastos.compartidos.data.ExchangeRateService
                                        .fetchRatesToBase(baseCurrency, otherCurrencies)
                                    if (fetched == null) {
                                        ratesError = context.getString(R.string.rate_fetch_failed)
                                    } else {
                                        fetched.forEach { (currency, rate) ->
                                            rateInputs[currency] = formatRate(rate)
                                        }
                                    }
                                    loadingRates = false
                                }
                            },
                            enabled = !loadingRates,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(if (loadingRates) stringResource(R.string.searching) else stringResource(R.string.fetch_daily_rate))
                        }
                        ratesError?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (missingRates.isNotEmpty()) {
                item(key = "totals-convert-hint") {
                    Text(
                        stringResource(R.string.missing_rates_hint, missingRates.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else if (converted != null) {
                item(key = "totals-converted") {
                    TotalsCard(converted, baseCurrency, showGroupTotal = true)
                }
            }
            item(key = "totals-detail-title") {
                Text(
                    stringResource(R.string.detail_by_currency),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        totals.forEach { (currency, groupTotals) ->
            item(key = "total-header-$currency") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.group_total),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatAmount(groupTotals.total, currency),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item(key = "total-card-$currency") { TotalsCard(groupTotals, currency) }
        }
    }
}

@Composable
private fun TotalsCard(
    totals: GroupTotals,
    currency: String,
    showGroupTotal: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (showGroupTotal) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(stringResource(R.string.group_total), modifier = Modifier.weight(1f))
                    Text(
                        formatAmount(totals.total, currency),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(stringResource(R.string.person), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.paid), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(stringResource(R.string.consumed), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            }
            totals.members.forEach { member ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(member.name, modifier = Modifier.weight(1f))
                    Text(formatAmount(member.paid, currency), modifier = Modifier.weight(1f))
                    Text(formatAmount(member.share, currency), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettleUpDialog(
    group: Group,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, currency: String, fromUid: String, toUid: String) -> Unit,
) {
    val currentMemberId = AuthRepository.currentUser?.uid?.let { group.memberIdOf(it) }.orEmpty()
    var from by rememberSaveable { mutableStateOf(currentMemberId) }
    var to by rememberSaveable {
        mutableStateOf(group.memberNames.keys.firstOrNull { it != currentMemberId } ?: "")
    }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(group.defaultCurrency) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.register_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MemberDropdown(
                    members = group.memberNames,
                    selected = from,
                    onSelected = { from = it },
                    label = stringResource(R.string.who_pays),
                )
                MemberDropdown(
                    members = group.memberNames,
                    selected = to,
                    onSelected = { to = it },
                    label = stringResource(R.string.who_receives),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.amount)) },
                    singleLine = true,
                )
                CurrencyDropdown(selected = currency, onSelected = { currency = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(',', '.').toDoubleOrNull()
                if (amount != null && amount > 0 && from.isNotBlank() && to.isNotBlank() && from != to) {
                    onConfirm(amount, currency, from, to)
                }
            }) { Text(stringResource(R.string.register)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
