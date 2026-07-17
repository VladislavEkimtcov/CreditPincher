package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.nio.file.Path

/**
 * Thin wrapper around the native `git` command line tool used to back up the
 * CreditPincher storage directory to a remote repository.
 */
class GitBackupService(private val workingDirectory: Path) {

    data class GitResult(val success: Boolean, val output: String)

    /** Returns true if [workingDirectory] is already inside a git work tree. */
    fun isGitRepository(): Boolean {
        if (!isGitAvailable()) {
            return false
        }

        val result = runGit(listOf("rev-parse", "--is-inside-work-tree"))
        return result.success && result.output.trim() == "true"
    }

    /** Returns true if the `git` executable can be located and invoked. */
    fun isGitAvailable(): Boolean = runGit(listOf("--version")).success

    /**
     * Initializes a new git repository in [workingDirectory], wires it up to
     * [remoteUrl] as `origin`, then performs an initial commit and push.
     */
    fun connectToRemote(remoteUrl: String): GitResult {
        if (!isGitAvailable()) {
            return GitResult(false, "git executable not found on PATH.")
        }

        val outputs = mutableListOf<String>()

        if (!isGitRepository()) {
            val initResult = runGit(listOf("init"))
            outputs += initResult.output
            if (!initResult.success) {
                return GitResult(false, outputs.joinToString("\n").trim())
            }
        }

        runGit(listOf("branch", "-M", "main")).also { outputs += it.output }

        // Replace any existing origin remote so re-connecting works as expected.
        runGit(listOf("remote", "remove", "origin"))
        val remoteResult = runGit(listOf("remote", "add", "origin", remoteUrl))
        outputs += remoteResult.output
        if (!remoteResult.success) {
            return GitResult(false, outputs.joinToString("\n").trim())
        }

        val pushResult = commitAndPush(commitMessage = "Initial CreditPincher backup", initialPush = true)
        outputs += pushResult.output
        return GitResult(pushResult.success, outputs.joinToString("\n").trim())
    }

    /** Stages all changes, commits (if there is anything to commit), and pushes to origin. */
    fun commitAndPush(
        commitMessage: String = "Update CreditPincher log",
        initialPush: Boolean = false,
    ): GitResult {
        val outputs = mutableListOf<String>()

        val addResult = runGit(listOf("add", "-A"))
        outputs += addResult.output
        if (!addResult.success) {
            return GitResult(false, outputs.joinToString("\n").trim())
        }

        val statusResult = runGit(listOf("status", "--porcelain"))
        val hasChangesToCommit = statusResult.output.isNotBlank()

        if (hasChangesToCommit) {
            val commitResult = runGit(listOf("commit", "-m", commitMessage))
            outputs += commitResult.output
            if (!commitResult.success) {
                return GitResult(false, outputs.joinToString("\n").trim())
            }
        }

        val pushArgs = if (initialPush) listOf("push", "-u", "origin", "main") else listOf("push")
        val pushResult = runGit(pushArgs)
        outputs += pushResult.output

        if (!hasChangesToCommit && pushResult.success) {
            outputs += "Nothing new to commit; pushed existing state."
        }

        return GitResult(pushResult.success, outputs.filter { it.isNotBlank() }.joinToString("\n").trim())
    }

    private fun runGit(args: List<String>): GitResult {
        return try {
            val commandLine = GeneralCommandLine(listOf("git") + args)
                .withWorkDirectory(workingDirectory.toFile())
                .withCharset(Charsets.UTF_8)
            val handler = CapturingProcessHandler(commandLine)
            val output = handler.runProcess(30_000)
            val text = listOf(output.stdout, output.stderr)
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .trim()
            GitResult(output.exitCode == 0 && !output.isTimeout, text)
        } catch (e: Exception) {
            GitResult(false, e.message ?: "Unknown error running git ${args.joinToString(" ")}")
        }
    }
}

