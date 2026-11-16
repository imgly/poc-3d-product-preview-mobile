package ly.img.poc.productpreview.editor.callback

import ly.img.poc.productpreview.editor.ProductConfigurationBuilder

/**
 * The callback that is invoked when the editor is loaded and ready to be used.
 */
suspend fun ProductConfigurationBuilder.onLoaded() {
    observeEditorEditMode()
}
