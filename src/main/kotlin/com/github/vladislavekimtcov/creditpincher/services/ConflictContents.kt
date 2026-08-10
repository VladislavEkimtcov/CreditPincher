package com.github.vladislavekimtcov.creditpincher.services

/**
 * The three sides of a git merge conflict for a single file.
 *
 * [ours] is the local version (index stage 2), [theirs] the incoming remote
 * version (stage 3) and [base] the common ancestor (stage 1), which is blank
 * for add/add conflicts.
 */
data class ConflictContents(
    val base: String,
    val ours: String,
    val theirs: String,
)

/**
 * Reconstructs the two (or three) sides of a conflict from the `<<<<<<<` /
 * `=======` / `>>>>>>>` markers git leaves in the working tree copy.
 *
 * Used as a fallback for the rare cases where the index stages are unavailable
 * (for example after a `git checkout --conflict=diff3` or when a stage was
 * pruned), so the merge window can still be populated with something useful.
 */
object ConflictMarkerParser {

    private const val OURS_MARKER = "<<<<<<<"
    private const val BASE_MARKER = "|||||||"
    private const val SEPARATOR_MARKER = "======="
    private const val THEIRS_MARKER = ">>>>>>>"

    /** True if [content] looks like it still contains an unresolved conflict block. */
    fun hasConflictMarkers(content: String): Boolean {
        var sawOurs = false
        for (line in content.lineSequence()) {
            if (line.startsWith(OURS_MARKER)) sawOurs = true
            if (sawOurs && line.startsWith(THEIRS_MARKER)) return true
        }
        return false
    }

    /**
     * Splits [content] into its conflict sides. Lines outside any conflict block
     * are common to all three sides; `|||||||` sections (diff3 style) populate
     * [ConflictContents.base].
     */
    fun parse(content: String): ConflictContents {
        val base = mutableListOf<String>()
        val ours = mutableListOf<String>()
        val theirs = mutableListOf<String>()

        var section = Section.COMMON
        for (line in content.lines()) {
            when {
                line.startsWith(OURS_MARKER) -> section = Section.OURS
                line.startsWith(BASE_MARKER) && section == Section.OURS -> section = Section.BASE
                line.startsWith(SEPARATOR_MARKER) && section != Section.COMMON -> section = Section.THEIRS
                line.startsWith(THEIRS_MARKER) && section == Section.THEIRS -> section = Section.COMMON
                else -> when (section) {
                    Section.COMMON -> {
                        base += line
                        ours += line
                        theirs += line
                    }

                    Section.OURS -> ours += line
                    Section.BASE -> base += line
                    Section.THEIRS -> theirs += line
                }
            }
        }

        return ConflictContents(
            base = base.joinToString("\n"),
            ours = ours.joinToString("\n"),
            theirs = theirs.joinToString("\n"),
        )
    }

    private enum class Section { COMMON, OURS, BASE, THEIRS }
}
