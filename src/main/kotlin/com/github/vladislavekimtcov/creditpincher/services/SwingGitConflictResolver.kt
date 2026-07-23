package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import java.nio.file.Path

class SwingGitConflictResolver(private val project: Project) : GitConflictResolver {

    override fun resolveConflicts(workingDirectory: Path, conflictedFiles: List<String>): Boolean {
        var resolved = false
        ApplicationManager.getApplication().invokeAndWait {
            val dialog = GitConflictResolverDialog(project, workingDirectory, conflictedFiles)
            if (dialog.showAndGet()) {
                dialog.applyResolutions()
                resolved = true
            }
        }
        return resolved
    }
}
