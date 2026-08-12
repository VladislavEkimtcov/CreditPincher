package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.nio.file.Path

/**
 * Thin wrapper around the native `git` command line tool used to back up the
 * CreditPincher storage directory to a remote repository.
 */
class GitBackupService(
    private val workingDirectory: Path,
    private val conflictResolver: GitConflictResolver = NoOpGitConflictResolver(),
) {

    /**
     * @param success whether the operation ultimately succeeded.
     * @param output human readable log of what happened, suitable for display.
     * @param conflict true if the failure was caused by an unresolved merge
     *   conflict (as opposed to a network/auth/other git error).
     */
    data class GitResult(val success: Boolean, val output: String, val conflict: Boolean = false)

    /**
     * Everything the UI needs to know about the storage directory, gathered in
     * one pass so the panel only has to make a single background call.
     *
     * A storage directory that is already a repository with its own remote is
     * the normal case for anyone who set it up by hand - it must be reported
     * accurately rather than treated as "not connected yet", because
     * re-initializing it would discard that existing setup.
     */
    data class RepositoryState(
        val gitAvailable: Boolean,
        val isRepository: Boolean,
        val remoteUrl: String? = null,
        val branch: String? = null,
        val ahead: Int = 0,
        val behind: Int = 0,
        val hasUpstream: Boolean = false,
        val hasUncommittedChanges: Boolean = false,
    ) {
        val hasRemote: Boolean get() = !remoteUrl.isNullOrBlank()
    }

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

    /** Collects a full [RepositoryState] snapshot of [workingDirectory]. */
    fun inspect(): RepositoryState {
        if (!isGitAvailable()) {
            return RepositoryState(gitAvailable = false, isRepository = false)
        }

        val insideWorkTree = runGit(listOf("rev-parse", "--is-inside-work-tree"))
        if (!insideWorkTree.success || insideWorkTree.output.trim() != "true") {
            return RepositoryState(gitAvailable = true, isRepository = false)
        }

        val remoteUrl = runGit(listOf("remote", "get-url", "origin"))
            .takeIf { it.success }?.output?.trim()?.ifEmpty { null }
        val branch = runGit(listOf("rev-parse", "--abbrev-ref", "HEAD"))
            .takeIf { it.success }?.output?.trim()?.ifEmpty { null }
        val hasUncommittedChanges = runGit(listOf("status", "--porcelain")).output.isNotBlank()

        // "<behind> <ahead>" relative to the tracked branch; absent when the
        // branch has no upstream yet, in which case both stay at zero.
        var ahead = 0
        var behind = 0
        val hasUpstream = hasUpstream()
        if (hasUpstream) {
            val counts = runGit(listOf("rev-list", "--left-right", "--count", "@{upstream}...HEAD"))
            if (counts.success) {
                val parts = counts.output.trim().split(Regex("\\s+"))
                if (parts.size == 2) {
                    behind = parts[0].toIntOrNull() ?: 0
                    ahead = parts[1].toIntOrNull() ?: 0
                }
            }
        }

        return RepositoryState(
            gitAvailable = true,
            isRepository = true,
            remoteUrl = remoteUrl,
            branch = branch,
            ahead = ahead,
            behind = behind,
            hasUpstream = hasUpstream,
            hasUncommittedChanges = hasUncommittedChanges,
        )
    }

    /**
     * Points `origin` at [remoteUrl] for a directory that is *already* a git
     * repository, without re-initializing it or renaming its current branch.
     *
     * Use this instead of [connectToRemote] whenever [isGitRepository] is true:
     * the caller's repository, branch and history are left untouched.
     */
    fun setRemote(remoteUrl: String): GitResult {
        if (!isGitAvailable()) {
            return GitResult(false, "git executable not found on PATH.")
        }
        if (!isGitRepository()) {
            return GitResult(false, "$workingDirectory is not a git repository.")
        }

        val existing = runGit(listOf("remote", "get-url", "origin"))
        val result = if (existing.success && existing.output.isNotBlank()) {
            runGit(listOf("remote", "set-url", "origin", remoteUrl))
        } else {
            runGit(listOf("remote", "add", "origin", remoteUrl))
        }

        return GitResult(result.success, result.output.ifBlank { "origin set to $remoteUrl" })
    }

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

    /**
     * Stages all changes, commits (if there is anything to commit), and pushes to origin.
     *
     * If the push is rejected because the remote has diverged, this will
     * silently attempt to reconcile the histories (first via a merge, then
     * via a rebase) before retrying the push once. If neither automatic
     * strategy succeeds, [conflictResolver] is given a chance to resolve the
     * remaining conflicts; if that also fails, any in-progress merge/rebase
     * is aborted and a failed [GitResult] with `conflict = true` is returned
     * so the caller can surface this to the user.
     *
     * [onStatusUpdate] is invoked (from the calling thread - callers running
     * this in the background should hop back to the UI thread themselves) to
     * report progress, e.g. "Resolving conflict…", so the UI can indicate
     * that automatic conflict resolution is underway.
     */
    fun commitAndPush(
        commitMessage: String = "Update CreditPincher log",
        initialPush: Boolean = false,
        onStatusUpdate: (String) -> Unit = {},
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

        // A hand-made repository may have a branch with no upstream yet; plain
        // `git push` would fail on it, so establish the tracking branch instead.
        val pushArgs = if (initialPush || !hasUpstream()) {
            listOf("push", "-u", "origin", currentBranch())
        } else {
            listOf("push")
        }
        var pushResult = runGit(pushArgs)
        outputs += pushResult.output

        if (!pushResult.success && isLikelyDivergedRejection(pushResult.output)) {
            val reconciliation = reconcileWithRemote(onStatusUpdate)
            outputs += reconciliation.output

            if (reconciliation.success) {
                pushResult = runGit(pushArgs)
                outputs += pushResult.output
            } else {
                return GitResult(false, outputs.filter { it.isNotBlank() }.joinToString("\n").trim(), conflict = true)
            }
        }

        if (!hasChangesToCommit && pushResult.success) {
            outputs += "Nothing new to commit; pushed existing state."
        }

        return GitResult(pushResult.success, outputs.filter { it.isNotBlank() }.joinToString("\n").trim())
    }

    /**
     * Fetches from `origin`, reconciles with it if the remote has moved ahead
     * (the same merge-then-rebase strategy [commitAndPush] uses reactively on
     * a rejected push), then stages, commits and pushes local changes.
     *
     * Unlike [commitAndPush], this proactively pulls in remote-only commits
     * even when there is nothing local to push - the behaviour an unattended
     * periodic sync needs, since nothing would otherwise trigger a pull on a
     * machine that has not touched its own copy of the storage directory.
     */
    fun sync(onStatusUpdate: (String) -> Unit = {}): GitResult {
        val fetchResult = runGit(listOf("fetch"))
        if (!fetchResult.success) {
            return GitResult(false, fetchResult.output)
        }

        if (hasUpstream() && behindUpstreamCount() > 0) {
            val reconciliation = reconcileWithRemote(onStatusUpdate)
            if (!reconciliation.success) {
                return reconciliation
            }
        }

        return commitAndPush(onStatusUpdate = onStatusUpdate)
    }

    /** Number of commits present on `@{upstream}` but not yet on HEAD. */
    private fun behindUpstreamCount(): Int {
        val counts = runGit(listOf("rev-list", "--left-right", "--count", "@{upstream}...HEAD"))
        if (!counts.success) {
            return 0
        }
        val parts = counts.output.trim().split(Regex("\\s+"))
        return if (parts.size == 2) parts[0].toIntOrNull() ?: 0 else 0
    }

    /**
     * Attempts to bring the local branch up to date with its remote
     * counterpart without any user interaction. Tries a plain merge first
     * (`git pull --no-rebase --no-edit`), then falls back to a rebase
     * (`git pull --rebase`). If both leave unresolved conflicts, gives
     * [conflictResolver] a chance to resolve them; otherwise aborts whichever
     * operation was in progress so the working tree is left clean.
     */
    private fun reconcileWithRemote(onStatusUpdate: (String) -> Unit): GitResult {
        val attempts = mutableListOf<String>()

        onStatusUpdate("Remote has new changes; attempting to merge automatically…")
        val mergeResult = runGit(listOf("pull", "--no-rebase", "--no-edit"))
        attempts += mergeResult.output
        if (mergeResult.success && !hasUnresolvedConflicts()) {
            return GitResult(true, attempts.joinToString("\n").trim())
        }

        if (hasUnresolvedConflicts() && tryResolveWithConflictResolver(onStatusUpdate, "merge")) {
            return GitResult(true, attempts.joinToString("\n").trim())
        }
        runGit(listOf("merge", "--abort"))

        onStatusUpdate("Automatic merge failed; retrying with a rebase…")
        val rebaseResult = runGit(listOf("pull", "--rebase"))
        attempts += rebaseResult.output
        if (rebaseResult.success && !hasUnresolvedConflicts()) {
            return GitResult(true, attempts.joinToString("\n").trim())
        }

        if (hasUnresolvedConflicts() && tryResolveWithConflictResolver(onStatusUpdate, "rebase")) {
            return GitResult(true, attempts.joinToString("\n").trim())
        }
        runGit(listOf("rebase", "--abort"))

        onStatusUpdate("Automatic conflict resolution failed.")
        attempts += "Could not automatically reconcile with the remote branch; manual conflict resolution is required."
        return GitResult(false, attempts.filter { it.isNotBlank() }.joinToString("\n").trim(), conflict = true)
    }

    /**
     * Gives [conflictResolver] a chance to resolve conflicts left behind by
     * an in-progress merge or rebase. If it reports success, finalizes the
     * in-progress operation (commit for a merge, `--continue` for a rebase).
     */
    private fun tryResolveWithConflictResolver(onStatusUpdate: (String) -> Unit, operation: String): Boolean {
        val conflictedFiles = conflictedFiles()
        if (conflictedFiles.isEmpty()) {
            return false
        }

        onStatusUpdate("Conflicts detected in ${conflictedFiles.size} file(s); asking conflict resolver…")
        if (!conflictResolver.resolveConflicts(workingDirectory, conflictedFiles)) {
            return false
        }

        val finalizeResult = if (operation == "rebase") {
            runGit(listOf("add", "-A"))
            runGit(listOf("rebase", "--continue"))
        } else {
            runGit(listOf("add", "-A"))
            runGit(listOf("commit", "--no-edit"))
        }

        return finalizeResult.success && !hasUnresolvedConflicts()
    }

    /** Returns true if `git status --porcelain` reports any unmerged paths. */
    private fun hasUnresolvedConflicts(): Boolean {
        val status = runGit(listOf("status", "--porcelain"))
        return status.output.lineSequence().any { line ->
            val statusCode = line.take(2)
            statusCode in UNMERGED_STATUS_CODES
        }
    }

    /** Name of the checked-out branch, falling back to `main` on a detached HEAD. */
    private fun currentBranch(): String {
        val result = runGit(listOf("rev-parse", "--abbrev-ref", "HEAD"))
        val branch = result.output.trim()
        return if (result.success && branch.isNotEmpty() && branch != "HEAD") branch else "main"
    }

    /** Returns true if the current branch tracks an upstream branch. */
    private fun hasUpstream(): Boolean =
        runGit(listOf("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{upstream}")).success

    /** Returns the list of file paths that currently have merge conflicts. */
    private fun conflictedFiles(): List<String> {
        val result = runGit(listOf("diff", "--name-only", "--diff-filter=U"))
        return result.output.lines().filter { it.isNotBlank() }
    }

    /** Heuristic check for a push rejection caused by the remote having diverged. */
    private fun isLikelyDivergedRejection(output: String): Boolean {
        val lower = output.lowercase()
        return lower.contains("rejected") ||
            lower.contains("non-fast-forward") ||
            lower.contains("fetch first") ||
            lower.contains("failed to push")
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

    private companion object {
        /** Git porcelain status codes that indicate an unmerged (conflicted) path. */
        val UNMERGED_STATUS_CODES = setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")
    }
}

