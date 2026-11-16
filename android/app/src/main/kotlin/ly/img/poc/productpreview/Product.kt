package ly.img.poc.productpreview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.json.JSONObject

/** The product definition a backend delivers per product (`products/<id>/product.json`). */
data class Product(
    val id: String,
    /** Position in the product picker (ascending). */
    val order: Int,
    val name: String,
    val folder: String,
    val glb: String,
    val printMaterial: String,
    val printWidthInch: Float,
    val printHeightInch: Float,
    val dpi: Float,
    val previewTextureWidthPx: Float,
    val template: Template,
    val cameraDistanceM: Float,
    val targetYM: Float,
    val initialYawDeg: Float,
    val autoRotateDegPerSec: Float,
) {
    data class Frame(val x: Float, val y: Float, val width: Float, val height: Float)

    data class Template(
        val backgroundColor: String,
        val imageUri: String,
        val imageFrame: Frame,
        val text: String,
        val textFrame: Frame,
        val textColor: String,
        val fontSizePt: Float,
    )

    /** Asset path of the GLB model, as SceneView expects it. */
    val glbAssetPath get() = "$folder/$glb"

    val printAreaLabel get() = "Print area ${fmt(printWidthInch)} × ${fmt(printHeightInch)} in"

    companion object {
        private fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

        private fun JSONObject.frame() = Frame(
            getDouble("x").toFloat(), getDouble("y").toFloat(),
            getDouble("width").toFloat(), getDouble("height").toFloat(),
        )

        /**
         * Loads every product folder in `assets/products` (each with a `product.json`), sorted by `order`.
         * A folder that cannot be read or parsed is skipped and logged.
         */
        fun loadAll(context: Context): List<Product> =
            context.assets.list("products").orEmpty().mapNotNull { name ->
                val folder = "products/$name"
                runCatching { parse(context, folder) }
                    .onFailure { Log.e("Product", "Cannot load $folder/product.json", it) }
                    .getOrNull()
            }.sortedBy { it.order }

        private fun parse(context: Context, folder: String): Product {
            val json = JSONObject(context.assets.open("$folder/product.json").bufferedReader().readText())
            val model = json.getJSONObject("model")
            val area = json.getJSONObject("printArea")
            val template = json.getJSONObject("template")
            val image = template.getJSONObject("image")
            val text = template.getJSONObject("text")
            val viewer = json.getJSONObject("viewer")
            return Product(
                id = json.getString("id"),
                order = json.getInt("order"),
                name = json.getString("name"),
                folder = folder,
                glb = model.getString("glb"),
                printMaterial = model.getString("printMaterial"),
                printWidthInch = area.getDouble("widthInch").toFloat(),
                printHeightInch = area.getDouble("heightInch").toFloat(),
                dpi = area.getDouble("dpi").toFloat(),
                previewTextureWidthPx = area.getDouble("previewTextureWidthPx").toFloat(),
                template = Template(
                    backgroundColor = template.getString("backgroundColor"),
                    imageUri = image.getString("uri"),
                    imageFrame = image.getJSONObject("frameInch").frame(),
                    text = text.getString("value"),
                    textFrame = text.getJSONObject("frameInch").frame(),
                    textColor = text.getString("colorHex"),
                    fontSizePt = text.getDouble("fontSizePt").toFloat(),
                ),
                cameraDistanceM = viewer.getDouble("cameraDistanceM").toFloat(),
                targetYM = viewer.getDouble("targetYM").toFloat(),
                initialYawDeg = viewer.getDouble("initialYawDeg").toFloat(),
                autoRotateDegPerSec = viewer.getDouble("autoRotateDegPerSec").toFloat(),
            )
        }
    }
}

/** The result of one editing session: the texture for the 3D model and the CE.SDK scene. */
data class DesignResult(val texture: Bitmap, val sceneString: String)

/**
 * The CE.SDK license, from `CESDK_LICENSE` in the git-ignored `android/local.properties`.
 * Empty = evaluation mode with watermark.
 */
val cesdkLicense: String? get() = BuildConfig.CESDK_LICENSE.ifEmpty { null }
