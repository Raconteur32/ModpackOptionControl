package fr.raconteur.moc.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.raconteur.moc.filesystem.FileDiffKind

val colorNew     = Color(0xFF2E7D32)
val colorDeleted = Color(0xFFC62828)
val colorChanged = Color(0xFFF57F17)
val colorDraft   = Color(0xFF1565C0)

@Composable
fun KindPrefix(kind: FileDiffKind, modifier: Modifier = Modifier) {
    val (label, color) = when (kind) {
        FileDiffKind.NEW     -> "+ new"     to colorNew
        FileDiffKind.DELETED -> "- deleted" to colorDeleted
        FileDiffKind.CHANGED -> "~ changed" to colorChanged
    }
    Text(
        text = label,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = modifier
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun DraftBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = colorDraft,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        modifier = modifier
            .background(colorDraft.copy(alpha = 0.10f))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
