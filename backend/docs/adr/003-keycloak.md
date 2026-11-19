# ADR-003: Keycloak as Identity Provider

- **Context:** The mobile app needs best-practice login and registration without an external SaaS service.
- **Decision:** Self-hosted Keycloak. The backend is a pure OAuth2 resource server.

| | Custom JWT issuing | Spring Authorization Server | Keycloak ✅ |
|---|---|---|---|
| Cost | – | free (Apache 2.0) | free (Apache 2.0, CNCF), separate container |
| Mobile best practice (Code + PKCE) | ❌ equivalent to the password grant, removed in OAuth 2.1 | ✅ | ✅ |
| Login/registration pages, password reset, email verification | build yourself | build yourself | included |
| MFA, passkeys, social login, admin UI | build yourself | build yourself | included |
| Operations | nothing extra | embedded in the backend | separate service (~0.5–1 GB RAM, own DB) |
| User data | own DB | own DB | in Keycloak, referenced via `sub` |
| Modules in the backend | `:adapter:security` + login endpoints | `auth-server` + `:adapter:security` | `:adapter:security` only |

- **Consequences:**
  - ➕ Standards-compliant OIDC flow, full user management without custom code, lean backend.
  - ➕ Switching to another IdP only changes the `issuer-uri` in the backend.
  - ➖ One more service to operate and update. Login pages need a Keycloak theme to customize.
  - ➖ User-related data in the backend references an external ID (`sub`). If a realm is recreated, every `sub` changes, so Keycloak needs its own persistent database and backups.
