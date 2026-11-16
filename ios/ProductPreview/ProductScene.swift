import IMGLYEngine
import UIKit

/// Builds the CE.SDK design page for a product and exports it as the 3D texture.
///
/// The page is exactly the print area. The model's `print_area` UVs cover the full page,
/// so the exported PNG goes onto the model without any further mapping.
@MainActor
enum ProductScene {
  /// Builds the default template in code from `product.json`.
  /// PoC simplification: a production app loads a `.scene` file or scene archive from its backend.
  static func build(engine: Engine, product: Product) async throws {
    let area = product.printArea
    let template = product.template
    let scene = try engine.scene.create(designUnit: .in, fontSizeUnit: .pt, sceneLayout: .free)
    try engine.block.setFloat(scene, property: "scene/dpi", value: area.dpi)

    let page = try engine.block.create(.page)
    try engine.block.setWidth(page, value: area.widthInch)
    try engine.block.setHeight(page, value: area.heightInch)
    try engine.block.appendChild(to: scene, child: page)
    let bg = rgb(template.backgroundColor)
    try engine.block.setFillSolidColor(page, r: bg.r, g: bg.g, b: bg.b)

    let image = try engine.block.create(.graphic)
    try engine.block.setShape(image, shape: engine.block.createShape(.rect))
    let fill = try engine.block.createFill(.image)
    try engine.block.setString(fill, property: "fill/image/imageFileURI", value: template.image.uri)
    try engine.block.setFill(image, fill: fill)
    try engine.block.setKind(image, kind: "image")
    try place(engine, image, template.image.frameInch)
    try engine.block.setContentFillMode(image, mode: .cover)
    try engine.block.setName(image, name: "photo")
    try engine.block.appendChild(to: page, child: image)

    let text = try engine.block.create(.text)
    try engine.block.replaceText(text, text: template.text.value)
    try engine.block.setTextFontSize(text, fontSize: template.text.fontSizePt)
    let tc = rgb(template.text.colorHex)
    try engine.block.setTextColor(text, color: .rgba(r: tc.r, g: tc.g, b: tc.b, a: 1))
    try engine.block.setEnum(text, property: "text/horizontalAlignment", value: "Center")
    try engine.block.setEnum(text, property: "text/verticalAlignment", value: "Center")
    try place(engine, text, template.text.frameInch)
    try engine.block.setHeightMode(text, mode: .auto)
    try engine.block.setName(text, name: "headline")
    try engine.block.appendChild(to: page, child: text)

    for block in [image, text] {
      try enableEditing(engine, block)
    }
  }

  /// The editor runs in the "Adopter" role: each block must allow the edits the user may do.
  private static func enableEditing(_ engine: Engine, _ id: DesignBlockID) throws {
    let scopes = [
      "editor/select", "fill/change", "fill/changeType", "layer/crop", "layer/move", "layer/resize",
      "layer/rotate", "layer/flip", "layer/opacity", "layer/visibility", "lifecycle/destroy",
      "lifecycle/duplicate", "stroke/change", "shape/change", "text/edit", "text/character",
      "appearance/adjustments", "appearance/filter", "appearance/effect", "appearance/blur", "appearance/shadow",
    ]
    for scope in scopes {
      try engine.block.setScopeEnabled(id, key: scope, enabled: true)
    }
  }

  /// The page is the print area: the user cannot move, resize or delete it.
  static func lockPage(engine: Engine) throws {
    for page in try engine.scene.getPages() {
      for scope in ["layer/move", "layer/resize", "layer/rotate", "lifecycle/destroy", "lifecycle/duplicate"] {
        try engine.block.setScopeEnabled(page, key: scope, enabled: false)
      }
    }
  }

  static func exportTexture(engine: Engine, product: Product) async throws -> UIImage {
    guard let page = try engine.scene.getPages().first else { throw ProductSceneError.noPage }
    let width = product.printArea.previewTextureWidthPx
    let height = (width * product.printArea.heightInch / product.printArea.widthInch).rounded()
    let data = try await engine.block.export(
      page, mimeType: .png, options: ExportOptions(targetWidth: width, targetHeight: height),
    )
    guard let image = UIImage(data: data) else { throw ProductSceneError.badImage }
    return image
  }

  /// Renders the default template without UI (first launch, before any edit).
  static func renderDefault(product: Product) async throws -> DesignResult {
    let engine = try await Engine(
      context: .offscreen(size: CGSize(width: 64, height: 64)),
      license: License.key, userID: "product-preview-poc",
    )
    try await build(engine: engine, product: product)
    try lockPage(engine: engine)
    let texture = try await exportTexture(engine: engine, product: product)
    let sceneString = try await engine.scene.saveToString()
    return DesignResult(texture: texture, sceneString: sceneString)
  }

  private static func place(_ engine: Engine, _ id: DesignBlockID, _ frame: Product.Frame) throws {
    try engine.block.setPositionX(id, value: frame.x)
    try engine.block.setPositionY(id, value: frame.y)
    try engine.block.setWidth(id, value: frame.width)
    try engine.block.setHeight(id, value: frame.height)
  }

  private static func rgb(_ hex: String) -> (r: Float, g: Float, b: Float) {
    let value = UInt32(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0xFFFFFF
    return (Float((value >> 16) & 0xFF) / 255, Float((value >> 8) & 0xFF) / 255, Float(value & 0xFF) / 255)
  }
}

enum ProductSceneError: Error {
  case noPage
  case badImage
}
