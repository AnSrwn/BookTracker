# BookTracker

A mobile app to keep track of your own books. Scan the barcode or add a book by hand, sort your books onto shelves and track your reading progress.

So far the repository holds only the backend. The mobile app does not exist yet.

## Features

The backend already supports:

- Sign up and sign in with Keycloak, with email verification
- Look up a book by its ISBN, for example after a barcode scan. Title, author and cover come from the public book sources.
- Search for books by title, author or ISBN, in the catalog, in your own books and in the public book sources
- Add a book from the catalog to your books
- Create a private book by hand that only you can see, when the catalog doesn't know the book
- List your books and view one book

Still planned for the MVP:

- Edit and remove books
- Set a reading status and the current page
- Sort books onto your own shelves, e.g. "My Books" and "Borrowed"
- Delete the account with all personal data

Details in [backend/docs/mvp-features.md](backend/docs/mvp-features.md).

## Tech stack

Kotlin, Spring Boot, PostgreSQL and Keycloak. Book data comes from Google Books, Open Library and the German National Library (DNB).

## Repository

- [`backend/`](backend/Readme.md): the REST API, with setup and run instructions
- [`backend/docs/`](backend/docs): architecture, book data, decision records, deployment
