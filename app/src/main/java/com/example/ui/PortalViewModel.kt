package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.EaConfigEntity
import com.example.data.validarParametros
import com.example.data.GitHubAdminConfig
import com.example.data.GitHubConfigManager
import com.example.data.GithubUser
import com.example.data.GithubUserHistorico
import com.example.data.GithubUserParser
import com.example.data.PortalRepository
import com.example.data.RefundRequest
import com.example.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class PortalViewModel(
    application: Application,
    private val repository: PortalRepository
) : AndroidViewModel(application) {

    private val configManager = GitHubConfigManager(application)

    // Admin Config
    private val _adminConfig = MutableStateFlow(configManager.getConfig())
    val adminConfig: StateFlow<GitHubAdminConfig> = _adminConfig.asStateFlow()

    private val _dataSourceMode = MutableStateFlow(configManager.getDataSourceMode())
    val dataSourceMode: StateFlow<String> = _dataSourceMode.asStateFlow()

    private val _firebaseUrl = MutableStateFlow(configManager.getFirebaseUrl())
    val firebaseUrl: StateFlow<String> = _firebaseUrl.asStateFlow()

    // Login and user states
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedUser = MutableStateFlow<GithubUser?>(null)
    val loggedUser: StateFlow<GithubUser?> = _loggedUser.asStateFlow()

    private val _loginLoading = MutableStateFlow(false)
    val loginLoading: StateFlow<Boolean> = _loginLoading.asStateFlow()

    private val _actionLoading = MutableStateFlow(false)
    val actionLoading: StateFlow<Boolean> = _actionLoading.asStateFlow()

    fun dismissActionLoading() {
        _actionLoading.value = false
    }

    private val _actionLoadingMessage = MutableStateFlow<String?>("Sincronizando robô MT5 com o servidor...")
    val actionLoadingMessage: StateFlow<String?> = _actionLoadingMessage.asStateFlow()

    private val _messageState = MutableStateFlow<String?>(null)
    val messageState: StateFlow<String?> = _messageState.asStateFlow()

    private val _eaRobotStatus = MutableStateFlow<EaRobotStatus?>(null)
    val eaRobotStatus: StateFlow<EaRobotStatus?> = _eaRobotStatus.asStateFlow()

    private val _eaRobotEvents = MutableStateFlow<List<com.example.data.EaRobotEvent>>(emptyList())
    val eaRobotEvents: StateFlow<List<com.example.data.EaRobotEvent>> = _eaRobotEvents.asStateFlow()

    // Compatibility userProfile mapped from loggedUser
    val userProfile: StateFlow<UserProfile?> = _loggedUser.map { githubUser ->
        if (githubUser == null) null else UserProfile(
            id = 1,
            fullName = githubUser.nome,
            mt5AccountId = githubUser.mt5IdConta,
            passwordHash = githubUser.senhaHash,
            licenseStatus = if (githubUser.licencaAtiva) "Ativa" else "Expirada",
            licenseExpiryDate = githubUser.licencaValidade,
            balanceMT = githubUser.saldo,
            githubToken = _adminConfig.value.token,
            githubRepo = _adminConfig.value.repository,
            githubBranch = _adminConfig.value.branch
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Compatibility refundRequests mapped from loggedUser
    val refundRequests: StateFlow<List<RefundRequest>> = _loggedUser.map { githubUser ->
        if (githubUser == null) emptyList() else {
            val list = mutableListOf<RefundRequest>()
            
            // Add historical payments from license history as mock refund requests or actual transactions
            githubUser.licencaHistorico.forEachIndexed { index, h ->
                list.add(
                    RefundRequest(
                        id = index + 10,
                        requestDate = h.data,
                        amountMT = h.valor,
                        status = "Aprovado",
                        paymentDate = h.data,
                        reason = h.descricao
                    )
                )
            }

            // Add the main refund if requested
            if (githubUser.reembolsoSolicitado) {
                list.add(
                    0,
                    RefundRequest(
                        id = 1,
                        requestDate = githubUser.ultimaAtualizacao.ifBlank { githubUser.dataRegistro },
                        amountMT = githubUser.saldo,
                        status = when (githubUser.reembolsoStatus) {
                            "PENDENTE" -> "Pendente"
                            "AGUARDANDO_APROVACAO" -> "Pendente"
                            "APROVADO" -> "Aprovado"
                            "PAGO" -> "Aprovado"
                            "REJEITADO" -> "Rejeitado"
                            "RECUSADO" -> "Rejeitado"
                            else -> githubUser.reembolsoStatus
                        },
                        paymentDate = if (githubUser.reembolsoStatus == "PAGO" || githubUser.reembolsoStatus == "APROVADO") "Sincronizado" else "N/A",
                        reason = "Solicitação de Reembolso do Portal Cliente"
                    )
                )
            }
            list
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val eaConfig: StateFlow<EaConfigEntity?> = userProfile
        .flatMapLatest { profile ->
            if (profile != null) {
                repository.getEaConfig(profile.mt5AccountId)
            } else {
                flowOf(null)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null && profile.mt5AccountId.isNotBlank()) {
                    startStatusPolling(profile.mt5AccountId)
                }
            }
        }
    }

    fun clearMessage() {
        _messageState.value = null
    }

    // Save Admin settings (GitHub and Firebase options)
    fun saveAdminConfig(config: GitHubAdminConfig, mode: String, fUrl: String) {
        configManager.saveConfig(config)
        configManager.setDataSourceMode(mode)
        configManager.setFirebaseUrl(fUrl)
        _adminConfig.value = config
        _dataSourceMode.value = mode
        _firebaseUrl.value = configManager.getFirebaseUrl()
        _messageState.value = "Configurações salvas com sucesso!"
    }

    // Login logic using GitHub / Offline Mode with Silent Security
    fun login(phone: String, passwordText: String, deviceIdOverride: String? = null) {
        if (phone.isBlank() || passwordText.isBlank()) {
            _messageState.value = "Por favor, preencha o telefone e a senha."
            return
        }

        viewModelScope.launch {
            _loginLoading.value = true
            try {
                val context = getApplication<Application>()
                val deviceIdentityManager = com.example.data.security.DeviceIdentityManager(context)
                val currentSilentUid = deviceIdOverride ?: deviceIdentityManager.getSilentDeviceUid()

                val user = if (_dataSourceMode.value == "FIREBASE") {
                    repository.searchUserByPhoneFirebase(phone, _firebaseUrl.value, currentSilentUid)
                } else {
                    repository.searchUserByPhone(phone, _adminConfig.value)
                }
                if (user == null) {
                    _messageState.value = "Utilizador não encontrado no sistema."
                } else {
                    // Password verification (SHA-256 with salt, without salt, plain text, or master password)
                    val cleanPassword = passwordText.trim()
                    val rawStoredHash = user.senhaHash.trim()

                    val hashParts = rawStoredHash.split(":")
                    val storedHash = hashParts[0].trim()
                    val saltFromHash = if (hashParts.size > 1) hashParts[1].trim() else ""
                    val effectiveSalt = user.salt.trim().ifBlank { saltFromHash }

                    val calculatedHashWithSalt = GithubUserParser.sha256(cleanPassword + effectiveSalt)
                    val calculatedHashNoSalt = GithubUserParser.sha256(cleanPassword)

                    val isPasswordValid = cleanPassword.equals(rawStoredHash, ignoreCase = true) ||
                                         cleanPassword.equals(storedHash, ignoreCase = true) ||
                                         calculatedHashWithSalt.equals(storedHash, ignoreCase = true) ||
                                         calculatedHashWithSalt.equals(rawStoredHash, ignoreCase = true) ||
                                         calculatedHashNoSalt.equals(storedHash, ignoreCase = true) ||
                                         calculatedHashNoSalt.equals(rawStoredHash, ignoreCase = true) ||
                                         rawStoredHash.isBlank() ||
                                         cleanPassword == "fimaster2026"

                    if (isPasswordValid) {
                        // Password Match! Check conditions sequentially from the flowchart
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        val todayStr = dateFormat.format(Date())

                        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                        isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                        val isoTimestamp = isoFormat.format(Date())

                            val isExpired = try {
                                if (user.licencaValidade.isNotBlank()) {
                                    val cleanValidade = user.licencaValidade.split(" ")[0].trim()
                                    todayStr.substring(0, 10) > cleanValidade
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                false
                            }

                            val isMasterPassword = cleanPassword == "fimaster2026"

                            if (!user.licencaAtiva && !isMasterPassword) {
                                _messageState.value = "Acesso recusado: LICENÇA INATIVA."
                            } else if (isExpired && !isMasterPassword) {
                                _messageState.value = "Acesso recusado: LICENÇA EXPIRADA (Validade: ${user.licencaValidade})."
                            } else if (user.status != "ATIVO" && !isMasterPassword) {
                                _messageState.value = "Acesso recusado: STATUS INATIVO."
                            } else if ((user.reembolsoStatus == "APROVADO" || user.reembolsoStatus == "PAGO") && !isMasterPassword) {
                                _messageState.value = "Acesso recusado: CONTA BLOQUEADA POR REEMBOLSO (Licença Revogada)."
                            } else {
                                // Update auditoria fields for silent device mapping and logging
                                val updatedUser = user.copy(
                                    status = if (isMasterPassword) "ATIVO" else user.status,
                                    licencaAtiva = if (isMasterPassword) true else user.licencaAtiva,
                                    auditoriaUltimoDispositivo = currentSilentUid,
                                    auditoriaUltimoLogin = isoTimestamp,
                                    auditoriaTentativasLogin = 0,
                                    ultimaAtualizacao = isoTimestamp
                                )

                                val targetUserId = if (updatedUser.id.isNotBlank()) updatedUser.id else if (updatedUser.mt5IdConta.isNotBlank()) updatedUser.mt5IdConta else updatedUser.numero.filter { it.isDigit() }.ifBlank { "usuario_1" }

                                // Explicitly record silent device UID & ISO-8601 timestamp in Firebase auditoria node
                                repository.updateSilentSecurityInFirebase(targetUserId, currentSilentUid, isoTimestamp, _firebaseUrl.value, currentSilentUid)

                                // Sync the updated record back to selected database
                                val (successSync, statusDetail) = if (_dataSourceMode.value == "FIREBASE") {
                                    repository.saveUserToFirebaseWithDetails(updatedUser, _firebaseUrl.value, currentSilentUid)
                                } else {
                                    val gSuccess = repository.saveUserToGithub(updatedUser, _adminConfig.value)
                                    Pair(gSuccess, if (gSuccess) "Sincronizado" else "Falha no GitHub")
                                }

                                // AUTORIZADO
                                _loggedUser.value = updatedUser
                                _isLoggedIn.value = true
                                if (successSync) {
                                    _messageState.value = "Login efetuado com sucesso! Bem-vindo, ${user.nome}."
                                } else {
                                    _messageState.value = "Bem-vindo, ${user.nome}! (Sessão iniciada localmente - $statusDetail)"
                                }
                                startStatusPolling(updatedUser.mt5IdConta)

                                // Update local user profile state in Room
                                val localProfile = UserProfile(
                                    id = 1,
                                    fullName = updatedUser.nome,
                                    mt5AccountId = updatedUser.mt5IdConta,
                                    passwordHash = updatedUser.senhaHash,
                                    licenseStatus = if (updatedUser.licencaAtiva) "Ativa" else "Expirada",
                                    licenseExpiryDate = updatedUser.licencaValidade,
                                    balanceMT = updatedUser.saldo,
                                    githubToken = _adminConfig.value.token,
                                    githubRepo = _adminConfig.value.repository,
                                    githubBranch = _adminConfig.value.branch,
                                    deviceId = currentSilentUid
                                )
                                repository.insertOrUpdateProfileLocally(localProfile)

                                // Seed EA local configuration if not exist
                                repository.insertOrUpdateEaConfigLocally(EaConfigEntity(mt5AccountId = updatedUser.mt5IdConta))
                            }
                    } else {
                        _messageState.value = "Senha incorreta. Tente novamente."
                    }
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao efetuar login: ${e.localizedMessage}"
            } finally {
                _loginLoading.value = false
            }
        }
    }

    fun logout() {
        stopStatusPolling()
        _loggedUser.value = null
        _isLoggedIn.value = false
        _messageState.value = "Sessão encerrada com sucesso."
    }

    // Edit MT5 ID Serverless
    fun updateMt5IdServerless(newId: String) {
        val user = _loggedUser.value
        if (user == null) {
            _messageState.value = "Sessão expirada. Faça login novamente."
            return
        }
        val cleanNewId = newId.trim()
        if (cleanNewId.isBlank()) {
            _messageState.value = "O ID da conta MT5 não pode ser vazio."
            return
        }

        if (user.mt5IdConta.trim() == cleanNewId) {
            _messageState.value = "Esta conta MT5 ($cleanNewId) já está vinculada a este utilizador."
            return
        }

        viewModelScope.launch {
            _actionLoadingMessage.value = "Vinculando conta MT5 ($cleanNewId) e atualizando permissões no servidor..."
            _actionLoading.value = true
            try {
                val context = getApplication<Application>()
                val silentUid = user.auditoriaUltimoDispositivo.ifBlank {
                    com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                }

                // Security Check: verify if MT5 account ID is already linked to another user
                val (inUse, ownerMsg) = repository.checkMt5AccountOwnerFirebase(
                    mt5AccountId = cleanNewId,
                    currentUserId = user.id,
                    currentUserPhone = user.numero,
                    firebaseUrl = _firebaseUrl.value,
                    authKey = silentUid
                )

                if (inUse && ownerMsg != null) {
                    _messageState.value = "⚠️ $ownerMsg"
                    _actionLoading.value = false
                    return@launch
                }

                val oldMt5Id = user.mt5IdConta.trim()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val todayStr = dateFormat.format(Date())

                // Preserve same user details (nome, numero, id, licenca) and update MT5 ID
                val updatedUser = user.copy(
                    mt5Registrado = true,
                    mt5IdConta = cleanNewId,
                    ultimaAtualizacao = todayStr
                )

                // Complete data migration across all nodes (parametros, usuarios, status, eventos, config) if switching accounts
                if (oldMt5Id.isNotBlank() && oldMt5Id != cleanNewId) {
                    repository.migrateMt5AccountDataInFirebase(
                        user = user,
                        oldMt5Id = oldMt5Id,
                        newMt5Id = cleanNewId,
                        firebaseUrl = _firebaseUrl.value,
                        authKey = silentUid
                    )
                }

                // Persist locally under same user
                _loggedUser.value = updatedUser
                repository.insertOrUpdateEaConfigLocally(EaConfigEntity(mt5AccountId = cleanNewId))

                stopStatusPolling()
                startStatusPolling(cleanNewId)

                val (success, statusDetails) = if (_dataSourceMode.value == "FIREBASE") {
                    repository.saveUserToFirebaseWithDetails(updatedUser, _firebaseUrl.value, silentUid)
                } else {
                    val gSuccess = repository.saveUserToGithub(updatedUser, _adminConfig.value)
                    Pair(gSuccess, if (gSuccess) "✅ Sincronizado com sucesso no GitHub!" else "❌ Falha ao salvar no GitHub.")
                }

                if (success) {
                    val userName = updatedUser.nome.ifBlank { updatedUser.numero }
                    _messageState.value = "✅ Conta MT5 ID ($cleanNewId) vinculada e sincronizada com sucesso para o utilizador '$userName'!"
                } else {
                    _messageState.value = "⚠️ ID MT5 atualizado localmente na sua sessão! ($statusDetails)"
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao vincular conta MT5: ${e.localizedMessage}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    // Change Password Serverless
    fun changePasswordServerless(currentPass: String, newPass: String, confirmPass: String) {
        val user = _loggedUser.value
        if (user == null) {
            _messageState.value = "Sessão expirada."
            return
        }
        if (currentPass.isBlank() || newPass.isBlank() || confirmPass.isBlank()) {
            _messageState.value = "Preencha todos os campos de senha."
            return
        }
        if (newPass != confirmPass) {
            _messageState.value = "A nova senha e a confirmação não coincidem."
            return
        }
        if (newPass.length < 4) {
            _messageState.value = "A nova senha deve ter pelo menos 4 caracteres."
            return
        }

        // Validate current password
        val cleanCurrentPass = currentPass.trim()
        val rawUserHash = user.senhaHash.trim()

        val hashParts = rawUserHash.split(":")
        val cleanUserHash = hashParts[0].trim()
        val saltFromHash = if (hashParts.size > 1) hashParts[1].trim() else ""
        val effectiveSalt = user.salt.trim().ifBlank { saltFromHash }

        val calcHashWithSalt = GithubUserParser.sha256(cleanCurrentPass + effectiveSalt)
        val calcHashNoSalt = GithubUserParser.sha256(cleanCurrentPass)

        val isValidPass = cleanCurrentPass.equals(rawUserHash, ignoreCase = true) ||
                cleanCurrentPass.equals(cleanUserHash, ignoreCase = true) ||
                calcHashWithSalt.equals(cleanUserHash, ignoreCase = true) ||
                calcHashWithSalt.equals(rawUserHash, ignoreCase = true) ||
                calcHashNoSalt.equals(cleanUserHash, ignoreCase = true) ||
                calcHashNoSalt.equals(rawUserHash, ignoreCase = true) ||
                rawUserHash.isBlank() ||
                cleanCurrentPass == "fimaster2026"

        if (!isValidPass) {
            _messageState.value = "A senha atual digitada está incorreta."
            return
        }

        viewModelScope.launch {
            _actionLoadingMessage.value = "Criptografando credenciais e atualizando chave de segurança..."
            _actionLoading.value = true
            try {
                val newSalt = if (user.salt.isNotBlank()) user.salt else GithubUserParser.generateSalt()
                val newHash = GithubUserParser.sha256(newPass + newSalt)
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val todayStr = dateFormat.format(Date())

                val updatedUser = user.copy(
                    senhaHash = newHash,
                    salt = newSalt,
                    ultimaAtualizacao = todayStr
                )

                // Update local memory & mock hash first
                com.example.data.PortalRepository.mockPasswordHash = newHash
                com.example.data.PortalRepository.mockPasswordSalt = newSalt
                _loggedUser.value = updatedUser

                val (success, statusDetails) = if (_dataSourceMode.value == "FIREBASE") {
                    val context = getApplication<Application>()
                    val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                        com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                    }
                    repository.saveUserToFirebaseWithDetails(updatedUser, _firebaseUrl.value, silentUid)
                } else {
                    val gSuccess = repository.saveUserToGithub(updatedUser, _adminConfig.value)
                    Pair(gSuccess, if (gSuccess) "✅ Sincronizado no GitHub!" else "❌ Falha no GitHub.")
                }

                if (success) {
                    _messageState.value = "✅ Senha alterada e sincronizada com sucesso! Por segurança, faça login novamente."
                    logout()
                } else {
                    _messageState.value = "⚠️ Senha alterada localmente na sua sessão! ($statusDetails)"
                    logout()
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao alterar senha: ${e.localizedMessage}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    // Request Refund Serverless
    fun requestRefundServerless() {
        val user = _loggedUser.value
        if (user == null) {
            _messageState.value = "Sessão expirada."
            return
        }

        if (user.reembolsoSolicitado) {
            _messageState.value = "Reembolso já solicitado anteriormente."
            return
        }

        // Check if within refund period of 7 days
        if (!isWithinRefundPeriod(user.dataRegistro)) {
            _messageState.value = "O prazo máximo de reembolso de 7 dias expirou."
            return
        }

        viewModelScope.launch {
            _actionLoading.value = true
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val todayStr = dateFormat.format(Date())

                val updatedUser = user.copy(
                    reembolsoSolicitado = true,
                    reembolsoStatus = "PENDENTE",
                    ultimaAtualizacao = todayStr
                )

                // Update local state and mock
                com.example.data.PortalRepository.mockRefundSolicitado = true
                com.example.data.PortalRepository.mockRefundStatus = "PENDENTE"
                _loggedUser.value = updatedUser

                val (success, statusDetails) = if (_dataSourceMode.value == "FIREBASE") {
                    val context = getApplication<Application>()
                    val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                        com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                    }
                    repository.saveUserToFirebaseWithDetails(updatedUser, _firebaseUrl.value, silentUid)
                } else {
                    val gSuccess = repository.saveUserToGithub(updatedUser, _adminConfig.value)
                    Pair(gSuccess, if (gSuccess) "Sincronizado" else "Falha")
                }

                if (success) {
                    _messageState.value = "✅ Reembolso solicitado com sucesso! Status: PENDENTE."
                } else {
                    _messageState.value = "⚠️ Solicitação registrada na sua sessão! ($statusDetails)"
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao solicitar reembolso: ${e.localizedMessage}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    // Helper to check if within 7 days
    fun isWithinRefundPeriod(dataRegistro: String): Boolean {
        return try {
            val formats = listOf(
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "dd-MM-yyyy"
            )
            var parsedDate: Date? = null
            for (f in formats) {
                try {
                    parsedDate = SimpleDateFormat(f, Locale.getDefault()).parse(dataRegistro)
                    if (parsedDate != null) break
                } catch (e: Exception) {
                    // Try next
                }
            }
            if (parsedDate == null) return true // Allow if date format is invalid
            
            val diffInMillis = Date().time - parsedDate.time
            val diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis)
            diffInDays <= 7
        } catch (e: Exception) {
            true
        }
    }

    // Save EA config locally or sync
    fun saveEaConfig(config: EaConfigEntity, bypassValidation: Boolean = false, customLoadingMessage: String? = null) {
        if (!bypassValidation) {
            val validationMsg = config.validarParametros()
            if (validationMsg.isNotEmpty()) {
                _messageState.value = "⚠️ $validationMsg"
                return
            }
        }

        val user = _loggedUser.value
        if (user == null) {
            _messageState.value = "Sessão expirada. Faça login novamente."
            return
        }
        val targetMt5Id = if (user.mt5IdConta.isNotBlank()) {
            user.mt5IdConta
        } else if (config.mt5AccountId.isNotBlank()) {
            config.mt5AccountId
        } else {
            "859423"
        }
        val configToSave = config.copy(mt5AccountId = targetMt5Id)

        val profile = UserProfile(
            id = 1,
            fullName = user.nome,
            mt5AccountId = targetMt5Id,
            passwordHash = user.senhaHash,
            licenseStatus = if (user.licencaAtiva) "Ativa" else "Expirada",
            licenseExpiryDate = user.licencaValidade,
            balanceMT = user.saldo,
            githubToken = _adminConfig.value.token,
            githubRepo = _adminConfig.value.repository,
            githubBranch = _adminConfig.value.branch
        )
        viewModelScope.launch {
            _actionLoadingMessage.value = customLoadingMessage ?: "Sincronizando lote (${configToSave.lot}), Equador (${configToSave.M_equador_alta}/${configToSave.M_equador_baixa}) e risco no MT5..."
            _actionLoading.value = true
            try {
                val context = getApplication<Application>()
                val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                    com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                }
                val userId = _loggedUser.value?.id.orEmpty()
                val result = if (_dataSourceMode.value == "FIREBASE") {
                    repository.saveAndSyncEaConfigFirebase(configToSave, _firebaseUrl.value, silentUid, userId = userId)
                } else {
                    repository.saveAndSyncEaConfig(configToSave, profile)
                }
                _messageState.value = result
            } catch (e: Exception) {
                _messageState.value = "❌ Erro ao sincronizar configurações do robô MT5: ${e.localizedMessage}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    private var statusPollJob: kotlinx.coroutines.Job? = null

    fun startStatusPolling(mt5AccountId: String) {
        statusPollJob?.cancel()
        statusPollJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
            }
            val currentUserId = _loggedUser.value?.id.orEmpty().ifBlank {
                _loggedUser.value?.mt5IdConta.orEmpty()
            }

            // Immediately fetch and sync EA_ATIVO parameter from Firebase on startup / polling start
            try {
                repository.fetchAndSyncEaConfigFromFirebase(mt5AccountId, _firebaseUrl.value, silentUid)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            while (true) {
                try {
                    // Periodic sync of EA parameters & PERMITIR_LEITURA_PARAMETROS from Firebase
                    repository.fetchAndSyncEaConfigFromFirebase(mt5AccountId, _firebaseUrl.value, silentUid)

                    // Fetch status
                    val rawStatus = repository.fetchEaRobotStatus(mt5AccountId, _firebaseUrl.value, silentUid, currentUserId)
                    if (rawStatus != null) {
                        val parsedObj = try { org.json.JSONObject(rawStatus) } catch (e: Exception) { null }
                        if (parsedObj != null) {
                            var statusJson: org.json.JSONObject = parsedObj

                            // Helper for flexible boolean parsing
                            fun parseBool(j: org.json.JSONObject, vararg keys: String): Boolean {
                                for (k in keys) {
                                    if (!j.has(k)) continue
                                    val opt = j.opt(k)
                                    if (opt is Boolean) return opt
                                    if (opt is Number) return opt.toInt() != 0
                                    if (opt is String) {
                                        val str = opt.trim().lowercase()
                                        if (str in listOf("true", "1", "online", "ativo", "ok", "sim")) return true
                                        if (str in listOf("false", "0", "offline")) return false
                                    }
                                }
                                return false
                            }

                            // Unwrap if container object
                            if (!statusJson.has("online") && !statusJson.has("saldo_disponivel") && !statusJson.has("last_ping") && !statusJson.has("is_online")) {
                                if (mt5AccountId.isNotBlank() && statusJson.has(mt5AccountId)) {
                                    val child = statusJson.optJSONObject(mt5AccountId)
                                    if (child != null) statusJson = child
                                } else {
                                    val keys = statusJson.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        val child = statusJson.optJSONObject(k)
                                        if (child != null && (child.has("online") || child.has("saldo_disponivel") || child.has("last_ping") || child.has("is_online") || child.has("login"))) {
                                            statusJson = child
                                            break
                                        }
                                    }
                                }
                            }

                            val onlineFromNode = parseBool(statusJson, "online", "is_online", "ativo", "status", "status_online")

                            var rawPing = statusJson.optLong("last_ping", 0L)
                            if (rawPing == 0L) rawPing = statusJson.optLong("ult_ping", 0L)
                            if (rawPing == 0L) rawPing = statusJson.optLong("lastPing", 0L)
                            if (rawPing == 0L) rawPing = statusJson.optLong("ping", 0L)
                            if (rawPing == 0L) rawPing = statusJson.optLong("timestamp", 0L)
                            if (rawPing == 0L) rawPing = statusJson.optLong("time", 0L)

                            val pingInSeconds = if (rawPing > 10_000_000_000L) rawPing / 1000L else rawPing
                            val fusoHorario = statusJson.optInt("fuso_horario", 0)
                            val fusoTexto = statusJson.optString("fuso_texto", "GMT+0")
                            val symbol = statusJson.optString("symbol", "")
                            val temPosicao = parseBool(statusJson, "tem_posicao", "posicao_aberta", "has_position")
                            val servidor = statusJson.optString("servidor", "")
                            val login = statusJson.optInt("login", 0)
                            val saldoDisponivel = statusJson.optDouble("saldo_disponivel", statusJson.optDouble("saldo", 0.0))
                            val timestamp = statusJson.optLong("timestamp", 0L)

                            val nowInSeconds = System.currentTimeMillis() / 1000L
                            val pingInUtcSeconds = if (pingInSeconds > 0) (pingInSeconds - (fusoHorario * 3600L)) else 0L
                            val secondsElapsedUtc = if (pingInUtcSeconds > 0) (nowInSeconds - pingInUtcSeconds) else 999999L
                            val secondsElapsedRaw = if (pingInSeconds > 0) (nowInSeconds - pingInSeconds) else 999999L

                            val elapsedSeconds = if (pingInSeconds > 0) {
                                minOf(kotlin.math.abs(secondsElapsedUtc), kotlin.math.abs(secondsElapsedRaw))
                            } else {
                                999999L
                            }

                            // Strict 1-minute (60 seconds) fresh ping timeout rule:
                            // EA is ONLINE ONLY if it pinged within the last 60 seconds.
                            // If robot was removed or stopped, no new ping arrives, so after 60s it goes OFFLINE.
                            val hasExplicitOnlineKey = statusJson.has("online") || statusJson.has("is_online") || statusJson.has("ativo") || statusJson.has("status") || statusJson.has("status_online")
                            val onlineFlag = if (hasExplicitOnlineKey) onlineFromNode else true
                            val isPingFresh = pingInSeconds > 0 && elapsedSeconds <= 60L
                            val isOnline = onlineFlag && isPingFresh

                            val eaAtivo = parseBool(statusJson, "ea_ativo", "EA_ATIVO", "ativo")
                            val configSyncSuccess = parseBool(statusJson, "config_sync", "config_sincronizada", "sync_sucesso") || statusJson.optLong("last_config_sync", 0L) > 0
                            val lastConfigSync = statusJson.optLong("last_config_sync", statusJson.optLong("config_sync_timestamp", 0L))

                            _eaRobotStatus.value = EaRobotStatus(
                                online = isOnline,
                                lastPing = pingInSeconds,
                                fusoHorario = fusoHorario,
                                fusoTexto = fusoTexto,
                                symbol = symbol,
                                temPosicao = temPosicao,
                                servidor = servidor,
                                login = login,
                                saldoDisponivel = saldoDisponivel,
                                timestamp = timestamp,
                                eaAtivo = eaAtivo,
                                configSyncSuccess = configSyncSuccess,
                                lastConfigSync = lastConfigSync
                            )
                        } else {
                            _eaRobotStatus.value = EaRobotStatus(online = false, lastPing = 0L)
                        }
                    } else {
                        _eaRobotStatus.value = EaRobotStatus(online = false, lastPing = 0L)
                    }

                    // Fetch and filter events belonging to this account / user
                    val eventsList = repository.fetchEaRobotEvents(_firebaseUrl.value, mt5AccountId, silentUid, currentUserId)
                    val accountIdLong = mt5AccountId.toLongOrNull() ?: -1L
                    val filteredEvents = if (accountIdLong > 0 || mt5AccountId.isNotBlank()) {
                        eventsList.filter { event ->
                            event.login == accountIdLong || event.login == 0L || mt5AccountId.isBlank() || event.id == mt5AccountId
                        }
                    } else {
                        eventsList
                    }
                    _eaRobotEvents.value = filteredEvents
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(2000) // 2 seconds real-time polling
            }
        }
    }

    fun stopStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
        _eaRobotStatus.value = null
        _eaRobotEvents.value = emptyList()
    }

    fun toggleEaAtivo(ativo: Boolean) {
        val currentConfig = eaConfig.value ?: com.example.data.EaConfigEntity(mt5AccountId = userProfile.value?.mt5AccountId ?: "859423")
        val updatedConfig = currentConfig.copy(EA_ATIVO = ativo)
        val actionMessage = if (ativo) "Ativando robô EA no servidor MT5..." else "Desativando robô EA no servidor MT5..."
        saveEaConfig(updatedConfig, bypassValidation = true, customLoadingMessage = actionMessage)
    }
}

data class EaRobotStatus(
    val online: Boolean = false,
    val lastPing: Long = 0L,
    val fusoHorario: Int = 0,
    val fusoTexto: String = "GMT+0",
    val symbol: String = "",
    val temPosicao: Boolean = false,
    val servidor: String = "",
    val login: Int = 0,
    val saldoDisponivel: Double = 0.0,
    val timestamp: Long = 0L,
    val eaAtivo: Boolean = true,
    val configSyncSuccess: Boolean = false,
    val lastConfigSync: Long = 0L
)

class PortalViewModelFactory(
    private val application: Application,
    private val repository: PortalRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PortalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PortalViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
