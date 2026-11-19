# ADR-004: Image Handling

- **Context:** Possible image types are book covers from the external book sources, cover photos taken by users, and profile pictures. The MVP only needs covers from the lookup.
- **Decision (MVP):** Only covers from the external sources, stored as a URL (hotlinking). No own image storage, no uploads. The app loads the image straight from the source, so cover traffic costs the backend no bandwidth and no rate-limit budget. Cover precedence per source is in [book-data.md](../book-data.md#2-merge).
- **Consequences:**
  - ➕ No storage infrastructure, no upload code, nothing to secure or clean up.
  - ➖ Depends on availability of the external sources. Cover URLs may change or disappear.
  - ↪ Switching to stored copies of external covers is possible later without API changes (the book keeps a cover URL).

## Future direction (once users can upload images)

- **Storage:** S3-compatible object storage (e.g. AWS S3, Cloudflare R2, Hetzner Object Storage; locally an S3 emulator container). Never in Postgres (`bytea`) or on the local file system. The database only holds metadata: key, content type, size, owner.
- **Architecture:** port `ImageStorage` in `core`, new module `:adapter:storage` (S3 client).
- **Upload:**

| | Multipart via backend (start here) | Presigned URL (direct to storage) |
|---|---|---|
| Flow | App → `POST /api/.../cover` → backend validates → storage | App requests upload URL → uploads directly → confirms to backend |
| Validation | fully in the backend | only afterwards (size limit can be encoded in the URL) |
| Backend load | yes | no |
| Complexity | low | higher (two steps, possible orphaned uploads) |

- **Delivery:** presigned GET URLs, or a public bucket / CDN with immutable, unguessable keys. A new upload always gets a new key, so clients can cache forever (`Cache-Control: immutable`).
- **Checklist:**
  - Check the file type by magic bytes, not by `Content-Type` or file extension
  - Enforce a size limit (`spring.servlet.multipart.max-file-size`)
  - The app converts HEIC to JPEG/WebP and downscales (e.g. max. 1200 px) before uploading
  - The backend re-encodes images: this strips EXIF data incl. GPS location and neutralizes crafted files. Generate thumbnails at the same time
  - Upload to storage first, then write the DB row. A periodic cleanup job removes orphaned files
  - Delete images when a book is removed and when an account is deleted (GDPR)
  - Upload quota per user to prevent abuse
