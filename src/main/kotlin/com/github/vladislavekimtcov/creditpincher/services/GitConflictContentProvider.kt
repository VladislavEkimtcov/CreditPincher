package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads the three sides of a conflicted file out of the git index
 * (`git show :1:`, `:2:`, `:3:`), falling back to the conflict markers left in
 * the working tree when the index stages are unavailable.
 *
 * [readStage] is `open` so tests can supply canned stage contents without
 * needing a real repository.
 */
open class GitConflictContentProvider(private val workingDirectory: Path) {

    /** Contents of [file] at merge [stage], or an empty string if that stage does not exist. */
    open fun readStage(stage: Int, file: String): String {
        return try {
            val commandLine = GeneralCommandLine("git", "show", ":$stage:$file")
                .withWorkDirectory(workingDirectory.toFile())
                .withCharset(StandardCharsets.UTF_8)
            val output = CapturingProcessHandler(commandLine).runProcess(10_000)
            if (output.exitCode == 0 && !output.isTimeout) output.stdout else ""
        } catch (e: Exception) {
            ""
        }
    }

    /** Collects the local, remote and common-ancestor versions of [file]. */
    fun contentsFor(file: String): ConflictContents {
        val base = readStage(1, file)
        val ours = readStage(2, file)
        val theirs = readStage(3, file)

        if (ours.isNotEmpty() || theirs.isNotEmpty()) {
            return ConflictContents(base = base, ours = ours, theirs = theirs)
        }

        val workingCopy = readWorkingCopy(file)
        return if (ConflictMarkerParser.hasConflictMarkers(workingCopy)) {
            ConflictMarkerParser.parse(workingCopy)
        } else {
            ConflictContents(base = base, ours = workingCopy, theirs = workingCopy)
        }
    }

    private fun readWorkingCopy(file: String): String =
        runCatching { Files.readString(workingDirectory.resolve(file)) }.getOrDefault("")
}
