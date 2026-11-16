package ly.img.poc.productpreview.editor

import androidx.compose.runtime.Stable
import ly.img.editor.BasicConfigurationBuilder
import ly.img.poc.productpreview.DesignResult
import ly.img.poc.productpreview.Product
import ly.img.poc.productpreview.editor.callback.onCreate
import ly.img.poc.productpreview.editor.callback.onExport
import ly.img.poc.productpreview.editor.callback.onLoaded
import ly.img.poc.productpreview.editor.component.rememberCanvasMenu
import ly.img.poc.productpreview.editor.component.rememberDock
import ly.img.poc.productpreview.editor.component.rememberInspectorBar
import ly.img.poc.productpreview.editor.component.rememberNavigationBar
import ly.img.poc.productpreview.editor.component.rememberOverlay
import ly.img.editor.core.EditorScope
import ly.img.editor.core.ScopedProperty
import ly.img.editor.core.component.EditorComponent
import ly.img.editor.core.event.EditorEvent

/**
 * Design Editor configuration for one product. Based on the CE.SDK Design Editor starter kit
 * v1.86.2 (MIT, see android/LICENSE-starterkit.txt).
 *
 * The editor opens one page sized to the product's print area ([savedScene] when the user edits
 * again). "Preview 3D" exports the page as PNG, hands it to [onDone] and closes the editor.
 */
@Stable
class ProductConfigurationBuilder(
    val product: Product,
    val savedScene: String?,
    val onDone: (DesignResult) -> Unit,
) : BasicConfigurationBuilder() {
    /**
     * The callback that is invoked when the editor is created.
     * Check parent class declaration for more details.
     */
    override var onCreate: (suspend EditorScope.() -> Unit)? = {
        onCreate()
    }

    /**
     * The callback that is invoked when the editor is loaded and ready to be used.
     * Check parent class declaration for more details.
     */
    override var onLoaded: (suspend EditorScope.() -> Unit)? = {
        onLoaded()
    }

    /**
     * The callback that is invoked when the export button is clicked.
     * Check parent class declaration for more details.
     */
    override var onExport: (suspend EditorScope.() -> Unit)? = {
        onExport()
    }

    /**
     * The callback that is invoked after [EditorEvent.OnClose] event is triggered or when the system back button is clicked
     * and editor cannot handle the event internally.
     * Check parent class declaration for more details.
     */
    override var onClose: (suspend EditorScope.() -> Unit)? = {
        showConfirmationOrCloseEditor()
    }

    /**
     * The callback that is invoked after the editor captures an error.
     * Check parent class declaration for more details.
     */
    override var onError: (suspend EditorScope.(Throwable) -> Unit)? = {
        error = it
    }

    /**
     * The configuration of the component that is displayed as horizontal list of items at the bottom of the editor.
     * Check parent class declaration for more details.
     */
    override var dock: ScopedProperty<EditorScope, EditorComponent<*>?>? = {
        rememberDock()
    }

    /**
     * The configuration of the component that is displayed as horizontal list of items at the top of the editor.
     * Check parent class declaration for more details.
     */
    override var navigationBar: ScopedProperty<EditorScope, EditorComponent<*>?>? = {
        rememberNavigationBar()
    }

    /**
     * The configuration of the component that is displayed as horizontal list of items at the
     * bottom of the editor when a design block is selected.
     * Check parent class declaration for more details.
     */
    override var inspectorBar: ScopedProperty<EditorScope, EditorComponent<*>?>? = {
        rememberInspectorBar()
    }

    /**
     * The configuration of the component that is displayed as horizontal list of items next to
     * the selected design block.
     * Check parent class declaration for more details.
     */
    override var canvasMenu: ScopedProperty<EditorScope, EditorComponent<*>?>? = {
        rememberCanvasMenu()
    }

    /**
     * The configuration of the component that is displayed over the editor. It is useful if you want to display a popup dialog or anything in the
     * overlay.
     * Check parent class declaration for more details.
     */
    override var overlay: ScopedProperty<EditorScope, EditorComponent<*>?>? = {
        rememberOverlay()
    }
}
