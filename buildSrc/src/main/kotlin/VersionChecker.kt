import org.gradle.api.GradleException
import java.net.URL
import com.google.gson.Gson

data class JetBrainsRelease(
    val version: String,
    val build: String,
    val type: String
)

data class JetBrainsProductResponse(
    val CL: List<JetBrainsRelease>
)

object VersionChecker {
    private const val JETBRAINS_API = "https://data.services.jetbrains.com/products/releases?code=CL&latest=true&type=release"
    
    /**
     * 获取 CLion 最新的主版本号 (例如 252)
     */
    fun getLatestClionBuildNumber(): String {
        return try {
            val json = URL(JETBRAINS_API).readText()
            val gson = Gson()
            val response = gson.fromJson(json, JetBrainsProductResponse::class.java)
            
            val latestRelease = response.CL.firstOrNull()
                ?: throw GradleException("无法从 JetBrains API 获取 CLion 版本信息")
            
            // 提取主版本号 (例如从 "252.100.200" 提取 "252")
            val buildNumber = latestRelease.build.split(".").firstOrNull()
                ?: throw GradleException("无法解析 CLion build 号: ${latestRelease.build}")
            
            println("✓ CLion 最新版本: ${latestRelease.version} (build: ${latestRelease.build})")
            println("✓ 主版本号: $buildNumber")
            
            buildNumber
        } catch (e: Exception) {
            throw GradleException("获取 CLion 最新版本失败: ${e.message}", e)
        }
    }
    
    /**
     * 从 untilBuild 字符串中提取主版本号 (例如从 "252.*" 提取 "252")
     */
    fun extractBuildNumber(untilBuild: String): String {
        return untilBuild.replace(".*", "").trim()
    }
    
    /**
     * 检查 untilBuild 是否匹配最新版本
     */
    fun checkVersion(currentUntilBuild: String, throwOnMismatch: Boolean = true): Boolean {
        val latestBuild = getLatestClionBuildNumber()
        val currentBuild = extractBuildNumber(currentUntilBuild)
        
        println("\n=== 版本检查 ===")
        println("当前 untilBuild: $currentUntilBuild (主版本: $currentBuild)")
        println("CLion 最新主版本: $latestBuild")
        
        val isUpToDate = currentBuild == latestBuild
        
        if (isUpToDate) {
            println("✓ 版本检查通过！untilBuild 已是最新版本")
        } else {
            val message = """
                ❌ 版本检查失败！
                当前 untilBuild: $currentUntilBuild
                CLion 最新版本: $latestBuild
                
                请运行以下命令更新版本：
                ./gradlew updateUntilBuild
            """.trimIndent()
            
            if (throwOnMismatch) {
                throw GradleException(message)
            } else {
                println(message)
            }
        }
        
        return isUpToDate
    }
}
