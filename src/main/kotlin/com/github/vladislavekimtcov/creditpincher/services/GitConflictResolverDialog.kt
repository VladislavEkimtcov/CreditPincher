package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.*

class GitConflictResolverDialog(
    private val project: Project,
    private val workingDirectory: Path,
    private val conflictedFiles: List<String>,
    private val contentProvider: GitConflictContentProvider = GitConflictContentProvider(workingDirectory),
) : DialogWrapper(project, true) {

    private val buttonGroups = mutableMapOf<String, ButtonGroup>()

    /** Conflict sides per file, read once so the git index is not queried twice. */
    private val contentsByFile: Map<String, ConflictContents> by lazy {
        conflictedFiles.associateWith { contentProvider.contentsFor(it) }
    }

    init {
        title = "Resolve Git Backup Conflicts"
        setOKButtonText("Resolve")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val rootPanel = JPanel(BorderLayout(0, JBUI.scale(12)))
        rootPanel.border = JBUI.Borders.empty(8)

        // Top info header
        val headerText = "Conflicts were detected while backing up your CreditPincher data to the remote repository. " +
                "Please choose how to resolve each conflict below."
        val headerLabel = JBLabel(headerText, Messages.getWarningIcon(), SwingConstants.LEFT)
        rootPanel.add(headerLabel, BorderLayout.NORTH)

        // Main files container
        val filesPanel = JPanel()
        filesPanel.layout = BoxLayout(filesPanel, BoxLayout.Y_AXIS)

        for (file in conflictedFiles) {
            val fileCard = JPanel()
            fileCard.layout = BoxLayout(fileCard, BoxLayout.Y_AXIS)
            fileCard.border = IdeBorderFactory.createTitledBorder(file, true)
            fileCard.alignmentX = Component.LEFT_ALIGNMENT

            val bg = ButtonGroup()
            buttonGroups[file] = bg

            val options = mutableListOf<JRadioButton>()

            if (file == USAGE_LOG_FILE) {
                options += radioButton("Merge records chronologically (Recommended)", ACTION_MERGE)
                options += radioButton("Keep Local entries only", ACTION_OURS)
                options += radioButton("Keep Remote entries only", ACTION_THEIRS)
            } else if (file == BUDGET_FILE) {
                val contents = contentsByFile[file]
                val localDisplay = contents?.ours?.trim().orEmpty().ifEmpty { "Not set" }
                val remoteDisplay = contents?.theirs?.trim().orEmpty().ifEmpty { "Not set" }

                options += radioButton("Keep Local budget ($localDisplay)", ACTION_OURS)
                options += radioButton("Keep Remote budget ($remoteDisplay)", ACTION_THEIRS)
            } else {
                // Fallback for other files: manual review is the safest default.
                options += manualRadioButton()
                options += radioButton("Keep Local version", ACTION_OURS)
                options += radioButton("Keep Remote version", ACTION_THEIRS)
            }

            if (options.none { it.actionCommand == ACTION_MANUAL }) {
                options += manualRadioButton()
            }

            options.first().isSelected = true
            for (option in options) {
                bg.add(option)
                fileCard.add(option)
            }

            filesPanel.add(fileCard)
            filesPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        rootPanel.add(filesPanel, BorderLayout.CENTER)
        return rootPanel
    }

    /**
     * Applies the selected resolution for every conflicted file, opening the
     * IDE's three-way merge window for any file the user chose to review by hand.
     *
     * @return true once every file has been resolved, or false as soon as the
     *   user cancels a merge window - in which case the caller should abort the
     *   in-progress merge/rebase rather than commit a half-resolved tree.
     */
    fun applyResolutions(): Boolean {
        for (file in conflictedFiles) {
            val selection = buttonGroups[file]?.selection?.actionCommand ?: ACTION_OURS
            val filePath = workingDirectory.resolve(file)
            val contents = contentsByFile[file] ?: contentProvider.contentsFor(file)

            if (selection == ACTION_MANUAL) {
                if (!ThreeWayMergeWindow.show(project, filePath, contents)) {
                    return false
                }
                continue
            }

            val resolvedContent = when (selection) {
                ACTION_MERGE -> if (file == USAGE_LOG_FILE) {
                    UsageLogMerger.merge(contents.ours, contents.theirs)
                } else {
                    contents.ours
                }

                ACTION_THEIRS -> contents.theirs
                else -> contents.ours
            }

            filePath.parent?.let { Files.createDirectories(it) }
            Files.writeString(filePath, resolvedContent, StandardCharsets.UTF_8)
        }

        return true
    }

    private fun radioButton(text: String, actionCommand: String): JRadioButton {
        val button = JRadioButton(text)
        button.actionCommand = actionCommand
        return button
    }

    private fun manualRadioButton(): JRadioButton {
        val button = radioButton("Review and edit in the merge window…", ACTION_MANUAL)
        button.toolTipText = "Opens the IDE's three-way merge window with an editable result pane."
        return button
    }

    private companion object {
        const val USAGE_LOG_FILE = "usage-log.csv"
        const val BUDGET_FILE = "monthly-budget.txt"

        const val ACTION_MERGE = "merge"
        const val ACTION_OURS = "ours"
        const val ACTION_THEIRS = "theirs"
        const val ACTION_MANUAL = "manual"
    }
}
