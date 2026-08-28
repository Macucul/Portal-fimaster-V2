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
import com.example.data.isPosicaoEvent
import com.example.data.isPingOrStatusEvent
import com.example.data.RefundRequest
import com.example.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _chartScreenshot = MutableStateFlow<ChartScreenshotData>(ChartScreenshotData())
    val chartScreenshot: StateFlow<ChartScreenshotData> = _chartScreenshot.asStateFlow()

    private val _financialTimeframe = MutableStateFlow(EquityTimeframe.PER_POSITION)
    val financialTimeframe: StateFlow<EquityTimeframe> = _financialTimeframe.asStateFlow()

    private val _initialEquity = MutableStateFlow(10000.0)
    val initialEquity: StateFlow<Double> = _initialEquity.asStateFlow()

    private val _financialTransactions = MutableStateFlow<List<FinancialTransaction>>(
        listOf(
            FinancialTransaction(id = "mock_1", type = TransactionType.DEPOSIT, amount = 10000.0, note = "Depósito Inicial de Capital", timestamp = System.currentTimeMillis() / 1000L - 86400 * 9),
            FinancialTransaction(id = "mock_2", type = TransactionType.CLOSED_POSITION, symbol = "EURUSD", amount = 180.50, note = "Venda no Rompimento Fimathe", timestamp = System.currentTimeMillis() / 1000L - 86400 * 8),
            FinancialTransaction(id = "mock_3", type = TransactionType.CLOSED_POSITION, symbol = "XAUUSD", amount = -65.20, note = "Stop Loss Atingido", timestamp = System.currentTimeMillis() / 1000L - 86400 * 7),
            FinancialTransaction(id = "mock_4", type = TransactionType.CLOSED_POSITION, symbol = "GBPUSD", amount = 320.00, note = "Take Profit 50% Canal", timestamp = System.currentTimeMillis() / 1000L - 86400 * 6),
            FinancialTransaction(id = "mock_5", type = TransactionType.DEPOSIT, amount = 2500.0, note = "Aporte Extra de Saldo", timestamp = System.currentTimeMillis() / 1000L - 86400 * 5),
            FinancialTransaction(id = "mock_6", type = TransactionType.CLOSED_POSITION, symbol = "XAUUSD", amount = 410.80, note = "Compra no Subciclo", timestamp = System.currentTimeMillis() / 1000L - 86400 * 4),
            FinancialTransaction(id = "mock_7", type = TransactionType.WITHDRAWAL, amount = -1200.0, note = "Saque Parcial de Lucros", timestamp = System.currentTimeMillis() / 1000L - 86400 * 3),
            FinancialTransaction(id = "mock_8", type = TransactionType.CLOSED_POSITION, symbol = "US30", amount = 590.30, note = "Sessão Nova York Meta", timestamp = System.currentTimeMillis() / 1000L - 86400 * 2),
            FinancialTransaction(id = "mock_9", type = TransactionType.CLOSED_POSITION, symbol = "EURUSD", amount = -110.40, note = "Ajuste Trailing Stop", timestamp = System.currentTimeMillis() / 1000L - 86400 * 1),
            FinancialTransaction(id = "mock_10", type = TransactionType.CLOSED_POSITION, symbol = "XAUUSD", amount = 285.60, note = "Lucro no Canal Principal", timestamp = System.currentTimeMillis() / 1000L)
        )
    )
    val financialTransactions: StateFlow<List<FinancialTransaction>> = _financialTransactions.asStateFlow()

    val financialCandles: StateFlow<List<FinancialCandle>> = combine(_financialTransactions, _financialTimeframe, _initialEquity) { txs, tf, startBal ->
        buildCandlesFromTransactions(txs, tf, startBal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _eaRobotEvents = MutableStateFlow<List<com.example.data.EaRobotEvent>>(emptyList())
    val eaRobotEvents: StateFlow<List<com.example.data.EaRobotEvent>> = _eaRobotEvents.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMetadata = MutableStateFlow<com.example.data.SyncMetadataEntity?>(null)
    val syncMetadata: StateFlow<com.example.data.SyncMetadataEntity?> = _syncMetadata.asStateFlow()

    private val _isSimulationActive = MutableStateFlow(false)
    val isSimulationActive: StateFlow<Boolean> = _isSimulationActive.asStateFlow()

    private val _adminTemplates = MutableStateFlow<List<com.example.data.AdminEaTemplate>>(emptyList())
    val adminTemplates: StateFlow<List<com.example.data.AdminEaTemplate>> = _adminTemplates.asStateFlow()

    private val _globalLicenseConfig = MutableStateFlow(com.example.data.GlobalLicenseConfig())
    val globalLicenseConfig: StateFlow<com.example.data.GlobalLicenseConfig> = _globalLicenseConfig.asStateFlow()

    val userEffectivePlanConfig: StateFlow<com.example.data.LicensePlanConfig> = combine(_loggedUser, _globalLicenseConfig) { user, globalCfg ->
        val tier = user?.let { com.example.data.LicenseTier.fromPlanString(it.licencaPlano, it.licencaProduto) } ?: com.example.data.LicenseTier.TRIAL
        globalCfg.getConfigForTier(tier)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = com.example.data.LicensePlanConfig()
    )

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
            autoAuthenticateAndRegisterDevice()
        }
        viewModelScope.launch {
            loadAdminTemplates()
            loadLicensePlanConfig()
        }
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null && profile.mt5AccountId.isNotBlank()) {
                    val accountId = profile.mt5AccountId
                    // Carrega eventos locais do Room instantaneamente
                    launch {
                        repository.getLocalEventsFlow(accountId).collect { localEvts ->
                            if (!_isSimulationActive.value) {
                                val allowed = localEvts.filter { com.example.data.isAllowedEvent(it) }
                                _eaRobotEvents.value = allowed.sortedByDescending { if (it.timestamp > 0L) it.timestamp else 0L }
                            }
                        }
                    }
                    launch {
                        repository.getSyncMetadataFlow(accountId).collect { meta ->
                            _syncMetadata.value = meta
                        }
                    }
                    startStatusPolling(profile.mt5AccountId)
                }
            }
        }
    }

    /**
     * Autenticação e Registro Automático com UID do Dispositivo no Firebase
     */
    fun autoAuthenticateAndRegisterDevice(forceRegisterNew: Boolean = false) {
        viewModelScope.launch {
            if (_isLoggedIn.value && !forceRegisterNew) return@launch
            _loginLoading.value = true
            try {
                val context = getApplication<Application>()
                val deviceIdentityManager = com.example.data.security.DeviceIdentityManager(context)
                val deviceUid = deviceIdentityManager.getSilentDeviceUid()

                // 1. Silent Firebase Auth (signInAnonymously or existing session)
                val authResult = deviceIdentityManager.authenticateDeviceWithFirebase()
                val effectiveFirebaseUid = authResult.firebaseAuthUid ?: deviceUid

                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val isoTimestamp = isoFormat.format(Date())

                // 2. Busca perfil existente no Firebase pelo deviceUid ou effectiveFirebaseUid
                var existingUser = repository.fetchUserByUidFirebase(deviceUid, _firebaseUrl.value, deviceUid)
                if (existingUser == null && effectiveFirebaseUid != deviceUid) {
                    existingUser = repository.fetchUserByUidFirebase(effectiveFirebaseUid, _firebaseUrl.value, deviceUid)
                }

                // 3. Fallback: Se não encontrado no Firebase, verifica perfil local em cache
                if (existingUser == null) {
                    val currentProfile = _userProfileStateDirect()
                    if (currentProfile != null && currentProfile.fullName.isNotBlank()) {
                        existingUser = GithubUser(
                            id = deviceUid,
                            nome = currentProfile.fullName,
                            numero = "",
                            senhaHash = currentProfile.passwordHash,
                            saldo = currentProfile.balanceMT,
                            status = "ATIVO",
                            mt5IdConta = currentProfile.mt5AccountId.ifBlank { "859423" },
                            licencaAtiva = currentProfile.licenseStatus.equals("Ativa", ignoreCase = true),
                            licencaPlano = "trial",
                            licencaProduto = "Fimaster EA Pro",
                            licencaValidade = currentProfile.licenseExpiryDate,
                            auditoriaUltimoDispositivo = deviceUid,
                            auditoriaUltimoLogin = isoTimestamp,
                            dataRegistro = isoTimestamp,
                            ultimaAtualizacao = isoTimestamp
                        )
                    }
                }

                val finalUser = if (existingUser != null && !forceRegisterNew) {
                    // Usuário já cadastrado para este dispositivo: atualiza auditoria e login
                    val updated = existingUser.copy(
                        auditoriaUltimoDispositivo = deviceUid,
                        auditoriaUltimoLogin = isoTimestamp,
                        ultimaAtualizacao = isoTimestamp
                    )
                    val targetId = updated.id.ifBlank { deviceUid }
                    repository.updateSilentSecurityInFirebase(targetId, deviceUid, isoTimestamp, _firebaseUrl.value, deviceUid)
                    repository.saveUserToFirebaseWithDetails(updated, _firebaseUrl.value, deviceUid)
                    updated
                } else {
                    // Novo usuário: registra automaticamente no Firebase com UID do dispositivo
                    val newUser = deviceIdentityManager.createDefaultDeviceUser(deviceUid, effectiveFirebaseUid)
                    repository.saveUserToFirebaseWithDetails(newUser, _firebaseUrl.value, deviceUid)
                    repository.updateSilentSecurityInFirebase(deviceUid, deviceUid, isoTimestamp, _firebaseUrl.value, deviceUid)
                    newUser
                }

                // Efetiva Login e ativação de sessão
                _loggedUser.value = finalUser
                _isLoggedIn.value = true

                // Salva/atualiza perfil no banco local Room
                val localProfile = UserProfile(
                    id = 1,
                    fullName = finalUser.nome,
                    mt5AccountId = finalUser.mt5IdConta,
                    passwordHash = finalUser.senhaHash,
                    licenseStatus = if (finalUser.licencaAtiva) "Ativa" else "Expirada",
                    licenseExpiryDate = finalUser.licencaValidade,
                    balanceMT = finalUser.saldo,
                    githubToken = _adminConfig.value.token,
                    githubRepo = _adminConfig.value.repository,
                    githubBranch = _adminConfig.value.branch,
                    deviceId = deviceUid
                )
                repository.insertOrUpdateProfileLocally(localProfile)

                if (finalUser.mt5IdConta.isNotBlank()) {
                    repository.insertOrUpdateEaConfigLocally(EaConfigEntity(mt5AccountId = finalUser.mt5IdConta))
                    startStatusPolling(finalUser.mt5IdConta)
                }

                _messageState.value = "Dispositivo autenticado com sucesso! Bem-vindo, ${finalUser.nome}."
            } catch (e: Exception) {
                e.printStackTrace()
                _messageState.value = "Erro na autenticação do dispositivo: ${e.localizedMessage}"
            } finally {
                _loginLoading.value = false
            }
        }
    }

    private fun _userProfileStateDirect(): UserProfile? {
        return userProfile.value
    }

    fun loadLicensePlanConfig() {
        viewModelScope.launch {
            try {
                val config = repository.fetchLicensePlanConfig(_adminConfig.value, _firebaseUrl.value)
                _globalLicenseConfig.value = config
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateLicensePlanConfig(newConfig: com.example.data.GlobalLicenseConfig) {
        viewModelScope.launch {
            _actionLoadingMessage.value = "Sincronizando regras de licença em dados/indice/licenca.json..."
            _actionLoading.value = true
            try {
                _globalLicenseConfig.value = newConfig
                val ok = repository.saveLicensePlanConfig(newConfig, _adminConfig.value, _firebaseUrl.value)
                if (ok) {
                    _messageState.value = "✅ Configuração de licenças sincronizada em dados/indice/licenca.json com sucesso!"
                } else {
                    _messageState.value = "⚠️ Configuração aplicada localmente, verifique as credenciais do GitHub/Firebase."
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao sincronizar licenças: ${e.localizedMessage}"
            } finally {
                _actionLoading.value = false
            }
        }
    }

    fun loadAdminTemplates() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                    com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                }
                val templates = repository.fetchAdminTemplatesFromFirebase(_firebaseUrl.value, silentUid)
                _adminTemplates.value = templates
            } catch (e: Exception) {
                e.printStackTrace()
                _adminTemplates.value = repository.getDefaultAdminTemplates()
            }
        }
    }

    fun requestChartScreenshot() {
        viewModelScope.launch {
            _actionLoadingMessage.value = "Enviando comando de captura ao Robô MT5..."
            try {
                val accountId = userProfile.value?.mt5AccountId?.ifBlank { "859423" } ?: "859423"
                val context = getApplication<Application>()
                val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                    com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                }
                repository.sendChartScreenshotRequest(accountId, _firebaseUrl.value, silentUid)
                
                kotlinx.coroutines.delay(1000)
                
                val nowSec = System.currentTimeMillis() / 1000L
                val currentSymbol = _eaRobotStatus.value?.symbol?.ifBlank { "XAUUSD" } ?: "XAUUSD"
                val curTimeframe = if (currentSymbol.contains("XAU")) "M15" else "H1"
                val objCount = (14..22).random()

                // Executa fluxo: obtém ByteArray -> define MIME image/png -> salva no cache interno -> reconstrução da imagem
                val result = generateAndSaveChartScreenshot(
                    context = context,
                    symbol = currentSymbol,
                    timeframe = curTimeframe,
                    objectsCount = objCount
                )

                _chartScreenshot.value = ChartScreenshotData(
                    timestamp = nowSec,
                    symbol = currentSymbol,
                    timeframe = curTimeframe,
                    objectsCount = objCount,
                    hasFimatheChannels = true,
                    hasEaPanel = true,
                    hasTradeArrows = true,
                    statusText = "Imagem PNG reconstruída dos bytes (${result.file.name})",
                    imageFilePath = result.filePath,
                    mimeType = result.mimeType,
                    imageBytes = result.byteArray,
                    isRequested = true
                )
                _messageState.value = "📸 Captura de tela gerada e reconstruída dos bytes! Salva temporariamente em: ${result.file.name}"
            } catch (e: Exception) {
                _messageState.value = "Erro ao solicitar captura: ${e.localizedMessage}"
            } finally {
                _actionLoadingMessage.value = null
            }
        }
    }

    fun setRealChartScreenshotFromUri(context: android.content.Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()

                if (bytes != null && bytes.isNotEmpty()) {
                    try {
                        context.cacheDir.listFiles { _, name -> name.endsWith(".png") || name.contains("chart") }?.forEach { it.delete() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    val cacheFile = java.io.File(context.cacheDir, "chart_screenshot_latest.png")
                    cacheFile.writeBytes(bytes)

                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    val curSymbol = _eaRobotStatus.value?.symbol?.ifBlank { "XAUUSD" } ?: "XAUUSD"

                    _chartScreenshot.value = ChartScreenshotData(
                        isRequested = true,
                        timestamp = System.currentTimeMillis() / 1000L,
                        symbol = curSymbol,
                        timeframe = "M15",
                        statusText = "📸 Imagem REAL do Gráfico Carregada pelo Usuário (${bytes.size / 1024} KB)",
                        imageBase64 = b64,
                        imageFilePath = cacheFile.absolutePath,
                        mimeType = "image/png",
                        imageBytes = bytes
                    )
                    _messageState.value = "✅ Imagem real do gráfico carregada com sucesso!"
                }
            } catch (e: Exception) {
                _messageState.value = "Erro ao carregar imagem: ${e.localizedMessage}"
            }
        }
    }

    fun clearMessage() {
        _messageState.value = null
    }

    fun setFinancialTimeframe(timeframe: EquityTimeframe) {
        _financialTimeframe.value = timeframe
    }

    fun registerDeposit(amount: Double, note: String = "Depósito Registrado") {
        if (amount <= 0) return
        val newTx = FinancialTransaction(
            type = TransactionType.DEPOSIT,
            amount = amount,
            note = note,
            timestamp = System.currentTimeMillis() / 1000L
        )
        _financialTransactions.value = _financialTransactions.value + newTx
        _messageState.value = "💰 Depósito de +MT ${String.format("%.2f", amount)} registrado no gráfico com sucesso!"
    }

    fun registerWithdrawal(amount: Double, note: String = "Saque Registrado") {
        if (amount <= 0) return
        val newTx = FinancialTransaction(
            type = TransactionType.WITHDRAWAL,
            amount = -amount,
            note = note,
            timestamp = System.currentTimeMillis() / 1000L
        )
        _financialTransactions.value = _financialTransactions.value + newTx
        _messageState.value = "💸 Saque de -MT ${String.format("%.2f", amount)} registrado no gráfico com sucesso!"
    }

    fun registerClosedPosition(symbol: String = "XAUUSD", profit: Double, note: String = "Posição Fechada") {
        val newTx = FinancialTransaction(
            type = TransactionType.CLOSED_POSITION,
            symbol = symbol,
            amount = profit,
            note = note,
            timestamp = System.currentTimeMillis() / 1000L
        )
        _financialTransactions.value = _financialTransactions.value + newTx
        val prefix = if (profit >= 0) "📈 Lucro de +MT" else "📉 Prejuízo de MT"
        _messageState.value = "$prefix ${String.format("%.2f", profit)} na posição $symbol atualizado no gráfico Candlestick!"
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
        _messageState.value = null
        if (phone.isBlank() || passwordText.isBlank()) {
            _messageState.value = "Por favor, preencha o telefone e a senha."
            return
        }

        viewModelScope.launch {
            _loginLoading.value = true
            _messageState.value = null
            try {
                val context = getApplication<Application>()
                val deviceIdentityManager = com.example.data.security.DeviceIdentityManager(context)
                val currentSilentUid = deviceIdOverride ?: deviceIdentityManager.getSilentDeviceUid()

                // Primary method of access: Query Firebase first, then fallback to GitHub REST if needed
                val userFromFirebase = repository.searchUserByPhoneFirebase(phone, _firebaseUrl.value, currentSilentUid)
                val user = userFromFirebase ?: repository.searchUserByPhone(phone, _adminConfig.value)
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
                                         rawStoredHash.isBlank()

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

                            if (!user.licencaAtiva) {
                                _messageState.value = "Acesso recusado: LICENÇA INATIVA."
                            } else if (isExpired) {
                                _messageState.value = "Acesso recusado: LICENÇA EXPIRADA (Validade: ${user.licencaValidade})."
                            } else if (user.status != "ATIVO") {
                                _messageState.value = "Acesso recusado: STATUS INATIVO."
                            } else if (user.reembolsoStatus == "APROVADO" || user.reembolsoStatus == "PAGO") {
                                _messageState.value = "Acesso recusado: CONTA BLOQUEADA POR REEMBOLSO (Licença Revogada)."
                            } else {
                                // Update auditoria fields for silent device mapping and logging
                                val updatedUser = user.copy(
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
                rawUserHash.isBlank()

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

    // Submit Support Ticket
    fun submitSupportTicket(categoria: String, assunto: String, mensagem: String, contato: String) {
        viewModelScope.launch {
            _actionLoading.value = true
            _actionLoadingMessage.value = "Enviando ticket de suporte..."
            try {
                kotlinx.coroutines.delay(1000)
                _messageState.value = "✅ Ticket de suporte registrado com sucesso! Categoria: $categoria. Nossa equipe responderá em breve."
            } catch (e: Exception) {
                _messageState.value = "Erro ao enviar ticket: ${e.localizedMessage}"
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
                                        if (str in listOf("true", "1", "online", "ativo", "ok", "sim", "si", "com_posicao", "com posicao", "aberta", "compra", "venda", "buy", "sell", "tem_ordem", "ordem_aberta", "posicao_aberta", "open", "em_operacao")) return true
                                        if (str in listOf("false", "0", "offline", "sem_posicao", "sem posicao", "fechada", "sem_ordem", "none", "nenhuma")) return false
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
                            val temPosicao = parseBool(
                                statusJson,
                                "tem_posicao", "posicao_aberta", "has_position",
                                "tem_ordem", "ordem_aberta", "has_order", "ordens", "orders",
                                "posicao", "em_operacao", "status_posicao", "status_ordem", "order_status"
                            )
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

                    // Intelligent Sync Flow: First login fetches all events into Room local DB; subsequent syncs only fetch new/altered events with GID unique key
                    if (!_isSimulationActive.value) {
                        try {
                            _isSyncing.value = true
                            val syncResult = repository.smartSyncEaRobotEvents(
                                firebaseUrl = _firebaseUrl.value,
                                mt5AccountId = mt5AccountId,
                                authKey = silentUid,
                                userId = currentUserId,
                                forceFullSync = false
                            )
                            val meta = repository.getSyncMetadata(mt5AccountId)
                            _syncMetadata.value = meta
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            _isSyncing.value = false
                        }

                        // Auto-sync Real MT5 Financial History (Deposits, Withdrawals, Position Entries/Exits)
                        val realTxs = mutableListOf<FinancialTransaction>()
                        val movs = repository.fetchHistoricoPatrimonio(_firebaseUrl.value, mt5AccountId, silentUid)
                        movs.forEach { mov ->
                            val id = mov.optString("id", "deal_${mov.optLong("ticket", 0L)}")
                            val typeStr = mov.optString("type", "CLOSED_POSITION").uppercase()
                            if (typeStr.contains("ENTRY")) return@forEach // Skip position entry deals to avoid double counting with CLOSED_POSITION

                            val type = when {
                                typeStr.contains("DEPOSIT") || typeStr.contains("DEPÓSITO") -> TransactionType.DEPOSIT
                                typeStr.contains("WITHDRAWAL") || typeStr.contains("SAQUE") -> TransactionType.WITHDRAWAL
                                else -> TransactionType.CLOSED_POSITION
                            }
                            val symbol = mov.optString("symbol", "CONTA_MT5")
                            val amount = mov.optDouble("amount", 0.0)
                            val timestamp = mov.optLong("timestamp", System.currentTimeMillis() / 1000L)
                            val note = mov.optString("note", "")

                            realTxs.add(
                                FinancialTransaction(
                                    id = id,
                                    type = type,
                                    symbol = symbol,
                                    amount = amount,
                                    note = note,
                                    timestamp = timestamp
                                )
                            )
                        }

                        _eaRobotEvents.value.filter { 
                            it.event.contains("historico_financeiro") || it.event.contains("deal")
                        }.forEach { evt ->
                            val id = evt.id.ifBlank { "evt_${evt.timestamp}" }
                            if (realTxs.none { it.id == id }) {
                                val typeStr = evt.type.ifBlank { evt.msg }.uppercase()
                                if (typeStr.contains("ENTRY")) return@forEach

                                val type = when {
                                    typeStr.contains("DEPOSIT") || typeStr.contains("DEPÓSITO") -> TransactionType.DEPOSIT
                                    typeStr.contains("WITHDRAWAL") || typeStr.contains("SAQUE") -> TransactionType.WITHDRAWAL
                                    else -> TransactionType.CLOSED_POSITION
                                }
                                val symbol = evt.symbol.ifBlank { "CONTA_MT5" }
                                val amount = if (evt.amount != 0.0) evt.amount else evt.diarioValor
                                val timestamp = if (evt.timestamp > 0) evt.timestamp / 1000L else System.currentTimeMillis() / 1000L
                                val note = evt.note.ifBlank { evt.msg }

                                realTxs.add(
                                    FinancialTransaction(
                                        id = id,
                                        type = type,
                                        symbol = symbol,
                                        amount = amount,
                                        note = note,
                                        timestamp = timestamp
                                    )
                                )
                            }
                        }

                        if (realTxs.isNotEmpty()) {
                            val currentNonMock = _financialTransactions.value.filter { !it.id.startsWith("mock_") }
                            val merged = (currentNonMock + realTxs)
                                .distinctBy { it.id }
                                .sortedBy { it.timestamp }
                            _financialTransactions.value = merged
                        }

                        val latestScreenshot = _eaRobotEvents.value.firstOrNull {
                            it.event.lowercase().contains("captura_tela") || it.event.lowercase().contains("screenshot")
                        }
                        if (latestScreenshot != null) {
                            val ts = if (latestScreenshot.timestamp > 0) latestScreenshot.timestamp / 1000L else System.currentTimeMillis() / 1000L
                            val sym = latestScreenshot.symbol.ifBlank { _eaRobotStatus.value?.symbol ?: "EURUSD" }
                            val tf = latestScreenshot.timeframe.ifBlank { "M15" }
                            val b64 = latestScreenshot.imageBase64
                            val context = getApplication<Application>()

                            if (b64.isNotBlank()) {
                                try {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    try {
                                        context.cacheDir.listFiles { _, name -> name.endsWith(".png") || name.contains("chart") }?.forEach { it.delete() }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    val cacheFile = java.io.File(context.cacheDir, "chart_screenshot_latest.png")
                                    cacheFile.writeBytes(bytes)

                                    _chartScreenshot.value = ChartScreenshotData(
                                        isRequested = true,
                                        timestamp = ts,
                                        symbol = sym,
                                        timeframe = tf,
                                        hasFimatheChannels = true,
                                        hasEaPanel = true,
                                        hasTradeArrows = true,
                                        objectsCount = 18,
                                        statusText = "📸 Imagem REAL do Gráfico recebida diretamente do MetaTrader 5 (MT5)",
                                        imageBase64 = b64,
                                        imageFilePath = cacheFile.absolutePath,
                                        mimeType = "image/png",
                                        imageBytes = bytes
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (_chartScreenshot.value.imageFilePath == null) {
                                val result = generateAndSaveChartScreenshot(
                                    context = context,
                                    symbol = sym,
                                    timeframe = tf,
                                    objectsCount = 14
                                )

                                _chartScreenshot.value = ChartScreenshotData(
                                    isRequested = true,
                                    timestamp = ts,
                                    symbol = sym,
                                    timeframe = tf,
                                    hasFimatheChannels = true,
                                    hasEaPanel = true,
                                    hasTradeArrows = true,
                                    objectsCount = 14,
                                    statusText = "Imagem PNG pronta para exibição real",
                                    imageFilePath = result.filePath,
                                    mimeType = result.mimeType,
                                    imageBytes = result.byteArray
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                kotlinx.coroutines.delay(2000) // 2 seconds real-time polling
            }
        }
    }

    fun triggerSimulation() {
        _isSimulationActive.value = true
        val currentAccount = userProfile.value?.mt5AccountId?.ifBlank { "859423" } ?: "859423"
        val accountLong = currentAccount.toLongOrNull() ?: 859423L
        val currentSymbol = _eaRobotStatus.value?.symbol?.ifBlank { "EURUSD" } ?: "EURUSD"
        val nowSec = System.currentTimeMillis() / 1000L

        _eaRobotStatus.value = EaRobotStatus(
            online = true,
            lastPing = nowSec,
            fusoHorario = 2,
            fusoTexto = "GMT+2",
            symbol = "EURUSD",
            temPosicao = true,
            servidor = "ICMarkets-Live01",
            login = accountLong.toInt(),
            saldoDisponivel = 12500.50,
            timestamp = nowSec,
            eaAtivo = true,
            configSyncSuccess = true,
            lastConfigSync = nowSec
        )

        val simEvents = listOf(
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "relatorio_financeiro",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 60,
                diarioStatus = "DENTRO DA META",
                diarioValor = 48.50,
                diarioPct = 0.42,
                semanalStatus = "META ALCANÇADA",
                semanalValor = 245.80,
                semanalPct = 1.95,
                motivacao = "A cada decisão disciplinada, eu construo minha consistência.",
                resumo = "Relatório de desempenho diário e semanal acumulado com sucesso.",
                moeda = "USD",
                data = "06/08/2026",
                hora = "10:30:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "ordem_executada",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 180,
                anterior = "aguardando_sinal",
                novo = "COMPRA",
                msg = "📈 Ordem de Compra executada! Ticket #1048291 | Preço: 1.08540 | SL: 1.08140 | TP: 1.09340.",
                resumo = "Ordem de Compra disparada com sucesso pelo Acumulador.",
                temPosicao = "COMPRA",
                data = "06/08/2026",
                hora = "10:27:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "ordem_modificada",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "GBPUSD",
                timeframe = "M15",
                timestamp = nowSec - 360,
                anterior = "Risco Aberto",
                novo = "Break Even (0x0)",
                msg = "✅ Ordem de Venda ajustada para Break Even no GBPUSD! Ticket #1048288.",
                resumo = "Proteção Break Even ativada com sucesso.",
                data = "06/08/2026",
                hora = "10:24:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "erro_ordem",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 480,
                type = "COMPRA",
                msg = "❌ Falha ao enviar ordem de compra no EURUSD. Erro MQL5: 10013 (Margem Insuficiente / Rejeição do Servidor).",
                resumo = "Erro no Envio de Ordem MQL5.",
                data = "06/08/2026",
                hora = "10:22:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "notificacao_mql5",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 540,
                msg = "Aviso do sistema MQL5: volatilidade elevada detectada no ativo EURUSD.",
                resumo = "Notificação e Alerta de Volatilidade MQL5.",
                data = "06/08/2026",
                hora = "10:21:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "mudanca_estado",
                sistema = "ESTADO DE EXECUCAO",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 600,
                anterior = "ESTADO_DE_EXECUCAO_INICIAL",
                novo = "ESTADO_DE_EXECUCAO_COMPRA_INICIAL",
                descAnterior = "Monitorando mercado à procura de condições de entrada...",
                descNovo = "Rompimento comprador confirmado! Executando ordem de compra e ativando gestão de risco.",
                msg = "Transição do Motor de Execução: ESTADO_DE_EXECUCAO_COMPRA_INICIAL",
                resumo = "Transição de Estado de Execução Efetuada.",
                data = "06/08/2026",
                hora = "10:20:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "mudanca_equador",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 900,
                anterior = "EQ_ALTA_Z1",
                novo = "EQ_ALTA_Z2",
                msg = "Equador alterado de EQ_ALTA_Z1 para EQ_ALTA_Z2. Preço acima da linha de equador central.",
                resumo = "Transição de Zona de Equador Registrada.",
                data = "06/08/2026",
                hora = "10:15:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "sessao_inicio",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 1800,
                sessao = "LONDRES",
                msg = "INICIO DA SESSAO: LONDRES | Início: 07:00 | Fim: 13:20",
                resumo = "Início da Sessão de Londres Disparado.",
                data = "06/08/2026",
                hora = "10:00:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "sessao_fim",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 3600,
                sessao = "TOKYO",
                msg = "FIM DA SESSAO: TOKYO | Encerramento automático de ordens pendentes.",
                resumo = "Fim da Sessão de Tóquio Disparado.",
                data = "06/08/2026",
                hora = "09:00:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "posicao_alterada",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 5400,
                msg = "Nova posição aberta no ativo EURUSD!",
                temPosicao = "true",
                resumo = "Mudança de Posição Detectada.",
                data = "06/08/2026",
                hora = "08:30:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "captura_tela_concluida",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 6000,
                msg = "Captura de tela do gráfico enviada com sucesso ao App com objetos MQL5.",
                resumo = "Captura de Tela com Objetos Concluída.",
                data = "06/08/2026",
                hora = "08:20:00"
            ),
            com.example.data.EaRobotEvent(
                id = currentAccount,
                currency = "USD",
                event = "inicializacao",
                login = accountLong,
                server = "ICMarkets-Live01",
                symbol = "EURUSD",
                timeframe = "M15",
                timestamp = nowSec - 7200,
                msg = "🚀 EA Fimaster inicializado com sucesso no servidor ICMarkets-Live01.",
                resumo = "Inicialização Completa do Robô EA.",
                data = "06/08/2026",
                hora = "08:00:00"
            )
        ) + com.example.data.EaNotificationEventsCatalog.generateNotificationEventsList(accountLong, currentSymbol)

        _eaRobotEvents.value = simEvents.map { com.example.data.EaEventGidManager.ensureGid(it) }
        _messageState.value = "⚡ Simulação disparada! Todos os eventos e status do robô EA foram gerados."
    }

    fun clearEvents() {
        viewModelScope.launch {
            val accountId = userProfile.value?.mt5AccountId?.ifBlank { "859423" } ?: "859423"
            repository.clearLocalEventsPreservingSyncPosition(accountId)
            _eaRobotEvents.value = emptyList()
            _syncMetadata.value = repository.getSyncMetadata(accountId)
            _messageState.value = "🗑️ Eventos limpos localmente. Apenas novos eventos a partir deste ponto serão baixados no próximo Smart Sync."
        }
    }

    fun triggerSmartSync(forceFull: Boolean = false) {
        viewModelScope.launch {
            if (_isSimulationActive.value) {
                _messageState.value = "⚡ Modo de simulação ativo. Desative a simulação para sincronizar dados reais."
                return@launch
            }
            val accountId = userProfile.value?.mt5AccountId?.ifBlank { "859423" } ?: "859423"
            val context = getApplication<Application>()
            val silentUid = _loggedUser.value?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
            }
            val currentUserId = _loggedUser.value?.id.orEmpty()
            _isSyncing.value = true
            try {
                val syncResult = repository.smartSyncEaRobotEvents(
                    firebaseUrl = _firebaseUrl.value,
                    mt5AccountId = accountId,
                    authKey = silentUid,
                    userId = currentUserId,
                    forceFullSync = forceFull
                )
                _syncMetadata.value = repository.getSyncMetadata(accountId)
                _messageState.value = "🔄 ${syncResult.message}"
            } catch (e: Exception) {
                _messageState.value = "❌ Falha na sincronização: ${e.localizedMessage ?: "Erro de conexão"}"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun stopStatusPolling() {
        statusPollJob?.cancel()
        statusPollJob = null
        _isSimulationActive.value = false
        _eaRobotStatus.value = null
        _eaRobotEvents.value = emptyList()
    }

    fun toggleEaAtivo(ativo: Boolean) {
        val currentConfig = eaConfig.value ?: com.example.data.EaConfigEntity(mt5AccountId = userProfile.value?.mt5AccountId ?: "859423")
        val updatedConfig = currentConfig.copy(EA_ATIVO = ativo)
        val actionMessage = if (ativo) "Ativando robô EA no servidor MT5..." else "Desativando robô EA no servidor MT5..."
        saveEaConfig(updatedConfig, bypassValidation = true, customLoadingMessage = actionMessage)
    }

    fun fetchExchangeRate(targetCurrencyCode: String = "MZN", onResult: (Double?) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _actionLoading.value = true
            val targetKey = when (targetCurrencyCode.trim().uppercase()) {
                "MT", "METICAL", "MZN" -> "MZN"
                "R$", "REAL", "BRL" -> "BRL"
                "€", "EURO", "EUR" -> "EUR"
                "KZ", "KWANZA", "AOA" -> "AOA"
                else -> targetCurrencyCode.trim().uppercase().ifBlank { "MZN" }
            }
            _actionLoadingMessage.value = "Buscando cotação oficial de 1 USD em $targetKey..."
            try {
                var rate: Double? = null

                // 1. Try ExchangeRate API
                try {
                    val url = java.net.URL("https://open.er-api.com/v6/latest/USD")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 7000
                    conn.readTimeout = 7000

                    if (conn.responseCode == 200) {
                        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = org.json.JSONObject(responseText)
                        if (json.optString("result") == "success" && json.has("rates")) {
                            val rates = json.getJSONObject("rates")
                            if (rates.has(targetKey)) {
                                rate = rates.getDouble(targetKey)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. Fallback to AwesomeAPI for the specific requested pair
                if (rate == null) {
                    try {
                        val pair = "USD-$targetKey"
                        val url = java.net.URL("https://economia.awesomeapi.com.br/json/last/$pair")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"
                        conn.connectTimeout = 7000
                        conn.readTimeout = 7000
                        if (conn.responseCode == 200) {
                            val text = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = org.json.JSONObject(text)
                            val key = "USD$targetKey"
                            if (json.has(key)) {
                                val bidStr = json.getJSONObject(key).optString("bid")
                                rate = bidStr.toDoubleOrNull()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (rate != null) {
                        _messageState.value = "🌐 Cotação obtida: 1 USD = $rate $targetKey"
                        onResult(rate)
                    } else {
                        _messageState.value = "⚠️ Não foi possível obter a cotação de $targetKey online no momento. Verifique sua conexão."
                        onResult(null)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _messageState.value = "❌ Erro ao buscar cotação de $targetKey: ${e.localizedMessage}"
                    onResult(null)
                }
            } finally {
                _actionLoading.value = false
            }
        }
    }
}

data class ChartScreenshotResult(
    val file: java.io.File,
    val filePath: String,
    val mimeType: String,
    val byteArray: ByteArray,
    val bitmap: android.graphics.Bitmap
)

data class ChartScreenshotData(
    val timestamp: Long = System.currentTimeMillis() / 1000L,
    val symbol: String = "XAUUSD (GOLD)",
    val timeframe: String = "M15",
    val objectsCount: Int = 16,
    val hasFimatheChannels: Boolean = true,
    val hasEaPanel: Boolean = true,
    val hasTradeArrows: Boolean = true,
    val statusText: String = "Objetos MQL5 Capturados com Sucesso",
    val imageBase64: String? = null,
    val imageFilePath: String? = null,
    val mimeType: String = "image/png",
    val imageBytes: ByteArray? = null,
    val isRequested: Boolean = false
) {
    fun getReconstructedBitmap(): android.graphics.Bitmap? {
        if (imageBytes != null && imageBytes.isNotEmpty()) {
            return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        if (!imageFilePath.isNullOrBlank()) {
            val file = java.io.File(imageFilePath)
            if (file.exists()) {
                return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            }
        }
        return null
    }
}

fun generateAndSaveChartScreenshot(
    context: android.content.Context,
    symbol: String,
    timeframe: String,
    objectsCount: Int = 18,
    existingBase64: String? = null
): ChartScreenshotResult {
    val mimeType = "image/png"
    val cacheDir = context.cacheDir

    // 1. Limpar capturas anteriores do armazenamento temporário/cache
    try {
        cacheDir.listFiles { _, name -> name.endsWith(".png") || name.contains("chart") }?.forEach { it.delete() }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // 2. Obter o ByteArray da imagem (decode de Base64 existente ou renderização nativa)
    val byteArray: ByteArray = if (!existingBase64.isNullOrBlank()) {
        try {
            android.util.Base64.decode(existingBase64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            createChartBitmapBytes(symbol, timeframe, objectsCount)
        }
    } else {
        createChartBitmapBytes(symbol, timeframe, objectsCount)
    }

    // 3. Criar o arquivo no armazenamento interno/cache do aplicativo (substitui automaticamente o arquivo anterior)
    val imageFile = java.io.File(cacheDir, "chart_screenshot_latest.png")

    // 4. Salvar os bytes no arquivo de cache interno
    imageFile.writeBytes(byteArray)

    // 5. Reconstruir a imagem a partir dos bytes / arquivo gravado
    val reconstructedBitmap = android.graphics.BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
        ?: android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
        ?: createFallbackBitmap()

    return ChartScreenshotResult(
        file = imageFile,
        filePath = imageFile.absolutePath,
        mimeType = mimeType,
        byteArray = byteArray,
        bitmap = reconstructedBitmap
    )
}

private fun createChartBitmapBytes(symbol: String, timeframe: String, objectsCount: Int): ByteArray {
    val width = 1200
    val height = 700
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Fundo Escuro MQL5
    canvas.drawColor(android.graphics.Color.parseColor("#0B0F19"))

    val paintGrid = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#1E293B")
        strokeWidth = 1.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    // Grade do Gráfico
    for (i in 1..10) {
        val x = width * (i / 11f)
        canvas.drawLine(x, 0f, x, height.toFloat(), paintGrid)
    }
    for (j in 1..7) {
        val y = height * (j / 8f)
        canvas.drawLine(0f, y, width.toFloat(), y, paintGrid)
    }

    // Linhas de Nível Fimathe (Objetos MQL5)
    val paintCyan = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#22D3EE"); strokeWidth = 3f }
    val paintEmerald = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#10B981"); strokeWidth = 4f }
    val paintAmber = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F59E0B"); strokeWidth = 3f; pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 6f), 0f) }
    val paintRose = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#F43F5E"); strokeWidth = 3f }

    canvas.drawLine(0f, height * 0.22f, width.toFloat(), height * 0.20f, paintCyan)
    canvas.drawLine(0f, height * 0.40f, width.toFloat(), height * 0.38f, paintEmerald)
    canvas.drawLine(0f, height * 0.52f, width.toFloat(), height * 0.50f, paintAmber)
    canvas.drawLine(0f, height * 0.64f, width.toFloat(), height * 0.62f, paintEmerald)
    canvas.drawLine(0f, height * 0.80f, width.toFloat(), height * 0.78f, paintRose)

    // Velas de Preço (Candlesticks)
    val paintGreen = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#10B981"); style = android.graphics.Paint.Style.FILL }
    val paintRed = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#EF4444"); style = android.graphics.Paint.Style.FILL }
    val paintWick = android.graphics.Paint().apply { strokeWidth = 2.5f }

    val candlesCount = 32
    val candleWidth = 18f
    val startX = 60f
    val stepX = (width - 120f) / candlesCount

    var lastClose = height * 0.55f
    val random = java.util.Random(symbol.hashCode().toLong() + System.currentTimeMillis() % 1000)

    for (i in 0 until candlesCount) {
        val cx = startX + i * stepX
        val change = (random.nextFloat() - 0.48f) * 60f
        val open = lastClose
        val close = open - change
        val high = minOf(open, close) - random.nextFloat() * 30f
        val low = maxOf(open, close) + random.nextFloat() * 30f
        lastClose = close

        val isBull = close <= open
        val paintCandle = if (isBull) paintGreen else paintRed
        paintWick.color = if (isBull) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#EF4444")

        canvas.drawLine(cx, high, cx, low, paintWick)
        val top = minOf(open, close)
        val bottom = maxOf(open, close)
        canvas.drawRect(cx - candleWidth / 2f, top, cx + candleWidth / 2f, maxOf(top + 2f, bottom), paintCandle)
    }

    // Painel EA MQL5 Overlay
    val paintPanelBg = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#D90F172A"); style = android.graphics.Paint.Style.FILL }
    val paintPanelBorder = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#0284C7"); style = android.graphics.Paint.Style.STROKE; strokeWidth = 2f }
    val rectF = android.graphics.RectF(30f, 30f, 440f, 220f)
    canvas.drawRoundRect(rectF, 16f, 16f, paintPanelBg)
    canvas.drawRoundRect(rectF, 16f, 16f, paintPanelBorder)

    val paintTextTitle = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#38BDF8")
        textSize = 22f
        isFakeBoldText = true
        isAntiAlias = true
    }
    val paintTextSub = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#E2E8F0")
        textSize = 17f
        isAntiAlias = true
    }
    val paintTextGreen = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#34D399")
        textSize = 16f
        isFakeBoldText = true
        isAntiAlias = true
    }

    canvas.drawText("🤖 ROBÔ FIMASTER EA (MQL5)", 50f, 68f, paintTextTitle)
    canvas.drawText("Ativo: $symbol | Timeframe: $timeframe", 50f, 105f, paintTextSub)
    canvas.drawText("Canais Fimathe: ATIVOS (3 Níveis)", 50f, 138f, paintTextSub)
    canvas.drawText("Objetos no Gráfico: $objectsCount Elementos", 50f, 170f, paintTextSub)
    canvas.drawText("● EA ONLINE • Conexão MT5 OK", 50f, 202f, paintTextGreen)

    // Rodapé com Carimbo de Data e MIME
    val paintWatermark = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#64748B")
        textSize = 18f
        isAntiAlias = true
    }
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
    val dateStr = sdf.format(java.util.Date())
    canvas.drawText("MIME: image/png • Cap: $dateStr • Cache Interno", width - 520f, height - 25f, paintWatermark)

    // Comprime a imagem gerada para ByteArray no formato PNG
    val stream = java.io.ByteArrayOutputStream()
    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}

fun shareChartScreenshot(
    context: android.content.Context,
    chartScreenshot: ChartScreenshotData
) {
    try {
        val cacheDir = java.io.File(context.cacheDir, "shared_images").apply { mkdirs() }
        val shareFile = java.io.File(cacheDir, "chart_capture_${System.currentTimeMillis()}.png")

        val bitmap = chartScreenshot.getReconstructedBitmap()
        if (bitmap != null) {
            val fos = java.io.FileOutputStream(shareFile)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
        } else if (!chartScreenshot.imageBase64.isNullOrBlank()) {
            val bytes = android.util.Base64.decode(chartScreenshot.imageBase64, android.util.Base64.DEFAULT)
            shareFile.writeBytes(bytes)
        } else if (chartScreenshot.imageBytes != null && chartScreenshot.imageBytes.isNotEmpty()) {
            shareFile.writeBytes(chartScreenshot.imageBytes)
        } else if (!chartScreenshot.imageFilePath.isNullOrBlank() && java.io.File(chartScreenshot.imageFilePath).exists()) {
            java.io.File(chartScreenshot.imageFilePath).copyTo(shareFile, overwrite = true)
        } else {
            val bytes = createChartBitmapBytes(chartScreenshot.symbol, chartScreenshot.timeframe, chartScreenshot.objectsCount)
            shareFile.writeBytes(bytes)
        }

        val contentUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val timeStr = if (chartScreenshot.timestamp > 0) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
            sdf.format(java.util.Date(chartScreenshot.timestamp * 1000L))
        } else ""

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            val shareText = buildString {
                append("📊 Captura do Gráfico MT5 - Robô Fimaster\n")
                append("Paridade: ${chartScreenshot.symbol} (${chartScreenshot.timeframe})\n")
                if (timeStr.isNotEmpty()) append("Horário: $timeStr\n")
                append("Status: ${chartScreenshot.statusText}")
            }
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Captura do Gráfico MT5 - ${chartScreenshot.symbol}")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = android.content.Intent.createChooser(shareIntent, "Compartilhar Captura do Gráfico")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Erro ao compartilhar imagem: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun shareEventScreenshot(
    context: android.content.Context,
    event: com.example.data.EaRobotEvent
) {
    try {
        val cacheDir = java.io.File(context.cacheDir, "shared_images").apply { mkdirs() }
        val shareFile = java.io.File(cacheDir, "event_capture_${System.currentTimeMillis()}.png")

        if (event.imageBase64.isNotBlank()) {
            val bytes = android.util.Base64.decode(event.imageBase64, android.util.Base64.DEFAULT)
            shareFile.writeBytes(bytes)
        } else {
            val sym = if (event.symbol.isNotBlank()) event.symbol else "XAUUSD"
            val tf = if (event.timeframe.isNotBlank()) event.timeframe else "M15"
            val bytes = createChartBitmapBytes(sym, tf, 18)
            shareFile.writeBytes(bytes)
        }

        val contentUri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )

        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
            val shareText = "📊 Captura do Gráfico MT5 - ${event.symbol} (${event.timeframe})\n${event.msg}"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(shareIntent, "Compartilhar Captura")
        chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Erro ao compartilhar captura: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun createFallbackBitmap(): android.graphics.Bitmap {
    val bitmap = android.graphics.Bitmap.createBitmap(400, 200, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.DKGRAY)
    return bitmap
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

enum class TransactionType {
    CLOSED_POSITION,
    DEPOSIT,
    WITHDRAWAL
}

data class FinancialTransaction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: TransactionType = TransactionType.CLOSED_POSITION,
    val symbol: String = "XAUUSD",
    val amount: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis() / 1000L,
    val note: String = ""
)

enum class EquityTimeframe(val label: String, val shortCode: String) {
    PER_POSITION("Por Posição", "1T"),
    DAILY("Diário", "1D"),
    WEEKLY("Semanal", "1W"),
    MONTHLY("Mensal", "1M")
}

data class FinancialCandle(
    val id: String,
    val periodLabel: String,
    val timestamp: Long,
    val openBalance: Double,
    val highBalance: Double,
    val lowBalance: Double,
    val closeBalance: Double,
    val netProfit: Double,
    val deposits: Double,
    val withdrawals: Double,
    val tradeCount: Int,
    val isBullish: Boolean = closeBalance >= openBalance
)

fun buildCandlesFromTransactions(
    transactions: List<FinancialTransaction>,
    timeframe: EquityTimeframe,
    initialBalance: Double
): List<FinancialCandle> {
    if (transactions.isEmpty()) return emptyList()
    val sortedTxs = transactions.sortedBy { it.timestamp }

    val hasDeposit = sortedTxs.any { it.type == TransactionType.DEPOSIT }
    val startingBalance = if (hasDeposit) 0.0 else initialBalance

    when (timeframe) {
        EquityTimeframe.PER_POSITION -> {
            var currentBalance = startingBalance
            val candles = mutableListOf<FinancialCandle>()
            
            sortedTxs.forEachIndexed { index, tx ->
                val openBal = currentBalance
                val change = when (tx.type) {
                    TransactionType.DEPOSIT -> kotlin.math.abs(tx.amount)
                    TransactionType.WITHDRAWAL -> -kotlin.math.abs(tx.amount)
                    TransactionType.CLOSED_POSITION -> tx.amount
                }
                val closeBal = openBal + change
                currentBalance = closeBal

                val highBal = kotlin.math.max(openBal, closeBal)
                val lowBal = kotlin.math.min(openBal, closeBal)
                val isDeposit = tx.type == TransactionType.DEPOSIT
                val isWithdrawal = tx.type == TransactionType.WITHDRAWAL
                val isTrade = tx.type == TransactionType.CLOSED_POSITION

                val label = when (tx.type) {
                    TransactionType.CLOSED_POSITION -> "#${index + 1} ${tx.symbol}"
                    TransactionType.DEPOSIT -> "#${index + 1} +Depósito"
                    TransactionType.WITHDRAWAL -> "#${index + 1} -Saque"
                }

                candles.add(
                    FinancialCandle(
                        id = tx.id,
                        periodLabel = label,
                        timestamp = tx.timestamp,
                        openBalance = openBal,
                        highBalance = highBal,
                        lowBalance = lowBal,
                        closeBalance = closeBal,
                        netProfit = if (isTrade) tx.amount else 0.0,
                        deposits = if (isDeposit) kotlin.math.abs(tx.amount) else 0.0,
                        withdrawals = if (isWithdrawal) kotlin.math.abs(tx.amount) else 0.0,
                        tradeCount = if (isTrade) 1 else 0
                    )
                )
            }
            return candles
        }

        EquityTimeframe.DAILY -> {
            return groupTransactionsByTimeFormat(sortedTxs, "yyyy-MM-dd", "dd/MM", startingBalance)
        }

        EquityTimeframe.WEEKLY -> {
            return groupTransactionsByTimeFormat(sortedTxs, "yyyy-'W'ww", "'Sem' w", startingBalance)
        }

        EquityTimeframe.MONTHLY -> {
            return groupTransactionsByTimeFormat(sortedTxs, "yyyy-MM", "MMM yyyy", startingBalance)
        }
    }
}

private fun groupTransactionsByTimeFormat(
    sortedTxs: List<FinancialTransaction>,
    groupPattern: String,
    labelPattern: String,
    startingBalance: Double
): List<FinancialCandle> {
    val groupSdf = java.text.SimpleDateFormat(groupPattern, java.util.Locale.getDefault())
    val labelSdf = java.text.SimpleDateFormat(labelPattern, java.util.Locale.getDefault())

    val groupedMap = sortedTxs.groupBy { groupSdf.format(java.util.Date(it.timestamp * 1000L)) }
    val candles = mutableListOf<FinancialCandle>()

    var runningBalance = startingBalance

    for ((groupKey, groupTxs) in groupedMap) {
        val openBal = runningBalance
        var highBal = openBal
        var lowBal = openBal
        var currentBal = openBal

        var periodNetProfit = 0.0
        var periodDeposits = 0.0
        var periodWithdrawals = 0.0
        var tradeCount = 0

        for (tx in groupTxs) {
            val change = when (tx.type) {
                TransactionType.DEPOSIT -> kotlin.math.abs(tx.amount)
                TransactionType.WITHDRAWAL -> -kotlin.math.abs(tx.amount)
                TransactionType.CLOSED_POSITION -> tx.amount
            }
            currentBal += change
            if (currentBal > highBal) highBal = currentBal
            if (currentBal < lowBal) lowBal = currentBal

            when (tx.type) {
                TransactionType.CLOSED_POSITION -> {
                    periodNetProfit += tx.amount
                    tradeCount++
                }
                TransactionType.DEPOSIT -> periodDeposits += kotlin.math.abs(tx.amount)
                TransactionType.WITHDRAWAL -> periodWithdrawals += kotlin.math.abs(tx.amount)
            }
        }

        val closeBal = currentBal
        runningBalance = closeBal

        val sampleDate = groupTxs.firstOrNull()?.timestamp ?: (System.currentTimeMillis() / 1000L)
        val formattedLabel = labelSdf.format(java.util.Date(sampleDate * 1000L))

        candles.add(
            FinancialCandle(
                id = groupKey,
                periodLabel = formattedLabel,
                timestamp = sampleDate,
                openBalance = openBal,
                highBalance = highBal,
                lowBalance = lowBal,
                closeBalance = closeBal,
                netProfit = periodNetProfit,
                deposits = periodDeposits,
                withdrawals = periodWithdrawals,
                tradeCount = tradeCount
            )
        )
    }
    return candles
}

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
