package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.RefundRequest
import com.example.data.UserProfile
import com.example.data.validarParametros
import java.text.NumberFormat
import java.util.Locale

enum class PortalTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD("Conta EA", Icons.Default.TrendingUp),
    CLIENT_LICENSE("Licença", Icons.Default.Verified),
    EA_EVENTS("Eventos EA", Icons.Default.Notifications),
    EA_CONFIG("Config EA", Icons.Default.Tune),
    HISTORY("Histórico", Icons.Default.History),
    SECURITY("Segurança", Icons.Default.Lock)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalApp(viewModel: PortalViewModel) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val loggedUser by viewModel.loggedUser.collectAsStateWithLifecycle()
    val loginLoading by viewModel.loginLoading.collectAsStateWithLifecycle()
    val actionLoading by viewModel.actionLoading.collectAsStateWithLifecycle()
    val actionLoadingMessage by viewModel.actionLoadingMessage.collectAsStateWithLifecycle()
    val adminConfig by viewModel.adminConfig.collectAsStateWithLifecycle()
    val dataSourceMode by viewModel.dataSourceMode.collectAsStateWithLifecycle()
    val firebaseUrl by viewModel.firebaseUrl.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.messageState.collectAsStateWithLifecycle()
    val refundRequests by viewModel.refundRequests.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(PortalTab.DASHBOARD) }
    var showAdminConfig by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("fimaster_prefs", android.content.Context.MODE_PRIVATE) }
    var showEaTourDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val hasSeen = prefs.getBoolean("has_seen_ea_tour", false)
            if (!hasSeen) {
                showEaTourDialog = true
                prefs.edit().putBoolean("has_seen_ea_tour", true).apply()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        feedbackMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    if (!isLoggedIn) {
        LoginScreen(
            viewModel = viewModel,
            adminConfig = adminConfig,
            loginLoading = loginLoading,
            onOpenAdminConfig = { showAdminConfig = true }
        )
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "PORTAL FIMASTER",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = Color(0xFF22D3EE)
                            )
                        )
                    },
                    navigationIcon = {
                        TextButton(
                            onClick = { showEaTourDialog = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF22D3EE))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = "Guia EA",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Guia EA",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.logout() }) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Sair",
                                tint = Color(0xFFEF4444)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF0F172A), // Slate 900
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0xFF334155).copy(alpha = 0.4f), // border-slate-800
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                ) {
                    PortalTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            label = {
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        color = if (currentTab == tab) Color(0xFF22D3EE) else Color(0xFF64748B)
                                    )
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (currentTab == tab) Color(0xFF22D3EE) else Color(0xFF64748B)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color(0xFF22D3EE).copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF0A0F1E), // Deep dark space blue
                                Color(0xFF050811)  // Deeper background
                            )
                        )
                    )
            ) {
                if (actionLoading) {
                    TopNeonProcessingBar(
                        loadingMessage = actionLoadingMessage,
                        onDismiss = { viewModel.dismissActionLoading() }
                    )
                }

                // Top Notification Banner (placed at Top after PROCESSAMENTO ÚNICO)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.fillMaxWidth()
                ) { data ->
                    val msgLower = data.visuals.message.lowercase()
                    val isError = msgLower.contains("erro") ||
                            msgLower.contains("falha") ||
                            msgLower.contains("recusado") ||
                            msgLower.contains("incorreta") ||
                            msgLower.contains("expirada") ||
                            msgLower.contains("bloqueada") ||
                            msgLower.contains("❌")
                    val isSuccess = msgLower.contains("sucesso") ||
                            msgLower.contains("sincronizado") ||
                            msgLower.contains("enviado") ||
                            msgLower.contains("atualizada") ||
                            msgLower.contains("bem-vindo") ||
                            msgLower.contains("✅")

                    val infiniteTransition = rememberInfiniteTransition(label = "SnackbarNeonAnim")
                    val neonPhase by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "SnackbarPhase"
                    )

                    val neonBrush = if (isError) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFEF4444), Color(0xFFF87171), Color(0xFFDC2626), Color(0xFFEF4444)),
                            start = androidx.compose.ui.geometry.Offset(1000f * neonPhase, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f * neonPhase - 400f, 200f)
                        )
                    } else if (isSuccess) {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF00F0FF), Color(0xFF10B981), Color(0xFF34D399), Color(0xFF00F0FF)),
                            start = androidx.compose.ui.geometry.Offset(1000f * neonPhase, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f * neonPhase - 400f, 200f)
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF22D3EE), Color(0xFF38BDF8), Color(0xFFA855F7), Color(0xFF22D3EE)),
                            start = androidx.compose.ui.geometry.Offset(1000f * neonPhase, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f * neonPhase - 400f, 200f)
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .border(width = 2.dp, brush = neonBrush, shape = RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) Color(0xFFEF4444) else if (isSuccess) Color(0xFF10B981) else Color(0xFF22D3EE),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = data.visuals.message,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { data.dismiss() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    val eaRobotStatus by viewModel.eaRobotStatus.collectAsStateWithLifecycle()
                    val eaConfig by viewModel.eaConfig.collectAsStateWithLifecycle()
                    val eaRobotEvents by viewModel.eaRobotEvents.collectAsStateWithLifecycle()

                    when (currentTab) {
                        PortalTab.DASHBOARD -> DashboardScreen(
                            loggedUser = loggedUser,
                            eaRobotStatus = eaRobotStatus,
                            eaConfig = eaConfig,
                            onSaveMt5Id = { viewModel.updateMt5IdServerless(it) },
                            onStartTour = { showEaTourDialog = true }
                        )
                        PortalTab.CLIENT_LICENSE -> ClientLicenseScreen(
                            loggedUser = loggedUser,
                            refundRequests = refundRequests,
                            onRequestNewRefund = { viewModel.requestRefundServerless() }
                        )
                        PortalTab.EA_EVENTS -> EaRobotEventsScreen(
                            events = eaRobotEvents,
                            mt5AccountId = loggedUser?.mt5IdConta ?: ""
                        )
                        PortalTab.EA_CONFIG -> EaConfigScreen(viewModel = viewModel)
                        PortalTab.HISTORY -> HistoryScreen(loggedUser)
                        PortalTab.SECURITY -> SecurityScreen(
                            loggedUser = loggedUser,
                            onUpdatePassword = { curr, new, conf -> viewModel.changePasswordServerless(curr, new, conf) }
                        )
                    }
                }
            }
        }
    }

    if (showEaTourDialog) {
        EaOnboardingTourDialog(
            onDismiss = { showEaTourDialog = false },
            onNavigateTab = { tab ->
                currentTab = tab
            }
        )
    }

    if (showAdminConfig) {
        AdminConfigDialog(
            config = adminConfig,
            currentMode = dataSourceMode,
            currentFirebaseUrl = firebaseUrl,
            onDismiss = { showAdminConfig = false },
            onSave = { newConfig, newMode, newUrl ->
                viewModel.saveAdminConfig(newConfig, newMode, newUrl)
                showAdminConfig = false
            }
        )
    }
}

@Composable
fun HeroEaBalanceCard(
    saldo: Double,
    creditoGuardado: Double = 0.0,
    isOnline: Boolean = false,
    temPosicao: Boolean = false,
    cambio: Double = 64.0,
    currencySymbol: String = "MT"
) {
    val valorCambiado = saldo * cambio

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF22D3EE),
                        Color(0xFF3B82F6),
                        Color(0xFF10B981)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.95f),
                            Color(0xFF0F172A).copy(alpha = 0.98f)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF22D3EE).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Saldo EA",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SALDO DISPONÍVEL DO EA",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF22D3EE),
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                            )
                            Text(
                                text = "Capital Operacional do Robô MT5",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }

                    Surface(
                        color = if (isOnline) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isOnline) "EA ONLINE" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                val formattedSaldo = try {
                    String.format(Locale.US, "%,.2f", valorCambiado)
                } catch (e: Exception) {
                    "0.00"
                }

                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = formattedSaldo,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = (-1).sp
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "Original: $%,.2f USD (Câmbio: %.1f MT/USD)", saldo, cambio),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF94A3B8),
                        fontWeight = FontWeight.SemiBold
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "POSIÇÃO NO MERCADO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (temPosicao) "OPERAÇÃO ABERTA" else "SEM POSIÇÃO ABERTA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (temPosicao) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CRÉDITO GUARDADO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = String.format(Locale.US, "%,.2f %s", creditoGuardado, currencySymbol),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    loggedUser: com.example.data.GithubUser?,
    eaRobotStatus: com.example.ui.EaRobotStatus?,
    eaConfig: com.example.data.EaConfigEntity?,
    onSaveMt5Id: (String) -> Unit,
    onStartTour: () -> Unit = {}
) {
    if (loggedUser == null) return

    val availableBalance = if (eaRobotStatus?.saldoDisponivel != null && eaRobotStatus.saldoDisponivel > 0.0) {
        eaRobotStatus.saldoDisponivel
    } else {
        loggedUser.saldo
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "PAINEL DA CONTA EA MT5",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF22D3EE),
                    letterSpacing = 2.sp
                )
            )
        }

        item {
            // ISOLATED, PROMINENT HERO SALDO CARD (WITH CAMBIO)
            HeroEaBalanceCard(
                saldo = availableBalance,
                creditoGuardado = loggedUser.creditoGuardado,
                isOnline = eaRobotStatus?.online == true,
                temPosicao = eaRobotStatus?.temPosicao == true,
                cambio = eaConfig?.CAMBIO ?: 64.0,
                currencySymbol = "MT"
            )
        }

        item {
            // Real-time Robot Execution Card
            EaRobotStatusCard(eaRobotStatus, cambio = eaConfig?.CAMBIO ?: 64.0)
        }

        item {
            // Quick MT5 Account Management Card
            Mt5AccountQuickManageCard(
                mt5AccountId = loggedUser.mt5IdConta,
                onSaveMt5Id = onSaveMt5Id
            )
        }

        item {
            // Trading Visual Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_trading_banner_1783542679424),
                    contentDescription = "Fimaster EA MT5",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0F1E).copy(alpha = 0.85f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = "Expert Advisor Fimaster MT5",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Conexão direta e atualizações em tempo real com seu robô de negociação",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.75f))
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ClientLicenseScreen(
    loggedUser: com.example.data.GithubUser?,
    refundRequests: List<RefundRequest>,
    onRequestNewRefund: () -> Unit
) {
    if (loggedUser == null) return

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "INFORMAÇÕES DA LICENÇA E CLIENTE",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF22D3EE),
                    letterSpacing = 2.sp
                )
            )
        }

        item {
            WelcomeHeader(loggedUser.nome, loggedUser.mt5IdConta)
        }

        item {
            UserUidCard(user = loggedUser)
        }

        item {
            LicenseStatusCard(
                status = if (loggedUser.licencaAtiva) "ATIVA" else "EXPIRADA",
                expiryDate = loggedUser.licencaValidade
            )
        }

        item {
            // General Client Details Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DADOS DA COMPRA & ASSINATURA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    DetailRow(label = "Nome do Cliente", value = loggedUser.nome)
                    DetailRow(label = "Número de Telefone", value = loggedUser.numero)
                    DetailRow(label = "ID do Utilizador", value = loggedUser.id)
                    DetailRow(label = "Origem do Registro", value = loggedUser.origem)
                    DetailRow(label = "Nível de Autorização", value = loggedUser.nivelAutorizacao)
                    DetailRow(label = "Status da Licença", value = if (loggedUser.licencaAtiva) "ATIVA" else "EXPIRADA", isAccent = true)
                    DetailRow(label = "Produto Contratado", value = loggedUser.licencaProduto)
                    DetailRow(label = "Plano Adquirido", value = loggedUser.licencaPlano)
                    DetailRow(label = "Validade da Licença", value = loggedUser.licencaValidade)
                    DetailRow(label = "Crédito Guardado", value = String.format("%,.2f MT", loggedUser.creditoGuardado))
                }
            }
        }

        item {
            // Reembolso Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SOLICITAÇÃO DE REEMBOLSO",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    letterSpacing = 1.sp
                                )
                            )
                            Text(
                                text = "Garantia da compra e licença de uso",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                            )
                        }

                        Button(
                            onClick = onRequestNewRefund,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Solicitar",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    if (refundRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        refundRequests.forEach { req ->
                            RefundRequestItemCard(request = req)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun EaRobotEventsScreen(
    events: List<com.example.data.EaRobotEvent>,
    mt5AccountId: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EVENTOS E LOGS DO ROBÔ EA",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF22D3EE),
                            letterSpacing = 2.sp
                        )
                    )
                    Text(
                        text = if (mt5AccountId.isNotBlank()) "Conta MT5: $mt5AccountId" else "Todas as Contas",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )
                }

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Text(
                        text = "${events.size} Eventos Visíveis",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (events.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Sem Eventos",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Nenhum evento do robô encontrado",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Os eventos enviados pelo robô MT5 serão listados aqui automaticamente em tempo real.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(events) { event ->
                EaEventItemCard(event = event)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun cleanStateEnumText(raw: String): String {
    if (raw.isBlank()) return ""
    var s = raw.trim()
    val prefixes = listOf(
        "ESTADO_DE_EXECUCAO_",
        "ESTADO_DE_PRECOS_",
        "ESTADO_SICLO_DE_CANAL_",
        "ESTADO_SICLO_DE_",
        "ESTADO_SICLO_",
        "ESTADO_DE_",
        "ESTADO_"
    )
    for (p in prefixes) {
        if (s.startsWith(p, ignoreCase = true)) {
            s = s.substring(p.length)
            break
        }
    }
    return s.replace("_", " ").trim().uppercase()
}

fun detectStateSystemType(event: com.example.data.EaRobotEvent): String {
    val sysUpper = event.sistema.uppercase().trim()
    val combined = "$sysUpper ${event.novo} ${event.anterior} ${event.event}".uppercase()

    return when {
        sysUpper.contains("EXECU") || combined.contains("EXECUCAO") || combined.contains("ROBO") || combined.contains("COMPRA") || combined.contains("VENDA") -> "ESTADO DE EXECUÇÃO"
        sysUpper.contains("PREÇO") || sysUpper.contains("PRECO") || combined.contains("PRECOS") || combined.contains("RANGE") || combined.contains("EXPANSAO") -> "ESTADO DE PREÇO"
        sysUpper.contains("CICLO") || sysUpper.contains("CANAL") || combined.contains("SICLO") || combined.contains("CICLO") -> "ESTADO DE CICLO"
        else -> if (sysUpper.isNotBlank()) cleanStateEnumText(sysUpper) else "ESTADO DE EXECUÇÃO"
    }
}

object AppTtsManager {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var isInitializing = false
    private var isReady = false
    private var activeMediaPlayer: android.media.MediaPlayer? = null

    fun speak(
        context: android.content.Context,
        text: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        val textToSpeak = text.trim()
        if (textToSpeak.isBlank()) return

        stopAll()

        val appContext = context.applicationContext

        fun playViaMediaPlayerFallback() {
            try {
                val encodedText = java.net.URLEncoder.encode(textToSpeak.take(250), "UTF-8")
                val audioUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=pt&client=tw-ob"
                val headers = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10)")

                val player = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(appContext, android.net.Uri.parse(audioUrl), headers)
                    prepareAsync()
                    setOnPreparedListener {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onStart() }
                        start()
                    }
                    setOnCompletionListener {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                        try { release() } catch (e: Exception) {}
                        if (activeMediaPlayer == this) activeMediaPlayer = null
                    }
                    setOnErrorListener { mp, _, _ ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onDone()
                            onError("Não foi possível carregar voz no dispositivo.")
                        }
                        try { mp.release() } catch (e: Exception) {}
                        if (activeMediaPlayer == mp) activeMediaPlayer = null
                        true
                    }
                }
                activeMediaPlayer = player
            } catch (e: Exception) {
                onDone()
                onError("Erro de áudio: ${e.localizedMessage}")
            }
        }

        fun doSpeakWithTts(engine: android.speech.tts.TextToSpeech) {
            try {
                engine.stop()
                engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onStart() }
                    }
                    override fun onDone(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post { onDone() }
                    }
                    override fun onError(utteranceId: String?) {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playViaMediaPlayerFallback()
                        }
                    }
                })

                val params = android.os.Bundle().apply {
                    putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                    putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                val result = engine.speak(textToSpeak, android.speech.tts.TextToSpeech.QUEUE_FLUSH, params, "PORTAL_TTS_${System.currentTimeMillis()}")
                if (result != android.speech.tts.TextToSpeech.SUCCESS) {
                    playViaMediaPlayerFallback()
                }
            } catch (e: Exception) {
                playViaMediaPlayerFallback()
            }
        }

        val currentTts = tts
        if (currentTts != null && isReady) {
            doSpeakWithTts(currentTts)
            return
        }

        if (isInitializing) {
            playViaMediaPlayerFallback()
            return
        }

        isInitializing = true
        var newTts: android.speech.tts.TextToSpeech? = null
        newTts = android.speech.tts.TextToSpeech(appContext) { status ->
            isInitializing = false
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                var langRes = newTts?.setLanguage(java.util.Locale("pt", "BR"))
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = newTts?.setLanguage(java.util.Locale("pt", "PT"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = newTts?.setLanguage(java.util.Locale("pt"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    newTts?.setLanguage(java.util.Locale.getDefault())
                }

                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                newTts?.setAudioAttributes(audioAttributes)
                newTts?.setSpeechRate(0.95f)

                tts = newTts
                isReady = true
                newTts?.let { doSpeakWithTts(it) }
            } else {
                playViaMediaPlayerFallback()
            }
        }
    }

    fun stopAll() {
        try {
            tts?.stop()
        } catch (e: Exception) {}
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
        } catch (e: Exception) {}
        activeMediaPlayer = null
    }
}

@Composable
fun ClassicEventCard(event: com.example.data.EaRobotEvent) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isSpeaking) {
                AppTtsManager.stopAll()
            }
        }
    }

    val eventLower = remember(event) { event.event.lowercase() }

    val isRelatorio = remember(eventLower) {
        eventLower.contains("relatorio") || eventLower.contains("financeiro")
    }
    val isSessao = remember(eventLower) {
        eventLower.contains("sessao") || eventLower.contains("sessão")
    }
    val isEquador = remember(eventLower) {
        eventLower.contains("equador")
    }
    val isInicializacao = remember(eventLower) {
        eventLower.contains("inicializ")
    }
    val isStateChange = remember(eventLower, event) {
        event.anterior.isNotEmpty() || event.novo.isNotEmpty() || event.descNovo.isNotEmpty() ||
                eventLower.contains("mudanca") || eventLower.contains("estado") || eventLower.contains("status")
    }

    val (cardTitle, badgeIcon, badgeColor) = remember(event, eventLower) {
        when {
            isRelatorio -> Triple("RELATÓRIO FINANCEIRO EA", Icons.Default.TrendingUp, Color(0xFF10B981))
            isSessao -> {
                val isStart = eventLower.contains("inicio") || eventLower.contains("start")
                Triple("SESSÃO FOREX ${if (isStart) "INICIADA" else "ENCERRADA"}", Icons.Default.Schedule, Color(0xFF38BDF8))
            }
            isEquador -> Triple("TRANSIÇÃO EQUADOR", Icons.Default.CompareArrows, Color(0xFFF59E0B))
            isInicializacao -> Triple("INICIALIZAÇÃO DO ROBÔ", Icons.Default.PlayArrow, Color(0xFF10B981))
            isStateChange -> {
                val sysCat = detectStateSystemType(event)
                Triple(sysCat, Icons.Default.Tune, Color(0xFF22D3EE))
            }
            eventLower.contains("erro") -> Triple("ERRO DE EXECUÇÃO", Icons.Default.Error, Color(0xFFEF4444))
            eventLower.contains("alerta") || eventLower.contains("warning") -> Triple("ALERTA DO SISTEMA", Icons.Default.Warning, Color(0xFFF59E0B))
            else -> Triple("EVENTO: ${event.event.uppercase().replace("_", " ")}", Icons.Default.Info, Color(0xFF38BDF8))
        }
    }

    val textToSpeak = remember(event, isRelatorio, isSessao, isEquador, isInicializacao, isStateChange) {
        when {
            isRelatorio -> {
                buildString {
                    append("Relatório financeiro do robô Fimaster. ")
                    if (event.diarioStatus.isNotBlank()) append("Diário: ${event.diarioStatus} de ${event.diarioValor} ${event.moeda}. ")
                    if (event.semanalStatus.isNotBlank()) append("Semanal: ${event.semanalStatus} de ${event.semanalValor} ${event.moeda}. ")
                    if (event.motivacao.isNotBlank()) append("Motivação: ${event.motivacao}. ")
                    if (event.resumo.isNotBlank()) append("Resumo: ${event.resumo}.")
                }
            }
            isSessao -> {
                val action = if (eventLower.contains("inicio") || eventLower.contains("start")) "iniciada" else "encerrada"
                "Sessão Forex ${event.sessao.ifEmpty { "Mercado" }} $action para o ativo ${event.symbol}."
            }
            isEquador -> {
                "Linha do Equador alterada de ${cleanStateEnumText(event.anterior)} para ${cleanStateEnumText(event.novo)} no ativo ${event.symbol}."
            }
            isInicializacao -> {
                "Robô EA Fimaster inicializado no ativo ${event.symbol} no servidor ${event.server} para a conta ${event.login}."
            }
            isStateChange -> {
                val stateText = cleanStateEnumText(event.novo.ifEmpty { event.anterior })
                val desc = cleanStateEnumText(event.descNovo.ifEmpty { event.descAnterior.ifEmpty { event.msg } })
                val sysName = detectStateSystemType(event)
                "Mudança de estado no $sysName. Estado: $stateText. $desc"
            }
            else -> {
                "Evento ${event.event}: ${cleanStateEnumText(event.msg.ifEmpty { event.resumo.ifEmpty { "Registrado para o ativo " + event.symbol } })}"
            }
        }
    }

    fun speakText(showToast: Boolean = true) {
        if (textToSpeak.isBlank()) return

        if (isSpeaking) {
            AppTtsManager.stopAll()
            isSpeaking = false
            return
        }

        if (showToast) {
            android.widget.Toast.makeText(context, "🔊 Reproduzindo áudio...", android.widget.Toast.LENGTH_SHORT).show()
        }

        AppTtsManager.speak(
            context = context,
            text = textToSpeak,
            onStart = { isSpeaking = true },
            onDone = { isSpeaking = false },
            onError = { msg ->
                isSpeaking = false
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()) }
    val dateFormatted = remember(event.timestamp) {
        if (event.timestamp > 0) {
            val tsMs = if (event.timestamp in 1L..9_999_999_999L) event.timestamp * 1000L else event.timestamp
            try { sdf.format(java.util.Date(tsMs)) } catch (e: Exception) { "N/A" }
        } else "N/A"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Badge + Title + Audio Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(badgeColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = cardTitle,
                            tint = badgeColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = badgeColor,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                Surface(
                    onClick = { speakText(showToast = true) },
                    color = if (isSpeaking) Color(0xFF10B981).copy(alpha = 0.2f) else badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSpeaking) Color(0xFF10B981) else badgeColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "Ouvir em Áudio",
                            tint = if (isSpeaking) Color(0xFF10B981) else badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSpeaking) "Lendo..." else "Áudio 🔊",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSpeaking) Color(0xFF10B981) else badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Specific Card Body Layouts based on event type
            when {
                isRelatorio -> {
                    // Relatório Financeiro Compact Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📊 GERENCIAMENTO DIÁRIO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${event.diarioStatus.ifEmpty { "STATUS OK" }}: ${String.format(java.util.Locale.US, "%.2f", event.diarioValor)} ${event.moeda} (${String.format(java.util.Locale.US, "%.2f", event.diarioPct)}%)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📊 GERENCIAMENTO SEMANAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${event.semanalStatus.ifEmpty { "STATUS OK" }}: ${String.format(java.util.Locale.US, "%.2f", event.semanalValor)} ${event.moeda} (${String.format(java.util.Locale.US, "%.2f", event.semanalPct)}%)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (event.motivacao.isNotBlank()) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        text = "🏆 Dica de Ouro / Motivação:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = event.motivacao,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFEF3C7))
                                    )
                                }
                            }
                        }

                        if (event.resumo.isNotBlank()) {
                            Text(
                                text = "📢 Resumo: ${event.resumo}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                isSessao -> {
                    // Sessão Forex Compact Layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "SESSÃO MERCADO",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                            )
                            Text(
                                text = event.sessao.ifEmpty { "Forex Global" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        val timeStr = if (event.horaInicio in 0..23) {
                            val min = if (event.minutoInicio in 0..59) event.minutoInicio else 0
                            String.format(java.util.Locale.US, "%02d:%02d", event.horaInicio, min)
                        } else if (event.horaFim in 0..23) {
                            val min = if (event.minutoFim in 0..59) event.minutoFim else 0
                            String.format(java.util.Locale.US, "%02d:%02d", event.horaFim, min)
                        } else if (event.hora.isNotBlank()) {
                            event.hora
                        } else if (event.timestamp > 0) {
                            val tsMs = if (event.timestamp in 1L..9_999_999_999L) event.timestamp * 1000L else event.timestamp
                            try {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(tsMs))
                            } catch (e: Exception) { "" }
                        } else ""

                        if (timeStr.isNotBlank()) {
                            Surface(
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Horário: $timeStr",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                isEquador -> {
                    // Mudança Linha Equador Compact Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("ANTERIOR", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)))
                                Text(
                                    text = cleanStateEnumText(event.anterior).ifEmpty { "N/A" },
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            Column {
                                Text("NOVO EQUADOR", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B)))
                                Text(
                                    text = cleanStateEnumText(event.novo).ifEmpty { "ATIVO" },
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        if (event.msg.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = cleanStateEnumText(event.msg), style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1)))
                        }
                    }
                }

                isInicializacao -> {
                    // Inicialização EA Compact & Proportional Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "✅ EA Fimaster conectado & ativo",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            )
                            if (event.symbol.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = event.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        val infoParts = mutableListOf<String>()
                        if (event.login > 0) infoParts.add("Conta MT5: ${event.login}")
                        if (event.server.isNotBlank()) infoParts.add("Servidor: ${event.server}")
                        if (infoParts.isNotEmpty()) {
                            Text(
                                text = infoParts.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                            )
                        }
                    }
                }

                else -> {
                    // Standard / Transition State Change Layout with System Type & Clean Enums
                    val sistemaTipo = detectStateSystemType(event)
                    val cleanNovo = cleanStateEnumText(event.novo)
                    val cleanAnterior = cleanStateEnumText(event.anterior)
                    val rawDesc = event.descNovo.ifEmpty { event.descAnterior.ifEmpty { event.msg.ifEmpty { event.resumo } } }
                    val cleanDesc = if (rawDesc.contains("ESTADO_")) cleanStateEnumText(rawDesc) else rawDesc.replace("_", " ")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = sistemaTipo,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = badgeColor,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            }

                            if (cleanNovo.isNotBlank()) {
                                Text(
                                    text = cleanNovo,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (cleanAnterior.isNotBlank() && cleanAnterior != cleanNovo) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DE: $cleanAnterior",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                                )
                                Text(text = "➔", style = MaterialTheme.typography.labelSmall.copy(color = badgeColor))
                                Text(
                                    text = "PARA: ${cleanNovo.ifEmpty { "ATIVADO" }}",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (cleanDesc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = cleanDesc,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), lineHeight = 16.sp)
                            )
                        }

                        if (event.motivacao.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Motivo: ${cleanStateEnumText(event.motivacao)}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Footer metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (event.symbol.isNotBlank()) {
                        Text(
                            text = "Ativo: ${event.symbol}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                        )
                    }
                    if (event.timeframe.isNotBlank()) {
                        Text(
                            text = "TF: ${event.timeframe}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                    if (event.server.isNotBlank()) {
                        Text(
                            text = "Servidor: ${event.server}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }

                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                )
            }
        }
    }
}

@Composable
fun EaEventItemCard(event: com.example.data.EaRobotEvent) {
    ClassicEventCard(event = event)
}

@Composable
fun ClassicStateChangeCard(event: com.example.data.EaRobotEvent) {
    ClassicEventCard(event = event)
}

@Composable
fun Mt5AccountQuickManageCard(
    mt5AccountId: String,
    onSaveMt5Id: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputId by remember(mt5AccountId) { mutableStateOf(mt5AccountId) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Conta MT5",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CONTA DE NEGOCIAÇÃO MT5",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.sp
                        )
                    )
                }

                IconButton(onClick = { isEditing = !isEditing }) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Close else Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = Color(0xFF22D3EE)
                    )
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = inputId,
                    onValueChange = { inputId = it },
                    label = { Text("Número da Conta MT5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF22D3EE),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        onSaveMt5Id(inputId.trim())
                        isEditing = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar ID da Conta", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (mt5AccountId.isNotBlank()) "ID Conectado: $mt5AccountId" else "Nenhuma conta vinculada",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun EaRobotStatusCard(status: com.example.ui.EaRobotStatus?, cambio: Double = 64.0) {
    var currentTimeSec by remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis() / 1000L) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeSec = System.currentTimeMillis() / 1000L
            kotlinx.coroutines.delay(1000)
        }
    }

    val rawPing = status?.lastPing ?: 0L
    val pingSec = if (rawPing > 10_000_000_000L) rawPing / 1000L else rawPing
    val fusoHorario = status?.fusoHorario ?: 0
    val pingUtc = if (pingSec > 0) (pingSec - (fusoHorario * 3600L)) else 0L

    val elapsedUtc = if (pingUtc > 0) (currentTimeSec - pingUtc) else 0L
    val elapsedRaw = if (pingSec > 0) (currentTimeSec - pingSec) else 0L
    val secondsAgo = if (elapsedUtc in 0..86400) elapsedUtc else elapsedRaw.coerceAtLeast(0)

    val isOnline = status?.online == true
    val displayColor = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444) // Green when online, Red when offline

    val signalTimeFormatted = when {
        isOnline -> "Online agora (sinal ativo)"
        pingSec == 0L -> "Sem sinal registrado"
        secondsAgo < 60 -> "Há ${secondsAgo}s"
        secondsAgo < 3600 -> "Há ${secondsAgo / 60}m ${secondsAgo % 60}s"
        secondsAgo < 86400 -> "Há ${secondsAgo / 3600}h ${(secondsAgo % 3600) / 60}m"
        else -> "Há ${secondsAgo / 86400}d ${(secondsAgo % 86400) / 3600}h"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.85f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = displayColor.copy(alpha = 0.25f),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header Row with Robot Icon and Online Pulse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Estado do EA",
                        tint = if (isOnline) Color(0xFF22D3EE) else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ROBÔ FIMASTER MT5",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 1.sp
                        )
                    )
                }

                // Pulsing light tag
                Surface(
                    color = displayColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, displayColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pulse circle
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(displayColor)
                        )
                        Text(
                            text = if (isOnline) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = displayColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid details
            if (isOnline && status != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FUSO HORÁRIO (EA)",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status.fusoTexto,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "POSIÇÃO OPERACIONAL",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (status.temPosicao) "⚠️ Posição Aberta" else "💤 Em Espera (Sem ordens)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (status.temPosicao) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SERVIDOR MT5",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = status.servidor.ifBlank { "N/A" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SINAL RECEBIDO HÁ",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = signalTimeFormatted,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFF22D3EE),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SALDO DISPONÍVEL (MARGEM LIVRE)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%,.2f", status.saldoDisponivel * cambio) + " MT",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format("Original: $%,.2f USD (Câmbio: %.1f)", status.saldoDisponivel, cambio),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B)
                            )
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "Garantido",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            } else {
                // Offline guidance
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Aviso Offline",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (pingSec > 0) "Robô MT5 desconectado. Último sinal recebido há $signalTimeFormatted." else "O robô Fimaster está offline ou não está executando no MetaTrader 5 do seu computador.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeHeader(clientName: String, mt5AccountId: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "PORTAL FIMASTER",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF22D3EE), // Cyan 400
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Olá, $clientName",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
            )
            if (mt5AccountId.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = "Conta MT5",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONTA MT5 ID: $mt5AccountId",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        }
        
        // Avatar Placeholder with Glowing Border
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B))
                .border(1.5.dp, Color(0xFF22D3EE), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Perfil",
                tint = Color(0xFF22D3EE),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun UserUidCard(user: com.example.data.GithubUser) {
    var showDialog by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val deviceIdentityManager = remember(context) { com.example.data.security.DeviceIdentityManager(context) }
    val silentDeviceUid = remember(context) { deviceIdentityManager.getSilentDeviceUid() }

    val effectiveUid = if (user.id.isNotBlank()) user.id else if (user.mt5IdConta.isNotBlank()) user.mt5IdConta else user.numero.filter { it.isDigit() }
    val deviceUid = user.auditoriaUltimoDispositivo.ifBlank { silentDeviceUid }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = Color(0xFF22D3EE).copy(alpha = 0.4f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "UID",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "IDENTIFICADOR ÚNICO (UID)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )
                    )
                }

                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = "Abrir Tela Menor UID",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "UID do Utilizador / Dispositivo",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = effectiveUid,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(effectiveUid))
                            android.widget.Toast.makeText(context, "UID copiado!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF22D3EE).copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Copiar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = "Dispositivo",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Silent Device UID: $silentDeviceUid",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }

    if (showDialog) {
        UserUidCompactDialog(
            user = user,
            effectiveUid = effectiveUid,
            silentDeviceUid = silentDeviceUid,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
fun UserUidCompactDialog(
    user: com.example.data.GithubUser,
    effectiveUid: String,
    silentDeviceUid: String,
    onDismiss: () -> Unit
) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier
                .width(320.dp)
                .padding(16.dp)
                .border(1.5.dp, Color(0xFF22D3EE), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF22D3EE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = "Identificador Único",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "IDENTIFICADOR ÚNICO (UID)",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color(0xFF22D3EE),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = user.nome.ifBlank { "Utilizador" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "UID DO UTILIZADOR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = effectiveUid,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color(0xFF22D3EE),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = Color(0xFF334155))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "CONTA MT5: ${user.mt5IdConta.ifBlank { "N/A" }}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SILENT DEVICE UID:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = silentDeviceUid,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF22D3EE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(silentDeviceUid))
                            android.widget.Toast.makeText(context, "Silent Device UID copiado!", android.widget.Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE))
                    ) {
                        Text("Copiar UID", color = Color(0xFF0F172A), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun LicenseStatusCard(status: String, expiryDate: String) {
    val displayStatusColor = if (status.equals("Ativa", ignoreCase = true)) {
        Color(0xFF22D3EE) // Bright Cyan 400
    } else if (status.equals("Pendente", ignoreCase = true)) {
        Color(0xFFF59E0B) // Warning Orange
    } else {
        Color(0xFFEF4444) // Error Red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1E293B), // Slate 800
                        Color(0xFF0F172A)  // Slate 900
                    )
                )
            )
            .border(
                1.dp,
                Color(0xFF334155).copy(alpha = 0.5f), // Border-slate-700/50
                RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LICENÇA ATUAL",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF94A3B8), // Slate 400
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(displayStatusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EA PRO MAX v4.2",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }

                // Styled status tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(displayStatusColor.copy(alpha = 0.15f))
                        .border(1.dp, displayStatusColor.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = status.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = displayStatusColor,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = Color(0xFF334155).copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "CONTA MT5",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B), // Slate 500
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ID: 88429105",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFCFFAFE), // Cyan 100
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "EXPIRA EM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B), // Slate 500
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = expiryDate,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BalanceWidget(balance: Double) {
    val formattedBalance = String.format("%,.2f", balance) + " MT"
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF050811))
            .border(
                1.dp,
                Color(0xFF334155).copy(alpha = 0.3f), // border-slate-700/30
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Lucro Acumulado",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color(0xFF94A3B8), // slate-400
                        fontWeight = FontWeight.Medium
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Atualizado via MetaTrader 5",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFF64748B) // slate-500
                    )
                )
            }
            Text(
                text = "+ $formattedBalance",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF22D3EE), // cyan-400
                    letterSpacing = (-0.5).sp
                )
            )
        }
    }
}

@Composable
fun AccountDetailsCard(profile: UserProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Detalhes da Conta MT5",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Nome do Cliente",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = profile.fullName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID do Usuário Portal",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = "FIM-#000${profile.id}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Conta MetaTrader 5 (ID)",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Text(
                    text = profile.mt5AccountId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun RefundsScreen(
    loggedUser: com.example.data.GithubUser?,
    refundRequests: List<RefundRequest>,
    onRequestNewRefund: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Portal de Reembolso",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = Color.White
            )
        )
        Text(
            text = "Política de garantia de satisfação e reembolso da sua licença Fimaster.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF94A3B8)
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // Rules display Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    Color(0xFF22D3EE).copy(alpha = 0.2f),
                    RoundedCornerShape(16.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Regras de Reembolso",
                        tint = Color(0xFF22D3EE),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Regras & Política de Reembolso",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Período de Garantia: Solicite o reembolso em até 7 dias corridos após a ativação.\n" +
                           "• Processamento de Reembolso: A devolução é processada em até 48 horas úteis.\n" +
                           "• Bloqueio da Licença: Ao ter o reembolso aprovado ou pago, o acesso ao robô Fimaster será revogado imediatamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (loggedUser != null) {
            Text(
                text = "Sua Solicitação Atual",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (loggedUser.reembolsoSolicitado) {
                val status = loggedUser.reembolsoStatus.uppercase()
                val badgeColor = when (status) {
                    "APROVADO", "PAGO" -> Color(0xFF10B981) // Green
                    "REJEITADO" -> Color(0xFFEF4444) // Red
                    "PENDENTE" -> Color(0xFF22D3EE) // Cyan/Yellow
                    else -> Color(0xFF94A3B8)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            badgeColor.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "STATUS: $status",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Você já possui uma solicitação de reembolso ativa no sistema com o status acima. Novas solicitações de reembolso estão bloqueadas para esta conta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRequestNewRefund,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = "Solicitar Reembolso",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Solicitar Reembolso",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Histórico de Transações Locais",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (refundRequests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sem transações locais registradas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(refundRequests) { request ->
                    RefundRequestItemCard(request = request)
                }
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun RefundRequestItemCard(request: RefundRequest) {
    val statusColor = when (request.status.lowercase()) {
        "aprovado" -> Color(0xFF10B981) // Solid Green
        "pendente" -> Color(0xFF22D3EE) // Bright Cyan (as in HTML: "Pendente" is cyan-400)
        "rejeitado" -> Color(0xFFEF4444) // Coral Red
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusIcon = when (request.status.lowercase()) {
        "aprovado" -> Icons.Default.CheckCircle
        "pendente" -> Icons.Default.HourglassEmpty
        "rejeitado" -> Icons.Default.Error
        else -> Icons.Default.Info
    }

    val formattedAmount = String.format("%,.2f", request.amountMT) + " MT"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E293B).copy(alpha = 0.4f)) // slate-800/40
            .border(
                1.dp,
                Color(0xFF334155).copy(alpha = 0.3f), // border-slate-700/30
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REEMBOLSO #${request.id}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8), // slate-400
                        letterSpacing = 1.sp
                    )
                )
                
                // Status badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = request.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusColor,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Valor Solicitado",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)) // slate-500
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Solicitado em: ${request.requestDate}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8), // slate-400
                            fontWeight = FontWeight.Medium
                        )
                    )
                    if (request.status.equals("Aprovado", ignoreCase = true)) {
                        Text(
                            text = "Pago em: ${request.paymentDate}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color(0xFF334155).copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Motivo: ${request.reason}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Normal
                )
            )
        }
    }
}

@Composable
fun ActionsScreen(
    userProfile: UserProfile?,
    onUpdateMt5: (String) -> Unit,
    onUpdatePassword: (String, String, String) -> Unit,
    onUpdateGithub: (String, String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    var mt5IdInput by remember { mutableStateOf(userProfile?.mt5AccountId ?: "") }
    
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var isPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(userProfile) {
        if (userProfile != null && mt5IdInput.isEmpty()) {
            mt5IdInput = userProfile.mt5AccountId
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Painel de Ações",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
            Text(
                text = "Gerencie seus parâmetros e configurações de acesso.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        // Section 1: Update MT5 ID
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuração MT5",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Atualizar Conta MetaTrader 5",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vincule o robô EA MT5 ao seu ID de conta correto para sincronização de lucros e validação da licença ativa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = mt5IdInput,
                        onValueChange = { mt5IdInput = it },
                        label = { Text("ID da Conta MetaTrader 5 (MT5)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onUpdateMt5(mt5IdInput)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Salvar ID da Conta",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // Section 2: Change Password
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Segurança",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Alterar Senha do Portal",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mantenha o seu acesso de autoatendimento seguro alterando sua senha periodicamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Senha Atual") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Ver senha"
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nova Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar Nova Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onUpdatePassword(currentPassword, newPassword, confirmPassword)
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Atualizar Senha",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        // Section 3: GitHub Sync Credentials
        item {
            var githubTokenInput by remember { mutableStateOf(userProfile?.githubToken ?: "") }
            var githubRepoInput by remember { mutableStateOf(userProfile?.githubRepo ?: "") }
            var githubBranchInput by remember { mutableStateOf(userProfile?.githubBranch ?: "main") }

            LaunchedEffect(userProfile) {
                if (userProfile != null) {
                    githubTokenInput = userProfile.githubToken
                    githubRepoInput = userProfile.githubRepo
                    githubBranchInput = userProfile.githubBranch
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sincronização Nuvem",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sincronização com GitHub",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Insira suas credenciais de repositório para salvar seus parâmetros de EA MQL5 (.set e .json) automaticamente em nuvem na inicialização do EA.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = githubTokenInput,
                        onValueChange = { githubTokenInput = it },
                        label = { Text("GitHub Personal Access Token (PAT)") },
                        placeholder = { Text("ghp_xxxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = githubRepoInput,
                        onValueChange = { githubRepoInput = it },
                        label = { Text("Repositório GitHub (dono/nome)") },
                        placeholder = { Text("usuario/ea-parametros") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = githubBranchInput,
                        onValueChange = { githubBranchInput = it },
                        label = { Text("Branch de Sincronização") },
                        placeholder = { Text("main") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onUpdateGithub(githubTokenInput, githubRepoInput, githubBranchInput)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Salvar Configurações Nuvem",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefundRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (Double, String) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Novo Reembolso",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Solicitar Reembolso",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Atenção: A solicitação de reembolso será analisada pelo suporte Fimaster em até 48 horas úteis. O reembolso é feito em Meticais (MT) para a mesma carteira ou conta de origem.",
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Valor Estimado em Meticais (MT)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Motivo / Justificativa") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Cancelar",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            onSubmit(amount, reasonText)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = "Enviar Solicitação",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EaConfigScreen(viewModel: PortalViewModel) {
    val configState by viewModel.eaConfig.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val dataSourceMode by viewModel.dataSourceMode.collectAsStateWithLifecycle()
    val eaRobotStatus by viewModel.eaRobotStatus.collectAsStateWithLifecycle()
    
    var localConfig by remember { mutableStateOf<com.example.data.EaConfigEntity?>(null) }
    
    LaunchedEffect(configState) {
        if (configState != null) {
            if (localConfig == null) {
                localConfig = configState
            } else {
                localConfig = localConfig?.copy(EA_ATIVO = configState!!.EA_ATIVO)
            }
        }
    }
    
    val config = localConfig
    if (config == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF22D3EE))
        }
        return
    }

    var expTemplates by remember { mutableStateOf(false) }
    var expAuth by remember { mutableStateOf(true) }
    var expColor by remember { mutableStateOf(false) }
    var expTrend by remember { mutableStateOf(false) }
    var expStrategy by remember { mutableStateOf(false) }
    var expAuto by remember { mutableStateOf(false) }
    var expOrder by remember { mutableStateOf(false) }
    var expCapital by remember { mutableStateOf(false) }
    var expOps by remember { mutableStateOf(false) }
    var expResult by remember { mutableStateOf(false) }

    var customTemplates by remember { mutableStateOf<List<Pair<String, com.example.data.EaConfigEntity>>>(emptyList()) }
    var newTemplateName by remember { mutableStateOf("") }
    var templateMessage by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item(key = "ea_header") {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Configurar EA MetaTrader 5",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = Color.White
                )
            )
            Text(
                text = "Modifique os parâmetros do robô Fimaster e sincronize-os diretamente na nuvem.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22D3EE))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vínculo Ativo: Conta MT5 #${config.mt5AccountId}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF22D3EE)
                    )
                )
            }
        }

        // Section 0: Card de Controle Ativar / Desativar EA e Sincronização
        item(key = "ea_status_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (config.EA_ATIVO) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ESTADO DO ROBÔ (EA)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = if (config.EA_ATIVO) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, if (config.EA_ATIVO) Color(0xFF10B981) else Color(0xFFEF4444))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (config.EA_ATIVO) Color(0xFF10B981) else Color(0xFFEF4444))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (config.EA_ATIVO) "EA ATIVADO" else "EA DESATIVADO",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = if (config.EA_ATIVO) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Toggle Button
                        Button(
                            onClick = {
                                val newAtivo = !config.EA_ATIVO
                                localConfig = config.copy(EA_ATIVO = newAtivo)
                                viewModel.toggleEaAtivo(newAtivo)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (config.EA_ATIVO) Color(0xFFDC2626) else Color(0xFF059669)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (config.EA_ATIVO) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (config.EA_ATIVO) "DESATIVAR EA" else "ATIVAR EA",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Sync Status Banner (New Stylish Card without emoji)
                    val isSynced = eaRobotStatus?.configSyncSuccess == true || (eaRobotStatus?.lastConfigSync ?: 0L) > 0L
                    
                    val cardBgBrush = if (isSynced) {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF064E3B).copy(alpha = 0.5f),
                                Color(0xFF022C22).copy(alpha = 0.7f)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF0F172A).copy(alpha = 0.6f),
                                Color(0xFF1E293B).copy(alpha = 0.6f)
                            )
                        )
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = 1.dp,
                                color = if (isSynced) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF38BDF8).copy(alpha = 0.4f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(cardBgBrush)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSynced) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, if (isSynced) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.Sync,
                                            contentDescription = null,
                                            tint = if (isSynced) Color(0xFF34D399) else Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isSynced) "Parâmetros sincronizados com sucesso pelo EA" else "Sincronizando parâmetros com o robô MetaTrader...",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSynced) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                            modifier = Modifier.padding(start = 6.dp)
                                        ) {
                                            Text(
                                                text = if (isSynced) "ONLINE" else "SYNC",
                                                color = if (isSynced) Color(0xFF34D399) else Color(0xFF38BDF8),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 9.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    
                                    Text(
                                        text = if (isSynced && (eaRobotStatus?.lastConfigSync ?: 0L) > 0L) {
                                            val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                                                .format(java.util.Date((eaRobotStatus?.lastConfigSync ?: 0L) * 1000L))
                                            "Confirmado pelo MT5 em $dateStr"
                                        } else {
                                            "O robô lê e aplica as alterações automaticamente a cada 5 segundos no MT5"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val validationWarning = config.validarParametros()
        if (validationWarning.isNotEmpty()) {
            item(key = "ea_validation_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alerta de Validação",
                            tint = Color(0xFFEF4444)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = validationWarning,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Card de Templates & Presets
        item(key = "ea_templates_card") {
            CategoryHeaderCard(
                title = "📁 Templates & Presets de Configuração",
                description = "Carregue conjuntos de parâmetros pré-definidos ou salve o atual",
                icon = Icons.Default.Bookmark,
                isExpanded = expTemplates,
                onToggle = { expTemplates = !expTemplates }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "⚡ PRESETS PADRÃO DISPONÍVEIS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    )

                    val defaultPresets = listOf(
                        Triple("⚡ Fimathe M15 Padrão", "FIMATHE M15 | Lote 0.01", config.copy(
                            ESTRATÉGIA = "FIMATHE",
                            OperationalPeriod = "PERIOD_M15",
                            lot = 0.01,
                            Nives = 1.0,
                            Costurar = true,
                            virada_de_jogo = false,
                            AUTO_PERIOD = "HORA_1"
                        )),
                        Triple("🌊 F_Surfada D1 Agressivo", "F_SURFADA D1 | Lote 0.05", config.copy(
                            ESTRATÉGIA = "F_SURFADA",
                            OperationalPeriod = "PERIOD_D1",
                            lot = 0.05,
                            Nives = 2.0,
                            Costurar = true,
                            virada_de_jogo = true,
                            AUTO_SURFADA = true,
                            AUTO_PERIOD = "DIARIO"
                        )),
                        Triple("🛡️ Conservador M30", "FIMATHE M30 | Risco Diário 0.5%", config.copy(
                            ESTRATÉGIA = "FIMATHE",
                            OperationalPeriod = "PERIOD_M30",
                            lot = 0.01,
                            GERENCIAMENTO_DE_RISCO_DIARIO = true,
                            porcentos = 0.5,
                            poercentosg = 0.5,
                            posicaoTake = true,
                            santo = 25.0,
                            dedo = 15
                        )),
                        Triple("🚀 Scalper M1", "FIMATHE M1 | Sessões Ativas", config.copy(
                            ESTRATÉGIA = "FIMATHE",
                            OperationalPeriod = "PERIOD_M1",
                            lot = 0.02,
                            Costurar = true,
                            AUTO_PERIOD = "HORA_1",
                            SESSAO_LONDRES = true,
                            SESSAO_NOVA_YORQUI = true
                        ))
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        defaultPresets.forEach { (title, subtitle, presetConfig) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        localConfig = presetConfig
                                        templateMessage = "Preset '$title' aplicado aos campos!"
                                    },
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text(text = subtitle, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                    }
                                    Button(
                                        onClick = {
                                            localConfig = presetConfig
                                            templateMessage = "Preset '$title' aplicado aos campos!"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("📥 Carregar", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                    Text(
                        text = "💾 SALVAR CONFIGURAÇÃO ATUAL COMO TEMPLATE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTemplateName,
                            onValueChange = { newTemplateName = it },
                            label = { Text("Nome do Template") },
                            placeholder = { Text("Ex: Meu Setup XAUUSD") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                if (newTemplateName.isNotBlank()) {
                                    val name = newTemplateName.trim()
                                    customTemplates = customTemplates + Pair(name, config)
                                    templateMessage = "Template '$name' salvo com sucesso!"
                                    newTemplateName = ""
                                }
                            },
                            enabled = newTemplateName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salvar", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    if (customTemplates.isNotEmpty()) {
                        Text(
                            text = "📌 SEUS TEMPLATES SALVOS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            customTemplates.forEachIndexed { index, (name, customCfg) ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = "📁 $name", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                            Text(
                                                text = "${customCfg.ESTRATÉGIA} | ${customCfg.OperationalPeriod} | Lot ${customCfg.lot}",
                                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                            )
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = {
                                                    localConfig = customCfg
                                                    templateMessage = "Template '$name' aplicado!"
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("📥 Usar", style = MaterialTheme.typography.labelSmall)
                                            }

                                            IconButton(
                                                onClick = {
                                                    customTemplates = customTemplates.filterIndexed { i, _ -> i != index }
                                                    templateMessage = "Template '$name' removido."
                                                }
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Excluir", tint = Color(0xFFEF4444))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (templateMessage.isNotEmpty()) {
                        Surface(
                            color = Color(0xFF0369A1).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Text(
                                text = templateMessage,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Autenticação
        item(key = "ea_auth_card") {
            CategoryHeaderCard(
                title = "1. Autenticação & Expiração",
                description = "Configurações de segurança e licença do robô",
                icon = Icons.Default.Lock,
                isExpanded = expAuth,
                onToggle = { expAuth = !expAuth }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🔒 Autenticação Obrigatória do Robô (Leitura Sempre Ativa no EA)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                            )
                        }
                    }

                    OutlinedTextField(
                        value = config.SENHA,
                        onValueChange = { localConfig = config.copy(SENHA = it) },
                        label = { Text("SENHA DO ROBÔ 🔒 (OBRIGATÓRIA)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        // Section 2: Cores
        item(key = "ea_color_card") {
            CategoryHeaderCard(
                title = "2. Esquema de Cores",
                description = "Cores de exibição de canais e linhas no gráfico",
                icon = Icons.Default.Palette,
                isExpanded = expColor,
                onToggle = { expColor = !expColor }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Interruptor de Leitura da Janela de Cores no EA
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Cores no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_ESQUEMA_CORES,
                            onCheckedChange = { localConfig = config.copy(LER_ESQUEMA_CORES = it) }
                        )
                    }

                    // Enum Preset de Temas de Cores
                    Text(
                        text = "TEMA / ENUM DE CORES DO EA",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    )

                    val colorOptions = listOf(
                        "CYAN_NEON" to "Cyan Neon 🩵",
                        "DARK_MATRIX" to "Dark Matrix 🟢",
                        "GOLDEN_PRO" to "Golden Pro 🟡",
                        "PURPLE_NIGHT" to "Purple Night 💜",
                        "CLASSIC_BLUE" to "Classic Blue 💙",
                        "CUSTOM" to "Personalizado 🎨"
                    )

                    var expandedColorEnum by remember { mutableStateOf(false) }
                    val currentLabel = colorOptions.find { it.first == config.ESQUEMA_CORES_ENUM }?.second ?: "Cyan Neon 🩵"

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expandedColorEnum = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enum selecionado: $currentLabel",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Selecionar Enum",
                                    tint = Color(0xFF38BDF8)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expandedColorEnum,
                            onDismissRequest = { expandedColorEnum = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            colorOptions.forEach { (enumVal, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (config.ESQUEMA_CORES_ENUM == enumVal) Color(0xFF38BDF8) else Color.White,
                                                fontWeight = if (config.ESQUEMA_CORES_ENUM == enumVal) FontWeight.Bold else FontWeight.Normal
                                            )
                                        )
                                    },
                                    onClick = {
                                        expandedColorEnum = false
                                        localConfig = when (enumVal) {
                                            "CYAN_NEON" -> config.copy(
                                                ESQUEMA_CORES_ENUM = "CYAN_NEON",
                                                cor_de_canal = "#22D3EE",
                                                cor_de_linhas = "#FF00E5",
                                                corr_de_equador = "#FFFF00"
                                            )
                                            "DARK_MATRIX" -> config.copy(
                                                ESQUEMA_CORES_ENUM = "DARK_MATRIX",
                                                cor_de_canal = "#00FF66",
                                                cor_de_linhas = "#008000",
                                                corr_de_equador = "#00FFCC"
                                            )
                                            "GOLDEN_PRO" -> config.copy(
                                                ESQUEMA_CORES_ENUM = "GOLDEN_PRO",
                                                cor_de_canal = "#FFD700",
                                                cor_de_linhas = "#FF8C00",
                                                corr_de_equador = "#FFFFFF"
                                            )
                                            "PURPLE_NIGHT" -> config.copy(
                                                ESQUEMA_CORES_ENUM = "PURPLE_NIGHT",
                                                cor_de_canal = "#A855F7",
                                                cor_de_linhas = "#EC4899",
                                                corr_de_equador = "#38BDF8"
                                            )
                                            "CLASSIC_BLUE" -> config.copy(
                                                ESQUEMA_CORES_ENUM = "CLASSIC_BLUE",
                                                cor_de_canal = "#3B82F6",
                                                cor_de_linhas = "#1D4ED8",
                                                corr_de_equador = "#60A5FA"
                                            )
                                            else -> config.copy(ESQUEMA_CORES_ENUM = "CUSTOM")
                                        }
                                    }
                                )
                            }
                        }
                    }

                    ColorInputField(
                        label = "Cor do Primeiro Canal (cor_de_canal)",
                        value = config.cor_de_canal,
                        onValueChange = { localConfig = config.copy(cor_de_canal = it, ESQUEMA_CORES_ENUM = "CUSTOM") }
                    )
                    
                    ColorInputField(
                        label = "Cor dos Canais (cor_de_linhas)",
                        value = config.cor_de_linhas,
                        onValueChange = { localConfig = config.copy(cor_de_linhas = it, ESQUEMA_CORES_ENUM = "CUSTOM") }
                    )
                    
                    ColorInputField(
                        label = "Cor do Equador (corr_de_equador)",
                        value = config.corr_de_equador,
                        onValueChange = { localConfig = config.copy(corr_de_equador = it, ESQUEMA_CORES_ENUM = "CUSTOM") }
                    )
                }
            }
        }

        // Section 3: Tendência
        item(key = "ea_trend_card") {
            CategoryHeaderCard(
                title = "3. Canais de Tendência",
                description = "Margens de tendência alta/baixa e equador",
                icon = Icons.Default.TrendingUp,
                isExpanded = expTrend,
                onToggle = { expTrend = !expTrend }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Tendência no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_CANAIS_TENDENCIA,
                            onCheckedChange = { localConfig = config.copy(LER_CANAIS_TENDENCIA = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Exibir Linhas de Equador",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.LINHAS_DE_EQUADOR,
                            onCheckedChange = { localConfig = config.copy(LINHAS_DE_EQUADOR = it) }
                        )
                    }

                    EnumDropdownField(
                        label = "Tendência de Entrada (enum tendencia)",
                        currentValue = config.TREND,
                        options = listOf(
                            "TENDENCIA_DE_ALTA" to "🟢 TENDENCIA_DE_ALTA",
                            "TENDENCIA_DE_BAIXA" to "🔴 TENDENCIA_DE_BAIXA"
                        ),
                        onValueChange = { localConfig = config.copy(TREND = it) }
                    )

                    DoubleOutlinedTextField(
                        value = config.M_equador_alta,
                        onValueChange = { localConfig = config.copy(M_equador_alta = it) },
                        label = "Linha de Equador Máxima"
                    )

                    DoubleOutlinedTextField(
                        value = config.M_equador_baixa,
                        onValueChange = { localConfig = config.copy(M_equador_baixa = it) },
                        label = "Linha de Equador Mínima"
                    )
                }
            }
        }

        // Section 4: Estratégia
        item(key = "ea_strategy_card") {
            CategoryHeaderCard(
                title = "4. Estratégia Principal",
                description = "Indicadores, lotes e períodos operacionais",
                icon = Icons.Default.Build,
                isExpanded = expStrategy,
                onToggle = { expStrategy = !expStrategy }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Estratégia no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_ESTRATEGIA_PRINCIPAL,
                            onCheckedChange = { localConfig = config.copy(LER_ESTRATEGIA_PRINCIPAL = it) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tema MA 9 / 21",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.TEMA,
                            onCheckedChange = { localConfig = config.copy(TEMA = it) }
                        )
                    }

                    EnumDropdownField(
                        label = "Estratégia de Entrada (enum Estrategia)",
                        currentValue = config.ESTRATÉGIA,
                        options = listOf(
                            "FIMATHE" to "⚡ FIMATHE",
                            "F_SURFADA" to "🌊 F_SURFADA"
                        ),
                        onValueChange = { localConfig = config.copy(ESTRATÉGIA = it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Virada de Jogo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.virada_de_jogo,
                            onCheckedChange = { localConfig = config.copy(virada_de_jogo = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.Nives,
                        onValueChange = { localConfig = config.copy(Nives = it) },
                        label = "Quantos Níveis"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Costurada",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.Costurar,
                            onCheckedChange = { localConfig = config.copy(Costurar = it) }
                        )
                    }

                    EnumDropdownField(
                        label = "Período Operacional",
                        currentValue = config.OperationalPeriod,
                        options = listOf(
                            "PERIOD_M1" to "M1 (1 Minuto)",
                            "PERIOD_M5" to "M5 (5 Minutos)",
                            "PERIOD_M15" to "M15 (15 Minutos)",
                            "PERIOD_M30" to "M30 (30 Minutos)",
                            "PERIOD_H1" to "H1 (1 Hora)",
                            "PERIOD_H4" to "H4 (4 Horas)",
                            "PERIOD_D1" to "D1 (Diário)"
                        ),
                        onValueChange = { localConfig = config.copy(OperationalPeriod = it) }
                    )

                    DoubleOutlinedTextField(
                        value = config.lot,
                        onValueChange = { localConfig = config.copy(lot = it) },
                        label = "Lote de Entrada"
                    )
                }
            }
        }

        // Section 5: Automático
        item(key = "ea_auto_card") {
            CategoryHeaderCard(
                title = "5. Automação & Sessões",
                description = "Horários de mercado e auto expansão",
                icon = Icons.Default.PlayArrow,
                isExpanded = expAuto,
                onToggle = { expAuto = !expAuto }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Automação & Sessões no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_AUTOMACAO_SESSOES,
                            onCheckedChange = { localConfig = config.copy(LER_AUTOMACAO_SESSOES = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Auto Trading",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.EA_AUTO,
                            onCheckedChange = { localConfig = config.copy(EA_AUTO = it) }
                        )
                    }

                    EnumDropdownField(
                        label = "Período Automático (enum AUTO_PERIODO)",
                        currentValue = config.AUTO_PERIOD,
                        options = listOf(
                            "MANUAL" to "⚙️ MANUAL",
                            "SESSOES" to "🌐 SESSOES",
                            "SEMANAL" to "📅 SEMANAL",
                            "DIARIO" to "📆 DIARIO",
                            "HORAS_8" to "⏱️ HORAS_8",
                            "HORA_1" to "⌛ HORA_1"
                        ),
                        onValueChange = { localConfig = config.copy(AUTO_PERIOD = it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto Surfada PCM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.AUTO_SURFADA,
                            onCheckedChange = { localConfig = config.copy(AUTO_SURFADA = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sessão Ásia-Tóquio",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.SESSAO_ASIA_TOQUIO,
                            onCheckedChange = { localConfig = config.copy(SESSAO_ASIA_TOQUIO = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sessão Londres",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.SESSAO_LONDRES,
                            onCheckedChange = { localConfig = config.copy(SESSAO_LONDRES = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sessão Nova Iorque",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.SESSAO_NOVA_YORQUI,
                            onCheckedChange = { localConfig = config.copy(SESSAO_NOVA_YORQUI = it) }
                        )
                    }

                    IntOutlinedTextField(
                        value = config.EXPANSAO_MINIMA,
                        onValueChange = { localConfig = config.copy(EXPANSAO_MINIMA = it) },
                        label = "Auto Expansão Mínima"
                    )

                    IntOutlinedTextField(
                        value = config.EXPANSAO_MAXIMA,
                        onValueChange = { localConfig = config.copy(EXPANSAO_MAXIMA = it) },
                        label = "Auto Expansão Máxima"
                    )
                }
            }
        }

        // Section 6: Posicionamento
        item(key = "ea_order_card") {
            CategoryHeaderCard(
                title = "6. Posicionamento de Ordem",
                description = "Preços limites e margens de Take Profit",
                icon = Icons.Default.List,
                isExpanded = expOrder,
                onToggle = { expOrder = !expOrder }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Posicionamento no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_POSICIONAMENTO_ORDEM,
                            onCheckedChange = { localConfig = config.copy(LER_POSICIONAMENTO_ORDEM = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.compra,
                        onValueChange = { localConfig = config.copy(compra = it) },
                        label = "Preço de Compra"
                    )

                    DoubleOutlinedTextField(
                        value = config.venda,
                        onValueChange = { localConfig = config.copy(venda = it) },
                        label = "Preço de Venda"
                    )

                    DoubleOutlinedTextField(
                        value = config.santo,
                        onValueChange = { localConfig = config.copy(santo = it) },
                        label = "Pontos Fora da Caixa & Santo"
                    )

                    IntOutlinedTextField(
                        value = config.dedo,
                        onValueChange = { localConfig = config.copy(dedo = it) },
                        label = "Pontos para Abertura (Dedo)"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Posicionamento de Take",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.posicaoTake,
                            onCheckedChange = { localConfig = config.copy(posicaoTake = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.buy_take,
                        onValueChange = { localConfig = config.copy(buy_take = it) },
                        label = "Take para Compra"
                    )

                    DoubleOutlinedTextField(
                        value = config.sell_take,
                        onValueChange = { localConfig = config.copy(sell_take = it) },
                        label = "Take para Venda"
                    )
                }
            }
        }

        // Section 7: Capital
        item(key = "ea_capital_card") {
            CategoryHeaderCard(
                title = "7. Gestão de Capital & Risco",
                description = "Saldos e limites diários/semanais de perdas e ganhos",
                icon = Icons.Default.AttachMoney,
                isExpanded = expCapital,
                onToggle = { expCapital = !expCapital }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Gestão de Risco no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_GESTAO_RISCO,
                            onCheckedChange = { localConfig = config.copy(LER_GESTAO_RISCO = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.SALDO,
                        onValueChange = { localConfig = config.copy(SALDO = it) },
                        label = "Saldo Simulador/Demo"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Risco Diário Ativo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.GERENCIAMENTO_DE_RISCO_DIARIO,
                            onCheckedChange = { localConfig = config.copy(GERENCIAMENTO_DE_RISCO_DIARIO = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.porcentos,
                        onValueChange = { localConfig = config.copy(porcentos = it) },
                        label = "Limite Perda Diária (%)"
                    )

                    DoubleOutlinedTextField(
                        value = config.poercentosg,
                        onValueChange = { localConfig = config.copy(poercentosg = it) },
                        label = "Limite Ganho Diário (%)"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Risco Semanal Ativo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.GERENCIAMENTO_DE_RISCO_SEMANAL,
                            onCheckedChange = { localConfig = config.copy(GERENCIAMENTO_DE_RISCO_SEMANAL = it) }
                        )
                    }

                    DoubleOutlinedTextField(
                        value = config.PORCENTOO,
                        onValueChange = { localConfig = config.copy(PORCENTOO = it) },
                        label = "Limite Perda Semanal (%)"
                    )

                    DoubleOutlinedTextField(
                        value = config.PORCENTOSS,
                        onValueChange = { localConfig = config.copy(PORCENTOSS = it) },
                        label = "Limite Ganho Semanal (%)"
                    )
                }
            }
        }

        // Section 8: Filtros Operacionais
        item(key = "ea_ops_card") {
            CategoryHeaderCard(
                title = "8. Parâmetros Operacionais",
                description = "Notificações, e-mails e condições de rompimento",
                icon = Icons.Default.Notifications,
                isExpanded = expOps,
                onToggle = { expOps = !expOps }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Notificações/Ops no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_RESULTADOS_NOTIFICACOES,
                            onCheckedChange = { localConfig = config.copy(LER_RESULTADOS_NOTIFICACOES = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar E-Mail (GMAIL)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.GMAIL,
                            onCheckedChange = { localConfig = config.copy(GMAIL = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Notificações",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.notific,
                            onCheckedChange = { localConfig = config.copy(notific = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Permitir Ordens de Venda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.ativar_ou_desativar_venda,
                            onCheckedChange = { localConfig = config.copy(ativar_ou_desativar_venda = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Permitir Ordens de Compra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.ativar_ou_desativar_compra,
                            onCheckedChange = { localConfig = config.copy(ativar_ou_desativar_compra = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Modificar SL para 0.0 (Modify_Sl_For_OxO)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.Modify_Sl_For_OxO,
                            onCheckedChange = { localConfig = config.copy(Modify_Sl_For_OxO = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rompimento de Compra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.condicao_De_rompimento_c,
                            onCheckedChange = { localConfig = config.copy(condicao_De_rompimento_c = it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Rompimento de Venda",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Switch(
                            checked = config.condicao_De_rompimento_v,
                            onCheckedChange = { localConfig = config.copy(condicao_De_rompimento_v = it) }
                        )
                    }
                }
            }
        }

        // Section 9: Resultado & Câmbio
        item(key = "ea_result_card") {
            CategoryHeaderCard(
                title = "9. Resultado & Câmbio",
                description = "Moedas locais e taxa de câmbio USD",
                icon = Icons.Default.Refresh,
                isExpanded = expResult,
                onToggle = { expResult = !expResult }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Habilitar Leitura de Câmbio/Painel no EA",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        )
                        Switch(
                            checked = config.LER_PAINEL_CAMBIO,
                            onCheckedChange = { localConfig = config.copy(LER_PAINEL_CAMBIO = it) }
                        )
                    }

                    OutlinedTextField(
                        value = config.mony,
                        onValueChange = { localConfig = config.copy(mony = it) },
                        label = { Text("Moeda de Exibição (mony)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    DoubleOutlinedTextField(
                        value = config.CAMBIO,
                        onValueChange = { localConfig = config.copy(CAMBIO = it) },
                        label = "Taxa de Câmbio USD (CAMBIO)"
                    )
                }
            }
        }

        // Section 10: Botão de Sincronização Principal com Interruptor de Segurança
        item(key = "ea_save_actions") {
            val isSynced = if (dataSourceMode == "FIREBASE") true else (userProfile?.githubToken?.isNotBlank() == true && userProfile?.githubRepo?.isNotBlank() == true)
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Barra de Sincronização com Interruptor de Segurança ao lado do Botão
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão Principal de Sincronização
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.saveEaConfig(config)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .border(
                                1.dp,
                                if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF22D3EE) else Color(0xFF64748B),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF22D3EE) else Color(0xFF334155),
                            contentColor = if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF0A0F1E) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Sincronizar",
                            tint = if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF0A0F1E) else Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SALVAR E SINCRONIZAR EA",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    // Interruptor de Segurança ao lado do Botão
                    Surface(
                        modifier = Modifier.height(56.dp),
                        color = if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF0369A1).copy(alpha = 0.3f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF38BDF8) else Color(0xFF475569)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (config.PERMITIR_LEITURA_PARAMETROS) "🔒 EA LÊ" else "🔒 EA BLOQ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (config.PERMITIR_LEITURA_PARAMETROS) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                    )
                                )
                                Switch(
                                    checked = config.PERMITIR_LEITURA_PARAMETROS,
                                    onCheckedChange = { localConfig = config.copy(PERMITIR_LEITURA_PARAMETROS = it) },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }
                        }
                    }
                }

                if (config.PERMITIR_LEITURA_PARAMETROS) {
                    Text(
                        text = "🟢 LEITURA DE PARÂMETROS AUTORIZADA: O robô EA lerá os parâmetros ao sincronizar e DESATIVARÁ este interruptor automaticamente após ler (1 leitura por envio).",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    Text(
                        text = "🔒 LEITURA CONCLUÍDA / DESATIVADO: O robô EA não lerá novos parâmetros. Ligue o interruptor ao lado sempre que quiser enviar e autorizar uma nova leitura pelo EA.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                if (dataSourceMode == "FIREBASE") {
                    Text(
                        text = "💡 Os parâmetros do seu robô MT5 estão sendo sincronizados automaticamente em nuvem com o servidor.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else if (!isSynced) {
                    Text(
                        text = "💡 Para ativar a sincronização em nuvem automática do arquivo .set para o seu robô MT5 no GitHub, configure o Token e Repositório no Painel de Ações.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }

        item(key = "ea_bottom_spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun CategoryHeaderCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isExpanded) Color(0xFF22D3EE).copy(alpha = 0.3f) else Color(0xFF334155).copy(alpha = 0.2f),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) Color(0xFF1E293B) else Color(0xFF0F172A)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF22D3EE).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8)
                            )
                        )
                    }
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = Color(0xFF64748B)
                )
            }
            
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Divider(color = Color(0xFF334155).copy(alpha = 0.4f))
                    content()
                }
            }
        }
    }
}

@Composable
fun ColorInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text("#FFFFFF") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )
        
        val parsedColor = remember(value) {
            try {
                Color(android.graphics.Color.parseColor(value.trim()))
            } catch (e: Exception) {
                Color.Transparent
            }
        }
        
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (parsedColor != Color.Transparent) parsedColor else Color(0xFF334155))
                .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (parsedColor == Color.Transparent) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Cor inválida",
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: PortalViewModel,
    adminConfig: com.example.data.GitHubAdminConfig,
    loginLoading: Boolean,
    onOpenAdminConfig: () -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val feedbackMessage by viewModel.messageState.collectAsStateWithLifecycle()
    val dataSourceMode by viewModel.dataSourceMode.collectAsStateWithLifecycle()
    var lastLoginError by remember { mutableStateOf<String?>(null) }
    var showHelpSection by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null && (
            feedbackMessage!!.contains("Erro", ignoreCase = true) ||
            feedbackMessage!!.contains("incorrect", ignoreCase = true) ||
            feedbackMessage!!.contains("incorreta", ignoreCase = true) ||
            feedbackMessage!!.contains("não encontrado", ignoreCase = true) ||
            feedbackMessage!!.contains("preencha", ignoreCase = true)
        )) {
            lastLoginError = feedbackMessage
        }
    }

    LaunchedEffect(phoneInput, passwordInput) {
        lastLoginError = null
    }

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF020617)  // Slate 950
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Elegant Icon / App name
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF22D3EE).copy(alpha = 0.1f))
                    .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Fimaster Logo",
                    tint = Color(0xFF22D3EE),
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PORTAL FIMASTER",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = Color.White
                )
            )

            Text(
                text = "Autoatendimento e Configuração de EAs",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Acesse sua Conta",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { input ->
                            // Allow phone numbers, MT5 account IDs or User IDs (e.g. USR000001, usuario_1)
                            if (input.length <= 35) {
                                phoneInput = input
                            }
                        },
                        label = { Text("Telefone / Conta MT5 / ID de Usuário") },
                        placeholder = { Text("Ex: 841234567, USR000001, 859423") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Identificador de Usuário",
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Senha") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Senha",
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isPasswordVisible) "Ocultar senha" else "Mostrar senha",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (lastLoginError != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Erro de Acesso",
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Falha no Login",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                    Text(
                                        text = lastLoginError ?: "",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFFCA5A5)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.login(phoneInput, passwordInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !loginLoading
                    ) {
                        if (loginLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFF0F172A),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Entrar",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                onClick = { showHelpSection = !showHelpSection },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF22D3EE))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (showHelpSection) Icons.Default.Help else Icons.Default.HelpOutline,
                        contentDescription = "Ajuda",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (showHelpSection) "Ocultar Instruções de Login" else "Não consegue entrar? Ver ajuda",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            AnimatedVisibility(visible = showHelpSection) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .border(1.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Guia de Resolução de Problemas",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        Text(
                            text = "Se encontrou um erro ao tentar entrar no portal, verifique os pontos abaixo:",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )

                        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "Telefone",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Formato do Telefone",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "Insira apenas os 9 dígitos locais do seu número (ex: 841234567). O código de país (+258) não deve ser incluído.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Internet",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Conexão com a Internet",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = if (dataSourceMode == "FIREBASE") {
                                        "Os dados são sincronizados em tempo real com o servidor de nuvem. Certifique-se de que tem uma ligação estável à internet."
                                    } else {
                                        "Os dados são sincronizados em tempo real com o banco de dados do GitHub. Certifique-se de que tem uma ligação estável à internet."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configurações",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Configuração do Servidor",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = if (dataSourceMode == "FIREBASE") {
                                        "Se houver um erro de sincronização com o banco, clique no ícone de engrenagem no canto superior direito para validar as URLs e credenciais do servidor."
                                    } else {
                                        "Se houver um erro de sincronização com o banco, clique no ícone de engrenagem no canto superior direito para validar as chaves e caminhos do GitHub."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Dúvidas",
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Esqueceu-se da Senha?",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "Se a sua licença estiver ativa mas não conseguir aceder ou se esqueceu da senha, contacte o suporte oficial Fimaster para repor o seu acesso.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Gear Button top-right (Drawn last to be on top of all other views and touchable)
        IconButton(
            onClick = onOpenAdminConfig,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Configurar Admin",
                tint = Color(0xFF22D3EE),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun Mt5ManageScreen(
    loggedUser: com.example.data.GithubUser?,
    onSaveMt5Id: (String) -> Unit
) {
    var mt5IdInput by remember { mutableStateOf(loggedUser?.mt5IdConta ?: "") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(loggedUser) {
        if (loggedUser != null && mt5IdInput.isEmpty()) {
            mt5IdInput = loggedUser.mt5IdConta
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Conta MetaTrader 5",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Text(
                text = "Gerencie a vinculação do seu robô com sua conta operacional.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF22D3EE).copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Configuração MT5",
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Vincular Conta MT5",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    Text(
                        text = "Vincule o robô EA MT5 ao seu ID de conta correto para sincronização de lucros e validação da licença ativa.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF94A3B8)
                    )

                    OutlinedTextField(
                        value = mt5IdInput,
                        onValueChange = { mt5IdInput = it },
                        label = { Text("ID da Conta MetaTrader 5 (MT5)") },
                        placeholder = { Text("Ex: 12345678") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onSaveMt5Id(mt5IdInput)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Salvar ID da Conta",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(loggedUser: com.example.data.GithubUser?) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Histórico de Licenças",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Text(
                text = "Acompanhe todos os seus pagamentos, renovações e alterações.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )
        }

        val historico = loggedUser?.licencaHistorico ?: emptyList()

        if (historico.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Sem Histórico",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Nenhum histórico encontrado",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            }
        } else {
            items(historico) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Color(0xFF334155).copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.data,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            
                            val formattedVal = String.format("%,.2f", item.valor) + " MT"
                            Text(
                                text = formattedVal,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color(0xFF22D3EE),
                                    fontWeight = FontWeight.Black
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))

                        Text(
                            text = item.descricao,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityScreen(
    loggedUser: com.example.data.GithubUser?,
    onUpdatePassword: (String, String, String) -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Segurança de Acesso",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            )
            Text(
                text = "Atualize sua senha de acesso ao portal para garantir sua privacidade.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFF94A3B8)
                )
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF22D3EE).copy(alpha = 0.15f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Segurança",
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Alterar Senha",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }

                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Senha Atual") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Mostrar senha",
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Nova Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirmar Nova Senha") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onUpdatePassword(currentPassword, newPassword, confirmPassword)
                            currentPassword = ""
                            newPassword = ""
                            confirmPassword = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Atualizar Senha",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }
        }

        item {
            val clipboardManager = LocalClipboardManager.current
            var rulesCopied by remember { mutableStateOf(false) }
            var showRulesJson by remember { mutableStateOf(false) }

            val rulesJson = """{
  "rules": {
    "dados": {
      "usuarios": {
        ".read": "true",
        "${'$'}userId": {
          ".read": "true",
          ".write": "true"
        }
      },
      "indices": {
        ".read": "true",
        ".write": "true"
      },
      "parametros": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "status": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "eventos": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "versao": {
        ".read": "true",
        ".write": "true"
      }
    }
  }
}"""

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color(0xFF10B981).copy(alpha = 0.25f),
                        RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Regras Firebase",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Regras do Firebase RTDB",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    Text(
                        text = "Políticas de segurança ativas no Firebase Realtime Database para proteger os dados de cada cliente e manter o acesso total do Administrador.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "• Admin (PyzB5d...): Escrita total em todos os nós.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "• Clientes: Apenas o seu próprio nó dados/usuarios/\$userId.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                        )
                        Text(
                            text = "• MT5 Robo: Permissão de atualização nos nós status e eventos.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRulesJson = !showRulesJson },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = if (showRulesJson) "Ocultar JSON" else "Ver JSON",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }

                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(rulesJson))
                                rulesCopied = true
                            },
                            modifier = Modifier.weight(1.2f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = if (rulesCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copiar",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (rulesCopied) "Copiado!" else "Copiar JSON",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            )
                        }
                    }

                    if (showRulesJson) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            color = Color(0xFF090D16),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = rulesJson,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConfigDialog(
    config: com.example.data.GitHubAdminConfig,
    currentMode: String,
    currentFirebaseUrl: String,
    onDismiss: () -> Unit,
    onSave: (com.example.data.GitHubAdminConfig, String, String) -> Unit
) {
    var tokenInput by remember { mutableStateOf(config.token) }
    var repositoryInput by remember { mutableStateOf(config.repository) }
    var branchInput by remember { mutableStateOf(config.branch) }
    var pathInput by remember { mutableStateOf(config.path) }
    var selectedMode by remember { mutableStateOf(currentMode) }
    var firebaseUrlInput by remember { mutableStateOf(currentFirebaseUrl) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Fonte de Dados Admin",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                )

                Text(
                    text = "Escolha o serviço que será usado para consultar e salvar as informações dos utilizadores.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )

                // Segmented selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMode == "GITHUB") Color(0xFF1E293B) else Color.Transparent)
                            .border(
                                width = if (selectedMode == "GITHUB") 1.dp else 0.dp,
                                color = if (selectedMode == "GITHUB") Color(0xFF22D3EE) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = "GITHUB" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "GitHub REST",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == "GITHUB") Color(0xFF22D3EE) else Color(0xFF94A3B8)
                            )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMode == "FIREBASE") Color(0xFF1E293B) else Color.Transparent)
                            .border(
                                width = if (selectedMode == "FIREBASE") 1.dp else 0.dp,
                                color = if (selectedMode == "FIREBASE") Color(0xFF22D3EE) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = "FIREBASE" }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Firebase RTDB",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = if (selectedMode == "FIREBASE") Color(0xFF22D3EE) else Color(0xFF94A3B8)
                            )
                        )
                    }
                }

                // Dynamic Input Fields based on Selected Mode
                if (selectedMode == "GITHUB") {
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("GitHub Personal Access Token") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "Token",
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = repositoryInput,
                        onValueChange = { repositoryInput = it },
                        label = { Text("Repositório GitHub (ex: Macucul/fimaster)") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Repositorio",
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = branchInput,
                            onValueChange = { branchInput = it },
                            label = { Text("Branch") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF22D3EE),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFF22D3EE),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            label = { Text("Caminho pasta") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF22D3EE),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = Color(0xFF22D3EE),
                                unfocusedLabelColor = Color(0xFF94A3B8),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = firebaseUrlInput,
                        onValueChange = { firebaseUrlInput = it },
                        label = { Text("Firebase Realtime Database URL") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Firebase URL",
                                tint = Color(0xFF94A3B8)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF22D3EE),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF22D3EE),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Info banner based on chosen mode
                if (selectedMode == "GITHUB") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Configurado",
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "O sistema sincronizará os dados através das APIs REST do GitHub.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Configurado",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "O aplicativo utilizará o Firebase RTDB com regras de segurança por UID de cliente e Admin.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val clipboardManager = LocalClipboardManager.current
                    var rulesCopiedInDialog by remember { mutableStateOf(false) }

                    val dialogRulesJson = """{
  "rules": {
    "dados": {
      "usuarios": {
        ".read": "true",
        "${'$'}userId": {
          ".read": "true",
          ".write": "true"
        }
      },
      "indices": {
        ".read": "true",
        ".write": "true"
      },
      "parametros": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "status": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "eventos": {
        ".read": "true",
        "${'$'}mt5Id": {
          ".write": "true"
        }
      },
      "versao": {
        ".read": "true",
        ".write": "true"
      }
    }
  }
}"""

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(dialogRulesJson))
                            rulesCopiedInDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = if (rulesCopiedInDialog) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copiar Regras",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (rulesCopiedInDialog) "Regras Firebase Copiadas!" else "Copiar Regras de Segurança Firebase (JSON)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        )
                    }
                }

                // Action buttons row (Always visible at bottom)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            onSave(
                                com.example.data.GitHubAdminConfig(
                                    token = tokenInput.trim(),
                                    repository = repositoryInput.trim(),
                                    branch = branchInput.trim(),
                                    path = pathInput.trim()
                                ),
                                selectedMode,
                                firebaseUrlInput.trim()
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Salvar",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    isAccent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color(0xFF94A3B8)
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isAccent) Color(0xFF22D3EE) else Color.White
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnumDropdownField(
    label: String,
    currentValue: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = options.find { it.first == currentValue || it.first.equals(currentValue, ignoreCase = true) }
    val displayLabel = selectedItem?.second ?: currentValue

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF38BDF8),
                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                focusedLabelColor = Color(0xFF38BDF8),
                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(10.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1E293B))
        ) {
            options.forEach { (key, title) ->
                val isSelected = key.equals(currentValue, ignoreCase = true)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = title,
                            color = if (isSelected) Color(0xFF38BDF8) else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onValueChange(key)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@Composable
fun DoubleOutlinedTextField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = ""
) {
    fun formatDouble(d: Double): String {
        return if (d % 1.0 == 0.0) {
            d.toLong().toString()
        } else {
            d.toString()
        }
    }

    var isFocused by remember { mutableStateOf(false) }
    var textState by remember { mutableStateOf(formatDouble(value)) }
    var lastExternalValue by remember { mutableStateOf(value) }

    if (!isFocused && value != lastExternalValue) {
        lastExternalValue = value
        textState = formatDouble(value)
    }

    OutlinedTextField(
        value = textState,
        onValueChange = { input ->
            val normalized = input.replace(',', '.')
            textState = normalized

            val parsed = normalized.toDoubleOrNull()
            if (parsed != null) {
                lastExternalValue = parsed
                onValueChange(parsed)
            } else if (normalized.isEmpty()) {
                lastExternalValue = 0.0
                onValueChange(0.0)
            }
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            if (!focusState.isFocused) {
                val parsed = textState.replace(',', '.').toDoubleOrNull()
                if (parsed != null) {
                    textState = formatDouble(parsed)
                } else if (textState.isEmpty()) {
                    textState = formatDouble(value)
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
}

@Composable
fun IntOutlinedTextField(
    value: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }
    var textState by remember { mutableStateOf(value.toString()) }
    var lastExternalValue by remember { mutableStateOf(value) }

    if (!isFocused && value != lastExternalValue) {
        lastExternalValue = value
        textState = value.toString()
    }

    OutlinedTextField(
        value = textState,
        onValueChange = { input ->
            textState = input

            val parsed = input.toIntOrNull()
            if (parsed != null) {
                lastExternalValue = parsed
                onValueChange(parsed)
            } else if (input.isEmpty()) {
                lastExternalValue = 0
                onValueChange(0)
            }
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotBlank()) { { Text(placeholder) } } else null,
        modifier = modifier.onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            if (!focusState.isFocused) {
                val parsed = textState.toIntOrNull()
                if (parsed != null) {
                    textState = parsed.toString()
                } else if (textState.isEmpty()) {
                    textState = value.toString()
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        ),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
}

data class EaTourStep(
    val stepIndex: Int,
    val title: String,
    val subtitle: String,
    val targetTab: PortalTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val highlights: List<Pair<String, String>>,
    val tip: String
)

@Composable
fun EaOnboardingBannerCard(onStartTour: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6))
                ),
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF0284C7), Color(0xFF06B6D4))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Guia Rápido EA MT5 🚀",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                Text(
                    text = "Aprenda a conectar a conta MT5, validar a licença e configurar lote/stop em 5 passos.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onStartTour,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Iniciar Tour",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun EaOnboardingTourDialog(
    onDismiss: () -> Unit,
    onNavigateTab: (PortalTab) -> Unit
) {
    val steps = remember {
        listOf(
            EaTourStep(
                stepIndex = 1,
                title = "1. Conectar Conta MT5",
                subtitle = "Identificação do Robô no Servidor",
                targetTab = PortalTab.DASHBOARD,
                icon = Icons.Default.AccountBalance,
                description = "O robô MetaTrader 5 comunica operações e bancas através do seu número de Conta MT5. Esse ID precisa estar sincronizado com seu perfil no portal.",
                highlights = listOf(
                    "ID da Conta MT5" to "Insira o número exato da sua conta de negociação (ex: 8841209).",
                    "Status do Robô" to "O painel indicará ONLINE assim que o EA for ativado na sua plataforma MT5."
                ),
                tip = "💡 Dica: Na aba 'Conta EA', atualize e salve o ID da conta a qualquer momento."
            ),
            EaTourStep(
                stepIndex = 2,
                title = "2. Verificar Licença Ativa",
                subtitle = "Validação & Vínculo de Segurança",
                targetTab = PortalTab.CLIENT_LICENSE,
                icon = Icons.Default.Verified,
                description = "A licença autoriza as ordens do seu robô nos servidores Fimaster. Verifique os dados para garantir funcionamento sem interrupções.",
                highlights = listOf(
                    "Status da Licença" to "Confirme se o indicador está como 'LICENÇA ATIVA' em verde.",
                    "Dispositivo (UID)" to "Seu aparelho é identificado para evitar duplicidade de acessos.",
                    "Tempo Restante" to "Acompanhe os dias de validade ou solicite renovação/suporte se necessário."
                ),
                tip = "🔒 Dica: Mantenha sua licença em dia para evitar travamento automático de operações no MT5."
            ),
            EaTourStep(
                stepIndex = 3,
                title = "3. Parâmetros Operacionais",
                subtitle = "Lote, Estratégia & Canais de Equador",
                targetTab = PortalTab.EA_CONFIG,
                icon = Icons.Default.Tune,
                description = "Defina como o robô deve negociar na aba Config EA. O formulário aceita números decimais (ex: 0.01 ou 1.0850) de forma natural.",
                highlights = listOf(
                    "Lote de Entrada (lot)" to "Define o volume por ordem. Exemplo: 0.01 para contas micro/cent.",
                    "Linhas de Equador" to "Preços de referência máxima (ex: 1.0850) e mínima (ex: 1.0720).",
                    "Estratégia & Tendência" to "Selecione opções de entrada no menu suspenso ou escolha CUSTOM.",
                    "Virada de Jogo / Costurar" to "Ative funções de recuperação automatizada de posições."
                ),
                tip = "⚙️ Dica: Agora você pode digitar decimais apagando e inserindo pontos/vírgulas sem conflitos!"
            ),
            EaTourStep(
                stepIndex = 4,
                title = "4. Gestão de Risco & Câmbio",
                subtitle = "Trava Diária/Semanal & Conversão de Saldo",
                targetTab = PortalTab.EA_CONFIG,
                icon = Icons.Default.Shield,
                description = "Estabeleça limites percentuais para interromper operações em momentos de volatilidade e proteger sua banca.",
                highlights = listOf(
                    "Limite Perda Diária (%)" to "Exemplo: 1.0%. Encerra o robô se a perda do dia atingir esse percentual.",
                    "Limite Ganho Diário (%)" to "Exemplo: 2.0%. Trava o lucro do dia ao bater a meta estipulada.",
                    "Taxa de Câmbio (CAMBIO)" to "Insira a cotação de conversão (ex: 64.0 MT/USD) para ver o saldo convertido no Dashboard."
                ),
                tip = "🛡️ Dica: A gestão de risco previne chamadas de margem (Margin Call) em dias de notícia forte."
            ),
            EaTourStep(
                stepIndex = 5,
                title = "5. Eventos & Alertas em Áudio",
                subtitle = "Acompanhamento em Tempo Real com Voz (TTS)",
                targetTab = PortalTab.EA_EVENTS,
                icon = Icons.Default.Notifications,
                description = "Receba notificações instantâneas de cada ordem aberta, fechada ou alteração de canal diretamente na aba Eventos EA.",
                highlights = listOf(
                    "Histórico de Eventos" to "Visualize carimbos de data/hora de todas as ações executadas pelo robô.",
                    "Voz Automatizada (TTS)" to "Notificações faladas em português BR/PT sem precisar olhar a tela.",
                    "Filtros por Sessão" to "Acompanhe aberturas de mercado nas sessões de Ásia, Londres e NY."
                ),
                tip = "🔊 Dica: Clique no ícone de alto-falante em qualquer card de evento para ouvir a reprodução sonora!"
            )
        )
    }

    var currentStepIndex by remember { mutableIntStateOf(0) }
    val currentStep = steps[currentStepIndex]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF22D3EE), Color(0xFF0284C7))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOUR GUIADO EA MT5",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF22D3EE),
                                letterSpacing = 1.5.sp
                            )
                        )
                        Text(
                            text = "Passo ${currentStep.stepIndex} de ${steps.size}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Tour",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Linear Progress bar
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF22D3EE),
                    trackColor = Color(0xFF1E293B)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content for Current Step
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Step Card Title Banner
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentStep.icon,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currentStep.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = currentStep.subtitle,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = currentStep.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFE2E8F0),
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Destaques & Parâmetros:",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22D3EE)
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    currentStep.highlights.forEach { (label, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF94A3B8)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Tip Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = currentStep.tip,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Deep-link Action Button to navigate directly to the step's tab
                    OutlinedButton(
                        onClick = {
                            onNavigateTab(currentStep.targetTab)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF22D3EE)),
                        border = BorderStroke(1.dp, Color(0xFF22D3EE)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = currentStep.targetTab.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ir para a tela: ${currentStep.targetTab.label}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Navigation Controls (Anterior / Próximo / Concluir)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Anterior",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Anterior")
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64748B))
                        ) {
                            Text("Pular Tour")
                        }
                    }

                    if (currentStepIndex < steps.size - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Próximo",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Próximo",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Concluir Tour",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopNeonProcessingBar(
    loadingMessage: String?,
    onDismiss: (() -> Unit)? = null
) {
    val displayDescription = loadingMessage?.takeIf { it.isNotBlank() }
        ?: "Sincronizando robô MT5 com o servidor..."

    // Animated Phase for Continuous Right-to-Left Loading Bar (1f down to 0f)
    val infiniteTransition = rememberInfiniteTransition(label = "NeonRTLAnim")
    val rightToLeftPhase by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RTLPhase"
    )

    // Pulsing Glow Alpha (0.35f to 0.95f) for the neon aura
    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseGlow"
    )

    // Vibrant Continuous Neon Colors
    val neonCyan = Color(0xFF00F0FF)
    val neonEmerald = Color(0xFF10B981)
    val neonPurple = Color(0xFFD946EF)
    val neonBlue = Color(0xFF38BDF8)

    // Moving Continuous Neon Gradient Brush for Border
    val neonBorderBrush = Brush.linearGradient(
        colors = listOf(
            neonCyan,
            neonEmerald,
            neonPurple,
            neonBlue,
            neonCyan
        ),
        start = androidx.compose.ui.geometry.Offset(1200f * rightToLeftPhase, 0f),
        end = androidx.compose.ui.geometry.Offset(1200f * rightToLeftPhase - 500f, 250f)
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B132B)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 1.8.dp,
                brush = neonBorderBrush,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Continuous Loading Bar canvas with Layered Neon Glow Beam (Right to Left)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(Color(0xFF0F172A))
            ) {
                val w = size.width
                val beamLength = w * 0.45f
                val headX = (w + beamLength) * rightToLeftPhase - beamLength

                // Layer 1: Outer Neon Glow Halo (wider and pulsing opacity)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            neonCyan.copy(alpha = pulseGlowAlpha * 0.5f),
                            neonEmerald.copy(alpha = pulseGlowAlpha * 0.6f),
                            neonPurple.copy(alpha = pulseGlowAlpha * 0.5f),
                            Color.Transparent
                        ),
                        startX = headX - 15f,
                        endX = headX + beamLength + 15f
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, size.height)
                )

                // Layer 2: Core Bright Neon Beam
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            neonCyan,
                            neonEmerald,
                            neonBlue,
                            Color.Transparent
                        ),
                        startX = headX,
                        endX = headX + beamLength
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 1f),
                    size = androidx.compose.ui.geometry.Size(w, size.height - 2f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing Neon Glow Indicator Dot
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(neonCyan.copy(alpha = pulseGlowAlpha * 0.4f))
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(neonCyan)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PROCESSAMENTO ÚNICO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = neonCyan,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayDescription,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onDismiss != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TraderProcessingOverlay(
    loadingMessage: String?,
    onDismiss: (() -> Unit)? = null
) {
    var selectedVisualMode by remember { mutableIntStateOf(0) } // 0: Candles, 1: Pipeline, 2: Radar
    var showTechDetails by remember { mutableStateOf(false) }
    var isMinimized by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var ttsObj by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val tts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                // Configured
            }
        }
        ttsObj = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val displayDescription = loadingMessage?.takeIf { it.isNotBlank() }
        ?: "Sincronizando robô MT5 com o servidor..."

    if (isMinimized) {
        // Non-blocking Floating Mini Bar at Bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF22D3EE), Color(0xFF10B981))
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFF22D3EE),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PROCESSAMENTO EM 2º PLANO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF22D3EE),
                                    fontSize = 10.sp
                                )
                            )
                            Text(
                                text = displayDescription,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { isMinimized = false },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Expandir Visualizador",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Expandir",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8))
                            )
                        }

                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Full Overlay Modal (with Scroll and Minimize option)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { isMinimized = true },
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
                .clickable(enabled = false) {}
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF22D3EE), Color(0xFF3B82F6), Color(0xFF10B981))
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7).copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PROCESSAMENTO TRADER MT5",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22D3EE),
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE SOCKET • 12ms Latência",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Minimize Button
                        IconButton(
                            onClick = { isMinimized = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimizar",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (onDismiss != null) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Fechar",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Mode Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val tabs = listOf("📊 Candles", "⚡ Ordens EA", "🌐 Radar Forex")
                    tabs.forEachIndexed { idx, label ->
                        val isSelected = selectedVisualMode == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color.Transparent)
                                .clickable { selectedVisualMode = idx }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Financial Interactive Canvas Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF050B14))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    when (selectedVisualMode) {
                        0 -> TraderCandlestickCanvas()
                        1 -> TraderPipelineCanvas()
                        else -> TraderRadarCanvas()
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mini Descrição do Processo (Exact Requested Description Card)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DESCRIÇÃO DO PROCESSAMENTO:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8),
                                    letterSpacing = 1.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = displayDescription,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                lineHeight = 18.sp
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Live Execution Step Log Ticker
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color(0xFF22D3EE),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "• ACK_SOCKET: Transmitindo dados criptografados ao MT5...",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Bottom Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            if (ttsObj != null) {
                                isSpeaking = true
                                ttsObj?.speak(displayDescription, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "process_speech")
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                            contentDescription = "Ouvir Status",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isSpeaking) "Reproduzindo..." else "Ouvir Status",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    TextButton(
                        onClick = { showTechDetails = !showTechDetails },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeveloperMode,
                            contentDescription = "Detalhes",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showTechDetails) "Ocultar Técnico" else "Ver Técnico",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                if (showTechDetails) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Prot: TLS v1.3 • Cipher: AES_256_GCM • Buffer: 2048b • Host: Fimaster EA Cloud",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF64748B),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun TraderCandlestickCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "CandleAnim")
    val animOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CandlePulse"
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        val w = size.width
        val h = size.height
        val candleWidth = w / 9f

        // Grid Lines
        val gridColor = Color(0xFF1E293B)
        for (i in 1..3) {
            val y = h * (i / 4f)
            drawLine(gridColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
        }

        // Animated Candlesticks
        val prices = listOf(
            Triple(0.3f, 0.7f, true),
            Triple(0.5f, 0.35f, false),
            Triple(0.4f, 0.8f, true),
            Triple(0.75f, 0.6f, false),
            Triple(0.55f, 0.85f, true),
            Triple(0.8f, 0.45f, false),
            Triple(0.4f + (animOffset * 0.3f), 0.9f, true)
        )

        val path = androidx.compose.ui.graphics.Path()

        prices.forEachIndexed { i, (openRatio, closeRatio, isGreen) ->
            val cx = (i + 1) * candleWidth
            val openY = h * (1f - openRatio)
            val closeY = h * (1f - closeRatio)
            val highY = minOf(openY, closeY) - 14f
            val lowY = maxOf(openY, closeY) + 14f

            val color = if (isGreen) Color(0xFF10B981) else Color(0xFFEF4444)

            // Wick
            drawLine(color, start = androidx.compose.ui.geometry.Offset(cx, highY), end = androidx.compose.ui.geometry.Offset(cx, lowY), strokeWidth = 2f)

            // Body
            val topBody = minOf(openY, closeY)
            val bodyHeight = maxOf(abs(closeY - openY), 6f)
            drawRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(cx - candleWidth * 0.35f, topBody),
                size = androidx.compose.ui.geometry.Size(candleWidth * 0.7f, bodyHeight)
            )

            // MA line
            if (i == 0) {
                path.moveTo(cx, (openY + closeY) / 2f)
            } else {
                path.lineTo(cx, (openY + closeY) / 2f)
            }
        }

        // Draw Moving Average curve
        drawPath(
            path = path,
            color = Color(0xFF22D3EE),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f)
        )
    }
}

private fun abs(value: Float): Float = if (value < 0) -value else value

@Composable
fun TraderPipelineCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "PipelineAnim")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PipelinePulse"
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val w = size.width
        val h = size.height
        val cy = h / 2f

        val nodes = listOf("PORTAL", "SSL", "EA CLOUD", "MT5")
        val nodeCount = nodes.size
        val step = w / (nodeCount + 1)

        // Draw connecting pipeline
        drawLine(
            color = Color(0xFF1E293B),
            start = androidx.compose.ui.geometry.Offset(step, cy),
            end = androidx.compose.ui.geometry.Offset(step * nodeCount, cy),
            strokeWidth = 4f
        )

        // Animated pulse along pipeline
        val currentPulseX = step + (step * (nodeCount - 1) * pulseProgress)
        drawCircle(
            color = Color(0xFF22D3EE),
            radius = 6f,
            center = androidx.compose.ui.geometry.Offset(currentPulseX, cy)
        )

        // Draw Nodes
        for (i in 1..nodeCount) {
            val cx = step * i
            val isActive = cx <= currentPulseX

            drawCircle(
                color = if (isActive) Color(0xFF0284C7) else Color(0xFF0F172A),
                radius = 16f,
                center = androidx.compose.ui.geometry.Offset(cx, cy)
            )
            drawCircle(
                color = if (isActive) Color(0xFF22D3EE) else Color(0xFF334155),
                radius = 16f,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}

@Composable
fun TraderRadarCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarAnim")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )

    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = minOf(cx, cy) - 10f

        // Radar Circles
        for (rRatio in listOf(0.33f, 0.66f, 1f)) {
            drawCircle(
                color = Color(0xFF1E293B),
                radius = maxR * rRatio,
                center = androidx.compose.ui.geometry.Offset(cx, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )
        }

        // Radar Sweep Line
        val rad = angle * (Math.PI / 180f).toFloat()
        val lx = cx + (maxR * kotlin.math.cos(rad))
        val ly = cy + (maxR * kotlin.math.sin(rad))

        drawLine(
            color = Color(0xFF10B981),
            start = androidx.compose.ui.geometry.Offset(cx, cy),
            end = androidx.compose.ui.geometry.Offset(lx, ly),
            strokeWidth = 2f
        )

        // Simulated trade targets
        drawCircle(
            color = Color(0xFF22D3EE),
            radius = 4f,
            center = androidx.compose.ui.geometry.Offset(cx + maxR * 0.4f, cy - maxR * 0.3f)
        )
        drawCircle(
            color = Color(0xFFEF4444),
            radius = 4f,
            center = androidx.compose.ui.geometry.Offset(cx - maxR * 0.5f, cy + maxR * 0.2f)
        )
    }
}

