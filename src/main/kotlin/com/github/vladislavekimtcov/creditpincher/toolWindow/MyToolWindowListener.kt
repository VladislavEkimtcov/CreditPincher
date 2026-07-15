package com.github.vladislavekimtcov.creditpincher.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener

class MyToolWindowListener(private val project: Project) : ToolWindowManagerListener {
    private var wasVisible = false

    override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val toolWindow = toolWindowManager.getToolWindow("AIUse") ?: return
        val isVisible = toolWindow.isVisible
        if (wasVisible && !isVisible) {
            val content = toolWindow.contentManager.contents.firstOrNull() ?: return
            val panel = content.component as? CreditUsagePanel ?: return
            ApplicationManager.getApplication().invokeLater {
                panel.resetToCurrentMonth()
            }
        }
        wasVisible = isVisible
    }
}
