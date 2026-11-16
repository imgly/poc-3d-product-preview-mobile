package ly.img.poc.productpreview

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.material.setBaseColorMap
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.texture.ImageTexture

/**
 * SceneView (Filament) viewer: loads the product GLB, puts the design texture on the
 * `print_area` material, and lets the user orbit (drag), zoom (pinch) and auto-rotate the model.
 *
 * [engine] and [modelLoader] come from the caller, so they outlive the viewer while the editor
 * is open or the product changes (no Filament restart).
 */
@Composable
fun ProductViewer(
    engine: Engine,
    modelLoader: ModelLoader,
    product: Product,
    texture: Bitmap?,
    autoRotate: Boolean,
    modifier: Modifier = Modifier,
) = key(product.id) {
    val modelInstance = rememberModelInstance(modelLoader, product.glbAssetPath)
    val uploaded = remember { mutableListOf<Texture>() }
    DisposableEffect(Unit) {
        onDispose {
            uploaded.forEach { engine.destroyTexture(it) }
            uploaded.clear()
        }
    }

    var yaw by remember { mutableFloatStateOf(product.initialYawDeg) }
    LaunchedEffect(autoRotate) {
        if (!autoRotate) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) yaw = (yaw + (now - last) / 1e9f * product.autoRotateDegPerSec) % 360f
                last = now
            }
        }
    }

    // Upload the design as a Filament texture and bind it to the print material.
    LaunchedEffect(modelInstance, texture) {
        val instance = modelInstance ?: return@LaunchedEffect
        val bitmap = texture ?: return@LaunchedEffect
        val materials = instance.materialInstances.filter { it.name == product.printMaterial }
        if (materials.isEmpty()) {
            Log.w("ProductViewer", "No material '${product.printMaterial}' in ${instance.materialInstances.map { it.name }}")
            return@LaunchedEffect
        }
        val newTexture = ImageTexture.Builder().bitmap(bitmap).build(engine)
        val sampler = TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
        materials.forEach { it.setBaseColorMap(newTexture, sampler) }
        uploaded.forEach { engine.destroyTexture(it) }
        uploaded.clear()
        uploaded += newTexture
    }

    val target = Position(0f, product.targetYM, 0f)
    val eye = Position(0f, product.targetYM + product.cameraDistanceM * 0.25f, product.cameraDistanceM)
    val cameraManipulator = rememberCameraManipulator(orbitHomePosition = eye, targetPosition = target)

    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        isOpaque = false,
        engine = engine,
        modelLoader = modelLoader,
        cameraManipulator = cameraManipulator,
        autoCenterContent = false,
        frameRatePolicy = FrameRatePolicy.Continuous(),
    ) {
        modelInstance?.let { ModelNode(modelInstance = it, rotation = Rotation(y = yaw)) }
    }
}
