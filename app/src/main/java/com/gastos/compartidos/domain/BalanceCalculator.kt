package com.gastos.compartidos.domain

import com.gastos.compartidos.data.Entry
import com.gastos.compartidos.data.EntryType
import com.gastos.compartidos.data.GroupTotals
import com.gastos.compartidos.data.MemberBalance
import com.gastos.compartidos.data.MemberTotal
import com.gastos.compartidos.data.Settlement
import kotlin.math.abs
import kotlin.math.min

/**
 * Calcula saldos netos y transferencias sugeridas a partir de los movimientos
 * de un grupo. Todo se calcula por moneda: no se convierten importes entre
 * monedas distintas.
 */
object BalanceCalculator {

    private const val EPSILON = 0.005

    /** Saldos netos por moneda. Positivo = le deben, negativo = debe. */
    fun balancesByCurrency(
        entries: List<Entry>,
        members: Map<String, String>,
    ): Map<String, List<MemberBalance>> {
        val byCurrency = entries.groupBy { it.currency }
        return byCurrency.mapValues { (_, currencyEntries) ->
            val net = mutableMapOf<String, Double>()
            for (entry in currencyEntries) {
                when (entry.type) {
                    EntryType.EXPENSE -> {
                        if (entry.participants.isEmpty()) continue
                        net[entry.paidBy] = (net[entry.paidBy] ?: 0.0) + entry.amount
                        for (p in entry.participants) {
                            net[p] = (net[p] ?: 0.0) - entry.shareOf(p)
                        }
                    }
                    EntryType.PAYMENT -> {
                        net[entry.paidBy] = (net[entry.paidBy] ?: 0.0) + entry.amount
                        net[entry.paidTo] = (net[entry.paidTo] ?: 0.0) - entry.amount
                    }
                }
            }
            members.map { (uid, name) ->
                MemberBalance(uid, name, net[uid] ?: 0.0)
            }.sortedByDescending { it.amount }
        }
    }

    /**
     * Simplificación de deudas (algoritmo voraz): empareja al mayor deudor con
     * el mayor acreedor hasta saldar todos los balances.
     */
    fun suggestedSettlements(balances: List<MemberBalance>): List<Settlement> {
        val creditors = balances.filter { it.amount > EPSILON }
            .map { it.copy() }.sortedByDescending { it.amount }.toMutableList()
        val debtors = balances.filter { it.amount < -EPSILON }
            .map { it.copy(amount = -it.amount) }.sortedByDescending { it.amount }.toMutableList()

        val settlements = mutableListOf<Settlement>()
        var ci = 0
        var di = 0
        while (ci < creditors.size && di < debtors.size) {
            val creditor = creditors[ci]
            val debtor = debtors[di]
            val transfer = min(creditor.amount, debtor.amount)
            if (transfer > EPSILON) {
                settlements += Settlement(
                    fromUid = debtor.uid,
                    fromName = debtor.name,
                    toUid = creditor.uid,
                    toName = creditor.name,
                    amount = transfer,
                )
            }
            creditors[ci] = creditor.copy(amount = creditor.amount - transfer)
            debtors[di] = debtor.copy(amount = debtor.amount - transfer)
            if (abs(creditors[ci].amount) <= EPSILON) ci++
            if (abs(debtors[di].amount) <= EPSILON) di++
        }
        return settlements
    }

    /**
     * Convierte los saldos de varias monedas a una sola moneda base usando las
     * cotizaciones dadas ([rates] = cuántas unidades de la base vale 1 unidad de
     * esa moneda; la base vale 1). Las monedas sin cotización se omiten.
     */
    fun convertedBalances(
        balancesByCurrency: Map<String, List<MemberBalance>>,
        baseCurrency: String,
        rates: Map<String, Double>,
    ): List<MemberBalance> {
        val names = mutableMapOf<String, String>()
        val net = mutableMapOf<String, Double>()
        for ((currency, balances) in balancesByCurrency) {
            val rate = if (currency == baseCurrency) 1.0 else rates[currency] ?: continue
            for (b in balances) {
                names[b.uid] = b.name
                net[b.uid] = (net[b.uid] ?: 0.0) + b.amount * rate
            }
        }
        return net.map { (uid, amount) -> MemberBalance(uid, names[uid] ?: "", amount) }
            .sortedByDescending { it.amount }
    }

    /**
     * Totales de gasto por moneda: cuánto se gastó en total y, por persona,
     * cuánto pagó y cuánto consumió (su parte). Solo cuenta los gastos, no los pagos.
     */
    fun totalsByCurrency(
        entries: List<Entry>,
        members: Map<String, String>,
    ): Map<String, GroupTotals> {
        val expensesByCurrency = entries
            .filter { it.type == EntryType.EXPENSE }
            .groupBy { it.currency }
        return expensesByCurrency.mapValues { (_, expenses) ->
            val paid = mutableMapOf<String, Double>()
            val share = mutableMapOf<String, Double>()
            var total = 0.0
            for (entry in expenses) {
                if (entry.participants.isEmpty()) continue
                total += entry.amount
                paid[entry.paidBy] = (paid[entry.paidBy] ?: 0.0) + entry.amount
                for (p in entry.participants) {
                    share[p] = (share[p] ?: 0.0) + entry.shareOf(p)
                }
            }
            val memberTotals = members
                .map { (uid, name) ->
                    MemberTotal(uid, name, paid[uid] ?: 0.0, share[uid] ?: 0.0)
                }
                .filter { it.paid > EPSILON || it.share > EPSILON }
                .sortedByDescending { it.share }
            GroupTotals(total = total, members = memberTotals)
        }
    }

    /**
     * Unifica los totales de varias monedas en [baseCurrency]. La tasa de cada
     * moneda indica cuántas unidades de la moneda base vale una unidad de ella.
     */
    fun convertedTotals(
        totalsByCurrency: Map<String, GroupTotals>,
        baseCurrency: String,
        rates: Map<String, Double>,
    ): GroupTotals {
        val names = mutableMapOf<String, String>()
        val paid = mutableMapOf<String, Double>()
        val shares = mutableMapOf<String, Double>()
        var total = 0.0

        for ((currency, currencyTotals) in totalsByCurrency) {
            val rate = if (currency == baseCurrency) 1.0 else rates[currency] ?: continue
            total += currencyTotals.total * rate
            for (member in currencyTotals.members) {
                names[member.uid] = member.name
                paid[member.uid] = (paid[member.uid] ?: 0.0) + member.paid * rate
                shares[member.uid] = (shares[member.uid] ?: 0.0) + member.share * rate
            }
        }

        return GroupTotals(
            total = total,
            members = names.map { (uid, name) ->
                MemberTotal(uid, name, paid[uid] ?: 0.0, shares[uid] ?: 0.0)
            }.filter { it.paid > EPSILON || it.share > EPSILON }
                .sortedByDescending { it.share },
        )
    }
}
