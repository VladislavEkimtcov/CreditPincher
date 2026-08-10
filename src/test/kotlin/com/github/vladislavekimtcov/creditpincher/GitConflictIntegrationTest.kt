package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.services.GitBackupService
import com.github.vladislavekimtcov.creditpincher.services.GitConflictContentProvider
import com.github.vladislavekimtcov.creditpincher.services.GitConflictResolver
import com.github.vladislavekimtcov.creditpincher.services.UsageLogMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Drives [GitBackupService] against real local repositories so the conflict
 * path - detection, reading the index stages via [GitConflictContentProvider],
 * writing a resolution and finalizing the merge - is exercised end to end.
 *
 * The interactive merge window itself cannot be shown headlessly, so this test
 * substitutes a resolver that performs the same steps the dialog performs once
 * the user has picked a resolution.
 */
class GitConflictIntegrationTest {

    @Test
    fun resolvesConflictingRemoteChangesAndPushes() {
        assumeTrue("git is not available on PATH", isGitAvailable())

        val root = Files.createTempDirectory("credit-pincher-git-test")
        try {
            val remote = root.resolve("remote.git")
            Files.createDirectories(remote)
            git(remote, "init", "--bare", "--initial-branch=main")

            val first = clone(root, remote, "first")
            writeLog(first, listOf("2026-07-01T09:00:00Z,1.0"))
            git(first, "add", "-A")
            git(first, "commit", "-m", "seed")
            git(first, "push", "-u", "origin", "main")

            val second = clone(root, remote, "second")

            // The remote moves ahead with an entry the second clone has never seen.
            writeLog(first, listOf("2026-07-01T09:00:00Z,1.0", "2026-07-02T09:00:00Z,2.0"))
            git(first, "add", "-A")
            git(first, "commit", "-m", "remote entry")
            git(first, "push")

            // The second clone appends a different entry at the same place, which
            // makes the push diverge and the automatic merge conflict.
            writeLog(second, listOf("2026-07-01T09:00:00Z,1.0", "2026-07-03T09:00:00Z,3.0"))

            val resolvedFiles = mutableListOf<String>()
            val service = GitBackupService(second, MergingConflictResolver(resolvedFiles))

            val result = service.commitAndPush()

            assertTrue("Expected the push to succeed:\n${result.output}", result.success)
            assertEquals(listOf("usage-log.csv"), resolvedFiles)

            val entries = UsageLogMerger.parseEntries(Files.readString(second.resolve("usage-log.csv")))
            assertEquals(3, entries.size)
            assertEquals(listOf(1.0, 2.0, 3.0), entries.map { it.amount })

            // The resolution reached the remote, so a fresh clone sees all three entries.
            val third = clone(root, remote, "third")
            val pushedEntries = UsageLogMerger.parseEntries(Files.readString(third.resolve("usage-log.csv")))
            assertEquals(3, pushedEntries.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    /** Resolves conflicts the way the dialog's "merge chronologically" option does. */
    private class MergingConflictResolver(private val resolvedFiles: MutableList<String>) : GitConflictResolver {
        override fun resolveConflicts(workingDirectory: Path, conflictedFiles: List<String>): Boolean {
            val provider = GitConflictContentProvider(workingDirectory)
            for (file in conflictedFiles) {
                val contents = provider.contentsFor(file)
                if (contents.ours.isEmpty() && contents.theirs.isEmpty()) {
                    return false
                }
                Files.writeString(
                    workingDirectory.resolve(file),
                    UsageLogMerger.merge(contents.ours, contents.theirs),
                    StandardCharsets.UTF_8,
                )
                resolvedFiles += file
            }
            return true
        }
    }

    private fun clone(root: Path, remote: Path, name: String): Path {
        git(root, "clone", remote.toAbsolutePath().toString(), name)
        val clone = root.resolve(name)
        git(clone, "config", "user.name", "CreditPincher Test")
        git(clone, "config", "user.email", "test@example.invalid")
        git(clone, "config", "commit.gpgsign", "false")
        return clone
    }

    private fun writeLog(repository: Path, rows: List<String>) {
        val content = (listOf("timestamp,amount") + rows).joinToString("\n", postfix = "\n")
        Files.writeString(repository.resolve("usage-log.csv"), content, StandardCharsets.UTF_8)
    }

    private fun git(workingDirectory: Path, vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(workingDirectory.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val exitCode = process.waitFor()
        assertEquals("git ${args.joinToString(" ")} failed:\n$output", 0, exitCode)
    }

    private fun isGitAvailable(): Boolean = runCatching {
        val process = ProcessBuilder("git", "--version").redirectErrorStream(true).start()
        process.inputStream.readBytes()
        process.waitFor() == 0
    }.getOrDefault(false)
}
