package com.gastos.compartidos.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Timestamp
import com.gastos.compartidos.R
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.data.SplitType
import com.gastos.compartidos.ui.common.CurrencyDropdown
import com.gastos.compartidos.ui.common.MemberDropdown
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    entryId: String? = null,
    onDone: () -> Unit,
    viewModel: AddExpenseViewModel = viewModel(key = "expense-$groupId-$entryId") {
        AddExpenseViewModel(groupId, entryId)
    },
) {
    val group by viewModel.group.collectAsState()
    val existing by viewModel.existing.collectAsState()
    val currentUid = AuthRepository.currentUser?.uid.orEmpty()
    val currentMemberId = group?.memberIdOf(currentUid).orEmpty()
    val context = LocalContext.current

    var description by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable(group?.id) {
        mutableStateOf(group?.defaultCurrency ?: "EUR")
    }
    var paidBy by rememberSaveable(group?.id) { mutableStateOf(currentMemberId) }
    // Por defecto participan todos los miembros
    val excluded = remember { mutableStateOf(setOf<String>()) }
    // Tipo de división: EQUAL, EXACT o PERCENT.
    var splitType by rememberSaveable { mutableStateOf(SplitType.EQUAL) }
    // memberId -> texto del monto/porcentaje (para EXACT y PERCENT).
    val shareInputs = remember { mutableStateMapOf<String, String>() }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by rememberSaveable { mutableStateOf(false) }
    // Fecha del gasto en milisegundos (por defecto hoy).
    var dateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // Para no pisar lo que el usuario edita, precargamos una sola vez.
    var prefilled by rememberSaveable { mutableStateOf(false) }

    // Al editar: precargar los datos del gasto cuando lleguen.
    LaunchedEffect(existing, group) {
        val e = existing
        val g = group
        if (e != null && g != null && !prefilled) {
            description = e.description
            amountText = formatNumberForInput(e.amount)
            currency = e.currency
            paidBy = e.paidBy
            excluded.value = g.memberNames.keys - e.participants.toSet()
            splitType = e.splitType
            e.shares.forEach { (id, v) -> shareInputs[id] = formatNumberForInput(v) }
            e.createdAt?.toDate()?.time?.let { dateMillis = it }
            prefilled = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditing) stringResource(R.string.edit_expense) else stringResource(R.string.add_expense)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val g = group ?: return@Scaffold

        val totalAmount = amountText.replace(',', '.').toDoubleOrNull() ?: 0.0
        val participants = g.memberNames.keys.filter { it !in excluded.value }
        // Suma actual de los montos/porcentajes ingresados (para feedback).
        val sumShares = participants.sumOf {
            shareInputs[it]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.description_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                CurrencyDropdown(
                    selected = currency,
                    onSelected = { currency = it },
                    modifier = Modifier.weight(1f),
                )
            }
            MemberDropdown(
                members = g.memberNames,
                selected = paidBy,
                onSelected = { paidBy = it },
                label = stringResource(R.string.paid_by),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.date_format, formatDate(dateMillis)))
            }

            Text(
                stringResource(R.string.split_method),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    SplitType.EQUAL to stringResource(R.string.split_equal),
                    SplitType.EXACT to stringResource(R.string.split_amounts),
                    SplitType.PERCENT to "%",
                )
                options.forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = splitType == type,
                        onClick = { splitType = type },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }

            g.memberNames.forEach { (memberId, name) ->
                val included = memberId !in excluded.value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Checkbox(
                        checked = included,
                        onCheckedChange = { checked ->
                            excluded.value =
                                if (checked) excluded.value - memberId else excluded.value + memberId
                        },
                    )
                    Text(
                        if (memberId == currentMemberId) stringResource(R.string.you_suffix, name) else name,
                        modifier = Modifier.weight(1f),
                    )
                    if (included && splitType != SplitType.EQUAL) {
                        OutlinedTextField(
                            value = shareInputs[memberId] ?: "",
                            onValueChange = { shareInputs[memberId] = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            suffix = { Text(if (splitType == SplitType.PERCENT) "%" else currency) },
                            modifier = Modifier.width(130.dp),
                        )
                    }
                }
            }

            // Feedback de la suma cuando no es a partes iguales.
            if (splitType == SplitType.EXACT) {
                val ok = abs(sumShares - totalAmount) < 0.01
                Text(
                    stringResource(R.string.sum_amount_format, formatNumberForInput(sumShares), formatNumberForInput(totalAmount), currency),
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (splitType == SplitType.PERCENT) {
                val ok = abs(sumShares - 100.0) < 0.01
                Text(
                    stringResource(R.string.sum_percent_format, formatNumberForInput(sumShares)),
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = {
                    error = null
                    val amount = amountText.replace(',', '.').toDoubleOrNull()
                    when {
                        description.isBlank() -> error = context.getString(R.string.description_required)
                        amount == null || amount <= 0 -> error = context.getString(R.string.invalid_amount)
                        participants.isEmpty() -> error = context.getString(R.string.participant_required)
                        paidBy.isBlank() -> error = context.getString(R.string.payer_required)
                        else -> {
                            val shares = participants.associateWith {
                                shareInputs[it]?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
                            }
                            val validationError = when (splitType) {
                                SplitType.EXACT ->
                                    if (abs(shares.values.sum() - amount) >= 0.01)
                                        context.getString(R.string.amounts_must_match) else null
                                SplitType.PERCENT ->
                                    if (abs(shares.values.sum() - 100.0) >= 0.01)
                                        context.getString(R.string.percent_must_match) else null
                                else -> null
                            }
                            if (validationError != null) {
                                error = validationError
                            } else {
                                saving = true
                                viewModel.save(
                                    description = description,
                                    amount = amount,
                                    currency = currency,
                                    paidBy = paidBy,
                                    participants = participants,
                                    splitType = splitType,
                                    shares = if (splitType == SplitType.EQUAL) emptyMap() else shares,
                                    date = Timestamp(Date(dateMillis)),
                                    onSuccess = onDone,
                                    onError = {
                                        saving = false
                                        error = it
                                    },
                                )
                            }
                        }
                    }
                },
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp),
            ) {
                Text(if (viewModel.isEditing) stringResource(R.string.save_changes) else stringResource(R.string.save_expense))
            }
        }
    }

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
}

/** Muestra el número sin decimales si es entero (12 en vez de 12.0). */
private fun formatNumberForInput(amount: Double): String =
    if (amount == amount.toLong().toDouble()) amount.toLong().toString()
    else amount.toString()

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
