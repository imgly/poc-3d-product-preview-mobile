import Foundation

/// The product definition a backend delivers per product (`products/<id>/product.json`).
struct Product: Decodable, Identifiable, Hashable {
  struct Model: Decodable, Hashable {
    let glb: String
    let obj: String
    let printMaterial: String
    let uvLayout: String
  }

  struct PrintArea: Decodable, Hashable {
    let widthInch: Float
    let heightInch: Float
    let dpi: Float
    let previewTextureWidthPx: Float
  }

  struct Frame: Decodable, Hashable {
    let x: Float
    let y: Float
    let width: Float
    let height: Float
  }

  struct Template: Decodable, Hashable {
    struct Image: Decodable, Hashable {
      let uri: String
      let frameInch: Frame
    }

    struct Text: Decodable, Hashable {
      let value: String
      let frameInch: Frame
      let colorHex: String
      let fontSizePt: Float
    }

    let backgroundColor: String
    let image: Image
    let text: Text
  }

  struct Viewer: Decodable, Hashable {
    let cameraDistanceM: Float
    let targetYM: Float
    let initialYawDeg: Float
    let autoRotateDegPerSec: Float
  }

  let id: String
  /// Position in the product picker (ascending).
  let order: Int
  let name: String
  let model: Model
  let printArea: PrintArea
  let template: Template
  let viewer: Viewer

  /// The folder that holds `product.json` and the model files.
  var folder: URL!

  private enum CodingKeys: String, CodingKey {
    case id, order, name, model, printArea, template, viewer
  }

  var objURL: URL { folder.appendingPathComponent(model.obj) }

  /// Loads every product folder bundled in `products/` (each with a `product.json`), sorted by `order`.
  /// A folder that cannot be read or decoded is skipped and logged.
  static func loadAll() -> [Product] {
    guard let root = Bundle.main.url(forResource: "products", withExtension: nil),
          let folders = try? FileManager.default.contentsOfDirectory(
            at: root, includingPropertiesForKeys: [.isDirectoryKey], options: [.skipsHiddenFiles],
          ) else {
      print("[Product] No products folder in the app bundle")
      return []
    }
    return folders.compactMap { folder in
      let json = folder.appendingPathComponent("product.json")
      guard FileManager.default.fileExists(atPath: json.path) else { return nil }
      do {
        var product = try JSONDecoder().decode(Product.self, from: Data(contentsOf: json))
        product.folder = folder
        return product
      } catch {
        print("[Product] Cannot load \(json.lastPathComponent) in \(folder.lastPathComponent): \(error)")
        return nil
      }
    }
    .sorted { $0.order < $1.order }
  }
}

/// The CE.SDK license. Put the key in `ios/ProductPreview/cesdk-license.txt` (git-ignored).
/// No key = evaluation mode with watermark.
enum License {
  static let key: String? = {
    guard let url = Bundle.main.url(forResource: "cesdk-license", withExtension: "txt"),
          let value = try? String(contentsOf: url, encoding: .utf8)
            .trimmingCharacters(in: .whitespacesAndNewlines),
          !value.isEmpty else { return nil }
    return value
  }()
}
