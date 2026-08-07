package fr.raconteur.moc.web

data class DraftEntryRequest(val filePath: String, val optionPath: String, val mode: String)
data class RemoveEntryRequest(val filePath: String, val optionPath: String)
data class FinalizeRequest(val name: String)
data class DeletePatchesRequest(val names: List<String>)
data class StartRecompRequest(val startIdx: Int, val endIdx: Int, val isAmend: Boolean = false)
data class AddIgnoreRequest(val filePath: String, val optionPath: String, val kind: String, val targetValue: String? = null)
data class RemoveIgnoreRequest(val filePath: String, val optionPath: String, val kind: String)
data class RecompIgnoreRequest(val filePath: String, val optionPath: String)
data class RecompEntryRequest(
    val filePath: String,
    val optionPath: String,
    val mode: String? = null,
    val action: String? = null,
    val kind: String? = null
)
