package com.autoglm.autoagent.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.autoglm.autoagent.shell.*
import com.autoglm.autoagent.shizuku.ActivationStatus

/**
 * Shell Service 激活界面 (回退版 - 移除 Kadb)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellServiceActivationScreen(
    viewModel: ShellServiceActivationViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高级模式 - Shell Service") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 状态卡片
            ServiceStatusCard(state)
            
            // 功能介绍
            FeaturesCard()
            
            if (state.isServiceRunning) {
                ServiceActiveCard(
                    onTestService = { viewModel.testService() },
                    onStopService = { viewModel.stopService() }
                )
            } else {
                GuideCard(
                    shizukuStatus = state.shizukuStatus,
                    activationCommand = state.activationCommand,
                    isLoading = state.isLoading,
                    errorMessage = state.errorMessage,
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                    },
                    onLaunchShizuku = {
                        // 逻辑：引导用户打开 Shizuku
                        try {
                            val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                            if (intent != null) context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    onActivateViaShizuku = {
                        viewModel.launchService()
                    },
                    onCopyCommand = { clipboardManager.setText(AnnotatedString(it)) }
                )
            }
        }
    }
}

@Composable
fun ServiceStatusCard(state: ShellActivationUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isServiceRunning) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    if (state.isServiceRunning) "✅ 服务已激活" else "❌ 服务未运行",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (state.isServiceRunning) "所有高级功能可用" else "该模式需要通过 ADB 或 Shizuku 激活",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FeaturesCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("高级模式优势", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FeatureItem("🖥️ 隔离运行", "AI 在后台静默操作，不干扰您的日常使用")
            FeatureItem("🚀 极速响应", "无需通过辅助功能模拟点击，响应更迅速")
            FeatureItem("🔒 安全沙箱", "基于 Binder 的安全通信机制")
        }
    }
}

@Composable
private fun FeatureItem(icon: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun GuideCard(
    shizukuStatus: ActivationStatus,
    activationCommand: String,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenSettings: () -> Unit,
    onLaunchShizuku: () -> Unit,
    onActivateViaShizuku: () -> Unit,
    onCopyCommand: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("💡 激活引导", style = MaterialTheme.typography.titleMedium)
            
            if (errorMessage != null) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }

            Text("方案一：Shizuku (推荐)", style = MaterialTheme.typography.labelLarge)
            
            when (shizukuStatus) {
                ActivationStatus.ACTIVATED -> {
                    Text("✅ Shizuku 已授权，可以一键启动。", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = onActivateViaShizuku,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("正在激活...")
                        } else {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("通过 Shizuku 一键启动")
                        }
                    }
                }
                ActivationStatus.NO_PERMISSION -> {
                    Text("❗ Shizuku 已运行，但尚未授权本应用。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onLaunchShizuku, modifier = Modifier.fillMaxWidth()) {
                        Text("去授权 (打开 Shizuku)")
                    }
                }
                else -> {
                    Text("如果您已安装 Shizuku 并且已激活，请在 Shizuku 中授权本应用。", 
                        style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onLaunchShizuku, modifier = Modifier.fillMaxWidth()) {
                        Text("打开 Shizuku")
                    }
                }
            }

            Divider()

            Text("方案二：手动 ADB", style = MaterialTheme.typography.labelLarge)
            Text("将手机连接电脑，开启 USB 调试，执行以下命令：", 
                style = MaterialTheme.typography.bodySmall)
            
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    activationCommand,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            OutlinedButton(
                onClick = { onCopyCommand(activationCommand) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("复制完整命令")
            }
            
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("打开开发者设置")
            }
        }
    }
}

@Composable
fun ServiceActiveCard(
    onTestService: () -> Unit,
    onStopService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Shell Service 运行中", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTestService, modifier = Modifier.weight(1f)) { Text("功能测试") }
                OutlinedButton(onClick = onStopService, modifier = Modifier.weight(1f)) { Text("停止服务") }
            }
        }
    }
}
