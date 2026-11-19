# Adapter – Web

The REST API for the mobile app. Translates HTTP requests into use case calls.

## Contains

- **Controllers:** thin, each call goes to one use case
- **DTOs and mappers:** request/response classes ↔ domain models
- **Validation and error handling**
- **OpenAPI docs:** Swagger UI setup and the descriptions of endpoints, fields and errors
