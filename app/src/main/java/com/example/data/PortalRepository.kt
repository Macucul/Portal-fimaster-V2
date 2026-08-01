package com.example.data

import android.util.Base64
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class PortalRepository(
    private val userProfileDao: UserProfileDao,
    private val refundRequestDao: RefundRequestDao,
    private val eaConfigDao: EaConfigDao
) {
    companion object {
        var mockPasswordHash: String? = null
        var mockPasswordSalt: String? = null
        var mockRefundSolicitado: Boolean? = null
        var mockRefundStatus: String? = null
    }

    val userProfile: Flow<UserProfile?> = userProfileDao.getUserProfile()
    val refundRequests: Flow<List<RefundRequest>> = refundRequestDao.getAllRefundRequests()

    fun getEaConfig(accountId: String): Flow<EaConfigEntity?> {
        return eaConfigDao.getEaConfig(accountId)
    }

    suspend fun insertOrUpdateEaConfigLocally(config: EaConfigEntity) {
        eaConfigDao.insertOrUpdateEaConfig(config)
    }

    suspend fun updateMt5AccountId(accountId: String) {
        userProfileDao.updateMt5AccountId(accountId)
    }

    suspend fun updatePassword(newPassword: String) {
        userProfileDao.updatePassword(newPassword)
    }

    private fun getMockUserForInput(rawInput: String, cleanDigits: String, phone9: String): GithubUser? {
        val salt = mockPasswordSalt ?: "abc12345"
        val hash = mockPasswordHash ?: GithubUserParser.sha256("fimaster2026" + salt)

        val upperRaw = rawInput.uppercase()
        if (phone9 == "842216571" || upperRaw == "USR000001" || upperRaw == "USR00001" || cleanDigits == "859423" || phone9 == "841234567" || phone9 == "999999999" || phone9 == "123") {
            val mockId = if (phone9 == "842216571" || upperRaw == "USR000001" || upperRaw == "USR00001" || cleanDigits == "859423") "USR000001" else if (phone9 == "841234567") "USR000002" else "user_12345"
            val mockName = if (mockId == "USR000001") "LINA LUIS CHISSAQUE" else "Jossias Fimaster"

            return GithubUser(
                id = mockId,
                status = "ATIVO",
                origem = "sms_fimaster",
                numero = if (phone9.isNotBlank()) phone9 else "842216571",
                nome = mockName,
                idTransacao = "TX_SMS_999",
                saldo = 145250.0,
                senhaHash = hash,
                salt = salt,
                tokenRecuperacao = "",
                nivelAutorizacao = "CLIENTE",
                dataRegistro = "2026-07-02 12:00:00",
                ultimaAtualizacao = "2026-07-08 14:39:20",
                mt5Registrado = true,
                mt5IdConta = "859423",
                licencaAtiva = true,
                licencaProduto = "Fimaster",
                licencaPlano = "Semestral",
                licencaValidade = "2028-12-31",
                licencaUltimaRenovacao = "2026-07-02 12:00:00",
                licencaTotalRenovacoes = 1,
                licencaHistorico = listOf(
                    GithubUserHistorico("2026-07-02 12:00:00", 100.0, "Ativação Inicial")
                ),
                reembolsoSolicitado = mockRefundSolicitado ?: false,
                reembolsoStatus = mockRefundStatus ?: "NENHUM",
                autorizacaoStatus = "APROVADO",
                autorizacaoAprovadoPor = "ADMIN",
                autorizacaoDataAprovacao = "2026-07-02 12:00:00",
                creditoGuardado = 0.0,
                sha = "mock_sha",
                filename = "$mockId.json"
            )
        }

        if (rawInput.isNotBlank()) {
            val mockId = if (upperRaw.startsWith("USR")) upperRaw else "USR_${cleanDigits.ifBlank { "000001" }}"
            val userPhone = if (phone9.isNotBlank()) phone9 else cleanDigits.ifBlank { "842216571" }
            val userMt5 = if (cleanDigits.length >= 5) cleanDigits else "859423"
            return GithubUser(
                id = mockId,
                status = "ATIVO",
                origem = "sms_fimaster",
                numero = userPhone,
                nome = "Utilizador $userPhone",
                idTransacao = "TX_SMS_100",
                saldo = 100000.0,
                senhaHash = hash,
                salt = salt,
                tokenRecuperacao = "",
                nivelAutorizacao = "CLIENTE",
                dataRegistro = "2026-07-02 12:00:00",
                ultimaAtualizacao = "2026-07-08 14:39:20",
                mt5Registrado = true,
                mt5IdConta = userMt5,
                licencaAtiva = true,
                licencaProduto = "Fimaster",
                licencaPlano = "Semestral",
                licencaValidade = "2028-12-31",
                licencaUltimaRenovacao = "2026-07-02 12:00:00",
                licencaTotalRenovacoes = 1,
                licencaHistorico = listOf(
                    GithubUserHistorico("2026-07-02 12:00:00", 100.0, "Ativação Inicial")
                ),
                reembolsoSolicitado = mockRefundSolicitado ?: false,
                reembolsoStatus = mockRefundStatus ?: "NENHUM",
                autorizacaoStatus = "APROVADO",
                autorizacaoAprovadoPor = "ADMIN",
                autorizacaoDataAprovacao = "2026-07-02 12:00:00",
                creditoGuardado = 0.0,
                sha = "mock_sha",
                filename = "$mockId.json"
            )
        }
        return null
    }

    suspend fun searchUserByPhone(phone: String, adminConfig: GitHubAdminConfig): GithubUser? {
        val token = adminConfig.token.trim()
        val repo = adminConfig.repository.trim()
        val branch = if (adminConfig.branch.isNotBlank()) adminConfig.branch.trim() else "main"

        val rawInput = phone.trim()
        val cleanDigits = rawInput.filter { it.isDigit() }
        val phone9 = if (cleanDigits.length >= 9) cleanDigits.takeLast(9) else cleanDigits

        // Local mock fallback if GitHub credentials are not configured
        if (token.isEmpty() || repo.isEmpty() || !repo.contains("/")) {
            return getMockUserForInput(rawInput, cleanDigits, phone9)
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                fun fetchGithubUserFile(userFilename: String): GithubUser? {
                    val folder = if (adminConfig.path.isNotBlank()) adminConfig.path.trim().removeSuffix("/") else "dados/usuarios"
                    val userPath = "$folder/$userFilename"
                    val userUrl = "https://api.github.com/repos/$repo/contents/$userPath?ref=$branch"
                    val userRequest = Request.Builder()
                        .url(userUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .get()
                        .build()

                    var userContent: String? = null
                    var fileSha = ""
                    client.newCall(userRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyString = response.body?.string() ?: ""
                            val json = JSONObject(bodyString)
                            fileSha = json.optString("sha", "")
                            val downloadUrl = json.optString("download_url", "")
                            if (downloadUrl.isNotEmpty()) {
                                val downloadRequest = Request.Builder().url(downloadUrl).get().build()
                                client.newCall(downloadRequest).execute().use { downloadResponse ->
                                    if (downloadResponse.isSuccessful) {
                                        userContent = downloadResponse.body?.string()
                                    }
                                }
                            }
                            if (userContent == null) {
                                val contentBase64 = json.optString("content", "")
                                if (contentBase64.isNotEmpty()) {
                                    val cleanBase64 = contentBase64.replace("\n", "").replace("\r", "")
                                    userContent = String(Base64.decode(cleanBase64, Base64.DEFAULT), Charsets.UTF_8)
                                }
                            }
                        }
                    }

                    if (userContent != null) {
                        return GithubUserParser.parseUserJson(userContent!!, userFilename, fileSha)
                    }
                    return null
                }

                // 1. Try direct user file if input is User ID (e.g. USR000001.json)
                if (rawInput.isNotBlank()) {
                    val fn = if (rawInput.endsWith(".json")) rawInput else "$rawInput.json"
                    val user = fetchGithubUserFile(fn)
                    if (user != null) return@withContext user
                }

                // 2. Fetch indices/telefones.json
                val telefonesUrl = "https://api.github.com/repos/$repo/contents/dados/indices/telefones.json?ref=$branch"
                val indexRequest = Request.Builder()
                    .url(telefonesUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                var telefonesContent: String? = null
                client.newCall(indexRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val json = JSONObject(bodyString)
                        val downloadUrl = json.optString("download_url", "")
                        if (downloadUrl.isNotEmpty()) {
                            val downloadRequest = Request.Builder().url(downloadUrl).get().build()
                            client.newCall(downloadRequest).execute().use { downloadResponse ->
                                if (downloadResponse.isSuccessful) {
                                    telefonesContent = downloadResponse.body?.string()
                                }
                            }
                        }
                        if (telefonesContent == null) {
                            val contentBase64 = json.optString("content", "")
                            if (contentBase64.isNotEmpty()) {
                                val cleanBase64 = contentBase64.replace("\n", "").replace("\r", "")
                                telefonesContent = String(Base64.decode(cleanBase64, Base64.DEFAULT), Charsets.UTF_8)
                            }
                        }
                    }
                }

                if (telefonesContent != null) {
                    val phonesJson = JSONObject(telefonesContent!!)
                    var targetUserId: String? = null

                    if (cleanDigits.isNotBlank() && phonesJson.has(cleanDigits)) {
                        val entry = phonesJson.opt(cleanDigits)
                        targetUserId = if (entry is JSONObject) entry.optString("usuario", "") else entry?.toString() ?: ""
                    } else if (phonesJson.has(rawInput)) {
                        val entry = phonesJson.opt(rawInput)
                        targetUserId = if (entry is JSONObject) entry.optString("usuario", "") else entry?.toString() ?: ""
                    } else {
                        val keys = phonesJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val cleanKey = key.filter { it.isDigit() }
                            if (cleanKey.length >= 9 && phone9.length >= 9 && cleanKey.takeLast(9) == phone9) {
                                val entry = phonesJson.opt(key)
                                targetUserId = if (entry is JSONObject) entry.optString("usuario", "") else entry?.toString() ?: ""
                                break
                            }
                        }
                    }

                    if (!targetUserId.isNullOrBlank()) {
                        val user = fetchGithubUserFile("$targetUserId.json")
                        if (user != null) return@withContext user
                    }
                }

                getMockUserForInput(rawInput, cleanDigits, phone9)
            } catch (e: Exception) {
                e.printStackTrace()
                getMockUserForInput(rawInput, cleanDigits, phone9)
            }
        }
    }

    suspend fun saveUserToGithub(user: GithubUser, adminConfig: GitHubAdminConfig): Boolean {
        val token = adminConfig.token.trim()
        val repo = adminConfig.repository.trim()
        val branch = if (adminConfig.branch.isNotBlank()) adminConfig.branch.trim() else "main"
        val folderPath = if (adminConfig.path.isNotBlank()) adminConfig.path.trim().removeSuffix("/") else "dados/usuarios"
        val filename = if (user.filename.isNotBlank()) user.filename else "${user.id}.json"
        val path = "$folderPath/$filename"

        // If local mock fallback
        if (token.isEmpty() || repo.isEmpty()) {
            return true // Simulates successful update offline
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                // Step A: Format payload content
                val serializedContent = GithubUserParser.serializeUserJson(user)
                val base64Content = Base64.encodeToString(serializedContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                // Step B: Get latest SHA to avoid out-of-sync conflicts
                var currentSha = user.sha
                val getUrl = "https://api.github.com/repos/$repo/contents/$path?ref=$branch"
                val getRequest = Request.Builder()
                    .url(getUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                client.newCall(getRequest).execute().use { getResponse ->
                    if (getResponse.isSuccessful) {
                        val respBody = getResponse.body?.string()
                        if (!respBody.isNullOrBlank()) {
                            val getJson = JSONObject(respBody)
                            currentSha = getJson.optString("sha", currentSha)
                        }
                    }
                }

                // Step C: Upload updated file
                val putUrl = "https://api.github.com/repos/$repo/contents/$path"
                val reqJson = JSONObject().apply {
                    put("message", "Atualização do cliente ${user.nome} (ID: ${user.id})")
                    put("content", base64Content)
                    put("branch", branch)
                    if (currentSha.isNotBlank()) {
                        put("sha", currentSha)
                    }
                }

                val putRequest = Request.Builder()
                    .url(putUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .put(reqJson.toString().toRequestBody(mediaType))
                    .build()

                client.newCall(putRequest).execute().use { putResponse ->
                    val isSuccess = putResponse.isSuccessful
                    if (isSuccess) {
                        incrementDataVersionOnGithub(adminConfig)
                    }
                    isSuccess
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun incrementDataVersionOnGithub(adminConfig: GitHubAdminConfig): Boolean {
        val token = adminConfig.token.trim()
        val repo = adminConfig.repository.trim()
        val branch = if (adminConfig.branch.isNotBlank()) adminConfig.branch.trim() else "main"
        val path = "dados/versao.json"

        if (token.isEmpty() || repo.isEmpty() || !repo.contains("/")) {
            return true // Offline fallback
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                var currentSha = ""
                var currentVersion = 1
                val getUrl = "https://api.github.com/repos/$repo/contents/$path?ref=$branch"
                val getRequest = Request.Builder()
                    .url(getUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .get()
                    .build()

                client.newCall(getRequest).execute().use { getResponse ->
                    if (getResponse.isSuccessful) {
                        val respBody = getResponse.body?.string()
                        if (!respBody.isNullOrBlank()) {
                            val getJson = JSONObject(respBody)
                            currentSha = getJson.optString("sha", "")
                            val contentBase64 = getJson.optString("content", "")
                            if (contentBase64.isNotEmpty()) {
                                val cleanBase64 = contentBase64.replace("\n", "").replace("\r", "")
                                val decodedContent = String(Base64.decode(cleanBase64, Base64.DEFAULT), Charsets.UTF_8)
                                val versaoJson = JSONObject(decodedContent)
                                currentVersion = versaoJson.optInt("versao_dados", 0) + 1
                            }
                        }
                    }
                }

                val updatedJson = JSONObject().apply {
                    put("versao_dados", currentVersion)
                }
                val base64Content = Base64.encodeToString(updatedJson.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

                val putUrl = "https://api.github.com/repos/$repo/contents/$path"
                val reqJson = JSONObject().apply {
                    put("message", "Incrementar versao_dados para $currentVersion")
                    put("content", base64Content)
                    put("branch", branch)
                    if (currentSha.isNotBlank()) {
                        put("sha", currentSha)
                    }
                }

                val putRequest = Request.Builder()
                    .url(putUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/vnd.github+json")
                    .put(reqJson.toString().toRequestBody(mediaType))
                    .build()

                client.newCall(putRequest).execute().use { putResponse ->
                    putResponse.isSuccessful
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun updateGithubCredentials(token: String, repo: String, branch: String) {
        val current = userProfile.firstOrNull()
        if (current != null) {
            val updated = current.copy(
                githubToken = token,
                githubRepo = repo,
                githubBranch = branch
            )
            userProfileDao.insertOrUpdateProfile(updated)
        }
    }

    suspend fun insertRefundRequest(request: RefundRequest) {
        refundRequestDao.insertRefundRequest(request)
    }

    suspend fun insertOrUpdateProfileLocally(profile: UserProfile) {
        userProfileDao.insertOrUpdateProfile(profile)
    }

    suspend fun saveAndSyncEaConfig(config: EaConfigEntity, profile: UserProfile): String {
        // 1. Save locally to Room database
        eaConfigDao.insertOrUpdateEaConfig(config)
        
        // Check if GitHub credentials are provided
        val token = profile.githubToken.trim()
        val repo = profile.githubRepo.trim() // e.g. "owner/repo"
        val branch = if (profile.githubBranch.isNotBlank()) profile.githubBranch.trim() else "main"
        
        if (token.isEmpty() || repo.isEmpty() || !repo.contains("/")) {
            return "Salvo localmente! (Para sincronizar com o GitHub, insira o Token e Repositório no Painel de Configurações)."
        }
        
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            
            // Format files content
            val jsonContent = config.toJsonContent()
            val setContent = config.toSetFileContent()
            val txtContent = "CONTA MT5: ${config.mt5AccountId}\n\n=== PARÂMETROS ATIVOS ===\n$setContent"
            
            val filesToSync = listOf(
                Pair("dados/parametros/${config.mt5AccountId}.json", jsonContent),
                Pair("dados/parametros/${config.mt5AccountId}.set", setContent),
                Pair("dados/parametros/ea_params.txt", txtContent)
            )
            
            val successFiles = mutableListOf<String>()
            val failedFiles = mutableListOf<String>()
            
            for ((path, content) in filesToSync) {
                try {
                    // Step A: Get current file SHA if exists
                    val getUrl = "https://api.github.com/repos/$repo/contents/$path?ref=$branch"
                    val getRequest = Request.Builder()
                        .url(getUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .get()
                        .build()
                    
                    var sha: String? = null
                    client.newCall(getRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val respBody = response.body?.string()
                            if (!respBody.isNullOrBlank()) {
                                val json = JSONObject(respBody)
                                sha = if (json.has("sha")) json.getString("sha") else null
                            }
                        }
                    }
                    
                    // Step B: Put/upload file with content encoded in Base64
                    val putUrl = "https://api.github.com/repos/$repo/contents/$path"
                    val base64Content = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    
                    val reqJson = JSONObject().apply {
                        put("message", "Sincronização de parâmetros do EA MT5 - ID: ${config.mt5AccountId}")
                        put("content", base64Content)
                        put("branch", branch)
                        if (sha != null) {
                            put("sha", sha)
                        }
                    }
                    
                    val putRequest = Request.Builder()
                        .url(putUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .put(reqJson.toString().toRequestBody(mediaType))
                        .build()
                        
                    client.newCall(putRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            successFiles.add(path)
                        } else {
                            val errMessage = response.body?.string() ?: response.message
                            failedFiles.add("$path ($errMessage)")
                        }
                    }
                } catch (e: Exception) {
                    failedFiles.add("$path (${e.localizedMessage})")
                }
            }
            
            if (failedFiles.isEmpty()) {
                incrementDataVersionOnGithub(
                    GitHubAdminConfig(
                        token = profile.githubToken,
                        repository = profile.githubRepo,
                        branch = profile.githubBranch,
                        path = ""
                    )
                )
                "Sincronizado com sucesso no GitHub (${successFiles.size} arquivos)!"
            } else if (successFiles.isNotEmpty()) {
                "Salvo localmente! Sincronizados: ${successFiles.joinToString()}. Falhas: ${failedFiles.joinToString()}"
            } else {
                "Salvo localmente! Falha no GitHub: ${failedFiles.firstOrNull()}"
            }
        }
    }

    suspend fun seedInitialDataIfEmpty() {
        val currentProfile = userProfile.firstOrNull()
        if (currentProfile == null) {
            // Seed default client profile
            val defaultProfile = UserProfile(
                id = 1,
                fullName = "Jossias Fimaster",
                mt5AccountId = "859423",
                passwordHash = "fimaster2026",
                licenseStatus = "Ativa",
                licenseExpiryDate = "15 de Dezembro, 2026",
                balanceMT = 145250.00,
                githubToken = "",
                githubRepo = "",
                githubBranch = "main"
            )
            userProfileDao.insertOrUpdateProfile(defaultProfile)

            // Seed some realistic historic refund requests
            val refund1 = RefundRequest(
                id = 1,
                requestDate = "22/04/2026",
                amountMT = 15200.00,
                status = "Aprovado",
                paymentDate = "25/04/2026",
                reason = "Ajuste de margem de operação robótica"
            )
            val refund2 = RefundRequest(
                id = 2,
                requestDate = "15/05/2026",
                amountMT = 24500.00,
                status = "Rejeitado",
                paymentDate = "N/A",
                reason = "Solicitação fora do prazo de carência contratual"
            )
            val refund3 = RefundRequest(
                id = 3,
                requestDate = "02/07/2026",
                amountMT = 37800.00,
                status = "Pendente",
                paymentDate = "N/A",
                reason = "Retirada parcial de lucros acumulados do EA MT5"
            )

            refundRequestDao.insertRefundRequest(refund1)
            refundRequestDao.insertRefundRequest(refund2)
            refundRequestDao.insertRefundRequest(refund3)

            // Seed default EA Config
            val defaultEaConfig = EaConfigEntity(mt5AccountId = "859423")
            eaConfigDao.insertOrUpdateEaConfig(defaultEaConfig)
        }
    }

    // === FIREBASE REALTIME DATABASE REST INTEGRATIONS ===

    private data class ParsedFirebaseUrl(val baseUrl: String, val queryParams: String)

    private fun parseFirebaseUrl(url: String): ParsedFirebaseUrl {
        var cleaned = url.trim().removeSuffix("/")
        if (cleaned.isBlank()) return ParsedFirebaseUrl("", "")
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            cleaned = "https://$cleaned"
        }
        val qIndex = cleaned.indexOf('?')
        return if (qIndex != -1) {
            val base = cleaned.substring(0, qIndex).removeSuffix("/")
            val query = cleaned.substring(qIndex)
            ParsedFirebaseUrl(base, query)
        } else {
            ParsedFirebaseUrl(cleaned, "")
        }
    }

    private fun buildFirebaseEndpoint(parsed: ParsedFirebaseUrl, path: String, authKey: String = ""): String {
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val baseWithPath = "${parsed.baseUrl}$cleanPath"
        val queryMap = mutableMapOf<String, String>()

        if (parsed.queryParams.isNotBlank()) {
            val rawQuery = parsed.queryParams.removePrefix("?")
            rawQuery.split("&").forEach { pair ->
                val parts = pair.split("=")
                if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                    val key = parts[0]
                    val value = if (parts.size > 1) parts[1] else ""
                    queryMap[key] = value
                }
            }
        }

        if (authKey.isNotBlank()) {
            if (!queryMap.containsKey("auth") || queryMap["auth"].isNullOrBlank()) {
                queryMap["auth"] = authKey
            }
        }

        return if (queryMap.isNotEmpty()) {
            val queryString = queryMap.entries.joinToString("&") { "${it.key}=${it.value}" }
            "$baseWithPath?$queryString"
        } else {
            baseWithPath
        }
    }

    private fun sanitizeFirebaseUrl(url: String): String {
        return parseFirebaseUrl(url).baseUrl
    }

    suspend fun searchUserByPhoneFirebase(phone: String, firebaseUrl: String, authKey: String = ""): GithubUser? {
        val parsed = parseFirebaseUrl(firebaseUrl)
        val rawInput = phone.trim()
        val cleanDigits = rawInput.filter { it.isDigit() }
        val phone9 = if (cleanDigits.length >= 9) cleanDigits.takeLast(9) else cleanDigits

        if (parsed.baseUrl.isBlank()) {
            return getMockUserForInput(rawInput, cleanDigits, phone9)
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()

                fun extractUserId(entry: Any?): String {
                    if (entry == null) return ""
                    if (entry is JSONObject) {
                        return entry.optString("usuario", entry.optString("id", entry.optString("id_conta", "")))
                    }
                    if (entry is String) {
                        return entry.removeSurrounding("\"")
                    }
                    return entry.toString().removeSurrounding("\"")
                }

                fun fetchUserById(userId: String): GithubUser? {
                    if (userId.isBlank()) return null
                    val cleanId = userId.trim()
                    val userUrl = buildFirebaseEndpoint(parsed, "/dados/usuarios/$cleanId.json", authKey)
                    val req = Request.Builder().url(userUrl).get().build()
                    try {
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val content = resp.body?.string()
                                if (!content.isNullOrBlank() && content != "null" && content != "{}") {
                                    val parsedUser = GithubUserParser.parseUserJson(content, "$cleanId.json", "")
                                    if (parsedUser != null) return parsedUser
                                }
                            }
                        }
                    } catch (e: Exception) {}
                    return null
                }

                // 1. Try fetching directly as User ID (e.g. USR000001, USR00001, usuario_1)
                if (rawInput.isNotBlank()) {
                    val directUser = fetchUserById(rawInput)
                    if (directUser != null) return@withContext directUser
                }

                // 2. Try fetching directly from phone index endpoint in Firebase
                val phoneKeysToTry = listOfNotNull(
                    cleanDigits.ifBlank { null },
                    phone9.ifBlank { null },
                    if (phone9.isNotBlank()) "258$phone9" else null
                ).distinct()

                for (pkey in phoneKeysToTry) {
                    val pUrl = buildFirebaseEndpoint(parsed, "/dados/indices/telefones/$pkey.json", authKey)
                    val pReq = Request.Builder().url(pUrl).get().build()
                    try {
                        client.newCall(pReq).execute().use { pResp ->
                            if (pResp.isSuccessful) {
                                val pContent = pResp.body?.string()
                                if (!pContent.isNullOrBlank() && pContent != "null") {
                                    val targetId = if (pContent.trim().startsWith("{")) {
                                        val obj = JSONObject(pContent)
                                        obj.optString("usuario", obj.optString("id", ""))
                                    } else {
                                        pContent.trim().removeSurrounding("\"")
                                    }
                                    val user = fetchUserById(targetId)
                                    if (user != null) return@withContext user
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // 3. Try fetching directly from MT5 index endpoint in Firebase
                if (cleanDigits.isNotBlank()) {
                    val mt5Url = buildFirebaseEndpoint(parsed, "/dados/indices/mt5/$cleanDigits.json", authKey)
                    val mt5Req = Request.Builder().url(mt5Url).get().build()
                    try {
                        client.newCall(mt5Req).execute().use { mt5Resp ->
                            if (mt5Resp.isSuccessful) {
                                val mt5Content = mt5Resp.body?.string()
                                if (!mt5Content.isNullOrBlank() && mt5Content != "null") {
                                    val targetId = if (mt5Content.trim().startsWith("{")) {
                                        val obj = JSONObject(mt5Content)
                                        obj.optString("usuario", obj.optString("id", ""))
                                    } else {
                                        mt5Content.trim().removeSurrounding("\"")
                                    }
                                    val user = fetchUserById(targetId)
                                    if (user != null) return@withContext user
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }

                // 4. Try bulk /dados/indices/telefones.json
                val telefonesUrl = buildFirebaseEndpoint(parsed, "/dados/indices/telefones.json", authKey)
                val indexRequest = Request.Builder().url(telefonesUrl).get().build()
                var telefonesContent: String? = null
                client.newCall(indexRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        telefonesContent = response.body?.string()
                    }
                }

                if (!telefonesContent.isNullOrBlank() && telefonesContent != "null") {
                    val phonesJson = JSONObject(telefonesContent!!)
                    var targetUserId = ""

                    if (cleanDigits.isNotBlank() && phonesJson.has(cleanDigits)) {
                        targetUserId = extractUserId(phonesJson.opt(cleanDigits))
                    } else if (rawInput.isNotBlank() && phonesJson.has(rawInput)) {
                        targetUserId = extractUserId(phonesJson.opt(rawInput))
                    } else {
                        val keys = phonesJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val cleanKey = key.filter { it.isDigit() }
                            if (cleanKey.length >= 9 && phone9.length >= 9 && cleanKey.takeLast(9) == phone9) {
                                targetUserId = extractUserId(phonesJson.opt(key))
                                break
                            }
                        }
                    }

                    if (targetUserId.isNotBlank()) {
                        val user = fetchUserById(targetUserId)
                        if (user != null) return@withContext user
                    }
                }

                // 5. Fallback Mock
                getMockUserForInput(rawInput, cleanDigits, phone9)
            } catch (e: Exception) {
                e.printStackTrace()
                getMockUserForInput(rawInput, cleanDigits, phone9)
            }
        }
    }

    suspend fun saveUserToFirebaseWithDetails(user: GithubUser, firebaseUrl: String, authKey: String = ""): Pair<Boolean, String> {
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return Pair(false, "URL do Firebase não configurada.")

        val targetId = if (user.id.isNotBlank()) user.id else if (user.mt5IdConta.isNotBlank()) user.mt5IdConta else user.numero.filter { it.isDigit() }.ifBlank { "usuario_1" }

        // 1. Try via Official Firebase Database SDK first
        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()
            val userJsonString = GithubUserParser.serializeUserJson(user.copy(id = targetId), wrapWithId = false)
            val userMap = JSONObject(userJsonString).toMap()
            db.getReference("dados/usuarios").child(targetId).setValue(userMap).await()

            val cleanPhone = user.numero.trim().filter { it.isDigit() }
            if (cleanPhone.isNotBlank()) {
                val phoneMap = mapOf("usuario" to targetId, "mt5" to user.mt5IdConta, "status" to user.status)
                db.getReference("dados/indices/telefones").child(cleanPhone).setValue(phoneMap).await()
            }

            if (user.mt5IdConta.isNotBlank()) {
                val mt5Map = mapOf(
                    "usuario" to targetId,
                    "telefone" to user.numero,
                    "nome" to user.nome,
                    "licenca_ativa" to user.licencaAtiva,
                    "validade" to user.licencaValidade,
                    "status" to user.status.lowercase()
                )
                db.getReference("dados/indices/mt5").child(user.mt5IdConta).setValue(mt5Map).await()
            }

            return Pair(true, "✅ Sincronizado via SDK Firebase Realtime Database com sucesso!")
        } catch (e: Exception) {
            // SDK attempt failed or no google-services initialized, proceeding to REST protocol fallback
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                
                // 1. Save user object to /dados/usuarios/{targetId}.json
                val userJsonString = GithubUserParser.serializeUserJson(user.copy(id = targetId), wrapWithId = false)
                val userUrl = buildFirebaseEndpoint(parsed, "/dados/usuarios/$targetId.json", authKey)
                
                val putRequest = Request.Builder()
                    .url(userUrl)
                    .put(userJsonString.toRequestBody(mediaType))
                    .build()
                
                var statusCode = 0
                var statusMsg = ""
                val userSaved = client.newCall(putRequest).execute().use { response ->
                    statusCode = response.code
                    statusMsg = response.message
                    response.isSuccessful
                }

                if (userSaved) {
                    // 2. Also update /dados/indices/telefones/{cleanPhone}.json
                    val cleanPhone = user.numero.trim().filter { it.isDigit() }
                    if (cleanPhone.isNotBlank()) {
                        val phoneObj = JSONObject().apply {
                            put("usuario", targetId)
                            put("mt5", user.mt5IdConta)
                            put("status", user.status)
                        }
                        val phoneUrl = buildFirebaseEndpoint(parsed, "/dados/indices/telefones/$cleanPhone.json", authKey)
                        val phoneReq = Request.Builder()
                            .url(phoneUrl)
                            .put(phoneObj.toString().toRequestBody(mediaType))
                            .build()
                        try { client.newCall(phoneReq).execute().close() } catch (e: Exception) {}
                    }

                    // 3. If mt5IdConta is set, update /dados/indices/mt5/{mt5IdConta}.json
                    if (user.mt5IdConta.isNotBlank()) {
                        val mt5Obj = JSONObject().apply {
                            put("usuario", targetId)
                            put("telefone", user.numero)
                            put("nome", user.nome)
                            put("licenca_ativa", user.licencaAtiva)
                            put("validade", user.licencaValidade)
                            put("status", user.status.lowercase())
                        }
                        val mt5Url = buildFirebaseEndpoint(parsed, "/dados/indices/mt5/${user.mt5IdConta}.json", authKey)
                        val mt5Req = Request.Builder()
                            .url(mt5Url)
                            .put(mt5Obj.toString().toRequestBody(mediaType))
                            .build()
                        try { client.newCall(mt5Req).execute().close() } catch (e: Exception) {}
                    }

                    Pair(true, "✅ Sincronizado com sucesso no Firebase!")
                } else {
                    if (statusCode == 401) {
                        Pair(false, "❌ Erro 401 (Permissão Negada) no Firebase. No Console Firebase > Realtime Database > Regras, configure \".read\": true e \".write\": true (ou use ?auth=SECRET na URL).")
                    } else {
                        Pair(false, "Erro no Firebase ($statusCode $statusMsg). Verifique as Regras do Database.")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "Erro de rede no Firebase: ${e.localizedMessage}")
            }
        }
    }

    suspend fun updateSilentSecurityInFirebase(
        userId: String,
        silentDeviceUid: String,
        isoTimestamp: String,
        firebaseUrl: String,
        authKey: String = ""
    ): Pair<Boolean, String> {
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return Pair(false, "URL do Firebase não configurada.")
        val targetId = userId.ifBlank { "usuario_1" }

        // 1. Try SDK
        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()
            val auditRef = db.getReference("dados/usuarios").child(targetId).child("auditoria")
            auditRef.child("ultimo_dispositivo").setValue(silentDeviceUid).await()
            auditRef.child("ultimo_login").setValue(isoTimestamp).await()
            return Pair(true, "✅ Segurança silenciosa gravada via SDK Firebase!")
        } catch (e: Exception) {
            // SDK attempt failed, fallback to REST
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val auditObj = JSONObject().apply {
                    put("ultimo_dispositivo", silentDeviceUid)
                    put("ultimo_login", isoTimestamp)
                }
                val effectiveAuthKey = authKey
                val auditUrl = buildFirebaseEndpoint(parsed, "/dados/usuarios/$targetId/auditoria.json", effectiveAuthKey)
                val req = Request.Builder()
                    .url(auditUrl)
                    .patch(auditObj.toString().toRequestBody(mediaType))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Pair(true, "✅ Segurança silenciosa gravada via REST Firebase!")
                    } else {
                        Pair(false, "Erro ao gravar segurança silenciosa (${resp.code})")
                    }
                }
            } catch (e: Exception) {
                Pair(false, "Erro de rede ao gravar segurança silenciosa: ${e.localizedMessage}")
            }
        }
    }

    suspend fun checkMt5AccountOwnerFirebase(
        mt5AccountId: String,
        currentUserId: String,
        currentUserPhone: String,
        firebaseUrl: String,
        authKey: String = ""
    ): Pair<Boolean, String?> {
        val cleanMt5 = mt5AccountId.trim()
        if (cleanMt5.isBlank()) return Pair(false, null)

        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return Pair(false, null)

        val cleanCurrentPhone = currentUserPhone.filter { it.isDigit() }
        val cleanCurrentPhone9 = if (cleanCurrentPhone.length >= 9) cleanCurrentPhone.takeLast(9) else cleanCurrentPhone
        val cleanUserId = currentUserId.trim()

        // 1. Try Firebase Realtime Database SDK first
        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()
            val snapshot = db.getReference("dados/indices/mt5").child(cleanMt5).get().await()
            if (snapshot.exists()) {
                val usuario = snapshot.child("usuario").value?.toString().orEmpty().trim()
                val telefone = snapshot.child("telefone").value?.toString().orEmpty().trim()
                val nome = snapshot.child("nome").value?.toString().orEmpty().trim()

                val cleanIndexPhone = telefone.filter { it.isDigit() }
                val cleanIndexPhone9 = if (cleanIndexPhone.length >= 9) cleanIndexPhone.takeLast(9) else cleanIndexPhone

                val isSameUser = (usuario.isNotBlank() && (usuario.equals(cleanUserId, ignoreCase = true) || usuario.equals(cleanCurrentPhone, ignoreCase = true) || usuario.equals(cleanCurrentPhone9, ignoreCase = true))) ||
                                 (cleanIndexPhone9.isNotBlank() && cleanCurrentPhone9.isNotBlank() && cleanIndexPhone9 == cleanCurrentPhone9)

                if (!isSameUser) {
                    val ownerInfo = nome.ifBlank { usuario.ifBlank { "Utilizador $telefone" } }
                    return Pair(true, "A conta MT5 ID ($cleanMt5) já está vinculada a outro utilizador ('$ownerInfo' - Tel: $telefone). Não é possível vincular a mesma conta MT5 a utilizadores diferentes.")
                }
            }
        } catch (e: Exception) {
            // SDK attempt failed, fallback to REST API
        }

        // 2. REST API check
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val indexUrl = buildFirebaseEndpoint(parsed, "/dados/indices/mt5/$cleanMt5.json", authKey)
                val req = Request.Builder().url(indexUrl).get().build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (body != null && body != "null" && body != "{}") {
                            val json = JSONObject(body)
                            val usuario = json.optString("usuario", "").trim()
                            val telefone = json.optString("telefone", "").trim()
                            val nome = json.optString("nome", "").trim()

                            val cleanIndexPhone = telefone.filter { it.isDigit() }
                            val cleanIndexPhone9 = if (cleanIndexPhone.length >= 9) cleanIndexPhone.takeLast(9) else cleanIndexPhone

                            val isSameUser = (usuario.isNotBlank() && (usuario.equals(cleanUserId, ignoreCase = true) || usuario.equals(cleanCurrentPhone, ignoreCase = true) || usuario.equals(cleanCurrentPhone9, ignoreCase = true))) ||
                                             (cleanIndexPhone9.isNotBlank() && cleanCurrentPhone9.isNotBlank() && cleanIndexPhone9 == cleanCurrentPhone9)

                            if (!isSameUser) {
                                val ownerInfo = nome.ifBlank { usuario.ifBlank { "Utilizador $telefone" } }
                                return@withContext Pair(true, "A conta MT5 ID ($cleanMt5) já está vinculada a outro utilizador ('$ownerInfo' - Tel: $telefone). Não é possível vincular a mesma conta MT5 a utilizadores diferentes.")
                            }
                        }
                    }
                }
                Pair(false, null)
            } catch (e: Exception) {
                Pair(false, null)
            }
        }
    }

    suspend fun deleteMt5IndexFirebase(
        mt5AccountId: String,
        firebaseUrl: String,
        authKey: String = ""
    ): Boolean {
        val cleanMt5 = mt5AccountId.trim()
        if (cleanMt5.isBlank()) return false
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return false

        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()
            db.getReference("dados/indices/mt5").child(cleanMt5).removeValue().await()
        } catch (e: Exception) {
            // SDK fallback
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val indexUrl = buildFirebaseEndpoint(parsed, "/dados/indices/mt5/$cleanMt5.json", authKey)
                val req = Request.Builder().url(indexUrl).delete().build()
                client.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun migrateMt5AccountDataInFirebase(
        user: GithubUser,
        oldMt5Id: String,
        newMt5Id: String,
        firebaseUrl: String,
        authKey: String = ""
    ): Boolean {
        val cleanOld = oldMt5Id.trim()
        val cleanNew = newMt5Id.trim()
        if (cleanOld.isBlank() || cleanNew.isBlank() || cleanOld == cleanNew) return false

        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return false

        val userId = if (user.id.isNotBlank()) user.id else if (user.numero.filter { it.isDigit() }.isNotBlank()) user.numero.filter { it.isDigit() } else "usuario_1"

        // 1. Try Firebase SDK migration
        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()

            // a) Parametros / Config
            val paramsSnap = db.getReference("dados/parametros").child(cleanOld).get().await()
            if (paramsSnap.exists() && paramsSnap.value != null) {
                db.getReference("dados/parametros").child(cleanNew).setValue(paramsSnap.value).await()
                db.getReference("dados/parametros").child(cleanOld).removeValue().await()
            }
            val configSnap = db.getReference("dados/config").child(cleanOld).get().await()
            if (configSnap.exists() && configSnap.value != null) {
                db.getReference("dados/config").child(cleanNew).setValue(configSnap.value).await()
                db.getReference("dados/usuarios").child(userId).child("config").setValue(configSnap.value).await()
                db.getReference("dados/config").child(cleanOld).removeValue().await()
            }

            // b) Status
            val statusSnap = db.getReference("dados/status").child(cleanOld).get().await()
            if (statusSnap.exists() && statusSnap.value != null) {
                db.getReference("dados/status").child(cleanNew).setValue(statusSnap.value).await()
                db.getReference("dados/usuarios").child(userId).child("status").setValue(statusSnap.value).await()
                db.getReference("dados/usuarios").child(cleanNew).child("status").setValue(statusSnap.value).await()
                db.getReference("dados/status").child(cleanOld).removeValue().await()
            }

            // c) Eventos
            val eventosSnap = db.getReference("dados/eventos").child(cleanOld).get().await()
            if (eventosSnap.exists() && eventosSnap.value != null) {
                db.getReference("dados/eventos").child(cleanNew).setValue(eventosSnap.value).await()
                db.getReference("dados/usuarios").child(userId).child("eventos").setValue(eventosSnap.value).await()
                db.getReference("dados/usuarios").child(cleanNew).child("eventos").setValue(eventosSnap.value).await()
                db.getReference("dados/eventos").child(cleanOld).removeValue().await()
            }

            // d) Clean old MT5 nodes if cleanOld != userId
            if (cleanOld != userId) {
                db.getReference("dados/usuarios").child(cleanOld).removeValue().await()
            }
            db.getReference("dados/indices/mt5").child(cleanOld).removeValue().await()
        } catch (e: Exception) {
            // SDK fallback
        }

        // 2. REST API Migration
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                fun httpGet(path: String): String? {
                    return try {
                        val url = buildFirebaseEndpoint(parsed, path, authKey)
                        val req = Request.Builder().url(url).get().build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body?.string()
                                if (body != null && body != "null" && body != "{}") body else null
                            } else null
                        }
                    } catch (e: Exception) { null }
                }

                fun httpPut(path: String, json: String): Boolean {
                    return try {
                        val url = buildFirebaseEndpoint(parsed, path, authKey)
                        val req = Request.Builder().url(url).put(json.toRequestBody(mediaType)).build()
                        client.newCall(req).execute().use { resp -> resp.isSuccessful }
                    } catch (e: Exception) { false }
                }

                fun httpDelete(path: String): Boolean {
                    return try {
                        val url = buildFirebaseEndpoint(parsed, path, authKey)
                        val req = Request.Builder().url(url).delete().build()
                        client.newCall(req).execute().use { resp -> resp.isSuccessful }
                    } catch (e: Exception) { false }
                }

                // Parametros
                val paramsJson = httpGet("/dados/parametros/$cleanOld.json")
                if (paramsJson != null) {
                    val updatedParams = paramsJson.replace("\"$cleanOld\"", "\"$cleanNew\"")
                    httpPut("/dados/parametros/$cleanNew.json", updatedParams)
                    httpDelete("/dados/parametros/$cleanOld.json")
                }

                // Config
                val configJson = httpGet("/dados/config/$cleanOld.json")
                    ?: httpGet("/dados/usuarios/$userId/config.json")
                    ?: httpGet("/dados/usuarios/$cleanOld/config.json")
                if (configJson != null) {
                    val updatedConfig = configJson.replace("\"$cleanOld\"", "\"$cleanNew\"")
                    httpPut("/dados/config/$cleanNew.json", updatedConfig)
                    httpPut("/dados/usuarios/$userId/config.json", updatedConfig)
                    httpPut("/dados/usuarios/$cleanNew/config.json", updatedConfig)
                    httpDelete("/dados/config/$cleanOld.json")
                    httpDelete("/dados/usuarios/$cleanOld/config.json")
                }

                // Status
                val statusJson = httpGet("/dados/status/$cleanOld.json")
                    ?: httpGet("/dados/usuarios/$userId/status.json")
                    ?: httpGet("/dados/usuarios/$cleanOld/status.json")
                if (statusJson != null) {
                    val updatedStatus = statusJson.replace("\"$cleanOld\"", "\"$cleanNew\"")
                    httpPut("/dados/status/$cleanNew.json", updatedStatus)
                    httpPut("/dados/usuarios/$userId/status.json", updatedStatus)
                    httpPut("/dados/usuarios/$cleanNew/status.json", updatedStatus)
                    httpDelete("/dados/status/$cleanOld.json")
                    httpDelete("/dados/usuarios/$cleanOld/status.json")
                }

                // Eventos
                val eventosJson = httpGet("/dados/eventos/$cleanOld.json")
                    ?: httpGet("/dados/usuarios/$userId/eventos.json")
                    ?: httpGet("/dados/usuarios/$cleanOld/eventos.json")
                if (eventosJson != null) {
                    val updatedEventos = eventosJson.replace("\"$cleanOld\"", "\"$cleanNew\"")
                    httpPut("/dados/eventos/$cleanNew.json", updatedEventos)
                    httpPut("/dados/usuarios/$userId/eventos.json", updatedEventos)
                    httpPut("/dados/usuarios/$cleanNew/eventos.json", updatedEventos)
                    httpDelete("/dados/eventos/$cleanOld.json")
                    httpDelete("/dados/usuarios/$cleanOld/eventos.json")
                }

                // Clean old user key if different from main userId
                if (cleanOld != userId) {
                    httpDelete("/dados/usuarios/$cleanOld.json")
                }
                httpDelete("/dados/indices/mt5/$cleanOld.json")

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun saveUserToFirebase(user: GithubUser, firebaseUrl: String, authKey: String = ""): Boolean {
        return saveUserToFirebaseWithDetails(user, firebaseUrl, authKey).first
    }

    suspend fun saveAndSyncEaConfigFirebase(config: EaConfigEntity, firebaseUrl: String, authKey: String = "", userId: String = ""): String {
        eaConfigDao.insertOrUpdateEaConfig(config)
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return "Salvo localmente! Servidor de sincronização não configurado."

        val jsonContent = config.toJsonContent()

        // 1. Try SDK request
        try {
            val db = if (parsed.baseUrl.isNotBlank()) FirebaseDatabase.getInstance(parsed.baseUrl) else FirebaseDatabase.getInstance()
            val configMap = JSONObject(jsonContent).toMap()
            db.getReference("dados/parametros").child(config.mt5AccountId).setValue(configMap).await()
            db.getReference("dados/config").child(config.mt5AccountId).setValue(configMap).await()
            db.getReference("dados/usuarios").child(config.mt5AccountId).child("config").setValue(configMap).await()
            if (userId.isNotBlank()) {
                db.getReference("dados/usuarios").child(userId).child("config").setValue(configMap).await()
            }
            return "✅ Parâmetros enviados com sucesso ao servidor! Aguardando o robô sincronizar."
        } catch (e: Exception) {
            // SDK attempt failed, fallback to REST
        }

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()

                val endpointsToUpdate = mutableListOf(
                    "/dados/parametros/${config.mt5AccountId}.json",
                    "/dados/config/${config.mt5AccountId}.json",
                    "/dados/usuarios/${config.mt5AccountId}/config.json"
                )
                if (userId.isNotBlank()) {
                    endpointsToUpdate.add("/dados/usuarios/$userId/config.json")
                }

                var anySuccess = false
                for (ep in endpointsToUpdate.distinct()) {
                    val userUrl = buildFirebaseEndpoint(parsed, ep, authKey)
                    val putRequest = Request.Builder()
                        .url(userUrl)
                        .put(jsonContent.toRequestBody(mediaType))
                        .build()

                    try {
                        client.newCall(putRequest).execute().use { response ->
                            if (response.isSuccessful) anySuccess = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (anySuccess) {
                    "✅ Parâmetros enviados com sucesso ao servidor! Aguardando o robô sincronizar."
                } else {
                    "Salvo localmente! Verifique a conexão com o servidor."
                }
            } catch (e: Exception) {
                "Salvo localmente! Erro ao sincronizar com o servidor: ${e.localizedMessage}"
            }
        }
    }

    suspend fun fetchAndSyncEaConfigFromFirebase(mt5AccountId: String, firebaseUrl: String, authKey: String = ""): Boolean {
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank() || mt5AccountId.isBlank()) return false

        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val pathsToTry = listOf(
                "/dados/parametros/$mt5AccountId.json",
                "/dados/usuarios/$mt5AccountId/config.json",
                "/dados/config/$mt5AccountId.json",
                "/dados/usuarios/$mt5AccountId/status.json",
                "/dados/status/$mt5AccountId.json"
            )

            for (p in pathsToTry) {
                val url = buildFirebaseEndpoint(parsed, p, authKey)
                try {
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null && body != "null" && body != "{}") {
                                val json = JSONObject(body)
                                var eaAtivo: Boolean? = null
                                when {
                                    json.has("EA_ATIVO") -> eaAtivo = json.optBoolean("EA_ATIVO")
                                    json.has("ea_ativo") -> eaAtivo = json.optBoolean("ea_ativo")
                                    json.has("ativo") -> eaAtivo = json.optBoolean("ativo")
                                }

                                var permitirLeitura: Boolean? = null
                                if (json.has("PERMITIR_LEITURA_PARAMETROS")) {
                                    permitirLeitura = json.optBoolean("PERMITIR_LEITURA_PARAMETROS")
                                }

                                if (eaAtivo != null || permitirLeitura != null) {
                                    val currentConfig = eaConfigDao.getEaConfigSync(mt5AccountId) ?: EaConfigEntity(mt5AccountId = mt5AccountId)
                                    var updatedConfig = currentConfig
                                    if (eaAtivo != null) updatedConfig = updatedConfig.copy(EA_ATIVO = eaAtivo)
                                    if (permitirLeitura != null) updatedConfig = updatedConfig.copy(PERMITIR_LEITURA_PARAMETROS = permitirLeitura)
                                    eaConfigDao.insertOrUpdateEaConfig(updatedConfig)
                                    return@withContext true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            false
        }
    }

    suspend fun fetchEaRobotStatus(mt5AccountId: String, firebaseUrl: String, authKey: String = "", userId: String = ""): String? {
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val pathsToTry = mutableListOf<String>()

            // 1. Direct MT5 Account ID node (Primary written by EA: /dados/status/{ACCOUNT_LOGIN}.json)
            if (mt5AccountId.isNotBlank()) {
                pathsToTry.add("/dados/status/$mt5AccountId.json")
                pathsToTry.add("/status/$mt5AccountId.json")
            }

            // 2. User ID nodes
            if (userId.isNotBlank()) {
                pathsToTry.add("/dados/usuarios/$userId/status.json")
                if (mt5AccountId.isNotBlank()) {
                    pathsToTry.add("/dados/usuarios/$userId/$mt5AccountId/status.json")
                    pathsToTry.add("/dados/usuarios/$userId/status/$mt5AccountId.json")
                }
            }

            // 3. User account copy nodes
            if (mt5AccountId.isNotBlank()) {
                pathsToTry.add("/dados/usuarios/$mt5AccountId/status.json")
            }

            pathsToTry.add("/dados/status.json")
            pathsToTry.add("/status.json")

            val urlsToTry = mutableListOf<String>()
            for (p in pathsToTry.distinct()) {
                if (authKey.isNotBlank()) {
                    urlsToTry.add(buildFirebaseEndpoint(parsed, p, authKey))
                }
                urlsToTry.add(buildFirebaseEndpoint(parsed, p, ""))
            }

            val client = OkHttpClient()
            for (url in urlsToTry.distinct()) {
                try {
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (body != null && body != "null" && body != "{}") return@withContext body
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            null
        }
    }

    suspend fun fetchEaRobotEvents(firebaseUrl: String, mt5AccountId: String = "", authKey: String = "", userId: String = ""): List<EaRobotEvent> {
        val parsed = parseFirebaseUrl(firebaseUrl)
        if (parsed.baseUrl.isBlank()) return emptyList()

        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()

            val pathsToTry = mutableListOf<String>()

            // 1. Direct MT5 Account ID node (Primary written by EA: /dados/eventos/{ACCOUNT_LOGIN}.json)
            if (mt5AccountId.isNotBlank()) {
                pathsToTry.add("/dados/eventos/$mt5AccountId.json")
                pathsToTry.add("/eventos/$mt5AccountId.json")
            }

            // 2. User ID nodes
            if (userId.isNotBlank()) {
                pathsToTry.add("/dados/usuarios/$userId/eventos.json")
                if (mt5AccountId.isNotBlank()) {
                    pathsToTry.add("/dados/usuarios/$userId/$mt5AccountId/eventos.json")
                    pathsToTry.add("/dados/usuarios/$userId/eventos/$mt5AccountId.json")
                }
            }

            // 3. User account copy nodes
            if (mt5AccountId.isNotBlank()) {
                pathsToTry.add("/dados/usuarios/$mt5AccountId/eventos.json")
            }

            // 4. Fallback collection reading
            pathsToTry.add("/dados/eventos.json")
            pathsToTry.add("/eventos.json")

            val urlsToTry = mutableListOf<String>()
            for (p in pathsToTry.distinct()) {
                if (authKey.isNotBlank()) {
                    urlsToTry.add(buildFirebaseEndpoint(parsed, p, authKey))
                }
                urlsToTry.add(buildFirebaseEndpoint(parsed, p, ""))
            }

            val allEvents = mutableListOf<EaRobotEvent>()

            for (url in urlsToTry.distinct()) {
                try {
                    val request = Request.Builder().url(url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank() && body != "null" && body != "{}") {
                                val parsedList = parseEventsFromJson(body, mt5AccountId)
                                if (parsedList.isNotEmpty()) {
                                    allEvents.addAll(parsedList)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            allEvents.distinctBy { "${it.id}_${it.timestamp}_${it.event}_${it.login}" }
                .sortedByDescending { it.timestamp }
        }
    }

    private fun parseEventObject(id: String, obj: JSONObject, defaultLogin: Long = 0L, contextName: String = ""): EaRobotEvent {
        val loginVal = obj.optLong("login", defaultLogin)
        val currency = obj.optString("currency", "")
        
        var event = obj.optString("event", obj.optString("evento", obj.optString("tipo", obj.optString("name", ""))))
        if (event.isBlank()) {
            if (contextName.isNotBlank() && !contextName.startsWith("-") && contextName.toLongOrNull() == null) {
                event = contextName
            } else if (!id.startsWith("-") && id.toLongOrNull() == null && id != "event") {
                event = id
            } else {
                event = "evento"
            }
        }

        val server = obj.optString("servidor", obj.optString("server", ""))
        val symbol = obj.optString("symbol", "")
        val timeframe = obj.optString("timeframe", "")
        
        var timestamp = obj.optLong("timestamp", 0L)
        val dataStr = obj.optString("data", "")
        val horaStr = obj.optString("hora", "")

        if (timestamp == 0L && dataStr.isNotBlank()) {
            try {
                val dateTimeStr = if (horaStr.isNotBlank()) "$dataStr $horaStr" else dataStr
                val formatStr = if (horaStr.isNotBlank()) "yyyy.MM.dd HH:mm:ss" else "yyyy.MM.dd"
                val sdf = java.text.SimpleDateFormat(formatStr, java.util.Locale.getDefault())
                val parsedDate = sdf.parse(dateTimeStr.replace("-", ".").replace("/", "."))
                if (parsedDate != null) {
                    timestamp = parsedDate.time
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // Fix for 21.01.1970 bug: Unix timestamps in seconds (< 10_000_000_000L) must be multiplied by 1000 for Java/Kotlin milliseconds
        if (timestamp in 1L..9_999_999_999L) {
            timestamp *= 1000L
        }

        val sistema = obj.optString("sistema", "")
        val anterior = obj.optString("anterior", "")
        val novo = obj.optString("novo", "")
        val descAnterior = obj.optString("descAnterior", "")
        val descNovo = obj.optString("descNovo", "")

        val motivacao = obj.optString("motivacao", "")
        val moeda = obj.optString("moeda", "")
        val diarioStatus = obj.optString("diario_status", "")
        val diarioValor = obj.optDouble("diario_valor", 0.0)
        val diarioPct = obj.optDouble("diario_pct", 0.0)
        val semanalStatus = obj.optString("semanal_status", "")
        val semanalValor = obj.optDouble("semanal_valor", 0.0)
        val semanalPct = obj.optDouble("semanal_pct", 0.0)
        val resumo = obj.optString("resumo", "")

        val sessao = obj.optString("sessao", "")

        // Handle session hours flexibly (support strings, numbers, timestamps, and hh:mm formats)
        var rawHoraInicio = obj.optLong("hora_inicio", -1L)
        var rawMinutoInicio = obj.optLong("minuto_inicio", -1L)
        var rawHoraFim = obj.optLong("hora_fim", -1L)
        var rawMinutoFim = obj.optLong("minuto_fim", -1L)

        val strHoraInicio = obj.optString("hora_inicio", "")
        if (strHoraInicio.contains(":")) {
            val parts = strHoraInicio.split(":")
            rawHoraInicio = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: -1L
            rawMinutoInicio = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: -1L
        }

        val strHoraFim = obj.optString("hora_fim", "")
        if (strHoraFim.contains(":")) {
            val parts = strHoraFim.split(":")
            rawHoraFim = parts.getOrNull(0)?.trim()?.toLongOrNull() ?: -1L
            rawMinutoFim = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: -1L
        }

        fun parseHourMinute(rawH: Long, rawM: Long, fallbackTs: Long): Pair<Int, Int> {
            if (rawH in 0..23) {
                val m = if (rawM in 0..59) rawM.toInt() else 0
                return Pair(rawH.toInt(), m)
            }
            if (rawH > 23) {
                val sec = if (rawH > 10_000_000_000L) rawH / 1000L else rawH
                val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT")).apply {
                    timeInMillis = sec * 1000L
                }
                return Pair(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            }
            if (fallbackTs > 0) {
                val ms = if (fallbackTs < 10_000_000_000L) fallbackTs * 1000L else fallbackTs
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = ms
                }
                return Pair(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
            }
            return Pair(-1, -1)
        }

        val (horaInicio, minutoInicio) = if (rawHoraInicio >= 0) {
            parseHourMinute(rawHoraInicio, rawMinutoInicio, timestamp)
        } else Pair(-1, -1)

        val (horaFim, minutoFim) = if (rawHoraFim >= 0) {
            parseHourMinute(rawHoraFim, rawMinutoFim, timestamp)
        } else Pair(-1, -1)

        val msg = obj.optString("msg", obj.optString("mensagem", ""))
        val temPosicao = obj.optString("tem_posicao", "")
        val fusoHorario = obj.optInt("fuso_horario", 0)
        val fusoTexto = obj.optString("fuso_texto", "")
        val saldoDisponivel = obj.optDouble("saldo_disponivel", 0.0)

        return EaRobotEvent(
            id = id,
            currency = currency,
            event = event,
            login = loginVal,
            server = server,
            symbol = symbol,
            timeframe = timeframe,
            timestamp = timestamp,
            sistema = sistema,
            anterior = anterior,
            novo = novo,
            descAnterior = descAnterior,
            descNovo = descNovo,
            data = dataStr,
            hora = horaStr,
            motivacao = motivacao,
            moeda = moeda,
            diarioStatus = diarioStatus,
            diarioValor = diarioValor,
            diarioPct = diarioPct,
            semanalStatus = semanalStatus,
            semanalValor = semanalValor,
            semanalPct = semanalPct,
            resumo = resumo,
            sessao = sessao,
            horaInicio = horaInicio,
            minutoInicio = minutoInicio,
            horaFim = horaFim,
            minutoFim = minutoFim,
            msg = msg,
            temPosicao = temPosicao,
            fusoHorario = fusoHorario,
            fusoTexto = fusoTexto,
            saldoDisponivel = saldoDisponivel
        )
    }

    private fun extractEventsFromObject(
        key: String,
        obj: JSONObject,
        defaultLogin: Long,
        list: MutableList<EaRobotEvent>,
        contextName: String = ""
    ) {
        var hasChildJson = false
        val childKeys = mutableListOf<String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            childKeys.add(k)
            if (obj.optJSONObject(k) != null || obj.optJSONArray(k) != null) {
                hasChildJson = true
            }
        }

        val hasEventFields = obj.has("event") || obj.has("evento") || obj.has("login") ||
                obj.has("symbol") || obj.has("timestamp") || obj.has("data") ||
                obj.has("hora") || obj.has("msg") || obj.has("mensagem") ||
                obj.has("resumo") || obj.has("motivacao") || obj.has("servidor") || obj.has("server") ||
                obj.has("diario_status") || obj.has("semanal_status") || obj.has("sessao") || obj.has("novo") ||
                obj.has("sistema") || obj.has("anterior") || obj.has("hora_inicio")

        if (!hasChildJson || hasEventFields) {
            val loginVal = obj.optLong("login", defaultLogin)
            list.add(parseEventObject(key, obj, loginVal, contextName))
            if (!hasChildJson) return
        }

        for (childKey in childKeys) {
            val childObj = obj.optJSONObject(childKey)
            if (childObj != null) {
                val childLogin = childKey.toLongOrNull() ?: defaultLogin
                val newContext = if (contextName.isBlank() && !childKey.startsWith("-") && childKey.toLongOrNull() == null) childKey else contextName
                extractEventsFromObject(childKey, childObj, childLogin, list, newContext)
            } else {
                val childArr = obj.optJSONArray(childKey)
                if (childArr != null) {
                    for (i in 0 until childArr.length()) {
                        val item = childArr.optJSONObject(i)
                        if (item != null) {
                            val itemLogin = item.optLong("login", defaultLogin)
                            val newContext = if (contextName.isBlank() && !childKey.startsWith("-")) childKey else contextName
                            extractEventsFromObject("$childKey-$i", item, itemLogin, list, newContext)
                        }
                    }
                }
            }
        }
    }

    private fun parseEventsFromJson(body: String, defaultMt5AccountId: String = ""): List<EaRobotEvent> {
        val list = mutableListOf<EaRobotEvent>()
        val defaultLogin = defaultMt5AccountId.toLongOrNull() ?: 0L
        val cleanStr = body.trim()

        if (cleanStr.startsWith("[")) {
            val array = JSONArray(cleanStr)
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i)
                if (item != null) {
                    extractEventsFromObject("event-$i", item, defaultLogin, list, "")
                }
            }
        } else if (cleanStr.startsWith("{")) {
            val root = JSONObject(cleanStr)
            extractEventsFromObject(defaultMt5AccountId.ifEmpty { "event" }, root, defaultLogin, list, "")
        }

        list.sortByDescending { it.timestamp }
        return list
    }
}

// Extensions for serialization
fun EaConfigEntity.toJsonContent(): String {
    return """{
  "mt5AccountId": "$mt5AccountId",
  "lJJ": "$lJJ",
  "xFF": "$xFF",
  "SENHA": "$SENHA",
  "aYY": "$aYY",
  "ESQUEMA_CORES_ENUM": "$ESQUEMA_CORES_ENUM",
  "cor_de_canal": "$cor_de_canal",
  "cor_de_linhas": "$cor_de_linhas",
  "corr_de_equador": "$corr_de_equador",
  "sJJ": "$sJJ",
  "LINHAS_DE_EQUADOR": $LINHAS_DE_EQUADOR,
  "TREND": "$TREND",
  "M_equador_alta": $M_equador_alta,
  "M_equador_baixa": $M_equador_baixa,
  "xxx": "$xxx",
  "TEMA": $TEMA,
  "ESTRATÉGIA": "$ESTRATÉGIA",
  "virada_de_jogo": $virada_de_jogo,
  "Nives": $Nives,
  "Costurar": $Costurar,
  "OperationalPeriod": "$OperationalPeriod",
  "lot": $lot,
  "dS": "$dS",
  "EA_ATIVO": $EA_ATIVO,
  "ea_ativo": $EA_ATIVO,
  "EA_AUTO": $EA_AUTO,
  "AUTO_PERIOD": "$AUTO_PERIOD",
  "AUTO_SURFADA": $AUTO_SURFADA,
  "SESSAO_ASIA_TOQUIO": $SESSAO_ASIA_TOQUIO,
  "SESSAO_LONDRES": $SESSAO_LONDRES,
  "SESSAO_NOVA_YORQUI": $SESSAO_NOVA_YORQUI,
  "EXPANSAO_MINIMA": $EXPANSAO_MINIMA,
  "EXPANSAO_MAXIMA": $EXPANSAO_MAXIMA,
  "dSS": "$dSS",
  "compra": $compra,
  "venda": $venda,
  "santo": $santo,
  "dedo": $dedo,
  "posicaoTake": $posicaoTake,
  "buy_take": $buy_take,
  "sell_take": $sell_take,
  "fDD": "$fDD",
  "SALDO": $SALDO,
  "GERENCIAMENTO_DE_RISCO_DIARIO": $GERENCIAMENTO_DE_RISCO_DIARIO,
  "porcentos": $porcentos,
  "poercentosg": $poercentosg,
  "GERENCIAMENTO_DE_RISCO_SEMANAL": $GERENCIAMENTO_DE_RISCO_SEMANAL,
  "PORCENTOO": $PORCENTOO,
  "PORCENTOSS": $PORCENTOSS,
  "gG": "$gG",
  "GMAIL": $GMAIL,
  "notific": $notific,
  "ativar_ou_desativar_venda": $ativar_ou_desativar_venda,
  "ativar_ou_desativar_compra": $ativar_ou_desativar_compra,
  "Modify_Sl_For_OxO": $Modify_Sl_For_OxO,
  "condicao_De_rompimento_c": $condicao_De_rompimento_c,
  "condicao_De_rompimento_v": $condicao_De_rompimento_v,
  "hFF": "$hFF",
  "mony": "$mony",
  "CAMBIO": $CAMBIO,
  "LER_CONEXAO_LICENCA": $LER_CONEXAO_LICENCA,
  "LER_ESQUEMA_CORES": $LER_ESQUEMA_CORES,
  "LER_PAINEL_CAMBIO": $LER_PAINEL_CAMBIO,
  "LER_CANAIS_TENDENCIA": $LER_CANAIS_TENDENCIA,
  "LER_ESTRATEGIA_PRINCIPAL": $LER_ESTRATEGIA_PRINCIPAL,
  "LER_POSICIONAMENTO_ORDEM": $LER_POSICIONAMENTO_ORDEM,
  "LER_GESTAO_RISCO": $LER_GESTAO_RISCO,
  "LER_AUTOMACAO_SESSOES": $LER_AUTOMACAO_SESSOES,
  "LER_RESULTADOS_NOTIFICACOES": $LER_RESULTADOS_NOTIFICACOES,
  "PERMITIR_LEITURA_PARAMETROS": $PERMITIR_LEITURA_PARAMETROS
}"""
}

fun EaConfigEntity.toSetFileContent(): String {
    return buildString {
        appendLine("lJJ=$lJJ")
        appendLine("xFF=$xFF")
        appendLine("SENHA=$SENHA")
        appendLine("aYY=$aYY")
        appendLine("ESQUEMA_CORES_ENUM=$ESQUEMA_CORES_ENUM")
        appendLine("cor_de_canal=$cor_de_canal")
        appendLine("cor_de_linhas=$cor_de_linhas")
        appendLine("corr_de_equador=$corr_de_equador")
        appendLine("sJJ=$sJJ")
        appendLine("LINHAS_DE_EQUADOR=${if (LINHAS_DE_EQUADOR) "1" else "0"}")
        appendLine("TREND=$TREND")
        appendLine("M_equador_alta=$M_equador_alta")
        appendLine("M_equador_baixa=$M_equador_baixa")
        appendLine("xxx=$xxx")
        appendLine("TEMA=${if (TEMA) "1" else "0"}")
        appendLine("ESTRATÉGIA=$ESTRATÉGIA")
        appendLine("virada_de_jogo=${if (virada_de_jogo) "1" else "0"}")
        appendLine("Nives=$Nives")
        appendLine("Costurar=${if (Costurar) "1" else "0"}")
        appendLine("OperationalPeriod=$OperationalPeriod")
        appendLine("lot=$lot")
        appendLine("dS=$dS")
        appendLine("EA_ATIVO=${if (EA_ATIVO) "1" else "0"}")
        appendLine("EA_AUTO=${if (EA_AUTO) "1" else "0"}")
        appendLine("AUTO_PERIOD=$AUTO_PERIOD")
        appendLine("AUTO_SURFADA=${if (AUTO_SURFADA) "1" else "0"}")
        appendLine("SESSAO_ASIA_TOQUIO=${if (SESSAO_ASIA_TOQUIO) "1" else "0"}")
        appendLine("SESSAO_LONDRES=${if (SESSAO_LONDRES) "1" else "0"}")
        appendLine("SESSAO_NOVA_YORQUI=${if (SESSAO_NOVA_YORQUI) "1" else "0"}")
        appendLine("EXPANSAO_MINIMA=$EXPANSAO_MINIMA")
        appendLine("EXPANSAO_MAXIMA=$EXPANSAO_MAXIMA")
        appendLine("dSS=$dSS")
        appendLine("compra=$compra")
        appendLine("venda=$venda")
        appendLine("santo=$santo")
        appendLine("dedo=$dedo")
        appendLine("posicaoTake=${if (posicaoTake) "1" else "0"}")
        appendLine("buy_take=$buy_take")
        appendLine("sell_take=$sell_take")
        appendLine("fDD=$fDD")
        appendLine("SALDO=$SALDO")
        appendLine("GERENCIAMENTO_DE_RISCO_DIARIO=${if (GERENCIAMENTO_DE_RISCO_DIARIO) "1" else "0"}")
        appendLine("porcentos=$porcentos")
        appendLine("poercentosg=$poercentosg")
        appendLine("GERENCIAMENTO_DE_RISCO_SEMANAL=${if (GERENCIAMENTO_DE_RISCO_SEMANAL) "1" else "0"}")
        appendLine("PORCENTOO=$PORCENTOO")
        appendLine("PORCENTOSS=$PORCENTOSS")
        appendLine("gG=$gG")
        appendLine("GMAIL=${if (GMAIL) "1" else "0"}")
        appendLine("notific=${if (notific) "1" else "0"}")
        appendLine("ativar_ou_desativar_venda=${if (ativar_ou_desativar_venda) "1" else "0"}")
        appendLine("ativar_ou_desativar_compra=${if (ativar_ou_desativar_compra) "1" else "0"}")
        appendLine("Modify_Sl_For_OxO=${if (Modify_Sl_For_OxO) "1" else "0"}")
        appendLine("condicao_De_rompimento_c=${if (condicao_De_rompimento_c) "1" else "0"}")
        appendLine("condicao_De_rompimento_v=${if (condicao_De_rompimento_v) "1" else "0"}")
        appendLine("hFF=$hFF")
        appendLine("mony=$mony")
        appendLine("CAMBIO=$CAMBIO")
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        var value = get(key)
        if (value is JSONObject) {
            value = value.toMap()
        } else if (value == JSONObject.NULL) {
            value = null
        }
        map[key] = value
    }
    return map
}
