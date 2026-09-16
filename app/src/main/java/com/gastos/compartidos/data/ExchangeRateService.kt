package com.gastos.compartidos.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Trae cotizaciones del día desde la API gratuita open.er-api.com (sin clave).
 */
object ExchangeRateService {

    /**
     * Devuelve, para cada moneda pedida, cuántas unidades de [base] vale 1
     * unidad de esa moneda (lo que espera la pantalla de saldos).
     * Devuelve null si falla la red o la API.
     */
    suspend fun fetchRatesToBase(base: String, currencies: List<String>): Map<String, Double>? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://open.er-api.com/v6/latest/$base")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                if (json.optString("result") != "success") return@withContext null
                val rates = json.getJSONObject("rates")
                // rates[c] = cuántas 'c' vale 1 base  =>  1 c = 1/rates[c] base.
                currencies.mapNotNull { c ->
                    val perBase = rates.optDouble(c, 0.0)
                    if (perBase > 0) c to (1.0 / perBase) else null
                }.toMap().ifEmpty { null }
            } catch (e: Exception) {
                null
            }
        }
}
