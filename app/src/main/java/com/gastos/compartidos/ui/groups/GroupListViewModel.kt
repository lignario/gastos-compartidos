package com.gastos.compartidos.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.compartidos.data.AuthRepository
import com.gastos.compartidos.data.Group
import com.gastos.compartidos.data.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupListViewModel : ViewModel() {

    val groups: StateFlow<List<Group>> =
        (AuthRepository.currentUser?.let { GroupRepository.observeGroups(it.uid) } ?: flowOf(emptyList()))
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val message = MutableStateFlow<String?>(null)

    fun createGroup(
        name: String,
        currency: String,
        type: String,
        otherPeople: List<String> = emptyList(),
        onCreated: (String) -> Unit,
    ) {
        val user = AuthRepository.currentUser ?: return
        viewModelScope.launch {
            try {
                val id = GroupRepository.createGroup(
                    name = name,
                    currency = currency,
                    uid = user.uid,
                    userName = user.displayName ?: user.email.orEmpty(),
                    otherPeople = otherPeople,
                    type = type,
                )
                onCreated(id)
            } catch (e: Exception) {
                message.value = e.localizedMessage
            }
        }
    }

    fun renameGroup(groupId: String, name: String) {
        viewModelScope.launch {
            try {
                GroupRepository.renameGroup(groupId, name)
            } catch (e: Exception) {
                message.value = e.localizedMessage
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            try {
                GroupRepository.deleteGroup(groupId)
            } catch (e: Exception) {
                message.value = e.localizedMessage
            }
        }
    }
}
