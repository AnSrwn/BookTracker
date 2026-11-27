import Shared
import UIKit

/// The native tab bar (D1). Built with the iOS 26 SDK or newer it gets Liquid Glass on iOS 26+,
/// and the classic tab bar on iOS 17–25. Each tab hosts its own Compose root with its own back stack.
final class MainTabBarController: UITabBarController {
    override func viewDidLoad() {
        super.viewDidLoad()
        viewControllers = [
            tab(IosApp.shared.bookshelfViewController(), title: String(localized: "tab.bookshelf"), symbol: "books.vertical"),
            tab(IosApp.shared.addBookViewController(), title: String(localized: "tab.addBook"), symbol: "plus.circle"),
            tab(IosApp.shared.accountViewController(), title: String(localized: "tab.account"), symbol: "person.crop.circle"),
        ]
    }

    private func tab(_ controller: UIViewController, title: String, symbol: String) -> UIViewController {
        controller.tabBarItem = UITabBarItem(title: title, image: UIImage(systemName: symbol), selectedImage: nil)
        return controller
    }
}
