package com.autoglm.autoagent.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
        agentRepository.stopAgent()
        _isLoading.value = false
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit
) {
    val agentStatus by viewModel.agentStatus.collectAsState()
    val lastUserCommand by viewModel.lastUserCommand.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()
    val isLogExpanded by viewModel.isLogExpanded.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val showTextInput by viewModel.showTextInput.collectAsState()
    val agentState by viewModel.agentState.collectAsState()
    val isRecording = agentState is AgentState.Listening

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkBackgroundSecondary)
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部导航栏
            TopAppBar(
                title = { 
                    Text(
                        "智灵助手",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.8f)
                )
            )

            // 中央内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // 发光圆环动画
                AnimatedGlowingCircle(
                    modifier = Modifier.size(220.dp),
                    isActive = isLoading
                )

                Spacer(modifier = Modifier.height(32.dp))

                // 状态文本
                Text(
                    text = agentStatus,
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(40.dp))

                // 可展开日志面板
                ExpandableLogCard(
                    lastCommand = lastUserCommand,
                    logs = logEntries,
                    isExpanded = isLogExpanded,
                    onToggle = { viewModel.toggleLogPanel() },
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }

            // 底部控制区域
            AnimatedContent(
                targetState = showTextInput,
                label = "input_mode",
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn()) togetherWith
                            (slideOutVertically { height -> height } + fadeOut())
                }
            ) { isTextMode ->
                if (isTextMode) {
                    // 文本输入模式
                    TextInputBar(
                        onSend = { text ->
                            viewModel.sendMessage(text)
                            viewModel.toggleTextInput()
                        },
                        onBack = { viewModel.toggleTextInput() },
                        enabled = !isLoading
                    )
                } else {
                    // 语音控制模式
                    VoiceControlBar(
                        onKeyboardClick = { viewModel.toggleTextInput() },
                        onMicClick = { 
                            if (isRecording) viewModel.stopExecution() // Or specific stop listening
                            else viewModel.startVoiceRecording() 
                        },
                        onStopClick = { viewModel.stopExecution() },
                        isRecording = isRecording
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceControlBar(
    onKeyboardClick: () -> Unit,
    onMicClick: () -> Unit,
    onStopClick: () -> Unit,
    isRecording: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 键盘按钮
        IconButton(
            onClick = onKeyboardClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(CardBackground)
        ) {
            Text(
                text = "⌨️",
                fontSize = 28.sp
            )
        }

        // 麦克风按钮 (带发光效果)
        Box(
            contentAlignment = Alignment.Center
        ) {
            // 发光背景层
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    GlowBlue.copy(alpha = 0.6f),
                                    GlowBlue.copy(alpha = 0.2f),
                                    androidx.compose.ui.graphics.Color.Transparent
                                )
                            )
                        )
                )
            }

            // 按钮本体
            IconButton(
                onClick = onMicClick,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(PrimaryBlue, PrimaryBlueDark)
                        )
                    ),
                enabled = !isRecording
            ) {
                Text(
                    text = "🎤",
                    fontSize = 32.sp
                )
            }
        }

        // 停止按钮
        IconButton(
            onClick = onStopClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(StopRed)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⏹",
                    fontSize = 24.sp
                )
                Text(
                    text = "STOP",
                    color = TextPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun TextInputBar(
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    enabled: Boolean
) {
    var inputText by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Text(
                text = "←",
                fontSize = 24.sp,
                color = TextPrimary
            )
        }

        // 输入框
        TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("输入指令...", color = TextHint) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBackgroundDark,
                unfocusedContainerColor = CardBackgroundDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PrimaryBlue,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = enabled
        )

        // 发送按钮
        IconButton(
            onClick = {
                if (inputText.isNotBlank()) {
                    onSend(inputText)
                    inputText = ""
                }
            },
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (inputText.isNotBlank()) PrimaryBlue 
                    else CardBackgroundDark
                ),
            enabled = enabled && inputText.isNotBlank()
        ) {
            Text(
                text = "➤",
                fontSize = 20.sp,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun ExpandableLogCard(
    lastCommand: String,
    logs: List<LogEntry>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            if (isExpanded && logs.isNotEmpty()) {
                // 展开状态 - 显示完整日志
                Text(
                    text = "指令历史",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // 添加滚动支持
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(logs.size) { index ->
                        LogItem(logs[index])
                        if (index < logs.size - 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = "点击收起",
                        color = PrimaryBlue,
                        fontSize = 12.sp
                    )
                }
            } else {
                // 收起状态 - 点击卡片展开
                TextButton(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastCommand,
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 图标
        Text(
            text = if (log.type == LogType.USER_COMMAND) "👤" else "🤖",
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        
        // 内容
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (log.type == LogType.USER_COMMAND) "用户指令" else "AI操作",
                color = if (log.type == LogType.USER_COMMAND) PrimaryBlue else AccentPurple,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = log.content,
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

