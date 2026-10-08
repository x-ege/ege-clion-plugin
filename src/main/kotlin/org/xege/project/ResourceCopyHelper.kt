package org.xege.project

import com.intellij.openapi.progress.ProgressIndicator
import java.io.File

/** All platforms build the same pinned sources with the target platform's toolchain. */
object EgeBuildMode {
    fun usesSource(osName: String = System.getProperty("os.name")): Boolean {
        require(osName.startsWith("Windows") || osName.startsWith("Mac") ||
            osName.startsWith("Linux")) { "Unsupported EGE platform: $osName" }
        return true
    }
}

object ResourceCopyHelper {
    fun generateProject(targetDir: File, demoFileName: String? = null, indicator: ProgressIndicator? = null) {
        EgeBuildMode.usesSource()
        EgeResourceCopier { path -> javaClass.getResourceAsStream("/assets/$path") }
            .generate(targetDir, demoFileName) { name ->
                indicator?.checkCanceled()
                indicator?.text2 = name
            }
    }
}
