package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.services.GitBackupService
import com.github.vladislavekimtcov.creditpincher.services.NoOpGitConflictResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Covers [GitBackupService.sync], the path the unattended periodic auto-sync
 * uses. Unlike [GitBackupService.commitAndPush] - which only pulls reactively
 * when a push is rejected as diverged - `sync` proactively fetches and
 * reconciles first, so a machine with nothing of its own to push still picks
 * up commits made elsewhere.
 */
class GitBackupSyncTest {

    @Test
    fun syncPullsRemoteCommitsWhenNothingToPush() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val remote = initBareRemote(root, "remote.git")

            val first = clone(root, remote, "first")
            write(first, "usage-log.csv", "timestamp,amount\n")
            git(first, "add", "-A")
            git(first, "commit", "-m", "seed")
            git(first, "push", "-u", "origin", "main")

            val second = clone(root, remote, "second")

            // Remote moves ahead of `second`, which has nothing local to push.
            write(first, "usage-log.csv", "timestamp,amount\n2026-07-02T09:00:00Z,2.0\n")
            git(first, "add", "-A")
            git(first, "commit", "-m", "remote entry")
            git(first, "push")

            val result = GitBackupService(second).sync()

            assertTrue("Expected sync to succeed:\n${result.output}", result.success)
            val content = Files.readString(second.resolve("usage-log.csv"), StandardCharsets.UTF_8)
            assertTrue("Expected the remote entry to be pulled in:\n$content", content.contains("2.0"))
        }
    }

    @Test
    fun syncAbortsAndReportsConflictWithoutADialog() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val remote = initBareRemote(root, "remote.git")

            val first = clone(root, remote, "first")
            write(first, "monthly-budget.txt", "100.0")
            git(first, "add", "-A")
            git(first, "commit", "-m", "seed")
            git(first, "push", "-u", "origin", "main")

            val second = clone(root, remote, "second")

            // Both clones edit the same line differently, so reconciliation fails.
            write(first, "monthly-budget.txt", "200.0")
            git(first, "add", "-A")
            git(first, "commit", "-m", "remote budget change")
            git(first, "push")

            write(second, "monthly-budget.txt", "300.0")
            git(second, "add", "-A")
            git(second, "commit", "-m", "local budget change")

            val result = GitBackupService(second, NoOpGitConflictResolver()).sync()

            assertFalse("Expected sync to fail on an unresolved conflict", result.success)
            assertTrue("Expected the failure to be reported as a conflict", result.conflict)
            assertTrue(
                "Expected the working tree to be left clean after aborting:\n${capture(second, "status", "--porcelain")}",
                capture(second, "status", "--porcelain").isEmpty(),
            )
        }
    }

    private fun initBareRemote(root: Path, name: String): Path {
        val remote = root.resolve(name)
        Files.createDirectories(remote)
        git(remote, "init", "--bare", "--initial-branch=main")
        return remote
    }

    private fun clone(root: Path, remote: Path, name: String): Path {
        git(root, "clone", remote.toAbsolutePath().toString(), name)
        val clone = root.resolve(name)
        git(clone, "config", "user.name", "CreditPincher Test")
        git(clone, "config", "user.email", "test@example.invalid")
        git(clone, "config", "commit.gpgsign", "false")
        return clone
    }

    private fun write(repository: Path, name: String, content: String) {
        Files.writeString(repository.resolve(name), content, StandardCharsets.UTF_8)
    }

    private fun withTemporaryDirectory(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("credit-pincher-git-sync")
        try {
            action(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun capture(workingDirectory: Path, vararg args: String): String {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8).trim()
        return if (process.waitFor() == 0) output else ""
    }

    private fun git(workingDirectory: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        assertEquals("git ${args.joinToString(" ")} failed:\n$output", 0, process.waitFor())
    }

    private fun isGitAvailable(): Boolean = runCatching {
        val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    }.getOrDefault(false)
}
