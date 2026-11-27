package com.sauerwein.booktracker.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Every screen of the app has one key here. The keys are serializable so that back stacks survive
 * process death; iOS has no reflection, so they are registered in [appNavSavedStateConfiguration].
 */
@Serializable
sealed interface AppNavKey : NavKey

/** Root of the bookshelf tab: the user's own books. */
@Serializable
data object BookshelfKey : AppNavKey

/** Details of one of the user's books. */
@Serializable
data class BookDetailKey(
    val bookId: String,
) : AppNavKey

/** Root of the add-book tab: search, scan, add by hand. */
@Serializable
data object AddBookKey : AppNavKey

/** Form for a book the catalog does not know. */
@Serializable
data object ManualBookKey : AppNavKey

/** Root of the account tab. */
@Serializable
data object AccountKey : AppNavKey

/** About the app: version, licenses. */
@Serializable
data object AboutKey : AppNavKey
