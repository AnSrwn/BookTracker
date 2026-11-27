package com.sauerwein.booktracker.data.config

/**
 * A backend the app talks to. Each has its own API and its own Keycloak realm, so tokens
 * and local data of one environment are never valid for the other.
 */
enum class AppEnvironment(
    val apiBaseUrl: String,
    val issuerUrl: String,
) {
    DEV(
        apiBaseUrl = "https://api.dev.booktracker.sauerwein.me/api/v1",
        issuerUrl = "https://auth.booktracker.sauerwein.me/realms/booktracker-dev",
    ),
    PROD(
        apiBaseUrl = "https://api.booktracker.sauerwein.me/api/v1",
        issuerUrl = "https://auth.booktracker.sauerwein.me/realms/booktracker",
    ),
    ;

    /** The public Keycloak client of the app, the same in both realms. */
    val clientId: String get() = "booktracker-app"
}
