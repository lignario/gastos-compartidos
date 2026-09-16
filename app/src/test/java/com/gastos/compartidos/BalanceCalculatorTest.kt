package com.gastos.compartidos

import com.gastos.compartidos.data.Entry
import com.gastos.compartidos.data.EntryType
import com.gastos.compartidos.data.SplitType
import com.gastos.compartidos.domain.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val members = mapOf("a" to "Ana", "b" to "Beto", "c" to "Carla")

    @Test
    fun `gasto repartido entre todos`() {
        // Ana paga 30 repartidos entre los tres: Ana +20, Beto -10, Carla -10
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b", "c")),
        )
        val balances = BalanceCalculator.balancesByCurrency(entries, members).getValue("EUR")
        val byUid = balances.associateBy { it.uid }
        assertEquals(20.0, byUid.getValue("a").amount, 0.001)
        assertEquals(-10.0, byUid.getValue("b").amount, 0.001)
        assertEquals(-10.0, byUid.getValue("c").amount, 0.001)
    }

    @Test
    fun `un pago salda la deuda`() {
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 20.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b")),
            Entry(type = EntryType.PAYMENT, amount = 10.0, currency = "EUR", paidBy = "b", paidTo = "a"),
        )
        val balances = BalanceCalculator.balancesByCurrency(entries, members).getValue("EUR")
        balances.forEach { assertEquals(0.0, it.amount, 0.001) }
    }

    @Test
    fun `las monedas no se mezclan`() {
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b")),
            Entry(type = EntryType.EXPENSE, amount = 100.0, currency = "USD", paidBy = "b", participants = listOf("a", "b")),
        )
        val byCurrency = BalanceCalculator.balancesByCurrency(entries, members)
        assertEquals(2, byCurrency.size)
        assertEquals(15.0, byCurrency.getValue("EUR").first { it.uid == "a" }.amount, 0.001)
        assertEquals(-50.0, byCurrency.getValue("USD").first { it.uid == "a" }.amount, 0.001)
    }

    @Test
    fun `simplificacion de deudas con tres miembros`() {
        // Ana +20, Beto -10, Carla -10 => dos transferencias hacia Ana
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b", "c")),
        )
        val balances = BalanceCalculator.balancesByCurrency(entries, members).getValue("EUR")
        val settlements = BalanceCalculator.suggestedSettlements(balances)
        assertEquals(2, settlements.size)
        settlements.forEach {
            assertEquals("a", it.toUid)
            assertEquals(10.0, it.amount, 0.001)
        }
    }

    @Test
    fun `cadena de deudas se reduce al minimo de transferencias`() {
        // Ana paga 30 entre Ana/Beto; Beto paga 30 entre Beto/Carla
        // Neto: Ana +15, Beto 0, Carla -15 => una sola transferencia Carla -> Ana
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b")),
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "b", participants = listOf("b", "c")),
        )
        val balances = BalanceCalculator.balancesByCurrency(entries, members).getValue("EUR")
        val settlements = BalanceCalculator.suggestedSettlements(balances)
        assertEquals(1, settlements.size)
        assertEquals("c", settlements[0].fromUid)
        assertEquals("a", settlements[0].toUid)
        assertEquals(15.0, settlements[0].amount, 0.001)
    }

    @Test
    fun `division por montos exactos`() {
        // Ana paga 100; le toca 30 a Ana, 70 a Beto.
        val entries = listOf(
            Entry(
                type = EntryType.EXPENSE, amount = 100.0, currency = "EUR", paidBy = "a",
                participants = listOf("a", "b"),
                splitType = SplitType.EXACT,
                shares = mapOf("a" to 30.0, "b" to 70.0),
            ),
        )
        val byUid = BalanceCalculator.balancesByCurrency(entries, members)
            .getValue("EUR").associateBy { it.uid }
        // Ana pagó 100 y consume 30 => +70. Beto consume 70 => -70.
        assertEquals(70.0, byUid.getValue("a").amount, 0.001)
        assertEquals(-70.0, byUid.getValue("b").amount, 0.001)
    }

    @Test
    fun `division por porcentajes`() {
        // Ana paga 200; 25% Ana, 75% Beto => Ana consume 50, Beto 150.
        val entries = listOf(
            Entry(
                type = EntryType.EXPENSE, amount = 200.0, currency = "EUR", paidBy = "a",
                participants = listOf("a", "b"),
                splitType = SplitType.PERCENT,
                shares = mapOf("a" to 25.0, "b" to 75.0),
            ),
        )
        val byUid = BalanceCalculator.balancesByCurrency(entries, members)
            .getValue("EUR").associateBy { it.uid }
        assertEquals(150.0, byUid.getValue("a").amount, 0.001)
        assertEquals(-150.0, byUid.getValue("b").amount, 0.001)
    }

    @Test
    fun `totales pagado y consumido`() {
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 30.0, currency = "EUR", paidBy = "a", participants = listOf("a", "b", "c")),
            Entry(type = EntryType.EXPENSE, amount = 60.0, currency = "EUR", paidBy = "b", participants = listOf("a", "b", "c")),
            // Un pago no debe contar en los totales de gasto.
            Entry(type = EntryType.PAYMENT, amount = 10.0, currency = "EUR", paidBy = "c", paidTo = "a"),
        )
        val totals = BalanceCalculator.totalsByCurrency(entries, members).getValue("EUR")
        assertEquals(90.0, totals.total, 0.001)
        val byUid = totals.members.associateBy { it.uid }
        assertEquals(30.0, byUid.getValue("a").paid, 0.001)
        assertEquals(60.0, byUid.getValue("b").paid, 0.001)
        assertEquals(0.0, byUid.getValue("c").paid, 0.001)
        // Cada uno consume 90/3 = 30.
        assertEquals(30.0, byUid.getValue("a").share, 0.001)
        assertEquals(30.0, byUid.getValue("c").share, 0.001)
    }

    @Test
    fun `totales de varias monedas se convierten a la moneda base`() {
        val entries = listOf(
            Entry(type = EntryType.EXPENSE, amount = 100.0, currency = "UYU", paidBy = "a", participants = listOf("a", "b")),
            Entry(type = EntryType.EXPENSE, amount = 10.0, currency = "USD", paidBy = "b", participants = listOf("a", "b")),
        )
        val totals = BalanceCalculator.totalsByCurrency(entries, members)
        val converted = BalanceCalculator.convertedTotals(totals, "UYU", mapOf("USD" to 40.0))

        assertEquals(500.0, converted.total, 0.001)
        val byUid = converted.members.associateBy { it.uid }
        assertEquals(100.0, byUid.getValue("a").paid, 0.001)
        assertEquals(400.0, byUid.getValue("b").paid, 0.001)
        assertEquals(250.0, byUid.getValue("a").share, 0.001)
        assertEquals(250.0, byUid.getValue("b").share, 0.001)
    }
}
