import java.util.Properties
import javax.inject.Inject
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CE.SDK license from the git-ignored local.properties (CESDK_LICENSE=...). Empty = evaluation mode.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val cesdkLicense = localProps.getProperty("CESDK_LICENSE", "")

android {
    namespace = "ly.img.poc.productpreview"
    compileSdk = 37

    defaultConfig {
        applicationId = "ly.img.poc.productpreview"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "CESDK_LICENSE", "\"$cesdkLicense\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // GLB files must stay uncompressed so Filament can read them from assets.
        noCompress += listOf("glb")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Copies ../products (shared with iOS) into the app assets as assets/products/<id>/...
abstract class SyncProductAssets : DefaultTask() {
    @get:InputDirectory
    abstract val source: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Inject
    abstract val fs: FileSystemOperations

    @TaskAction
    fun sync() {
        fs.sync {
            from(source)
            into(outputDir.dir("products"))
        }
    }
}

val syncProductAssets = tasks.register<SyncProductAssets>("syncProductAssets") {
    source.set(rootProject.layout.projectDirectory.dir("../products"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(syncProductAssets, SyncProductAssets::outputDir)
    }
}

dependencies {
    implementation("ly.img:editor:1.86.2")
    implementation("io.github.sceneview:sceneview:4.69.5")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.material3:material3")
}
