# AutoDroid

<div align="center">

**Open-AutoGLM 的 Android 原生客户端**

让 AI 智能助理直接在手机上运行,无需电脑辅助

</div>

## 项目简介

AutoDroid 是 [Open-AutoGLM](https://github.com/zai-org/Open-AutoGLM) 项目的 Android 原生实现。与原项目通过 ADB 从电脑控制手机不同,AutoDroid 是一个完整的 Android 应用,可以直接安装在手机上独立运行。

**核心特点:**
- 🤖 **原生 Android 应用** - 无需电脑,直接在手机上运行
- 🎤 **语音控制** - 支持语音输入任务指令
- 📱 **自动化操作** - 通过无障碍服务控制应用
- 🔒 **安全存储** - API 配置加密保存
- 🌐 **自定义 API** - 支持自部署或第三方 AI 服务

## 功能演示

用户只需:
1. 启用无障碍服务和输入法
2. 配置 AI 模型服务地址
3. 语音或文本输入任务 (如 "打开抖音搜索舞蹈视频")
4. AI 自动完成整个操作流程

## 技术架构

### 技术栈

- **UI**: Jetpack Compose + Material 3
- **架构**: MVVM + Repository Pattern
- **依赖注入**: Hilt (Dagger)
- **网络**: Retrofit + OkHttp
- **存储**: EncryptedSharedPreferences
- **语音识别**: Sherpa-ONNX (离线高精度识别)
  - 模型: Paraformer 2024-03-09
  - 支持中英双语,方言识别
  - 准确率 > 90%
- **Kotlin**: 1.9.x
- **Min SDK**: 26 (Android 7.0+)
- **Target SDK**: 34

### 核心组件

#### 1. AutoAgentService (无障碍服务)
位置: `service/AutoAgentService.kt`

实现自动化操作的核心服务:
- 屏幕截图和 UI 树分析
- 点击、长按、滑动等手势操作
- 文本输入通过自定义输入法实现

```kotlin
// 主要功能
- click(x, y)          // 点击
- longPress(x, y)      // 长按
- scroll(x1, y1, x2, y2) // 滑动
- takeScreenshotAsync() // 截图
```

#### 2. AgentInputMethodService (输入法服务)
位置: `service/AgentInputMethodService.kt`

提供文本输入能力:
- 通过 InputConnection 直接输入文本
- 维护全局单例供无障碍服务调用

#### 3. AIClient (AI 通信)
位置: `data/api/AIClient.kt`

与 AI 模型服务通信:
- 兼容 OpenAI Chat Completions API 格式
- 支持多模态输入(文本 + 图片)
- 可配置 Base URL、API Key、Model

```kotlin
// API 参数
temperature: 0.0        // 严格模式
top_p: 0.85
frequency_penalty: 0.2
max_tokens: 3000
```

#### 4. AgentRepository (业务逻辑)
位置: `data/AgentRepository.kt`

协调任务执行流程:
- 管理对话历史
- 循环执行: 截图 → AI 推理 → 动作执行
- 状态和日志管理

#### 5. UI 界面

- **HomeScreen**: 主界面,包含语音/文本输入和日志显示
- **SettingsScreen**: API 配置和权限管理

## 项目结构

```
AutoDroid/
├── app/
│   ├── src/main/
│   │   ├── java/com/autoglm/autoagent/
│   │   │   ├── MainActivity.kt           # 主 Activity
│   │   │   ├── data/
│   │   │   │   ├── api/AIClient.kt       # AI 客户端
│   │   │   │   ├── AgentRepository.kt    # 业务逻辑
│   │   │   │   └── SettingsRepository.kt # 配置管理
│   │   │   ├── service/
│   │   │   │   ├── AutoAgentService.kt   # 无障碍服务
│   │   │   │   └── AgentInputMethodService.kt # 输入法
│   │   │   └── ui/
│   │   │       ├── HomeScreen.kt         # 主界面
│   │   │       └── SettingsScreen.kt     # 设置界面
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
└── build.gradle.kts
```

## 快速开始

### 环境要求

- Android Studio Arctic Fox+
- JDK 8+
- Android SDK API 26+
- Gradle 8.x

### 编译安装

```bash
# 1. 克隆项目 (如果从独立仓库)
git clone <your-repo-url>
cd AutoDroid

# 2. 构建 APK
./gradlew assembleDebug

# 3. 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 配置使用

#### 步骤 1: 启用权限

安装后首次运行,需要授予以下权限:

1. **无障碍服务**
   - 打开 AutoDroid,点击"启用无障碍服务"
   - 在系统设置中找到并启用 "AutoAgent Service"

2. **输入法**
   - 进入 `设置 > 语言和输入法 > 虚拟键盘`
   - 启用 "AutoAgent Keyboard"

3. **其他权限**
   - 录音权限 (语音输入)
   - 网络权限 (AI 通信)

#### 步骤 2: 配置 AI 服务

在设置界面配置 AI 模型服务:

**选项 A: 使用第三方服务**

智谱 BigModel:
```
Base URL: https://open.bigmodel.cn/api/paas/v4
Model: autoglm-phone
API Key: 在智谱平台申请
```

ModelScope:
```
Base URL: https://api-inference.modelscope.cn/v1
Model: ZhipuAI/AutoGLM-Phone-9B
API Key: 在 ModelScope 申请
```

**选项 B: 自部署服务**

使用 vLLM 或 SGLang 部署 AutoGLM-Phone-9B 模型:
```
Base URL: http://你的服务器IP:8000/v1
Model: autoglm-phone-9b
API Key: (可选)
```

详见 [Open-AutoGLM 部署文档](https://github.com/zai-org/Open-AutoGLM#%E5%90%AF%E5%8A%A8%E6%A8%A1%E5%9E%8B%E6%9C%8D%E5%8A%A1)

#### 步骤 3: 使用应用

**语音模式:**
1. 点击麦克风图标
2. 说出任务 (如 "打开抖音刷视频")
3. 点击停止,AI 开始执行

**文本模式:**
1. 点击键盘图标
2. 输入任务描述
3. 点击发送

## 与原项目的区别

| 特性 | Open-AutoGLM (原项目) | AutoDroid (本项目) |
|------|----------------------|-------------------|
| **运行方式** | 电脑通过 ADB 控制手机 | 直接在手机上运行 |
| **输入方式** | 命令行或 Python API | 语音 / 文本界面 |
| **部署复杂度** | 需要 Python 环境和 ADB | 仅需安装 APK |
| **截图方式** | ADB screencap 命令 | 无障碍服务 API |
| **操作执行** | ADB input 命令 | 无障碍手势 + 自定义输入法 |
| **适用场景** | 开发测试、批量任务 | 个人日常使用 |

## 已知限制

1. **无障碍服务限制** - 部分系统应用或安全应用可能限制无障碍访问
2. **截图限制** - 银行、支付类应用可能禁止截图
3. **APK 体积** - 包含离线语音模型,APK 约 300 MB
4. **系统版本** - 最低需要 Android 7.0+

## 开发计划

- [x] ~~支持在线语音识别~~ - 已升级到 Sherpa-ONNX
- [ ] 任务历史记录
- [ ] 自定义快捷指令
- [ ] UI 优化和动画
- [ ] 多语言支持
- [ ] 性能和稳定性改进

## 相关资源

- [Open-AutoGLM 原项目](https://github.com/zai-org/Open-AutoGLM)
- [AutoGLM-Phone-9B 模型](https://huggingface.co/zai-org/AutoGLM-Phone-9B)
- [智谱 AI](https://bigmodel.cn/)

## 贡献

欢迎提交 Issue 和 Pull Request!

## 许可证

本项目基于 **Apache License 2.0** 开源。

### 第三方组件

本项目使用了以下开源组件:
- **Sherpa-ONNX** (Apache 2.0) - 离线语音识别引擎
- **Paraformer 模型** (Apache 2.0) - 中文语音识别模型

详见 [第三方组件许可](./THIRD_PARTY_LICENSES.md)

⚠️ **免责声明**: 本项目仅供研究和学习使用,严禁用于任何非法用途。

---

**开发者**: Open-AutoGLM Community  
**最后更新**: 2026-01-02
