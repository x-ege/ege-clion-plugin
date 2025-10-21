package org.xege

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.*

private const val BUNDLE = "messages.XegeBundle"

/**
 * 国际化资源管理器
 * 
 * 用于加载和获取国际化文本资源
 * 根据系统语言环境自动选择合适的语言
 * 
 * 调试选项：
 * 修改 DEBUG_LOCALE 来测试不同语言
 * - null: 使用系统语言（默认）
 * - Locale.ENGLISH: 强制使用英文
 * - Locale.SIMPLIFIED_CHINESE: 强制使用简体中文
 */
object XegeBundle : DynamicBundle(BUNDLE) {
    /**
     * 调试用语言设置
     * 
     * 使用方法：
     * 1. 测试英文：设置为 Locale.ENGLISH
     * 2. 测试中文：设置为 Locale.SIMPLIFIED_CHINESE
     * 3. 使用系统语言：设置为 null
     * 
     * ⚠️ 注意：修改后需要重启 IDE 才能生效
     */
    private val DEBUG_LOCALE: Locale? = null  // 👈 修改这里来切换语言
    // private val DEBUG_LOCALE: Locale? = Locale.ENGLISH  // 强制英文
    // private val DEBUG_LOCALE: Locale? = Locale.SIMPLIFIED_CHINESE  // 强制中文
    
    /**
     * 获取国际化消息
     * @param key 资源键
     * @param params 可选参数，用于替换消息中的占位符
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return if (DEBUG_LOCALE != null) {
            // 调试模式：使用指定的语言
            val bundle = ResourceBundle.getBundle(BUNDLE, DEBUG_LOCALE)
            val pattern = bundle.getString(key)
            if (params.isEmpty()) {
                pattern
            } else {
                java.text.MessageFormat.format(pattern, *params)
            }
        } else {
            // 正常模式：使用系统语言
            getMessage(key, *params)
        }
    }
}
