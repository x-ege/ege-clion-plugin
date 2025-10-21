package org.xege

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HelpAction : AnAction() {
    private val logger = Logger.getInstance(HelpAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // 获取插件真实版本号（找不到时使用默认值）
        val pluginVersion = PluginManagerCore.getPlugin(PluginId.getId("org.xege.clion"))?.version ?: "1.0"

        // 显示插件信息对话框
        Messages.showInfoMessage(
            project,
            """
            • 版本: $pluginVersion
            • 作者: wysaid
            • 邮箱: this@xege.org
            • 源码地址: https://github.com/x-ege/ege-jetbrains-plugin
            
            功能说明:
            • 提供 EGE (Easy Graphics Engine) 项目模板
            • 简化 C++ 图形编程项目配置
            """.trimIndent(),
            "关于 Xege Plugin"
        )
        
        // 在 IDE 日志中输出
        logger.info("Xege Plugin information displayed")
        
    }

    private fun showNotification(project: Project?, title: String, content: String) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Xege Plugin Notifications")
                .createNotification(title, content, NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            // 如果通知组不存在，使用默认方式
            logger.info("Notification: $title - $content")
        }
    }
}
