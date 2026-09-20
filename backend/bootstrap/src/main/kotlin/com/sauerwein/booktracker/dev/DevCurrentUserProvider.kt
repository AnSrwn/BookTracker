package com.sauerwein.booktracker.dev

import com.sauerwein.booktracker.core.user.CurrentUserProvider
import com.sauerwein.booktracker.core.user.UserId
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*

// TODO
/**
 * Stands in for the real provider until Keycloak arrives, so ownership can be built
 * and tested without auth. Configurable via `booktracker.dev-user-id`, which makes it
 * possible to check visibility by restarting instead of changing code.
 *
 * Deleted in the auth step, together with this package.
 */
@Component
class DevCurrentUserProvider(
    @Value("\${booktracker.dev-user-id}") private val devUserId: UUID,
) : CurrentUserProvider {
    override fun currentUserId(): UserId = UserId(devUserId)
}
