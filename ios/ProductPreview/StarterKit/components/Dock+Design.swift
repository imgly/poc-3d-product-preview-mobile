import IMGLYEditor

// MARK: - Dock

extension ProductEditorConfiguration {
  /// The dock: add photos, text, shapes and stickers. No page resize: the page is the print area.
  static var defaultDock: Dock.Configuration {
    Dock.Configuration { builder in
      builder.items { _ in
        Dock.Buttons.photoRoll()
        Dock.Buttons.imagesLibrary()
        Dock.Buttons.textLibrary()
        Dock.Buttons.shapesLibrary()
        Dock.Buttons.stickersLibrary()
      }
    }
  }
}
