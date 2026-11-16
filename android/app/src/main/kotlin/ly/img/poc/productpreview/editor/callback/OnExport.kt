package ly.img.poc.productpreview.editor.callback

import kotlinx.coroutines.CancellationException
import ly.img.poc.productpreview.editor.ProductConfigurationBuilder
import ly.img.editor.core.event.EditorEvent
import ly.img.poc.productpreview.DesignResult
import ly.img.poc.productpreview.ProductScene
import java.nio.ByteBuffer

/**
 * The callback that is invoked when the export button is clicked.
 */
suspend fun ProductConfigurationBuilder.onExport(
    preExport: suspend ProductConfigurationBuilder.() -> Unit = {
        onPreExport()
    },
    exportByteBuffer: suspend ProductConfigurationBuilder.() -> ByteBuffer = {
        onExportByteBuffer()
    },
    postExport: suspend ProductConfigurationBuilder.(ByteBuffer) -> Unit = {
        onPostExport(it)
    },
    error: suspend ProductConfigurationBuilder.(Exception) -> Unit = {
        onExportError(it)
    },
    finally: suspend ProductConfigurationBuilder.() -> Unit = {
        onExportFinally()
    },
) {
    try {
        preExport()
        val result = exportByteBuffer()
        postExport(result)
    } catch (exception: Exception) {
        error(exception)
    } finally {
        finally()
    }
}

/**
 * The callback that is invoked before the export is started.
 */
fun ProductConfigurationBuilder.onPreExport() {
    showLoading = true
}

/**
 * Exports the page (= print area) as PNG at the preview texture size.
 */
suspend fun ProductConfigurationBuilder.onExportByteBuffer(): ByteBuffer =
    ProductScene.exportTexture(editorContext.engine, product)

/**
 * Hands the texture and the scene to the 3D preview and closes the editor.
 */
suspend fun ProductConfigurationBuilder.onPostExport(byteBuffer: ByteBuffer) {
    val sceneString = editorContext.engine.scene.saveToString(scene = requireNotNull(editorContext.engine.scene.get()))
    onDone(DesignResult(ProductScene.toBitmap(byteBuffer), sceneString))
    editorContext.eventHandler.send(EditorEvent.CloseEditor())
}

/**
 * The callback that is invoked in case any of the export functions throw an exception.
 */
fun ProductConfigurationBuilder.onExportError(error: Exception) {
    if (error is CancellationException) {
        throw error
    }
    this.error = error
}

/**
 * The callback that is invoked as the last step of [onExportByteBuffer].
 * It always runs, no matter success or failure on previous steps.
 */
fun ProductConfigurationBuilder.onExportFinally() {
    showLoading = false
}
