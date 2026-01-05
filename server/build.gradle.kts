plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

// 配置：自动将编译后的 JAR 转换为 DEX 并复制到 app assets
tasks.register("buildServerDex") {
    group = "build"
    description = "Build server JAR, convert to DEX, and copy to app/assets"
    
    dependsOn("build")
    
    doLast {
        val jarTask = tasks.named("jar").get() as Jar
        val jarFile = jarTask.archiveFile.get().asFile
        
        if (!jarFile.exists()) {
            throw GradleException("Server JAR not found: ${jarFile.absolutePath}")
        }
        
        println("✅ Found server JAR: ${jarFile.name}")
        
        println("✅ Found server JAR: ${jarFile.name}")
        
        // 查找 Android SDK (复用 dependencies 中的逻辑)
        val localProperties = file("${project.rootDir}/local.properties")
        val sdkDir = if (localProperties.exists()) {
            localProperties.readLines()
                .firstOrNull { it.trim().startsWith("sdk.dir") }
                ?.substringAfter("=")
                ?.trim()
                ?.replace("\\:", ":") 
                ?.replace("\\\\", "\\")
        } else {
            null
        }
        
        val androidHome = sdkDir 
            ?: System.getenv("ANDROID_HOME") 
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "E:/Temp/Android_SDK"
            
        // Use File to ensure absolute path
        val buildToolsDir = File(androidHome, "build-tools")
        
        if (!buildToolsDir.exists()) {
             throw GradleException("Android SDK build-tools not found at: ${buildToolsDir.absolutePath}")
        }
        
        val d8Tool = if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            buildToolsDir.listFiles()
                ?.sortedDescending()
                ?.firstOrNull()
                ?.let { File(it, "d8.bat") }
        } else {
            buildToolsDir.listFiles()
                ?.sortedDescending()
                ?.firstOrNull()
                ?.let { File(it, "d8") }
        }
        
        if (d8Tool == null || !d8Tool.exists()) {
            throw GradleException("d8 tool not found in Android SDK build-tools")
        }
        
        println("✅ Using d8: ${d8Tool.absolutePath}")
        
        // 创建临时目录
        val tempDir = file("$buildDir/dex-temp")
        tempDir.mkdirs()
        
        val dexFile = File(tempDir, "classes.dex")
        
        // 执行 d8 转换
        println("🔄 Converting JAR to DEX using JAVA_HOME: ${System.getProperty("java.home")}")
        val processBuilder = ProcessBuilder(
            d8Tool.absolutePath,
            "--output", tempDir.absolutePath,
            "--min-api", "26",  // Android 8.0
            jarFile.absolutePath
        )
        
        // Explicitly set JAVA_HOME for d8
        processBuilder.environment()["JAVA_HOME"] = System.getProperty("java.home")
        
        val d8Process = processBuilder.redirectErrorStream(true).start()
        
        val d8Output = d8Process.inputStream.bufferedReader().readText()
        val exitCode = d8Process.waitFor()
        
        if (exitCode != 0) {
            println("❌ d8 output:\n$d8Output")
            throw GradleException("d8 conversion failed with exit code $exitCode")
        }
        
        if (!dexFile.exists()) {
            throw GradleException("DEX file not generated: ${dexFile.absolutePath}")
        }
        
        println("✅ DEX generated: ${dexFile.name} (${dexFile.length()} bytes)")
        
        // 复制到 app/src/main/assets
        val assetsDir = file("${project.rootDir}/app/src/main/assets")
        assetsDir.mkdirs()
        
        val targetDex = File(assetsDir, "server.dex")
        dexFile.copyTo(targetDex, overwrite = true)
        
        println("✅ Copied to: ${targetDex.absolutePath}")
        println("🎉 Server DEX build complete!")
    }
}

// 让 app 模块的 build 依赖于此任务
// 注意:这个依赖需要在 app/build.gradle.kts 中配置，而不是在这里

dependencies {
    // Android SDK (compileOnly - 运行时通过 app_process 提供)
    compileOnly("androidx.annotation:annotation:1.7.0")
    
    // 需要 Android SDK jar 才能编译
    // 从 Android Studio 的 local.properties 获取 SDK 路径
    val localProperties = file("${project.rootDir}/local.properties")
    val sdkDir = if (localProperties.exists()) {
        localProperties.readLines()
            .firstOrNull { it.trim().startsWith("sdk.dir") }
            ?.substringAfter("=")
            ?.trim()
            ?.replace("\\:", ":") // Fix escaped colon on Windows
            ?.replace("\\\\", "\\") // Fix escaped backslashes
    } else {
        null
    }
    
    val androidHome = sdkDir 
        ?: System.getenv("ANDROID_HOME") 
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: "E:/Temp/Android_SDK"  // Hardcoded fallback with safe forward slashes
    
    // Clean up the path: remove potential escape characters from Properties format if manual parsing failed to do so perfectly
    val cleanHome = androidHome.replace("\\:", ":").replace("\\\\", "\\")
    
    // Use File directly to ensure absolute path
    val androidJar = File(cleanHome, "platforms/android-34/android.jar")
    
    if (!androidJar.exists()) {
        println("⚠️ Android SDK jar not found at: ${androidJar.absolutePath}")
        println("ℹ️ Trying hardcoded fallback...")
        // Final desperate fallback
        val fallbackJar = File("E:/Temp/Android_SDK/platforms/android-34/android.jar")
        if (fallbackJar.exists()) {
             compileOnly(files(fallbackJar))
             println("✅ Using Fallback Android SDK: ${fallbackJar.absolutePath}")
             return@dependencies
        }
        throw GradleException("Android SDK not found. Checked: ${androidJar.absolutePath}")
    }
    
    println("✅ Using Android SDK: ${androidJar.absolutePath}")
    compileOnly(files(androidJar))
}
