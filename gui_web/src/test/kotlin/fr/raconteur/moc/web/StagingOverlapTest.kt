package fr.raconteur.moc.web

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.versioning.PatchMode
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

// Store-level pin for the "" file-deletion marker: it overlaps nothing by path
// semantics, so delete-then-recreate compositions stay possible in both orders.
class StagingOverlapTest : WebTestBase() {

    @Test
    fun `file deletion marker coexists with value entries in the same file`() {
        DraftPatch.setDeletionEntry(OptionDiff.Deleted("f.json", "", mapOf("x" to 1)), PatchMode.OVERRIDE)
        DraftPatch.setValueEntry(OptionDiff.Changed("f.json", "$['x']", 1, 2), PatchMode.OVERRIDE)
        assertEquals(2, DraftPatch.entries.size, "value after deletion marker must coexist")

        DraftPatch.clear()

        DraftPatch.setValueEntry(OptionDiff.Changed("f.json", "$['x']", 1, 2), PatchMode.OVERRIDE)
        DraftPatch.setDeletionEntry(OptionDiff.Deleted("f.json", "", mapOf("x" to 1)), PatchMode.OVERRIDE)
        assertEquals(2, DraftPatch.entries.size, "deletion marker after value must coexist")
    }
}
