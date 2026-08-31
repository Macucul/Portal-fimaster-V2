package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Event ingestion utility.
 * - Validates minimal schema (event, login, timestamp)
 * - Maps raw JSON into EaRobotEvent and upserts into Room via EaRobotEventDao
 * - If event is a "ping"/status and firebaseUrl provided, updates /dados/status/{login}.json instead of inserting into feed
 *
 * Usage:
 *   EventIngestion.ingestRawEvent(eaRobotEventDao, rawJson, firebaseUrl, authKey)
 *
 * This file is intentionally independent (works with the public DAO interface) so it can be called
 * from PortalRepository or any Firebase listener that has access to the DAO.
 */
object EventIngestion {

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    private val mediaTypeJson = "application/json; charset=utf-8".toMediaTypeOrNull()

    suspend fun ingestRawEvent(
        eaRobotEventDao: EaRobotEventDao,
        rawJson: String,
        firebaseUrl: String = "",
        authKey: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val obj = JSONObject(rawJson)
                val event = obj.optString("event", "").trim()
                val loginStr = when {
                    obj.has("login") -> obj.optString("login", "").trim()
                    obj.has("id") -> obj.optString("id", "").trim()
                    else -> ""
                }

                val timestamp = if (obj.has("timestamp")) obj.optLong("timestamp", System.currentTimeMillis() / 1000L) else System.currentTimeMillis() / 1000L

                // Minimal validation
                if (event.isBlank() || loginStr.isBlank()) {
                    // invalid message - reject
                    return@withContext false
                }

                // Determine if ping/status => update status node instead of feed
                val isPing = event.equals("ping", ignoreCase = true) || event.equals("status", ignoreCase = true) || event.contains("ping", ignoreCase = true)

                if (isPing && firebaseUrl.isNotBlank()) {
                    try {
                        val parsed = PortalRepository.parseFirebaseUrlStatic(firebaseUrl)
                        if (parsed.baseUrl.isNotBlank()) {
                            val statusObj = JSONObject()
                            statusObj.put("online", true)
                            statusObj.put("ea_ativo", obj.optBoolean("ea_ativo", true))
                            statusObj.put("last_ping", timestamp)
                            statusObj.put("last_ping_raw", timestamp)
                            statusObj.put("timestamp", timestamp)
                            if (obj.has("saldo_disponivel")) statusObj.put("saldo_disponivel", obj.optDouble("saldo_disponivel", 0.0))
                            if (obj.has("symbol")) statusObj.put("symbol", obj.optString("symbol", ""))
                            val statusUrl = PortalRepository.buildFirebaseEndpointStatic(parsed, "/dados/status/$loginStr.json", authKey)
                            val req = Request.Builder()
                                .url(statusUrl)
                                .put(statusObj.toString().toRequestBody(mediaTypeJson))
                                .build()
                            client.newCall(req).execute().use { resp ->
                                // ignore response body; success if 2xx
                                return@withContext resp.isSuccessful
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        return@withContext false
                    }
                }

                // Map to EaRobotEvent domain object
                val canonicalId = obj.optString("event_id", "").ifBlank { generateCanonicalEventId(event, timestamp, loginStr) }

                val eaEvent = EaRobotEvent(
                    id = loginStr,
                    gid = canonicalId,
                    currency = obj.optString("currency", ""),
                    event = event,
                    login = loginStr.toLongOrNull() ?: 0L,
                    server = obj.optString("server", ""),
                    symbol = obj.optString("symbol", ""),
                    timeframe = obj.optString("timeframe", ""),
                    timestamp = timestamp,
                    sistema = obj.optString("sistema", ""),
                    anterior = obj.optString("anterior", ""),
                    novo = obj.optString("novo", ""),
                    descAnterior = obj.optString("anterior_desc", ""),
                    descNovo = obj.optString("descNovo", ""),
                    data = obj.optString("data", ""),
                    hora = obj.optString("hora", ""),
                    motivacao = obj.optString("motivacao", ""),
                    moeda = obj.optString("moeda", ""),
                    diarioStatus = obj.optString("diario_status", ""),
                    diarioValor = obj.optDouble("diario_valor", 0.0),
                    diarioPct = obj.optDouble("diario_pct", 0.0),
                    semanalStatus = obj.optString("semanal_status", ""),
                    semanalValor = obj.optDouble("semanal_valor", 0.0),
                    semanalPct = obj.optDouble("semanal_pct", 0.0),
                    resumo = obj.optString("resumo", ""),
                    sessao = obj.optString("sessao", ""),
                    horaInicio = obj.optInt("hora_inicio", -1),
                    minutoInicio = obj.optInt("minuto_inicio", -1),
                    horaFim = obj.optInt("hora_fim", -1),
                    minutoFim = obj.optInt("minuto_fim", -1),
                    msg = obj.optString("msg", ""),
                    temPosicao = obj.optString("tem_posicao", ""),
                    fusoHorario = obj.optInt("fuso_horario", 0),
                    fusoTexto = obj.optString("fuso_texto", ""),
                    saldoDisponivel = obj.optDouble("saldo_disponivel", 0.0),
                    amount = obj.optDouble("amount", 0.0),
                    type = obj.optString("type", ""),
                    tipo = obj.optString("tipo", ""),
                    price = obj.optDouble("price", 0.0),
                    volume = obj.optDouble("volume", 0.0),
                    sl = obj.optDouble("sl", 0.0),
                    tp = obj.optDouble("tp", 0.0),
                    novoSl = obj.optDouble("novo_sl", 0.0),
                    novoTp = obj.optDouble("novo_tp", 0.0),
                    alvoMt = obj.optDouble("alvo_mt", 0.0),
                    protecaoMt = obj.optDouble("protecao_mt", 0.0),
                    lucroPct = obj.optDouble("lucro_pct", 0.0),
                    perdaPct = obj.optDouble("perda_pct", 0.0),
                    erroCode = obj.optInt("erro_code", 0),
                    note = obj.optString("msg", ""),
                    ticket = obj.optString("ticket", ""),
                    imageBase64 = obj.optString("imageBase64", ""),
                    filename = obj.optString("filename", "")
                )

                // Upsert into Room via DAO (uses OnConflictStrategy.REPLACE)
                try {
                    eaRobotEventDao.insertOrUpdateEvent(eaEvent.toEntity())
                    return@withContext true
                } catch (e: Exception) {
                    e.printStackTrace()
                    return@withContext false
                }

            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext false
            }
        }
    }

    private fun generateCanonicalEventId(event: String, timestamp: Long, login: String): String {
        val base = "$timestamp-$login-$event"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(base.toByteArray())
        return timestamp.toString() + "-" + digest.joinToString("") { "%02x".format(it) }
    }
}
