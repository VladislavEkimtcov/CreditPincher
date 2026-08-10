package com.github.vladislavekimtcov.creditpincher.services

import com.intellij.diff.DiffManagerEx
import com.intellij.diff.DiffRequestFactory
import com.intellij.diff.merge.MergeResult
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opens the IDE's own three-way merge window - the very same tool IntelliJ shows
 * for VCS conflicts - for a single conflicted file: the local changes on the
 * left, the fully editable merge result in the middle and the incoming remote
 * changes on the right, complete with the platform's accept-left/accept-right
 * gutter actions and conflict colour coding.
 *
 * Must be called on the EDT. The window is modal, so [show] does not return
 * until the user has applied or cancelled the merge.
 */
object ThreeWayMergeWindow {

    private const val LOCAL_TITLE = "Local Changes"
    private const val BASE_TITLE = "Base (Common Ancestor)"
    private const val REMOTE_TITLE = "Incoming Remote Changes"

    /**
     * Shows the merge window for [filePath], seeded with [contents].
     *
     * @return true if the user applied a resolution - the resolved text has been
     *   written to [filePath] and is ready to be staged - or false if they
     *   cancelled or the window could not be opened.
     */
    fun show(project: Project, filePath: Path, contents: ConflictContents): Boolean {
        val fileName = filePath.fileName?.toString() ?: filePath.toString()

        return try {
            // The merge result is written back into this file, so it has to exist
            // and be known to the VFS before the request is built.
            filePath.parent?.let { Files.createDirectories(it) }
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, contents.ours, StandardCharsets.UTF_8)
            }

            val outputFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(filePath)
            if (outputFile == null) {
                thisLogger().warn("Could not locate $filePath in the VFS; skipping merge window.")
                return false
            }

            var mergeResult: MergeResult? = null
            val request = DiffRequestFactory.getInstance().createMergeRequest(
                project,
                outputFile,
                listOf(
                    contents.ours.toByteArray(StandardCharsets.UTF_8),
                    contents.base.toByteArray(StandardCharsets.UTF_8),
                    contents.theirs.toByteArray(StandardCharsets.UTF_8),
                ),
                "Resolve Conflict - $fileName",
                listOf(LOCAL_TITLE, BASE_TITLE, REMOTE_TITLE),
            ) { result -> mergeResult = result }

            // showMergeBuiltin always uses a modal window (and skips any configured
            // external merge tool), which is what lets us block here and report the
            // outcome back to the caller.
            DiffManagerEx.getInstance().showMergeBuiltin(project, request)

            val resolved = mergeResult != null && mergeResult != MergeResult.CANCEL
            if (resolved) {
                flushToDisk(filePath)
            }
            resolved
        } catch (e: Exception) {
            thisLogger().warn("Could not open the merge window for $fileName", e)
            false
        }
    }

    /**
     * The merge tool applies its result to the output document; make sure it has
     * reached the disk before the caller hands the file back to `git add`.
     */
    private fun flushToDisk(filePath: Path) {
        val file = LocalFileSystem.getInstance().findFileByNioFile(filePath) ?: return
        val documentManager = FileDocumentManager.getInstance()
        val document = documentManager.getCachedDocument(file)
        if (document != null && documentManager.isDocumentUnsaved(document)) {
            documentManager.saveDocument(document)
        }
        file.refresh(false, false)
    }
}
