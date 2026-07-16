package org.xege

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType

class HelpAction : AnAction() {
    private val logger = Logger.getInstance(HelpAction::class.java)
    
    init {
        // 设置国际化的菜单文本
        templatePresentation.text = XegeBundle.message("menu.about")
        templatePresentation.description = XegeBundle.message("menu.about.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project

        // 版本号由 Gradle 在构建时注入资源包
        val pluginVersion = XegeBundle.message("plugin.version")

        // 显示插件信息对话框
        Messages.showInfoMessage(
            project,
            XegeBundle.message("help.dialog.message", pluginVersion),
            XegeBundle.message("help.dialog.title")
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
