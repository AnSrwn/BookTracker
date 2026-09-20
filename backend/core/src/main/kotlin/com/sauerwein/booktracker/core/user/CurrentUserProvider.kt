package com.sauerwein.booktracker.core.user

fun interface CurrentUserProvider {
    fun currentUserId(): UserId
}
