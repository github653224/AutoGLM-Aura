package com.autoglm.autoagent.shell

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * 使用 mDNS 自动发现无线调试端口
 * 
 * Android 无线调试使用 _adb-tls-connect._tcp 服务类型广播
 */
@Singleton
class MdnsDiscovery @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    
    /**
     * 扫描 ADB 无线调试端口
     * @param timeoutMs 扫描超时时间
     * @return 发现的端口，未发现返回 null
     */
    suspend fun discoverAdbPort(timeoutMs: Long = 5000): Int? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                var discoveryListener: NsdManager.DiscoveryListener? = null
                
                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "mDNS discovery started for $serviceType")
                    }
                    
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Found service: ${serviceInfo.serviceName}")
                        
                        // 解析服务获取端口
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "Resolve failed: $errorCode")
                            }
                            
                            override fun onServiceResolved(si: NsdServiceInfo) {
                                Log.d(TAG, "Resolved: ${si.host}:${si.port}")
                                if (continuation.isActive) {
                                    try {
                                        nsdManager.stopServiceDiscovery(discoveryListener)
                                    } catch (e: Exception) { /* ignore */ }
                                    continuation.resume(si.port)
                                }
                            }
                        })
                    }
                    
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                    }
                    
                    override fun onDiscoveryStopped(serviceType: String) {
                        Log.d(TAG, "Discovery stopped")
                    }
                    
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Start discovery failed: $errorCode")
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                    
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.e(TAG, "Stop discovery failed: $errorCode")
                    }
                }
                
                continuation.invokeOnCancellation {
                    try {
                        nsdManager.stopServiceDiscovery(discoveryListener)
                    } catch (e: Exception) { /* ignore */ }
                }
                
                try {
                    nsdManager.discoverServices(
                        SERVICE_TYPE,
                        NsdManager.PROTOCOL_DNS_SD,
                        discoveryListener
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start discovery", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
    
    companion object {
        private const val TAG = "MdnsDiscovery"
        private const val SERVICE_TYPE = "_adb-tls-connect._tcp"
    }
}
