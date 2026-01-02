package com.autoglm.autoagent.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.autoglm.autoagent.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    // Static fallback from Open-AutoGLM config/apps.py
    // This helps when system labels might differ (e.g. "WeChat" vs "微信")
    private val staticAppMap = mapOf(
        "微信" to "com.tencent.mm",
        "wechat" to "com.tencent.mm",
        "qq" to "com.tencent.mobileqq",
        "淘宝" to "com.taobao.taobao",
        "京东" to "com.jingdong.app.mall",
        "拼多多" to "com.xunmeng.pinduoduo",
        "小红书" to "com.xingin.xhs",
        "抖音" to "com.ss.android.ugc.aweme",
        "快手" to "com.smile.gifmaker",
        "bilibili" to "tv.danmaku.bili",
        "高德地图" to "com.autonavi.minimap",
        "百度地图" to "com.baidu.BaiduMap",
        "美团" to "com.sankuai.meituan",
        "饿了么" to "me.ele",
        "支付宝" to "com.eg.android.AlipayGphone",
        "alipay" to "com.eg.android.AlipayGphone",
        "相机" to "com.android.camera",
        "settings" to "com.android.settings",
        "设置" to "com.android.settings"
    )
    
    private val appMap = mutableMapOf<String, String>()
    private var isInitialized = false

    init {
        // 先从配置加载缓存的应用列表
        try {
            val cachedApps = settingsRepository.loadAppList()
            if (cachedApps.isNotEmpty()) {
                appMap.putAll(cachedApps)
                Log.d("AppManager", "Loaded ${cachedApps.size} apps from cache")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e("AppManager", "Failed to load cached apps", e)
        }
        
        // 添加静态映射作为 fallback
        if (appMap.isEmpty()) {
            appMap.putAll(staticAppMap)
            Log.d("AppManager", "Initialized with ${staticAppMap.size} static apps")
            isInitialized = true
        }
    }
    
    private fun ensureInitialized() {
        if (!isInitialized || appMap.size <= staticAppMap.size) {
            refreshAppList()
        }
    }

    fun refreshAppList() {
        Log.d("AppManager", "Refreshing app list...")
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            
            // 先保存旧数据
            val oldAppMap = appMap.toMap()
            appMap.clear()
            
            // 1. Add static map first (high priority for known aliases)
            appMap.putAll(staticAppMap)

            // 2. Add dynamic installed apps
            for (pkg in packages) {
                val intent = pm.getLaunchIntentForPackage(pkg.packageName)
                if (intent != null) {
                    val label = pkg.applicationInfo.loadLabel(pm).toString()
                    appMap[label.lowercase()] = pkg.packageName
                    appMap[label] = pkg.packageName // Case sensitive backup
                }
            }
            Log.d("AppManager", "Indexed ${appMap.size} apps (${packages.size} packages scanned)")
            
            // 保存到配置文件
            settingsRepository.saveAppList(appMap)
            Log.d("AppManager", "Saved app list to config")
        } catch (e: Exception) {
            Log.e("AppManager", "Failed to list apps", e)
            // 如果失败,至少保证有 staticAppMap
            if (appMap.isEmpty()) {
                appMap.putAll(staticAppMap)
                Log.d("AppManager", "Fallback to static app map")
            }
        }
    }

    fun stopApp(appName: String): Boolean {
        ensureInitialized()
        val targetPkg = appMap[appName.lowercase()] 
            ?: appMap.keys.find { it.contains(appName, ignoreCase = true) }?.let { appMap[it] }
            
        if (targetPkg != null) {
            Log.d("AppManager", "Attempting to stop app: $targetPkg ($appName)")
            return try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                am.killBackgroundProcesses(targetPkg)
                true
            } catch (e: Exception) {
                Log.e("AppManager", "Failed to stop $targetPkg", e)
                false
            }
        }
        return false
    }

    fun launchApp(appName: String): Boolean {
        ensureInitialized()  // 确保已初始化
        
        Log.d("AppManager", "Attempting to launch app: $appName")
        Log.d("AppManager", "Total apps in map: ${appMap.size}")
        
        // Fuzzy match
        val targetPkg = appMap[appName.lowercase()] 
            ?: appMap.keys.find { it.contains(appName, ignoreCase = true) }?.let { appMap[it] }
        
        if (targetPkg != null) {
            Log.d("AppManager", "Found package: $targetPkg for app: $appName")
            return try {
                val intent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                if (intent == null) {
                    Log.e("AppManager", "Launch intent is null for $targetPkg")
                    return false
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d("AppManager", "Successfully started activity for $targetPkg")
                true
            } catch (e: Exception) {
                Log.e("AppManager", "Failed to launch $targetPkg", e)
                false
            }
        }
        Log.e("AppManager", "App not found in map: $appName. Available apps: ${appMap.keys.take(10)}")
        return false
    }
}
