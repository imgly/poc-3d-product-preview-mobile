package ly.img.poc.productpreview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import ly.img.engine.Color
import ly.img.engine.ContentFillMode
import ly.img.engine.DesignBlock
import ly.img.engine.DesignBlockType
import ly.img.engine.DesignUnit
import ly.img.engine.Engine
import ly.img.engine.ExportOptions
import ly.img.engine.FillType
import ly.img.engine.FontUnit
import ly.img.engine.MimeType
import ly.img.engine.ShapeType
import ly.img.engine.SizeMode
import java.nio.ByteBuffer

/**
 * Builds the CE.SDK design page for a product and exports it as the 3D texture.
 *
 * The page is exactly the print area. The model's `print_area` UVs cover the full page,
 * so the exported PNG goes onto the model without any further mapping.
 */
object ProductScene {
    /**
     * Builds the default template in code from `product.json`.
     * PoC simplification: a production app loads a `.scene` file or scene archive from its backend.
     */
    fun build(engine: Engine, product: Product) {
        val t = product.template
        val scene = engine.scene.create(designUnit = DesignUnit.INCH, fontSizeUnit = FontUnit.POINT)
        engine.block.setFloat(scene, "scene/dpi", product.dpi)

        val page = engine.block.create(DesignBlockType.Page)
        engine.block.setWidth(page, product.printWidthInch)
        engine.block.setHeight(page, product.printHeightInch)
        engine.block.appendChild(parent = scene, child = page)
        engine.block.setFillSolidColor(page, Color.fromHex(t.backgroundColor))

        val image = engine.block.create(DesignBlockType.Graphic)
        engine.block.setShape(image, engine.block.createShape(ShapeType.Rect))
        val fill = engine.block.createFill(FillType.Image)
        engine.block.setString(fill, "fill/image/imageFileURI", t.imageUri)
        engine.block.setFill(image, fill)
        engine.block.setKind(image, "image")
        place(engine, image, t.imageFrame)
        engine.block.setContentFillMode(image, ContentFillMode.COVER)
        engine.block.setName(image, "photo")
        engine.block.appendChild(parent = page, child = image)

        val text = engine.block.create(DesignBlockType.Text)
        engine.block.replaceText(text, t.text)
        engine.block.setTextFontSize(text, t.fontSizePt)
        engine.block.setTextColor(text, Color.fromHex(t.textColor))
        engine.block.setEnum(text, "text/horizontalAlignment", "Center")
        engine.block.setEnum(text, "text/verticalAlignment", "Center")
        place(engine, text, t.textFrame)
        engine.block.setHeightMode(text, SizeMode.AUTO)
        engine.block.setName(text, "headline")
        engine.block.appendChild(parent = page, child = text)

        listOf(image, text).forEach { enableEditing(engine, it) }
    }

    /** The page is the print area: the user cannot move, resize or delete it. */
    fun lockPage(engine: Engine) {
        engine.scene.getPages().forEach { page ->
            listOf("layer/move", "layer/resize", "layer/rotate", "lifecycle/destroy", "lifecycle/duplicate")
                .forEach { engine.block.setScopeEnabled(page, it, false) }
        }
    }

    suspend fun exportTexture(engine: Engine, product: Product): ByteBuffer {
        val page = engine.scene.getPages().first()
        val width = product.previewTextureWidthPx
        val height = Math.round(width * product.printHeightInch / product.printWidthInch).toFloat()
        return engine.block.export(
            block = page,
            mimeType = MimeType.PNG,
            options = ExportOptions(targetWidth = width, targetHeight = height),
        )
    }

    fun toBitmap(buffer: ByteBuffer): Bitmap {
        val bytes = ByteArray(buffer.remaining()).also { buffer.duplicate().get(it) }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /** Renders the default template without UI (first launch, before any edit). */
    suspend fun renderDefault(product: Product): DesignResult {
        val engine = Engine.getInstance(id = "product-preview-headless")
        try {
            engine.start(license = cesdkLicense, userId = "product-preview-poc")
            engine.bindOffscreen(width = 64, height = 64)
            build(engine, product)
            lockPage(engine)
            val texture = toBitmap(exportTexture(engine, product))
            return DesignResult(texture, engine.scene.saveToString(scene = requireNotNull(engine.scene.get())))
        } finally {
            engine.stop()
        }
    }

    /** The editor runs in the "Adopter" role: each block must allow the edits the user may do. */
    private fun enableEditing(engine: Engine, block: DesignBlock) {
        listOf(
            "editor/select", "fill/change", "fill/changeType", "layer/crop", "layer/move", "layer/resize",
            "layer/rotate", "layer/flip", "layer/opacity", "layer/visibility", "lifecycle/destroy",
            "lifecycle/duplicate", "stroke/change", "shape/change", "text/edit", "text/character",
            "appearance/adjustments", "appearance/filter", "appearance/effect", "appearance/blur", "appearance/shadow",
        ).forEach { engine.block.setScopeEnabled(block, it, true) }
    }

    private fun place(engine: Engine, block: DesignBlock, frame: Product.Frame) {
        engine.block.setPositionX(block, frame.x)
        engine.block.setPositionY(block, frame.y)
        engine.block.setWidth(block, frame.width)
        engine.block.setHeight(block, frame.height)
    }
}
