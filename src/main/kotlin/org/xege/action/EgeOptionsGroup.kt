package org.xege.action

import com.intellij.openapi.actionSystem.DefaultActionGroup
import org.xege.XegeBundle

/**
 * EGE 选项菜单组
 * 支持国际化的菜单组
 */
class EgeOptionsGroup : DefaultActionGroup() {
    
    init {
        // 设置国际化的菜单文本
        templatePresentation.text = XegeBundle.message("menu.ege.options")
        templatePresentation.description = XegeBundle.message("menu.ege.options.description")
    }
}
