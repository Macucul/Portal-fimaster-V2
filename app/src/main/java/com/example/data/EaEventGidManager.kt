package com.example.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador central de GID (Global Identifier) para eventos do Robô EA.
 * Garante que cada evento possua um UUID único, estável e nunca reutilizado.
 */
object EaEventGidManager {

    private val signatureToGidMap = ConcurrentHashMap<String, String>()

    /**
     * Gera um novo GID único usando UUID v4.
     */
    fun generateNewGid(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Obtém ou cria um GID estável e único para um determinado evento.
     * Se o evento já possuir um GID válido, ele é registrado e retornado.
     * Se não possuir, uma assinatura única do evento é gerada e associada a um UUID determinístico ou persistido.
     */
    fun getOrCreateGid(event: EaRobotEvent): String {
        if (event.gid.isNotBlank()) {
            val sig = buildEventSignature(
                id = event.id,
                login = event.login,
                timestamp = event.timestamp,
                eventType = event.event,
                sistema = event.sistema,
                novo = event.novo,
                msg = event.msg,
                ticket = event.ticket,
                hora = event.hora,
                data = event.data
            )
            signatureToGidMap[sig] = event.gid
            return event.gid
        }

        return getOrCreateGid(
            id = event.id,
            login = event.login,
            timestamp = event.timestamp,
            eventType = event.event,
            sistema = event.sistema,
            novo = event.novo,
            msg = event.msg,
            ticket = event.ticket,
            hora = event.hora,
            data = event.data
        )
    }

    /**
     * Obtém ou cria um GID único e estável a partir dos parâmetros de um evento.
     */
    fun getOrCreateGid(
        id: String,
        login: Long,
        timestamp: Long,
        eventType: String,
        sistema: String = "",
        novo: String = "",
        msg: String = "",
        ticket: String = "",
        hora: String = "",
        data: String = ""
    ): String {
        val signature = buildEventSignature(
            id = id,
            login = login,
            timestamp = timestamp,
            eventType = eventType,
            sistema = sistema,
            novo = novo,
            msg = msg,
            ticket = ticket,
            hora = hora,
            data = data
        )

        return signatureToGidMap.computeIfAbsent(signature) {
            // Gera um UUID determinístico baseado na assinatura única se timestamp for conhecido
            if (timestamp > 0L || id.isNotBlank()) {
                UUID.nameUUIDFromBytes(signature.toByteArray(Charsets.UTF_8)).toString()
            } else {
                UUID.randomUUID().toString()
            }
        }
    }

    /**
     * Garante que o evento retorne com um campo `gid` devidamente populado.
     */
    fun ensureGid(event: EaRobotEvent): EaRobotEvent {
        return if (event.gid.isNotBlank()) {
            event
        } else {
            event.copy(gid = getOrCreateGid(event))
        }
    }

    private fun buildEventSignature(
        id: String,
        login: Long,
        timestamp: Long,
        eventType: String,
        sistema: String,
        novo: String,
        msg: String,
        ticket: String,
        hora: String,
        data: String
    ): String {
        val cleanMsgHash = msg.trim().hashCode()
        return "id_${id}_login_${login}_ts_${timestamp}_evt_${eventType.trim().lowercase()}_sys_${sistema.trim()}_nv_${novo.trim()}_tk_${ticket.trim()}_h_${hora}_d_${data}_m_${cleanMsgHash}"
    }
}
