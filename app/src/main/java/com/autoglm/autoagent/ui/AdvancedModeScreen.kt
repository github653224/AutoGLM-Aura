package com.autoglm.autoagent.ui

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
import com.autoglm.autoagent.shizuku.ActivationStatus
import com.autoglm.autoagent.shizuku.ShizukuManager

/**
 * 高级模式激活界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedModeScreen(
    shizukuManager: ShizukuManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    var activationStatus by remember { 
        mutableStateOf(shizukuManager.getActivationStatus()) 
    }
    
    // 定期检查状态
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        activationStatus = shizukuManager.getActivationStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("高级模式") },
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
            StatusCard(activationStatus)
            
            // 功能对比
            AdvancedFeaturesCard()
            
            // 激活引导
            when (activationStatus) {
                ActivationStatus.NOT_INSTALLED -> {
                    InstallShizukuGuide()
                }
                ActivationStatus.NOT_RUNNING -> {
                    WirelessActivationGuide(
                        onCopyCommands = { commands ->
                            clipboardManager.setText(AnnotatedString(commands))
                        },
                        onRefresh = {
                            activationStatus = shizukuManager.getActivationStatus()
                        }
                    )
                }
                ActivationStatus.NO_PERMISSION -> {
                    PermissionGuide(
                        onRequestPermission = {
                            shizukuManager.requestPermission()
                        }
                    )
                }
                ActivationStatus.ACTIVATED -> {
                    ActivatedCard()
                }
            }
        }
    }
}

@Composable
fun StatusCard(status: ActivationStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ActivationStatus.ACTIVATED -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                when (status) {
                    ActivationStatus.ACTIVATED -> Icons.Default.CheckCircle
                    else -> Icons.Default.Warning
                },
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            
            Column {
                Text(
                    text = when (status) {
                        ActivationStatus.NOT_INSTALLED -> "未安装 Shizuku"
                        ActivationStatus.NOT_RUNNING -> "Shizuku 未运行"
                        ActivationStatus.NO_PERMISSION -> "未授权"
                        ActivationStatus.ACTIVATED -> "✅ 高级模式已激活"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = when (status) {
                        ActivationStatus.NOT_INSTALLED -> "需要先安装 Shizuku"
                        ActivationStatus.NOT_RUNNING -> "需要激活 Shizuku 服务"
                        ActivationStatus.NO_PERMISSION -> "需要授权 AutoDroid"
                        ActivationStatus.ACTIVATED -> "所有高级功能可用"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun AdvancedFeaturesCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "高级模式功能",
                style = MaterialTheme.typography.titleMedium
            )
            
            AdvancedFeatureItem("🖥️ 虚拟屏幕", "AI 后台运行，不占用主屏幕")
            AdvancedFeatureItem("🚀 应用自动启动", "无需手动切换应用")
            AdvancedFeatureItem("🔄 跨应用操作", "AI 自动在多个应用间切换")
            AdvancedFeatureItem("👁️ 实时浮窗", "查看 AI 操作进度")
        }
    }
}

@Composable
fun AdvancedFeatureItem(icon: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon)
        Text(description, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun WirelessActivationGuide(
    onCopyCommands: (String) -> Unit,
    onRefresh: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "📱 无线调试激活（无需电脑）",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                "适用于 Android 11+，完全无需电脑",
                style = MaterialTheme.typography.bodySmall
            )
            
            Divider()
            
            // 步骤 1
            Text("1️⃣ 安装 Termux", style = MaterialTheme.typography.titleSmall)
            Text("从 F-Droid 或 GitHub 下载")
            Button(
                onClick = {
                    // TODO: 打开下载链接
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("下载 Termux")
            }
            
            // 步骤 2
            Text("2️⃣ 启用无线调试", style = MaterialTheme.typography.titleSmall)
            Text("设置 → 开发者选项 → 无线调试 → 开启")
            
            // 步骤 3
            Text("3️⃣ 在 Termux 执行命令", style = MaterialTheme.typography.titleSmall)
            
            val commands = """
                pkg install android-tools
                adb pair localhost:端口号
                (输入配对码)
                adb connect localhost:5555
                adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
            """.trimIndent()
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    commands,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyCommands(commands) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(4.dp))
                    Text("复制命令")
                }
                
                Button(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("检查状态")
                }
            }
        }
    }
}

@Composable
fun InstallShizukuGuide() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "安装 Shizuku",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text("高级模式需要先安装 Shizuku")
            
            Button(
                onClick = {
                    // TODO: 打开 Shizuku 下载页
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(8.dp))
                Text("下载 Shizuku")
            }
        }
    }
}

@Composable
fun PermissionGuide(onRequestPermission: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "授权 AutoDroid",
                style = MaterialTheme.typography.titleMedium
            )
            
            Text("Shizuku 已运行，需要授权 AutoDroid 使用")
            
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, null)
                Spacer(Modifier.width(8.dp))
                Text("请求授权")
            }
        }
    }
}

@Composable
fun ActivatedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "高级模式已激活！",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "所有高级功能现已可用",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
