package com.github.vladislavekimtcov.creditpincher.services

import java.nio.file.Path

/**
 * Hook for resolving git merge conflicts that could not be resolved
 * automatically (e.g. via `git pull --no-rebase --no-edit` or
 * `git pull --rebase`).
 *
 * Implementations may present UI to the user (a dialog listing conflicting
 * files with options to keep "ours"/"theirs", open a merge tool, edit files
 * manually, etc.) and should leave the working tree in a resolved,
 * committable state (or a rebase/merge continued) if they return `true`.
 */
interface GitConflictResolver {

    /**
     * Attempt to resolve the given conflicted [conflictedFiles] inside
     * [workingDirectory]. Returns `true` if the conflicts were resolved and
     * the caller can proceed (e.g. `git commit` / `git rebase --continue`
     * followed by a push), or `false` if the conflicts remain unresolved and
     * the in-progress merge/rebase should be aborted.
     */
    fun resolveConflicts(workingDirectory: Path, conflictedFiles: List<String>): Boolean
}

/**
 * Resolver used when no UI is available (headless usage, tests). It silently
 * declines to resolve anything so the backup operation can safely abort the
 * in-progress merge/rebase and report the conflict to the user instead of
 * leaving the repository in a broken state.
 *
 * [SwingGitConflictResolver] is the interactive implementation: it shows a
 * per-file chooser and can hand any file off to the IDE's three-way merge
 * window via [ThreeWayMergeWindow].
 */
class NoOpGitConflictResolver : GitConflictResolver {
    override fun resolveConflicts(workingDirectory: Path, conflictedFiles: List<String>): Boolean = false
}

