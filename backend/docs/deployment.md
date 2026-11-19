# BookTracker Backend – Deployment

> How the backend gets from a merge to a running server. This document holds the concept only: no host names, no IDs and no secrets. Real values live in the places named under [Configuration and secrets](#configuration-and-secrets).

## Overview

There are two environments, **dev** and **prod**, on one Docker host managed with Portainer. Both run the same image, built by GitHub Actions, with different configuration and data.

| | dev | prod |
|---|---|---|
| Deployed by | every push to `main` | a tag `v<major>.<minor>.<patch>` on `main` |
| Image tag | `sha-<short commit>` | the tag, e.g. `v0.1.0` |
| Keycloak realm | `booktracker-dev` | `booktracker` |
| Database | its own Postgres | its own Postgres |
| Sign-up | open, with email verification | open, with email verification |

Feature branches go into `main` by pull request. There is no `develop` branch and no release branch: `main` always runs on dev, and a tag promotes that state to prod. The tag build compiles the tagged commit again (it does not reuse dev's image), and it refuses a tag whose commit is not on `main`.

```mermaid
flowchart LR
    dev([Developer]) -- "merge PR" --> main[main]
    dev -- "tag v0.1.0" --> tag[tag]
    main --> ci[GitHub Actions<br/>build, test, image]
    tag --> ci
    ci -- "push image" --> reg[(GHCR)]
    ci -- "Portainer API:<br/>update stack" --> port[Portainer]
    port -- "pull image,<br/>recreate containers" --> stack[Stack dev / prod]
    ci -- "wait for<br/>/actuator/info + health" --> stack
```

## What runs on the server

| Stack | Contents | Source |
|---|---|---|
| `booktracker-keycloak` | Keycloak and its own Postgres. One server for both environments, one realm per environment | `deploy/keycloak/` |
| `booktracker-dev` | Backend and its own Postgres | `deploy/backend/compose.yaml` |
| `booktracker-prod` | Backend and its own Postgres, same compose file | `deploy/backend/compose.yaml` |

- A **reverse proxy** in front of everything ends TLS and forwards one host name per service: `api.dev.<domain>`, `api.<domain>`, `auth.<domain>`.
- The proxy, the two backends and Keycloak share the external Docker network **`booktracker-proxy`**. No container publishes a port on the host.
- The Postgres containers sit only on their own stack's network, so the proxy and the other stacks cannot reach them.
- Container names are unique across stacks (`booktracker-dev-app`, `booktracker-prod-app`, `booktracker-keycloak`), because the proxy and the backends address them by name on the shared network.
- Every container has a memory limit. The buildpack's JVM memory calculator and Keycloak size their heaps from it, not from the host's memory.

## How a deploy works

1. **Build and test**: the same job that runs on pull requests.
2. **Image**: `./gradlew :bootstrap:bootBuildImage` builds the image with Spring Boot's built-in buildpack support (Paketo, Java runtime chosen from the project's Java version). It needs no Dockerfile. The image goes to GHCR, and the commit hash goes into `/actuator/info`.
3. **Update the stack** (`deploy/portainer-deploy.sh`), using Portainer's API with an access token:
   1. Read the stack, including its variables.
   2. Change only `IMAGE_TAG`.
   3. Send the compose file of the deployed commit and all variables back, with "re-pull image" on.

   Portainer's stack update **replaces all stack variables** with the ones sent, which is why the script reads them first. The variables hold secrets, so the script never prints them or a response that contains them.
4. **Wait for Portainer.** Since Portainer 2.45 the update only starts the deployment and answers at once. The script polls the stack until its status is "active", or stops with Portainer's error message (for example, a failed image pull).
5. **Wait for the new version**, up to 5 minutes, until `/actuator/info` reports the deployed commit and `/actuator/health` reports `UP`. Flyway migrates the database while the new container starts.
   - If that doesn't happen, the workflow fails and GitHub reports it.
   - There is no automatic rollback, because a migration that already ran cannot be undone by starting the old image.

Two deploys to the same environment never run at the same time (`concurrency` per environment).

**Every version is pinned**, never `latest` or a bare major tag: the GitHub actions (`@v5.1.0`), the runner (`ubuntu-24.04`), the buildpack builder (`bootstrap/build.gradle.kts`), and the Keycloak and Postgres images. The same commit then always builds and runs on the same base. Updates are deliberate: raise the version in one place, open a pull request, and let it deploy to dev first.

Keycloak has its own workflow (`.github/workflows/keycloak.yml`). Changes on `main` only build and push its image. Deploying it takes a manual **Run workflow** on `main`, because one Keycloak serves both environments.

## Keycloak

- **Image:** `deploy/keycloak/Dockerfile` extends the official image, runs `kc.sh build` (Postgres, health checks) and starts with `start --optimized`.
- **Realms:**
  - The realm files under `deploy/keycloak/realms/` are part of the image.
  - `--import-realm` creates a realm only if it doesn't exist yet, so a redeploy never resets one. Changes to an existing realm go through the admin console.
  - The realm files contain no users. Their `${…}` placeholders (the Swagger UI redirect and web origin per environment) are filled from the stack variables on import.
  - The local realm `keycloak/realm-booktracker.json` with its test users is only for `compose.yaml` on the laptop.
- **Sign-up:** open in both realms. A new user must confirm their email address before the first login, and "forgot password" works. New users get the `user` role through the realm's default role. The email address is the username (*Email as username*, in the realm files), and first and last name are optional. Making the names optional is a manual setting per realm in the admin console (*Realm settings → User profile → firstName / lastName → Required field* off), since the user profile lives in an embedded configuration the realm files do not carry. The backend reads neither: it identifies users by the Keycloak `sub` only.
- **Changing the email address:** users change it themselves in the account console (*Personal info → Update email*). The new address only replaces the old one after the user clicks a link sent to it. The backend is not affected, since it identifies users by the Keycloak `sub`, not by their email. This needs two settings **per realm in the admin console**, which the realm files do not carry: *Authentication → Required actions → Update Email* enabled, and in its configuration *Force Email Verification* on. (The realm files leave required actions out on purpose: a `requiredActions` list in a realm file replaces Keycloak's whole default list, so an incomplete one would, for example, switch off *Verify Email* on a fresh install.)
- **Mail:** Keycloak sends verification and reset mails through an SMTP mailbox on the project's domain, on port 587 with STARTTLS. Port 465 (implicit TLS) does not work from the server: cloud providers commonly block outgoing traffic on 25 and 465 against spam, and 587 stays open. For a fresh realm, the realm files take the server, sender, login and password from the `BOOKTRACKER_SMTP_*` stack variables on import. For realms that already exist, the settings live in the admin console (*Realm settings → Email*), so changing the variables alone changes nothing there. The domain needs an SPF record (and DKIM, where the mail provider offers it) that allows the SMTP server to send for it, or the mails land in spam.
- **Brute force protection** is on in both app realms and in the master realm, with a temporary lockout.
- **The admin console** is public like the login pages. The admin account is protected by its password, two-factor login (OTP) and the master realm's brute force protection. An extra Basic password at the reverse proxy was tried first and dropped: the console and the account console call Keycloak with `Authorization: Bearer …`, which collides with Basic auth on the same paths.
- **The bootstrap admin** is not in the compose file. For the very first start, add `KC_BOOTSTRAP_ADMIN_USERNAME` and `KC_BOOTSTRAP_ADMIN_PASSWORD` to the Keycloak service's environment in Portainer's editor by hand. Replace it with a personal admin account that uses two-factor login, delete it, and remove both lines again (the next deploy from the workflow does that anyway). Do not leave either variable set but empty: Keycloak then refuses to start. The same two lines also recover access if every admin is locked out.
- **Token checks:** the backend checks a token's issuer against the public realm URL. It loads the signing keys inside Docker (`http://booktracker-keycloak:8080/…`) instead of through the proxy.
- **Never change the public Keycloak host name** once users exist. It is part of every token and every redirect URI.

## Swagger UI

Swagger UI and `/v3/api-docs` stay reachable on both environments, but ask for an extra password first (HTTP Basic, `BOOKTRACKER_API_DOCS_USERNAME` / `_PASSWORD`). That password only opens the pages. Calls to `/api/v1` still need a Keycloak token. Without a password set (local development) the pages are open.

## Configuration and secrets

**No secret and no host name is in git.** The repository holds variable names only.

| Where | What |
|---|---|
| Portainer, stack variables | Every server setting and secret: database credentials, issuer URL, hit-id secret, Google key, Swagger password, SMTP login. Names: `deploy/backend/stack.env.example`, `deploy/keycloak/stack.env.example` |
| Portainer, registry | Read access to GHCR (a token with `read:packages`), so Portainer can pull the private images |
| GitHub, repository secret | `PORTAINER_TOKEN`: the access token of a Portainer user that may update only the BookTracker stacks |
| GitHub, repository variables | `PORTAINER_URL`, `PORTAINER_ENDPOINT_ID`, `DEV_STACK_ID`, `PROD_STACK_ID`, `KEYCLOAK_STACK_ID`, `DEV_API_URL`, `PROD_API_URL`, `AUTH_URL` |

Filled-in copies of the `*.env` files are ignored by git. Images are pushed with the workflow's own `GITHUB_TOKEN`, so no extra secret is needed for that.

## One-time setup

The order matters, because each step needs the one before it. The deploy jobs skip themselves while their stack id variable is not set, so the workflows can be merged before the stacks exist. The merge builds the first images.

1. **DNS:** one record per host name, pointing at the server. With Cloudflare, use "DNS only": its free certificate covers one subdomain level only, and the reverse proxy gets its own certificates.
   - **Mail:** a mailbox for the sender address at the domain's mail provider, and an SPF record (plus DKIM, if offered) that allows the provider's servers to send for the domain.
2. **Portainer:**
   - Create the network `booktracker-proxy` and connect the reverse proxy's container to it.
   - Add the GHCR registry.
   - Create a deploy user and its access token.
3. **Merge the workflows into `main`.** This builds the backend and Keycloak images. The deploys are skipped.
4. **Keycloak stack:**
   - Create the stack from `deploy/keycloak/compose.yaml` with its variables (including the SMTP login), `IMAGE_TAG` set to the Keycloak image built in step 3, plus the two bootstrap admin lines (see *Keycloak* above).
   - Give the deploy user access to it.
5. **Reverse proxy:** one proxy host per host name with a TLS certificate and *Force SSL*.
6. **Keycloak:** a personal admin with two-factor login and an email address, delete the bootstrap admin and remove its two lines, turn on brute force protection in the master realm. Check *Realm settings → Email → Test connection* in both app realms, enable *Update Email* with *Force Email Verification*, and make first and last name optional in the user profile (see *Keycloak* above).
7. **Backend stacks:** create `booktracker-dev` and `booktracker-prod` from `deploy/backend/compose.yaml`, each with its own variables (`IMAGE_TAG`: the backend image from step 3), and give the deploy user access.
8. **GitHub:** the secret and variables from the table above.
9. **First deploys:** the next push to `main` deploys dev, and the first tag deploys prod.

## Day to day

- **Deploy dev:** merge a pull request into `main`.
- **Release to prod:** tag a commit on `main` and push the tag (`git tag v0.2.0 && git push origin v0.2.0`).
- **Go back to an older version:** re-run the workflow of the older tag. It rebuilds and deploys that version. This works only while the database is still compatible with it: Flyway leaves newer migrations in place.
- **Change a setting or a secret:** edit the stack variable in Portainer and redeploy the stack there, or wait for the next deploy, which keeps the variables as they are.
- **Points to know when rotating:**
  - `BOOKTRACKER_CATALOG_HIT_ID_SECRET` makes search results from before the change unaddable until the app searches again.
  - A database password must be changed in Postgres too (`ALTER USER`), since the variable only sets it when the volume is first created.
  - The Portainer token is replaced in Portainer and in the GitHub secret together.

## Not covered yet

- **Backups.** Losing the server or a volume loses every account and every book. A lost Keycloak database also orphans every user's rows, because `owner_id` has no foreign key.
- **Monitoring and alerts.** An outage is only noticed in use.
- **Privacy policy, imprint and account deletion.** Sign-up is open, so the app stores strangers' email addresses. The legal pages and a "delete my account" path are still missing (Keycloak deletes the account, but the app's rows stay behind, since `owner_id` has no foreign key).
- **More than one server.** The per-IP rate limits towards the book sources assume a single instance (see [architecture.md](architecture.md), *External Book Data*).
