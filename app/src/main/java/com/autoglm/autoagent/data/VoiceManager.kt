package com.autoglm.autoagent.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var recognizer: OfflineRecognizer? = null
    private var stream: OfflineStream? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    
    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState = _voiceState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    
    // Paraformer 2024 Chinese model
    private val MODEL_NAME = "sherpa-onnx-paraformer"

    sealed class VoiceState {
        object Idle : VoiceState()
        data class Downloading(val progress: Int) : VoiceState()
        object Initializing : VoiceState()
        object Listening : VoiceState()
        data class Error(val msg: String) : VoiceState()
    }

    private var onResultCallback: ((String) -> Unit)? = null

    fun startListening(onResult: (String) -> Unit) {
        onResultCallback = onResult
        
        if (recognizer != null) {
            startRecording()
        } else {
            scope.launch {
                initModel()
                if (recognizer != null) {
                    startRecording()
                }
            }
        }
    }

    @Synchronized
    fun stopListening() {
        if (audioRecord == null) return
        
        isRecording = false
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
        } catch (e: IllegalStateException) {
            Log.w("VoiceManager", "Error stopping AudioRecord: ${e.message}")
        } catch (e: Exception) {
            Log.e("VoiceManager", "Exception during stop", e)
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error releasing AudioRecord", e)
            }
            audioRecord = null
            _voiceState.value = VoiceState.Idle
        }
    }

    private fun startRecording() {
        stopListening() // Ensure clean state
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                _voiceState.value = VoiceState.Error("需要录音权限")
                return
            }
            
            val currentRecognizer = recognizer
            if (currentRecognizer == null) {
                _voiceState.value = VoiceState.Error("Recognizer not initialized")
                return
            }
            
            stream = currentRecognizer.createStream()
            
            val sampleRate = 16000
            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _voiceState.value = VoiceState.Error("Failed to initialize AudioRecord")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            _voiceState.value = VoiceState.Listening

            scope.launch(Dispatchers.IO) {
                processAudioOffline()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to start recording", e)
            _voiceState.value = VoiceState.Error("Failed to start: ${e.message}")
            stopListening()
        }
    }

    private suspend fun processAudioOffline() {
        val shortBuffer = ShortArray(1600) // 0.1s chunk
        val currentRecognizer = recognizer ?: return
        val currentStream = stream ?: return

        val startTime = System.currentTimeMillis()
        val maxRecordingMs = 8000L 

        var hasReceivedAudio = false // Track local audio state

        try {
            var silenceCount = 0
            val maxSilenceFrames = 20 // 2s

            while (isRecording) {
                // Double check if cleared externally
                if (audioRecord == null) break

                if (System.currentTimeMillis() - startTime > maxRecordingMs) {
                    Log.d("VoiceManager", "Recording timeout")
                    break
                }

                val read = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                if (read > 0) {
                    val floatBuffer = FloatArray(read)
                    var currentMaxAmp = 0
                    
                    for (i in 0 until read) {
                        floatBuffer[i] = shortBuffer[i] / 32768.0f
                        val amp = Math.abs(shortBuffer[i].toInt())
                        if (amp > currentMaxAmp) currentMaxAmp = amp
                    }
                    
                    // Accept waveform every chunk
                    currentStream.acceptWaveform(floatBuffer, 16000)
                    hasReceivedAudio = true
                    
                    // Simple VAD
                    if (currentMaxAmp > 800) {
                        silenceCount = 0
                    } else {
                        silenceCount++
                        if (silenceCount > maxSilenceFrames && System.currentTimeMillis() - startTime > 1000) {
                             Log.d("VoiceManager", "Silence detected")
                             break
                        }
                    }
                } else if (read < 0) {
                    Log.e("VoiceManager", "Audio read error: $read")
                    break
                }
            }
            
            // Critical: Don't call stopListening() here if loop exited due to isRecording=false
            // But we must decoding.
            // Safest pattern: Just stop audio now.
            stopListening()

            if (hasReceivedAudio) {
                currentRecognizer.decode(currentStream)
                val result = currentRecognizer.getResult(currentStream)
                
                if (result.text.isNotBlank()) {
                    Log.d("VoiceManager", "Recognition result: ${result.text}")
                    withContext(Dispatchers.Main) {
                        onResultCallback?.invoke(result.text)
                    }
                } else {
                     withContext(Dispatchers.Main) {
                        _voiceState.value = VoiceState.Error("未听到声音")
                     }
                }
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Recognition process failed", e)
             withContext(Dispatchers.Main) {
                _voiceState.value = VoiceState.Error("识别出错")
            }
        } finally {
            stream?.release()
            stream = null
        }
    }

    @Synchronized
    fun cancelListening() {
        if (audioRecord == null) return
        
        isRecording = false
        // Just stop and release, do NOT invoke callback or decode
        try {
            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.stop()
            }
        } catch (e: Exception) {
            Log.e("VoiceManager", "Error canceling AudioRecord", e)
        } finally {
            try {
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e("VoiceManager", "Error releasing AudioRecord", e)
            }
            audioRecord = null
            _voiceState.value = VoiceState.Idle
            Log.d("VoiceManager", "Listening Canceled")
        }
    }

    // Public method to preload model
    fun preloadModel() {
        if (recognizer == null && _voiceState.value !is VoiceState.Initializing) {
            scope.launch {
                initModel()
            }
        }
    }

    private suspend fun initModel() {
        _voiceState.value = VoiceState.Initializing
        try {
            // 直接从 assets 加载,不复制到外部存储
            val config = createRecognizerConfig()
            recognizer = OfflineRecognizer(
                assetManager = context.assets,
                config = config
            )
            Log.d("VoiceManager", "Model loaded from assets")
            _voiceState.value = VoiceState.Idle
        } catch (e: Exception) {
            Log.e("VoiceManager", "Failed to load model", e)
            _voiceState.value = VoiceState.Error("Model load failed: ${e.message}")
        }
    }





    private fun createRecognizerConfig(): OfflineRecognizerConfig {
        // 直接使用 assets 路径
        val modelConfig = OfflineModelConfig(
            paraformer = OfflineParaformerModelConfig(
                model = "$MODEL_NAME/model.int8.onnx"
            ),
            tokens = "$MODEL_NAME/tokens.txt",
            numThreads = 1,
            provider = "cpu",
            debug = false,
            modelType = "paraformer"
        )

        return OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
    }

    fun cleanup() {
        stopListening()
        recognizer?.release()
        recognizer = null
    }
}
