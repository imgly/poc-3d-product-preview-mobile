import IMGLYEditor
import SwiftUI

/// Keeps the current design (texture + CE.SDK scene) per product for this app run.
/// PoC simplification: in memory only. A production app stores the scene string (per user and product).
@MainActor
final class DesignStore: ObservableObject {
  @Published var textures: [String: UIImage] = [:]
  @Published var errors: [String: String] = [:]
  var scenes: [String: String] = [:]

  func renderDefaults(_ products: [Product]) async {
    for product in products where textures[product.id] == nil {
      do {
        let result = try await ProductScene.renderDefault(product: product)
        // The user may have edited this product while the default rendered: keep the user's design.
        if textures[product.id] == nil {
          save(result, for: product)
        }
      } catch {
        errors[product.id] = "\(error)"
      }
    }
  }

  func save(_ result: DesignResult, for product: Product) {
    textures[product.id] = result.texture
    scenes[product.id] = result.sceneString
  }
}

struct ContentView: View {
  private let products = Product.loadAll()
  @StateObject private var store = DesignStore()
  @State private var selectedID: String?
  @State private var autoRotate = true
  @State private var editing: Product?

  private var product: Product? { products.first { $0.id == selectedID } ?? products.first }

  private var selection: Binding<String> {
    Binding(get: { product?.id ?? "" }, set: { selectedID = $0 })
  }

  var body: some View {
    VStack(spacing: 16) {
      Picker("Product", selection: selection) {
        ForEach(products) { Text($0.name).tag($0.id) }
      }
      .pickerStyle(.segmented)
      .padding(.horizontal)

      if let product {
        ZStack(alignment: .bottomTrailing) {
          ProductSceneView(product: product, texture: store.textures[product.id], autoRotate: autoRotate)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
          Button {
            autoRotate.toggle()
          } label: {
            Image(systemName: autoRotate ? "pause.circle.fill" : "rotate.3d.circle.fill")
              .font(.system(size: 34))
              .symbolRenderingMode(.hierarchical)
          }
          .accessibilityIdentifier("autoRotateButton")
          .padding()
        }
        .background(
          LinearGradient(colors: [Color(white: 0.97), Color(white: 0.88)], startPoint: .top, endPoint: .bottom),
        )
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .overlay(alignment: .top) {
          if store.textures[product.id] == nil {
            ProgressView("Rendering design…").padding(.top, 24)
          }
        }
        .padding(.horizontal)

        VStack(spacing: 4) {
          Text(product.name).font(.headline)
          Text("\(product.printArea.printAreaLabel) · drag to rotate, pinch to zoom")
            .font(.footnote)
            .foregroundStyle(.secondary)
          if let error = store.errors[product.id] {
            Text(error).font(.caption2).foregroundStyle(.red)
          }
        }

        Button {
          editing = product
        } label: {
          Label("Personalize", systemImage: "paintbrush.pointed")
            .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .accessibilityIdentifier("personalizeButton")
        .padding(.horizontal)
      }
    }
    .padding(.vertical)
    .task { await store.renderDefaults(products) }
    .fullScreenCover(item: $editing) { product in
      NavigationStack {
        Editor(EngineSettings(license: License.key, userID: "product-preview-poc"))
          .imgly.configuration {
            ProductEditorConfiguration(product: product, savedScene: store.scenes[product.id]) { result in
              store.save(result, for: product)
            }
          }
      }
    }
  }
}

extension Product.PrintArea {
  var printAreaLabel: String {
    let f = { (v: Float) in v == v.rounded() ? String(Int(v)) : String(format: "%.1f", v) }
    return "Print area \(f(widthInch)) × \(f(heightInch)) in"
  }
}
