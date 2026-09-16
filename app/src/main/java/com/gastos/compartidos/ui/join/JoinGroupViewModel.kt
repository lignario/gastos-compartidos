package com.gastos.compartidos.ui.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinState(
    val loading: Boolean = true,
    val group: Group? = null,
    val joining: Boolean = false,
    val joinedGroupId: String? = null,
    val error: String? = null,
)

class JoinGroupViewModel(private val inviteCode: String) : ViewModel() {

    private val _state = MutableStateFlow(JoinState())
    val state: StateFlow<JoinState> = _state

    init {
        viewModelScope.launch {
            try {
                val group = GroupRepository.findGroupByCode(inviteCode)
                // Si ya es miembro, entrar directamente sin preguntar.
                val uid = AuthRepository.currentUser?.uid
                if (group != null && uid != null && group.memberIdOf(uid) != null) {
                    _state.update { it.copy(loading = false, group = group, joinedGroupId = group.id) }
                } else {
                    _state.update { it.copy(loading = false, group = group) }
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.localizedMessage) }
            }
        }
    }

    fun claim(memberId: String) {
        val group = _state.value.group ?: return
        val uid = AuthRepository.currentUser?.uid ?: return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            try {
                GroupRepository.claimMember(group.id, memberId, uid)
                _state.update { it.copy(joinedGroupId = group.id) }
            } catch (e: Exception) {
                _state.update { it.copy(joining = false, error = e.localizedMessage) }
            }
        }
    }

    fun joinAsNew(name: String) {
        val group = _state.value.group ?: return
        val uid = AuthRepository.currentUser?.uid ?: return
        _state.update { it.copy(joining = true, error = null) }
        viewModelScope.launch {
            try {
                GroupRepository.joinAsNewPerson(group.id, name, uid)
                _state.update { it.copy(joinedGroupId = group.id) }
            } catch (e: Exception) {
                _state.update { it.copy(joining = false, error = e.localizedMessage) }
            }
        }
    }
}
