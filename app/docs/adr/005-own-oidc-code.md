# ADR-005: Own code for the Keycloak login

- **Context:** The app signs in with Keycloak through Authorization Code + PKCE in the system browser; the backend never sees a password ([backend ADR-003](../../../backend/docs/adr/003-keycloak.md)).
- **Decision:** Our own implementation: PKCE, token exchange and refresh with Ktor, the browser through `ASWebAuthenticationSession` (iOS) and Custom Tabs (Android). No OIDC library.

| | kotlin-multiplatform-oidc | AppAuth per platform | Own code ✅ |
|---|---|---|---|
| Control | limited | limited, two APIs | full |
| Maintenance | library, pre-1.0 | two libraries | ours |
| Security-critical code | in the library | in the library | ours |

- **Consequences:**
  - ➕ Full control over the flow, the token storage and the refresh, no library pre-1.0.
  - ➖ Security-critical code we maintain ourselves.
  - ⚠ The auth step must get PKCE S256, the `state` check, secure token storage (Keychain, Android Keystore) and concurrent refreshes right. The storage library is decided then.
