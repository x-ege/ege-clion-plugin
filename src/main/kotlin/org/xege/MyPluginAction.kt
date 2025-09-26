package org.xege

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MyPluginAction : AnAction() {
    private val logger = Logger.getInstance(MyPluginAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        // 在 IDE 日志中输出
        logger.info("Xege Plugin Action triggered at $currentTime")

        // 在控制台输出（可在 IDE 控制台查看）
        println("=== Xege Plugin Action Executed ===")
        println("Time: $currentTime")
        println("Project: ${project?.name ?: "No project"}")
        println("===================================")

        // 显示通知（右下角通知栏）
        showNotification(project, "Xege Plugin 已激活！", "插件在 $currentTime 成功执行")

        // 显示对话框
        Messages.showInfoMessage(
            project,
            "🎉 Xege Plugin 成功运行！\n\n" +
            "时间: $currentTime\n" +
            "项目: ${project?.name ?: "无项目"}\n\n" +
            "插件功能已激活，可以在以下位置查看输出：\n" +
            "• IDE 控制台 (View → Tool Windows → Console)\n" +
            "• IDE 日志 (Help → Show Log in Finder)\n" +
            "• 右下角通知栏",
            "Xege Plugin - 激活成功"
        )
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
