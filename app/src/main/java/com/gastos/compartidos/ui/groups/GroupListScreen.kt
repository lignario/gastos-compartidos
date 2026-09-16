package com.gastos.compartidos.ui.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.compartidos.R
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onOpenGroup: (String) -> Unit,
    onJoinWithCode: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: GroupListViewModel = viewModel(),
) {
    val groups by viewModel.groups.collectAsState()
    // 0 = grupos de gastos, 1 = agendas de actividades, 2 = blocs de notas
    var mainTab by rememberSaveable { mutableIntStateOf(0) }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showJoin by rememberSaveable { mutableStateOf(false) }
    // Grupo (id) sobre el que se abrió el menú contextual / diálogos, o null.
    var groupOptions by rememberSaveable { mutableStateOf<String?>(null) }
    var groupToRename by rememberSaveable { mutableStateOf<String?>(null) }
    var groupToDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var showLanguages by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: ""
    }

    val isActivities = mainTab == 1
    val isNotes = mainTab == 2
    val sectionType = when (mainTab) {
        1 -> GroupType.ACTIVITIES
        2 -> GroupType.NOTES
        else -> GroupType.EXPENSES
    }
    // Los grupos viejos sin campo "type" cuentan como de gastos.
    val shownGroups = groups.filter {
        (it.type.ifBlank { GroupType.EXPENSES }) == sectionType
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            when {
                                isActivities -> stringResource(R.string.activities)
                                isNotes -> stringResource(R.string.notes)
                                else -> stringResource(R.string.my_groups)
                            }
                        )
                        if (versionName.isNotEmpty()) {
                            Text(
                                stringResource(R.string.version_format, versionName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                },
                actions = {
                    Box {
                        TextButton(onClick = { showLanguages = true }) {
                            Text(
                                (AppCompatDelegate.getApplicationLocales()[0]?.language
                                    ?: context.resources.configuration.locales[0].language)
                                    .uppercase()
                            )
                        }
                        DropdownMenu(
                            expanded = showLanguages,
                            onDismissRequest = { showLanguages = false },
                        ) {
                            listOf(
                                "es" to stringResource(R.string.language_spanish),
                                "en" to stringResource(R.string.language_english),
                                "pt" to stringResource(R.string.language_portuguese),
                            ).forEach { (tag, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showLanguages = false
                                        AppCompatDelegate.setApplicationLocales(
                                            LocaleListCompat.forLanguageTags(tag)
                                        )
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        AuthRepository.signOut()
                        onSignedOut()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = stringResource(R.string.sign_out))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = mainTab == 0,
                    onClick = { mainTab = 0 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    label = { Text(stringResource(R.string.expenses)) },
                )
                NavigationBarItem(
                    selected = mainTab == 1,
                    onClick = { mainTab = 1 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text(stringResource(R.string.activities)) },
                )
                NavigationBarItem(
                    selected = mainTab == 2,
                    onClick = { mainTab = 2 },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.notes)) },
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = {
                    Text(
                        when {
                            isActivities -> stringResource(R.string.new_agenda)
                            isNotes -> stringResource(R.string.new_notebook)
                            else -> stringResource(R.string.new_group)
                        }
                    )
                },
            )
        },
    ) { padding ->
        if (shownGroups.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            isActivities -> stringResource(R.string.no_agendas)
                            isNotes -> stringResource(R.string.no_notebooks)
                            else -> stringResource(R.string.no_groups)
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        when {
                            isActivities -> stringResource(R.string.create_agenda_or_join)
                            isNotes -> stringResource(R.string.create_notebook_or_join)
                            else -> stringResource(R.string.create_or_join)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                    )
                    OutlinedButton(onClick = { showJoin = true }) {
                        Text(stringResource(R.string.join_with_code))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shownGroups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onOpenGroup(group.id) },
                        onLongClick = { groupOptions = group.id },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = { showJoin = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.join_with_code))
                    }
                }
            }
        }
    }

    if (showCreate) {
        if (isActivities || isNotes) {
            CreateAgendaDialog(
                title = if (isNotes) stringResource(R.string.new_notes_notebook) else stringResource(R.string.new_agenda),
                nameLabel = if (isNotes) stringResource(R.string.notebook_name_hint)
                else stringResource(R.string.agenda_name_hint),
                onDismiss = { showCreate = false },
                onCreate = { name, people ->
                    showCreate = false
                    viewModel.createGroup(
                        name = name,
                        currency = "UYU",
                        type = if (isNotes) GroupType.NOTES else GroupType.ACTIVITIES,
                        otherPeople = people,
                        onCreated = onOpenGroup,
                    )
                },
            )
        } else {
            CreateGroupDialog(
                onDismiss = { showCreate = false },
                onCreate = { name, currency ->
                    showCreate = false
                    viewModel.createGroup(
                        name = name,
                        currency = currency,
                        type = GroupType.EXPENSES,
                        onCreated = onOpenGroup,
                    )
                },
            )
        }
    }
    if (showJoin) {
        JoinGroupDialog(
            onDismiss = { showJoin = false },
            onJoin = { code ->
                showJoin = false
                onJoinWithCode(code.trim().uppercase())
            },
        )
    }
    groupOptions?.let { id ->
        val current = groups.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { groupOptions = null },
            title = { Text(current?.name ?: "") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            groupToRename = id
                            groupOptions = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.rename)) }
                    TextButton(
                        onClick = {
                            groupToDelete = id
                            groupOptions = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { groupOptions = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    groupToRename?.let { id ->
        val current = groups.firstOrNull { it.id == id }
        RenameGroupDialog(
            initialValue = current?.name.orEmpty(),
            onDismiss = { groupToRename = null },
            onConfirm = {
                viewModel.renameGroup(id, it)
                groupToRename = null
            },
        )
    }
    groupToDelete?.let { id ->
        val current = groups.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.delete_group_title, current?.name.orEmpty())) },
            text = {
                Text(
                    stringResource(R.string.delete_group_message)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(id)
                    groupToDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(group: Group, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(16.dp),
        ) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    when (group.type) {
                        GroupType.ACTIVITIES -> stringResource(
                            R.string.group_kind_agenda,
                            pluralStringResource(R.plurals.people_count, group.memberNames.size, group.memberNames.size),
                        )
                        GroupType.NOTES -> stringResource(
                            R.string.group_kind_notes,
                            pluralStringResource(R.plurals.people_count, group.memberNames.size, group.memberNames.size),
                        )
                        else -> stringResource(
                            R.string.group_kind_expenses,
                            pluralStringResource(R.plurals.people_count, group.memberNames.size, group.memberNames.size),
                            group.defaultCurrency,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, currency: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("UYU") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_expense_group)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                )
                com.gastos.compartidos.ui.common.CurrencyChips(
                    selected = currency,
                    onSelected = { currency = it },
                    label = stringResource(R.string.main_currency),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, currency) },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun CreateAgendaDialog(
    title: String,
    nameLabel: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, people: List<String>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var peopleText by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = peopleText,
                    onValueChange = { peopleText = it },
                    label = { Text(stringResource(R.string.additional_people_hint)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.creator_included_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name, peopleText.split(',').map { it.trim() }.filter { it.isNotBlank() })
                    }
                },
            ) { Text(stringResource(R.string.create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onJoin: (code: String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_with_code)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text(stringResource(R.string.invitation_code)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (code.isNotBlank()) onJoin(code) }) { Text(stringResource(R.string.join)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun RenameGroupDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
