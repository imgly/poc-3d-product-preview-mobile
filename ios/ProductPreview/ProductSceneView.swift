import SceneKit
import SwiftUI

/// SceneKit viewer: loads the product model, puts the design texture on the `print_area`
/// material, and lets the user orbit (drag), zoom (pinch) and auto-rotate the model.
struct ProductSceneView: UIViewRepresentable {
  let product: Product
  let texture: UIImage?
  let autoRotate: Bool

  func makeCoordinator() -> Coordinator { Coordinator() }

  func makeUIView(context: Context) -> SCNView {
    let view = SCNView()
    view.backgroundColor = .clear
    view.antialiasingMode = .multisampling4X
    view.allowsCameraControl = true
    view.defaultCameraController.interactionMode = .orbitTurntable
    view.defaultCameraController.inertiaEnabled = true
    view.defaultCameraController.maximumVerticalAngle = 80
    view.defaultCameraController.minimumVerticalAngle = -30
    view.accessibilityIdentifier = "product3DView"
    context.coordinator.view = view
    context.coordinator.show(product)
    return view
  }

  func updateUIView(_ view: SCNView, context: Context) {
    let coordinator = context.coordinator
    if coordinator.productID != product.id { coordinator.show(product) }
    coordinator.apply(texture: texture)
    coordinator.setAutoRotate(autoRotate, speedDeg: product.viewer.autoRotateDegPerSec)
  }

  @MainActor
  final class Coordinator {
    weak var view: SCNView?
    var productID: String?
    private var modelNode = SCNNode()
    private var printMaterials: [SCNMaterial] = []
    private var texture: UIImage?
    private var rotating = false

    func show(_ product: Product) {
      guard let view else { return }
      productID = product.id
      texture = nil
      rotating = false

      let scene = SCNScene()
      scene.lightingEnvironment.contents = Self.environmentImage()
      scene.lightingEnvironment.intensity = 1.4

      let model: SCNNode
      do {
        model = try SCNScene(url: product.objURL).rootNode
      } catch {
        print("[ProductSceneView] Cannot load \(product.objURL.lastPathComponent): \(error)")
        model = SCNNode()
      }
      modelNode = SCNNode()
      model.childNodes.forEach { modelNode.addChildNode($0) }
      printMaterials = []
      modelNode.enumerateHierarchy { node, _ in
        node.geometry?.materials.forEach { material in
          material.lightingModel = .physicallyBased
          material.metalness.contents = 0.0
          if material.name == product.model.printMaterial {
            material.roughness.contents = 0.3
            material.diffuse.contents = UIColor.white
            material.diffuse.wrapS = .clamp
            material.diffuse.wrapT = .clamp
            material.diffuse.mipFilter = .linear
            self.printMaterials.append(material)
          } else {
            material.roughness.contents = 0.3
          }
        }
      }
      if printMaterials.isEmpty {
        var names: [String] = []
        modelNode.enumerateHierarchy { node, _ in names += node.geometry?.materials.compactMap(\.name) ?? [] }
        print("[ProductSceneView] No material '\(product.model.printMaterial)' in \(names)")
      }
      modelNode.eulerAngles.y = product.viewer.initialYawDeg * .pi / 180
      scene.rootNode.addChildNode(modelNode)

      // Key light with a soft shadow on an invisible floor.
      let light = SCNNode()
      light.light = SCNLight()
      light.light?.type = .directional
      light.light?.intensity = 900
      light.light?.castsShadow = true
      light.light?.shadowMode = .deferred
      light.light?.shadowRadius = 8
      light.light?.shadowSampleCount = 16
      light.light?.shadowColor = UIColor.black.withAlphaComponent(0.35)
      light.eulerAngles = SCNVector3(-Float.pi / 2.6, Float.pi / 5, 0)
      scene.rootNode.addChildNode(light)

      let floor = SCNNode(geometry: SCNFloor())
      (floor.geometry as? SCNFloor)?.reflectivity = 0
      floor.geometry?.firstMaterial?.lightingModel = .shadowOnly
      floor.position.y = (modelNode.boundingBox.min.y)
      scene.rootNode.addChildNode(floor)

      let camera = SCNNode()
      camera.camera = SCNCamera()
      camera.camera?.fieldOfView = 35
      camera.camera?.zNear = 0.01
      camera.camera?.wantsHDR = false
      let target = SCNVector3(0, product.viewer.targetYM, 0)
      camera.position = SCNVector3(0, target.y + product.viewer.cameraDistanceM * 0.25, product.viewer.cameraDistanceM)
      camera.look(at: target)
      scene.rootNode.addChildNode(camera)

      view.scene = scene
      view.pointOfView = camera
      view.defaultCameraController.target = target
    }

    func apply(texture newTexture: UIImage?) {
      guard newTexture !== texture else { return }
      texture = newTexture
      printMaterials.forEach { $0.diffuse.contents = newTexture ?? UIColor.white }
    }

    func setAutoRotate(_ on: Bool, speedDeg: Float) {
      guard on != rotating else { return }
      rotating = on
      if on {
        let seconds = TimeInterval(360 / max(speedDeg, 1))
        modelNode.runAction(.repeatForever(.rotateBy(x: 0, y: .pi * 2, z: 0, duration: seconds)), forKey: "spin")
      } else {
        modelNode.removeAction(forKey: "spin")
      }
    }

    /// A simple studio environment (bright top, soft horizon) for the PBR reflections.
    private static func environmentImage() -> UIImage {
      let size = CGSize(width: 256, height: 128)
      return UIGraphicsImageRenderer(size: size).image { ctx in
        let colors = [UIColor(white: 1.0, alpha: 1).cgColor, UIColor(white: 0.75, alpha: 1).cgColor,
                      UIColor(white: 0.35, alpha: 1).cgColor]
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray,
                                  locations: [0, 0.5, 1])!
        ctx.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [])
      }
    }
  }
}
