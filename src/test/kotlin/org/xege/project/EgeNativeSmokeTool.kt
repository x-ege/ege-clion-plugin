package org.xege.project

import java.io.File

/** CI loads EgeResourceCopier and resources from the delivered plugin JAR. */
object EgeNativeSmokeTool {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size in 1..2) { "Usage: EgeNativeSmokeTool <project-directory> [demo.cpp]" }
        EgeResourceCopier { path -> EgeResourceCopier::class.java.getResourceAsStream("/assets/$path") }
            .generate(File(args[0]), args.getOrNull(1))
        println("Generated from packaged EgeResourceCopier: ${File(args[0]).canonicalPath}")
    }
}
