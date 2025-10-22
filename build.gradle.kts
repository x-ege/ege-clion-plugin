plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "2.1.21"
}

group = "org.xege.creator"
version = project.findProperty("pluginVersion") as String? ?: "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

intellij {
    version.set("2023.3")
    type.set("CL") // CLion
    plugins.set(listOf("com.intellij.clion"))
}

tasks.patchPluginXml {
    version.set(project.version.toString())
    sinceBuild.set("233")
    untilBuild.set("252.*")  // 2025.2 - 当前已知的最新主分支
    changeNotes.set("""
        <h3>Version ${project.version}</h3>
        <ul>
            <li>Support for the <b>easy graphics engine</b> project wizard</li>
            <li>CLion-specific integration</li>
            <li>Cross-platform support (Windows, macOS, Linux)</li>
            <li>Supports CLion 2023.3 and above</li>
            <li>Updated deprecated APIs</li>
        </ul>
    """.trimIndent())
}

tasks {
    buildPlugin {
        dependsOn("test")
    }
    runIde {
        // 使用默认的 IDEA 实例进行调试
    }
    buildSearchableOptions {
        enabled = false
    }
    
    // 将 assets 目录复制到 resources 中
    processResources {
        from("assets") {
            into("assets")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

// ========================================
// 版本管理任务
// ========================================

/**
 * 任务1: 检查 CLion 最新版本
 * 如果 untilBuild 不是最新版本，则报错
 */
tasks.register("checkClionVersion") {
    group = "verification"
    description = "检查 untilBuild 是否匹配 CLion 最新发布版本"
    
    doLast {
        val patchTask = tasks.named("patchPluginXml").get() as org.jetbrains.intellij.tasks.PatchPluginXmlTask
        val currentUntilBuild = patchTask.untilBuild.get()
        
        VersionChecker.checkVersion(currentUntilBuild, throwOnMismatch = true)
    }
}

/**
 * 任务2: 自动更新 untilBuild
 * 将 untilBuild 更新为 CLion 最新发布的主版本号
 */
tasks.register("updateUntilBuild") {
    group = "version"
    description = "自动将 untilBuild 更新为 CLion 最新发布版本"
    
    doLast {
        val latestBuild = VersionChecker.getLatestClionBuildNumber()
        val buildFile = file("build.gradle.kts")
        var content = buildFile.readText()
        
        // 使用正则表达式替换 untilBuild
        val regex = """untilBuild\.set\("(\d+)\.\*"\)""".toRegex()
        val match = regex.find(content)
        
        if (match != null) {
            val currentBuild = match.groupValues[1]
            
            if (currentBuild == latestBuild) {
                println("✓ untilBuild 已是最新版本 ($latestBuild.*)")
            } else {
                content = content.replace(
                    """untilBuild.set("$currentBuild.*")""",
                    """untilBuild.set("$latestBuild.*")"""
                )
                
                buildFile.writeText(content)
                
                println("""
                    ✓ 已更新 untilBuild！
                    旧版本: $currentBuild.*
                    新版本: $latestBuild.*
                    
                    请检查更改并提交到版本控制系统
                """.trimIndent())
            }
        } else {
            throw GradleException("无法在 build.gradle.kts 中找到 untilBuild 配置")
        }
    }
}