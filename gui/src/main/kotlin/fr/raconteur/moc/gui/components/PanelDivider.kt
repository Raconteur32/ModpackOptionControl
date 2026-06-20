package fr.raconteur.moc.gui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun PanelDivider(
    horizontal: Boolean = false,
    onDrag: (Float) -> Unit
) {
    val orientation = if (horizontal) Orientation.Vertical else Orientation.Horizontal
    val cursor      = if (horizontal) Cursor.S_RESIZE_CURSOR else Cursor.E_RESIZE_CURSOR

    Box(
        modifier = Modifier
            .then(
                if (horizontal) Modifier.fillMaxWidth().height(5.dp)
                else Modifier.fillMaxHeight().width(5.dp)
            )
            .background(MaterialTheme.colors.onSurface.copy(alpha = 0.10f))
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { delta -> onDrag(delta) }
            )
    )
}
