package ly.img.poc.productpreview

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ly.img.editor.Editor
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import ly.img.engine.Engine
import ly.img.editor.core.configuration.EditorConfiguration
import ly.img.editor.core.configuration.remember
import ly.img.poc.productpreview.editor.ProductConfigurationBuilder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Needed before the headless engine in ProductScene.renderDefault starts.
        Engine.init(application)
        enableEdgeToEdge()
        setContent {
            MaterialTheme { App() }
        }
    }
}

@Composable
private fun App() {
    val context = LocalContext.current
    val products = remember { Product.loadAll(context) }
    var selectedId by remember { mutableStateOf(products.firstOrNull()?.id) }
    // PoC simplification: designs live in memory only. A production app stores the scene string.
    val textures = remember { mutableStateMapOf<String, Bitmap>() }
    val scenes = remember { mutableMapOf<String, String>() }
    // One Filament engine for the whole app run, kept while the editor is open.
    val filamentEngine = rememberEngine()
    val modelLoader = rememberModelLoader(filamentEngine)
    val errors = remember { mutableStateMapOf<String, String>() }
    var editing by remember { mutableStateOf<Product?>(null) }
    var autoRotate by remember { mutableStateOf(true) }

    // First launch: render each product's default template headless, so the 3D model is not blank.
    LaunchedEffect(Unit) {
        products.forEach { product ->
            runCatching { ProductScene.renderDefault(product) }
                .onSuccess {
                    textures.putIfAbsent(product.id, it.texture)
                    scenes.putIfAbsent(product.id, it.sceneString)
                }
                .onFailure { errors[product.id] = it.toString() }
        }
    }

    val current = editing
    if (current != null) {
        Editor(
            license = cesdkLicense,
            userId = "product-preview-poc",
            configuration = {
                EditorConfiguration.remember(builderFactory = {
                    ProductConfigurationBuilder(current, scenes[current.id]) { result ->
                        textures[current.id] = result.texture
                        scenes[current.id] = result.sceneString
                    }
                })
            },
            onClose = { editing = null },
        )
        return
    }

    val product = products.firstOrNull { it.id == selectedId } ?: products.firstOrNull()
    if (product == null) {
        Text("No products found in assets/products.", modifier = Modifier.safeDrawingPadding().padding(16.dp))
        return
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF111111)) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                products.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = p.id == selectedId,
                        onClick = { selectedId = p.id },
                        shape = SegmentedButtonDefaults.itemShape(index, products.size),
                        colors = SegmentedButtonDefaults.colors(inactiveContentColor = Color.White),
                    ) { Text(p.name, maxLines = 1) }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF7F7F7), Color(0xFFE0E0E0)))),
            ) {
                ProductViewer(
                    engine = filamentEngine,
                    modelLoader = modelLoader,
                    product = product,
                    texture = textures[product.id],
                    autoRotate = autoRotate,
                    modifier = Modifier.fillMaxSize(),
                )
                if (textures[product.id] == null && errors[product.id] == null) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(24.dp))
                }
                FilledTonalButton(
                    onClick = { autoRotate = !autoRotate },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                ) { Text(if (autoRotate) "Pause" else "Rotate") }
            }
            Text(product.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "${product.printAreaLabel} · drag to rotate, pinch to zoom",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFAAAAAA),
                textAlign = TextAlign.Center,
            )
            errors[product.id]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF6666)) }
            Button(onClick = { editing = product }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Personalize")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
