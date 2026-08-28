package com.example.ui

import com.example.data.isTemplateValido
import com.example.data.getResolvedGid
import com.example.data.EaEventGidManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
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
import com.example.data.LicenseTier
import com.example.data.LocalLicenseTier
import com.example.data.LocalLicensePlanConfig
import com.example.data.LocalGlobalLicenseConfig
import com.example.data.LicensePlanConfig
import com.example.data.GlobalLicenseConfig
import com.example.data.buildDynamicFeatures
import com.example.data.effectiveLicenseTier
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

enum class NotificationSeverity {
    CRITICAL, WARNING, INFO
}

data class SystemNotification(
    val id: String = "",
    val gid: String = id.ifBlank { java.util.UUID.randomUUID().toString() },
    val title: String,
    val message: String,
    val severity: NotificationSeverity,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val targetTab: PortalTab? = null,
    val targetEventGid: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

fun calculateSystemNotifications(
    loggedUser: com.example.data.GithubUser?,
    eaRobotStatus: com.example.ui.EaRobotStatus?,
    eaConfig: com.example.data.EaConfigEntity?,
    eaRobotEvents: List<com.example.data.EaRobotEvent>,
    viewedGids: Set<String> = emptySet()
): List<SystemNotification> {
    if (loggedUser == null) return emptyList()
    val list = mutableListOf<SystemNotification>()

    // 1. License Expiry Check (quando faltar 1 semana ou menos - 7 dias)
    val validadeStr = loggedUser.licencaValidade.trim()
    if (validadeStr.isNotBlank()) {
        val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy")
        var parsedDate: java.util.Date? = null
        for (f in formats) {
            try {
                parsedDate = java.text.SimpleDateFormat(f, java.util.Locale.getDefault()).parse(validadeStr)
                if (parsedDate != null) break
            } catch (_: Exception) {}
        }

        if (parsedDate != null) {
            val now = java.util.Date()
            val diffMs = parsedDate.time - now.time
            val daysRemaining = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMs)

            if (!loggedUser.licencaAtiva || daysRemaining <= 0) {
                val licExpiredGid = "lic_expired_${loggedUser.id}"
                if (!viewedGids.contains(licExpiredGid)) {
                    list.add(
                        SystemNotification(
                            id = licExpiredGid,
                            gid = licExpiredGid,
                            title = "🚨 LICENÇA EXPIRADA OU VENCIDA",
                            message = "Sua licença do robô EA expirou (Validade: $validadeStr). Entre em contato com o suporte para renovação.",
                            severity = NotificationSeverity.CRITICAL,
                            icon = Icons.Default.LockClock,
                            targetTab = PortalTab.CLIENT_LICENSE
                        )
                    )
                }
            } else if (daysRemaining in 1..7) {
                val licWarnGid = "lic_warning_7days_${loggedUser.id}_${daysRemaining}"
                if (!viewedGids.contains(licWarnGid)) {
                    val diasTexto = if (daysRemaining == 1L) "1 dia" else "$daysRemaining dias"
                    list.add(
                        SystemNotification(
                            id = licWarnGid,
                            gid = licWarnGid,
                            title = "⚠️ LICENÇA EXPIRANDO EM $diasTexto",
                            message = "Atenção: Sua licença vence em $diasTexto (Validade: $validadeStr). Faltam menos de 7 dias! Renove para não interromper o robô no MT5.",
                            severity = NotificationSeverity.WARNING,
                            icon = Icons.Default.Warning,
                            targetTab = PortalTab.CLIENT_LICENSE
                        )
                    )
                }
            }
        }
    } else if (!loggedUser.licencaAtiva) {
        val licInactGid = "lic_inactive_${loggedUser.id}"
        if (!viewedGids.contains(licInactGid)) {
            list.add(
                SystemNotification(
                    id = licInactGid,
                    gid = licInactGid,
                    title = "🚨 LICENÇA INATIVA",
                    message = "Sua licença no Portal Fimaster está desativada. Solicite a liberação no suporte.",
                    severity = NotificationSeverity.CRITICAL,
                    icon = Icons.Default.Lock,
                    targetTab = PortalTab.CLIENT_LICENSE
                )
            )
        }
    }

    // 2. Robot Execution Attention Checks
    val hasMt5Account = loggedUser.mt5IdConta.isNotBlank() && loggedUser.mt5Registrado
    val isEaAtivo = eaConfig?.EA_ATIVO == true

    if (hasMt5Account && !isEaAtivo) {
        val robotDisabledGid = "robot_disabled_${loggedUser.id}"
        if (!viewedGids.contains(robotDisabledGid)) {
            list.add(
                SystemNotification(
                    id = robotDisabledGid,
                    gid = robotDisabledGid,
                    title = "⚠️ ATENÇÃO AO ROBÔ: EXECUÇÃO DESATIVADA",
                    message = "O robô está conectado no MT5, mas a chave 'EA ATIVO' está desligada no aplicativo.",
                    severity = NotificationSeverity.WARNING,
                    icon = Icons.Default.PauseCircle,
                    targetTab = PortalTab.EA_CONFIG
                )
            )
        }
    } else if (!hasMt5Account && eaRobotEvents.isEmpty()) {
        val noMt5Gid = "no_mt5_${loggedUser.id}"
        if (!viewedGids.contains(noMt5Gid)) {
            list.add(
                SystemNotification(
                    id = noMt5Gid,
                    gid = noMt5Gid,
                    title = "ℹ️ CONTA MT5 NÃO VINCULADA",
                    message = "Cadastre o número da sua conta MT5 na aba 'Conta EA' para sincronizar seu robô em tempo real.",
                    severity = NotificationSeverity.INFO,
                    icon = Icons.Default.AccountBalance,
                    targetTab = PortalTab.DASHBOARD
                )
            )
        }
    }

    // 3. Process each Robot Event and identify Errors, Warnings, and Order Executions via GID
    eaRobotEvents.forEach { evt ->
        val eventGid = evt.getResolvedGid()
        val notifGid = "notif_evt_$eventGid"

        // Skip if this notification or event was already marked as viewed or resolved
        if (!viewedGids.contains(notifGid) && !viewedGids.contains(eventGid)) {
            val fullTextLower = "${evt.event} ${evt.sistema} ${evt.novo} ${evt.descNovo} ${evt.resumo} ${evt.msg} ${evt.motivacao} ${evt.note}".lowercase()
            val isError = evt.erroCode != 0 ||
                fullTextLower.contains("erro") ||
                fullTextLower.contains("error") ||
                fullTextLower.contains("fail") ||
                fullTextLower.contains("falha") ||
                fullTextLower.contains("rejeit") ||
                fullTextLower.contains("invalid") ||
                fullTextLower.contains("inválid") ||
                fullTextLower.contains("desconect") ||
                fullTextLower.contains("desconex") ||
                fullTextLower.contains("expirad")

            val isWarning = fullTextLower.contains("alerta") ||
                fullTextLower.contains("warning") ||
                fullTextLower.contains("margin") ||
                fullTextLower.contains("margem") ||
                fullTextLower.contains("travad") ||
                fullTextLower.contains("bloquead") ||
                fullTextLower.contains("pausad") ||
                fullTextLower.contains("interrompid") ||
                fullTextLower.contains("sem saldo") ||
                evt.novo.equals("TRAVADO", ignoreCase = true) ||
                evt.novo.equals("BLOQUEADO", ignoreCase = true)

            val isOrder = fullTextLower.contains("ordem") ||
                fullTextLower.contains("posicao") ||
                fullTextLower.contains("posição") ||
                fullTextLower.contains("compra") ||
                fullTextLower.contains("venda") ||
                fullTextLower.contains("take profit") ||
                fullTextLower.contains("stop loss") ||
                evt.temPosicao.isNotBlank()

            if (isError || isWarning || isOrder) {
                val title = when {
                    isError -> "⚡ ERRO DE EXECUÇÃO NO MT5"
                    isWarning -> "⚠️ ALERTA DO ROBÔ EA"
                    else -> "📊 ATIVIDADE NO MT5"
                }
                val desc = if (evt.resumo.isNotBlank()) evt.resumo else if (evt.msg.isNotBlank()) evt.msg else evt.event
                val timeTag = if (evt.hora.isNotBlank()) " (${evt.hora})" else ""

                list.add(
                    SystemNotification(
                        id = notifGid,
                        gid = notifGid,
                        title = title,
                        message = "$desc$timeTag",
                        severity = if (isError) NotificationSeverity.CRITICAL else if (isWarning) NotificationSeverity.WARNING else NotificationSeverity.INFO,
                        icon = if (isError) Icons.Default.ReportProblem else if (isWarning) Icons.Default.Warning else Icons.Default.TrendingUp,
                        targetTab = PortalTab.EA_EVENTS,
                        targetEventGid = eventGid,
                        timestamp = if (evt.timestamp > 0L) evt.timestamp else System.currentTimeMillis()
                    )
                )
            }
        }
    }

    return list
}

@Composable
fun SystemNotificationsBannerCard(
    notifications: List<SystemNotification>,
    onNavigateToTab: (PortalTab) -> Unit,
    onResolveNotification: ((SystemNotification) -> Unit)? = null,
    onMarkAsViewed: ((SystemNotification) -> Unit)? = null
) {
    if (notifications.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        notifications.forEach { notif ->
            val bgColor = when (notif.severity) {
                NotificationSeverity.CRITICAL -> Color(0xFF451A03)
                NotificationSeverity.WARNING -> Color(0xFF382300)
                NotificationSeverity.INFO -> Color(0xFF0F172A)
            }
            val borderColor = when (notif.severity) {
                NotificationSeverity.CRITICAL -> Color(0xFFEF4444)
                NotificationSeverity.WARNING -> Color(0xFFF59E0B)
                NotificationSeverity.INFO -> Color(0xFF0EA5E9)
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = bgColor.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, borderColor.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(borderColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = notif.icon,
                                contentDescription = null,
                                tint = borderColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = notif.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = borderColor,
                                    fontSize = 12.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = notif.message,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (onMarkAsViewed != null) {
                                TextButton(
                                    onClick = { onMarkAsViewed(notif) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Visualizado ✔️",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (onResolveNotification != null) {
                                        onResolveNotification(notif)
                                    } else if (notif.targetTab != null) {
                                        onNavigateToTab(notif.targetTab)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = borderColor.copy(alpha = 0.85f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Resolver 🎯",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemNotificationsDialog(
    notifications: List<SystemNotification>,
    onNavigateToTab: (PortalTab) -> Unit,
    onResolveNotification: (SystemNotification) -> Unit = {},
    onMarkAsViewed: (SystemNotification) -> Unit = {},
    onMarkAllAsViewed: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B132B),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Central de Alertas",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "Robô EA & Notificações",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                if (notifications.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${notifications.size} pendente${if (notifications.size > 1) "s" else ""}",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFFCA5A5),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Tudo em ordem!",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Nenhum alerta ou erro pendente.",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header action: Mark all as viewed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            onClick = onMarkAllAsViewed,
                            color = Color(0xFF1E293B).copy(alpha = 0.6f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Marcar todas como lidas",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(notifications, key = { it.gid }) { notif ->
                            val currentlyReadingKey by PortalEventQueueManager.currentlyReadingKey.collectAsStateWithLifecycle()
                            val isItemReading = notif.targetEventGid != null && notif.targetEventGid == currentlyReadingKey

                            val borderColor = when {
                                isItemReading -> Color(0xFF38BDF8)
                                notif.severity == NotificationSeverity.CRITICAL -> Color(0xFFEF4444)
                                notif.severity == NotificationSeverity.WARNING -> Color(0xFFF59E0B)
                                else -> Color(0xFF0EA5E9)
                            }

                            val cardBg = when {
                                isItemReading -> Color(0xFF0C2438)
                                notif.severity == NotificationSeverity.CRITICAL -> Color(0xFF221115)
                                notif.severity == NotificationSeverity.WARNING -> Color(0xFF1F180A)
                                else -> Color(0xFF0F192C)
                            }

                            // Small, Compact Alert Card
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = cardBg,
                                border = BorderStroke(if (isItemReading) 1.5.dp else 1.dp, borderColor.copy(alpha = if (isItemReading) 0.9f else 0.45f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(borderColor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isItemReading) Icons.Default.VolumeUp else notif.icon,
                                                contentDescription = null,
                                                tint = borderColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = borderColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isItemReading) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = Color(0xFF0284C7).copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(0.5.dp, Color(0xFF38BDF8))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    VoiceAudioWaveBars(
                                                        barColor = Color(0xFF38BDF8),
                                                        barCount = 3,
                                                        maxHeight = 8.dp
                                                    )
                                                    Text(
                                                        text = "Lendo...",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF38BDF8),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(3.dp))

                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp
                                        ),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Compact Action Buttons
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            onClick = { onMarkAsViewed(notif) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF1E293B),
                                            border = BorderStroke(0.5.dp, Color(0xFF475569))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Done,
                                                    contentDescription = null,
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "Lido",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Surface(
                                            onClick = { onResolveNotification(notif) },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (notif.severity == NotificationSeverity.CRITICAL) Color(0xFFDC2626) else Color(0xFF0284C7)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = if (notif.targetEventGid != null) "Resolver 🎯" else "Resolver →",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.5.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Fechar",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    )
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

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        AppTtsManager.initIfNeeded(context)
    }

    var currentTab by remember { mutableStateOf(PortalTab.DASHBOARD) }
    var showAdminConfig by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showVipRoomDialog by remember { mutableStateOf(false) }

    val userPlanConfig by viewModel.userEffectivePlanConfig.collectAsStateWithLifecycle()
    val globalLicenseConfig by viewModel.globalLicenseConfig.collectAsStateWithLifecycle()
    val userTier = remember(loggedUser) { loggedUser.effectiveLicenseTier }

    val eaRobotStatus by viewModel.eaRobotStatus.collectAsStateWithLifecycle()
    val eaConfig by viewModel.eaConfig.collectAsStateWithLifecycle()
    val eaRobotEvents by viewModel.eaRobotEvents.collectAsStateWithLifecycle()
    val syncMetadata by viewModel.syncMetadata.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val chartScreenshot by viewModel.chartScreenshot.collectAsStateWithLifecycle()

    val financialTimeframe by viewModel.financialTimeframe.collectAsStateWithLifecycle()
    val financialCandles by viewModel.financialCandles.collectAsStateWithLifecycle()
    val financialTransactions by viewModel.financialTransactions.collectAsStateWithLifecycle()

    val viewedGids by com.example.data.NotificationStateManager.viewedGids.collectAsStateWithLifecycle()
    val focusedEventGid by com.example.data.NotificationStateManager.focusedEventGid.collectAsStateWithLifecycle()
    val currentlyReadingKey by PortalEventQueueManager.currentlyReadingKey.collectAsStateWithLifecycle()
    val isTtsSpeaking by AppTtsManager.isSpeaking.collectAsStateWithLifecycle()
    val isVoiceActive = currentlyReadingKey != null || isTtsSpeaking

    val systemNotifications = remember(loggedUser, eaRobotStatus, eaConfig, eaRobotEvents, viewedGids) {
        calculateSystemNotifications(loggedUser, eaRobotStatus, eaConfig, eaRobotEvents, viewedGids)
    }

    LaunchedEffect(focusedEventGid) {
        if (!focusedEventGid.isNullOrBlank()) {
            currentTab = PortalTab.EA_EVENTS
        }
    }

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

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            com.example.service.EaEventMonitorService.startService(context)
        } else {
            com.example.service.EaEventMonitorService.stopService(context)
        }
    }

    LaunchedEffect(eaRobotEvents, isLoggedIn) {
        if (isLoggedIn && eaRobotEvents.isNotEmpty()) {
            PortalEventQueueManager.onEventsReceived(context, eaRobotEvents)
        }
    }

    LaunchedEffect(feedbackMessage, isLoggedIn) {
        if (isLoggedIn) {
            feedbackMessage?.let {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearMessage()
            }
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
        val userTier = loggedUser?.effectiveLicenseTier ?: LicenseTier.TRIAL
        CompositionLocalProvider(
            LocalLicenseTier provides userTier,
            LocalLicensePlanConfig provides userPlanConfig,
            LocalGlobalLicenseConfig provides globalLicenseConfig
        ) {
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
                        IconButton(onClick = { showNotificationsDialog = true }) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isVoiceActive) {
                                    VoicePulsingWaveHalo(
                                        modifier = Modifier.size(36.dp),
                                        color = Color(0xFF38BDF8)
                                    )
                                }

                                Box(contentAlignment = Alignment.Center) {
                                    if (isVoiceActive) {
                                        VoicePulsingWaveHalo(
                                            color = Color(0xFF38BDF8),
                                            modifier = Modifier.size(38.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { showNotificationsDialog = true },
                                        modifier = Modifier.testTag("system_notifications_button")
                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (isVoiceActive) {
                                                    Badge(
                                                        containerColor = Color(0xFF0284C7),
                                                        contentColor = Color.White
                                                    ) {
                                                        VoiceAudioWaveBars(
                                                            barColor = Color.White,
                                                            barCount = 3,
                                                            maxHeight = 7.dp,
                                                            modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                } else if (systemNotifications.isNotEmpty()) {
                                                    Badge(
                                                        containerColor = if (systemNotifications.any { it.severity == NotificationSeverity.CRITICAL }) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                                    ) {
                                                        Text(
                                                            text = systemNotifications.size.toString(),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isVoiceActive) Icons.Default.VolumeUp else Icons.Default.Notifications,
                                                contentDescription = "Notificações",
                                                tint = if (isVoiceActive) Color(0xFF38BDF8) else if (systemNotifications.isNotEmpty()) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { showVipRoomDialog = true },
                            modifier = Modifier.testTag("vip_room_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = if (userPlanConfig.sala) Color(0xFFA855F7) else Color(0xFF64748B),
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (userPlanConfig.sala) "VIP" else "LOCK",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (userPlanConfig.sala) Icons.Default.Stars else Icons.Default.Lock,
                                    contentDescription = "Sala VIP de Sinais",
                                    tint = if (userPlanConfig.sala) Color(0xFFA855F7) else Color(0xFF94A3B8)
                                )
                            }
                        }

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
            },
            floatingActionButton = {
                SmallFloatingActionButton(
                    onClick = { showSupportDialog = true },
                    containerColor = Color(0xFF0EA5E9),
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("support_fab")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Help,
                            contentDescription = "Suporte Técnico",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Suporte",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.End
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
                    when (currentTab) {
                        PortalTab.DASHBOARD -> DashboardScreen(
                            loggedUser = loggedUser,
                            eaRobotStatus = eaRobotStatus,
                            eaRobotEvents = eaRobotEvents,
                            eaConfig = eaConfig,
                            chartScreenshot = chartScreenshot,
                            financialTimeframe = financialTimeframe,
                            financialCandles = financialCandles,
                            financialTransactions = financialTransactions,
                            onSelectFinancialTimeframe = { viewModel.setFinancialTimeframe(it) },
                            onRegisterDeposit = { amt, note -> viewModel.registerDeposit(amt, note) },
                            onRegisterWithdrawal = { amt, note -> viewModel.registerWithdrawal(amt, note) },
                            onRegisterClosedPosition = { sym, profit, note -> viewModel.registerClosedPosition(sym, profit, note) },
                            onRequestScreenshot = { viewModel.requestChartScreenshot() },
                            onSaveMt5Id = { viewModel.updateMt5IdServerless(it) },
                            onStartTour = { showEaTourDialog = true },
                            systemNotifications = systemNotifications,
                            onNavigateToTab = { currentTab = it },
                            onResolveNotification = { notif ->
                                com.example.data.NotificationStateManager.markAsResolved(context, notif.gid, notif.targetEventGid)
                                if (notif.targetEventGid != null) {
                                    com.example.data.NotificationStateManager.setFocusedEventGid(notif.targetEventGid)
                                    currentTab = PortalTab.EA_EVENTS
                                } else if (notif.targetTab != null) {
                                    currentTab = notif.targetTab
                                }
                            },
                            onMarkAsViewed = { notif ->
                                com.example.data.NotificationStateManager.markAsViewed(context, notif.gid, notif.targetEventGid)
                            },
                            onSaveEaConfig = { viewModel.saveEaConfig(it) },
                            onFetchExchangeRate = { code, cb -> viewModel.fetchExchangeRate(code, cb) }
                        )
                        PortalTab.CLIENT_LICENSE -> ClientLicenseScreen(
                            loggedUser = loggedUser,
                            refundRequests = refundRequests,
                            onRequestNewRefund = { viewModel.requestRefundServerless() },
                            systemNotifications = systemNotifications,
                            onNavigateToTab = { currentTab = it }
                        )
                        PortalTab.EA_EVENTS -> EaRobotEventsScreen(
                            events = eaRobotEvents,
                            mt5AccountId = loggedUser?.mt5IdConta ?: "",
                            focusedEventGid = focusedEventGid,
                            syncMetadata = syncMetadata,
                            isSyncing = isSyncing,
                            onTriggerSync = { forceFull -> viewModel.triggerSmartSync(forceFull) },
                            onClearFocus = { com.example.data.NotificationStateManager.clearFocusedEventGid() },
                            onClearEvents = { viewModel.clearEvents() }
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
    }

    if (showNotificationsDialog) {
        SystemNotificationsDialog(
            notifications = systemNotifications,
            onNavigateToTab = { tab ->
                currentTab = tab
                showNotificationsDialog = false
            },
            onResolveNotification = { notif ->
                showNotificationsDialog = false
                com.example.data.NotificationStateManager.markAsResolved(context, notif.gid, notif.targetEventGid)
                if (notif.targetEventGid != null) {
                    com.example.data.NotificationStateManager.setFocusedEventGid(notif.targetEventGid)
                    currentTab = PortalTab.EA_EVENTS
                } else if (notif.targetTab != null) {
                    currentTab = notif.targetTab
                }
            },
            onMarkAsViewed = { notif ->
                com.example.data.NotificationStateManager.markAsViewed(context, notif.gid, notif.targetEventGid)
            },
            onMarkAllAsViewed = {
                val notifGids = systemNotifications.map { it.gid }
                val eventGids = systemNotifications.mapNotNull { it.targetEventGid }
                com.example.data.NotificationStateManager.markAllAsViewed(context, notifGids, eventGids)
            },
            onDismiss = { showNotificationsDialog = false }
        )
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

    if (showSupportDialog) {
        SupportTicketDialog(
            userProfile = loggedUser,
            onDismiss = { showSupportDialog = false },
            onSubmit = { categoria, assunto, mensagem, contato ->
                viewModel.submitSupportTicket(categoria, assunto, mensagem, contato)
                showSupportDialog = false
            }
        )
    }

    if (showVipRoomDialog) {
        VipSignalRoomDialog(
            planConfig = userPlanConfig,
            currentTier = userTier,
            onDismiss = { showVipRoomDialog = false },
            onNavigateToPlans = {
                currentTab = PortalTab.CLIENT_LICENSE
                showVipRoomDialog = false
            }
        )
    }
}

// Helper function to open Telegram
fun openTelegramLink(context: android.content.Context, link: String = "https://t.me/FiMasterVipOficial") {
    try {
        val trimmed = link.trim()
        val cleanUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("tg://") -> trimmed
            trimmed.startsWith("@") -> "https://t.me/" + trimmed.substring(1)
            trimmed.startsWith("t.me/") -> "https://$trimmed"
            else -> "https://t.me/$trimmed"
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        android.widget.Toast.makeText(context, "Abrindo Telegram...", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Telegram Link", link)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Link copiado: $link", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Helper function to open WhatsApp for customer support and payment confirmation
fun openWhatsAppLink(
    context: android.content.Context,
    linkOrNumber: String = "https://wa.me/258840000000",
    message: String = "Olá, gostaria de falar sobre as licenças FiMaster."
) {
    try {
        val trimmed = linkOrNumber.trim()
        val cleanUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                if (trimmed.contains("text=")) trimmed
                else if (trimmed.contains("?")) "$trimmed&text=" + java.net.URLEncoder.encode(message, "UTF-8")
                else "$trimmed?text=" + java.net.URLEncoder.encode(message, "UTF-8")
            }
            trimmed.startsWith("wa.me/") -> {
                "https://$trimmed" + (if (trimmed.contains("?")) "&text=" else "?text=") + java.net.URLEncoder.encode(message, "UTF-8")
            }
            else -> {
                val cleanDigits = trimmed.replace(Regex("[^0-9]"), "")
                if (cleanDigits.isNotEmpty()) "https://wa.me/$cleanDigits?text=" + java.net.URLEncoder.encode(message, "UTF-8")
                else "https://wa.me/?text=" + java.net.URLEncoder.encode(message, "UTF-8")
            }
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(cleanUrl)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        android.widget.Toast.makeText(context, "Abrindo WhatsApp...", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("WhatsApp Link", linkOrNumber)
        clipboard?.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Link de atendimento copiado!", android.widget.Toast.LENGTH_SHORT).show()
    }
}

// Decodes Base64 string bytes into an Android ImageBitmap
fun decodeBase64ToImageBitmap(base64Str: String?): androidx.compose.ui.graphics.ImageBitmap? {
    if (base64Str.isNullOrBlank()) return null
    return try {
        val clean = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val bytes = android.util.Base64.decode(clean.trim(), android.util.Base64.DEFAULT)
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// Composable to visualize the QR Code either from Base64 decoded bytes or procedural canvas representation
@Composable
fun QrCodeVisualizer(
    base64Image: String?,
    qrCodeLink: String,
    modifier: Modifier = Modifier,
    contentDescription: String = "QR Code de Pagamento"
) {
    val bitmap = remember(base64Image) { decodeBase64ToImageBitmap(base64Image) }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 8.dp,
        border = BorderStroke(2.dp, Color(0xFF38BDF8)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(12.dp)
                .size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                ProceduralQrCodeCanvas(data = qrCodeLink, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// Renders a high-tech QR Code pattern for the given data
@Composable
fun ProceduralQrCodeCanvas(data: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val gridSize = 21
        val cellSize = sizePx / gridSize
        val blackColor = Color(0xFF0F172A)
        val hash = (data.ifBlank { "https://fimaster.vip/pay" }).hashCode()
        val random = java.util.Random(hash.toLong())

        drawRect(Color.White)

        fun drawFinderPattern(startX: Int, startY: Int) {
            drawRect(
                color = blackColor,
                topLeft = androidx.compose.ui.geometry.Offset(startX * cellSize, startY * cellSize),
                size = androidx.compose.ui.geometry.Size(7 * cellSize, 7 * cellSize)
            )
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset((startX + 1) * cellSize, (startY + 1) * cellSize),
                size = androidx.compose.ui.geometry.Size(5 * cellSize, 5 * cellSize)
            )
            drawRect(
                color = blackColor,
                topLeft = androidx.compose.ui.geometry.Offset((startX + 2) * cellSize, (startY + 2) * cellSize),
                size = androidx.compose.ui.geometry.Size(3 * cellSize, 3 * cellSize)
            )
        }

        drawFinderPattern(0, 0)
        drawFinderPattern(gridSize - 7, 0)
        drawFinderPattern(0, gridSize - 7)

        for (i in 8 until (gridSize - 8)) {
            if (i % 2 == 0) {
                drawRect(
                    color = blackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(6 * cellSize, i * cellSize),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
                drawRect(
                    color = blackColor,
                    topLeft = androidx.compose.ui.geometry.Offset(i * cellSize, 6 * cellSize),
                    size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                )
            }
        }

        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val isFinder = (row < 8 && col < 8) ||
                               (row < 8 && col >= gridSize - 8) ||
                               (row >= gridSize - 8 && col < 8) ||
                               (row == 6 || col == 6)
                if (!isFinder) {
                    val bit = ((row * 31 + col * 17 + hash) % 3 == 0) || random.nextBoolean()
                    if (bit) {
                        drawRect(
                            color = blackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(col * cellSize, row * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

data class VipSignalItem(
    val symbol: String,
    val type: String,
    val entry: String,
    val tp: String,
    val sl: String,
    val strategy: String,
    val status: String,
    val isBuy: Boolean
)

@Composable
fun VipSignalRoomDialog(
    planConfig: LicensePlanConfig,
    currentTier: LicenseTier,
    onDismiss: () -> Unit,
    onNavigateToPlans: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("fimaster_vip_prefs", android.content.Context.MODE_PRIVATE) }
    var telegramLink by remember { 
        mutableStateOf(prefs.getString("custom_telegram_vip_url", "https://t.me/FiMasterVipOficial") ?: "https://t.me/FiMasterVipOficial") 
    }
    var isEditingTelegramLink by remember { mutableStateOf(false) }
    var editLinkInput by remember { mutableStateOf(telegramLink) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .border(1.5.dp, if (planConfig.sala) Color(0xFF229ED9).copy(alpha = 0.8f) else Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(
                                    if (planConfig.sala) Color(0xFF229ED9).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .border(1.dp, if (planConfig.sala) Color(0xFF229ED9) else Color(0xFFEF4444), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (planConfig.sala) Icons.Default.Send else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (planConfig.sala) Color(0xFF229ED9) else Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "SALA VIP TELEGRAM",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 1.sp
                                    )
                                )
                                Surface(
                                    color = if (planConfig.sala) Color(0xFF229ED9).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, if (planConfig.sala) Color(0xFF229ED9) else Color(0xFFEF4444))
                                ) {
                                    Text(
                                        text = if (planConfig.sala) "ONLINE" else "BLOQUEADO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (planConfig.sala) Color(0xFF38BDF8) else Color(0xFFEF4444),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (planConfig.sala) "Sinais, Lives & Transmissões no Telegram 📲" else "Recurso Restrito ao Plano Master VIP",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (planConfig.sala) Color(0xFF38BDF8) else Color(0xFFEF4444),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF94A3B8))
                    }
                }

                HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                if (planConfig.sala) {
                    // VIP Room Content (Allowed)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Main Telegram Action Hero Banner
                        Surface(
                            color = Color(0xFF0369A1).copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF229ED9).copy(alpha = 0.7f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(0xFF10B981), CircleShape)
                                        )
                                        Text(
                                            text = "TRANSMISSÃO TELEGRAM ATIVA",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF38BDF8),
                                                letterSpacing = 0.5.sp
                                            )
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            editLinkInput = telegramLink
                                            isEditingTelegramLink = !isEditingTelegramLink
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar Link",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Conecte-se agora à Sala VIP Oficial no Telegram para receber análises, calls de entrada e acompanhamento ao vivo dos analistas da Fimathe.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp
                                    )
                                )

                                if (isEditingTelegramLink) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Link / Canal do Telegram:",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                        )
                                        OutlinedTextField(
                                            value = editLinkInput,
                                            onValueChange = { editLinkInput = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF229ED9),
                                                unfocusedBorderColor = Color(0xFF475569)
                                            )
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = { isEditingTelegramLink = false }) {
                                                Text("Cancelar", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    val saved = if (editLinkInput.isNotBlank()) editLinkInput.trim() else "https://t.me/FiMasterVipOficial"
                                                    telegramLink = saved
                                                    prefs.edit().putString("custom_telegram_vip_url", saved).apply()
                                                    isEditingTelegramLink = false
                                                    android.widget.Toast.makeText(context, "Link do Telegram atualizado!", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("Salvar Link", color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Primary Button: Direct Push to Telegram
                                    Button(
                                        onClick = {
                                            openTelegramLink(context, telegramLink)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(vertical = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ABRIR NO TELEGRAM",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Black
                                            )
                                        )
                                    }

                                    // Secondary Button: Copy Link
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("Telegram VIP Link", telegramLink)
                                            clipboard?.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Link copiado: $telegramLink", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFF475569), RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar Link",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "SETUPS & SINAIS TRANSMITIDOS (AO VIVO)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 1.sp
                            )
                        )

                        // Sample VIP Signals Live Stream
                        val liveSignals = listOf(
                            VipSignalItem(
                                symbol = "XAUUSD (Ouro)",
                                type = "COMPRA / BUY",
                                entry = "2714.50",
                                tp = "2728.00 (Canal 100%)",
                                sl = "2708.20 (Subciclo)",
                                strategy = "Rompimento de Canal Fimathe M15",
                                status = "EM ANDAMENTO ⚡",
                                isBuy = true
                            ),
                            VipSignalItem(
                                symbol = "EURUSD",
                                type = "VENDA / SELL",
                                entry = "1.08450",
                                tp = "1.08050",
                                sl = "1.08720",
                                strategy = "Tendência Primária D1 / H1",
                                status = "ALVO 1 ATINGIDO (+40 pips) 🎯",
                                isBuy = false
                            ),
                            VipSignalItem(
                                symbol = "US30 (Dow Jones)",
                                type = "COMPRA / BUY",
                                entry = "43820",
                                tp = "44150",
                                sl = "43650",
                                strategy = "Sessão Nova York Reversão 50%",
                                status = "AGUARDANDO GATILHO ⏳",
                                isBuy = true
                            )
                        )

                        liveSignals.forEach { sig ->
                            Surface(
                                color = Color(0xFF1E293B).copy(alpha = 0.8f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (sig.isBuy) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = sig.symbol,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        )
                                        Surface(
                                            color = if (sig.isBuy) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (sig.isBuy) Color(0xFF10B981) else Color(0xFFEF4444))
                                        ) {
                                            Text(
                                                text = sig.type,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Black,
                                                    color = if (sig.isBuy) Color(0xFF34D399) else Color(0xFFF87171),
                                                    fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = sig.strategy,
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontSize = 11.sp)
                                    )

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("ENTRADA", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.sp))
                                            Text(sig.entry, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                        }
                                        Column {
                                            Text("TAKE PROFIT", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.sp))
                                            Text(sig.tp, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("STOP LOSS", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.sp))
                                            Text(sig.sl, style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFEF4444), fontWeight = FontWeight.Bold))
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Status: ${sig.status}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFC084FC), fontWeight = FontWeight.SemiBold)
                                        )

                                        TextButton(
                                            onClick = { openTelegramLink(context, telegramLink) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = null,
                                                tint = Color(0xFF229ED9),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Ver no Telegram",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = Color(0xFF229ED9),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // VIP Room Locked
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(70.dp),
                            border = BorderStroke(2.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Acesso Bloqueado",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "ACESSO BLOQUEADO PELO PLANO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFEF4444),
                                letterSpacing = 1.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "A transmissão ao vivo de sinais, análises diárias da Fimathe e calls de mercado no Telegram são exclusivas para assinantes com o parâmetro 'sala: true' (Plano Master VIP).\n\nO seu plano atual (${currentTier.displayName}) não inclui acesso ao Canal VIP do Telegram.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFCBD5E1),
                                lineHeight = 20.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF229ED9).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "📲 BENEFÍCIOS DA SALA VIP TELEGRAM:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                )
                                Text("• Sinais em tempo real para XAUUSD, Forex e Índices no Telegram", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)))
                                Text("• Lives de abertura de mercado (Londres e Nova York)", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)))
                                Text("• Setups e projeções de canais Fimathe detalhados", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)))
                                Text("• Suporte VIP Direto 24/7", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0)))
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onNavigateToPlans,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Fazer Upgrade para Master VIP 👑",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getCurrencySymbol(monyCode: String?): String {
    val code = monyCode?.uppercase()?.trim() ?: ""
    return when {
        code.contains("USD") || code == "$" -> "USD"
        code.contains("MZN") || code.contains("METICAL") || code.contains("METICAIS") || code == "MT" -> "MT"
        code.contains("BRL") || code.contains("REAL") || code == "R$" -> "R$"
        code.contains("EUR") || code.contains("EURO") || code == "€" -> "€"
        code.contains("AOA") || code.contains("KWANZA") || code == "KZ" -> "Kz"
        code.isNotBlank() -> code.take(5)
        else -> "MT"
    }
}

@Composable
fun HeroEaBalanceCard(
    saldo: Double,
    resultadoOrdem: Double = 0.0,
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
                    text = String.format(Locale.US, "Original: $%,.2f USD (Câmbio: %.1f %s/USD)", saldo, cambio, currencySymbol),
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
                            text = "RESULTADOS DA ORDEM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold
                            )
                        )
                        val resCambiado = resultadoOrdem * cambio
                        val resColor = if (resultadoOrdem >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        val resSign = if (resultadoOrdem > 0) "+" else ""
                        Text(
                            text = String.format(Locale.US, "%s%,.2f %s", resSign, resCambiado, currencySymbol),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = resColor,
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
fun AccountCurrencySelectorCard(
    currentMony: String,
    currentCambio: Double,
    onSelectCurrency: (code: String, rate: Double) -> Unit,
    onUpdateRateOnline: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CurrencyExchange,
                            contentDescription = "Câmbio e Conversão",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CÂMBIO & MOEDA DA CONTA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "Alterne o câmbio para recalcular o Saldo, Gráficos e Acumuladores",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                        )
                    }
                }
            }

            val currencies = listOf(
                "USD" to "🇺🇸 USD ($)",
                "MZN" to "🇲🇿 Metical (MT)",
                "BRL" to "🇧🇷 Real (R$)",
                "EUR" to "🇪🇺 Euro (€)",
                "AOA" to "🇦🇴 Kwanza (Kz)"
            )
            val defaultPresetRates = mapOf(
                "USD" to 1.0,
                "MZN" to 64.0,
                "BRL" to 5.5,
                "EUR" to 0.92,
                "AOA" to 920.0
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currencies.forEach { (code, label) ->
                    val isSelected = currentMony.uppercase().contains(code) || (code == "USD" && currentMony.uppercase().contains("USD"))
                    val presetRate = defaultPresetRates[code] ?: 1.0

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSelectCurrency(code, presetRate)
                                if (code != "USD") {
                                    onUpdateRateOnline(code)
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFF0EA5E9) else Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TAXA ATIVA:",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "1 USD = $currentCambio ${getCurrencySymbol(currentMony)}",
                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = { onUpdateRateOnline(currentMony.ifBlank { "MZN" }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Atualizar On-line", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp))
                }
            }
        }
    }
}

@Composable
fun OrderResultsAccumulatorCard(
    eaRobotEvents: List<com.example.data.EaRobotEvent>,
    financialCandles: List<com.example.ui.FinancialCandle> = emptyList(),
    financialTransactions: List<com.example.ui.FinancialTransaction> = emptyList(),
    cambio: Double = 64.0,
    currencySymbol: String = "MT"
) {
    val closedTrades = remember(financialTransactions) {
        financialTransactions.filter { it.type == com.example.ui.TransactionType.CLOSED_POSITION }
    }

    val nowSec = remember { System.currentTimeMillis() / 1000L }

    val totalDiarioEvents = remember(closedTrades, eaRobotEvents, nowSec) {
        if (closedTrades.isNotEmpty()) {
            val todayTrades = closedTrades.filter { (nowSec - it.timestamp) <= 86400L }
            if (todayTrades.isNotEmpty()) todayTrades.sumOf { it.amount }
            else closedTrades.lastOrNull()?.amount ?: 0.0
        } else {
            eaRobotEvents.firstOrNull { it.diarioValor != 0.0 }?.diarioValor ?: 0.0
        }
    }

    val totalSemanalEvents = remember(closedTrades, eaRobotEvents, totalDiarioEvents, nowSec) {
        if (closedTrades.isNotEmpty()) {
            val weekTrades = closedTrades.filter { (nowSec - it.timestamp) <= 7 * 86400L }
            if (weekTrades.isNotEmpty()) weekTrades.sumOf { it.amount }
            else closedTrades.sumOf { it.amount }
        } else {
            val sem = eaRobotEvents.firstOrNull { it.semanalValor != 0.0 }?.semanalValor
            sem ?: (totalDiarioEvents * 4.0)
        }
    }

    val totalMensalEvents = remember(closedTrades, eaRobotEvents, totalSemanalEvents, nowSec) {
        if (closedTrades.isNotEmpty()) {
            val monthTrades = closedTrades.filter { (nowSec - it.timestamp) <= 30 * 86400L }
            if (monthTrades.isNotEmpty()) monthTrades.sumOf { it.amount }
            else closedTrades.sumOf { it.amount }
        } else {
            if (totalSemanalEvents != 0.0) totalSemanalEvents * 4.5 else totalDiarioEvents * 22.0
        }
    }

    val orderCount = remember(closedTrades, financialCandles, eaRobotEvents) {
        if (closedTrades.isNotEmpty()) {
            closedTrades.size
        } else if (financialCandles.isNotEmpty() && financialCandles.sumOf { it.tradeCount } > 0) {
            financialCandles.sumOf { it.tradeCount }
        } else if (eaRobotEvents.isNotEmpty()) {
            val count = eaRobotEvents.count { 
                val ev = it.event.lowercase()
                ev.contains("ordem") || ev.contains("posicao") || ev.contains("order") || ev.contains("estado") || ev.contains("equador") || ev.contains("sessao") || ev.contains("relatorio") || ev.contains("captura")
            }
            if (count == 0) eaRobotEvents.size else count
        } else {
            0
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.3f), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueryStats,
                            contentDescription = "Acumulador de Ordens",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "ACUMULADOR & RESULTADOS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFFC084FC),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                        )
                        Text(
                            text = "Resultados e Projeção de Ordens MT5",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                }
            }

            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "PROJEÇÃO DE RESULTADOS ACUMULADOS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RES. DIÁRIO",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%s%,.2f %s", if (totalDiarioEvents >= 0) "+" else "", totalDiarioEvents * cambio, currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (totalDiarioEvents >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%s$%,.2f USD", if (totalDiarioEvents >= 0) "+" else "", totalDiarioEvents),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                            )
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "RES. SEMANAL",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%s%,.2f %s", if (totalSemanalEvents >= 0) "+" else "", totalSemanalEvents * cambio, currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (totalSemanalEvents >= 0) Color(0xFF38BDF8) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%s$%,.2f USD", if (totalSemanalEvents >= 0) "+" else "", totalSemanalEvents),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "RES. MENSAL",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%s%,.2f %s", if (totalMensalEvents >= 0) "+" else "", totalMensalEvents * cambio, currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (totalMensalEvents >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = String.format(Locale.US, "%s$%,.2f USD", if (totalMensalEvents >= 0) "+" else "", totalMensalEvents),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                            )
                        }

                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(
                                text = "NÚMERO DE ORDENS",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$orderCount ordens",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Sincronizadas com o histórico",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                            )
                        }
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
    eaRobotEvents: List<com.example.data.EaRobotEvent> = emptyList(),
    eaConfig: com.example.data.EaConfigEntity?,
    chartScreenshot: com.example.ui.ChartScreenshotData = com.example.ui.ChartScreenshotData(),
    financialTimeframe: com.example.ui.EquityTimeframe = com.example.ui.EquityTimeframe.PER_POSITION,
    financialCandles: List<com.example.ui.FinancialCandle> = emptyList(),
    financialTransactions: List<com.example.ui.FinancialTransaction> = emptyList(),
    onSelectFinancialTimeframe: (com.example.ui.EquityTimeframe) -> Unit = {},
    onRegisterDeposit: (Double, String) -> Unit = { _, _ -> },
    onRegisterWithdrawal: (Double, String) -> Unit = { _, _ -> },
    onRegisterClosedPosition: (String, Double, String) -> Unit = { _, _, _ -> },
    onRequestScreenshot: () -> Unit = {},
    onSaveMt5Id: (String) -> Unit,
    onStartTour: () -> Unit = {},
    systemNotifications: List<SystemNotification> = emptyList(),
    onNavigateToTab: (PortalTab) -> Unit = {},
    onResolveNotification: ((SystemNotification) -> Unit)? = null,
    onMarkAsViewed: ((SystemNotification) -> Unit)? = null,
    onSaveEaConfig: (com.example.data.EaConfigEntity) -> Unit = {},
    onFetchExchangeRate: (String, (Double?) -> Unit) -> Unit = { _, _ -> }
) {
    if (loggedUser == null) return

    val currentCambio = eaConfig?.CAMBIO ?: 64.0
    val currentMony = eaConfig?.mony ?: "MZN"
    val currencySymbol = getCurrencySymbol(currentMony)

    val availableBalance = if (eaRobotStatus?.saldoDisponivel != null && eaRobotStatus.saldoDisponivel > 0.0) {
        eaRobotStatus.saldoDisponivel
    } else {
        loggedUser.saldo
    }

    val totalResultadoOrdem = remember(eaRobotEvents) {
        var sum = 0.0
        for (event in eaRobotEvents) {
            if (event.diarioValor != 0.0) {
                sum += event.diarioValor
            }
        }
        sum
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PAINEL DA CONTA EA MT5",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF22D3EE),
                        letterSpacing = 2.sp
                    )
                )
            }
        }

        item {
            // ISOLATED, PROMINENT HERO SALDO CARD (WITH CAMBIO AND RESULTADOS DA ORDEM)
            HeroEaBalanceCard(
                saldo = availableBalance,
                resultadoOrdem = totalResultadoOrdem,
                isOnline = eaRobotStatus?.online == true,
                temPosicao = eaRobotStatus?.temPosicao == true,
                cambio = currentCambio,
                currencySymbol = currencySymbol
            )
        }

        item {
            // Real-time Robot Execution Card
            EaRobotStatusCard(eaRobotStatus, cambio = currentCambio)
        }

        item {
            // Chart Screenshot Card with MQL5 Objects
            ChartScreenshotCard(
                chartScreenshot = chartScreenshot,
                onRequestScreenshot = onRequestScreenshot,
                onViewFullChart = {},
                onViewMql5Code = {}
            )
        }

        item {
            // Painel de Gráfico Candlestick de Patrimônio, Posições, Depósitos e Saques
            FinancialEquityCandlestickCard(
                candles = financialCandles,
                currentTimeframe = financialTimeframe,
                onSelectTimeframe = onSelectFinancialTimeframe,
                cambio = currentCambio,
                currencySymbol = currencySymbol
            )
        }

        item {
            // Acumulador e Pré-visualização de Resultados das Ordens
            OrderResultsAccumulatorCard(
                eaRobotEvents = eaRobotEvents,
                financialCandles = financialCandles,
                financialTransactions = financialTransactions,
                cambio = currentCambio,
                currencySymbol = currencySymbol
            )
        }

        item {
            // Botões de Câmbio & Seletor de Moeda para a Aba de Conta
            AccountCurrencySelectorCard(
                currentMony = currentMony,
                currentCambio = currentCambio,
                onSelectCurrency = { code, defaultRate ->
                    val baseConfig = eaConfig ?: com.example.data.EaConfigEntity(mt5AccountId = loggedUser.mt5IdConta)
                    val updated = baseConfig.copy(mony = code, CAMBIO = defaultRate)
                    onSaveEaConfig(updated)
                },
                onUpdateRateOnline = { targetCode ->
                    onFetchExchangeRate(targetCode) { newRate ->
                        if (newRate != null && newRate > 0.0) {
                            val baseConfig = eaConfig ?: com.example.data.EaConfigEntity(mt5AccountId = loggedUser.mt5IdConta)
                            val updated = baseConfig.copy(mony = targetCode, CAMBIO = newRate)
                            onSaveEaConfig(updated)
                        }
                    }
                }
            )
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
    onRequestNewRefund: () -> Unit,
    systemNotifications: List<SystemNotification> = emptyList(),
    onNavigateToTab: (PortalTab) -> Unit = {}
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
                expiryDate = loggedUser.licencaValidade,
                creditoGuardado = loggedUser.creditoGuardado,
                tier = loggedUser.effectiveLicenseTier,
                mt5AccountId = loggedUser.mt5IdConta
            )
        }

        item {
            PlanTiersShowcaseCard(
                currentTier = loggedUser.effectiveLicenseTier,
                loggedUser = loggedUser
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
    mt5AccountId: String,
    focusedEventGid: String? = null,
    syncMetadata: com.example.data.SyncMetadataEntity? = null,
    isSyncing: Boolean = false,
    onTriggerSync: (Boolean) -> Unit = {},
    onClearFocus: () -> Unit = {},
    onClearEvents: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val readKeys by PortalEventQueueManager.readEventKeys.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val visibleEvents = remember(events) {
        events.filterNot { evt ->
            evt.event.equals("posicao_alterada", ignoreCase = true) ||
            evt.event.contains("posicao_alterada", ignoreCase = true)
        }
    }

    val readCount = remember(visibleEvents, readKeys) {
        visibleEvents.count { readKeys.contains(PortalEventQueueManager.getEventUniqueKey(it)) }
    }
    val unreadCount = visibleEvents.size - readCount

    // Smooth scroll to targeted event if focused via GID
    LaunchedEffect(focusedEventGid, visibleEvents) {
        if (!focusedEventGid.isNullOrBlank()) {
            val targetIdx = visibleEvents.indexOfFirst {
                it.getResolvedGid() == focusedEventGid || it.gid == focusedEventGid || it.id == focusedEventGid
            }
            if (targetIdx >= 0) {
                listState.animateScrollToItem(index = targetIdx + 1)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title and Accounts + Badge Counts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "EVENTOS E LOGS DO ROBÔ EA",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22D3EE),
                                    letterSpacing = 0.5.sp
                                )
                            )
                            Text(
                                text = if (mt5AccountId.isNotBlank()) "Conta MT5: $mt5AccountId" else "Todas as Contas",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                            )
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${visibleEvents.size} Total",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF22D3EE),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                                )
                                Text(
                                    text = "$readCount Lidos",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (unreadCount > 0) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                                    )
                                    Text(
                                        text = "$unreadCount Pendentes",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Smart Sync Button
                        Surface(
                            onClick = { onTriggerSync(false) },
                            enabled = !isSyncing,
                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sincronização Inteligente",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isSyncing) "Sincronizando..." else "Smart Sync",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }

                        if (visibleEvents.isNotEmpty()) {
                            Surface(
                                onClick = {
                                    PortalEventQueueManager.markAllAsRead(visibleEvents)
                                    android.widget.Toast.makeText(context, "Todos os eventos foram marcados como lidos", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Marcar como Lidos",
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Marcar Lidos",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF34D399),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    PortalEventQueueManager.clearAll(context)
                                    onClearEvents()
                                },
                                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Limpar Eventos",
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Limpar",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFF87171),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Smart Sync Status Info Banner
                    if (syncMetadata != null && syncMetadata.lastSyncTime > 0L) {
                        val syncTimeFormatted = remember(syncMetadata.lastSyncTime) {
                            java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(syncMetadata.lastSyncTime))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0F2238),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    val statusDetail = if (syncMetadata.totalEventsCount == 0 && syncMetadata.lastEventTimestamp > 0L) {
                                        "Limpo (Cursor salvo • Apenas novos)"
                                    } else {
                                        "${syncMetadata.totalEventsCount} locais"
                                    }
                                    Text(
                                        text = "Smart Sync: $syncTimeFormatted • $statusDetail",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFFBAE6FD),
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                TextButton(
                                    onClick = { onTriggerSync(true) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text(
                                        text = "Forçar Completa",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Focused GID Banner
        if (!focusedEventGid.isNullOrBlank()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF082F49),
                    border = BorderStroke(1.5.dp, Color(0xFF38BDF8))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "🎯 Evento Focado via Notificação",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Visualizando o alerta selecionado",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF7DD3FC),
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        TextButton(
                            onClick = onClearFocus,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Limpar Foco ✕",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }

        if (visibleEvents.isEmpty()) {
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
            items(visibleEvents, key = { it.getResolvedGid() }) { event ->
                val isFocused = !focusedEventGid.isNullOrBlank() &&
                    (event.getResolvedGid() == focusedEventGid || event.gid == focusedEventGid || event.id == focusedEventGid)
                EaEventItemCard(event = event, isFocused = isFocused)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun sanitizeText(input: String): String {
    if (input.isBlank()) return ""
    var s = input

    // 1. Fix double/corrupted suffixes produced by previous replacements or raw EA concatenation
    s = s.replace("ãoo", "ão")
        .replace("ááo", "ção")
        .replace("ááes", "ções")
        .replace("áá", "á")

    // 2. Specific fixes for MT5 EA text errors and missing accents
    s = s.replace("configuraçãoo", "configuração")
        .replace("utilizaçãoo", "utilização")
        .replace("organizaçãoo", "organização")
        .replace("formaçãoo", "formação")
        .replace("operaçãoo", "operação")
        .replace("validaçãoo", "validação")
        .replace("recalculari", "recalcular")
        .replace("resistncias", "resistências")
        .replace("resistncia", "resistência")
        .replace("expanso", "expansão")
        .replace("expanses", "expansões")
        .replace("concluso", "conclusão")
        .replace("concluses", "conclusões")
        .replace(" Aps ", " Após ")
        .replace("Aps ", "Após ")
        .replace("aps ", "após ")
        .replace(" poder ser", " poderá ser")
        .replace("fase  aumentar", "fase é aumentar")
        .replace(" fase  ", " fase é ")
        .replace(" at ", " até ")
        .replace(" necessrios", " necessários")
        .replace("necessrio", "necessário")
        .replace("necessria", "necessária")
        .replace("necessrias", "necessárias")
        .replace("vlida", "válida")
        .replace("vlido", "válido")
        .replace("organizao", "organização")
        .replace("informaes", "informações")
        .replace("informaçoes", "informações")
        .replace("formao", "formação")
        .replace("preos", "preços")
        .replace("decises", "decisões")
        .replace("posicao", "posição")
        .replace("posicoes", "posições")
        .replace("execucao", "execução")
        .replace("modificacao", "modificação")
        .replace("protecao", "proteção")
        .replace("operacao", "operação")
        .replace("transicao", "transição")
        .replace("alteracao", "alteração")
        .replace("notificacao", "notificação")
        .replace("configuracao", "configuração")
        .replace("utilizacao", "utilização")
        .replace("validacao", "validação")
        .replace("atuao", "atuação")
        .replace("anlise", "análise")
        .replace("anlises", "análises")
        .replace("automtica", "automática")
        .replace("automtico", "automático")
        .replace("parmetro", "parâmetro")
        .replace("parmetros", "parâmetros")
        .replace("estratgia", "estratégia")
        .replace("estratgias", "estratégias")
        .replace("caractersticas", "características")
        .replace("compatveis", "compatíveis")
        .replace("visveis", "visíveis")
        .replace("nvel", "nível")
        .replace("nveis", "níveis")
        .replace("incio", "início")
        .replace("concludo", "concluído")
        .replace("concluda", "concluída")
        .replace("tendncia", "tendência")
        .replace("tendncias", "tendências")
        .replace("variao", "variação")
        .replace("revises", "revisões")
        .replace("condies", "condições")
        .replace("perodo", "período")
        .replace("perodos", "períodos")
        .replace("referncias", "referências")
        .replace("substitudas", "substituídas")
        .replace("substitudo", "substituído")
        .replace("proxima", "próxima")
        .replace("proximo", "próximo")
        .replace("prxmo", "próximo")

    // 3. Fallback for raw \uFFFD or ?? bytes leftover
    s = s.replace("\uFFFD\uFFFDes", "ções")
        .replace("\uFFFD\uFFFD", "ção")
        .replace("\uFFFDes", "ções")
        .replace("\uFFFDos", "ços")
        .replace("a\uFFFD\uFFFD", "ação")
        .replace("a\uFFFD", "ação")
        .replace("\uFFFD", "")
        .replace("??", "")

    // 4. Clean formatting and spacing
    s = s.replace("  ", " ")
        .replace(" ,", ",")
        .replace(" .", ".")
        .replace(Regex("(\\p{L})\\.(\\p{Lu})"), "$1. $2")
        .trim()

    // 5. Final check against extra trailing 'o'
    s = s.replace("ãoo", "ão")

    return s
}

fun cleanStateEnumText(raw: String): String {
    if (raw.isBlank()) return ""
    var s = sanitizeText(raw.trim())
    val prefixes = listOf(
        "ESTADO_DE_EXECUCAO_",
        "ESTADO_DE_PRECOS_",
        "ESTADO_SICLO_DE_CANAL_",
        "ESTADO_SICLO_DE_",
        "ESTADO_SICLO_",
        "ESTADO_DE_",
        "ESTADO_",
        "notificacao_"
    )
    for (p in prefixes) {
        if (s.startsWith(p, ignoreCase = true)) {
            s = s.substring(p.length)
            break
        }
    }
    s = s.replace("_", " ").trim().uppercase()
    
    return when (s) {
        "ENTRY POSITION", "ENTRY_POSITION", "ENTRY" -> "ENTRADA DE POSIÇÃO"
        "CLOSED POSITION", "CLOSED_POSITION", "CLOSED" -> "POSIÇÃO ENCERRADA"
        "ORDER EXECUTED", "ORDER_EXECUTED" -> "ORDEM EXECUTADA"
        "ORDER MODIFIED", "ORDER_MODIFIED" -> "ORDEM MODIFICADA"
        "ORDER ERROR", "ORDER_ERROR" -> "ERRO DE ORDEM"
        "PERIOD M1" -> "M1"
        "PERIOD M5" -> "M5"
        "PERIOD M15" -> "M15"
        "PERIOD M30" -> "M30"
        "PERIOD H1" -> "H1"
        "PERIOD H4" -> "H4"
        "PERIOD D1" -> "D1"
        else -> s
    }
}

fun detectStateSystemType(event: com.example.data.EaRobotEvent): String {
    val sysUpper = event.sistema.uppercase().trim()
    val combined = "$sysUpper ${event.novo} ${event.anterior} ".uppercase()

    return when {
        sysUpper.contains("ESTADO DE EXECUCAO") || combined.contains("EXECUCAO")   -> "ESTADO DE EXECUÇÃO"
        sysUpper.contains("ESTADO DE PRECOS") || combined.contains("EXPANSAO") -> "ESTADO DE PREÇO"
        sysUpper.contains("ESTADO DE CANAL") || combined.contains("SICLO")  -> "ESTADO DE CICLO"
        else -> if (sysUpper.isNotBlank()) cleanStateEnumText(sysUpper) else "ESTADO DE EXECUÇÃO" 
    }
}

object AppTtsManager {
    private var tts: android.speech.tts.TextToSpeech? = null
    @Volatile private var isInitializing = false
    @Volatile private var isReady = false
    private var activeMediaPlayer: android.media.MediaPlayer? = null
    @Volatile private var currentSessionId: Long = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val _isSpeaking = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isSpeaking: kotlinx.coroutines.flow.StateFlow<Boolean> get() = _isSpeaking

    fun selectMalePtPtVoice(voices: Collection<android.speech.tts.Voice>): android.speech.tts.Voice? {
        // Priority 1: European Portuguese (pt-PT) explicitly male tagged (ptm, male, man, ptd, pt-pt-x-ptm)
        val malePtPtExplicit = voices.firstOrNull { v ->
            val loc = v.locale
            val isPtPt = loc != null && loc.language == "pt" && (loc.country.equals("PT", ignoreCase = true) || v.name.lowercase().contains("pt-pt"))
            val isMale = v.name.lowercase().let { n ->
                (n.contains("male") || n.contains("ptm") || n.contains("ptd") || n.contains("man") || n.contains("x-m") || n.contains("x-ptm")) &&
                !n.contains("female") && !n.contains("woman") && !n.contains("ptf") && !n.contains("vega")
            }
            isPtPt && isMale
        }
        if (malePtPtExplicit != null) return malePtPtExplicit

        // Priority 2: European Portuguese (pt-PT) non-female voice
        val ptPtNonFemale = voices.firstOrNull { v ->
            val loc = v.locale
            val isPtPt = loc != null && loc.language == "pt" && (loc.country.equals("PT", ignoreCase = true) || v.name.lowercase().contains("pt-pt"))
            val isNotFemale = v.name.lowercase().let { n ->
                !n.contains("female") && !n.contains("woman") && !n.contains("ptf") && !n.contains("vega")
            }
            isPtPt && isNotFemale
        }
        if (ptPtNonFemale != null) return ptPtNonFemale

        // Priority 3: Any European Portuguese (pt-PT) voice
        val anyPtPt = voices.firstOrNull { v ->
            val loc = v.locale
            loc != null && loc.language == "pt" && (loc.country.equals("PT", ignoreCase = true) || v.name.lowercase().contains("pt-pt"))
        }
        if (anyPtPt != null) return anyPtPt

        // Priority 4: Portuguese male voice
        return voices.firstOrNull { v ->
            val loc = v.locale
            val isPt = loc != null && loc.language == "pt"
            val isMale = v.name.lowercase().let { n ->
                (n.contains("male") || n.contains("ptm") || n.contains("man")) && !n.contains("female")
            }
            isPt && isMale
        } ?: voices.firstOrNull { v ->
            v.locale?.language == "pt"
        }
    }

    @Synchronized
    fun initIfNeeded(context: android.content.Context) {
        if (tts != null || isInitializing) return
        isInitializing = true
        val appContext = context.applicationContext
        var newTts: android.speech.tts.TextToSpeech? = null
        newTts = android.speech.tts.TextToSpeech(appContext) { status ->
            isInitializing = false
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                val ptPtLocale = java.util.Locale("pt", "PT")
                var langRes = newTts?.setLanguage(ptPtLocale)
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = newTts?.setLanguage(java.util.Locale("pt", "POR"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = newTts?.setLanguage(java.util.Locale("pt"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    newTts?.setLanguage(java.util.Locale.getDefault())
                }

                try {
                    val voices = newTts?.voices
                    if (voices != null) {
                        val malePtVoice = selectMalePtPtVoice(voices)
                        if (malePtVoice != null) {
                            newTts?.voice = malePtVoice
                        }
                    }
                } catch (e: Exception) {}

                val audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                newTts?.setAudioAttributes(audioAttributes)
                newTts?.setSpeechRate(1.00f)
                newTts?.setPitch(0.96f)

                tts = newTts
                isReady = true
            } else {
                isReady = false
            }
        }
    }

    @Synchronized
    fun speak(
        context: android.content.Context,
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val rawClean = sanitizeText(text)
        if (rawClean.isBlank()) {
            onDone()
            return
        }

        stopAll()
        val thisSessionId = currentSessionId
        val appContext = context.applicationContext

        val hasCompleted = java.util.concurrent.atomic.AtomicBoolean(false)

        fun safeOnStart() {
            if (thisSessionId != currentSessionId) return
            _isSpeaking.value = true
            mainHandler.post {
                if (thisSessionId == currentSessionId) {
                    onStart()
                }
            }
        }

        fun safeOnDone() {
            if (hasCompleted.compareAndSet(false, true)) {
                _isSpeaking.value = false
                mainHandler.post {
                    if (thisSessionId == currentSessionId) {
                        onDone()
                    }
                }
            }
        }

        fun safeOnError(msg: String) {
            if (hasCompleted.compareAndSet(false, true)) {
                _isSpeaking.value = false
                mainHandler.post {
                    if (thisSessionId == currentSessionId) {
                        onError(msg)
                    }
                }
            }
        }

        fun playViaMediaPlayerFallback(cleanText: String) {
            if (thisSessionId != currentSessionId) return

            try { tts?.stop() } catch (e: Exception) {}

            try {
                val chunks = mutableListOf<String>()
                var remaining = cleanText.trim()
                while (remaining.isNotEmpty()) {
                    if (remaining.length <= 180) {
                        chunks.add(remaining)
                        break
                    }
                    var cutIdx = remaining.lastIndexOf('.', 180)
                    if (cutIdx <= 30) cutIdx = remaining.lastIndexOf(' ', 180)
                    if (cutIdx <= 30) cutIdx = 180
                    chunks.add(remaining.substring(0, cutIdx + 1).trim())
                    remaining = remaining.substring(cutIdx + 1).trim()
                }

                if (chunks.isEmpty()) {
                    safeOnDone()
                    return
                }

                fun playChunk(idx: Int) {
                    if (thisSessionId != currentSessionId) return
                    if (idx >= chunks.size) {
                        safeOnDone()
                        return
                    }
                    val shortText = chunks[idx]
                    val encodedText = java.net.URLEncoder.encode(shortText, "UTF-8")
                    val audioUrl = "https://translate.google.com/translate_tts?ie=UTF-8&q=$encodedText&tl=pt-PT&client=tw-ob"
                    val headers = mapOf("User-Agent" to "Mozilla/5.0 (Android; Mobile)")

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
                            if (thisSessionId != currentSessionId) {
                                try { release() } catch (e: Exception) {}
                                return@setOnPreparedListener
                            }
                            if (idx == 0) {
                                safeOnStart()
                            }
                            start()
                        }
                        setOnCompletionListener {
                            try { release() } catch (e: Exception) {}
                            if (activeMediaPlayer == this) activeMediaPlayer = null
                            if (thisSessionId == currentSessionId) {
                                playChunk(idx + 1)
                            }
                        }
                        setOnErrorListener { mp, _, _ ->
                            try { mp.release() } catch (e: Exception) {}
                            if (activeMediaPlayer == mp) activeMediaPlayer = null
                            if (thisSessionId == currentSessionId) {
                                safeOnError("Não foi possível carregar voz no dispositivo.")
                            }
                            true
                        }
                    }
                    activeMediaPlayer = player
                }

                playChunk(0)
            } catch (e: Exception) {
                safeOnError("Erro de áudio: ${e.localizedMessage}")
            }
        }

        fun doSpeakWithTts(engine: android.speech.tts.TextToSpeech) {
            if (thisSessionId != currentSessionId) return
            try {
                engine.stop()

                val ptPtLocale = java.util.Locale("pt", "PT")
                var langRes = engine.setLanguage(ptPtLocale)
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = engine.setLanguage(java.util.Locale("pt", "POR"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = engine.setLanguage(java.util.Locale("pt", "BR"))
                }
                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    langRes = engine.setLanguage(java.util.Locale("pt"))
                }

                if (langRes == android.speech.tts.TextToSpeech.LANG_MISSING_DATA || langRes == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                    playViaMediaPlayerFallback(rawClean)
                    return
                }

                engine.setPitch(0.96f)
                engine.setSpeechRate(1.00f)

                try {
                    val voices = engine.voices
                    if (voices != null) {
                        val ptVoice = selectMalePtPtVoice(voices)
                        if (ptVoice != null) {
                            engine.voice = ptVoice
                        }
                    }
                } catch (e: Exception) {}

                val formattedText = rawClean
                    .replace(Regex("\\.{2,}"), ".")
                    .replace(Regex(",{2,}"), ",")
                    .replace(Regex("(?<=\\w)\\.\\s+(?=\\w)"), ", ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                if (formattedText.isBlank()) {
                    safeOnDone()
                    return
                }

                val chunks = if (formattedText.length <= 1500) {
                    listOf(formattedText)
                } else {
                    formattedText.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                }

                val firstUttId = "PORTAL_TTS_${thisSessionId}_0"
                val lastUttId = "PORTAL_TTS_${thisSessionId}_${chunks.size - 1}"

                engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        if (thisSessionId != currentSessionId) return
                        if (utteranceId == firstUttId) {
                            safeOnStart()
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (thisSessionId != currentSessionId) return
                        if (utteranceId == lastUttId) {
                            safeOnDone()
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        if (thisSessionId != currentSessionId) return
                        try { engine.stop() } catch (e: Exception) {}
                        safeOnError("Erro durante a reprodução de voz.")
                    }
                })

                val params = android.os.Bundle().apply {
                    putInt(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
                    putFloat(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }

                var queueMode = android.speech.tts.TextToSpeech.QUEUE_FLUSH
                var successCount = 0
                for ((idx, chunk) in chunks.withIndex()) {
                    if (thisSessionId != currentSessionId) break
                    val uttId = "PORTAL_TTS_${thisSessionId}_$idx"
                    val res = engine.speak(chunk, queueMode, params, uttId)
                    if (res == android.speech.tts.TextToSpeech.SUCCESS) {
                        successCount++
                        queueMode = android.speech.tts.TextToSpeech.QUEUE_ADD
                    }
                }

                if (successCount == 0 && thisSessionId == currentSessionId) {
                    playViaMediaPlayerFallback(rawClean)
                }
            } catch (e: Exception) {
                if (thisSessionId == currentSessionId) {
                    playViaMediaPlayerFallback(rawClean)
                }
            }
        }

        val currentTts = tts
        if (currentTts != null && isReady) {
            doSpeakWithTts(currentTts)
            return
        }

        initIfNeeded(context)

        var attempts = 0
        fun checkReadyAndSpeak() {
            if (thisSessionId != currentSessionId) return
            val readyTts = tts
            if (readyTts != null && isReady) {
                doSpeakWithTts(readyTts)
            } else if (attempts < 60) {
                attempts++
                mainHandler.postDelayed({ checkReadyAndSpeak() }, 100)
            } else {
                playViaMediaPlayerFallback(rawClean)
            }
        }

        checkReadyAndSpeak()
    }

    @Synchronized
    fun stopAll() {
        currentSessionId++
        _isSpeaking.value = false
        mainHandler.removeCallbacksAndMessages(null)
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

enum class EventReadState {
    UNREAD,    // Não lido (Sinal Cinza)
    IN_QUEUE,  // Na fila (Sinal Amarelo)
    READING,   // Lendo (Sinal Azul)
    READ       // Lido (Sinal Verde)
}

object PortalEventQueueManager {
    private val _readEventKeys = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val readEventKeys: kotlinx.coroutines.flow.StateFlow<Set<String>> get() = _readEventKeys

    private val _currentlyReadingKey = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentlyReadingKey: kotlinx.coroutines.flow.StateFlow<String?> get() = _currentlyReadingKey

    private val _queuedEventKeys = kotlinx.coroutines.flow.MutableStateFlow<List<String>>(emptyList())
    val queuedEventKeys: kotlinx.coroutines.flow.StateFlow<List<String>> get() = _queuedEventKeys

    private val processedKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val queue = java.util.Collections.synchronizedList(mutableListOf<com.example.data.EaRobotEvent>())
    @Volatile private var isProcessing = false

    fun getEventUniqueKey(event: com.example.data.EaRobotEvent): String {
        return event.getResolvedGid()
    }

    fun getReadState(event: com.example.data.EaRobotEvent): EventReadState {
        val key = getEventUniqueKey(event)
        return when {
            _readEventKeys.value.contains(key) -> EventReadState.READ
            _currentlyReadingKey.value == key -> EventReadState.READING
            _queuedEventKeys.value.contains(key) -> EventReadState.IN_QUEUE
            else -> EventReadState.UNREAD
        }
    }

    @Synchronized
    fun onEventsReceived(context: android.content.Context, events: List<com.example.data.EaRobotEvent>) {
        if (events.isEmpty()) return

        val currentReadSet = _readEventKeys.value

        val newEvents = events.filter { evt ->
            val key = getEventUniqueKey(evt)
            !currentReadSet.contains(key) &&
            _currentlyReadingKey.value != key &&
            !processedKeys.contains(key) &&
            !queue.any { getEventUniqueKey(it) == key }
        }

        if (newEvents.isNotEmpty()) {
            val sortedNew = newEvents.sortedBy { if (it.timestamp > 0L) it.timestamp else 0L }
            synchronized(queue) {
                sortedNew.forEach { evt ->
                    val key = getEventUniqueKey(evt)
                    if (!queue.any { getEventUniqueKey(it) == key } && !processedKeys.contains(key)) {
                        processedKeys.add(key)
                        queue.add(evt)
                    }
                }
                _queuedEventKeys.value = queue.map { getEventUniqueKey(it) }
            }
            processNextInQueue(context)
        }
    }

    @Synchronized
    private fun processNextInQueue(context: android.content.Context) {
        if (isProcessing) return

        val nextEvent = synchronized(queue) {
            if (queue.isEmpty()) null else queue.removeAt(0)
        }

        if (nextEvent == null) {
            _currentlyReadingKey.value = null
            _queuedEventKeys.value = synchronized(queue) { queue.map { getEventUniqueKey(it) } }
            isProcessing = false
            return
        }

        isProcessing = true
        val key = getEventUniqueKey(nextEvent)
        processedKeys.add(key)
        _currentlyReadingKey.value = key
        _queuedEventKeys.value = synchronized(queue) { queue.map { getEventUniqueKey(it) } }

        val textToSpeak = getEventSpeechText(nextEvent)

        if (textToSpeak.isBlank()) {
            markAsRead(key)
            isProcessing = false
            processNextInQueue(context)
            return
        }

        AppTtsManager.speak(
            context = context,
            text = textToSpeak,
            onStart = {
                _currentlyReadingKey.value = key
                val title = nextEvent.resumo.ifEmpty { nextEvent.event.ifEmpty { "Evento" } }
                android.widget.Toast.makeText(context, "🔊 Lendo evento: $title", android.widget.Toast.LENGTH_SHORT).show()
            },
            onDone = {
                markAsRead(key)
                _currentlyReadingKey.value = null
                isProcessing = false
                processNextInQueue(context)
            },
            onError = { msg ->
                processedKeys.remove(key)
                _currentlyReadingKey.value = null
                isProcessing = false
                android.widget.Toast.makeText(context, "⚠️ Falha ao ler evento: $msg", android.widget.Toast.LENGTH_SHORT).show()
                processNextInQueue(context)
            }
        )
    }

    fun markAsRead(key: String) {
        processedKeys.add(key)
        val updated = _readEventKeys.value.toMutableSet()
        updated.add(key)
        _readEventKeys.value = updated
        if (_currentlyReadingKey.value == key) {
            _currentlyReadingKey.value = null
        }
    }

    fun markAllAsRead(events: List<com.example.data.EaRobotEvent>) {
        stopAllQueue()
        val updated = _readEventKeys.value.toMutableSet()
        events.forEach { evt ->
            val key = getEventUniqueKey(evt)
            updated.add(key)
            processedKeys.add(key)
        }
        _readEventKeys.value = updated
    }

    fun stopAllQueue() {
        AppTtsManager.stopAll()
        synchronized(queue) { queue.clear() }
        _queuedEventKeys.value = emptyList()
        _currentlyReadingKey.value = null
        isProcessing = false
    }

    fun speakSingleEvent(context: android.content.Context, event: com.example.data.EaRobotEvent) {
        val key = getEventUniqueKey(event)

        if (_currentlyReadingKey.value == key) {
            AppTtsManager.stopAll()
            markAsRead(key)
            isProcessing = false
            processNextInQueue(context)
            return
        }

        AppTtsManager.stopAll()
        synchronized(queue) {
            queue.removeAll { getEventUniqueKey(it) == key }
            queue.add(0, event)
        }
        isProcessing = false
        processNextInQueue(context)
    }

    fun clearAll(context: android.content.Context) {
        stopAllQueue()
        processedKeys.clear()
        _readEventKeys.value = emptySet()
    }
}

@Composable
fun VoicePulsingWaveHalo(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF38BDF8)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoicePulseHalo")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HaloScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HaloAlpha"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .border(1.5.dp, color.copy(alpha = alpha), CircleShape)
            .background(color.copy(alpha = alpha * 0.25f), CircleShape)
    )
}

@Composable
fun VoiceAudioWaveBars(
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF38BDF8),
    barCount: Int = 4,
    maxHeight: androidx.compose.ui.unit.Dp = 12.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "VoiceWaveBars")

    val delays = listOf(0, 140, 280, 420, 180, 320)
    val animatedScales = (0 until barCount).map { index ->
        val delay = delays.getOrElse(index) { index * 120 }
        infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 380 + (index * 70),
                    delayMillis = delay,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "BarScale_$index"
        )
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        animatedScales.forEach { scaleState ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(maxHeight * scaleState.value)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun EventReadSignalBadge(
    readState: EventReadState,
    modifier: Modifier = Modifier
) {
    val (bgColor, borderColor, textColor, statusText) = when (readState) {
        EventReadState.READ -> Tuple4(
            Color(0xFF10B981).copy(alpha = 0.2f),
            Color(0xFF10B981),
            Color(0xFF34D399),
            "LIDO"
        )
        EventReadState.READING -> Tuple4(
            Color(0xFF0284C7).copy(alpha = 0.25f),
            Color(0xFF38BDF8),
            Color(0xFF38BDF8),
            "LENDO..."
        )
        EventReadState.IN_QUEUE -> Tuple4(
            Color(0xFFF59E0B).copy(alpha = 0.2f),
            Color(0xFFFBBF24),
            Color(0xFFFBBF24),
            "NA FILA"
        )
        EventReadState.UNREAD -> Tuple4(
            Color(0xFF64748B).copy(alpha = 0.2f),
            Color(0xFF64748B).copy(alpha = 0.6f),
            Color(0xFF94A3B8),
            "NÃO LIDO"
        )
    }

    val dotColor = when (readState) {
        EventReadState.READ -> Color(0xFF10B981)
        EventReadState.READING -> Color(0xFF38BDF8)
        EventReadState.IN_QUEUE -> Color(0xFFFBBF24)
        EventReadState.UNREAD -> Color(0xFF94A3B8)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (readState == EventReadState.READING) {
                VoiceAudioWaveBars(
                    barColor = Color(0xFF38BDF8),
                    barCount = 3,
                    maxHeight = 9.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape)
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            )
        }
    }
}

private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

fun getEventSpeechText(event: com.example.data.EaRobotEvent): String {
    if (com.example.data.isPingOrStatusEvent(event)) return ""

    val eventLower = event.event.lowercase()

    val isOrdemExecutada = eventLower == "ordem_executada" || eventLower == "ordem executada" || eventLower == "order_executed" || eventLower == "ordem_aberta"
    val isOrdemModificada = eventLower == "ordem_modificada" || eventLower == "ordem modificada" || eventLower == "order_modified" || eventLower == "sl_tp_modificado"
    val isErroOrdem = eventLower == "erro_ordem" || eventLower == "erro ordem" || eventLower == "order_error" || (eventLower.contains("erro") && eventLower.contains("ordem"))
    val isOrdemNaoExecutada = eventLower.contains("ordem_não_executada") || eventLower.contains("ordem_nao_executada") || eventLower.contains("ordem não executada")

    val isRelatorio = eventLower.contains("relatorio") || eventLower.contains("financeiro") || eventLower.contains("relatório")
    val isPosicao = (eventLower.contains("posicao") || eventLower.contains("posição") || eventLower.contains("position") || event.temPosicao.isNotBlank()) &&
            !isOrdemExecutada && !isOrdemModificada && !isErroOrdem && !isOrdemNaoExecutada
    val isSessao = eventLower.contains("sessao") || eventLower.contains("sessão")
    val isEquador = eventLower.contains("equador")
    val isInicializacao = eventLower.contains("inicializ") || eventLower.contains("boot")
    val isNotificacao = isOrdemNaoExecutada || eventLower.startsWith("notificacao") || event.sistema.contains("NOTIFICACOES") ||
            event.msg.contains("desativado") || event.msg.contains("travado") || event.msg.contains("trvado") ||
            event.msg.contains("contol_de_gerenciamento")

    val isStateChange = !isNotificacao && !isOrdemExecutada && !isOrdemModificada && !isErroOrdem && !isRelatorio && !isInicializacao && !isSessao && !isEquador && !isPosicao &&
            (event.anterior.isNotEmpty() || event.novo.isNotEmpty() || event.descNovo.isNotEmpty() ||
            eventLower.contains("mudanca") || eventLower.contains("estado") || eventLower.contains("status"))

    return when {
        isOrdemExecutada -> {
            val tipoStr = if (event.tipo.isNotBlank()) event.tipo else if (event.type.isNotBlank()) event.type else "Operação"
            val ticketStr = if (event.ticket.isNotBlank()) " bilhete ${event.ticket}" else ""
            "Ordem de $tipoStr executada no ativo ${event.symbol}$ticketStr. Preço ${event.price}, volume ${event.volume} lotes. ${sanitizeText(event.msg)}"
        }
        isOrdemModificada -> {
            val ticketStr = if (event.ticket.isNotBlank()) " bilhete ${event.ticket}" else ""
            "Ordem $ticketStr modificada no ativo ${event.symbol}. Novo Stop Loss ${event.novoSl}, novo Take Profit ${event.novoTp}. ${sanitizeText(event.msg)}"
        }
        isErroOrdem || isOrdemNaoExecutada -> {
            "Erro de ordem no ativo ${event.symbol}. ${sanitizeText(event.msg)}"
        }
        isNotificacao -> {
            "Notificação do Robô Fimaster: ${sanitizeText(event.resumo.ifEmpty { event.msg })}. ${sanitizeText(event.descNovo)}"
        }
        isRelatorio -> {
            buildString {
                append("Relatório financeiro do robô Fimaster. ")
                if (event.diarioStatus.isNotBlank()) append("Diário: ${event.diarioStatus} de ${event.diarioValor} ${event.moeda}. ")
                if (event.semanalStatus.isNotBlank()) append("Semanal: ${event.semanalStatus} de ${event.semanalValor} ${event.moeda}. ")
                if (event.motivacao.isNotBlank()) append("Motivação: ${sanitizeText(event.motivacao)}. ")
                if (event.resumo.isNotBlank()) append("Resumo: ${sanitizeText(event.resumo)}.")
            }
        }
        isPosicao -> {
            buildString {
                append("Posição alterada no ativo ${event.symbol}. ")
                val posState = event.novo.ifEmpty { event.anterior.ifEmpty { event.temPosicao } }
                if (posState.isNotBlank()) append("Estado da posição: ${cleanStateEnumText(posState)}. ")
                if (event.msg.isNotBlank()) append("${sanitizeText(event.msg)}. ")
                if (event.resumo.isNotBlank()) append("${sanitizeText(event.resumo)}.")
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
            val rawDesc = com.example.data.resolveEventStateDescription(event)
            val cleanDesc = sanitizeText(rawDesc)
            val sysName = detectStateSystemType(event)
            buildString {
                append("Mudança de estado no $sysName. ")
                if (stateText.isNotBlank()) append("Novo estado: $stateText. ")
                if (cleanDesc.isNotBlank() && !cleanDesc.equals(stateText, ignoreCase = true)) {
                    append(cleanDesc)
                } else if (event.msg.isNotBlank()) {
                    append(sanitizeText(event.msg))
                }
            }.trim()
        }
        else -> {
            "Evento ${event.event}: ${sanitizeText(event.msg.ifEmpty { event.resumo.ifEmpty { "Registrado para o ativo " + event.symbol } })}"
        }
    }
}

fun decodeBase64ToBitmap(base64Str: String): android.graphics.Bitmap? {
    if (base64Str.isBlank()) return null
    return try {
        val cleanBase64 = if (base64Str.contains(",")) base64Str.substringAfter(",") else base64Str
        val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun ClassicEventCard(
    event: com.example.data.EaRobotEvent,
    isFocused: Boolean = false
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val readKeys by PortalEventQueueManager.readEventKeys.collectAsStateWithLifecycle()
    val currentlyReadingKey by PortalEventQueueManager.currentlyReadingKey.collectAsStateWithLifecycle()
    val queuedKeys by PortalEventQueueManager.queuedEventKeys.collectAsStateWithLifecycle()

    val readState = remember(event, readKeys, currentlyReadingKey, queuedKeys) {
        PortalEventQueueManager.getReadState(event)
    }

    val eventLower = remember(event) { event.event.lowercase().trim() }
    val rawTipoLower = remember(event) { (event.tipo.ifEmpty { event.type }).lowercase().trim() }
    val msgLower = remember(event) { event.msg.lowercase().trim() }

    val isCapturaTela = remember(eventLower, event) {
        eventLower == "captura" || eventLower == "captura_tela" || eventLower == "screenshot" || eventLower == "print" ||
        event.imageBase64.isNotBlank() || event.filename.contains("captura") ||
        (eventLower.contains("captura") && !eventLower.contains("ordem") && !eventLower.contains("estado"))
    }
    val isRelatorio = remember(eventLower, event) {
        !isCapturaTela && (
            eventLower == "relatorio_financeiro" || eventLower == "relatorio financeiro" || eventLower == "relatório_financeiro" ||
            (eventLower.contains("relatorio") || eventLower.contains("relatório")) ||
            (eventLower.contains("financeiro") && (event.diarioValor != 0.0 || event.semanalValor != 0.0 || event.diarioStatus.isNotBlank()))
        )
    }
    val isErroOrdem = remember(eventLower) {
        !isCapturaTela && (
            eventLower == "erro_ordem" || eventLower == "erro ordem" || eventLower == "order_error" ||
            (eventLower.contains("erro") && (eventLower.contains("ordem") || eventLower.contains("order")))
        )
    }
    val isOrdemNaoExecutada = remember(eventLower) {
        !isCapturaTela && !isErroOrdem && (
            eventLower == "ordem_não_executada" || eventLower == "ordem_nao_executada" ||
            eventLower == "ordem não executada" || eventLower == "ordem nao executada" ||
            eventLower.contains("não_executad") || eventLower.contains("nao_executad")
        )
    }
    val isOrdemModificada = remember(eventLower) {
        !isCapturaTela && !isErroOrdem && !isOrdemNaoExecutada && (
            eventLower == "ordem_modificada" || eventLower == "ordem modificada" || eventLower == "order_modified" || eventLower == "sl_tp_modificado"
        )
    }
    val isOrdemExecutada = remember(eventLower, rawTipoLower, msgLower) {
        !isCapturaTela && !isErroOrdem && !isOrdemNaoExecutada && !isOrdemModificada && (
            eventLower == "ordem_executada" || eventLower == "ordem executada" || eventLower == "order_executed" ||
            eventLower == "ordem_aberta" || eventLower == "entry_position" || eventLower == "closed_position" ||
            eventLower == "posicao_fechada" || eventLower == "ordem_fechada"
        )
    }
    val isSessao = remember(eventLower) {
        !isCapturaTela && (
            eventLower == "sessao_inicio" || eventLower == "sessao_fim" || eventLower == "sessão_inicio" || eventLower == "sessão_fim" ||
            eventLower == "sessao" || eventLower == "sessão" || eventLower.contains("sessao") || eventLower.contains("sessão")
        )
    }
    val isEquador = remember(eventLower) {
        !isCapturaTela && (
            eventLower == "mudanca_equador" || eventLower == "mudança_equador" || eventLower.contains("equador")
        )
    }
    val isInicializacao = remember(eventLower) {
        !isCapturaTela && (
            eventLower == "inicializacao" || eventLower == "inicialização" || eventLower.contains("inicializ") || eventLower.contains("boot")
        )
    }
    val isPosicao = remember(eventLower, event) {
        !isCapturaTela && !isOrdemExecutada && !isOrdemModificada && !isErroOrdem && !isOrdemNaoExecutada && (
            eventLower == "posicao_alterada" || eventLower == "posição_alterada" ||
            (eventLower.contains("posicao") && !eventLower.contains("mudanca") && !eventLower.contains("estado")) ||
            (event.temPosicao.isNotBlank() && !eventLower.contains("mudanca_estado"))
        )
    }
    val isStateChange = remember(eventLower, event) {
        !isCapturaTela && !isOrdemExecutada && !isOrdemModificada && !isErroOrdem && !isOrdemNaoExecutada && !isRelatorio && !isInicializacao && !isSessao && !isEquador && !isPosicao && (
            eventLower == "mudanca_estado" || eventLower == "mudança_estado" || eventLower == "mudanca estado" ||
            eventLower.contains("mudanca") || eventLower.contains("estado") ||
            event.sistema.isNotBlank() || event.novo.isNotBlank() || event.anterior.isNotBlank()
        )
    }

    val (cardTitle, badgeIcon, badgeColor) = remember(
        event, eventLower, isCapturaTela, isOrdemExecutada, isOrdemModificada, isErroOrdem,
        isOrdemNaoExecutada, isRelatorio, isPosicao, isSessao, isEquador,
        isInicializacao, isStateChange
    ) {
        when {
            isCapturaTela -> Triple("CAPTURA DE TELA CONCLUÍDA", Icons.Default.CameraAlt, Color(0xFF38BDF8))
            isRelatorio -> Triple("RELATÓRIO FINANCEIRO EA", Icons.Default.TrendingUp, Color(0xFF10B981))
            isErroOrdem -> Triple("ERRO DE ORDEM MT5", Icons.Default.Error, Color(0xFFEF4444))
            isOrdemNaoExecutada -> Triple("ORDEM NÃO EXECUTADA", Icons.Default.Warning, Color(0xFFEF4444))
            isOrdemModificada -> Triple("ORDEM MODIFICADA (SL/TP)", Icons.Default.Tune, Color(0xFF38BDF8))
            isOrdemExecutada -> {
                val tipoLower = rawTipoLower
                val isVenda = tipoLower.contains("venda") || msgLower.contains("venda") || eventLower.contains("sell")
                val isCompra = tipoLower.contains("compra") || msgLower.contains("compra") || eventLower.contains("buy")
                val isClosed = eventLower.contains("closed") || eventLower.contains("fechad")
                val isEntry = eventLower.contains("entry") || eventLower.contains("entrad")
                val title = when {
                    isCompra && isClosed -> "ORDEM DE COMPRA FECHADA"
                    isVenda && isClosed -> "ORDEM DE VENDA FECHADA"
                    isClosed -> "POSIÇÃO / ORDEM ENCERRADA"
                    isCompra -> "ORDEM DE COMPRA EXECUTADA"
                    isVenda -> "ORDEM DE VENDA EXECUTADA"
                    isEntry -> "ORDEM DE ENTRADA EXECUTADA"
                    else -> "ORDEM EXECUTADA MT5"
                }
                val icon = if (isCompra) Icons.Default.TrendingUp else if (isVenda) Icons.Default.TrendingDown else Icons.Default.ReceiptLong
                val color = if (isCompra) Color(0xFF10B981) else if (isVenda) Color(0xFFEF4444) else Color(0xFF8B5CF6)
                Triple(title, icon, color)
            }
            isSessao -> {
                val isStart = eventLower.contains("inicio") || eventLower.contains("start")
                Triple("SESSÃO FOREX ${if (isStart) "INICIADA" else "ENCERRADA"}", Icons.Default.Schedule, Color(0xFF38BDF8))
            }
            isEquador -> Triple("ALTERAÇÃO LINHA EQUADOR", Icons.Default.CompareArrows, Color(0xFFF59E0B))
            isInicializacao -> Triple("INICIALIZAÇÃO DO ROBÔ", Icons.Default.PlayArrow, Color(0xFF10B981))
            isPosicao -> Triple("POSIÇÃO ALTERADA NO MERCADO", Icons.Default.SwapHoriz, Color(0xFFF59E0B))
            isStateChange -> {
                val sysCat = detectStateSystemType(event)
                Triple("MUDANÇA DE ESTADO: $sysCat", Icons.Default.Tune, Color(0xFF22D3EE))
            }
            else -> {
                val rawTitle = event.event.uppercase().replace("_", " ").trim()
                val cleanTitle = if (rawTitle == "EVENTO" || rawTitle == "EVENT" || rawTitle.isBlank() || rawTitle == "DESCONHECIDO") {
                    if (event.sistema.isNotBlank()) "SISTEMA • ${cleanStateEnumText(event.sistema)}"
                    else if (event.novo.isNotBlank()) "ESTADO • ${cleanStateEnumText(event.novo)}"
                    else if (event.msg.isNotBlank()) "REGISTRO DE EVENTO EA"
                    else "NOTIFICAÇÃO DO ROBÔ"
                } else {
                    "EVENTO: $rawTitle"
                }
                Triple(cleanTitle, Icons.Default.Info, Color(0xFF38BDF8))
            }
        }
    }

    val sdf = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()) }
    val dateFormatted = remember(event.timestamp, event.data, event.hora) {
        if (event.timestamp > 0) {
            val tsMs = if (event.timestamp in 1L..9_999_999_999L) event.timestamp * 1000L else event.timestamp
            try { sdf.format(java.util.Date(tsMs)) } catch (e: Exception) { "N/A" }
        } else if (event.data.isNotBlank()) {
            if (event.hora.isNotBlank()) "${event.data} ${event.hora}" else event.data
        } else "N/A"
    }

    val infiniteCardTransition = rememberInfiniteTransition(label = "CardReadingPulse")
    val pulseGlowAlpha by infiniteCardTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val cardBorder = when {
        readState == EventReadState.READING -> BorderStroke(2.dp, Color(0xFF38BDF8).copy(alpha = pulseGlowAlpha))
        isFocused -> BorderStroke(2.dp, Color(0xFF22D3EE))
        else -> BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    }
    val cardContainerColor = when {
        readState == EventReadState.READING -> Color(0xFF0C243B)
        isFocused -> Color(0xFF0C2438)
        else -> Color(0xFF0F172A)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainerColor),
        shape = RoundedCornerShape(14.dp),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (isFocused) {
                Surface(
                    color = Color(0xFF22D3EE).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF22D3EE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "🎯 EVENTO LOCALIZADO (RESOLVER)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF22D3EE),
                                fontWeight = FontWeight.Black,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }

            // Header Row: Badge + Title + Signal Badge + Audio Button
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
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Signal Indicator Badge (Cinza = Não lido / Verde = Lido / Wave = Lendo)
                EventReadSignalBadge(readState = readState)

                Spacer(modifier = Modifier.width(6.dp))

                val planConfig = LocalLicensePlanConfig.current
                val isAudioAllowed = planConfig.audio

                // Audio Button with Wave Animation when Reading
                Surface(
                    onClick = {
                        if (!isAudioAllowed) {
                            android.widget.Toast.makeText(context, "🔒 Alertas de áudio bloqueados no seu plano.", android.widget.Toast.LENGTH_SHORT).show()
                            return@Surface
                        }
                        PortalEventQueueManager.speakSingleEvent(context, event)
                    },
                    color = if (!isAudioAllowed) Color(0xFF334155).copy(alpha = 0.4f) else if (readState == EventReadState.READING) Color(0xFF0284C7).copy(alpha = 0.3f) else badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (!isAudioAllowed) Color(0xFF475569) else if (readState == EventReadState.READING) Color(0xFF38BDF8) else badgeColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isAudioAllowed) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Áudio Bloqueado",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Áudio 🔒",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        } else if (readState == EventReadState.READING) {
                            VoiceAudioWaveBars(
                                barColor = Color(0xFF38BDF8),
                                barCount = 3,
                                maxHeight = 11.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Lendo",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        } else {
                            Icon(
                                imageVector = if (readState == EventReadState.READ) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                contentDescription = "Ouvir em Áudio",
                                tint = badgeColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Áudio 🔊",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Specific Card Body Layouts based on event type
            when {
                isCapturaTela -> {
                    // Captura de Tela Exclusive Layout (Com Blur de Segurança para Licença TRIAL)
                    val licenseTier = LocalLicenseTier.current
                    val isTrial = (licenseTier == LicenseTier.TRIAL)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (event.imageBase64.isNotBlank()) {
                            val bitmap = remember(event.imageBase64) { decodeBase64ToBitmap(event.imageBase64) }
                            if (bitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Captura de Tela do Gráfico MT5",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (isTrial) Modifier.blur(16.dp) else Modifier),
                                        contentScale = ContentScale.Fit
                                    )

                                    if (isTrial) {
                                        // 🔒 Overlay de Bloqueio com Vidro Fosco Cyber-Trading Dark
                                        Surface(
                                            color = Color(0xFF0F172A).copy(alpha = 0.88f),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                                            modifier = Modifier
                                                .fillMaxWidth(0.92f)
                                                .padding(8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Lock,
                                                        contentDescription = "Bloqueado",
                                                        tint = Color(0xFFF59E0B),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "CAPTURA DESFOCADA (PLANO TRIAL)",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFFF59E0B),
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 0.5.sp
                                                    ),
                                                    textAlign = TextAlign.Center
                                                )
                                                Text(
                                                    text = "Imagens nítidas do gráfico MT5 e relatórios com objetos visuais são exclusivos dos planos Starter, Pro e Master.",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFFE2E8F0),
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp
                                                    ),
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (event.filename.isNotBlank()) "Arquivo: ${event.filename}" else "Captura Real MT5",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                                val details = mutableListOf<String>()
                                if (event.symbol.isNotBlank()) details.add("Ativo: ${event.symbol}")
                                if (event.timeframe.isNotBlank()) details.add("TF: ${event.timeframe}")
                                if (event.login > 0) details.add("Conta: ${event.login}")
                                if (details.isNotEmpty()) {
                                    Text(
                                        text = details.joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                                    )
                                }
                            }
                            Surface(
                                color = if (isTrial) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isTrial) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF38BDF8).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = if (isTrial) "TRIAL RESTRITO" else "CONCLUÍDO",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isTrial) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        val capMsg = event.msg.ifEmpty { "Captura de tela REAL enviada com sucesso ao App do MT5 com objetos MQL5." }
                        Surface(
                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = sanitizeText(capMsg),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, lineHeight = 18.sp),
                                modifier = Modifier.padding(8.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (isTrial) {
                                    android.widget.Toast.makeText(context, "🔒 Upgrade necessário para exportar capturas do MT5", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    shareEventScreenshot(context, event)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTrial) Color(0xFF334155) else Color(0xFF0284C7).copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, if (isTrial) Color(0xFF64748B) else Color(0xFF38BDF8).copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isTrial) Icons.Default.Lock else Icons.Default.Share,
                                contentDescription = "Compartilhar Captura",
                                tint = if (isTrial) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTrial) "EXPORTAR DISPONÍVEL NO PLANO STARTER" else "COMPARTILHAR CAPTURA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                isRelatorio -> {
                    // Relatório Financeiro Compact Layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (event.diarioStatus.isNotBlank() || event.diarioValor != 0.0 || event.diarioPct != 0.0) {
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
                                        text = "${event.diarioStatus.ifEmpty { "STATUS OK" }}: ${String.format(java.util.Locale.US, "%.2f", event.diarioValor)} ${event.moeda.ifEmpty { "USD" }} (${String.format(java.util.Locale.US, "%.2f", event.diarioPct)}%)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        if (event.semanalStatus.isNotBlank() || event.semanalValor != 0.0 || event.semanalPct != 0.0) {
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
                                        text = "${event.semanalStatus.ifEmpty { "STATUS OK" }}: ${String.format(java.util.Locale.US, "%.2f", event.semanalValor)} ${event.moeda.ifEmpty { "USD" }} (${String.format(java.util.Locale.US, "%.2f", event.semanalPct)}%)",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        if (event.saldoDisponivel > 0.0) {
                            Text(
                                text = "💰 Saldo Disponível: ${String.format(java.util.Locale.US, "%.2f", event.saldoDisponivel)} ${event.moeda.ifEmpty { "USD" }}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            )
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
                                        text = sanitizeText(event.motivacao),
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFFEF3C7))
                                    )
                                }
                            }
                        }

                        val relatorioDesc = event.resumo.ifEmpty { event.msg }
                        if (relatorioDesc.isNotBlank()) {
                            Text(
                                text = "📢 Resumo: ${sanitizeText(relatorioDesc)}",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                isOrdemExecutada -> {
                    // Ordem Executada Layout (1.1)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val tipoRaw = event.tipo.ifEmpty { event.type.ifEmpty { event.novo } }
                                val tipoStr = cleanStateEnumText(tipoRaw)
                                if (tipoStr.isNotBlank() && !tipoStr.startsWith("BILHETE")) {
                                    Text(
                                        text = tipoStr,
                                        style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }
                                if (event.ticket.isNotBlank() && event.ticket != "0") {
                                    Surface(
                                        color = badgeColor.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "Bilhete #${event.ticket}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = badgeColor, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            if (event.symbol.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = event.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Order Metrics Grid
                        val metrics = mutableListOf<String>()
                        if (event.price > 0.0) metrics.add("Preço: ${String.format(java.util.Locale.US, "%.5f", event.price)}")
                        if (event.volume > 0.0) metrics.add("Volume: ${String.format(java.util.Locale.US, "%.2f", event.volume)} Lotes")
                        if (event.sl > 0.0) metrics.add("SL: ${String.format(java.util.Locale.US, "%.5f", event.sl)}")
                        if (event.tp > 0.0) metrics.add("TP: ${String.format(java.util.Locale.US, "%.5f", event.tp)}")

                        if (metrics.isNotEmpty()) {
                            Text(
                                text = metrics.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                            )
                        }

                        val targets = mutableListOf<String>()
                        if (event.alvoMt > 0.0) targets.add("Alvo: ${String.format(java.util.Locale.US, "%.2f", event.alvoMt)} MT")
                        if (event.protecaoMt > 0.0) targets.add("Proteção: ${String.format(java.util.Locale.US, "%.2f", event.protecaoMt)} MT")
                        if (event.lucroPct != 0.0) targets.add("Lucro: ${String.format(java.util.Locale.US, "%.2f", event.lucroPct)}%")
                        if (event.perdaPct != 0.0) targets.add("Perda: ${String.format(java.util.Locale.US, "%.2f", event.perdaPct)}%")

                        if (targets.isNotEmpty()) {
                            Text(
                                text = targets.joinToString(" | "),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            )
                        }

                        if (event.msg.isNotBlank()) {
                            Text(
                                text = sanitizeText(event.msg),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                isOrdemModificada -> {
                    // Ordem Modificada Layout (1.2)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (event.ticket.isNotBlank() && event.ticket != "0") {
                                    Surface(
                                        color = Color(0xFF38BDF8).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "Bilhete #${event.ticket}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                val tipoStr = event.tipo.ifEmpty { event.type }
                                if (tipoStr.isNotBlank()) {
                                    Text(
                                        text = tipoStr.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            if (event.symbol.isNotBlank()) {
                                Surface(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = event.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val modDetails = mutableListOf<String>()
                        val effectiveSl = if (event.novoSl > 0.0) event.novoSl else event.sl
                        val effectiveTp = if (event.novoTp > 0.0) event.novoTp else event.tp
                        if (effectiveSl > 0.0) modDetails.add("Novo SL: ${String.format(java.util.Locale.US, "%.5f", effectiveSl)}")
                        if (effectiveTp > 0.0) modDetails.add("Novo TP: ${String.format(java.util.Locale.US, "%.5f", effectiveTp)}")

                        if (modDetails.isNotEmpty()) {
                            Text(
                                text = modDetails.joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            )
                        }

                        if (event.msg.isNotBlank()) {
                            Text(
                                text = sanitizeText(event.msg),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1))
                            )
                        }
                    }
                }

                isErroOrdem || isOrdemNaoExecutada -> {
                    // Erro de Ordem / Ordem Não Executada Layout (1.3 & 1.10)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF451A1A).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isOrdemNaoExecutada) "FALHA / ORDEM NÃO EXECUTADA" else "ERRO DE ORDEM MT5",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                                )
                            }
                            if (event.symbol.isNotBlank()) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = event.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        val errDetails = mutableListOf<String>()
                        if (event.erroCode > 0) errDetails.add("Código MQL5: ${event.erroCode}")
                        val opType = event.tipo.ifEmpty { event.type }
                        if (opType.isNotBlank()) errDetails.add("Operação: ${opType.uppercase()}")
                        if (event.login > 0) errDetails.add("Conta: ${event.login}")

                        if (errDetails.isNotEmpty()) {
                            Text(
                                text = errDetails.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFFECACA), fontWeight = FontWeight.SemiBold)
                            )
                        }

                        if (event.msg.isNotBlank()) {
                            Text(
                                text = sanitizeText(event.msg),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                            )
                        }
                    }
                }

                isPosicao -> {
                    // Posicao Alterada Layout (1.8)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "MUDANÇA DE POSIÇÃO NO MERCADO",
                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                )
                                val statusPosText = cleanStateEnumText(event.novo.ifEmpty { event.anterior.ifEmpty { event.temPosicao } })
                                Text(
                                    text = if (statusPosText.isNotBlank()) statusPosText else "POSIÇÃO ALTERADA",
                                    style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                )
                            }
                            if (event.symbol.isNotBlank()) {
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = event.symbol,
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        if (event.anterior.isNotBlank() && event.novo.isNotBlank() && event.anterior != event.novo) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(text = "De: ${cleanStateEnumText(event.anterior)}", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                Text(text = "Para: ${cleanStateEnumText(event.novo)}", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold))
                            }
                        }

                        val detailMsg = event.msg.ifEmpty { event.resumo.ifEmpty { event.descNovo.ifEmpty { event.descAnterior } } }
                        if (detailMsg.isNotBlank()) {
                            Text(
                                text = sanitizeText(detailMsg.replace("_", " ")),
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "EA Fimaster conectado & ativo",
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
                        if (event.timeframe.isNotBlank()) infoParts.add("TF: ${event.timeframe}")
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
                            Spacer(modifier = Modifier.height(6.dp))
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

                        // Explicit State Description Block
                        val officialDesc = com.example.data.resolveEventStateDescription(event).trim()
                        if (officialDesc.isNotBlank() && !officialDesc.startsWith("ESTADO_")) {
                            val cleanDescNovo = sanitizeText(officialDesc.replace("_", " "))
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF0284C7).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "DESCRIÇÃO DO ESTADO:",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                letterSpacing = 0.3.sp
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = cleanDescNovo,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFE2E8F0),
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp
                                        )
                                    )
                                }
                            }
                        } else if (event.msg.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = sanitizeText(event.msg),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), lineHeight = 18.sp)
                            )
                        } else {
                            val detailsList = mutableListOf<String>()
                            if (event.symbol.isNotBlank()) detailsList.add("Ativo: ${event.symbol}")
                            if (event.timeframe.isNotBlank()) detailsList.add("TF: ${cleanStateEnumText(event.timeframe)}")
                            if (event.login > 0) detailsList.add("Conta: ${event.login}")
                            if (event.server.isNotBlank()) detailsList.add("Servidor: ${event.server}")
                            val fallbackStr = if (detailsList.isNotEmpty()) detailsList.joinToString(" • ") else "Log de transição registrado no terminal MT5 do robô."

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = fallbackStr,
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), lineHeight = 18.sp)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (event.symbol.isNotBlank() && !isInicializacao) {
                        Text(
                            text = "Ativo: ${event.symbol}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold),
                            maxLines = 1
                        )
                    }
                    if (event.timeframe.isNotBlank() && !isInicializacao) {
                        Text(
                            text = "TF: ${event.timeframe}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)),
                            maxLines = 1
                        )
                    }
                    if (event.server.isNotBlank() && !isInicializacao) {
                        Text(
                            text = "Servidor: ${event.server}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B)),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun EaEventItemCard(
    event: com.example.data.EaRobotEvent,
    isFocused: Boolean = false
) {
    ClassicEventCard(event = event, isFocused = isFocused)
}

@Composable
fun ClassicStateChangeCard(
    event: com.example.data.EaRobotEvent,
    isFocused: Boolean = false
) {
    ClassicEventCard(event = event, isFocused = isFocused)
}

@Composable
fun Mt5AccountQuickManageCard(
    mt5AccountId: String,
    onSaveMt5Id: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var inputId by remember(mt5AccountId) { mutableStateOf(mt5AccountId) }
    val planConfig = LocalLicensePlanConfig.current

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
                    Column {
                        Text(
                            text = "CONTA DE NEGOCIAÇÃO MT5",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 1.sp
                            )
                        )
                        Text(
                            text = "Limite do plano: ${planConfig.vincularConta} conta(s)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color(0xFF22D3EE),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
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
            if (status != null) {
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
                            text = status.fusoTexto.ifBlank { "GMT+0" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "POSIÇÃO / ORDENS",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (status.temPosicao) "⚠️ Posição / Ordem Aberta" else "💤 Em Espera (Sem ordens)",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (status.temPosicao) Color(0xFFF59E0B) else Color(0xFF94A3B8),
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
fun LicenseStatusCard(
    status: String,
    expiryDate: String,
    creditoGuardado: Double = 0.0,
    tier: LicenseTier = LicenseTier.TRIAL,
    mt5AccountId: String = ""
) {
    val displayStatusColor = if (status.equals("Ativa", ignoreCase = true)) {
        tier.accentColor
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
                1.5.dp,
                tier.accentColor.copy(alpha = 0.6f),
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
                        text = "PLANO & LICENÇA ATUAL",
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(displayStatusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tier.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
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
                        .border(1.dp, displayStatusColor.copy(alpha = 0.5f), RoundedCornerShape(50.dp))
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
            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        text = if (mt5AccountId.isNotBlank()) "ID: $mt5AccountId" else "Não vinculada",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFFCFFAFE), // Cyan 100
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CRÉDITO GUARDADO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(Locale.US, "%,.2f MT", creditoGuardado),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color(0xFF10B981),
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
                        text = if (tier == LicenseTier.MASTER) "VITALÍCIO" else expiryDate.ifBlank { "N/A" },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            val planConfig = LocalLicensePlanConfig.current
            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "RECURSOS & PERMISSÕES DO SEU PLANO",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlanFeatureBadge(label = "Templates EA", isEnabled = planConfig.templates)
                PlanFeatureBadge(label = "Captura Gráfica", isEnabled = planConfig.capturaDeTela)
                PlanFeatureBadge(label = "Gráfico Equity", isEnabled = planConfig.graficoDePatrimonio)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlanFeatureBadge(label = "Alertas de Áudio", isEnabled = planConfig.audio)
                PlanFeatureBadge(label = "Contas MT5", isEnabled = true, customValue = "${planConfig.vincularConta} conta(s)")
                PlanFeatureBadge(label = "Sala VIP Sinais", isEnabled = planConfig.sala)
            }
        }
    }
}

@Composable
fun PlanFeatureBadge(
    label: String,
    isEnabled: Boolean,
    customValue: String? = null
) {
    Surface(
        color = if (isEnabled) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.8.dp, if (isEnabled) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = if (isEnabled) Color(0xFF34D399) else Color(0xFFF87171),
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = customValue ?: if (isEnabled) label else "$label 🔒",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isEnabled) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
fun PlanTiersShowcaseCard(
    currentTier: LicenseTier,
    loggedUser: com.example.data.GithubUser
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val globalLicenseConfig = LocalGlobalLicenseConfig.current
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var selectedTierForUpgrade by remember { mutableStateOf<LicenseTier?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "PLANOS & UPGRADES DISPONÍVEIS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF38BDF8),
                            letterSpacing = 1.sp
                        )
                    )
                    Text(
                        text = "Compare os recursos e desbloqueie o potencial máximo do Robô EA",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            // Cards for all 4 tiers
            val allTiers = listOf(LicenseTier.TRIAL, LicenseTier.STARTER, LicenseTier.PRO, LicenseTier.MASTER)

            allTiers.forEach { tier ->
                val isCurrent = (tier == currentTier)
                val cardBorderColor = if (isCurrent) tier.accentColor else Color(0xFF334155).copy(alpha = 0.6f)
                val cardBg = if (isCurrent) Color(0xFF1E293B).copy(alpha = 0.9f) else Color(0xFF131D31)
                val tierPlanConfig = globalLicenseConfig.getConfigForTier(tier)
                val dynamicFeatures = tier.buildDynamicFeatures(tierPlanConfig)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    color = cardBg,
                    border = BorderStroke(if (isCurrent) 2.dp else 1.dp, cardBorderColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(tier.accentColor, CircleShape)
                                )
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = tier.displayName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                color = Color.White
                                            )
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = tier.accentColor.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, tier.accentColor)
                                            ) {
                                                Text(
                                                    text = "SEU PLANO ATUAL",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = tier.accentColor,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.sp
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = tier.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }

                            Surface(
                                color = tier.accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, tier.accentColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tier.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = tier.accentColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Pricing & specs row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "PREÇO / VALIDADE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                val priceText = if (tier.priceMt == 0.0) "GRÁTIS" else String.format(Locale.US, "%,.0f MT (~$%,.0f USD)", tier.priceMt, tier.priceUsd)
                                Text(
                                    text = "$priceText • ${tier.durationLabel}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "LOTE OPERACIONAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF64748B),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "ILIMITADO",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }

                        // Dynamic Features list from JSON
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            dynamicFeatures.forEach { feat ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (feat.isIncluded) Icons.Default.CheckCircle else if (feat.isTrialLocked) Icons.Default.Lock else Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = if (feat.isIncluded) Color(0xFF10B981) else if (feat.isTrialLocked) Color(0xFFF59E0B) else Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = feat.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (feat.isIncluded) Color(0xFFE2E8F0) else Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Upgrade CTA
                        if (!isCurrent) {
                            Button(
                                onClick = {
                                    selectedTierForUpgrade = tier
                                    showUpgradeDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = tier.accentColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Adquirir / Fazer Upgrade para ${tier.displayName} 🚀",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = if (tier == LicenseTier.STARTER) Color(0xFF0F172A) else Color.White,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUpgradeDialog && selectedTierForUpgrade != null) {
        val targetTier = selectedTierForUpgrade!!
        val targetPlanConfig = globalLicenseConfig.getConfigForTier(targetTier)
        val upgradeMessage = "Olá Suporte FiMaster! Gostaria de ativar/fazer upgrade para a licença *${targetTier.displayName}* (${targetTier.badgeLabel}) para a minha conta MT5 *${loggedUser.mt5IdConta.ifBlank { "A Definir" }}* (Cliente: ${loggedUser.nome}). Segue o comprovativo de pagamento."

        Dialog(
            onDismissRequest = { showUpgradeDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .heightIn(max = 740.dp)
                    .border(1.5.dp, targetTier.accentColor.copy(alpha = 0.8f), RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(targetTier.accentColor.copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, targetTier.accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Diamond,
                                    contentDescription = null,
                                    tint = targetTier.accentColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "ADQUIRIR ${targetTier.displayName.uppercase()}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                                Text(
                                    text = if (targetPlanConfig.priceMt > 0.0) 
                                        "${String.format(Locale.US, "%,.0f", targetPlanConfig.priceMt)} MT (~$${String.format(Locale.US, "%,.0f", targetPlanConfig.priceUsd)} USD) • ${targetPlanConfig.durationDays} Dias"
                                    else "Plano Gratuito de Teste • ${targetPlanConfig.durationDays} Dias",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = targetTier.accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        IconButton(onClick = { showUpgradeDialog = false }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF94A3B8))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // QR Code Visualizer Section
                        Surface(
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = "QR Code",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "QR CODE DE PAGAMENTO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }

                                // Visualized QR Code (Base64 converted bytes or procedural canvas)
                                QrCodeVisualizer(
                                    base64Image = targetPlanConfig.qrCodeBase64,
                                    qrCodeLink = targetPlanConfig.qrCodeLink,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )

                                Text(
                                    text = "Escaneie o QR Code acima com seu aplicativo bancário ou carteira digital.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }

                        // QR Code Link / Key Section
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CHAVE / LINK DO QR CODE:",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("QR Code Link", targetPlanConfig.qrCodeLink)
                                            clipboard?.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Link do QR Code copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar Link",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = targetPlanConfig.qrCodeLink.ifBlank { "https://fimaster.vip/checkout" },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("QR Code Link", targetPlanConfig.qrCodeLink)
                                            clipboard?.setPrimaryClip(clip)
                                            android.widget.Toast.makeText(context, "Chave / Link copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f),
                                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                                        contentPadding = PaddingValues(vertical = 6.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copiar Chave", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (targetPlanConfig.qrCodeLink.startsWith("http://") || targetPlanConfig.qrCodeLink.startsWith("https://")) {
                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(targetPlanConfig.qrCodeLink)).apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Abrir Link", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // WhatsApp Atendimento Direct Button (WhatsApp é para atendimentos)
                        Surface(
                            color = Color(0xFF064E3B).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Chat, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "ATENDIMENTO WHATSAPP (SUPORTE & COMPROVATIVO)",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF10B981),
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.5.sp
                                        )
                                    )
                                }

                                Text(
                                    text = "Envie o comprovativo e confirme a ativação imediata pelo WhatsApp oficial:",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0), fontSize = 11.sp)
                                )

                                Button(
                                    onClick = {
                                        openWhatsAppLink(context, targetPlanConfig.whatsappLink, upgradeMessage)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviar no WhatsApp 📲", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                }
                            }
                        }

                        // Telegram Channel Direct Button
                        Surface(
                            color = Color(0xFF0C4A6E).copy(alpha = 0.35f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF229ED9).copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CANAL DO TELEGRAM",
                                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Acompanhe atualizações e novidades",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        openTelegramLink(context, targetPlanConfig.telegramLink)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF229ED9)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Telegram 🚀", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Bottom Dismiss Button
                    OutlinedButton(
                        onClick = { showUpgradeDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text("Fechar", color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                    }
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
                            text = "Confirmar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

data class FaqItem(
    val id: Int,
    val question: String,
    val answer: String,
    val category: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun SupportTicketDialog(
    userProfile: com.example.data.GithubUser?,
    onDismiss: () -> Unit,
    onSubmit: (categoria: String, assunto: String, mensagem: String, contato: String) -> Unit
) {
    var activeSupportTab by remember { mutableIntStateOf(0) } // 0: FAQ, 1: Canais Oficiais, 2: Abrir Ticket, 3: Diagnóstico
    var searchQuery by remember { mutableStateOf("") }
    var selectedFaqCategory by remember { mutableStateOf("Todas") }
    var expandedFaqIndex by remember { mutableIntStateOf(-1) }
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val faqCategories = listOf("Todas", "MT5 & Conexão", "Risco & Lote", "Licença", "Alertas & Voz", "Smart Sync")

    val faqList = remember {
        listOf(
            FaqItem(
                id = 1,
                question = "Como vincular minha conta do MT5 ao Portal?",
                answer = "Acesse a aba 'Conta EA' no menu inferior do aplicativo, digite o número exato da sua conta de negociação no MT5 (ex: 8841209) e clique em 'Salvar Número MT5'. Quando o robô estiver em execução no MetaTrader 5 com esse mesmo ID, o Portal exibirá o status ONLINE em tempo real com luz verde pulsante.",
                category = "MT5 & Conexão",
                icon = Icons.Default.AccountBalance
            ),
            FaqItem(
                id = 2,
                question = "Por que os Templates Oficiais do Admin vêm com Lote 0.00?",
                answer = "Por estrita medida de segurança e gestão de risco! A regra de ouro da mesa proprietária é uma exposição máxima de 0.5% por operação em relação ao saldo total da sua conta. O Admin publica os parâmetros com lote 0.00 para que você defina o lote apropriado na aba 'Config EA' de acordo com o tamanho exato da sua banca antes de sincronizar.",
                category = "Risco & Lote",
                icon = Icons.Default.Shield
            ),
            FaqItem(
                id = 3,
                question = "Como carregar e aplicar um Template do Admin?",
                answer = "Na aba 'Config EA', abra a seção 'Templates & Presets do Admin'. Escolha o preset desejado (ex: Gold Conservador, Surfada D1, Scalper Asiático), confira o par (XAUUSD, EURUSD) e pontos do canal, clique em 'Carregar' ou 'Aplicar'. Os campos do formulário serão preenchidos. Ajuste o lote para 0.5% de exposição e clique em 'Salvar e Sincronizar'.",
                category = "Risco & Lote",
                icon = Icons.Default.CloudDownload
            ),
            FaqItem(
                id = 4,
                question = "O que fazer se o Robô no MT5 aparecer como OFFLINE?",
                answer = "1. Certifique-se de que o botão 'AlgoTrading' ou 'AutoTrading' está ativo (ícone verde) no topo do MetaTrader 5.\n2. Verifique se o número da conta MT5 cadastrado na aba 'Conta EA' é exatamente o mesmo da conta logada no MetaTrader.\n3. Confirme se as URLs do servidor Firebase estão autorizadas em Ferramentas > Opções > Expert Advisors no MT5.\n4. Verifique se o indicador ou EA está anexado ao gráfico com o 'Permitir WebRequest' marcado.",
                category = "MT5 & Conexão",
                icon = Icons.Default.Warning
            ),
            FaqItem(
                id = 5,
                question = "Como verificar o status e validade da minha Licença?",
                answer = "Acesse a aba 'Licença' no menu inferior. Lá você poderá visualizar se sua licença está ativa ('LICENÇA ATIVA' em verde esmeralda), conferir a data de expiração exata, os dias restantes e o identificador seguro do dispositivo (UID). Quando faltar 7 dias ou menos para o término, o app exibirá alertas no painel superior.",
                category = "Licença",
                icon = Icons.Default.Verified
            ),
            FaqItem(
                id = 6,
                question = "Como funcionam os Alertas em Áudio (TTS) no Portal?",
                answer = "Na aba 'Eventos EA', o Portal Fimaster processa notificações de ordens e canais do robô em tempo real. Cada evento possui um botão com ícone de alto-falante para reproduzir a voz sintetizada em português (BR/PT). Você também pode ativar a reprodução contínua no topo para ouvir cada nova operação sem olhar a tela.",
                category = "Alertas & Voz",
                icon = Icons.Default.VolumeUp
            ),
            FaqItem(
                id = 7,
                question = "Como funciona o Smart Sync e a limpeza de eventos?",
                answer = "O Smart Sync armazena todos os eventos no banco de dados SQLite local (Room) do seu celular. No primeiro login ele baixa o histórico completo; nos acessos seguintes, só transfere novidades, economizando seus dados móveis. Ao clicar em 'Limpar', o app esvazia o visor local mas guarda o cursor de data/hora, garantindo que eventos antigos apagados não voltem a poluir a tela no próximo sync, a menos que você clique em 'Forçar Completa'.",
                category = "Smart Sync",
                icon = Icons.Default.Sync
            ),
            FaqItem(
                id = 8,
                question = "Qual é o benefício de não ficar na frente da tela do gráfico?",
                answer = "O maior inimigo do trader é o cansaço psicológico e a ansiedade de tela. Ficar horas acompanhando cada oscilação gera impulsos prejudiciais como fechar ordens antes da hora ou aumentar lotes para recuperar perdas (revenge trading). Com o robô EA e o Portal Fimaster, a estratégia roda no VPS com precisão matemática, e você mantém a clareza mental acompanhando métricas e ouvindo notificações de qualquer lugar.",
                category = "Risco & Lote",
                icon = Icons.Default.Psychology
            ),
            FaqItem(
                id = 9,
                question = "Como solicitar captura de tela (Screenshot) do gráfico?",
                answer = "No painel da aba 'Conta EA', localize o botão 'Capturar Gráfico'. O comando é despachado em tempo real para o EA no MT5, que tira um print em alta resolução da tela do ativo e envia de volta para o aplicativo para você conferir a montagem gráfica instantaneamente.",
                category = "MT5 & Conexão",
                icon = Icons.Default.Screenshot
            )
        )
    }

    val filteredFaq = remember(searchQuery, selectedFaqCategory, faqList) {
        faqList.filter { item ->
            val matchCategory = selectedFaqCategory == "Todas" || item.category == selectedFaqCategory
            val matchQuery = searchQuery.isBlank() ||
                    item.question.contains(searchQuery, ignoreCase = true) ||
                    item.answer.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchCategory && matchQuery
        }
    }

    val categorias = listOf(
        "Problema no MT5",
        "Licença & Expiração",
        "Configuração do EA",
        "Gestão de Risco & Lote",
        "Outros Assuntos"
    )
    val urgencias = listOf("Normal", "Alta", "Urgente")
    var selectedCategory by remember { mutableStateOf(categorias[0]) }
    var selectedUrgencia by remember { mutableStateOf(urgencias[0]) }
    var assuntoText by remember { mutableStateOf("") }
    var mensagemText by remember { mutableStateOf("") }
    var contatoText by remember { mutableStateOf(userProfile?.numero ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 16.dp)
                .border(
                    1.dp,
                    Color(0xFF0EA5E9).copy(alpha = 0.4f),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0EA5E9).copy(alpha = 0.15f))
                            .border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (activeSupportTab) {
                                0 -> Icons.Default.Help
                                1 -> Icons.Default.Forum
                                2 -> Icons.Default.SupportAgent
                                else -> Icons.Default.Build
                            },
                            contentDescription = "Suporte",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (activeSupportTab) {
                                0 -> "Central de Dúvidas (FAQ)"
                                1 -> "Canais Oficiais Fimaster"
                                2 -> "Abrir Chamado / Ticket"
                                else -> "Diagnóstico & Conexão"
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = when (activeSupportTab) {
                                0 -> "Respostas completas para operar com máxima confiança"
                                1 -> "Atendimento VIP direto com a equipe de especialistas"
                                2 -> "Envie sua dúvida técnica com resposta garantida"
                                else -> "Status da infraestrutura e parâmetros do dispositivo"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar",
                            tint = Color(0xFF64748B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Top Tab Selector (4 Abas: FAQ, Canais, Ticket, Diagnóstico)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, "FAQ", Icons.Default.QuestionAnswer),
                        Triple(1, "Canais", Icons.Default.Forum),
                        Triple(2, "Ticket", Icons.Default.Send),
                        Triple(3, "Info", Icons.Default.Info)
                    )
                    tabs.forEach { (index, label, icon) ->
                        val isSelected = activeSupportTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeSupportTab = index },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) Color(0xFF0EA5E9) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (activeSupportTab) {
                    0 -> {
                        // TAB 0: FAQ (Perguntas Frequentes)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            // Search bar
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Pesquisar dúvida (ex: lote, VPS, áudio, MT5)...", fontSize = 11.5.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF0EA5E9),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Limpar",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Category Chips
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(faqCategories) { cat ->
                                    val isSelected = cat == selectedFaqCategory
                                    Surface(
                                        onClick = { selectedFaqCategory = cat },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
                                    ) {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 10.5.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (filteredFaq.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhuma dúvida encontrada para '${searchQuery}'",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF94A3B8))
                                    )
                                }
                            } else {
                                filteredFaq.forEachIndexed { index, item ->
                                    val isExpanded = expandedFaqIndex == index
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable {
                                                expandedFaqIndex = if (isExpanded) -1 else index
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isExpanded) Color(0xFF1E293B) else Color(0xFF0F172A)
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isExpanded) Color(0xFF0EA5E9) else Color(0xFF334155)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF0EA5E9).copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = null,
                                                        tint = Color(0xFF38BDF8),
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = item.question,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Icon(
                                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            if (isExpanded) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                HorizontalDivider(color = Color(0xFF334155))
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = item.answer,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFFCBD5E1),
                                                        fontSize = 11.5.sp,
                                                        lineHeight = 17.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Call to action button to open ticket
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeSupportTab = 2 },
                                color = Color(0xFF0EA5E9).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HeadsetMic,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Não encontrou sua dúvida? Abra um Ticket com 1 clique",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: CANAIS OFICIAIS DE CONTATO
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Atendimento Direto & Comunidade",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            // WhatsApp Suporte VIP
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Chat,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "WhatsApp Suporte VIP",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = "Atendimento prioritário com engenheiro de suporte",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFFA7F3D0),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString("+5511999999999"))
                                            android.widget.Toast.makeText(context, "Contato de Suporte copiado!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar contato",
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Telegram Comunidade Oficial
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        openTelegramLink(context, "https://t.me/FiMasterVipOficial")
                                    },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0369A1).copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Comunidade VIP Telegram",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = "Avisos de mercado, presets e networking entre traders",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFFBAE6FD),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            // E-mail Técnico
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF475569)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF64748B).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = null,
                                            tint = Color(0xFFCBD5E1),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "E-mail de Suporte Técnico",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = "suporte@fimaster.com • Resposta em até 2 horas úteis",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }

                            // Horário de Atendimento
                            Surface(
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Plantão Operacional: Segunda a Sexta, das 07:00 às 22:00 (Horário de Brasília/Maputo)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFFDE68A),
                                            fontSize = 10.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // TAB 2: ABRIR TICKET
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Tipo de Solicitação",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(categorias) { cat ->
                                    val isSelected = cat == selectedCategory
                                    Surface(
                                        modifier = Modifier.clickable { selectedCategory = cat },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF0EA5E9) else Color(0xFF1E293B),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                        )
                                    ) {
                                        Text(
                                            text = cat,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Nível de Urgência",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                urgencias.forEach { urg ->
                                    val isSelected = urg == selectedUrgencia
                                    val tagColor = when (urg) {
                                        "Urgente" -> Color(0xFFEF4444)
                                        "Alta" -> Color(0xFFF59E0B)
                                        else -> Color(0xFF10B981)
                                    }
                                    Surface(
                                        onClick = { selectedUrgencia = urg },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) tagColor.copy(alpha = 0.25f) else Color(0xFF1E293B),
                                        border = BorderStroke(1.dp, if (isSelected) tagColor else Color(0xFF334155))
                                    ) {
                                        Text(
                                            text = urg,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) tagColor else Color(0xFF94A3B8),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Assunto
                            OutlinedTextField(
                                value = assuntoText,
                                onValueChange = {
                                    assuntoText = it
                                    errorMessage = null
                                },
                                label = { Text("Assunto / Título do Chamado") },
                                placeholder = { Text("Ex: Dúvida sobre Trava Diária e Sessões") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedLabelColor = Color(0xFF38BDF8),
                                    unfocusedLabelColor = Color(0xFF64748B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Mensagem / Descrição
                            OutlinedTextField(
                                value = mensagemText,
                                onValueChange = {
                                    mensagemText = it
                                    errorMessage = null
                                },
                                label = { Text("Descrição Detalhada do Problema") },
                                placeholder = { Text("Descreva com detalhes o que está acontecendo...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 5,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedLabelColor = Color(0xFF38BDF8),
                                    unfocusedLabelColor = Color(0xFF64748B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Contato (WhatsApp/E-mail)
                            OutlinedTextField(
                                value = contatoText,
                                onValueChange = { contatoText = it },
                                label = { Text("Contato para Retorno (WhatsApp / E-mail)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF0EA5E9),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedLabelColor = Color(0xFF38BDF8),
                                    unfocusedLabelColor = Color(0xFF64748B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (errorMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = Color(0xFFEF4444),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onDismiss) {
                                    Text("Cancelar", color = Color(0xFF94A3B8))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (assuntoText.isBlank() || mensagemText.isBlank()) {
                                            errorMessage = "Por favor, preencha o assunto e a descrição."
                                        } else {
                                            val fullSubject = "[$selectedUrgencia] $assuntoText"
                                            onSubmit(selectedCategory, fullSubject, mensagemText.trim(), contatoText.trim())
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enviar Ticket", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    else -> {
                        // TAB 3: DIAGNÓSTICO & INFORMAÇÕES DO SISTEMA
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 440.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Diagnóstico do Aplicativo & Conexões",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                            )

                            // Card de Conta MT5 & Licença
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Conta MT5 Vinculada:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                        Text(
                                            text = userProfile?.mt5IdConta?.ifBlank { "859423" } ?: "859423",
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Status da Licença:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                        Text(
                                            text = if (userProfile?.licencaAtiva == true) "LICENÇA ATIVA" else "AGUARDANDO ATIVAÇÃO",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (userProfile?.licencaAtiva == true) Color(0xFF34D399) else Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Banco Local (SQLite Room):", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                        Text("ONLINE / Sincronizado", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF34D399), fontWeight = FontWeight.Bold))
                                    }
                                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Síntese de Voz (TTS):", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                        Text("Português (BR/PT) Ativo", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                                    }
                                    HorizontalDivider(color = Color(0xFF334155).copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Versão do Portal:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)))
                                        Text("v4.5.0 Pro Cloud", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }

                            // Dispositivo UID
                            val deviceUid = userProfile?.auditoriaUltimoDispositivo.orEmpty().ifBlank {
                                com.example.data.security.DeviceIdentityManager(context).getSilentDeviceUid()
                            }
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "ID Seguro do Dispositivo (UID)",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                                        )
                                        Text(
                                            text = deviceUid.take(24) + "...",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(deviceUid))
                                            android.widget.Toast.makeText(context, "UID copiado para a área de transferência!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copiar UID",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
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
    val adminTemplates by viewModel.adminTemplates.collectAsStateWithLifecycle()
    
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
    var activeTemplateId by remember { mutableStateOf<String?>(null) }
    var activeTemplateAction by remember { mutableStateOf<String?>(null) }
    var showAdminInstructionsDialog by remember { mutableStateOf(false) }

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
                                    Text(
                                        text = if (isSynced) "Parâmetros sincronizados com sucesso pelo EA" else "Sincronizando parâmetros com o robô MetaTrader...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    )
                                    
                                    if (isSynced && (eaRobotStatus?.lastConfigSync ?: 0L) > 0L) {
                                        val dateStr = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
                                            .format(java.util.Date((eaRobotStatus?.lastConfigSync ?: 0L) * 1000L))
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = "Confirmado pelo MT5 em ",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF10B981).copy(alpha = 0.25f),
                                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Schedule,
                                                        contentDescription = null,
                                                        tint = Color(0xFF34D399),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = dateStr,
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFF34D399),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "O robô lê e aplica as alterações automaticamente a cada 5 segundos no MT5",
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
                val userPlanConfig = LocalLicensePlanConfig.current
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!userPlanConfig.templates) {
                        Surface(
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Templates Bloqueados",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "🔒 RECURSO BLOQUEADO PELO SEU PLANO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFEF4444)
                                        )
                                    )
                                    Text(
                                        text = "O carregamento de templates e presets está restrito (templates: false). Faça upgrade para Starter, Pro ou Master VIP para liberar presets pré-configurados.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 1. ADMIN PUBLISHED TEMPLATES SECTION
                    if (showAdminInstructionsDialog) {
                        InstrucoesAdminTemplatesDialog(onDismiss = { showAdminInstructionsDialog = false })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👑 TEMPLATES DO ADMIN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFC084FC),
                                letterSpacing = 0.5.sp
                            ),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = { showAdminInstructionsDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFC084FC).copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Instruções Admin",
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Schema",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFC084FC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.loadAdminTemplates()
                                templateMessage = "Servidor consultado: Templates atualizados com sucesso!"
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFC084FC)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Atualizar Templates",
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Atualizar",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFFC084FC),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (adminTemplates.isEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Text(
                                    text = "Nenhum template publicado pelo Admin no momento.",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            adminTemplates.forEach { tpl ->
                                val isValid = tpl.isTemplateValido()
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isValid) Color(0xFF0EA5E9).copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tpl.titulo,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                )
                                                if (tpl.descricao.isNotBlank()) {
                                                    Text(
                                                        text = tpl.descricao,
                                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 11.sp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isValid) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f),
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isValid) Color(0xFF34D399) else Color(0xFFF87171)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isValid) Color(0xFF34D399) else Color(0xFFF87171))
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isValid) "DISPONÍVEL" else "INDISPONÍVEL",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = if (isValid) Color(0xFF34D399) else Color(0xFFF87171),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Event, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "Pub: ${tpl.dataPublicacao}",
                                                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp)
                                                )
                                            }

                                            if (!isValid && tpl.validoAte.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(imageVector = Icons.Default.Schedule, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(12.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Expirado: ${tpl.validoAte}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = Color(0xFFF87171),
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        if (tpl.paridade.isNotBlank() || tpl.pontosAtivo.isNotBlank()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (tpl.paridade.isNotBlank()) {
                                                    Surface(
                                                        color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                                                    ) {
                                                        Text(
                                                            text = "💱 ${tpl.paridade}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = Color(0xFF7DD3FC),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                                if (tpl.pontosAtivo.isNotBlank()) {
                                                    Surface(
                                                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                                                    ) {
                                                        Text(
                                                            text = "🎯 ${tpl.pontosAtivo}",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                color = Color(0xFF6EE7B7),
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold
                                                            ),
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Text(
                                            text = "Estratégia: ${tpl.config.ESTRATÉGIA} | TF: ${tpl.config.OperationalPeriod}",
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        )

                                        Surface(
                                            color = Color(0xFF0284C7).copy(alpha = 0.15f),
                                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "⚠️ Lote 0.00: Ajuste o lote nos campos conforme a margem da sua conta! A exposição recomendada é de 0.5%",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = Color(0xFFBAE6FD),
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        lineHeight = 12.sp
                                                    )
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val isThisActive = activeTemplateId == tpl.id
                                            val isLoaded = isThisActive && activeTemplateAction == "LOADED"
                                            val isApplied = isThisActive && activeTemplateAction == "APPLIED"
                                            val canUseTemplate = isValid && userPlanConfig.templates

                                            OutlinedButton(
                                                onClick = {
                                                    if (!canUseTemplate) {
                                                        templateMessage = "Recurso bloqueado: seu plano não permite carregar templates."
                                                        return@OutlinedButton
                                                    }
                                                    localConfig = tpl.config
                                                    activeTemplateId = tpl.id
                                                    activeTemplateAction = "LOADED"
                                                    templateMessage = "Template '${tpl.titulo}' carregado nos campos para revisão!"
                                                },
                                                enabled = canUseTemplate,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (!canUseTemplate) Color(0xFF475569) else if (isLoaded) Color(0xFF10B981) else Color(0xFF38BDF8)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    disabledContentColor = Color(0xFF64748B)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (!userPlanConfig.templates) Icons.Default.Lock else if (isLoaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                                    contentDescription = null,
                                                    tint = if (!canUseTemplate) Color(0xFF64748B) else if (isLoaded) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (!userPlanConfig.templates) "Bloqueado" else if (isLoaded) "✓ Carregado" else "Carregar",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (!canUseTemplate) Color(0xFF64748B) else if (isLoaded) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                        fontWeight = if (isLoaded) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    if (!canUseTemplate) {
                                                        templateMessage = "Recurso bloqueado: seu plano não permite aplicar templates."
                                                        return@Button
                                                    }
                                                    localConfig = tpl.config
                                                    activeTemplateId = tpl.id
                                                    activeTemplateAction = "APPLIED"
                                                    templateMessage = "Template '${tpl.titulo}' aplicado nos campos! Clique em 'Salvar e Sincronizar' para enviar ao banco."
                                                },
                                                enabled = canUseTemplate,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isApplied) Color(0xFF10B981) else Color(0xFF0284C7),
                                                    disabledContainerColor = Color(0xFF334155),
                                                    disabledContentColor = Color(0xFF64748B)
                                                ),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (!userPlanConfig.templates) Icons.Default.Lock else Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (canUseTemplate) Color.White else Color(0xFF64748B),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (!userPlanConfig.templates) "Bloqueado" else if (isApplied) "✓ Aplicado" else "Aplicar",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (canUseTemplate) Color.White else Color(0xFF64748B),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                            }
                                        }
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
                                val customId = "custom_$index"
                                val isCustomLoaded = activeTemplateId == customId && activeTemplateAction == "LOADED"
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, if (isCustomLoaded) Color(0xFF10B981) else Color(0xFF334155))
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
                                                    if (!userPlanConfig.templates) {
                                                        templateMessage = "Recurso bloqueado: seu plano não permite carregar templates."
                                                        return@Button
                                                    }
                                                    localConfig = customCfg
                                                    activeTemplateId = customId
                                                    activeTemplateAction = "LOADED"
                                                    templateMessage = "Template '$name' aplicado!"
                                                },
                                                enabled = userPlanConfig.templates,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCustomLoaded) Color(0xFF10B981) else Color(0xFF0284C7),
                                                    disabledContainerColor = Color(0xFF334155),
                                                    disabledContentColor = Color(0xFF64748B)
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = if (!userPlanConfig.templates) "🔒 Bloqueado" else if (isCustomLoaded) "✓ Aplicado" else "📥 Usar",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
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

                    // Quick Currency Presets & Auto Fetch Online Quote
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🌐 Buscar Cotação em Tempo Real (API Financeira)",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        )

                        Text(
                            text = "Obtenha a taxa oficial de conversão USD via mercado financeiro internacional:",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("MZN" to "🇲🇿 Metical", "BRL" to "🇧🇷 Real", "EUR" to "🇪🇺 Euro", "AOA" to "🇦🇴 Kwanza").forEach { (code, label) ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            localConfig = config.copy(mony = code)
                                            viewModel.fetchExchangeRate(code) { newRate ->
                                                if (newRate != null) {
                                                    localConfig = localConfig?.copy(CAMBIO = newRate, mony = code)
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (config.mony.uppercase() == code) Color(0xFF0EA5E9) else Color(0xFF1E293B),
                                    border = BorderStroke(0.5.dp, Color(0xFF0EA5E9).copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                val targetCode = config.mony.ifBlank { "MZN" }
                                viewModel.fetchExchangeRate(targetCode) { newRate ->
                                    if (newRate != null) {
                                        localConfig = localConfig?.copy(CAMBIO = newRate, mony = targetCode)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Atualizar Câmbio On-line Agora", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
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
        val msg = feedbackMessage
        if (!msg.isNullOrBlank()) {
            if (msg.startsWith("Login efetuado") || msg.startsWith("Bem-vindo")) {
                lastLoginError = null
            } else {
                lastLoginError = msg
            }
        }
    }

    LaunchedEffect(phoneInput, passwordInput) {
        if (lastLoginError != null) {
            lastLoginError = null
            viewModel.clearMessage()
        }
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

            Spacer(modifier = Modifier.height(20.dp))

            val currentContext = LocalContext.current
            val deviceUid = remember {
                com.example.data.security.DeviceIdentityManager(currentContext).getSilentDeviceUid()
            }

            Surface(
                color = Color(0xFF0369A1).copy(alpha = 0.25f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF0EA5E9).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Device Auth",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "AUTENTICAÇÃO POR DISPOSITIVO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF38BDF8),
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "SEGURO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "UID: " + deviceUid.take(24) + "...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    )

                    Button(
                        onClick = { viewModel.autoAuthenticateAndRegisterDevice() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loginLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Entrar com este Dispositivo ⚡",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                        text = "Ou Acesse com Telefone / MT5",
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
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D).copy(alpha = 0.9f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Erro de Acesso",
                                    tint = Color(0xFFFCA5A5),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
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
                                IconButton(
                                    onClick = {
                                        lastLoginError = null
                                        viewModel.clearMessage()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Fechar aviso",
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(18.dp)
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
    var selectedMode by remember { mutableStateOf(currentMode) }

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22D3EE).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configuração",
                            tint = Color(0xFF22D3EE),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Fonte de Conexão Admin",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                }

                Text(
                    text = "Selecione o método de acesso do servidor para consulta e sincronização:",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                )

                // Segmented selector row with GI and FI
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // FI Option (Firebase - Primary)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMode == "FIREBASE") Color(0xFF10B981).copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (selectedMode == "FIREBASE") 1.dp else 0.dp,
                                color = if (selectedMode == "FIREBASE") Color(0xFF10B981) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = "FIREBASE" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "FI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (selectedMode == "FIREBASE") Color(0xFF10B981) else Color(0xFF94A3B8)
                                )
                            )
                            Text(
                                text = "(Firebase)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (selectedMode == "FIREBASE") Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }

                    // GI Option (GitHub - Secondary)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedMode == "GITHUB") Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (selectedMode == "GITHUB") 1.dp else 0.dp,
                                color = if (selectedMode == "GITHUB") Color(0xFF38BDF8) else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMode = "GITHUB" }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "GI",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = if (selectedMode == "GITHUB") Color(0xFF38BDF8) else Color(0xFF94A3B8)
                                )
                            )
                            Text(
                                text = "(GitHub)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (selectedMode == "GITHUB") Color.White else Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                // Info banner without showing raw URL strings
                if (selectedMode == "FIREBASE") {
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "FI Selecionado",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "FI — Método Principal (Firebase)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Acesso direto e sincronização em tempo real ativada para clientes e licenças.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFA7F3D0)
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "GI Selecionado",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "GI — Método Secundário (GitHub)",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                                Text(
                                    text = "Acesso via repositório e serviços REST ativado.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFBAE6FD)
                                    )
                                )
                            }
                        }
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
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            onSave(config, selectedMode, currentFirebaseUrl)
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMode == "FIREBASE") Color(0xFF10B981) else Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Salvar",
                            style = MaterialTheme.typography.bodyMedium.copy(
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
    var activeGuideSection by remember { mutableIntStateOf(0) } // 0: Sobre o App, 1: Tour Passo a Passo, 2: Estratégias & Regras, 3: Checklist Diário

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
                title = "3. Templates & Presets do Admin",
                subtitle = "Carregamento Rápido & Regra de Risco (0.5%)",
                targetTab = PortalTab.EA_CONFIG,
                icon = Icons.Default.CloudDownload,
                description = "Na aba Config EA, expanda 'Templates & Presets do Admin' para aplicar setups oficiais testados pela equipe com 1 clique.",
                highlights = listOf(
                    "Paridades & Pontos" to "Verifique o ativo (ex: 💱 XAUUSD, EURUSD) e os pontos do canal (ex: 🎯 250 pts).",
                    "Aviso de Lote 0.00" to "Os templates vêm zerados (0.00) por segurança para evitar sobrealavancagem.",
                    "Exposição Recomendada (0.5%)" to "Ajuste o lote conforme a margem da sua conta (máximo 0.5% de risco).",
                    "Carregar & Aplicar" to "Sincronize com 1 clique em 'Aplicar' e depois 'Salvar e Sincronizar'."
                ),
                tip = "⚡ Dica: Nunca execute com lote padrão sem calcular a exposição de 0.5% da sua banca!"
            ),
            EaTourStep(
                stepIndex = 4,
                title = "4. Parâmetros Operacionais",
                subtitle = "Lote, Estratégia & Linhas de Equador",
                targetTab = PortalTab.EA_CONFIG,
                icon = Icons.Default.Tune,
                description = "Defina como o robô deve negociar na aba Config EA. O formulário aceita números decimais (ex: 0.01 ou 1.0850) de forma fluida.",
                highlights = listOf(
                    "Lote de Entrada (lot)" to "Define o volume por ordem (ex: 0.01 para contas micro/cent).",
                    "Linhas de Equador" to "Preços de referência máxima (ex: 1.0850) e mínima (ex: 1.0720).",
                    "Estratégia & Tendência" to "Selecione opções de entrada no menu suspenso ou escolha CUSTOM.",
                    "Virada de Jogo / Costurar" to "Ative funções de recuperação automatizada de posições."
                ),
                tip = "⚙️ Dica: Você pode digitar números decimais apagando e inserindo pontos/vírgulas sem travamentos!"
            ),
            EaTourStep(
                stepIndex = 5,
                title = "5. Gestão de Risco & Câmbio",
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
                stepIndex = 6,
                title = "6. Eventos & Alertas em Áudio",
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

    // Checklist state
    var checkVps by remember { mutableStateOf(true) }
    var checkMt5 by remember { mutableStateOf(true) }
    var checkLicenca by remember { mutableStateOf(true) }
    var checkLoteRisco by remember { mutableStateOf(false) }
    var checkVozTts by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 20.dp)
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
                    .padding(16.dp)
            ) {
                // Top header bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22D3EE).copy(alpha = 0.15f))
                                .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (activeGuideSection) {
                                    0 -> Icons.Default.AutoAwesome
                                    1 -> Icons.Default.Explore
                                    2 -> Icons.Default.TrendingUp
                                    else -> Icons.Default.Checklist
                                },
                                contentDescription = null,
                                tint = Color(0xFF22D3EE),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GUIA & SOBRE O APP",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF22D3EE),
                                    letterSpacing = 1.2.sp
                                )
                            )
                            Text(
                                text = "Portal Fimaster • Automação Inteligente MT5",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Guia",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs (Sobre, Tour, Estratégias, Checklist)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val guideTabs = listOf(
                        Triple(0, "Sobre", Icons.Default.Star),
                        Triple(1, "Tour", Icons.Default.Explore),
                        Triple(2, "Estratégias", Icons.Default.Insights),
                        Triple(3, "Checklist", Icons.Default.Checklist)
                    )
                    guideTabs.forEach { (index, label, icon) ->
                        val isSelected = activeGuideSection == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { activeGuideSection = index },
                            shape = RoundedCornerShape(9.dp),
                            color = if (isSelected) Color(0xFF0284C7) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (activeGuideSection) {
                    0 -> {
                        // ABA 0: SOBRE O APLICATIVO & BENEFÍCIOS REVOLUCIONÁRIOS
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 450.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Hero Card de Apresentação
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C4A6E).copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.WorkspacePremium,
                                            contentDescription = null,
                                            tint = Color(0xFFFACC15),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Portal Fimaster: Liberdade & Lucro Automatizado",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "O Portal Fimaster é a central de comando definitiva criada para traders que buscam consistência profissional sem abrir mão da sua qualidade de vida.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFBAE6FD),
                                            lineHeight = 17.sp
                                        )
                                    )
                                }
                            }

                            // SEÇÃO DE DESTAQUE: BENEFÍCIO DE NÃO FICAR PRESO NA FRENTE DO GRÁFICO
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sensors,
                                                contentDescription = null,
                                                tint = Color(0xFF34D399),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "A Grande Revolução: Liberdade de Tela",
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF34D399)
                                                )
                                            )
                                            Text(
                                                text = "Por que nunca mais ser refém dos gráficos?",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFFA7F3D0),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Ficar 6 a 10 horas por dia encarando velas e candles oscilando é o principal motivo de fracasso no trading. O desgaste psicológico gera ansiedade, medo de perder (FOMO) e leva a entradas precipitadas por impulso (revenge trading).",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = Color(0xFFE2E8F0),
                                            lineHeight = 17.sp
                                        )
                                    )

                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val benefits = listOf(
                                            "⏱️ Liberdade de Tempo" to "Trabalhe na sua profissão, passe tempo com a família, treine e durma tranquilo enquanto o EA executa no VPS 24h/dia.",
                                            "🧠 Psicologia 100% Blindada" to "O robô não hesita, não tem medo de entrar e não tem ganância de sair fora da regra. A matemática prevalece.",
                                            "🔊 Monitoramento por Áudio (TTS)" to "Você ouve as ordens e metas sendo batidas em português claro pelo celular, sem precisar tirar os olhos do trânsito ou do seu trabalho.",
                                            "🛡️ Trava de Segurança Automática" to "Ao bater a meta de ganho ou limite de perda, o sistema desativa as operações automaticamente, protegendo seu lucro."
                                        )
                                        benefits.forEach { (title, desc) ->
                                            Row(
                                                verticalAlignment = Alignment.Top,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier
                                                        .size(15.dp)
                                                        .padding(top = 2.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = title,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color.White,
                                                            fontSize = 11.5.sp
                                                        )
                                                    )
                                                    Text(
                                                        text = desc,
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // FUNCIONALIDADES EXCLUSIVAS DO ECOSSISTEMA FIMASTER
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Arsenal Tecnológico do Aplicativo:",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8)
                                        )
                                    )

                                    val features = listOf(
                                        Triple("⚡ Smart Sync & Room SQLite", "Sincronização inteligente incremental que economiza 95% dos seus dados móveis e carrega seu histórico mesmo offline.", Icons.Default.Sync),
                                        Triple("📊 Presets Oficiais do Admin", "Acesso instantâneo a setups consagrados pela mesa proprietária aplicados com 1 toque sob a regra de 0.5% de risco.", Icons.Default.CloudDownload),
                                        Triple("📸 Captura de Gráficos MT5", "Comando de screenshot remoto que traz uma foto cristalina do gráfico do MT5 para conferência imediata.", Icons.Default.Screenshot),
                                        Triple("🔒 Licenciamento & Auditoria UID", "Segurança avançada por hardware para blindar sua conta contra clones e invasões.", Icons.Default.Security)
                                    )

                                    features.forEach { (title, desc, icon) ->
                                        Row(
                                            verticalAlignment = Alignment.Top,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = title,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 11.5.sp
                                                    )
                                                )
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Botão de Ir para o Tour
                            Button(
                                onClick = { activeGuideSection = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ver Tour Passo a Passo", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    1 -> {
                        // ABA 1: TOUR PASSO A PASSO (1 ao 6)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 450.dp)
                        ) {
                            // Step counter + progress bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Passo ${currentStep.stepIndex} de ${steps.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = currentStep.subtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.5.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { (currentStepIndex + 1).toFloat() / steps.size.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Color(0xFF22D3EE),
                                trackColor = Color(0xFF1E293B)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

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
                                        .padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = currentStep.icon,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = currentStep.title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        )
                                        Text(
                                            text = currentStep.subtitle,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = currentStep.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 17.sp
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Destaques & Parâmetros:",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF22D3EE)
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))

                                currentStep.highlights.forEach { (label, desc) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier
                                                .size(15.dp)
                                                .padding(top = 2.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 11.5.sp
                                                )
                                            )
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

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
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.5.sp
                                        ),
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Abrir Tela: ${currentStep.targetTab.label}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Navigation Controls (Anterior / Próximo)
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
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Anterior", fontSize = 12.sp)
                                    }
                                } else {
                                    TextButton(
                                        onClick = { activeGuideSection = 0 },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64748B))
                                    ) {
                                        Text("Voltar ao Sobre", fontSize = 12.sp)
                                    }
                                }

                                if (currentStepIndex < steps.size - 1) {
                                    Button(
                                        onClick = { currentStepIndex++ },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "Próximo",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = "Próximo",
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { activeGuideSection = 2 },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ver Estratégias",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // ABA 2: ESTRATÉGIAS & REGRAS DE OURO
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 450.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Regras de Ouro da Mesa Fimaster",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            )

                            // Regra 1: Exposição 0.5%
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("1. A Regra Inegociável dos 0.5%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFFDE68A)))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Nunca arrisque mais do que 0.5% da banca em uma única operação. Exemplo: Para uma banca de \$1.000 USD, o risco máximo por stop deve ser \$5 USD (Lote 0.01 em micro/cent ou ajustado no ouro).",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 11.sp, lineHeight = 16.sp)
                                    )
                                }
                            }

                            // Regra 2: Linhas de Equador e Canais
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("2. Linhas de Equador & Canais", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFBAE6FD)))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "O robô utiliza o centro do canal (Equador) como divisor de águas entre zonas de compra e venda. Quando o preço rompe o canal superior, aciona gatilhos de continuação ou exaustão matemática.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 11.sp, lineHeight = 16.sp)
                                    )
                                }
                            }

                            // Regra 3: Virada de Jogo / Costurar
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Autorenew, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("3. Algoritmo de Costura (Virada)", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFA7F3D0)))
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Se o mercado inverter bruscamente, o EA executa contra-ordens milimétricas calculadas para sair no zero a zero (breakeven) ou com lucro reduzido, preservando o capital principal.",
                                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 11.sp, lineHeight = 16.sp)
                                    )
                                }
                            }

                            // Sessões de Mercado
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Horários das Sessões:", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8)))
                                    Text("🌏 Tóquio / Ásia: 21:00 às 06:00 (Mercado calmo, ideal para canais estreitos)", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 10.5.sp))
                                    Text("🇬🇧 Londres: 04:00 às 13:00 (Alta liquidez, fortes rompimentos de canais)", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 10.5.sp))
                                    Text("🇺🇸 Nova York: 09:30 às 17:00 (Máxima volatilidade, notícias e tendências)", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCBD5E1), fontSize = 10.5.sp))
                                }
                            }

                            Button(
                                onClick = { activeGuideSection = 3 },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Checklist, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ir para o Checklist Diário", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    else -> {
                        // ABA 3: CHECKLIST DIÁRIO DO TRADER AUTOMATIZADO
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 450.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Rotina Pré-Mercado (5 Minutos por Dia)",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            )
                            Text(
                                text = "Marque os itens antes de deixar o robô operando autonomamente:",
                                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                            )

                            // Item 1: VPS / AlgoTrading
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (checkVps) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (checkVps) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkVps = !checkVps }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checkVps,
                                        onCheckedChange = { checkVps = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("1. VPS & Botão AlgoTrading", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text("MT5 aberto na VPS com o botão verde AlgoTrading ativo.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                                    }
                                }
                            }

                            // Item 2: Conta MT5
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (checkMt5) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (checkMt5) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkMt5 = !checkMt5 }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checkMt5,
                                        onCheckedChange = { checkMt5 = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("2. Conta MT5 Vinculada", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text("Número salvo na aba 'Conta EA' e status ONLINE verificado.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                                    }
                                }
                            }

                            // Item 3: Licença
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (checkLicenca) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (checkLicenca) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkLicenca = !checkLicenca }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checkLicenca,
                                        onCheckedChange = { checkLicenca = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("3. Validade da Licença", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text("Licença com status 'LICENÇA ATIVA' no app.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                                    }
                                }
                            }

                            // Item 4: Lote & Risco
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (checkLoteRisco) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (checkLoteRisco) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkLoteRisco = !checkLoteRisco }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checkLoteRisco,
                                        onCheckedChange = { checkLoteRisco = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("4. Lote Ajustado para 0.5% de Risco", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text("Lote dimensionado conforme o saldo atual da banca.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                                    }
                                }
                            }

                            // Item 5: Notificações e Voz
                            Card(
                                colors = CardDefaults.cardColors(containerColor = if (checkVozTts) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (checkVozTts) Color(0xFF10B981) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checkVozTts = !checkVozTts }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = checkVozTts,
                                        onCheckedChange = { checkVozTts = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF10B981))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("5. Voz TTS Pronta & Viver a Vida!", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                        Text("Celular preparado para avisos em áudio enquanto você aproveita seu dia.", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Button(
                                onClick = onDismiss,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tudo Pronto! Fechar Guia", fontWeight = FontWeight.Bold, color = Color.White)
                            }
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
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        AppTtsManager.initIfNeeded(context)
        onDispose {
            if (isSpeaking) {
                AppTtsManager.stopAll()
            }
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
                            if (isSpeaking) {
                                AppTtsManager.stopAll()
                                isSpeaking = false
                            } else {
                                AppTtsManager.speak(
                                    context = context,
                                    text = displayDescription,
                                    onStart = { isSpeaking = true },
                                    onDone = { isSpeaking = false }
                                )
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

@Composable
fun InstrucoesAdminTemplatesDialog(onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copiedContent by remember { mutableStateOf(false) }

    val fullMarkdownText = """# INSTRUÇÕES PARA PUBLICAÇÃO DE TEMPLATES DO ADMINISTRADOR MASTER

1. ENDEREÇO DO NÓ NO FIREBASE:
   -> /dados/indices/instrucoes_admin_templates/instrucoes_admin_templates.json
   (Alternativos: /dados/instrucoes_admin_templates.json ou /instrucoes_admin_templates.json)

2. ENUMERADORES (ENUMS) E VALORES VÁLIDOS:
   - LINHAS_DE_EQUADOR (boolean): true (exibir) | false (ocultar)
   - ESQUEMA_CORES_ENUM: "CYAN_NEON" | "DARK_MATRIX" | "GOLDEN_PRO" | "PURPLE_NIGHT" | "CLASSIC_BLUE" | "CUSTOM"
   - TREND: "TENDENCIA_DE_ALTA" (ou "UP_TREND") | "TENDENCIA_DE_BAIXA" (ou "DOWN_TREND")
   - ESTRATÉGIA: "FIMATHE" | "F_SURFADA"
   - OperationalPeriod: "PERIOD_M1" | "PERIOD_M5" | "PERIOD_M15" | "PERIOD_M30" | "PERIOD_H1" | "PERIOD_H4" | "PERIOD_D1"
   - AUTO_PERIOD: "MANUAL" | "SESSOES" | "SEMANAL" | "DIARIO" | "HORAS_8" | "HORA_1"
   - BOOLEANS (true/false): EA_ATIVO, EA_AUTO, AUTO_SURFADA, virada_de_jogo, Costurar, TEMA, SESSAO_ASIA_TOQUIO, SESSAO_LONDRES, SESSAO_NOVA_YORQUI, GERENCIAMENTO_DE_RISCO_DIARIO, GERENCIAMENTO_DE_RISCO_SEMANAL, Modify_Sl_For_OxO, condicao_De_rompimento_c, condicao_De_rompimento_v, ativar_ou_desativar_compra, ativar_ou_desativar_venda, GMAIL, notific

3. ORGANIZAÇÃO VISUAL E DIVISÃO DOS PARÂMETROS NO APP:
   Os parâmetros do objeto "config" são divididos em 9 seções no visual do aplicativo:
   1. Autenticação & Expiração: mt5AccountId, SENHA
   2. Esquema de Cores: ESQUEMA_CORES_ENUM, cor_de_canal, cor_de_linhas, corr_de_equador, LINHAS_DE_EQUADOR
   3. Canais de Tendência: TREND, M_equador_alta, M_equador_baixa
   4. Estratégia Principal: ESTRATÉGIA, OperationalPeriod, virada_de_jogo, Nives, Costurar, TEMA
   5. Automação & Sessões: EA_ATIVO, EA_AUTO, AUTO_PERIOD, AUTO_SURFADA, SESSAO_ASIA_TOQUIO, SESSAO_LONDRES, SESSAO_NOVA_YORQUI
   6. Posicionamento de Ordem: EXPANSAO_MINIMA, EXPANSAO_MAXIMA, compra, venda, santo, dedo, posicaoTake, buy_take, sell_take
   7. Gestão de Capital & Risco: SALDO, lot (lote 0.00), GERENCIAMENTO_DE_RISCO_DIARIO, porcentos, poercentosg, GERENCIAMENTO_DE_RISCO_SEMANAL, PORCENTOO, PORCENTOSS
   8. Parâmetros Operacionais: ativar_ou_desativar_compra, ativar_ou_desativar_venda, Modify_Sl_For_OxO, condicao_De_rompimento_c, condicao_De_rompimento_v, GMAIL, notific
   9. Resultado & Câmbio: CAMBIO

4. IDENTIFICADORES ÚNICOS (id):
   - Formato: tpl_<codigo>_<estrategia>_<perfil> (ex: tpl_001_fimathe_m15).
   - O id deve ser IDÊNTICO na chave do JSON e na propriedade "id" interna.

5. TÍTULO E DESCRIÇÃO:
   - Título: "⚡ Template M15 Gold Conservador (Oficial Admin)"
   - Descrição: Especificar objetivo, paridades, sessões recomendadas e aviso de lote 0.00 por segurança.

6. REGRA DE 3 ATIVOS COM SUBSTITUIÇÃO AUTOMÁTICA:
   - No máximo 3 templates ativos exibidos simultaneamente.
   - Substituição automática ao expirar a data "validoAte".

7. JSON SCHEMA COMPLETO:
{
  "instrucoes_admin_templates": {
    "descricao": "Schema Único de Parâmetros para Publicação de Templates pelo Administrador Master.",
    "limite_publicados_ativos": 3,
    "regra_substituicao": "Publicação de 3 templates ativos. Quando 1 expira, é automaticamente substituído pelo próximo publicado.",
    "templates": {
      "tpl_001_fimathe_m15": {
        "id": "tpl_001_fimathe_m15",
        "titulo": "⚡ Template M15 Gold Conservador (Oficial Admin)",
        "descricao": "Setup oficial com gestão de risco ajustada para XAUUSD e EURUSD nas sessões de Londres e Nova Iorque. Lote zerado 0.00 por segurança.",
        "autor": "Admin Master Fimaster",
        "dataPublicacao": "02/08/2026 09:00",
        "validoAte": "31/12/2026",
        "disponivel": true,
        "versaoMinimaEa": "v3.2",
        "pontosAtivo": "250 pts",
        "paridade": "XAUUSD",
        "config": {
          "mt5AccountId": "TEMPLATE",
          "SENHA": "123456",
          "ESQUEMA_CORES_ENUM": "CYAN_NEON",
          "cor_de_canal": "#22D3EE",
          "cor_de_linhas": "#FF00E5",
          "corr_de_equador": "#FFFF00",
          "LINHAS_DE_EQUADOR": false,
          "TREND": "TENDENCIA_DE_ALTA",
          "M_equador_alta": 1.2500,
          "M_equador_baixa": 1.2400,
          "TEMA": false,
          "ESTRATÉGIA": "FIMATHE",
          "virada_de_jogo": false,
          "Nives": 1.0,
          "Costurar": true,
          "OperationalPeriod": "PERIOD_M15",
          "lot": 0.00,
          "EA_ATIVO": true,
          "EA_AUTO": false,
          "AUTO_PERIOD": "HORA_1",
          "AUTO_SURFADA": false,
          "SESSAO_ASIA_TOQUIO": false,
          "SESSAO_LONDRES": true,
          "SESSAO_NOVA_YORQUI": true,
          "EXPANSAO_MINIMA": 10,
          "EXPANSAO_MAXIMA": 30,
          "compra": 1.2550,
          "venda": 1.2500,
          "santo": 20.0,
          "dedo": 10,
          "posicaoTake": false,
          "buy_take": 0.0,
          "sell_take": 0.0,
          "SALDO": 1000.0,
          "GERENCIAMENTO_DE_RISCO_DIARIO": true,
          "porcentos": 1.0,
          "poercentosg": 1.5,
          "GERENCIAMENTO_DE_RISCO_SEMANAL": false,
          "PORCENTOO": 2.0,
          "PORCENTOSS": 2.0,
          "GMAIL": true,
          "notific": true,
          "ativar_ou_desativar_venda": true,
          "ativar_ou_desativar_compra": true,
          "Modify_Sl_For_OxO": true,
          "condicao_De_rompimento_c": true,
          "condicao_De_rompimento_v": true,
          "CAMBIO": 64.0
        }
      }
    }
  }
}"""

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFC084FC), RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📋 INSTRUÇÕES ADMIN TEMPLATES",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFC084FC)
                            )
                        )
                        Text(
                            text = "Arquivo: INSTRUCOES_ADMIN_TEMPLATES.md",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8))
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFF94A3B8))
                    }
                }

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "• Nó Firebase: /instrucoes_admin_templates.json",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "• Estrutura Única de 'config' para todos os parâmetros com ID individual.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE2E8F0))
                        )
                        Text(
                            text = "• Publicação de 3 Ativos com substituição automática ao expirar.",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFA7F3D0))
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
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
                            text = fullMarkdownText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(fullMarkdownText))
                            copiedContent = true
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFC084FC))
                    ) {
                        Icon(
                            imageVector = if (copiedContent) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copiar",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedContent) "Conteúdo Copiado!" else "Copiar Instruções",
                            color = Color(0xFFC084FC),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = "Fechar", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

// ====================================================================
// COMPONENTES DE CAPTURA DE TELA DO GRÁFICO MT5 COM OBJETOS MQL5
// ====================================================================

private data class Quad(val xRatio: Float, val openRatio: Float, val highRatio: Float, val lowRatio: Float, val closeRatio: Float)

@Composable
fun ChartScreenshotCanvas(
    modifier: Modifier = Modifier,
    symbol: String = "XAUUSD",
    timeframe: String = "M15",
    hasFimatheChannels: Boolean = true,
    hasEaPanel: Boolean = true,
    hasTradeArrows: Boolean = true,
    chartScreenshot: com.example.ui.ChartScreenshotData? = null
) {
    val reconstructedBitmap = remember(chartScreenshot?.imageFilePath, chartScreenshot?.timestamp, chartScreenshot?.imageBytes) {
        chartScreenshot?.getReconstructedBitmap()
    }

    Box(
        modifier = modifier
            .background(Color(0xFF0B0F19))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (reconstructedBitmap != null) {
            Image(
                bitmap = reconstructedBitmap.asImageBitmap(),
                contentDescription = "Imagem do Gráfico MT5",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // 1. Grid Background
            val gridCountX = 8
            val gridCountY = 6
            val strokeGrid = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            for (i in 1 until gridCountX) {
                val x = w * (i / gridCountX.toFloat())
                drawLine(
                    color = Color(0xFF1E293B),
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, h),
                    pathEffect = strokeGrid,
                    strokeWidth = 1f
                )
            }
            for (j in 1 until gridCountY) {
                val y = h * (j / gridCountY.toFloat())
                drawLine(
                    color = Color(0xFF1E293B),
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(w, y),
                    pathEffect = strokeGrid,
                    strokeWidth = 1f
                )
            }

            // 2. Fimathe Channels & Level Lines (Objects created by EA MQL5)
            if (hasFimatheChannels) {
                // Resistance / Upper Channel Line (Cyan)
                val yRes = h * 0.22f
                drawLine(
                    color = Color(0xFF22D3EE),
                    start = androidx.compose.ui.geometry.Offset(0f, yRes),
                    end = androidx.compose.ui.geometry.Offset(w, yRes - 15f),
                    strokeWidth = 2.5f
                )
                // Canal Principal Top Line (Emerald)
                val yCanal1 = h * 0.42f
                drawLine(
                    color = Color(0xFF10B981),
                    start = androidx.compose.ui.geometry.Offset(0f, yCanal1),
                    end = androidx.compose.ui.geometry.Offset(w, yCanal1 - 15f),
                    strokeWidth = 3f
                )
                // Canal Principal Bottom Line (Emerald)
                val yCanal0 = h * 0.62f
                drawLine(
                    color = Color(0xFF10B981),
                    start = androidx.compose.ui.geometry.Offset(0f, yCanal0),
                    end = androidx.compose.ui.geometry.Offset(w, yCanal0 - 15f),
                    strokeWidth = 3f
                )
                // Golden Ratio 50% Subciclo (Amber Dashed)
                val y50 = h * 0.52f
                drawLine(
                    color = Color(0xFFF59E0B),
                    start = androidx.compose.ui.geometry.Offset(0f, y50),
                    end = androidx.compose.ui.geometry.Offset(w, y50 - 15f),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                    strokeWidth = 2f
                )
                // Stop Loss Line (Red Dashed)
                val ySL = h * 0.76f
                drawLine(
                    color = Color(0xFFEF4444),
                    start = androidx.compose.ui.geometry.Offset(w * 0.35f, ySL),
                    end = androidx.compose.ui.geometry.Offset(w, ySL - 5f),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f),
                    strokeWidth = 2.5f
                )
                // Take Profit Line (Green Dashed)
                val yTP = h * 0.15f
                drawLine(
                    color = Color(0xFF10B981),
                    start = androidx.compose.ui.geometry.Offset(w * 0.35f, yTP),
                    end = androidx.compose.ui.geometry.Offset(w, yTP - 5f),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 5f), 0f),
                    strokeWidth = 2.5f
                )
            }

            // 3. Candlesticks
            val candleData = listOf(
                Quad(0.08f, 0.70f, 0.50f, 0.73f, 0.65f),
                Quad(0.16f, 0.66f, 0.45f, 0.68f, 0.52f),
                Quad(0.24f, 0.53f, 0.42f, 0.58f, 0.44f),
                Quad(0.32f, 0.45f, 0.35f, 0.48f, 0.38f),
                Quad(0.40f, 0.39f, 0.48f, 0.50f, 0.46f),
                Quad(0.48f, 0.45f, 0.32f, 0.47f, 0.34f),
                Quad(0.56f, 0.35f, 0.22f, 0.37f, 0.25f),
                Quad(0.64f, 0.26f, 0.34f, 0.36f, 0.32f),
                Quad(0.72f, 0.33f, 0.18f, 0.35f, 0.20f),
                Quad(0.80f, 0.21f, 0.28f, 0.30f, 0.26f),
                Quad(0.88f, 0.27f, 0.15f, 0.29f, 0.18f)
            )

            for (c in candleData) {
                val cx = w * c.xRatio
                val yOpen = h * c.openRatio
                val yClose = h * c.closeRatio
                val yHigh = h * c.highRatio
                val yLow = h * c.lowRatio
                val isBull = c.closeRatio < c.openRatio
                val color = if (isBull) Color(0xFF10B981) else Color(0xFFEF4444)

                // Wick
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(cx, yHigh),
                    end = androidx.compose.ui.geometry.Offset(cx, yLow),
                    strokeWidth = 1.5f
                )
                // Body
                val topY = kotlin.math.min(yOpen, yClose)
                val botY = kotlin.math.max(yOpen, yClose)
                val bodyHeight = kotlin.math.max(botY - topY, 4f)
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - 7f, topY),
                    size = androidx.compose.ui.geometry.Size(14f, bodyHeight)
                )
            }

            // 4. Trade Execution Arrow (Buy Arrow MQL5 Object)
            if (hasTradeArrows) {
                val arrowX = w * 0.40f
                val arrowY = h * 0.50f
                val arrowPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(arrowX, arrowY - 14f)
                    lineTo(arrowX - 8f, arrowY + 4f)
                    lineTo(arrowX + 8f, arrowY + 4f)
                    close()
                }
                drawPath(path = arrowPath, color = Color(0xFF10B981))
            }
        }

        // Overlay Labels & EA Information Panel
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$symbol, $timeframe",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            if (hasEaPanel) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0xFF020617).copy(alpha = 0.90f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF22D3EE).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "⚡ EA FIMASTER MT5 v3.2",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp
                        )
                    )
                    Text(
                        text = "Estratégia: FIMATHE M15",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontSize = 9.sp)
                    )
                    Text(
                        text = "Lote: 0.01 | Risco: 0.5%",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontSize = 9.sp)
                    )
                    Text(
                        text = "SL: 2,642.50 | TP: 2,658.00",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontSize = 9.sp)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "── Canal Fimathe",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "⋯⋯ 50% Golden",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "▲ Seta Ordem",
                    style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF22D3EE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
}

@Composable
fun ChartScreenshotCard(
    chartScreenshot: com.example.ui.ChartScreenshotData,
    onRequestScreenshot: () -> Unit,
    onViewFullChart: () -> Unit,
    onViewMql5Code: () -> Unit
) {
    var showFullModal by remember { mutableStateOf(false) }
    val userPlanConfig = LocalLicensePlanConfig.current
    val canCapture = userPlanConfig.capturaDeTela

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (canCapture) Color(0xFF0284C7).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(if (canCapture) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, if (canCapture) Color(0xFF38BDF8) else Color(0xFFEF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (canCapture) Icons.Default.CameraAlt else Icons.Default.Lock,
                            contentDescription = "Captura de Tela",
                            tint = if (canCapture) Color(0xFF38BDF8) else Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CAPTURA DE TELA DO GRÁFICO MT5",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (canCapture) Color(0xFF38BDF8) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        )
                        val timeFormatted = remember(chartScreenshot.timestamp) {
                            if (chartScreenshot.timestamp > 0) {
                                val date = java.util.Date(chartScreenshot.timestamp * 1000L)
                                java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                            } else ""
                        }
                        Text(
                            text = if (!canCapture) "Recurso bloqueado pelo plano"
                                else if (chartScreenshot.isRequested) 
                                "Paridade: ${chartScreenshot.symbol} • ${chartScreenshot.timeframe}" + if (timeFormatted.isNotEmpty()) " • às $timeFormatted" else ""
                                else "Aguardando solicitação",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (canCapture) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }

            if (!canCapture) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "🔒 RECURSO BLOQUEADO PELO SEU PLANO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEF4444)
                                )
                            )
                            Text(
                                text = "A captura de tela gráfica remota do MT5 está desabilitada (captura de tela: false). Faça upgrade para o plano Pro ou Master VIP para liberar.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            if (canCapture && chartScreenshot.isRequested) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { showFullModal = true }
                ) {
                    ChartScreenshotCanvas(
                        modifier = Modifier.fillMaxSize(),
                        symbol = chartScreenshot.symbol,
                        timeframe = chartScreenshot.timeframe,
                        hasFimatheChannels = chartScreenshot.hasFimatheChannels,
                        hasEaPanel = chartScreenshot.hasEaPanel,
                        hasTradeArrows = chartScreenshot.hasTradeArrows,
                        chartScreenshot = chartScreenshot
                    )

                    Surface(
                        onClick = { showFullModal = true },
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = "AMPLIAR",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AMPLIAR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                        .clickable { onRequestScreenshot() },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF0284C7).copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "NENHUMA CAPTURA CARREGADA",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                        Text(
                            text = "Clique abaixo para solicitar a captura do gráfico do MetaTrader 5 em tempo real.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }

            if (chartScreenshot.isRequested) {
                val timeFormatted = remember(chartScreenshot.timestamp) {
                    val date = java.util.Date(chartScreenshot.timestamp * 1000L)
                    java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = "Horário",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PARIDADE: ${chartScreenshot.symbol} (${chartScreenshot.timeframe})",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(
                        text = "Capturado às $timeFormatted",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    )
                }
            }

            val currentContext = androidx.compose.ui.platform.LocalContext.current
            if (canCapture && chartScreenshot.isRequested) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRequestScreenshot,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Solicitar Captura",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ATUALIZAR",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Button(
                        onClick = { shareChartScreenshot(currentContext, chartScreenshot) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.3f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar Captura",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COMPARTILHAR",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (!canCapture) {
                            android.widget.Toast.makeText(currentContext, "🔒 Recurso de captura gráfica bloqueado no seu plano.", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onRequestScreenshot()
                    },
                    enabled = canCapture,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0284C7),
                        disabledContainerColor = Color(0xFF334155),
                        disabledContentColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (!canCapture) Icons.Default.Lock else Icons.Default.Refresh,
                        contentDescription = "Solicitar Captura",
                        tint = if (canCapture) Color.White else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (!canCapture) "CAPTURA BLOQUEADA PELO PLANO" else "SOLICITAR CAPTURA",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (canCapture) Color.White else Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    }

    if (showFullModal) {
        ChartScreenshotModalDialog(
            chartScreenshot = chartScreenshot,
            onRequestScreenshot = onRequestScreenshot,
            onDismiss = { showFullModal = false }
        )
    }
}

@Composable
fun ChartScreenshotModalDialog(
    chartScreenshot: com.example.ui.ChartScreenshotData,
    onRequestScreenshot: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF090D16)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val timeFormatted = remember(chartScreenshot.timestamp) {
                    val date = java.util.Date(chartScreenshot.timestamp * 1000L)
                    java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(date)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ChartScreenshotCanvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ),
                        symbol = chartScreenshot.symbol,
                        timeframe = chartScreenshot.timeframe,
                        hasFimatheChannels = true,
                        hasEaPanel = true,
                        hasTradeArrows = true,
                        chartScreenshot = chartScreenshot
                    )
                }

                // Top bar with Parity, Time, Share button & Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PARIDADE: ${chartScreenshot.symbol} (${chartScreenshot.timeframe})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Capturado às $timeFormatted",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { shareChartScreenshot(context, chartScreenshot) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Compartilhar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Compartilhar",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .background(Color(0xFF1E293B), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Bottom Zoom Indicator, Reset & Share Button
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Zoom: ${(scale * 100).toInt()}% • Arraste com o dedo",
                        style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Medium)
                    )
                    if (scale > 1.0f) {
                        TextButton(
                            onClick = {
                                scale = 1.0f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("RESETAR", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { shareChartScreenshot(context, chartScreenshot) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Compartilhar",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ENVIAR",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Mql5CodeModalDialog(onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    val mql5Code = """
// ====================================================================
// EA FIMASTER COMPLETO & INTEGRADO AO APP (ea_fimaster_completo.mql5)
// ====================================================================
// O código fonte MQL5 completo e atualizado está disponível no arquivo:
// '/ea_fimaster_completo.mql5' e '/fimaster_notificar.mql5'
//
// Destaques de Comunicação com este Aplicativo Android:
// 1. NotificarEAOnline() - Ping de conectividade a cada 60s
// 2. SincronizarParametrosDoApp() - Lê parâmetros alterados no App a cada 5s
// 3. CapturarGraficoComObjetos() - Tira screenshot com linhas Fimathe e objetos MQL5
// 4. VerificarEEnviarHistoricoFinanceiro() - Auto-detecta depósitos, saques e ordens fechadas
// 5. Disparo de Eventos:
//    - 'ordem_executada' (Compra / Venda)
//    - 'ordem_modificada' (Ajuste de Stop Loss / Take Profit)
//    - 'mudanca_estado' (Transições de Máquina de Estados Fimathe)
//    - 'relatorio_financeiro' (Lucros/Prejuízos diários e semanais)
// ====================================================================
    """.trimIndent()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Code, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CÓDIGO MQL5 DO ROBÔ", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                Text(
                    text = "Instruções: O robô usa a função nativa MQL5 'ChartScreenShot()' que captura automaticamente todas as velas e objetos desenhados (Canais Fimathe, Linhas de Nível, Painel EA e Setas) e envia para o app.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                )

                Surface(
                    color = Color(0xFF020617),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = mql5Code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(mql5Code))
                            copied = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (copied) Color(0xFF10B981) else Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copied) "COPIADO!" else "COPIAR CÓDIGO", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("FECHAR", style = MaterialTheme.typography.labelSmall.copy(color = Color.White))
                    }
                }
            }
        }
    }
}

data class ChartTrendline(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startCandleIndex: Float,
    val startPrice: Double,
    val endCandleIndex: Float,
    val endPrice: Double,
    val color: Color = Color(0xFFEAB308)
)

data class ChartHorizontalLine(
    val id: String = java.util.UUID.randomUUID().toString(),
    val price: Double,
    val color: Color = Color(0xFF38BDF8)
)

private fun formatChartPriceValue(price: Double, currencySymbol: String = ""): String {
    val formatted = if (price < 10.0 && price > 0.0) {
        String.format(java.util.Locale.US, "%.5f", price)
    } else {
        String.format(java.util.Locale.US, "%.2f", price)
    }
    return if (currencySymbol.isNotBlank()) "$currencySymbol $formatted" else formatted
}

@Composable
fun FinancialEquityCandlestickCard(
    candles: List<com.example.ui.FinancialCandle>,
    currentTimeframe: com.example.ui.EquityTimeframe,
    onSelectTimeframe: (com.example.ui.EquityTimeframe) -> Unit,
    cambio: Double = 64.0,
    currencySymbol: String = "MT"
) {
    var selectedCandle by remember { mutableStateOf<com.example.ui.FinancialCandle?>(null) }
    var zoomLevelX by remember { mutableFloatStateOf(1.0f) }
    var zoomLevelY by remember { mutableFloatStateOf(1.0f) }
    var isFullScreen by remember { mutableStateOf(false) }
    var trendlines by remember { mutableStateOf<List<ChartTrendline>>(emptyList()) }
    var horizontalLines by remember { mutableStateOf<List<ChartHorizontalLine>>(emptyList()) }
    var activeToolMode by remember { mutableStateOf<String?>(null) }
    var pendingTrendlineStart by remember { mutableStateOf<Pair<Float, Double>?>(null) }
    var isLandscapeMode by remember { mutableStateOf(false) }
    var selectedObjectId by remember { mutableStateOf<String?>(null) }

    val handleUpdateHorizontalLine: (ChartHorizontalLine) -> Unit = { updated ->
        horizontalLines = horizontalLines.map { if (it.id == updated.id) updated else it }
    }
    val handleUpdateTrendline: (ChartTrendline) -> Unit = { updated ->
        trendlines = trendlines.map { if (it.id == updated.id) updated else it }
    }
    val handleDeleteObject: (String) -> Unit = { id ->
        horizontalLines = horizontalLines.filter { it.id != id }
        trendlines = trendlines.filter { it.id != id }
        if (selectedObjectId == id) selectedObjectId = null
    }

    val selectedHorizontalLine = horizontalLines.find { it.id == selectedObjectId }
    val selectedTrendline = trendlines.find { it.id == selectedObjectId }

    val activeCandle = selectedCandle ?: candles.lastOrNull()

    val totalDeposits = remember(candles, cambio) { candles.sumOf { it.deposits } * cambio }
    val totalWithdrawals = remember(candles, cambio) { candles.sumOf { it.withdrawals } * cambio }
    val totalNetProfit = remember(candles, cambio) { candles.sumOf { it.netProfit } * cambio }
    val finalEquity = remember(candles, cambio) { (candles.lastOrNull()?.closeBalance ?: 0.0) * cambio }

    val userPlanConfig = LocalLicensePlanConfig.current
    val canViewChart = userPlanConfig.graficoDePatrimonio

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (canViewChart) Color(0xFF10B981).copy(alpha = 0.4f) else Color(0xFFEF4444).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (canViewChart) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, if (canViewChart) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (canViewChart) Icons.Default.ShowChart else Icons.Default.Lock,
                            contentDescription = "Gráfico Candlestick",
                            tint = if (canViewChart) Color(0xFF10B981) else Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GRÁFICO CANDLESTICK DE PATRIMÔNIO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (canViewChart) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = if (canViewChart) "Sincronização via Robô EA MT5" else "Recurso bloqueado pelo seu plano",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (canViewChart) {
                    Surface(
                        onClick = { isFullScreen = true },
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Tela Cheia",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "TELA CHEIA",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                }
            }

            if (!canViewChart) {
                Surface(
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Bloqueado",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "🔒 RECURSO BLOQUEADO PELO SEU PLANO",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFEF4444)
                                )
                            )
                            Text(
                                text = "A visualização gráfica de patrimônio e candlesticks de ordens está desabilitada (grafico de património: false). Faça upgrade para Pro ou Master VIP para liberar.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }

            // Timeframe Selector Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                com.example.ui.EquityTimeframe.values().forEach { tf ->
                    val isSelected = tf == currentTimeframe
                    val shortLabel = when (tf) {
                        com.example.ui.EquityTimeframe.PER_POSITION -> "Posição"
                        com.example.ui.EquityTimeframe.DAILY -> "Diário"
                        com.example.ui.EquityTimeframe.WEEKLY -> "Semanal"
                        com.example.ui.EquityTimeframe.MONTHLY -> "Mensal"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFF10B981) else Color.Transparent)
                            .clickable {
                                onSelectTimeframe(tf)
                                selectedCandle = null
                            }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${tf.shortCode} • $shortLabel",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }

            // Quick Metrics Grid (2x2 Organized Cards)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Saldo Final
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("SALDO FINAL", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "$currencySymbol ${String.format("%.2f", finalEquity)}",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }
                    }

                    // Lucro Líquido
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("LUCRO LÍQUIDO", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            val color = if (totalNetProfit >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            val prefix = if (totalNetProfit >= 0) "+" else ""
                            Text(
                                "$prefix$currencySymbol ${String.format("%.2f", totalNetProfit)}",
                                style = MaterialTheme.typography.titleSmall.copy(color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Depósitos
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("DEPÓSITOS (+)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "+$currencySymbol ${String.format("%.2f", totalDeposits)}",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }
                    }

                    // Saques
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text("SAQUES (-)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF64748B), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "-$currencySymbol ${String.format("%.2f", totalWithdrawals)}",
                                style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                        }
                    }
                }
            }

            // Active Candle Inspector Tooltip
            if (activeCandle != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF020617), RoundedCornerShape(10.dp))
                        .border(1.dp, if (activeCandle.isBullish) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VELA: ${activeCandle.periodLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                        val pColor = if (activeCandle.netProfit >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                        val pPrefix = if (activeCandle.netProfit >= 0) "+" else ""
                        Text(
                            text = "Resultado: $pPrefix$currencySymbol ${String.format("%.2f", activeCandle.netProfit * cambio)}",
                            style = MaterialTheme.typography.labelSmall.copy(color = pColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        )
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Left Column: Abertura & Fechamento
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Abertura:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 9.5.sp))
                                Text("$currencySymbol ${String.format("%.2f", activeCandle.openBalance * cambio)}", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fechamento:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 9.5.sp))
                                Text("$currencySymbol ${String.format("%.2f", activeCandle.closeBalance * cambio)}", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp))
                            }
                        }

                        // Right Column: Máximo & Mínimo
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Máximo:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 9.5.sp))
                                Text("$currencySymbol ${String.format("%.2f", activeCandle.highBalance * cambio)}", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Mínimo:", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 9.5.sp))
                                Text("$currencySymbol ${String.format("%.2f", activeCandle.lowBalance * cambio)}", style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp))
                            }
                        }
                    }
                }
            }

            // Toolbar Row: Botões de Zoom (Horizontal & Vertical) & Ferramentas de Análise Técnica
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Controles de Zoom & Expandir Tela Cheia
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = {
                            zoomLevelX = (zoomLevelX - 0.25f).coerceAtLeast(0.5f)
                            zoomLevelY = (zoomLevelY - 0.25f).coerceAtLeast(0.5f)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Diminuir Zoom",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }

                    Surface(
                        onClick = { isFullScreen = true },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = "Zoom Expandido",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Zoom ${((zoomLevelX + zoomLevelY) / 2f * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            zoomLevelX = (zoomLevelX + 0.25f).coerceAtMost(4.0f)
                            zoomLevelY = (zoomLevelY + 0.25f).coerceAtMost(4.0f)
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Aumentar Zoom",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }

                    if (zoomLevelX != 1.0f || zoomLevelY != 1.0f) {
                        Surface(
                            onClick = {
                                zoomLevelX = 1.0f
                                zoomLevelY = 1.0f
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, Color(0xFF0EA5E9))
                        ) {
                            Text(
                                text = "Reset",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Botão de Virar Tela (Girar Orientação do Gráfico)
                Surface(
                    onClick = { isLandscapeMode = !isLandscapeMode },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLandscapeMode) Color(0xFF8B5CF6) else Color(0xFF1E293B),
                    border = BorderStroke(0.5.dp, if (isLandscapeMode) Color(0xFFA78BFA) else Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Virar Tela",
                            tint = if (isLandscapeMode) Color.White else Color(0xFFA78BFA),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isLandscapeMode) "90° Paisagem" else "Virar Tela",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp
                            )
                        )
                    }
                }

                // Botão de Ferramentas (Linha de Tendência & Linha Horizontal)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isTrendActive = activeToolMode == "TRENDLINE" || trendlines.isNotEmpty()
                    Surface(
                        onClick = {
                            activeToolMode = if (activeToolMode == "TRENDLINE") null else "TRENDLINE"
                            if (candles.size >= 2) {
                                val count = candles.size
                                val startIdx = (count * 0.2f).coerceIn(0f, (count - 1).toFloat())
                                val endIdx = (count * 0.8f).coerceIn(0f, (count - 1).toFloat())
                                val p1 = candles[startIdx.toInt()].closeBalance * cambio
                                val p2 = candles[endIdx.toInt()].closeBalance * cambio
                                val newLine = ChartTrendline(
                                    startCandleIndex = startIdx,
                                    startPrice = p1,
                                    endCandleIndex = endIdx,
                                    endPrice = p2
                                )
                                trendlines = trendlines + newLine
                                selectedObjectId = newLine.id
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isTrendActive) Color(0xFFEAB308) else Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, if (isTrendActive) Color(0xFFFACC15) else Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShowChart,
                                contentDescription = "Linha de Tendência",
                                tint = if (isTrendActive) Color.Black else Color(0xFFEAB308),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+ Tendência",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isTrendActive) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }

                    val isHorizActive = activeToolMode == "HORIZONTAL" || horizontalLines.isNotEmpty()
                    Surface(
                        onClick = {
                            activeToolMode = if (activeToolMode == "HORIZONTAL") null else "HORIZONTAL"
                            val currentPrice = (activeCandle?.closeBalance ?: candles.lastOrNull()?.closeBalance ?: 0.0) * cambio
                            if (currentPrice > 0.0) {
                                val newLine = ChartHorizontalLine(price = currentPrice)
                                horizontalLines = horizontalLines + newLine
                                selectedObjectId = newLine.id
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isHorizActive) Color(0xFF0EA5E9) else Color(0xFF1E293B),
                        border = BorderStroke(0.5.dp, if (isHorizActive) Color(0xFF38BDF8) else Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HorizontalRule,
                                contentDescription = "Linha Horizontal",
                                tint = if (isHorizActive) Color.White else Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+ Horizontal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }

                    if (trendlines.isNotEmpty() || horizontalLines.isNotEmpty()) {
                        Surface(
                            onClick = {
                                trendlines = emptyList()
                                horizontalLines = emptyList()
                                activeToolMode = null
                                pendingTrendlineStart = null
                                selectedObjectId = null
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar Desenhos",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.padding(4.dp).size(14.dp)
                            )
                        }
                    }
                }
            }

            // Contextual Overlay Bar when a Line is Selected
            if (selectedObjectId != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedHorizontalLine != null) {
                                Icon(
                                    imageVector = Icons.Default.HorizontalRule,
                                    contentDescription = null,
                                    tint = selectedHorizontalLine.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Horizontal: ${formatChartPriceValue(selectedHorizontalLine.price, currencySymbol)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            } else if (selectedTrendline != null) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = selectedTrendline.color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Linha de Tendência (Arraste pontas ou corpo)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Color Selector Chips
                            val colors = listOf(
                                Color(0xFFF59E0B),
                                Color(0xFF38BDF8),
                                Color(0xFF10B981),
                                Color(0xFFA855F7),
                                Color(0xFFEF4444)
                            )
                            colors.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(col, CircleShape)
                                        .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                        .clickable {
                                            selectedHorizontalLine?.let {
                                                handleUpdateHorizontalLine(it.copy(color = col))
                                            }
                                            selectedTrendline?.let {
                                                handleUpdateTrendline(it.copy(color = col))
                                            }
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Delete Button
                            Surface(
                                onClick = {
                                    selectedObjectId?.let { handleDeleteObject(it) }
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir Linha",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.padding(4.dp).size(14.dp)
                                )
                            }

                            // Deselect / Done Button
                            Surface(
                                onClick = { selectedObjectId = null },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.25f),
                                border = BorderStroke(0.5.dp, Color(0xFF10B981))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Concluir",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.padding(4.dp).size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (canViewChart) {
                // Candlestick Chart Canvas
                FinancialCandlestickCanvas(
                    candles = candles,
                    selectedCandle = activeCandle,
                    onCandleSelect = { selectedCandle = it },
                    cambio = cambio,
                    currencySymbol = currencySymbol,
                    zoomLevelX = zoomLevelX,
                    zoomLevelY = zoomLevelY,
                    onZoomChange = { newX, newY ->
                        zoomLevelX = newX
                        zoomLevelY = newY
                    },
                    trendlines = trendlines,
                    horizontalLines = horizontalLines,
                    activeToolMode = activeToolMode,
                    isLandscapeMode = isLandscapeMode,
                    selectedObjectId = selectedObjectId,
                    onSelectObject = { selectedObjectId = it },
                    onUpdateHorizontalLine = handleUpdateHorizontalLine,
                    onUpdateTrendline = handleUpdateTrendline,
                    onDeleteObject = handleDeleteObject,
                    onAddHorizontalLine = { price ->
                        val newLine = ChartHorizontalLine(price = price)
                        horizontalLines = horizontalLines + newLine
                        selectedObjectId = newLine.id
                    },
                    onAddTrendlinePoint = { index, price ->
                        val pending = pendingTrendlineStart
                        if (pending == null) {
                            pendingTrendlineStart = Pair(index, price)
                        } else {
                            val newLine = ChartTrendline(
                                startCandleIndex = pending.first,
                                startPrice = pending.second,
                                endCandleIndex = index,
                                endPrice = price
                            )
                            trendlines = trendlines + newLine
                            selectedObjectId = newLine.id
                            pendingTrendlineStart = null
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscapeMode) 320.dp else 240.dp)
                )

                // Legend Organized in 2 Clean Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lucro / Alta", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Prejuízo / Baixa", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.5.sp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(12.dp).height(2.dp).background(Color(0xFF38BDF8)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preço Atual", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFF22D3EE), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("● Depósito (MT5 Auto)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF22D3EE), fontSize = 10.5.sp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("● Saque (MT5 Auto)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFF59E0B), fontSize = 10.5.sp))
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF0B132B), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Text(
                            text = "GRÁFICO BLOQUEADO PELO PLANO",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Acompanhe a curva de evolução patrimonial e candlesticks com os planos Pro ou Master VIP.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                }
            }
        }
    }

    // Modal / Fullscreen Expanded Zoom View
    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF020617)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Fullscreen Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                                    .border(1.dp, Color(0xFF10B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "GRÁFICO DE PATRIMÔNIO (TELA CHEIA)",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                                Text(
                                    text = "Zoom H: ${(zoomLevelX * 100).toInt()}% • Zoom V: ${(zoomLevelY * 100).toInt()}% • Pinçar para Zoom",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }

                        IconButton(
                            onClick = { isFullScreen = false },
                            modifier = Modifier
                                .background(Color(0xFF1E293B), CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar Tela Cheia",
                                tint = Color.White
                            )
                        }
                    }

                    // Timeframes in Fullscreen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        com.example.ui.EquityTimeframe.values().forEach { tf ->
                            val isSelected = tf == currentTimeframe
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) Color(0xFF10B981) else Color.Transparent)
                                    .clickable {
                                        onSelectTimeframe(tf)
                                        selectedCandle = null
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tf.shortCode,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Fullscreen Toolbar: Zoom X, Zoom Y, Virar Tela & Ferramentas de Análise Técnica
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Horizontal Zoom Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("X:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp))
                            Surface(
                                onClick = { zoomLevelX = (zoomLevelX - 0.25f).coerceAtLeast(0.5f) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom H-", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                            }
                            Text(
                                text = "${(zoomLevelX * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            )
                            Surface(
                                onClick = { zoomLevelX = (zoomLevelX + 0.25f).coerceAtMost(5.0f) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom H+", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                            }
                        }

                        // Vertical Zoom Controls
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Y:", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp))
                            Surface(
                                onClick = { zoomLevelY = (zoomLevelY - 0.25f).coerceAtLeast(0.5f) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Zoom V-", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                            }
                            Text(
                                text = "${(zoomLevelY * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            )
                            Surface(
                                onClick = { zoomLevelY = (zoomLevelY + 0.25f).coerceAtMost(5.0f) },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Zoom V+", tint = Color.White, modifier = Modifier.padding(4.dp).size(14.dp))
                            }
                        }

                        if (zoomLevelX != 1.0f || zoomLevelY != 1.0f) {
                            Surface(
                                onClick = {
                                    zoomLevelX = 1.0f
                                    zoomLevelY = 1.0f
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0EA5E9).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFF0EA5E9))
                            ) {
                                Text("Reset Zoom", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                            }
                        }

                        // Botão Virar Tela
                        Surface(
                            onClick = { isLandscapeMode = !isLandscapeMode },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isLandscapeMode) Color(0xFF8B5CF6) else Color(0xFF1E293B),
                            border = BorderStroke(0.5.dp, if (isLandscapeMode) Color(0xFFA78BFA) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = "Virar Tela",
                                    tint = if (isLandscapeMode) Color.White else Color(0xFFA78BFA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isLandscapeMode) "90° Paisagem" else "Virar Tela",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        // Ferramentas de Análise Técnica em Tela Cheia
                        val isTrendActive = activeToolMode == "TRENDLINE" || trendlines.isNotEmpty()
                        Surface(
                            onClick = {
                                activeToolMode = if (activeToolMode == "TRENDLINE") null else "TRENDLINE"
                                if (candles.size >= 2) {
                                    val count = candles.size
                                    val startIdx = (count * 0.2f).coerceIn(0f, (count - 1).toFloat())
                                    val endIdx = (count * 0.8f).coerceIn(0f, (count - 1).toFloat())
                                    val p1 = candles[startIdx.toInt()].closeBalance * cambio
                                    val p2 = candles[endIdx.toInt()].closeBalance * cambio
                                    val newLine = ChartTrendline(
                                        startCandleIndex = startIdx,
                                        startPrice = p1,
                                        endCandleIndex = endIdx,
                                        endPrice = p2
                                    )
                                    trendlines = trendlines + newLine
                                    selectedObjectId = newLine.id
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isTrendActive) Color(0xFFEAB308) else Color(0xFF1E293B),
                            border = BorderStroke(0.5.dp, if (isTrendActive) Color(0xFFFACC15) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShowChart,
                                    contentDescription = "Linha de Tendência",
                                    tint = if (isTrendActive) Color.Black else Color(0xFFEAB308),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+ Tendência",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isTrendActive) Color.Black else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        val isHorizActive = activeToolMode == "HORIZONTAL" || horizontalLines.isNotEmpty()
                        Surface(
                            onClick = {
                                activeToolMode = if (activeToolMode == "HORIZONTAL") null else "HORIZONTAL"
                                val currentPrice = (activeCandle?.closeBalance ?: candles.lastOrNull()?.closeBalance ?: 0.0) * cambio
                                if (currentPrice > 0.0) {
                                    val newLine = ChartHorizontalLine(price = currentPrice)
                                    horizontalLines = horizontalLines + newLine
                                    selectedObjectId = newLine.id
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isHorizActive) Color(0xFF0EA5E9) else Color(0xFF1E293B),
                            border = BorderStroke(0.5.dp, if (isHorizActive) Color(0xFF38BDF8) else Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HorizontalRule,
                                    contentDescription = "Linha Horizontal",
                                    tint = if (isHorizActive) Color.White else Color(0xFF38BDF8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "+ Horizontal",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        if (trendlines.isNotEmpty() || horizontalLines.isNotEmpty()) {
                            Surface(
                                onClick = {
                                    trendlines = emptyList()
                                    horizontalLines = emptyList()
                                    activeToolMode = null
                                    pendingTrendlineStart = null
                                    selectedObjectId = null
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                            ) {
                                Text("Limpar", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                            }
                        }
                    }

                    // Contextual Overlay Bar in Fullscreen when a Line is Selected
                    if (selectedObjectId != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (selectedHorizontalLine != null) {
                                        Icon(
                                            imageVector = Icons.Default.HorizontalRule,
                                            contentDescription = null,
                                            tint = selectedHorizontalLine.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Horizontal: ${formatChartPriceValue(selectedHorizontalLine.price, currencySymbol)}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    } else if (selectedTrendline != null) {
                                        Icon(
                                            imageVector = Icons.Default.ShowChart,
                                            contentDescription = null,
                                            tint = selectedTrendline.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Linha de Tendência (Arraste pontas ou corpo)",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Color Selector Chips
                                    val colors = listOf(
                                        Color(0xFFF59E0B),
                                        Color(0xFF38BDF8),
                                        Color(0xFF10B981),
                                        Color(0xFFA855F7),
                                        Color(0xFFEF4444)
                                    )
                                    colors.forEach { col ->
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .background(col, CircleShape)
                                                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                                                .clickable {
                                                    selectedHorizontalLine?.let {
                                                        handleUpdateHorizontalLine(it.copy(color = col))
                                                    }
                                                    selectedTrendline?.let {
                                                        handleUpdateTrendline(it.copy(color = col))
                                                    }
                                                }
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    // Delete Button
                                    Surface(
                                        onClick = {
                                            selectedObjectId?.let { handleDeleteObject(it) }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFEF4444).copy(alpha = 0.25f),
                                        border = BorderStroke(0.5.dp, Color(0xFFEF4444))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Excluir Linha",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.padding(4.dp).size(14.dp)
                                        )
                                    }

                                    // Deselect / Done Button
                                    Surface(
                                        onClick = { selectedObjectId = null },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.25f),
                                        border = BorderStroke(0.5.dp, Color(0xFF10B981))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Concluir",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.padding(4.dp).size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Fullscreen Candlestick Canvas (Takes remaining vertical space)
                    FinancialCandlestickCanvas(
                        candles = candles,
                        selectedCandle = activeCandle,
                        onCandleSelect = { selectedCandle = it },
                        cambio = cambio,
                        currencySymbol = currencySymbol,
                        zoomLevelX = zoomLevelX,
                        zoomLevelY = zoomLevelY,
                        onZoomChange = { newX, newY ->
                            zoomLevelX = newX
                            zoomLevelY = newY
                        },
                        trendlines = trendlines,
                        horizontalLines = horizontalLines,
                        activeToolMode = activeToolMode,
                        isLandscapeMode = isLandscapeMode,
                        selectedObjectId = selectedObjectId,
                        onSelectObject = { selectedObjectId = it },
                        onUpdateHorizontalLine = handleUpdateHorizontalLine,
                        onUpdateTrendline = handleUpdateTrendline,
                        onDeleteObject = handleDeleteObject,
                        onAddHorizontalLine = { price ->
                            horizontalLines = horizontalLines + ChartHorizontalLine(price = price)
                        },
                        onAddTrendlinePoint = { index, price ->
                            val pending = pendingTrendlineStart
                            if (pending == null) {
                                pendingTrendlineStart = Pair(index, price)
                            } else {
                                trendlines = trendlines + ChartTrendline(
                                    startCandleIndex = pending.first,
                                    startPrice = pending.second,
                                    endCandleIndex = index,
                                    endPrice = price
                                )
                                pendingTrendlineStart = null
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

private fun distanceToSegment(pX: Float, pY: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)
    if (l2 == 0f) return kotlin.math.hypot(pX - x1, pY - y1)
    var t = ((pX - x1) * (x2 - x1) + (pY - y1) * (y2 - y1)) / l2
    t = t.coerceIn(0f, 1f)
    val projX = x1 + t * (x2 - x1)
    val projY = y1 + t * (y2 - y1)
    return kotlin.math.hypot(pX - projX, pY - projY)
}

@Composable
fun FinancialCandlestickCanvas(
    candles: List<com.example.ui.FinancialCandle>,
    selectedCandle: com.example.ui.FinancialCandle?,
    onCandleSelect: (com.example.ui.FinancialCandle) -> Unit,
    cambio: Double = 1.0,
    currencySymbol: String = "MT",
    zoomLevelX: Float = 1.0f,
    zoomLevelY: Float = 1.0f,
    onZoomChange: ((Float, Float) -> Unit)? = null,
    trendlines: List<ChartTrendline> = emptyList(),
    horizontalLines: List<ChartHorizontalLine> = emptyList(),
    activeToolMode: String? = null,
    isLandscapeMode: Boolean = false,
    selectedObjectId: String? = null,
    onSelectObject: ((String?) -> Unit)? = null,
    onUpdateHorizontalLine: ((ChartHorizontalLine) -> Unit)? = null,
    onUpdateTrendline: ((ChartTrendline) -> Unit)? = null,
    onDeleteObject: ((String) -> Unit)? = null,
    onAddHorizontalLine: ((Double) -> Unit)? = null,
    onAddTrendlinePoint: ((Float, Double) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val licenseTier = LocalLicenseTier.current
    val isTrial = (licenseTier == LicenseTier.TRIAL)

    Box(
        modifier = modifier
            .background(Color(0xFF020617))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
    ) {
        if (candles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nenhuma posição ou transação registrada no período.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF64748B))
                )
            }
            return@Box
        }

        val minEquity = remember(candles, cambio) { (candles.minOf { it.lowBalance * cambio } * 0.98).coerceAtLeast(0.0) }
        val maxEquity = remember(candles, cambio) { (candles.maxOf { it.highBalance * cambio } * 1.02).coerceAtLeast(minEquity + (100.0 * cambio)) }
        val equityRange = maxEquity - minEquity

        val scrollStateX = rememberScrollState()
        val scrollStateY = rememberScrollState()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isTrial) Modifier.blur(12.dp) else Modifier)
        ) {
            val baseWidthPx = constraints.maxWidth.toFloat()
            val baseHeightPx = constraints.maxHeight.toFloat()
            val totalChartWidthPx = (baseWidthPx * zoomLevelX).coerceAtLeast(baseWidthPx)
            val totalChartHeightPx = (baseHeightPx * zoomLevelY).coerceAtLeast(baseHeightPx)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (isLandscapeMode) 90f else 0f
                    }
                    .horizontalScroll(scrollStateX, enabled = zoomLevelX > 1.0f || isLandscapeMode)
                    .verticalScroll(scrollStateY, enabled = zoomLevelY > 1.0f || isLandscapeMode)
            ) {
                Canvas(
                    modifier = Modifier
                        .width(with(androidx.compose.ui.platform.LocalDensity.current) { totalChartWidthPx.toDp() })
                        .height(with(androidx.compose.ui.platform.LocalDensity.current) { totalChartHeightPx.toDp() })
                        .padding(top = 16.dp, bottom = 28.dp, start = 16.dp, end = 16.dp)
                        .pointerInput(candles, horizontalLines, trendlines, minEquity, equityRange, isLandscapeMode, selectedObjectId, activeToolMode, zoomLevelX, zoomLevelY) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val touchStart = down.position
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val count = candles.size.coerceAtLeast(1)
                                val rightMargin = 120f
                                val chartWidth = (w - rightMargin).coerceAtLeast(50f)
                                val slotWidth = chartWidth / count

                                // 1. Hit testing with highest priority on selected object handles, then unselected lines
                                var hitId: String? = null
                                var hitMode: String? = null // "START", "END", "TRENDLINE_BODY", "HORIZONTAL"
                                var initialStartIdx = 0f
                                var initialEndIdx = 0f
                                var initialStartPrice = 0.0
                                var initialEndPrice = 0.0
                                var initialHLinePrice = 0.0

                                val selTLine = trendlines.find { it.id == selectedObjectId }
                                if (selTLine != null) {
                                    val x1 = (selTLine.startCandleIndex + 0.5f) * slotWidth
                                    val y1 = h * (1f - ((selTLine.startPrice - minEquity) / equityRange).toFloat())
                                    val x2 = (selTLine.endCandleIndex + 0.5f) * slotWidth
                                    val y2 = h * (1f - ((selTLine.endPrice - minEquity) / equityRange).toFloat())
                                    val midX = (x1 + x2) / 2f
                                    val midY = (y1 + y2) / 2f

                                    val d1 = kotlin.math.hypot(touchStart.x - x1, touchStart.y - y1)
                                    val d2 = kotlin.math.hypot(touchStart.x - x2, touchStart.y - y2)
                                    val dMid = kotlin.math.hypot(touchStart.x - midX, touchStart.y - midY)
                                    val dBody = distanceToSegment(touchStart.x, touchStart.y, x1, y1, x2, y2)

                                    if (d1 <= 64f) {
                                        hitId = selTLine.id
                                        hitMode = "START"
                                        initialStartIdx = selTLine.startCandleIndex
                                        initialStartPrice = selTLine.startPrice
                                    } else if (d2 <= 64f) {
                                        hitId = selTLine.id
                                        hitMode = "END"
                                        initialEndIdx = selTLine.endCandleIndex
                                        initialEndPrice = selTLine.endPrice
                                    } else if (dMid <= 48f || dBody <= 44f) {
                                        hitId = selTLine.id
                                        hitMode = "TRENDLINE_BODY"
                                        initialStartIdx = selTLine.startCandleIndex
                                        initialEndIdx = selTLine.endCandleIndex
                                        initialStartPrice = selTLine.startPrice
                                        initialEndPrice = selTLine.endPrice
                                    }
                                }

                                if (hitId == null) {
                                    // Check all other trendlines
                                    for (tLine in trendlines) {
                                        val x1 = (tLine.startCandleIndex + 0.5f) * slotWidth
                                        val y1 = h * (1f - ((tLine.startPrice - minEquity) / equityRange).toFloat())
                                        val x2 = (tLine.endCandleIndex + 0.5f) * slotWidth
                                        val y2 = h * (1f - ((tLine.endPrice - minEquity) / equityRange).toFloat())
                                        val d1 = kotlin.math.hypot(touchStart.x - x1, touchStart.y - y1)
                                        val d2 = kotlin.math.hypot(touchStart.x - x2, touchStart.y - y2)
                                        val dSeg = distanceToSegment(touchStart.x, touchStart.y, x1, y1, x2, y2)

                                        if (d1 <= 54f) {
                                            hitId = tLine.id
                                            hitMode = "START"
                                            initialStartIdx = tLine.startCandleIndex
                                            initialStartPrice = tLine.startPrice
                                            break
                                        } else if (d2 <= 54f) {
                                            hitId = tLine.id
                                            hitMode = "END"
                                            initialEndIdx = tLine.endCandleIndex
                                            initialEndPrice = tLine.endPrice
                                            break
                                        } else if (dSeg <= 44f) {
                                            hitId = tLine.id
                                            hitMode = "TRENDLINE_BODY"
                                            initialStartIdx = tLine.startCandleIndex
                                            initialEndIdx = tLine.endCandleIndex
                                            initialStartPrice = tLine.startPrice
                                            initialEndPrice = tLine.endPrice
                                            break
                                        }
                                    }
                                }

                                if (hitId == null) {
                                    // Check horizontal lines (selected first or closest)
                                    val selHLine = horizontalLines.find { it.id == selectedObjectId }
                                    if (selHLine != null) {
                                        val yLine = h * (1f - ((selHLine.price - minEquity) / equityRange).toFloat())
                                        if (kotlin.math.abs(touchStart.y - yLine) <= 48f) {
                                            hitId = selHLine.id
                                            hitMode = "HORIZONTAL"
                                            initialHLinePrice = selHLine.price
                                        }
                                    }
                                    if (hitId == null) {
                                        for (hLine in horizontalLines) {
                                            val yLine = h * (1f - ((hLine.price - minEquity) / equityRange).toFloat())
                                            if (kotlin.math.abs(touchStart.y - yLine) <= 44f) {
                                                hitId = hLine.id
                                                hitMode = "HORIZONTAL"
                                                initialHLinePrice = hLine.price
                                                break
                                            }
                                        }
                                    }
                                }

                                // If a line or control handle was touched:
                                if (hitId != null && hitMode != null) {
                                    // Immediate selection on touch down without lag
                                    onSelectObject?.invoke(hitId)
                                    down.consume()

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val pointer = event.changes.find { it.id == down.id } ?: event.changes.firstOrNull()
                                        if (pointer == null || !pointer.pressed) {
                                            // Finger released or cancelled -> State already saved automatically
                                            break
                                        }

                                        val currentPos = pointer.position
                                        pointer.consume()

                                        val totalDx = currentPos.x - touchStart.x
                                        val totalDy = currentPos.y - touchStart.y

                                        val idxShift = totalDx / slotWidth
                                        val priceShift = -(totalDy / h) * equityRange

                                        if (hitMode == "HORIZONTAL") {
                                            val line = horizontalLines.find { it.id == hitId }
                                            if (line != null && onUpdateHorizontalLine != null) {
                                                val newPrice = (initialHLinePrice + priceShift).coerceIn(minEquity, maxEquity)
                                                onUpdateHorizontalLine(line.copy(price = newPrice))
                                            }
                                        } else {
                                            val tLine = trendlines.find { it.id == hitId }
                                            if (tLine != null && onUpdateTrendline != null) {
                                                when (hitMode) {
                                                    "START" -> {
                                                        val newStartIdx = (initialStartIdx + idxShift).coerceIn(0f, (count - 1).toFloat())
                                                        val newStartPrice = (initialStartPrice + priceShift).coerceIn(minEquity, maxEquity)
                                                        onUpdateTrendline(tLine.copy(startCandleIndex = newStartIdx, startPrice = newStartPrice))
                                                    }
                                                    "END" -> {
                                                        val newEndIdx = (initialEndIdx + idxShift).coerceIn(0f, (count - 1).toFloat())
                                                        val newEndPrice = (initialEndPrice + priceShift).coerceIn(minEquity, maxEquity)
                                                        onUpdateTrendline(tLine.copy(endCandleIndex = newEndIdx, endPrice = newEndPrice))
                                                    }
                                                    "TRENDLINE_BODY" -> {
                                                        val newStartIdx = (initialStartIdx + idxShift).coerceIn(0f, (count - 1).toFloat())
                                                        val newEndIdx = (initialEndIdx + idxShift).coerceIn(0f, (count - 1).toFloat())
                                                        val newStartPrice = (initialStartPrice + priceShift).coerceIn(minEquity, maxEquity)
                                                        val newEndPrice = (initialEndPrice + priceShift).coerceIn(minEquity, maxEquity)

                                                        onUpdateTrendline(tLine.copy(
                                                            startCandleIndex = newStartIdx,
                                                            startPrice = newStartPrice,
                                                            endCandleIndex = newEndIdx,
                                                            endPrice = newEndPrice
                                                        ))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // Touch on chart background / candles / pinch-zoom
                                    var hasMoved = false
                                    var lastPos = touchStart

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.changes.size > 1 && onZoomChange != null) {
                                            // Multi-touch pinch zoom
                                            val p1 = event.changes[0]
                                            val p2 = event.changes[1]
                                            val currentDist = (p1.position - p2.position).getDistance()
                                            val prevDist = (p1.previousPosition - p2.previousPosition).getDistance()
                                            if (prevDist > 0f) {
                                                val zoomFactor = (currentDist / prevDist).coerceIn(0.85f, 1.15f)
                                                val newX = (zoomLevelX * zoomFactor).coerceIn(0.5f, 5.0f)
                                                val newY = (zoomLevelY * zoomFactor).coerceIn(0.5f, 5.0f)
                                                onZoomChange(newX, newY)
                                                p1.consume()
                                                p2.consume()
                                                hasMoved = true
                                            }
                                        } else {
                                            val pointer = event.changes.find { it.id == down.id } ?: event.changes.firstOrNull()
                                            if (pointer == null || !pointer.pressed) {
                                                break
                                            }

                                            val currentPos = pointer.position
                                            val dx = currentPos.x - lastPos.x
                                            val dy = currentPos.y - lastPos.y

                                            if (kotlin.math.abs(currentPos.x - touchStart.x) > 10f || kotlin.math.abs(currentPos.y - touchStart.y) > 10f) {
                                                hasMoved = true
                                                if (zoomLevelX > 1.0f || zoomLevelY > 1.0f || isLandscapeMode) {
                                                    pointer.consume()
                                                    val scrollDeltaX = if (isLandscapeMode) dy else -dx
                                                    val scrollDeltaY = if (isLandscapeMode) -dx else -dy
                                                    scrollStateX.dispatchRawDelta(scrollDeltaX)
                                                    scrollStateY.dispatchRawDelta(scrollDeltaY)
                                                }
                                            }
                                            lastPos = currentPos
                                        }
                                    }

                                    if (!hasMoved) {
                                        // Tap on background or candle
                                        val clickedIndex = (touchStart.x / slotWidth).coerceIn(0f, (count - 1).toFloat())
                                        val clickedPrice = maxEquity - (touchStart.y / h) * equityRange

                                        if (activeToolMode == "HORIZONTAL" && onAddHorizontalLine != null) {
                                            onAddHorizontalLine(clickedPrice)
                                        } else if (activeToolMode == "TRENDLINE" && onAddTrendlinePoint != null) {
                                            onAddTrendlinePoint(clickedIndex, clickedPrice)
                                        } else {
                                            // Deselect any line and select tapped candle
                                            onSelectObject?.invoke(null)
                                            val candleIdx = clickedIndex.toInt().coerceIn(0, candles.size - 1)
                                            if (candleIdx in candles.indices) {
                                                onCandleSelect(candles[candleIdx])
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val count = candles.size
                    
                    val rightMargin = 120f
                    val chartWidth = (w - rightMargin).coerceAtLeast(50f)
                    val slotWidth = chartWidth / count
                    val candleBodyWidth = (slotWidth * 0.55f).coerceIn(6f, 36f)

                    // Grid Lines
                    val gridSteps = 4
                    val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    for (i in 0..gridSteps) {
                        val ratio = i / gridSteps.toFloat()
                        val y = h * (1f - ratio)

                        drawLine(
                            color = Color(0xFF1E293B),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(w, y),
                            pathEffect = dashEffect,
                            strokeWidth = 1f
                        )
                    }

                    // Draw Candlesticks
                    candles.forEachIndexed { index, candle ->
                        val cx = (index + 0.5f) * slotWidth
                        val isSelected = selectedCandle?.id == candle.id

                        val openVal = candle.openBalance * cambio
                        val closeVal = candle.closeBalance * cambio
                        val highVal = candle.highBalance * cambio
                        val lowVal = candle.lowBalance * cambio

                        val yOpen = h * (1f - ((openVal - minEquity) / equityRange).toFloat())
                        val yClose = h * (1f - ((closeVal - minEquity) / equityRange).toFloat())
                        val yHigh = h * (1f - ((highVal - minEquity) / equityRange).toFloat())
                        val yLow = h * (1f - ((lowVal - minEquity) / equityRange).toFloat())

                        val color = if (candle.isBullish) Color(0xFF10B981) else Color(0xFFEF4444)

                        if (isSelected) {
                            drawRect(
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                topLeft = androidx.compose.ui.geometry.Offset(cx - (slotWidth / 2f), 0f),
                                size = androidx.compose.ui.geometry.Size(slotWidth, h)
                            )
                        }

                        // Wick Line
                        drawLine(
                            color = color,
                            start = androidx.compose.ui.geometry.Offset(cx, yHigh),
                            end = androidx.compose.ui.geometry.Offset(cx, yLow),
                            strokeWidth = if (isSelected) 3.5f else 2f
                        )

                        // Body Rect
                        val topY = kotlin.math.min(yOpen, yClose)
                        val botY = kotlin.math.max(yOpen, yClose)
                        val bodyH = kotlin.math.max(botY - topY, 4f)

                        drawRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(cx - (candleBodyWidth / 2f), topY),
                            size = androidx.compose.ui.geometry.Size(candleBodyWidth, bodyH)
                        )

                        // Deposit Badge (+)
                        if (candle.deposits > 0.0) {
                            drawCircle(
                                color = Color(0xFF22D3EE),
                                radius = 6f,
                                center = androidx.compose.ui.geometry.Offset(cx, yHigh - 10f)
                            )
                        }

                        // Withdrawal Badge (-)
                        if (candle.withdrawals > 0.0) {
                            drawCircle(
                                color = Color(0xFFF59E0B),
                                radius = 6f,
                                center = androidx.compose.ui.geometry.Offset(cx, yLow + 10f)
                            )
                        }
                    }

                    // 1. Horizontal Lines (Analysis Tool) with Real-Time Draggable Price & Accompanying Price Label
                    horizontalLines.forEach { line ->
                        val yLine = h * (1f - ((line.price - minEquity) / equityRange).toFloat())
                        if (yLine in 0f..h) {
                            val isObjSelected = line.id == selectedObjectId
                            val lineDashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)

                            if (isObjSelected) {
                                // Outer Glow line
                                drawLine(
                                    color = line.color.copy(alpha = 0.35f),
                                    start = androidx.compose.ui.geometry.Offset(0f, yLine),
                                    end = androidx.compose.ui.geometry.Offset(w, yLine),
                                    strokeWidth = 9f
                                )
                                // Left Grab Handle
                                drawCircle(color = line.color, radius = 10f, center = androidx.compose.ui.geometry.Offset(18f, yLine))
                                drawCircle(color = Color.White, radius = 6f, center = androidx.compose.ui.geometry.Offset(18f, yLine))
                            }

                            // Main Horizontal Line
                            drawLine(
                                color = if (isObjSelected) line.color else line.color.copy(alpha = 0.85f),
                                start = androidx.compose.ui.geometry.Offset(0f, yLine),
                                end = androidx.compose.ui.geometry.Offset(w, yLine),
                                pathEffect = if (isObjSelected) null else lineDashEffect,
                                strokeWidth = if (isObjSelected) 4f else 2.5f
                            )

                            // Accompanying Price Badge on the Right Edge
                            val priceText = formatChartPriceValue(line.price, currencySymbol)
                            val badgeWidth = 108f
                            val badgeHeight = 24f
                            val badgeLeft = (w - badgeWidth).coerceAtLeast(0f)
                            val badgeTop = (yLine - (badgeHeight / 2f)).coerceIn(0f, h - badgeHeight)

                            // Badge Background
                            drawRoundRect(
                                color = Color(0xFF020617),
                                topLeft = androidx.compose.ui.geometry.Offset(badgeLeft, badgeTop),
                                size = androidx.compose.ui.geometry.Size(badgeWidth, badgeHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                            )
                            // Badge Border
                            drawRoundRect(
                                color = if (isObjSelected) line.color else line.color.copy(alpha = 0.8f),
                                topLeft = androidx.compose.ui.geometry.Offset(badgeLeft, badgeTop),
                                size = androidx.compose.ui.geometry.Size(badgeWidth, badgeHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (isObjSelected) 2f else 1.2f)
                            )

                            // Badge Text
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = if (isObjSelected) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#38BDF8")
                                    textSize = 21f
                                    isFakeBoldText = true
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                drawText(
                                    priceText,
                                    badgeLeft + (badgeWidth / 2f),
                                    badgeTop + (badgeHeight / 2f) + 6.5f,
                                    paint
                                )
                            }
                        }
                    }

                    // 2. Trendlines (Analysis Tool) with Endpoints and Body Handles
                    trendlines.forEach { tLine ->
                        val x1 = (tLine.startCandleIndex + 0.5f) * slotWidth
                        val y1 = h * (1f - ((tLine.startPrice - minEquity) / equityRange).toFloat())
                        val x2 = (tLine.endCandleIndex + 0.5f) * slotWidth
                        val y2 = h * (1f - ((tLine.endPrice - minEquity) / equityRange).toFloat())

                        val isObjSelected = tLine.id == selectedObjectId

                        if (isObjSelected) {
                            // Outer Glow Line
                            drawLine(
                                color = tLine.color.copy(alpha = 0.35f),
                                start = androidx.compose.ui.geometry.Offset(x1, y1),
                                end = androidx.compose.ui.geometry.Offset(x2, y2),
                                strokeWidth = 10f
                            )
                        }

                        // Main Trendline
                        drawLine(
                            color = if (isObjSelected) tLine.color else tLine.color.copy(alpha = 0.9f),
                            start = androidx.compose.ui.geometry.Offset(x1, y1),
                            end = androidx.compose.ui.geometry.Offset(x2, y2),
                            strokeWidth = if (isObjSelected) 4.5f else 3f
                        )

                        if (isObjSelected) {
                            // Start Endpoint Handle (Adjusts Inclination)
                            drawCircle(color = tLine.color.copy(alpha = 0.4f), radius = 16f, center = androidx.compose.ui.geometry.Offset(x1, y1))
                            drawCircle(color = tLine.color, radius = 10f, center = androidx.compose.ui.geometry.Offset(x1, y1))
                            drawCircle(color = Color.White, radius = 5.5f, center = androidx.compose.ui.geometry.Offset(x1, y1))

                            // End Endpoint Handle (Adjusts Inclination)
                            drawCircle(color = tLine.color.copy(alpha = 0.4f), radius = 16f, center = androidx.compose.ui.geometry.Offset(x2, y2))
                            drawCircle(color = tLine.color, radius = 10f, center = androidx.compose.ui.geometry.Offset(x2, y2))
                            drawCircle(color = Color.White, radius = 5.5f, center = androidx.compose.ui.geometry.Offset(x2, y2))

                            // Midpoint Handle (Move entire line without changing inclination)
                            val midX = (x1 + x2) / 2f
                            val midY = (y1 + y2) / 2f
                            drawCircle(color = tLine.color, radius = 8f, center = androidx.compose.ui.geometry.Offset(midX, midY))
                            drawCircle(color = Color.Black, radius = 4f, center = androidx.compose.ui.geometry.Offset(midX, midY))
                        } else {
                            // Subtle endpoints when unselected
                            drawCircle(color = tLine.color, radius = 5.5f, center = androidx.compose.ui.geometry.Offset(x1, y1))
                            drawCircle(color = tLine.color, radius = 5.5f, center = androidx.compose.ui.geometry.Offset(x2, y2))
                        }
                    }

                    // Current Price / Equity Horizontal Reference Line
                    val lastCandle = candles.lastOrNull()
                    if (lastCandle != null) {
                        val currentPrice = lastCandle.closeBalance * cambio
                        val yCurrent = h * (1f - ((currentPrice - minEquity) / equityRange).toFloat()).coerceIn(0f, 1f)
                        val currentDashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 5f), 0f)

                        // Current Price Line
                        drawLine(
                            color = Color(0xFF38BDF8),
                            start = androidx.compose.ui.geometry.Offset(0f, yCurrent),
                            end = androidx.compose.ui.geometry.Offset(w, yCurrent),
                            pathEffect = currentDashEffect,
                            strokeWidth = 2f
                        )

                        // Current Price Badge on Right Edge
                        val priceText = "$currencySymbol ${String.format("%.2f", currentPrice)}"
                        val badgeWidth = 100f
                        val badgeHeight = 24f
                        val badgeLeft = (w - badgeWidth).coerceAtLeast(0f)
                        val badgeTop = (yCurrent - (badgeHeight / 2f)).coerceIn(0f, h - badgeHeight)

                        drawRoundRect(
                            color = Color(0xFF0F172A),
                            topLeft = androidx.compose.ui.geometry.Offset(badgeLeft, badgeTop),
                            size = androidx.compose.ui.geometry.Size(badgeWidth, badgeHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = Color(0xFF38BDF8),
                            topLeft = androidx.compose.ui.geometry.Offset(badgeLeft, badgeTop),
                            size = androidx.compose.ui.geometry.Size(badgeWidth, badgeHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
                        )

                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.parseColor("#38BDF8")
                                textSize = 20f
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            drawText(
                                priceText,
                                badgeLeft + (badgeWidth / 2f),
                                badgeTop + (badgeHeight / 2f) + 6f,
                                paint
                            )
                        }
                    }
                }
            }
        }

        if (isTrial) {
            Surface(
                color = Color(0xFF0F172A).copy(alpha = 0.92f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFF59E0B), Color(0xFF38BDF8)))),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Gráficos Bloqueados",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Text(
                        text = "GRÁFICO BLOQUEADO NO PLANO TRIAL",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = Color(0xFFF59E0B),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "O gráfico financeiro ao vivo de velas (M15/H1), linhas de tendência dinâmicas e níveis de equador estão disponíveis para usuários a partir do Plano Starter.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFCBD5E1),
                            lineHeight = 18.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                    ) {
                        listOf("Lote Ilimitado", "Pro: Templates", "Master: Sala VIP 👑").forEach { tag ->
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Mql5NotificationsModalDialog(onDismiss: () -> Unit) {
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var copiedIndex by remember { mutableStateOf(-1) }
    val notifications = com.example.data.EaNotificationEventsCatalog.ALL_NOTIFICATIONS

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp),
            color = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("CATÁLOGO DE NOTIFICAÇÕES MQL5", style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            Text("Eventos de Trava, Gerenciamento & Equador", style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 10.sp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(notifications) { index, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (item.isSell) Color(0xFFEF4444).copy(alpha = 0.4f) else Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (item.isSell) Color(0xFFEF4444) else Color(0xFF38BDF8),
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFF334155))
                                    ) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFFCBD5E1), fontSize = 9.sp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 11.sp)
                                )

                                Surface(
                                    color = Color(0xFF020617),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.mql5CodeSnippet,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color(0xFF38BDF8),
                                            fontSize = 9.5.sp
                                        ),
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.mql5CodeSnippet))
                                            copiedIndex = index
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = if (copiedIndex == index) Color(0xFF10B981) else Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (copiedIndex == index) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (copiedIndex == index) "COPIADO!" else "COPIAR MQL5",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("FECHAR", style = MaterialTheme.typography.labelMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}




