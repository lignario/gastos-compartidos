package com.gastos.compartidos.data

import com.google.firebase.Timestamp

/**
 * Un grupo de gastos compartidos (un viaje, un piso, etc.).
 *
 * Los miembros son "personas" identificadas por un memberId propio: pueden
 * existir solo con un nombre (las agregó alguien del grupo) y más adelante un
 * usuario con cuenta puede reclamarlas ("soy Inés") al unirse con el código.
 */
/** Tipo de grupo: gastos compartidos, agenda de actividades o bloc de notas. */
object GroupType {
    const val EXPENSES = "EXPENSES"
    const val ACTIVITIES = "ACTIVITIES"
    const val NOTES = "NOTES"
}

data class Group(
    val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    /** EXPENSES (por defecto) o ACTIVITIES */
    val type: String = GroupType.EXPENSES,
    /** memberId -> nombre visible */
    val memberNames: Map<String, String> = emptyMap(),
    /** memberId -> uid de la cuenta que reclamó a esa persona */
    val memberClaims: Map<String, String> = emptyMap(),
    /** uids con cuenta vinculada, duplicado para consultar con array-contains */
    val memberIds: List<String> = emptyList(),
    val defaultCurrency: String = "UYU",
    val createdAt: Timestamp? = null,
) {
    /** memberId de la persona reclamada por este uid, si existe. */
    fun memberIdOf(uid: String): String? =
        memberClaims.entries.firstOrNull { it.value == uid }?.key

    /** Personas que todavía no están vinculadas a ninguna cuenta. */
    fun unclaimedMembers(): Map<String, String> =
        memberNames.filterKeys { it !in memberClaims }
}

/** Tipo de movimiento dentro de un grupo. */
object EntryType {
    const val EXPENSE = "EXPENSE"
    const val PAYMENT = "PAYMENT"
}

/** Cómo se reparte un gasto entre los participantes. */
object SplitType {
    const val EQUAL = "EQUAL"     // a partes iguales
    const val EXACT = "EXACT"     // shares = monto exacto por persona
    const val PERCENT = "PERCENT" // shares = porcentaje por persona
}

/**
 * Un movimiento del grupo: un gasto (lo pagó uno, se reparte entre los
 * participantes) o un pago/devolución entre dos miembros.
 * Todos los campos de persona (paidBy, participants, paidTo) son memberIds.
 */
data class Entry(
    val id: String = "",
    val type: String = EntryType.EXPENSE,
    val description: String = "",
    val amount: Double = 0.0,
    val currency: String = "UYU",
    /** memberId de quien pagó */
    val paidBy: String = "",
    /** memberIds entre los que se reparte el gasto (solo EXPENSE) */
    val participants: List<String> = emptyList(),
    /** Cómo se reparte: EQUAL (por defecto), EXACT o PERCENT. */
    val splitType: String = SplitType.EQUAL,
    /** memberId -> monto exacto o porcentaje, según splitType (vacío si EQUAL). */
    val shares: Map<String, Double> = emptyMap(),
    /** memberId de quien recibe el dinero (solo PAYMENT) */
    val paidTo: String = "",
    val createdAt: Timestamp? = null,
) {
    /** Cuánto le corresponde pagar al participante [memberId] de este gasto. */
    fun shareOf(memberId: String): Double {
        if (memberId !in participants) return 0.0
        return when (splitType) {
            SplitType.EXACT -> shares[memberId] ?: 0.0
            SplitType.PERCENT -> amount * (shares[memberId] ?: 0.0) / 100.0
            else -> if (participants.isEmpty()) 0.0 else amount / participants.size
        }
    }
}

/** Saldo neto de un miembro en una moneda: positivo = le deben, negativo = debe. */
data class MemberBalance(
    val uid: String,
    val name: String,
    val amount: Double,
)

/** Transferencia sugerida para saldar deudas. */
data class Settlement(
    val fromUid: String,
    val fromName: String,
    val toUid: String,
    val toName: String,
    val amount: Double,
)

/** Cuánto pagó y cuánto consumió un miembro (para la vista de totales). */
data class MemberTotal(
    val uid: String,
    val name: String,
    val paid: Double,
    val share: Double,
)

/** Totales de gasto del grupo en una moneda. */
data class GroupTotals(
    val total: Double,
    val members: List<MemberTotal>,
)

/**
 * Una actividad/tarea de la casa: algo que alguien hizo en una fecha
 * ("Inés limpió la cocina") y que opcionalmente se repite cada [recurDays]
 * días ("cambiar el filtro del agua cada 180 días"). Si es recurrente y está
 * activa, [nextDue] indica cuándo vuelve a tocar; al marcarla "hecha" se crea
 * una entrada nueva y se cierra la anterior (nextDue = null).
 */
data class HouseTask(
    val id: String = "",
    val title: String = "",
    val notes: String = "",
    /** memberId de quien la hizo */
    val memberId: String = "",
    /** cuándo se hizo */
    val date: Timestamp? = null,
    /** cada cuántos días se repite; 0 = no se repite */
    val recurDays: Long = 0,
    /** próxima vez que toca hacerla (solo recurrentes activas) */
    val nextDue: Timestamp? = null,
)

/** Opciones de repetición ofrecidas en el selector de tareas. */
val RECURRENCE_OPTIONS = listOf(
    "No se repite" to 0L,
    "Cada semana" to 7L,
    "Cada 10 días" to 10L,
    "Cada 15 días" to 15L,
    "Cada mes" to 30L,
    "Cada 3 meses" to 90L,
    "Cada 6 meses" to 180L,
    "Cada año" to 365L,
)

/**
 * Una nota compartida del grupo, tipo bloc de notas: cualquier integrante puede
 * ver, editar el título y el contenido, o eliminarla, y se sincroniza entre todos.
 */
data class Note(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    /** memberId de quien la creó */
    val createdBy: String = "",
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
)

/** Monedas ofrecidas en el selector (UYU primero por defecto). */
val SUPPORTED_CURRENCIES = listOf("UYU", "USD", "EUR", "ARS", "BRL", "PEN", "CLP", "MXN", "COP", "GBP")
