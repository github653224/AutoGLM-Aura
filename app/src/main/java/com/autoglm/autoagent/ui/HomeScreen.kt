package com.autoglm.autoagent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.autoagent.data.*
import com.autoglm.autoagent.data.api.ChatMessage
import com.autoglm.autoagent.ui.components.AnimatedGlowingCircle
import com.autoglm.autoagent.ui.theme.*
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogEntry(
    val timestamp: Long,
    val type: LogType,
    val content: String
)

enum class LogType {
    USER_COMMAND, AI_ACTION
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    init {
        // Preload voice model on ViewModel creation
        agentRepository.preloadVoiceModel()
        
        // Auto-run Diagnostics
        checkAccessibilityStatus()
    }
    
    fun checkAccessibilityStatus() {
        // 1. Check System Settings
        val serviceId = "${context.packageName}/com.autoglm.autoagent.service.AutoAgentService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val isSystemEnabled = enabledServices.contains(serviceId)
        
        // 2. Check Actual Service Connection (Instance)
        val isServiceConnected = com.autoglm.autoagent.service.AutoAgentService.instance != null
        
        agentRepository.addMessage("system", "🔍 系统自检报告 (System Report)")
        
        if (isSystemEnabled) {
            agentRepository.addMessage("system", "✅ [系统开关] 已开启 (Settings: Enabled)")
        } else {
             agentRepository.addMessage("system", "❌ [系统开关] 未开启 (请去设置打开)")
        }
        
        if (isServiceConnected) {
             agentRepository.addMessage("system", "✅ [服务连接] 正常 (Service Connected)")
        } else {
             if (isSystemEnabled) {
                 agentRepository.addMessage("system", "⚠️ [服务连接] 异常！(系统已开但服务未连)")
                 agentRepository.addMessage("system", "💡 建议: 请尝试关闭无障碍再重新打开，或重启设备。")
             } else {
                 agentRepository.addMessage("system", "❌ [服务连接] 断开 (等待开启)")
             }
        }
    }
    
    // 监听AgentRepository的消息
    val messages = agentRepository.chatMessages
    val agentState = agentRepository.agentState
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 从 AgentRepository.agentState 派生状态文本
    val agentStatus: StateFlow<String> = agentRepository.agentState
        .map { state ->
            when (state) {
                is AgentState.Idle -> "等待指令..."
                is AgentState.Planning -> "正在规划..."
                is AgentState.Running -> "正在执行..."
                is AgentState.Paused -> "暂停等待中..."
                is AgentState.Listening -> "正在聆听..."
                is AgentState.Error -> "出现错误: ${state.msg}"
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, "等待指令...")
    
    private val _isLogExpanded = MutableStateFlow(false)
    val isLogExpanded = _isLogExpanded.asStateFlow()

    // 从 AgentRepository.chatMessages 派生日志列表
    val logEntries: StateFlow<List<LogEntry>> = agentRepository.chatMessages
        .map { messages ->
            messages.filter { it.role == "user" || it.role == "assistant" || it.role == "system" }
                .mapNotNull { msg ->
                    val content = when (val c = msg.content) {
                        is String -> c
                        is List<*> -> "分析截屏中..."
                        else -> c.toString()
                    }
                    
                    val type = when {
                        msg.role == "user" && content.startsWith("Task:") -> LogType.USER_COMMAND
                        msg.role == "assistant" -> LogType.AI_ACTION
                        msg.role == "system" && (content.contains("Step") || content.contains("Error")) -> LogType.AI_ACTION
                        else -> null
                    }
                    
                    if (type != null) {
                        LogEntry(
                            timestamp = System.currentTimeMillis(),
                            type = type,
                            content = content.removePrefix("Task: ").removePrefix("Think: ").removePrefix("Action: ")
                        )
                    } else null
                }
                .takeLast(50)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
    // 最后一条用户指令
    val lastUserCommand: StateFlow<String> = logEntries.map { logs ->
        logs.lastOrNull { it.type == LogType.USER_COMMAND }?.content 
            ?: "等待输入指令..."
    }.stateIn(viewModelScope, SharingStarted.Lazily, "等待输入指令...")

    private val _showTextInput = MutableStateFlow(false)
    val showTextInput = _showTextInput.asStateFlow()

    fun toggleTextInput() {
        _showTextInput.value = !_showTextInput.value
    }
    
    fun toggleLogPanel() {
        _isLogExpanded.value = !_isLogExpanded.value
    }
    
    fun sendMessage(text: String) {
        // 发送指令前检测无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            viewModelScope.launch {
                agentRepository.setError("请先开启无障碍服务")
                kotlinx.coroutines.delay(3000)
                agentRepository.resetToIdle()
            }
            return
        }
        
        viewModelScope.launch {
            _isLoading.value = true
            try {
                agentRepository.executeTask(text)
            } catch (ignored: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${context.packageName}/com.autoglm.autoagent.service.AutoAgentService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(service)
    }

    fun startVoiceRecording() {
        agentRepository.setListening(true)
    }

    fun stopExecution() {
        if (agentState.value is AgentState.Listening) {
             agentRepository.cancelListening()
        }
        agentRepository.stopAgent()
        _isLoading.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    onOpenAdvancedMode: () -> Unit = {}  // New parameter
) {
    val agentStatus by viewModel.agentStatus.collectAsState()
    val lastUserCommand by viewModel.lastUserCommand.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val isLogExpanded by viewModel.isLogExpanded.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showTextInput by viewModel.showTextInput.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val isRecording = agentState is AgentState.Listening

    // Root Container with Particle Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkBackgroundSecondary)
                )
            )
    ) {
        // 1. Ambient Motion Layer
        com.autoglm.autoagent.ui.components.ParticleBackground(
            modifier = Modifier.fillMaxSize().alpha(0.6f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(), // Respect bottom nav bar
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 2. Minimalist Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "智灵助手", // Brand Name
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = TextPrimary
                    )
                    Text(
                        text = "Hands-free AI Agent, built on Open-AutoGLM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Advanced Mode Icon
                    IconButton(
                        onClick = onOpenAdvancedMode,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PrimaryPurple.copy(alpha=0.15f))
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Rocket,
                            contentDescription = "高级模式",
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Settings Icon (Glass)
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GlassLight.copy(alpha=0.1f))
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Setting",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. THE CORE: Living Orb
            // No more "Power Button". This IS the agent.
            var isOrbPressed by remember { mutableStateOf(false) }
            
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(300.dp)
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = if (agentState is AgentState.Running || agentState is AgentState.Listening) {
                            "停止执行"
                        } else {
                            "开始语音识别"
                        }
                        onClick {
                            if (agentState is AgentState.Running || agentState is AgentState.Listening) {
                                viewModel.stopExecution()
                            } else {
                                viewModel.startVoiceRecording()
                            }
                            true
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isOrbPressed = true
                                tryAwaitRelease()
                                isOrbPressed = false
                            },
                            onTap = {
                                if (agentState is AgentState.Running || agentState is AgentState.Listening) {
                                    viewModel.stopExecution()
                                } else {
                                    viewModel.startVoiceRecording()
                                }
                            }
                        )
                    }
            ) {
                com.autoglm.autoagent.ui.components.LivingOrb(
                    modifier = Modifier.fillMaxSize(),
                    isActive = isLoading || agentState !is AgentState.Idle,
                    isListening = isRecording,
                    isPressed = isOrbPressed
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))

            // 4. Dynamic Status Text
            AnimatedContent(targetState = agentStatus, label = "status") { status ->
            // 4. Status Text with Animated Ellipsis
            Box(
                 modifier = Modifier
                     .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                     .heightIn(min = 40.dp, max = 120.dp),
                 contentAlignment = Alignment.Center
            ) {
                 val baseText = if (status.endsWith("...")) status.dropLast(3) else status
                 val shouldAnimate = status.endsWith("...")
                 
                 if (shouldAnimate) {
                     val infiniteTransition = rememberInfiniteTransition(label = "dots")
                     val dotCount by infiniteTransition.animateValue(
                         initialValue = 0,
                         targetValue = 4, // 0, 1, 2, 3
                         typeConverter = Int.VectorConverter,
                         animationSpec = infiniteRepeatable(
                             animation = tween(1500, easing = LinearEasing),
                             repeatMode = RepeatMode.Restart
                         ),
                         label = "dot_count"
                     )
                     
                     Text(
                        text = "$baseText${".".repeat(dotCount)}",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Light
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                 } else {
                     Text(
                        text = status,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Light
                        ),
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                 }
            }
            }
            
            Text(
                text = if (isRecording) "点击取消" else "点击圆球开始对话",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary.copy(alpha=0.6f),
                modifier = Modifier.padding(top = 0.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // 5. Floating Action Bar (Bottom)
            // Replaces the heavy "Card" layout with a sleek floating row
            Row(
                modifier = Modifier
                    .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Log Toggle (Subtle Frosted Glass)
                com.autoglm.autoagent.ui.components.GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp) // Standard sleek height
                        .clickable { viewModel.toggleLogPanel() },
                    shape = RoundedCornerShape(30.dp),
                    backgroundColor = Color.White.copy(alpha = 0.05f), // Very subtle frost
                    borderColor = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.List, 
                            contentDescription = null, 
                            tint = PrimaryCyan.copy(alpha = 0.8f), 
                            modifier = Modifier.size(20.dp) // Standard Size
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "日志", 
                            color = TextPrimary.copy(alpha = 0.9f), 
                            fontSize = 14.sp, // Standard Size
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Keyboard Toggle (Subtle Frosted Glass)
                com.autoglm.autoagent.ui.components.GlassCard(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp) // Standard sleek height
                        .clickable { viewModel.toggleTextInput() },
                    shape = RoundedCornerShape(30.dp),
                    backgroundColor = Color.White.copy(alpha = 0.05f), // Very subtle frost
                    borderColor = Color.White.copy(alpha = 0.15f)
                ) {
                     Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit, 
                            contentDescription = null, 
                            tint = PrimaryPurple.copy(alpha = 0.8f), 
                            modifier = Modifier.size(20.dp) // Standard Size
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "输入", 
                            color = TextPrimary.copy(alpha = 0.9f), 
                            fontSize = 14.sp, // Standard Size
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
        
        // Overlays (Log & Input) - Kept same logic, just ensure they sit on top
        if (isLogExpanded) {
             LogSheet(logs = logEntries, onDismiss = { viewModel.toggleLogPanel() })
        }

        if (showTextInput) {
             TextInputSheet(
                 onSend = { 
                     viewModel.sendMessage(it)
                     viewModel.toggleTextInput() 
                 }, 
                 onDismiss = { viewModel.toggleTextInput() }
            )
        }
    }
}
// Dependent sub-components (LogSheet, TextInputSheet, LogItem) remain unchanged...
// Deleted PowerButton and redundant VoiceControlBar


@Composable
fun LogSheet(logs: List<LogEntry>, onDismiss: () -> Unit) {
     Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
         com.autoglm.autoagent.ui.components.GlassCard(
            modifier = Modifier.fillMaxWidth().height(400.dp).clickable(enabled=false){},
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            backgroundColor = DarkSurface
        ) {
             Column(modifier = Modifier.padding(24.dp)) {
                 Text("指令历史", style = MaterialTheme.typography.headlineMedium)
                 Spacer(modifier = Modifier.height(16.dp))
                  androidx.compose.foundation.lazy.LazyColumn {
                    items(logs.size) { index ->
                        LogItem(logs[index])
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
             }
        }
    }
}

@Composable
fun TextInputSheet(onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
     Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.7f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        com.autoglm.autoagent.ui.components.GlassCard(
            modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(), // Handle keyboard
            backgroundColor = DarkSurface
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                 TextField(
                     value = text, 
                     onValueChange = { text = it }, 
                     modifier = Modifier.weight(1f),
                     colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                         unfocusedTextColor = TextPrimary,
                     ),
                     placeholder = { Text("输入指令...", color = TextSecondary) }
                 )
                 IconButton(onClick = { if (text.isNotBlank()) onSend(text) }) {
                     Icon(Icons.Default.Send, null, tint = PrimaryBlue)
                 }
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    Row(verticalAlignment = Alignment.Top) {
        Text(if (log.type == LogType.USER_COMMAND) "👤" else "🤖", modifier = Modifier.padding(end=12.dp))
        Column {
             Text(
                text = log.content,
                color = if (log.type == LogType.USER_COMMAND) PrimaryBlueLight else TextSecondary,
                style = MaterialTheme.typography.bodyMedium
             )
        }
    }
}

