// The look of the app: theme, colors, typography, shared components and icons.
// The real design comes in the design step of the roadmap; until then Material 3 defaults.
plugins {
    id("booktracker.kmp-compose")
}

// Features use the icons, so the generated Res class is public here
compose.resources {
    publicResClass = true
}
