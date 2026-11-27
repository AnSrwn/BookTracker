import Shared
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        guard let flavor = Bundle.main.object(forInfoDictionaryKey: "BookTrackerFlavor") as? String else {
            fatalError("BookTrackerFlavor is missing in Info.plist; it comes from the build configuration")
        }
        IosApp.shared.start(flavorId: flavor)
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        UISceneConfiguration(name: "Default Configuration", sessionRole: connectingSceneSession.role)
    }
}
