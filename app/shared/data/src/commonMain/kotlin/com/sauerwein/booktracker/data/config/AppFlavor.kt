package com.sauerwein.booktracker.data.config

/**
 * Which app is installed: the dev app (own app id, own name) or the prod app. Both can be
 * installed side by side, so each has its own login redirect scheme. The dev app starts on
 * the dev backend and may later switch to prod in its dev menu; it keeps its own scheme then.
 */
enum class AppFlavor(
    val id: String,
    val defaultEnvironment: AppEnvironment,
    private val redirectScheme: String,
) {
    DEV(id = "dev", defaultEnvironment = AppEnvironment.DEV, redirectScheme = "booktracker-dev"),
    PROD(id = "prod", defaultEnvironment = AppEnvironment.PROD, redirectScheme = "booktracker"),
    ;

    val authRedirectUri: String get() = "$redirectScheme://auth/callback"
    val postLogoutRedirectUri: String get() = "$redirectScheme://auth/logout"

    companion object {
        /** [id] comes from the Android flavor (`BuildConfig.FLAVOR`) or the iOS build configuration. */
        fun fromId(id: String): AppFlavor =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown app flavor '$id', expected one of ${entries.map { it.id }}")
    }
}
