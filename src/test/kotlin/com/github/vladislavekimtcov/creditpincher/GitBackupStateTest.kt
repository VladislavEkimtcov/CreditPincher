package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.services.GitBackupService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Covers the repository inspection the tool window relies on, and the promise
 * that pointing an existing repository at a new remote leaves that repository
 * otherwise untouched.
 */
class GitBackupStateTest {

    @Test
    fun reportsPlainDirectoryAsNotARepository() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val plain = root.resolve("plain")
            Files.createDirectories(plain)

            val state = GitBackupService(plain).inspect()

            assertTrue("git should be discoverable", state.gitAvailable)
            assertFalse(state.isRepository)
            assertFalse(state.hasRemote)
        }
    }

    @Test
    fun reportsRemoteBranchAndDivergenceOfExistingRepository() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val remote = initBareRemote(root, "remote.git")

            val first = clone(root, remote, "first")
            write(first, "usage-log.csv", "timestamp,amount\n")
            git(first, "add", "-A")
            git(first, "commit", "-m", "seed")
            git(first, "push", "-u", "origin", "main")

            val second = clone(root, remote, "second")

            // One commit only on the remote, one only locally: ahead 1, behind 1 -
            // the same shape as a storage directory used from two machines.
            write(first, "usage-log.csv", "timestamp,amount\n2026-07-02T09:00:00Z,2.0\n")
            git(first, "add", "-A")
            git(first, "commit", "-m", "remote entry")
            git(first, "push")

            write(second, "usage-log.csv", "timestamp,amount\n2026-07-03T09:00:00Z,3.0\n")
            git(second, "add", "-A")
            git(second, "commit", "-m", "local entry")
            git(second, "fetch", "origin")

            // An unstaged edit on top, so the dirty-tree flag is exercised too.
            write(second, "monthly-budget.txt", "310.0")

            val state = GitBackupService(second).inspect()

            assertTrue(state.isRepository)
            assertTrue(state.hasRemote)
            assertEquals(remote.toAbsolutePath().toString(), state.remoteUrl)
            assertEquals("main", state.branch)
            assertTrue(state.hasUpstream)
            assertEquals(1, state.ahead)
            assertEquals(1, state.behind)
            assertTrue(state.hasUncommittedChanges)
        }
    }

    @Test
    fun setRemoteRetargetsOriginWithoutTouchingBranchOrHistory() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val oldRemote = initBareRemote(root, "old.git")
            val newRemote = initBareRemote(root, "new.git")

            // A hand-made repository: non-default branch name, own origin, own history.
            val repository = root.resolve("storage")
            Files.createDirectories(repository)
            git(repository, "init", "--initial-branch=usage")
            configure(repository)
            write(repository, "usage-log.csv", "timestamp,amount\n")
            git(repository, "add", "-A")
            git(repository, "commit", "-m", "hand made")
            git(repository, "remote", "add", "origin", oldRemote.toAbsolutePath().toString())

            val headBefore = capture(repository, "rev-parse", "HEAD")
            val commitsBefore = capture(repository, "rev-list", "--count", "HEAD")

            val result = GitBackupService(repository).setRemote(newRemote.toAbsolutePath().toString())

            assertTrue("Expected the remote to be updated:\n${result.output}", result.success)
            assertEquals(newRemote.toAbsolutePath().toString(), capture(repository, "remote", "get-url", "origin"))
            // The branch is not renamed to main and no commit is created or lost.
            assertEquals("usage", capture(repository, "rev-parse", "--abbrev-ref", "HEAD"))
            assertEquals(headBefore, capture(repository, "rev-parse", "HEAD"))
            assertEquals(commitsBefore, capture(repository, "rev-list", "--count", "HEAD"))

            val state = GitBackupService(repository).inspect()
            assertEquals("usage", state.branch)
            assertEquals(newRemote.toAbsolutePath().toString(), state.remoteUrl)
            assertFalse("A fresh origin has no upstream yet", state.hasUpstream)
        }
    }

    @Test
    fun connectToRemoteWouldRenameTheBranchWhichIsWhySetRemoteExists() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val remote = initBareRemote(root, "remote.git")
            val repository = root.resolve("storage")
            Files.createDirectories(repository)
            git(repository, "init", "--initial-branch=usage")
            configure(repository)
            write(repository, "usage-log.csv", "timestamp,amount\n")
            git(repository, "add", "-A")
            git(repository, "commit", "-m", "hand made")

            GitBackupService(repository).connectToRemote(remote.toAbsolutePath().toString())

            // Documents the destructive behaviour the tool window must avoid for an
            // existing repository: connectToRemote force-renames the branch.
            assertEquals("main", capture(repository, "rev-parse", "--abbrev-ref", "HEAD"))
        }
    }

    @Test
    fun commitAndPushEstablishesUpstreamForABranchWithoutOne() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        withTemporaryDirectory { root ->
            val remote = initBareRemote(root, "remote.git")

            val repository = root.resolve("storage")
            Files.createDirectories(repository)
            git(repository, "init", "--initial-branch=usage")
            configure(repository)
            write(repository, "usage-log.csv", "timestamp,amount\n")
            git(repository, "add", "-A")
            git(repository, "commit", "-m", "hand made")
            git(repository, "remote", "add", "origin", remote.toAbsolutePath().toString())

            // Nothing tracks origin yet, so a plain `git push` would fail here.
            val service = GitBackupService(repository)
            assertFalse(service.inspect().hasUpstream)

            write(repository, "monthly-budget.txt", "310.0")
            val result = service.commitAndPush()

            assertTrue("Expected the push to succeed:\n${result.output}", result.output.isNotBlank())
            assertTrue("Expected the push to succeed:\n${result.output}", result.success)

            val state = service.inspect()
            assertTrue("Upstream should now be tracked", state.hasUpstream)
            assertEquals(0, state.ahead)
            assertEquals(0, state.behind)
            assertNotEquals("", capture(repository, "rev-parse", "origin/usage"))
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
        configure(clone)
        return clone
    }

    private fun configure(repository: Path) {
        git(repository, "config", "user.name", "CreditPincher Test")
        git(repository, "config", "user.email", "test@example.invalid")
        git(repository, "config", "commit.gpgsign", "false")
    }

    private fun write(repository: Path, name: String, content: String) {
        Files.writeString(repository.resolve(name), content, StandardCharsets.UTF_8)
    }

    private fun withTemporaryDirectory(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("credit-pincher-git-state")
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
