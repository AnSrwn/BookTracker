package com.sauerwein.booktracker.adapter.security

import com.sauerwein.booktracker.core.user.CurrentUserProvider
import com.sauerwein.booktracker.core.user.UserId
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reads the current user from the validated JWT. Keycloak's `sub` is a UUID, so it maps
 * straight onto [UserId] — the app keeps no user table of its own (ADR-003).
 *
 * Every path that reaches a use case is authenticated by [SecurityConfig], so a missing
 * or non-UUID `sub` is a misconfiguration, not a client error: it fails loudly as a 500
 * rather than silently falling back to some other identity.
 */
@Component
class JwtCurrentUserProvider : CurrentUserProvider {

    override fun currentUserId(): UserId {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        val jwt = principal as? Jwt
            ?: error("No authenticated JWT in the security context; this endpoint must require authentication")

        val subject = jwt.subject ?: error("JWT has no 'sub' claim")

        return UserId(
            runCatching { UUID.fromString(subject) }
                .getOrElse { error("JWT 'sub' is not a UUID: $subject") }
        )
    }
}
