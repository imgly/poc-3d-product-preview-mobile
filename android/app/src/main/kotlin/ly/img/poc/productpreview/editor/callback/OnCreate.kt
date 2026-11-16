package ly.img.poc.productpreview.editor.callback

import androidx.core.net.toUri
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ly.img.poc.productpreview.ProductScene
import ly.img.poc.productpreview.editor.ProductConfigurationBuilder
import ly.img.editor.core.library.data.AssetSourceType
import ly.img.editor.core.library.data.SystemGalleryAssetSource
import ly.img.editor.core.library.data.SystemGalleryPermission
import ly.img.engine.DesignBlockType

/**
 * The callback that is invoked when the editor is created.
 */
suspend fun ProductConfigurationBuilder.onCreate(
    preCreateScene: suspend ProductConfigurationBuilder.() -> Unit = {
        onPreCreateScene()
    },
    createScene: suspend ProductConfigurationBuilder.() -> Unit = {
        onCreateScene()
    },
    loadAssetSources: suspend ProductConfigurationBuilder.() -> Unit = {
        onLoadAssetSources()
    },
    postCreateScene: suspend ProductConfigurationBuilder.() -> Unit = {
        onPostCreateScene()
    },
    finally: suspend ProductConfigurationBuilder.() -> Unit = {
        onCreateFinally()
    },
) {
    try {
        preCreateScene()
        createScene()
        loadAssetSources()
        postCreateScene()
    } finally {
        finally()
    }
}

/**
 * The callback that is invoked before the scene is created.
 */
fun ProductConfigurationBuilder.onPreCreateScene() {
    showLoading = true
    // Enable horizontal sliding between pages
    editorContext.engine.editor.setSettingBoolean(
        keypath = "features/pageCarouselEnabled",
        value = true,
    )
    // Press and hold an image, then drag it onto another image to exchange the two.
    editorContext.engine.editor.setSettingBoolean(keypath = "dragToSwapFills/enabled", value = true)
}

/**
 * The callback that is responsible for creating the scene.
 */
suspend fun ProductConfigurationBuilder.onCreateScene() {
    val engine = editorContext.engine
    if (engine.scene.get() == null) {
        val saved = savedScene
        if (saved != null) {
            engine.scene.load(scene = saved)
        } else {
            ProductScene.build(engine, product)
        }
    }
    ProductScene.lockPage(engine)
}

/**
 * The callback that loads all the required assets sources.
 */
suspend fun ProductConfigurationBuilder.onLoadAssetSources() {
    // Load asset sources in parallel from content.json files
    coroutineScope {
        val baseUri = editorContext.baseUri
        val sourceIds = listOf(
            "ly.img.sticker",
            "ly.img.vector.shape",
            "ly.img.filter",
            "ly.img.color.palette",
            "ly.img.effect",
            "ly.img.blur",
            "ly.img.typeface",
            "ly.img.crop.presets",
            "ly.img.page.presets",
            "ly.img.text",
            "ly.img.text.styles",
            "ly.img.text.curves",
            "ly.img.text.components",
            "ly.img.image",
        )
        sourceIds.forEach { id ->
            launch {
                editorContext.engine.asset.addLocalSourceFromJSON(
                    contentUri = "$baseUri/$id/content.json".toUri(),
                )
            }
        }
    }

    // Load local asset sources
    editorContext.engine.asset.addLocalSource(
        sourceId = "ly.img.image.upload",
        supportedMimeTypes = listOf(
            "image/jpeg",
            "image/png",
            "image/heic",
            "image/heif",
            "image/svg+xml",
            "image/gif",
            "image/apng",
            "image/bmp",
        ),
    )

    // Register gallery asset sources
    listOf(
        AssetSourceType.GalleryAllVisuals,
        AssetSourceType.GalleryImage,
        AssetSourceType.GalleryVideo,
    ).forEach { type ->
        editorContext.engine.asset.addSource(
            source = SystemGalleryAssetSource(
                context = editorContext.engine.applicationContext,
                type = type,
            ),
        )
    }
    SystemGalleryPermission.setMode(systemGalleryConfiguration)
}

/**
 * The callback that is invoked right after [onCreateScene], after the scene is created.
 */
fun ProductConfigurationBuilder.onPostCreateScene() {
    editorContext.engine.block
        .findByType(DesignBlockType.Stack)
        .firstOrNull()
        ?.let {
            // Display all pages in a horizontal stack.
            editorContext.engine.block.setEnum(
                block = it,
                property = "stack/axis",
                value = "Horizontal",
            )
        }
}

/**
 * The callback that is invoked as the last step of [onCreate].
 * It always runs, no matter success or failure on previous steps.
 */
fun ProductConfigurationBuilder.onCreateFinally() {
    showLoading = false
}
