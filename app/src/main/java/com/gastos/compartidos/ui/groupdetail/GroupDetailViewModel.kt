package com.gastos.compartidos.ui.groupdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.data.Entry
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupRepository
import com.gastos.compartidos.data.GroupTotals
import com.gastos.compartidos.data.HouseTask
import com.gastos.compartidos.data.MemberBalance
import com.gastos.compartidos.data.Note
import com.gastos.compartidos.domain.BalanceCalculator
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupDetailViewModel(private val groupId: String) : ViewModel() {

    val group: StateFlow<Group?> = GroupRepository.observeGroup(groupId)
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<Entry>> = GroupRepository.observeEntries(groupId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saldos por moneda, recalculados en tiempo real. */
    val balances: StateFlow<Map<String, List<MemberBalance>>> =
        combine(group, entries) { group, entries ->
            if (group == null) emptyMap()
            else BalanceCalculator.balancesByCurrency(entries, group.memberNames)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Totales de gasto por moneda (total del grupo y por persona). */
    val totals: StateFlow<Map<String, GroupTotals>> =
        combine(group, entries) { group, entries ->
            if (group == null) emptyMap()
            else BalanceCalculator.totalsByCurrency(entries, group.memberNames)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Actividades/tareas de la casa, en tiempo real. */
    val tasks: StateFlow<List<HouseTask>> = GroupRepository.observeTasks(groupId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Notas compartidas del grupo, en tiempo real. */
    val notes: StateFlow<List<Note>> = GroupRepository.observeNotes(groupId)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addNote(title: String, content: String) {
        val uid = AuthRepository.currentUser?.uid
        val memberId = uid?.let { group.value?.memberIdOf(it) }.orEmpty()
        viewModelScope.launch {
            try {
                GroupRepository.addNote(groupId, title, content, memberId)
            } catch (_: Exception) {
            }
        }
    }

    fun updateNote(noteId: String, title: String, content: String) {
        viewModelScope.launch {
            try {
                GroupRepository.updateNote(groupId, noteId, title, content)
            } catch (_: Exception) {
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            try {
                GroupRepository.deleteNote(groupId, noteId)
            } catch (_: Exception) {
            }
        }
    }

    fun addTask(
        title: String,
        notes: String,
        memberId: String,
        dateMillis: Long,
        recurDays: Long,
        nextDueMillis: Long? = null,
    ) {
        viewModelScope.launch {
            try {
                GroupRepository.addTask(
                    groupId, title, notes, memberId,
                    Timestamp(Date(dateMillis)), recurDays,
                    nextDueOverride = nextDueMillis?.let { Timestamp(Date(it)) },
                )
            } catch (_: Exception) {
            }
        }
    }

    fun updateTask(
        task: HouseTask,
        title: String,
        notes: String,
        memberId: String,
        dateMillis: Long,
        recurDays: Long,
        nextDueMillis: Long? = null,
    ) {
        viewModelScope.launch {
            try {
                GroupRepository.updateTask(
                    groupId, task.id, title, notes, memberId,
                    Timestamp(Date(dateMillis)), recurDays,
                    nextDueOverride = nextDueMillis?.let { Timestamp(Date(it)) },
                    // Si la entrada ya estaba cerrada (recurrente completada),
                    // editarla no debe volver a ponerla como pendiente.
                    keepClosed = task.nextDue == null && task.recurDays > 0,
                )
            } catch (_: Exception) {
            }
        }
    }

    fun completeTask(task: HouseTask) {
        val uid = AuthRepository.currentUser?.uid
        val byMemberId = uid?.let { group.value?.memberIdOf(it) } ?: task.memberId
        viewModelScope.launch {
            try {
                GroupRepository.completeTask(groupId, task, byMemberId)
            } catch (_: Exception) {
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                GroupRepository.deleteTask(groupId, taskId)
            } catch (_: Exception) {
            }
        }
    }

    fun addPerson(name: String) {
        viewModelScope.launch {
            try {
                GroupRepository.addPerson(groupId, name)
            } catch (_: Exception) {
            }
        }
    }

    fun updateGroupSettings(name: String, defaultCurrency: String) {
        viewModelScope.launch {
            try {
                GroupRepository.updateGroupSettings(groupId, name, defaultCurrency)
            } catch (_: Exception) {
            }
        }
    }

    fun renamePerson(memberId: String, name: String) {
        viewModelScope.launch {
            try {
                GroupRepository.renamePerson(groupId, memberId, name)
            } catch (_: Exception) {
            }
        }
    }

    fun registerPayment(amount: Double, currency: String, fromUid: String, toUid: String) {
        viewModelScope.launch {
            try {
                GroupRepository.addPayment(groupId, amount, currency, fromUid, toUid)
            } catch (_: Exception) {
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            try {
                GroupRepository.deleteEntry(groupId, entryId)
            } catch (_: Exception) {
            }
        }
    }
}
