package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
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
    project: Project,
    private val workingDirectory: Path,
    private val conflictedFiles: List<String>
) : DialogWrapper(project, true) {

    private val buttonGroups = mutableMapOf<String, ButtonGroup>()

    init {
        title = "Resolve Git Backup Conflicts"
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

            if (file == "usage-log.csv") {
                val rbMerge = JRadioButton("Merge records chronologically (Recommended)", true)
                rbMerge.actionCommand = "merge"
                val rbOurs = JRadioButton("Keep Local entries only")
                rbOurs.actionCommand = "ours"
                val rbTheirs = JRadioButton("Keep Remote entries only")
                rbTheirs.actionCommand = "theirs"

                bg.add(rbMerge)
                bg.add(rbOurs)
                bg.add(rbTheirs)

                fileCard.add(rbMerge)
                fileCard.add(rbOurs)
                fileCard.add(rbTheirs)
            } else if (file == "monthly-budget.txt") {
                val localVal = gitShow(2, file).trim()
                val remoteVal = gitShow(3, file).trim()

                val localDisplay = if (localVal.isNotEmpty()) localVal else "Not set"
                val remoteDisplay = if (remoteVal.isNotEmpty()) remoteVal else "Not set"

                val rbOurs = JRadioButton("Keep Local budget ($localDisplay)", true)
                rbOurs.actionCommand = "ours"
                val rbTheirs = JRadioButton("Keep Remote budget ($remoteDisplay)")
                rbTheirs.actionCommand = "theirs"

                bg.add(rbOurs)
                bg.add(rbTheirs)

                fileCard.add(rbOurs)
                fileCard.add(rbTheirs)
            } else {
                // Fallback for other files
                val rbOurs = JRadioButton("Keep Local version", true)
                rbOurs.actionCommand = "ours"
                val rbTheirs = JRadioButton("Keep Remote version")
                rbTheirs.actionCommand = "theirs"

                bg.add(rbOurs)
                bg.add(rbTheirs)

                fileCard.add(rbOurs)
                fileCard.add(rbTheirs)
            }

            filesPanel.add(fileCard)
            filesPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        rootPanel.add(filesPanel, BorderLayout.CENTER)
        return rootPanel
    }

    fun applyResolutions() {
        for (file in conflictedFiles) {
            val bg = buttonGroups[file] ?: continue
            val selection = bg.selection?.actionCommand ?: "ours"
            val filePath = workingDirectory.resolve(file)

            val resolvedContent = when (selection) {
                "merge" -> {
                    if (file == "usage-log.csv") {
                        val ours = gitShow(2, file)
                        val theirs = gitShow(3, file)
                        UsageLogMerger.merge(ours, theirs)
                    } else {
                        gitShow(2, file)
                    }
                }
                "theirs" -> gitShow(3, file)
                else -> gitShow(2, file) // ours
            }

            Files.createDirectories(filePath.parent)
            Files.writeString(filePath, resolvedContent, StandardCharsets.UTF_8)
        }
    }

    private fun gitShow(stage: Int, file: String): String {
        return try {
            val commandLine = GeneralCommandLine("git", "show", ":$stage:$file")
                .withWorkDirectory(workingDirectory.toFile())
                .withCharset(StandardCharsets.UTF_8)
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(10_000)
            if (output.exitCode == 0) output.stdout else ""
        } catch (e: Exception) {
            ""
        }
    }
}
