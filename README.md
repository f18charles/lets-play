# Micro-e API

A Spring Boot + MongoDB API for managing users and products. It has JWT authentication,
role-based access (USER / ADMIN), BCrypt password hashing, HTTPS, CORS and rate limiting.

## Requirements

- Java 25 or newer (the project targets 25)
- MongoDB 8: Atlas or local (Docker works)
- `keytool` (ships with the JDK) for the HTTPS certificate
- No Maven install needed: use the included `./mvnw`

## Setup

1. **Environment.** Copy `.env.example` to `.env`, fill it in, then load it:
   ```bash
   cp .env.example .env
   set -a; source .env; set +a
   ```
   Keep the single quotes around `MONGODB_URI`, because Atlas URIs contain `&`.

   | Variable | Purpose | Default |
   |---|---|---|
   | `JWT_SECRET` | HMAC signing key, at least 32 bytes (**required**). Make one: `openssl rand -hex 32` | none |
   | `MONGODB_URI` | Mongo connection string, with the database name before any `?` | `mongodb://localhost:27017/letsplay` |
   | `MONGODB_URI_TEST` | Database used by the tests. They wipe it, so never use the real one | `mongodb://localhost:27017/letsplay_test` |
   | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Seeds one admin at startup (password at least 8 characters) | none: no admin |
   | `SSL_KEYSTORE_PASSWORD` | Password of the HTTPS keystore | none |
   | `SSL_KEYSTORE` | Keystore location | `file:./keystore.p12` |
   | `CORS_ORIGINS` | Comma-separated browser origins allowed to call the API | `https://localhost:3000` |

2. **HTTPS certificate** (self-signed, development only):
   ```bash
   export SSL_KEYSTORE_PASSWORD=changeit
   keytool -genkeypair -alias letsplay -keyalg RSA -keysize 2048 -storetype PKCS12 \
     -keystore keystore.p12 -validity 365 \
     -storepass "$SSL_KEYSTORE_PASSWORD" -keypass "$SSL_KEYSTORE_PASSWORD" \
     -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
   ```
   > **Note:** The password must be at least 6 characters (`keytool` requirement). If you change it, also update your `.env` or pass it when running: `SSL_KEYSTORE_PASSWORD=yourpass ./mvnw spring-boot:run`
3. **MongoDB.** Use Atlas (put its connection string in `MONGODB_URI`), or run local Mongo.
   The test suite needs a local Mongo:
   ```bash
   docker run -d --name letsplay-mongo -e GLIBC_TUNABLES=glibc.pthread.rseq=1 \
     -p 127.0.0.1:27017:27017 -v letsplay-mongo-data:/data/db mongo:8
   ```
   The `GLIBC_TUNABLES` flag is only needed on Linux kernels 6.19 to 7.0.13, which MongoDB otherwise refuses to start on.
4. **Run:** `./mvnw spring-boot:run`. The API is served at `https://localhost:8443`.
   Use `curl -k` because the certificate is self-signed.
5. **Test:** `./mvnw clean test`. This needs local Mongo running.

## Authentication

Register, log in, then send the token on every protected request:

```
Authorization: Bearer <token>
```

Tokens expire after 1 hour. Registration always creates a `USER`. Admins come only from the startup seeder
(`ADMIN_EMAIL` and `ADMIN_PASSWORD`) or from an admin changing a role through `PUT /users/{id}`.

## Endpoints

| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/auth/register` | public | 201 |
| POST | `/auth/login` | public | 200 |
| GET | `/products`, `/products/{id}` | public | 200 |
| POST | `/products` | any logged-in user | 201 |
| PUT | `/products/{id}` | owner or admin | 200 |
| DELETE | `/products/{id}` | owner or admin | 204 |
| GET | `/users`, `/users/{id}` | admin | 200 |
| PUT | `/users/{id}` | admin | 200 |
| DELETE | `/users/{id}` (also deletes their products) | admin | 204 |

### Examples

```bash
# register
curl -k -X POST https://localhost:8443/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'
# 201 {"id":"...","name":"Alice","email":"alice@example.com","role":"USER"}

# login
curl -k -X POST https://localhost:8443/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}'
# 200 {"token":"eyJ...","tokenType":"Bearer"}

# create a product
curl -k -X POST https://localhost:8443/products \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Lamp","description":"Desk lamp","price":19.99}'
# 201 {"id":"...","name":"Lamp","description":"Desk lamp","price":19.99,"userId":"..."}
```

The owner of a product is always the logged-in user. A `userId` in the request body is ignored.

## Errors

Every error has the same JSON shape:

```json
{"timestamp":"2026-11-23T10:00:00Z","status":404,"error":"Not Found","message":"Product not found","path":"/products/abc"}
```

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, wrong field type |
| 401 | Missing, invalid or expired token; wrong email or password |
| 403 | Logged in, but not the owner or an admin |
| 404 | Resource not found |
| 405 / 415 | Unsupported method or content type |
| 409 | Email already registered |
| 429 | Rate limit exceeded (a `Retry-After` header says when to retry) |
| 500 | Unexpected error: generic message, details only in the server log |

## Security

- Passwords are hashed with BCrypt (salted) and never returned by any endpoint.
- Request and response DTOs keep sensitive fields out: clients cannot set `role` or a product's `userId`.
- The user is loaded from the database on every request, so a deleted or demoted user loses access immediately.
- Queries use Spring Data derived methods with typed fields, so MongoDB operator payloads such as
  `{"$ne":""}` fail validation with a 400.
- Login returns the same error for an unknown email and a wrong password.
- HTTPS only. Secrets come from environment variables and are never committed.

## Rate limiting and CORS

- `/auth/**` is limited to 5 requests per minute per IP. Everything else is limited to 120 per minute per IP.
  Both are configurable through `app.rate-limit.*`.
- Only the origins listed in `CORS_ORIGINS` may call the API from a browser.

## Project structure

```
src/main/java/com/example/lets_play/
├── common/      ErrorResponse and exception/ (custom exceptions, GlobalExceptionHandler)
├── config/      SecurityConfig, CorsConfig, PasswordConfig, AdminSeeder
├── security/    JWTService, JwtAuthFilter, RateLimitFilter, AuthUser, 401/403 handlers
├── products/    Product, ProductRepository, ProductService, ProductController, dto/
└── users/       User, Role, UserRepository, controllers/, services/, dto/
```