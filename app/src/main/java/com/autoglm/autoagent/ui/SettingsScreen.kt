package com.autoglm.autoagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoglm.autoagent.data.ApiConfig
import com.autoglm.autoagent.data.ApiProvider
import com.autoglm.autoagent.data.SettingsRepository
import com.autoglm.autoagent.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PermissionStatus(
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val queryPackagesGranted: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    val config = repository.config.stateIn(viewModelScope, SharingStarted.Lazily, null)
    
    private val _permissionStatus = MutableStateFlow(PermissionStatus())
    val permissionStatus = _permissionStatus.asStateFlow()

    fun saveConfig(provider: ApiProvider, baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            repository.saveConfig(provider, baseUrl, apiKey, model)
        }
    }
    
    fun checkPermissions(context: Context) {
        val status = PermissionStatus(
            accessibilityGranted = isAccessibilityServiceEnabled(context),
            overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true,
            queryPackagesGranted = canQueryPackages(context)
        )
        _permissionStatus.value = status
    }
    
    private fun canQueryPackages(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            // 正常的安卓手机至少有20+系统应用
            // 如果少于20个，说明权限被拒
            packages.size >= 20
        } catch (e: Exception) {
            false
        }
    }
    
    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/com.autoglm.autoagent.service.AutoAgentService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return enabledServices.contains(service)
    }
    
    fun requestPermissions(context: Context) {
        val status = _permissionStatus.value
        
        when {
            !status.accessibilityGranted -> {
                // 跳转到无障碍设置
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            !status.overlayGranted -> {
                // 跳转到悬浮窗设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
            !status.queryPackagesGranted -> {
                // 跳转到应用详情页，方便用户找到“权限管理”并开启“获取应用列表”
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            else -> {
                // 所有权限已授予
                checkPermissions(context)
            }
        }
    }
    
    fun loadProviderConfig(provider: ApiProvider): ApiConfig {
        return repository.loadProviderConfig(provider)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val config by viewModel.config.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val context = LocalContext.current
    
    // 进入设置页时自动检测权限
    LaunchedEffect(Unit) {
        viewModel.checkPermissions(context)
    }
    
    if (config == null) return

    var selectedProvider by remember { mutableStateOf(config!!.provider) }
    var baseUrl by remember { mutableStateOf(config!!.baseUrl) }
    var apiKey by remember { mutableStateOf(config!!.apiKey) }
    var model by remember { mutableStateOf(config!!.model) }
    var showApiKey by remember { mutableStateOf(false) }

    // Pre-fill defaults when switching
    LaunchedEffect(selectedProvider) {
        val providerConfig = viewModel.loadProviderConfig(selectedProvider)
        baseUrl = providerConfig.baseUrl
        apiKey = providerConfig.apiKey
        model = providerConfig.model
    }

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
                        "设置",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "←",
                            fontSize = 24.sp,
                            color = PrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground.copy(alpha = 0.8f)
                )
            )

            // 滚动内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 权限检查卡片
                SettingsCard(
                    title = "🛡️ 权限状态",
                    content = {
                        PermissionItem("无障碍服务", permissionStatus.accessibilityGranted)
                        Text(
                            text = "• 用途: 支持 AI 自动点击、滑动、输入等所有操作",
                            color = TextHint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        
                        PermissionItem("悬浮窗权限", permissionStatus.overlayGranted)
                        Text(
                            text = "• 用途: 显示悬浮控制按钮，方便随时停止任务",
                            color = TextHint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        
                        PermissionItem("获取应用列表", permissionStatus.queryPackagesGranted)
                        Text(
                            text = "• 用途: 让 AI 能启动其他应用(如\"打开拼多多\")\n• 若显示未授予，请点击下方按钮进入应用详情页手动开启“获取应用列表”权限",
                            color = TextHint,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.requestPermissions(context) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("检查并请求权限", color = TextPrimary)
                        }
                    }
                )

                // API配置卡片
                SettingsCard(
                    title = "🌐 API配置",
                    content = {
                        Text(
                            "选择API提供商",
                            color = TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProviderChip(
                                text = "自定义",
                                selected = selectedProvider == ApiProvider.EDGE,
                                onClick = { selectedProvider = ApiProvider.EDGE },
                                modifier = Modifier.weight(1f)
                            )
                            ProviderChip(
                                text = "智谱AI",
                                selected = selectedProvider == ApiProvider.ZHIPU,
                                onClick = { selectedProvider = ApiProvider.ZHIPU },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        DarkTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = "Base URL",
                            placeholder = "https://open.bigmodel.cn/api/paas/v4/"
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DarkTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = "API Key",
                            placeholder = "输入你的API密钥",
                            visualTransformation = if (showApiKey) 
                                VisualTransformation.None 
                            else 
                                PasswordVisualTransformation(),
                            trailingIcon = {
                                TextButton(onClick = { showApiKey = !showApiKey }) {
                                    Text(
                                        if (showApiKey) "隐藏" else "显示",
                                        color = PrimaryBlue,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        DarkTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = "模型名称",
                            placeholder = "glm-4-flash"
                        )
                    }
                )

                // 关于卡片
                SettingsCard(
                    title = "ℹ️ 关于",
                    content = {
                        InfoRow("版本", "1.0.0")
                        InfoRow("项目", "AutoDroid")
                        InfoRow("开源", "GitHub")
                    }
                )

                // 底部保存按钮
                Button(
                    onClick = {
                        viewModel.saveConfig(selectedProvider, baseUrl, apiKey, model)
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        "保存设置",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun PermissionItem(name: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = if (granted) "✅" else "❌",
            fontSize = 18.sp
        )
    }
}

@Composable
fun ProviderChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PrimaryBlue else CardBackgroundDark
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 14.sp
        )
    }
}

@Composable
fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextHint, fontSize = 14.sp) },
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CardBackgroundDark,
                unfocusedContainerColor = CardBackgroundDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PrimaryBlue,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 14.sp
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 14.sp
        )
    }
}
