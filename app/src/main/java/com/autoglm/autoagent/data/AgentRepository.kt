package com.autoglm.autoagent.data

import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.autoglm.autoagent.data.api.*
import com.autoglm.autoagent.service.AppManager
import com.autoglm.autoagent.service.AutoAgentService
import com.autoglm.autoagent.service.FloatingWindowService
import com.autoglm.autoagent.service.ScreenCaptureService
import com.autoglm.autoagent.service.ScreenshotData
import com.autoglm.autoagent.config.TimingConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch // Fix Unresolved refernece 'launch'
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.IOException
import java.util.regex.Pattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.text.RegexOption

sealed class AgentState {
    object Idle : AgentState()
    object Planning : AgentState()
    object Running : AgentState()
    object Paused : AgentState()
    object Listening : AgentState()
    data class Error(val msg: String) : AgentState()
}

@Singleton
class AgentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiClient: AIClient,
    private val appManager: AppManager,
    private val settingsRepository: SettingsRepository,
    private val voiceManager: VoiceManager
) {
    
    // Scope for launching tasks from voice callback
    private val repositoryScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    
    // Job to track current running task for immediate cancellation
    private var currentTaskJob: kotlinx.coroutines.Job? = null

    fun setListening(isListening: Boolean) {
        if (isListening) {
            if (_agentState.value is AgentState.Listening) return
            
            // Launch observation of voice state + command listening
            repositoryScope.launch {
                voiceManager.voiceState.collect { vState ->
                    when (vState) {
                        is VoiceManager.VoiceState.Downloading -> {
                            // Map Downloading to a visual state (can reuse Idle with message or add new state. Using Planning here as a busy indicator fallback, or stick to Idle with Toast)
                             // Ideally we'd have a specific "Downloading" state, but for now let's just Log and maybe not block UI logic too much, or map Planning?
                             // Let's use Idle but Log it? NO, UI needs to show progress.
                             // Let's temporarily map Planning -> "Downloading Model ${vState.progress}%" if we could change text.
                             // Simpler: Just rely on UI observing voiceManager directly? No, AgentRepository is the source of truth.
                             // I will add a temporary state or reuse Planning.
                        }
                        is VoiceManager.VoiceState.Initializing -> {
                            // Loading model
                        }
                        is VoiceManager.VoiceState.Listening -> {
                            _agentState.value = AgentState.Listening
                        }
                        is VoiceManager.VoiceState.Error -> {
                            _agentState.value = AgentState.Error(vState.msg)
                            setListening(false) 
                        }
                        else -> { }
                    }
                }
            }

            voiceManager.startListening { text ->
                Log.d("AgentRepository", "Voice Command: $text")
                _agentState.value = AgentState.Idle 
                currentTaskJob = repositoryScope.launch {
                    executeTask(text)
                }
            }
        } else {
            voiceManager.stopListening()
            if (_agentState.value == AgentState.Listening) {
                _agentState.value = AgentState.Idle
            }
        }
    }
    
    fun setError(message: String) {
        _agentState.value = AgentState.Error(message)
    }
    
    fun resetToIdle() {
        voiceManager.stopListening()
        _agentState.value = AgentState.Idle
    }
    
    fun pauseAgent() {
        if (_agentState.value == AgentState.Running) {
            _agentState.value = AgentState.Paused
            addUiMessage("system", "Agent Paused. Waiting for manual interaction...")
        }
    }

    fun resumeAgent() {
        if (_agentState.value == AgentState.Paused) {
            _agentState.value = AgentState.Running
            addUiMessage("system", "Agent Resumed.")
        }
    }

    fun cancelListening() {
        voiceManager.cancelListening()
        if (_agentState.value == AgentState.Listening) {
             _agentState.value = AgentState.Idle
        }
    }
    
    fun preloadVoiceModel() {
        repositoryScope.launch {
            voiceManager.preloadModel()
        }
    }
    
    private val _agentState = MutableStateFlow<AgentState>(AgentState.Idle)
    val agentState = _agentState.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages = _chatMessages.asStateFlow()

    private val messages = mutableListOf<ChatMessage>()
    
    // Duplicate click tracking
    private var lastActionType = ""
    private var lastTapX = 0
    private var lastTapY = 0
    private var sameTapCount = 0
    private var isDeadlockState = false
    
    // Default screen dimensions (fallback only, actual size from screenshot)
    private val defaultScreenWidth = 1080
    private val defaultScreenHeight = 2400

    // 任务笔记: 用于实装 Note 功能,存储跨步骤的关键数据
    private val taskNotes = mutableListOf<String>()

    // Official System Prompt (Condensed)
    // Official System Prompt (Full Chinese Version)
    private fun getSystemPrompt(): String {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 EEEE", Locale.CHINA)
        val dateStr = dateFormat.format(Date())
        return """
今天的日期是: $dateStr

你是一个智能体分析专家，可以根据操作历史和当前状态图执行一系列操作来完成任务。
你必须严格按照要求输出以下格式：
<think>{think}</think>
<answer>{action}</answer>

其中：
- {think} 是对你为什么选择这个操作的简短推理说明。
- {action} 是本次执行的具体操作指令，必须严格遵循下方定义的指令格式。

操作指令及其作用如下：
- do(action="Launch", app="xxx")  
    Launch是启动目标app的操作，这比通过主屏幕导航更快。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y])  
    Tap是点击操作，点击屏幕上的特定点。可用此操作点击按钮、选择项目、从主屏幕打开应用程序，或与任何可点击的用户界面元素进行交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Tap", element=[x,y], message="重要操作")  
    基本功能同Tap，点击涉及财产、支付、隐私等敏感按钮时触发。
- do(action="Type", text="xxx")  
    Type是输入操作，在当前聚焦的输入框中输入文本。使用此操作前，请确保输入框已被聚焦（先点击它）。输入的文本将像使用键盘输入一样输入。重要提示：手机可能正在使用 ADB 键盘，该键盘不会像普通键盘那样占用屏幕空间。要确认键盘已激活，请查看屏幕底部是否显示 'ADB Keyboard {ON}' 类似的文本，或者检查输入框是否处于激活/高亮状态。不要仅仅依赖视觉上的键盘显示。自动清除文本：当你使用输入操作时，输入框中现有的任何文本（包括占位符文本和实际输入）都会在输入新文本前自动清除。你无需在输入前手动清除文本——直接使用输入操作输入所需文本即可。
- do(action="Interact")  
    Interact是当有多个满足条件的选项时而触发的交互操作，询问用户如何选择。
- do(action="Swipe", start=[x1,y1], end=[x2,y2])  
    Swipe是滑动操作，通过从起始坐标拖动到结束坐标来执行滑动手势。可用于滚动内容、在屏幕之间导航、下拉通知栏以及项目栏或进行基于手势的导航。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。滑动持续时间会自动调整以实现自然的移动。此操作完成后，您将自动收到结果状态的截图。
- do(action="Note", message="xxx")  
    做笔记的操作，用于跨应用记录当前应用页面的关键信息、数据或状态，以便在后续步骤（跨应用时）查阅。笔记内容将在后续每一轮的上下文中展示。
- do(action="Call_API", instruction="xxx")  
    总结或评论当前页面内容，或基于已记录的笔记进行逻辑汇总。
- do(action="Long Press", element=[x,y])  
    Long Pres是长按操作，在屏幕上的特定点长按指定时间。可用于触发上下文菜单、选择文本或激活长按交互。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的屏幕截图。
- do(action="Double Tap", element=[x,y])  
    Double Tap在屏幕上的特定点快速连续点按两次。使用此操作可以激活双击交互，如缩放、选择文本或打开项目。坐标系统从左上角 (0,0) 开始到右下角（999,999)结束。此操作完成后，您将自动收到结果状态的截图。
- do(action="Take_over", message="xxx")  
    Take_over是接管操作，表示在登录和验证阶段需要用户协助。
- do(action="GetUI")  
    GetUI是获取当前全量UI树结构的操作。当你通过截图无法准确判断元素位置、状态（如按钮是否可点击）或者需要提取复杂列表文本时使用。此操作将返回一个 JSON 结构，包含屏幕上所有有效元素的 class、text、id、bounds 以及属性状态（如 selected、checked、focused）。
- do(action="Back")  
    导航返回到上一个屏幕或关闭当前对话框。相当于按下 Android 的返回按钮。使用此操作可以从更深的屏幕返回、关闭弹出窗口或退出当前上下文。此操作完成后，您将自动收到结果状态的截图。
- do(action="Home") 
    Home是回到系统桌面的操作，相当于按下 Android 主屏幕按钮。使用此操作可退出当前应用并返回启动器，或从已知状态启动新任务。此操作完成后，您将自动收到结果状态的截图。
- do(action="Wait", duration="x seconds")  
    等待页面加载，x为需要等待多少秒。
- finish(message="xxx")  
    finish是结束任务的操作，表示准确完整完成任务，message是终止信息。 

必须遵循的规则：
1. 在执行任何操作前，先检查当前app是否是目标app，如果不是，先执行 Launch。
2. 如果进入到了无关页面，先执行 Back。如果执行Back后页面没有变化，请点击页面左上角的返回键进行返回，或者右上角的X号关闭。
3. 如果页面未加载出内容，最多连续 Wait 三次，否则执行 Back重新进入。
4. 如果页面显示网络问题，需要重新加载，请点击重新加载.如果没有重新加载按钮请重新打开app.
5. 如果当前页面找不到目标联系人、商品、店铺等信息，可以尝试 Swipe 滑动查找。
6. 遇到价格区间、时间区间等筛选条件，如果没有完全符合的，可以放宽要求。
7. 在做小红书总结类任务时一定要筛选图文笔记。
8. 购物车全选后再点击全选可以把状态设为全不选，在做购物车任务时，如果购物车里已经有商品被选中时，你需要点击全选后再点击取消全选，再去找需要购买或者删除的商品。
9. 在做外卖任务时，如果相应店铺购物车里已经有其他商品你需要先把购物车清空再去购买用户指定的外卖。
10. 在做点外卖任务时，如果用户需要点多个外卖，请尽量在同一店铺进行购买，如果无法找到可以下单，并说明某个商品未找到。
11. 请严格遵循用户意图执行任务，用户的特殊要求可以执行多次搜索，滑动查找。比如（i）用户要求点一杯咖啡，要咸的，你可以直接搜索咸咖啡，或者搜索咖啡后滑动查找咸的咖啡，比如海盐咖啡。（ii）用户要找到XX群，发一条消息，你可以先搜索XX群，找不到结果后，将"群"字去掉，搜索XX重试。（iii）用户要找到宠物友好的餐厅，你可以搜索餐厅，找到筛选，找到设施，选择可带宠物，或者直接搜索可带宠物，必要时可以使用AI搜索。
12. 在选择日期时，如果原滑动方向与预期日期越来越远，请向反方向滑动查找。
13. 执行任务过程中如果有多个可选择的项目栏，请逐个查找每个项目栏，直到完成任务，一定不要在同一项目栏多次查找，从而陷入死循环。
14. 在执行下一步操作前请一定要检查上一步的操作是否生效，如果点击没生效，可能因为app反应较慢，请先稍微等待一下，如果还是不生效请调整一下点击位置重试，如果仍然不生效请跳过这一步继续任务，并在finish message说明点击不生效。
15. 在执行任务中如果遇到滑动不生效的情况，请调整一下起始点位置，增大滑动距离重试，如果还是不生效，有可能是已经滑到底了，请继续向反方向滑动，直到顶部或底部，如果仍然没有符合要求的结果，请跳过这一步继续任务，并在finish message说明但没找到要求的项目。
16. 在做游戏任务时如果在战斗页面如果有自动战斗一定要开启自动战斗，如果多轮历史状态相似要检查自动战斗是否开启。
17. 如果没有合适的搜索结果，可能是因为搜索页面不对，请返回到搜索页面的上一级尝试重新搜索，如果尝试三次返回上一级搜索后仍然没有符合要求的结果，执行 finish(message="原因")。
18. 在结束任务前请一定要仔细检查任务是否完整准确的完成，如果出现错选、漏选、多选的情况，请返回之前的步骤进行纠正.
19. 如果执行Launch启动app后显示的界面不是符合的界面,请使用home操作返回到主屏幕后尝试通过桌面找到app后使用Tap点击操作启动app,如果桌面找不到请结束任务.
20. 如果最后购买步骤中商品的价格比用户指令中预期的价格超过了很多,说明购买的商品不对,请返回重新寻找商品,不然你的操作会给用户带来额外损失.
""".trimIndent()

    }

    suspend fun executeTask(goal: String) {
        if (_agentState.value !is AgentState.Idle) return
        
        _agentState.value = AgentState.Running
        taskNotes.clear() // 任务开始前必须清空笔记
        
        // ===== 执行前权限和服务检查 =====
        val checkResult = checkPrerequisites()
        if (!checkResult.first) {
            _agentState.value = AgentState.Error(checkResult.second)
            addUiMessage("system", "❌ 前置检查失败:\n${checkResult.second}")
            delay(TimingConfig.Task.ERROR_DELAY)
            stopAgent()
            return
        }
        addUiMessage("system", "✅ 权限检查通过,开始执行任务...")
        // ===== 检查完成 =====

        // 清理历史消息并添加系统提示
        messages.clear()
        messages.add(ChatMessage("system", getSystemPrompt()))
        
        val taskGoal = "Task: $goal"
        addUiMessage("user", taskGoal)
        
        try {
            context.startService(Intent(context, FloatingWindowService::class.java))
        } catch (e: Exception) {
            Log.e("Agent", "Overlay start failed", e)
        }

        val MAX_STEPS = 20
        var stepsCount = 0

        try {
            while (stepsCount < MAX_STEPS) {
                if (_agentState.value == AgentState.Idle) break
                stepsCount++
                
                addUiMessage("system", "Step $stepsCount thinking...")

                // 1. Capture Screenshot
                var screenshotBase64: String? = null
                var currentScreenWidth = defaultScreenWidth  // Default fallback
                var currentScreenHeight = defaultScreenHeight
                var extraDeadlockInfo = ""
                
                if (isDeadlockState) {
                    val uiTree = AutoAgentService.instance?.dumpOptimizedUiTree() ?: "{ \"ui\": [] }"
                    val isUiTreeEmpty = uiTree.contains("\"ui\": []")
                    
                    if (isUiTreeEmpty) {
                        Log.d("Agent", "Deadlock + Empty UI Tree. Fallback to screenshot.")
                        val screenshot = ScreenCaptureService.instance?.captureSnapshot()
                        if (screenshot != null) {
                            screenshotBase64 = screenshot.base64
                            currentScreenWidth = screenshot.width
                            currentScreenHeight = screenshot.height
                        }
                        extraDeadlockInfo = "\n\n** WARNING: No UI Structure detected. Falling back to visual screenshot for recovery. **"
                    } else {
                        Log.d("Agent", "Deadlock + Valid UI Tree. Skipping screenshot.")
                        extraDeadlockInfo = "\n\n** LOGIC RECOVERY: Screenshot suppressed. Refer to UI Tree provided in the previous message's error context. **"
                    }
                } else {
                    Log.d("Agent", "Attempting to capture screenshot...")
                    
                    // API 30+ 直接使用 AccessibilityService 截图
                    val accessibilityService = AutoAgentService.instance
                    if (accessibilityService != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        val bitmap = accessibilityService.takeScreenshotAsync()
                        if (bitmap != null) {
                            // 转换为 base64
                            val stream = java.io.ByteArrayOutputStream()
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                            val byteArray = stream.toByteArray()
                            screenshotBase64 = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                            currentScreenWidth = bitmap.width
                            currentScreenHeight = bitmap.height
                            bitmap.recycle()
                            Log.d("Agent", "✅ Screenshot captured: ${currentScreenWidth}x${currentScreenHeight}")
                        } else {
                            Log.w("Agent", "⚠️ AccessibilityService screenshot returned NULL!")
                        }
                    } else {
                        // 回退到 ScreenCaptureService (API < 30 或 AccessibilityService 未启动)
                        val screenshot = ScreenCaptureService.instance?.captureSnapshot()
                        if (screenshot != null) {
                            screenshotBase64 = screenshot.base64
                            currentScreenWidth = screenshot.width
                            currentScreenHeight = screenshot.height
                            Log.d("Agent", "✅ Screenshot captured via ScreenCaptureService: ${currentScreenWidth}x${currentScreenHeight}")
                        } else {
                            Log.w("Agent", "⚠️ All screenshot methods failed! Using fallback: ${currentScreenWidth}x${currentScreenHeight}")
                            Log.w("Agent", "AccessibilityService.instance = $accessibilityService")
                            Log.w("Agent", "ScreenCaptureService.instance = ${ScreenCaptureService.instance}")
                        }
                    }
                }
                
                // 2. Build User Message
                val contentParts = mutableListOf<ContentPart>()
                if (screenshotBase64 != null) {
                    contentParts.add(ContentPart(type = "image_url", image_url = ImageUrl("data:image/png;base64,$screenshotBase64")))
                }
                
                val currentApp = AutoAgentService.instance?.currentPackageName ?: "Unknown"
                // 构造带笔记的环境信息
                val notesContext = if (taskNotes.isNotEmpty()) "\n\n** Task Notes **\n" + taskNotes.joinToString("\n") { "- $it" } else ""
                
                // 只有首步发送指令, 后续步骤发送环境信息
                val textContent = if (stepsCount == 1) {
                    "$taskGoal\n\n** Current Environment **\nApp: $currentApp$notesContext$extraDeadlockInfo"
                } else {
                    "** Screen Info **\nApp: $currentApp$notesContext$extraDeadlockInfo"
                }
                contentParts.add(ContentPart(type = "text", text = textContent))

                // 添加新的 user 消息(带图片)
                messages.add(ChatMessage("user", contentParts))

                // 3. Inference
                val responseMsg = aiClient.sendMessage(messages) 
                val rawContent = responseMsg.content ?: ""
                Log.d("Agent", "Raw Response: $rawContent")
                
                // 4. Parse Response
                val (think, actionStr) = parseResponse(rawContent)
                
                if (think.isNotEmpty()) {
                    addUiMessage("assistant", "Think: $think")
                }
                addUiMessage("assistant", "Action: $actionStr")
                
                messages.add(ChatMessage("assistant", rawContent))
                
                // ✅ **重要**: 推理后立即移除上一条user消息中的图片(节省Token)
                // 匹配 Python: self._context[-1] = remove_images_from_message(self._context[-1])
                removeImageFromLastUserMessage()

                // 5. Execute Action
                if (actionStr.contains("finish(")) {
                    Log.i("Agent", "Step $stepsCount: Received Finish command.")
                    addUiMessage("system", "任务已完成！")
                    delay(TimingConfig.Task.FINISH_DELAY)
                    stopAgent()
                    return
                }
                
                Log.i("Agent", "Step $stepsCount: Executing Action -> $actionStr")
                val result = executeActionString(actionStr, currentScreenWidth, currentScreenHeight)
                Log.i("Agent", "Step $stepsCount: Execution Result -> $result")
                addUiMessage("system", "Result: $result")
                
                // Python 原版不发送执行结果给 AI!
                // AI 只能通过新截图判断操作是否成功
                // messages.add(ChatMessage("user", "Action executed. Result: $result"))
                
                // 如果是未知操作, 可能是AI输出了冗余文字, 提示它
                if (result.startsWith("Unknown Action")) {
                     messages.add(ChatMessage("user", "Unknown action. Please use specified format: do(action=\"...\", ...) or finish(message=\"...\")"))
                }
                
                // 检查是否暂停
                while (_agentState.value == AgentState.Paused) {
                    delay(TimingConfig.Task.PAUSE_CHECK_DELAY)
                    if (_agentState.value == AgentState.Idle) {
                         addUiMessage("system", "任务已停止")
                         return
                    }
                }

                // 检查是否被停止
                if (_agentState.value == AgentState.Idle) {
                    addUiMessage("system", "任务已停止")
                    return
                }
            }
        } catch (e: Exception) {
            // Handle normal cancellation separately - not an error
            if (e is kotlinx.coroutines.CancellationException) {
                Log.d("Agent", "Task cancelled by user")
                addUiMessage("system", "任务已取消")
                _agentState.value = AgentState.Idle
                return
            }
            
            e.printStackTrace()
            val errorMsg = when (e) {
                is IOException -> "网络连接失败，请检查网络设置"
                else -> e.message ?: "发生未知错误，请检查 API 配置或网络"
            }
            _agentState.value = AgentState.Error(errorMsg)
            addUiMessage("system", "Error: $errorMsg")
            delay(TimingConfig.Task.ERROR_DELAY)
            _agentState.value = AgentState.Idle
        }
    }

    private fun stripPreviousImages() {
        // Iterate through history and remove image_url from old user messages
        // We iterate specifically over the mutable list `messages`
        for (i in 0 until messages.size - 1) { // Skip the very last message (which might be the new one, though we call this before adding new one usually)
            val msg = messages[i]
            if (msg.role == "user" && msg.content is List<*>) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val contentList = msg.content as? MutableList<ContentPart>
                    if (contentList != null) {
                        val iterator = contentList.iterator()
                        var removed = false
                        while (iterator.hasNext()) {
                            val part = iterator.next()
                            if (part.type == "image_url") {
                                iterator.remove()
                                removed = true
                            }
                        }
                        if (removed) {
                            contentList.add(ContentPart(type = "text", text = "(Previous screenshot removed to save context)"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Agent", "Failed to strip image", e)
                }
            }
        }
    }
    
    /**
     * 检查执行前的前置条件
     * @return Pair<成功, 错误信息>
     */
    private fun checkPrerequisites(): Pair<Boolean, String> {
        // 1. 检查 AccessibilityService
        if (AutoAgentService.instance == null) {
            return Pair(false, "无障碍服务未启用\n请在系统设置中启用 AutoGLM 无障碍服务")
        }
        
        // 2. 检查截图功能
        // API 30+ 使用 AccessibilityService.takeScreenshot (无需额外权限)
        // API < 30 需要 MediaProjection (ScreenCaptureService)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // API 30+: 无障碍服务已经足够
            Log.d("Agent", "✅ 使用 AccessibilityService 截图 (API ${android.os.Build.VERSION.SDK_INT})")
        } else {
            // API < 30: 需要 MediaProjection
            if (ScreenCaptureService.instance == null) {
                return Pair(false, "截图服务未启动\n请在应用内授予录屏权限\n(API ${android.os.Build.VERSION.SDK_INT} < 30)")
            }
            Log.d("Agent", "✅ 使用 MediaProjection 截图 (API ${android.os.Build.VERSION.SDK_INT})")
        }
        
        // 3. 检查 AI 配置
        val config = settingsRepository.config.value
        if (config.baseUrl.isBlank() || config.apiKey.isBlank()) {
            return Pair(false, "AI 配置未完成\n请在设置中配置 API URL 和 API Key")
        }
        
        return Pair(true, "")
    }

    private fun parseResponse(content: String): Pair<String, String> {
        var action = ""
        var think = ""
        
        // 1. 优先提取 Action: 寻找 <answer> 标签或 do()/finish() 命令
        val answerMatcher = Pattern.compile("<answer>(.*?)</answer", Pattern.DOTALL).matcher(content)  // 注意：容错不完整的标签
        if (answerMatcher.find()) {
            action = answerMatcher.group(1).trim()
        } else {
            // 兜底: 寻找 do(...) 或 finish(...)
            val cmdP = Pattern.compile("(do\\s*\\(.*?\\)|finish\\s*\\(.*?\\))", Pattern.DOTALL)
            val cmdM = cmdP.matcher(content)
            if (cmdM.find()) {
                action = cmdM.group(1).trim()
            }
        }

        // 2. 规范化 Action 格式
        // AI 可能输出 {action="Back"} 而不是 do(action="Back")
        // 需要将 {action="..."} 转换为 do(action="...")
        if (action.isNotBlank()) {
            // 去除外层花括号（如果有）
            var normalized = action.trim()
            if (normalized.startsWith("{") && normalized.contains("}")) {
                // 提取花括号内的内容
                val endBrace = normalized.indexOf("}")
                normalized = normalized.substring(1, endBrace).trim()
            }
            
            // 如果是纯 action="..." 格式，包装成 do(...)
            if (normalized.startsWith("action=") && !normalized.startsWith("do(")) {
                normalized = "do($normalized)"
            }
            
            action = normalized
        }

        // 3. 提取推理过程 (Think)
        val thinkP = Pattern.compile("(?:\\{think\\}|<think>)\\s*(.*?)\\s*(?:</think>|<answer>|do\\s*\\(|finish\\s*\\(|$)", Pattern.DOTALL)
        val thinkM = thinkP.matcher(content)
        if (thinkM.find()) {
            think = thinkM.group(1).trim()
        }
        
        // 如果标签提取失败且 action 已找到, 将 action 之前的内容全部视为 think
        if (think.isBlank() && action.isNotBlank()) {
            val idx = content.indexOf(action)
            if (idx > 0) {
                think = content.substring(0, idx)
                    .replace(Regex("<think>|\\{think\\}|<answer>"), "")
                    .trim()
            }
        }
        
        // 4. 收尾清理: 确保 action 截断到第一个右括号
        if (action.contains(")")) {
            action = action.substring(0, action.indexOf(")") + 1)
        }
        
        Log.d("Agent", "Parsed -> Think: '${think.take(50)}...', Action: '$action'")
        return Pair(think, action)
    }

    private suspend fun executeActionString(actionStr: String, screenWidth: Int, screenHeight: Int): String {
        val trimmedAction = actionStr.trim()
        
        // 1. Handle explicit commands that are not do(action=...)
        if (trimmedAction.startsWith("finish", ignoreCase = true)) {
             return "Command Recognized" // Finish logic handled outside in loop, but we return string here
        }
        
        // 2. Extract action name from do(action="...")
        // Regex handles spaces and quotes: action \s* = \s* ["'] (name) ["']
        val actionPattern = Pattern.compile("action\\s*=\\s*[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = actionPattern.matcher(trimmedAction)
        
        val actionName = if (matcher.find()) {
            matcher.group(1).lowercase(Locale.ROOT)
        } else {
             // Fallback for special non-standard formats or if no action param
             if (trimmedAction.contains("call_api", ignoreCase = true)) "call_api" else ""
        }
        
        Log.d("Agent", "Resolved Action Name: '$actionName' from '$trimmedAction'")

        return try {
            when (actionName) {
                "tap", "click" -> {
                    val m = Pattern.compile("element\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+)\\s*,\\s*(\\d+))?\\s*\\]").matcher(trimmedAction)
                    if (m.find()) {
                        val (normX, normY) = resolveCenter(m)
                        if (checkDuplicate("Tap", normX, normY)) {
                            isDeadlockState = true
                            return handleDeadlock(normX, normY)
                        }
                        isDeadlockState = false
                        val (absX, absY) = denormalizeCoordinates(normX, normY, screenWidth, screenHeight)
                        AutoAgentService.instance?.click(absX, absY)
                        delay(TimingConfig.Action.TAP_DELAY)
                        "Tapped ($normX, $normY)"
                    } else "Failed to parse Tap coords"
                }
                
                "swipe", "scroll" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    val startM = Pattern.compile("start\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+)\\s*,\\s*(\\d+))?\\s*\\]").matcher(trimmedAction)
                    val endM = Pattern.compile("end\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+)\\s*,\\s*(\\d+))?\\s*\\]").matcher(trimmedAction)
                    
                    if (startM.find() && endM.find()) {
                        val (sX, sY) = resolveCenter(startM)
                        val (eX, eY) = resolveCenter(endM)
                        val (absSX, absSY) = denormalizeCoordinates(sX, sY, screenWidth, screenHeight)
                        val (absEX, absEY) = denormalizeCoordinates(eX, eY, screenWidth, screenHeight)
                        AutoAgentService.instance?.scroll(absSX, absSY, absEX, absEY)
                        delay(TimingConfig.Action.SWIPE_DELAY)
                        "Swiped ($sX,$sY)->($eX,$eY)"
                    } else "Failed to parse Swipe coords"
                }
                
                "type", "input" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    val m = Pattern.compile("text\\s*=\\s*[\"'](.*?)[\"']").matcher(trimmedAction)
                    if (m.find()) {
                        var text = m.group(1)
                        // 安全过滤: 移除可能夹带在 text 参数中的 <think> 标签内容
                        text = text.replace(Regex("(?s)<think>.*?</think>"), "")
                                   .replace(Regex("(?s)\\{think\\}.*?\\{/think\\}"), "")
                                   .trim()
                        
                        var success = com.autoglm.autoagent.service.AgentInputMethodService.instance?.inputText(text) ?: false
                        if (!success) success = AutoAgentService.instance?.inputText(text) ?: false
                        delay(TimingConfig.Action.TYPE_DELAY)
                        if (success) "Typed: $text" else "Type Failed"
                    } else "Failed to parse text"
                }
                
                "launch", "open" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    val m = Pattern.compile("app\\s*=\\s*[\"'](.*?)[\"']").matcher(trimmedAction)
                    if (m.find()) {
                        val appName = m.group(1)
                        val success = appManager.launchApp(appName)
                        delay(TimingConfig.Action.LAUNCH_DELAY)
                        if (success) "Launched $appName" else "Launch Failed: $appName"
                    } else "Failed to parse app name"
                }
                
                "home" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    AutoAgentService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                    delay(TimingConfig.Action.HOME_DELAY)
                    "Home Pressed"
                }
                
                "back" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    AutoAgentService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(TimingConfig.Action.BACK_DELAY)
                    "Back Pressed"
                }

                "long press", "long_press", "double tap", "double_tap" -> {
                    val m = Pattern.compile("element\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*(\\d+)(?:\\s*,\\s*(\\d+)\\s*,\\s*(\\d+))?\\s*\\]").matcher(trimmedAction)
                    if (m.find()) {
                        val (normX, normY) = resolveCenter(m)
                        val (absX, absY) = denormalizeCoordinates(normX, normY, screenWidth, screenHeight)
                        if (actionName.contains("long")) {
                            AutoAgentService.instance?.longPress(absX, absY)
                        } else {
                            AutoAgentService.instance?.doubleTap(absX, absY)
                        }
                        delay(TimingConfig.Action.TAP_DELAY)
                        "Executed Press/Tap at ($normX, $normY)"
                    } else "Failed to parse coords"
                }

                "wait" -> {
                    val m = Pattern.compile("duration\\s*=\\s*[\"'](\\d+).*?[\"']").matcher(trimmedAction)
                    val seconds = if (m.find()) m.group(1).toLong() else 3L
                    delay(seconds * 1000)
                    "Waited ${seconds}s"
                }

                "getui", "get_ui" -> {
                    val uiTree = AutoAgentService.instance?.dumpOptimizedUiTree() ?: "{ \"error\": \"Service not available\" }"
                    Log.d("Agent", "UI Tree requested, size: ${uiTree.length} bytes")
                    uiTree
                }

                "interact", "take_over" -> {
                    pauseAgent()
                    "Paused for User"
                }
                
                "note" -> {
                    resetDuplicateTracker()
                    isDeadlockState = false
                    val m = Pattern.compile("message\\s*=\\s*[\"'](.*?)[\"']").matcher(trimmedAction)
                    if (m.find()) {
                        val content = m.group(1)
                        taskNotes.add(content)
                        Log.d("Agent", "Note recorded: $content")
                        "Note added: $content"
                    } else "Failed to parse Note"
                }

                "call_api" -> {
                    "Command Recognized"
                }

                else -> "Unknown Action: $trimmedAction (Parsed Name: '$actionName')"
            }
        } catch (e: Exception) {
            Log.e("Agent", "Action failed", e)
            "Error: ${e.message}"
        }
    }



    // 辅助工具: 解析 [x,y] 或 [l,t,r,b] 并返回中心点
    private fun resolveCenter(m: java.util.regex.Matcher): Pair<Int, Int> {
        return if (m.group(3) != null && m.group(4) != null) {
            val l = m.group(1).toInt()
            val t = m.group(2).toInt()
            val r = m.group(3).toInt()
            val b = m.group(4).toInt()
            Pair((l + r) / 2, (t + b) / 2)
        } else {
            Pair(m.group(1).toInt(), m.group(2).toInt())
        }
    }

    private fun handleDeadlock(normX: Int, normY: Int): String {
        val uiTree = AutoAgentService.instance?.dumpOptimizedUiTree() ?: "{ \"ui\": [] }"
        val isUiTreeEmpty = uiTree.contains("\"ui\": []")
        
        Log.w("Agent", "Deadlock triggered at ($normX, $normY)")
        return """
            {
              "event": "ERROR_STUCK_DETECTED",
              "reason": "坐标 ($normX, $normY) 连续3次点击无响应。",
              "forbidden_zone": {
                "target": [$normX, $normY],
                "rule": "STRICT_FORBIDDEN: 禁止在此阻塞状态下再次点击原处。"
              },
              "suggested_recovery": [
                ${if (!isUiTreeEmpty) "\"由于多次尝试无效，已断开视觉上传。请依据下方 current_ui_context 中的 UI 树重新寻找路径以打破死循环\"," else "\"由于应用无结构，请依据截图重新尝试点击其他位置或执行滑动/返回\""},
                "尝试 Swipe 滑动、Back 返回以改变当前界面状态"
              ],
              "current_ui_context": $uiTree
            }
        """.trimIndent()
    }
    
    // Denormalize: [0, 1000] -> [0, ScreenPixels]
    // 修复: 匹配 Python 版本的整数截断行为
    // Python: int(element[0] / 1000 * screen_width)
    private fun denormalizeCoordinates(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        val absX = ((x * screenWidth) / 1000).toFloat()
        val absY = ((y * screenHeight) / 1000).toFloat()
        return Pair(absX, absY)
    }

    fun stopAgent() {
        // Immediately cancel running task
        currentTaskJob?.cancel()
        currentTaskJob = null
        
        // Stop listening if active
        voiceManager.cancelListening()
        
        // Set state to idle
        _agentState.value = AgentState.Idle
        
        android.util.Log.d("Agent", "Task stopped, floating window remains active")
    }
    
    private fun checkDuplicate(type: String, x: Int, y: Int): Boolean {
        if (type == lastActionType && 
            kotlin.math.abs(x - lastTapX) < 20 && 
            kotlin.math.abs(y - lastTapY) < 20) {
            sameTapCount++
        } else {
            sameTapCount = 1
            lastActionType = type
            lastTapX = x
            lastTapY = y
        }
        return sameTapCount > 2
    }

    private fun resetDuplicateTracker() {
        lastActionType = ""
        lastTapX = 0
        lastTapY = 0
        sameTapCount = 0
    }

    fun addMessage(role: String, content: String) {
        val current = _chatMessages.value.toMutableList()
        current.add(ChatMessage(role, content))
        _chatMessages.value = current
    }

    private fun addUiMessage(role: String, content: String) {
        addMessage(role, content)
    }
    
    /**
     * 从最后一条 user 消息中移除图片,仅保留文本
     * 匹配 Python: self._context[-1] = MessageBuilder.remove_images_from_message(self._context[-1])
     */
    private fun removeImageFromLastUserMessage() {
        // 找到最后一条 user 消息
        val lastUserIndex = messages.indexOfLast { it.role == "user" }
        if (lastUserIndex == -1) return
        
        val lastUserMsg = messages[lastUserIndex]
        
        // 如果是 List<ContentPart>,移除图片部分
        if (lastUserMsg.content is List<*>) {
            val contentParts = lastUserMsg.content as? List<ContentPart> ?: return
            val textOnly = contentParts.filter { it.type == "text" }
            
            // 如果只剩文本,转换为纯文本消息
            if (textOnly.isNotEmpty()) {
                val textContent = textOnly.joinToString("\n") { it.text ?: "" }
                messages[lastUserIndex] = ChatMessage("user", textContent)
                Log.d("Agent", "✅ 已移除最后一条user消息中的图片,仅保留文本")
            }
        }
    }
}
