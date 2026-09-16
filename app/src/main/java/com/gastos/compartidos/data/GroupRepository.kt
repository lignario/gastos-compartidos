package com.gastos.compartidos.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object GroupRepository {

    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val groups get() = db.collection("groups")

    /** Grupos de los que el usuario es miembro, en tiempo real. */
    fun observeGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val registration = groups
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(Group::class.java)?.copy(id = it.id) }
                    ?.sortedByDescending { it.createdAt }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val registration = groups.document(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toObject(Group::class.java)?.copy(id = snapshot.id))
        }
        awaitClose { registration.remove() }
    }

    /**
     * Crea un grupo. El creador queda como persona ya vinculada a su cuenta;
     * el resto de nombres se crean como personas sin cuenta (reclamables).
     */
    suspend fun createGroup(
        name: String,
        currency: String,
        uid: String,
        userName: String,
        otherPeople: List<String> = emptyList(),
        type: String = GroupType.EXPENSES,
    ): String {
        val creatorMemberId = newMemberId()
        val memberNames = mutableMapOf(creatorMemberId to userName)
        for (person in otherPeople.map { it.trim() }.filter { it.isNotBlank() }) {
            memberNames[newMemberId()] = person
        }
        val doc = groups.document()
        doc.set(
            mapOf(
                "name" to name.trim(),
                "inviteCode" to generateInviteCode(),
                "type" to type,
                "memberNames" to memberNames,
                "memberClaims" to mapOf(creatorMemberId to uid),
                "memberIds" to listOf(uid),
                "defaultCurrency" to currency,
                "createdAt" to Timestamp.now(),
            )
        ).await()
        return doc.id
    }

    /** Busca un grupo por código de invitación (para el flujo de unirse). */
    suspend fun findGroupByCode(inviteCode: String): Group? {
        val snapshot = groups
            .whereEqualTo("inviteCode", inviteCode.trim().uppercase())
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull() ?: return null
        return doc.toObject(Group::class.java)?.copy(id = doc.id)
    }

    /** Agrega una persona (solo nombre, sin cuenta) al grupo. */
    suspend fun addPerson(groupId: String, name: String): String {
        val memberId = newMemberId()
        groups.document(groupId)
            .update("memberNames.$memberId", name.trim())
            .await()
        return memberId
    }

    /** Cambia el nombre del grupo. */
    suspend fun renameGroup(groupId: String, name: String) {
        groups.document(groupId).update("name", name.trim()).await()
    }

    /**
     * Elimina un grupo con todos sus movimientos y actividades (para todos
     * los miembros). Firestore no borra subcolecciones en cascada, así que
     * se limpian en lotes antes de borrar el documento.
     */
    suspend fun deleteGroup(groupId: String) {
        val ref = groups.document(groupId)
        for (sub in listOf("entries", "tasks", "notes")) {
            while (true) {
                val docs = ref.collection(sub).limit(200).get().await()
                if (docs.isEmpty) break
                val batch = db.batch()
                docs.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                if (docs.size() < 200) break
            }
        }
        ref.delete().await()
    }

    /** Cambia el nombre y la moneda por defecto del grupo. */
    suspend fun updateGroupSettings(groupId: String, name: String, defaultCurrency: String) {
        groups.document(groupId).update(
            mapOf(
                "name" to name.trim(),
                "defaultCurrency" to defaultCurrency,
            )
        ).await()
    }

    /** Cambia el nombre de una persona del grupo. */
    suspend fun renamePerson(groupId: String, memberId: String, name: String) {
        groups.document(groupId).update("memberNames.$memberId", name.trim()).await()
    }

    /** Vincula la cuenta [uid] a una persona existente del grupo ("soy Inés"). */
    suspend fun claimMember(groupId: String, memberId: String, uid: String) {
        groups.document(groupId).update(
            mapOf(
                "memberClaims.$memberId" to uid,
                "memberIds" to FieldValue.arrayUnion(uid),
            )
        ).await()
    }

    /** Se une al grupo como una persona nueva ya vinculada a su cuenta. */
    suspend fun joinAsNewPerson(groupId: String, name: String, uid: String) {
        val memberId = newMemberId()
        groups.document(groupId).update(
            mapOf(
                "memberNames.$memberId" to name.trim(),
                "memberClaims.$memberId" to uid,
                "memberIds" to FieldValue.arrayUnion(uid),
            )
        ).await()
    }

    /** Movimientos del grupo (gastos y pagos), más recientes primero. */
    fun observeEntries(groupId: String): Flow<List<Entry>> = callbackFlow {
        val registration = groups.document(groupId).collection("entries")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(Entry::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    /** Lee un movimiento concreto (para precargar la pantalla de edición). */
    suspend fun getEntry(groupId: String, entryId: String): Entry? {
        val doc = groups.document(groupId).collection("entries").document(entryId).get().await()
        return doc.toObject(Entry::class.java)?.copy(id = doc.id)
    }

    suspend fun addExpense(
        groupId: String,
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        participants: List<String>,
        splitType: String = SplitType.EQUAL,
        shares: Map<String, Double> = emptyMap(),
        date: Timestamp = Timestamp.now(),
    ) {
        groups.document(groupId).collection("entries").add(
            mapOf(
                "type" to EntryType.EXPENSE,
                "description" to description.trim(),
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "participants" to participants,
                "splitType" to splitType,
                "shares" to shares,
                "createdAt" to date,
            )
        ).await()
    }

    suspend fun addPayment(
        groupId: String,
        amount: Double,
        currency: String,
        paidBy: String,
        paidTo: String,
    ) {
        groups.document(groupId).collection("entries").add(
            mapOf(
                "type" to EntryType.PAYMENT,
                "description" to "Pago",
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "paidTo" to paidTo,
                "createdAt" to Timestamp.now(),
            )
        ).await()
    }

    /** Edita un gasto existente; los saldos se recalculan solos al cambiar los datos. */
    suspend fun updateExpense(
        groupId: String,
        entryId: String,
        description: String,
        amount: Double,
        currency: String,
        paidBy: String,
        participants: List<String>,
        splitType: String,
        shares: Map<String, Double>,
        date: Timestamp,
    ) {
        groups.document(groupId).collection("entries").document(entryId).update(
            mapOf(
                "description" to description.trim(),
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "participants" to participants,
                "splitType" to splitType,
                "shares" to shares,
                "createdAt" to date,
            )
        ).await()
    }

    /** Edita un pago existente. */
    suspend fun updatePayment(
        groupId: String,
        entryId: String,
        amount: Double,
        currency: String,
        paidBy: String,
        paidTo: String,
    ) {
        groups.document(groupId).collection("entries").document(entryId).update(
            mapOf(
                "amount" to amount,
                "currency" to currency,
                "paidBy" to paidBy,
                "paidTo" to paidTo,
            )
        ).await()
    }

    suspend fun deleteEntry(groupId: String, entryId: String) {
        groups.document(groupId).collection("entries").document(entryId).delete().await()
    }

    // ---- Agenda de la casa (tareas/actividades) ----

    /** Actividades del grupo, más recientes primero. */
    fun observeTasks(groupId: String): Flow<List<HouseTask>> = callbackFlow {
        val registration = groups.document(groupId).collection("tasks")
            .orderBy("date", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(HouseTask::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addTask(
        groupId: String,
        title: String,
        notes: String,
        memberId: String,
        date: Timestamp,
        recurDays: Long,
        nextDueOverride: Timestamp? = null,
    ) {
        // Con fecha personalizada, el ciclo pasa a ser la distancia entre la
        // fecha de la tarea y esa próxima fecha (para reprogramar con "Hecho hoy").
        val effectiveRecurDays = if (nextDueOverride != null) {
            maxOf(1L, Math.round((nextDueOverride.seconds - date.seconds) / 86_400.0))
        } else {
            recurDays
        }
        groups.document(groupId).collection("tasks").add(
            mapOf(
                "title" to title.trim(),
                "notes" to notes.trim(),
                "memberId" to memberId,
                "date" to date,
                "recurDays" to effectiveRecurDays,
                "nextDue" to (nextDueOverride ?: nextDueFrom(date, recurDays)),
            )
        ).await()
    }

    /** Edita una actividad existente. [keepClosed] preserva entradas ya cerradas. */
    suspend fun updateTask(
        groupId: String,
        taskId: String,
        title: String,
        notes: String,
        memberId: String,
        date: Timestamp,
        recurDays: Long,
        nextDueOverride: Timestamp? = null,
        keepClosed: Boolean = false,
    ) {
        val effectiveRecurDays = if (nextDueOverride != null) {
            maxOf(1L, Math.round((nextDueOverride.seconds - date.seconds) / 86_400.0))
        } else {
            recurDays
        }
        val nextDue = nextDueOverride
            ?: if (keepClosed) null else nextDueFrom(date, recurDays)
        groups.document(groupId).collection("tasks").document(taskId).update(
            mapOf(
                "title" to title.trim(),
                "notes" to notes.trim(),
                "memberId" to memberId,
                "date" to date,
                "recurDays" to effectiveRecurDays,
                "nextDue" to nextDue,
            )
        ).await()
    }

    /**
     * Marca una tarea recurrente como hecha hoy: cierra la entrada anterior
     * (deja de estar pendiente) y crea una nueva con la fecha de hoy y el
     * mismo ciclo de repetición.
     */
    suspend fun completeTask(groupId: String, task: HouseTask, byMemberId: String) {
        val tasks = groups.document(groupId).collection("tasks")
        val now = Timestamp.now()
        db.runBatch { batch ->
            batch.update(tasks.document(task.id), "nextDue", null)
            batch.set(
                tasks.document(),
                mapOf(
                    "title" to task.title,
                    "notes" to task.notes,
                    "memberId" to byMemberId,
                    "date" to now,
                    "recurDays" to task.recurDays,
                    "nextDue" to nextDueFrom(now, task.recurDays),
                ),
            )
        }.await()
    }

    suspend fun deleteTask(groupId: String, taskId: String) {
        groups.document(groupId).collection("tasks").document(taskId).delete().await()
    }

    // ---- Notas compartidas ----

    /** Notas del grupo, las modificadas más recientemente primero. */
    fun observeNotes(groupId: String): Flow<List<Note>> = callbackFlow {
        val registration = groups.document(groupId).collection("notes")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { it.toObject(Note::class.java)?.copy(id = it.id) }
                    ?: emptyList()
                trySend(list)
            }
        awaitClose { registration.remove() }
    }

    suspend fun addNote(groupId: String, title: String, content: String, memberId: String) {
        val now = Timestamp.now()
        groups.document(groupId).collection("notes").add(
            mapOf(
                "title" to title.trim(),
                "content" to content,
                "createdBy" to memberId,
                "createdAt" to now,
                "updatedAt" to now,
            )
        ).await()
    }

    suspend fun updateNote(groupId: String, noteId: String, title: String, content: String) {
        groups.document(groupId).collection("notes").document(noteId).update(
            mapOf(
                "title" to title.trim(),
                "content" to content,
                "updatedAt" to Timestamp.now(),
            )
        ).await()
    }

    suspend fun deleteNote(groupId: String, noteId: String) {
        groups.document(groupId).collection("notes").document(noteId).delete().await()
    }

    private fun nextDueFrom(date: Timestamp, recurDays: Long): Timestamp? =
        if (recurDays > 0) Timestamp(date.seconds + recurDays * 86_400, 0) else null

    private fun newMemberId(): String = db.collection("ids").document().id

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin caracteres ambiguos
        return (1..6).map { chars.random() }.joinToString("")
    }
}
