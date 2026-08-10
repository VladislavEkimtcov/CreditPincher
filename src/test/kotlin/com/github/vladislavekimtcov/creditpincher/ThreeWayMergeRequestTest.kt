package com.github.vladislavekimtcov.creditpincher

import com.github.vladislavekimtcov.creditpincher.services.ConflictContents
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.merge.MergeResult
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies against a real IDE application that the three-way merge request
 * behind [com.github.vladislavekimtcov.creditpincher.services.ThreeWayMergeWindow]
 * is built correctly: three panes in the order the window expects, an editable
 * result pane, and a result that lands on disk once applied.
 *
 * The window itself is modal, so it cannot be shown from a test; everything up
 * to (and including) applying its result is covered here.
 */
class ThreeWayMergeRequestTest : BasePlatformTestCase() {

    private val contents = ConflictContents(
        base = "timestamp,amount\n",
        ours = "timestamp,amount\n2026-07-03T09:00:00Z,3.0\n",
        theirs = "timestamp,amount\n2026-07-04T09:00:00Z,4.0\n",
    )

    fun testBuildsEditableThreePaneMergeRequest() {
        withTemporaryLogFile { path ->
            val outputFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            assertNotNull("Storage file should be visible to the VFS", outputFile)

            val request = DiffRequestFactory.getInstance().createTextMergeRequest(
                project,
                outputFile!!,
                byteContents(),
                "Resolve Conflict - usage-log.csv",
                listOf("Local Changes", "Base (Common Ancestor)", "Incoming Remote Changes"),
            ) { }

            assertEquals(3, request.contents.size)
            assertEquals(contents.ours, request.contents[0].document.text)
            assertEquals(contents.base, request.contents[1].document.text)
            assertEquals(contents.theirs, request.contents[2].document.text)
            assertEquals(
                listOf("Local Changes", "Base (Common Ancestor)", "Incoming Remote Changes"),
                request.contentTitles,
            )

            // The middle pane is the one the user edits, so it must be writable
            // while the two side panes stay read-only.
            assertTrue("Result pane must be editable", request.outputContent.document.isWritable)
        }
    }

    fun testApplyingResultNotifiesCallbackAndReachesDisk() {
        withTemporaryLogFile { path ->
            val outputFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!

            var reported: MergeResult? = null
            val request = DiffRequestFactory.getInstance().createTextMergeRequest(
                project,
                outputFile,
                byteContents(),
                "Resolve Conflict - usage-log.csv",
                listOf("Local Changes", "Base (Common Ancestor)", "Incoming Remote Changes"),
            ) { result -> reported = result }

            request.applyResult(MergeResult.RIGHT)

            assertEquals(MergeResult.RIGHT, reported)
            assertEquals(contents.theirs, request.outputContent.document.text)

            // ThreeWayMergeWindow saves the document afterwards so `git add` sees
            // the resolved text; check that this actually updates the file.
            FileDocumentManager.getInstance().saveDocument(request.outputContent.document)
            assertEquals(contents.theirs, Files.readString(path))
        }
    }

    private fun byteContents(): List<ByteArray> = listOf(
        contents.ours.toByteArray(StandardCharsets.UTF_8),
        contents.base.toByteArray(StandardCharsets.UTF_8),
        contents.theirs.toByteArray(StandardCharsets.UTF_8),
    )

    private fun withTemporaryLogFile(action: (Path) -> Unit) {
        val directory = Files.createTempDirectory("credit-pincher-merge-test")
        try {
            val path = directory.resolve("usage-log.csv")
            Files.writeString(path, contents.ours, StandardCharsets.UTF_8)
            action(path)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
