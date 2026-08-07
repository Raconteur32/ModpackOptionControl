package fr.raconteur.moc.gui

import fr.raconteur.moc.content.OptionDiff
import fr.raconteur.moc.versioning.PatchMode

// Moved out of common with the authoring engine extraction (it depends on DraftPatch,
// which is authoring-tool code). Common keeps the generic path helpers
// (directChildren / isDescendant).
fun applyDiffToDraft(optDiff: OptionDiff?, mode: PatchMode) = when (optDiff) {
    is OptionDiff.New     -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Changed -> DraftPatch.setValueEntry(optDiff, mode)
    is OptionDiff.Deleted -> DraftPatch.setDeletionEntry(optDiff, mode)
    null                  -> Unit
}
