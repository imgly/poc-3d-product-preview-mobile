@file:Suppress("UnusedReceiverParameter")

package ly.img.poc.productpreview.editor.component

import androidx.compose.runtime.Composable
import ly.img.poc.productpreview.editor.ProductConfigurationBuilder
import ly.img.editor.core.component.Dock
import ly.img.editor.core.component.remember
import ly.img.editor.core.component.rememberImagesLibrary
import ly.img.editor.core.component.rememberShapesLibrary
import ly.img.editor.core.component.rememberStickersLibrary
import ly.img.editor.core.component.rememberSystemGallery
import ly.img.editor.core.component.rememberTextLibrary

/**
 * The configuration of the component that is displayed as horizontal list of items at the bottom of the editor.
 */
@Composable
fun ProductConfigurationBuilder.rememberDock() = Dock.remember {
    listBuilder = {
        Dock.ListBuilder.remember {
            add { Dock.Button.rememberSystemGallery() }
            add { Dock.Button.rememberImagesLibrary() }
            add { Dock.Button.rememberTextLibrary() }
            add { Dock.Button.rememberShapesLibrary() }
            add { Dock.Button.rememberStickersLibrary() }
        }
    }
}
