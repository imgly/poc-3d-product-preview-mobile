import IMGLYEditor
import IMGLYEngine
import SwiftUI

// Based on the CE.SDK Design Editor starter kit v1.86.2 (MIT, see ios/LICENSE-starterkit.txt).
// The editor opens one page sized to the product's print area. "Preview 3D" exports
// the page as PNG, hands it to the 3D viewer and closes the editor.

/// The result of one editing session.
struct DesignResult {
  let texture: UIImage
  let sceneString: String
}

/// Design Editor configuration for one product.
final class ProductEditorConfiguration: EditorConfiguration {
  let product: Product
  let savedScene: String?
  let onDone: @MainActor (DesignResult) -> Void

  init(product: Product, savedScene: String?, onDone: @escaping @MainActor (DesignResult) -> Void) {
    self.product = product
    self.savedScene = savedScene
    self.onDone = onDone
    super.init()
  }

  // MARK: - EditorConfiguration — Settings

  override var zoomPadding: CGFloat? {
    16
  }

  // MARK: - EditorConfiguration — Callbacks

  override var onCreate: OnCreate.Handler? {
    let product = product
    let savedScene = savedScene
    return { engine, _ in
      try await Self.defaultOnCreate(createScene: { engine in
        if let savedScene {
          try await engine.scene.load(from: savedScene)
        } else {
          try await ProductScene.build(engine: engine, product: product)
        }
        try ProductScene.lockPage(engine: engine)
      })(engine)
    }
  }

  override var onExport: OnExport.Handler? {
    let product = product
    let onDone = onDone
    return { engine, eventHandler, _ in
      let texture = try await ProductScene.exportTexture(engine: engine, product: product)
      let sceneString = try await engine.scene.saveToString()
      onDone(DesignResult(texture: texture, sceneString: sceneString))
      eventHandler.send(.closeEditor)
    }
  }

  // MARK: - EditorConfiguration — UI Components

  override var navigationBar: NavigationBar.Configuration? {
    Self.defaultNavigationBar
  }

  override var dock: Dock.Configuration? {
    Self.defaultDock
  }

  override var inspectorBar: InspectorBar.Configuration? {
    Self.defaultInspectorBar
  }

  override var canvasMenu: CanvasMenu.Configuration? {
    Self.defaultCanvasMenu
  }
}
