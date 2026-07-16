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
    // 1.x 插件默认会自动补上与开发 SDK 相同分支的 until-build。
    // 关闭自动补丁，使用 plugin.xml 中仅含 since-build 的开放式兼容范围。
    updateSinceUntilBuild.set(false)
}

tasks.patchPluginXml {
    version.set(project.version.toString())
    // 保持开放式兼容范围，避免新 CLion 版本仅因 untilBuild 上限而拒绝安装。
    // 新版本兼容性由 Plugin Verifier 和 Marketplace 审核持续兜底。
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
    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
    runIde {
        // 使用默认的 IDEA 实例进行调试
    }
    buildSearchableOptions {
        enabled = false
    }
    
    // 将 assets 目录复制到 resources 中
    processResources {
        filesMatching("messages/XegeBundle.properties") {
            expand("pluginVersion" to project.version)
        }
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
 * 确保插件保持开放式兼容范围。
 * JetBrains 推荐不设置 untilBuild，以免每个 IDE 大版本都必须重新发布插件。
 */
tasks.register("checkClionVersion") {
    group = "verification"
    description = "检查插件是否未设置 untilBuild 上限"
    dependsOn("patchPluginXml")

    doLast {
        val patchedPluginXml = layout.buildDirectory
            .file("patchedPluginXmlFiles/plugin.xml")
            .get()
            .asFile
        val content = patchedPluginXml.readText()
        val untilBuild = Regex("""until-build="([^"]+)"""").find(content)?.groupValues?.get(1)

        if (untilBuild != null) {
            throw GradleException(
                "生成的 plugin.xml 中仍存在 until-build=$untilBuild。请保持开放式兼容范围。"
            )
        }

        println("✓ 未设置 untilBuild；插件不会因 IDE 主版本升级而被元数据阻止安装")
    }
}
