package com.gastos.compartidos.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.gastos.compartidos.data.Entry
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddExpenseViewModel(
    private val groupId: String,
    private val entryId: String? = null,
) : ViewModel() {

    val group: StateFlow<Group?> = GroupRepository.observeGroup(groupId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Gasto a editar (null mientras carga o si es alta nueva). */
    val existing = MutableStateFlow<Entry?>(null)

    /** true cuando estamos editando un gasto ya existente. */
    val isEditing: Boolean get() = entryId != null

    init {
        if (entryId != null) {
            viewModelScope.launch {
                existing.value = GroupRepository.getEntry(groupId, entryId)
            }
        }
    }

    fun save(
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        participants: List<String>,
        splitType: String,
        shares: Map<String, Double>,
        date: Timestamp,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                if (entryId != null) {
                    GroupRepository.updateExpense(
                        groupId, entryId, description, amount, currency, paidBy,
                        participants, splitType, shares, date
                    )
                } else {
                    GroupRepository.addExpense(
                        groupId, description, amount, currency, paidBy,
                        participants, splitType, shares, date
                    )
                }
                onSuccess()
            } catch (e: Exception) {
                onError(e.localizedMessage ?: "No se pudo guardar el gasto")
            }
        }
    }
}
