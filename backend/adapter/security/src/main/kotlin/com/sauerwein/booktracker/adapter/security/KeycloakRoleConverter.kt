package com.sauerwein.booktracker.adapter.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

/**
 * Keycloak puts realm roles into `realm_access.roles`; Spring expects authorities
 * prefixed with `ROLE_`. Without this converter `hasRole(...)` would never match.
 */
@Component
class KeycloakRoleConverter : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(source: Jwt): AbstractAuthenticationToken {
        val roles = source.getClaimAsMap(REALM_ACCESS_CLAIM)
            ?.get(ROLES_CLAIM)
            ?.let { it as? Collection<*> }
            .orEmpty()
            .filterIsInstance<String>()

        val authorities = roles.map { SimpleGrantedAuthority("$ROLE_PREFIX$it") }

        // The two-arg constructor takes the principal name from the `sub` claim.
        return JwtAuthenticationToken(source, authorities)
    }

    private companion object {
        const val REALM_ACCESS_CLAIM = "realm_access"
        const val ROLES_CLAIM = "roles"
        const val ROLE_PREFIX = "ROLE_"
    }
}
