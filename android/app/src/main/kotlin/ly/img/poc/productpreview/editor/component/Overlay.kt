package ly.img.poc.productpreview.editor.component

import androidx.compose.runtime.Composable
import ly.img.poc.productpreview.editor.ProductConfigurationBuilder
import ly.img.editor.core.component.EditorComponent
import ly.img.editor.core.component.remember

/**
 * The configuration of the component that is displayed over the editor. It is useful if you want to display a popup dialog or anything in the
 * overlay.
 */
@Composable
fun ProductConfigurationBuilder.rememberOverlay() = EditorComponent.remember {
    decoration = { Overlay() }
}
