package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Gerenciador de estado de visualização e resolução de notificações e eventos.
 * Mantém o histórico sincronizado, remove notificações pendentes tratadas e
 * garante que eventos visualizados/resolvidos não voltem para a lista de alertas pendentes.
 */
object NotificationStateManager {

    private const val PREFS_NAME = "ea_notifications_state"
    private const val KEY_VIEWED_GIDS = "viewed_notification_gids"
    private const val KEY_RESOLVED_GIDS = "resolved_notification_gids"

    private val _viewedGids = MutableStateFlow<Set<String>>(emptySet())
    val viewedGids: StateFlow<Set<String>> = _viewedGids.asStateFlow()

    private val _resolvedGids = MutableStateFlow<Set<String>>(emptySet())
    val resolvedGids: StateFlow<Set<String>> = _resolvedGids.asStateFlow()

    private val _focusedEventGid = MutableStateFlow<String?>(null)
    val focusedEventGid: StateFlow<String?> = _focusedEventGid.asStateFlow()

    private var initialized = false
    private val scope = CoroutineScope(Dispatchers.IO)

    @Synchronized
    fun initIfNeeded(context: Context) {
        if (initialized) return
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedViewed = prefs.getStringSet(KEY_VIEWED_GIDS, emptySet()) ?: emptySet()
            val savedResolved = prefs.getStringSet(KEY_RESOLVED_GIDS, emptySet()) ?: emptySet()
            _viewedGids.value = savedViewed.toSet()
            _resolvedGids.value = savedResolved.toSet()
            initialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Marca uma notificação (e o evento associado) como visualizada.
     * Atualiza o estado em memória imediatamente para re-renderização instantânea
     * do sino de notificação e persiste em disco.
     */
    fun markAsViewed(context: Context?, notifGid: String, eventGid: String? = null) {
        val updated = _viewedGids.value.toMutableSet()
        if (notifGid.isNotBlank()) updated.add(notifGid)
        if (!eventGid.isNullOrBlank()) updated.add(eventGid)
        _viewedGids.value = updated

        context?.let { saveToPrefs(it) }
    }

    /**
     * Marca uma notificação como resolvida e define o GID do evento para foco direto na aba de Eventos.
     */
    fun markAsResolved(context: Context?, notifGid: String, eventGid: String? = null) {
        val updatedViewed = _viewedGids.value.toMutableSet()
        val updatedResolved = _resolvedGids.value.toMutableSet()

        if (notifGid.isNotBlank()) {
            updatedViewed.add(notifGid)
            updatedResolved.add(notifGid)
        }
        if (!eventGid.isNullOrBlank()) {
            updatedViewed.add(eventGid)
            updatedResolved.add(eventGid)
            _focusedEventGid.value = eventGid
        }

        _viewedGids.value = updatedViewed
        _resolvedGids.value = updatedResolved

        context?.let { saveToPrefs(it) }
    }

    /**
     * Marca todas as notificações fornecidas como visualizadas.
     */
    fun markAllAsViewed(context: Context?, notifGids: List<String>, eventGids: List<String> = emptyList()) {
        val updated = _viewedGids.value.toMutableSet()
        notifGids.filter { it.isNotBlank() }.forEach { updated.add(it) }
        eventGids.filter { it.isNotBlank() }.forEach { updated.add(it) }
        _viewedGids.value = updated

        context?.let { saveToPrefs(it) }
    }

    /**
     * Verifica se uma notificação ou seu evento já foi visualizado/resolvido.
     */
    fun isViewed(notifGid: String, eventGid: String? = null): Boolean {
        val set = _viewedGids.value
        if (notifGid.isNotBlank() && set.contains(notifGid)) return true
        if (!eventGid.isNullOrBlank() && set.contains(eventGid)) return true
        return false
    }

    /**
     * Define o GID do evento em foco para navegação direta.
     */
    fun setFocusedEventGid(gid: String?) {
        _focusedEventGid.value = gid
    }

    /**
     * Limpa o foco do evento atual.
     */
    fun clearFocusedEventGid() {
        _focusedEventGid.value = null
    }

    /**
     * Reseta todo o histórico de visualizados (útil para limpar dados).
     */
    fun resetAllViewed(context: Context?) {
        _viewedGids.value = emptySet()
        _resolvedGids.value = emptySet()
        _focusedEventGid.value = null
        context?.let {
            val prefs = it.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }

    private fun saveToPrefs(context: Context) {
        val currentViewed = _viewedGids.value
        val currentResolved = _resolvedGids.value
        scope.launch {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .putStringSet(KEY_VIEWED_GIDS, currentViewed)
                    .putStringSet(KEY_RESOLVED_GIDS, currentResolved)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isNull(str: String?): Boolean = str == null
}
