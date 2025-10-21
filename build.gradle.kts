plugins {
    id("org.jetbrains.intellij") version "1.17.4"
    kotlin("jvm") version "2.1.21"
}

group = "org.xege.clion"
version = "1.0-SNAPSHOT"

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
    changeNotes.set("Initial version.")
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