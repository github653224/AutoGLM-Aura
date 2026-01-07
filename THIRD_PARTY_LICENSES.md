# 第三方组件许可

AutoGLM-Aura 使用了以下第三方开源组件:

## Sherpa-ONNX

**项目**: https://github.com/k2-fsa/sherpa-onnx  
**许可**: Apache License 2.0  
**用途**: 离线语音识别  
**版本**: 1.12.20

```
Copyright 2022-2024 K2-FSA

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

## Paraformer 模型

**来源**: https://huggingface.co/csukuangfj/sherpa-onnx-paraformer-zh-small-2024-03-09  
**原始模型**: ModelScope/达摩院  
**许可**: Apache License 2.0  
**用途**: 中文语音识别模型

## 其他依赖

详见 `app/build.gradle.kts` 中的依赖列表:
- Jetpack Compose (Apache 2.0)
- Hilt (Apache 2.0)
- Retrofit (Apache 2.0)
- OkHttp (Apache 2.0)

---

所有第三方组件均遵循其各自的开源许可协议。
