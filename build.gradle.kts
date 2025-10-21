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
    changeNotes.set("""
        <h3>Version ${project.version}</h3>
        <ul>
            <li>EGE 图形库项目向导支持</li>
            <li>CLion 专属集成</li>
            <li>多平台支持 (Windows, macOS, Linux)</li>
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