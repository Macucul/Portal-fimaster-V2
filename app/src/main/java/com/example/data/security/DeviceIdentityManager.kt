package com.example.data.security

import android.content.Context
import android.provider.Settings
import com.example.data.GithubUser
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class DeviceAuthResult(
    val deviceUid: String,
    val firebaseAuthUid: String? = null,
    val isAuthenticated: Boolean = false,
    val isNewRegistration: Boolean = false,
    val message: String = ""
)

class DeviceIdentityManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("fimaster_security_prefs", Context.MODE_PRIVATE)

    /**
     * Obtém ou gera um UID Silencioso e Permanente para este dispositivo
     */
    fun getSilentDeviceUid(): String {
        // 1. Tenta recuperar UID salvo previamente
        var savedUid = prefs.getString("KEY_SILENT_DEVICE_UID", null)
        if (!savedUid.isNullOrEmpty()) {
            return savedUid
        }

        // 2. Se não existir, tenta capturar o ANDROID_ID do sistema
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }

        // 3. Define o ID único (se ANDROID_ID for inválido ou nulo, gera um UUID)
        savedUid = if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            UUID.randomUUID().toString()
        }

        // 4. Guarda persistentemente nas SharedPreferences
        prefs.edit().putString("KEY_SILENT_DEVICE_UID", savedUid).apply()
        return savedUid
    }

    /**
     * Autentica silenciosamente no Firebase Auth e vincula o UID do dispositivo.
     */
    suspend fun authenticateDeviceWithFirebase(): DeviceAuthResult {
        val deviceUid = getSilentDeviceUid()
        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            val firebaseUid = if (currentUser != null) {
                currentUser.uid
            } else {
                val authResult = auth.signInAnonymously().await()
                authResult.user?.uid ?: deviceUid
            }

            prefs.edit()
                .putString("KEY_FIREBASE_AUTH_UID", firebaseUid)
                .putLong("KEY_LAST_AUTH_TIMESTAMP", System.currentTimeMillis())
                .apply()

            DeviceAuthResult(
                deviceUid = deviceUid,
                firebaseAuthUid = firebaseUid,
                isAuthenticated = true,
                message = "Autenticado com sucesso via Firebase Auth (UID Dispositivo: $deviceUid)"
            )
        } catch (e: Exception) {
            // Em caso de offline ou ambiente sem google-services ativo, mantém fallback transparente via deviceUid
            DeviceAuthResult(
                deviceUid = deviceUid,
                firebaseAuthUid = deviceUid,
                isAuthenticated = false,
                message = "Modo Offline / Fallback local para UID Dispositivo: $deviceUid (${e.localizedMessage})"
            )
        }
    }

    /**
     * Gera um perfil padrão de usuário associado ao UID do dispositivo para registro automático.
     */
    fun createDefaultDeviceUser(deviceUid: String, firebaseAuthUid: String? = null): GithubUser {
        val shortId = deviceUid.filter { it.isLetterOrDigit() }.take(6).uppercase()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = Date()
        val isoTimestamp = isoFormat.format(now)

        val cal = Calendar.getInstance()
        cal.time = now
        cal.add(Calendar.DAY_OF_YEAR, 30)
        val validadeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val validadeStr = validadeFormat.format(cal.time)

        return GithubUser(
            id = deviceUid,
            nome = "Cliente Dispositivo #$shortId",
            numero = "",
            senhaHash = "",
            salt = "",
            saldo = 1000.0,
            status = "ATIVO",
            mt5IdConta = "859423",
            licencaAtiva = true,
            licencaPlano = "trial",
            licencaProduto = "Fimaster EA Pro",
            licencaValidade = validadeStr,
            auditoriaUltimoDispositivo = deviceUid,
            auditoriaUltimoLogin = isoTimestamp,
            dataRegistro = isoTimestamp,
            ultimaAtualizacao = isoTimestamp
        )
    }
}
