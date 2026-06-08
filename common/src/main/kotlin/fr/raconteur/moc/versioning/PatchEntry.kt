package fr.raconteur.moc.versioning

import com.google.gson.annotations.SerializedName

data class PatchEntry(
    @SerializedName("file_path")   val filePath: String,
    @SerializedName("option_path") val optionPath: String,
    @SerializedName("from_value")  val fromValue: Any?,
    @SerializedName("to_value")    val toValue: Any?,
    val kind: EntryKind,
    val mode: PatchMode
)
