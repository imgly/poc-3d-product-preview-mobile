import IMGLYEditor
import SwiftUI

// MARK: - Navigation Bar

extension ProductEditorConfiguration {
  /// Leading: close, undo, redo. Trailing: "3D" (runs the export handler and returns to the 3D view).
  ///
  /// The labels are short and their text size is capped, so the bar fits at large text sizes and
  /// with Display Zoom. If a toolbar group does not fit, iOS collapses it into a "…" menu.
  static var defaultNavigationBar: NavigationBar.Configuration {
    NavigationBar.Configuration { builder in
      builder.items { _ in
        NavigationBar.ItemGroup(placement: .topBarLeading) {
          NavigationBar.Buttons.closeEditor(label: { _ in
            Image(systemName: "chevron.backward")
              .accessibilityLabel("Back")
              .dynamicTypeSize(...DynamicTypeSize.large)
          })
          NavigationBar.Buttons.undo()
          NavigationBar.Buttons.redo()
        }
        NavigationBar.ItemGroup(placement: .topBarTrailing) {
          NavigationBar.Buttons.export(label: { _ in
            Label("3D", systemImage: "rotate.3d")
              .labelStyle(.titleAndIcon)
              .fontWeight(.semibold)
              .accessibilityLabel("Preview 3D")
              .dynamicTypeSize(...DynamicTypeSize.large)
          })
        }
      }
    }
  }
}
