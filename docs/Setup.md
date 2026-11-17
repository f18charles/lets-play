# Let's Play API: Setup Guide

Spring Boot 4 + MongoDB 8 REST API with JWT authentication, role-based access, HTTPS, CORS and rate limiting.
This guide covers the fixes for the failing tests, the bonus features (CORS and rate limiting), the environment
setup (including a `.env` file), every terminal command from a clean machine, and the project README.

## Contents

1. [Fixing the failing tests](#1-fixing-the-failing-tests)
2. [Bonus features: CORS and rate limiting](#2-bonus-features-cors-and-rate-limiting)
3. [Environment variables and the `.env` file](#3-environment-variables-and-the-env-file)
4. [Full command sequence](#4-full-command-sequence)
5. [README.md](#5-readmemd)
6. [Final checklist](#6-final-checklist)

---

## 1. Fixing the failing tests

The failing test run pointed to three separate causes.

### 1.1 `field 'id' not found` even though the body contains `"id":"..."`

The `field` helper in `ApiIntegrationTest` has a typo, probably in its regex. Replace it with this version, which uses no regex:

```java
private static String field(String json, String name) {
    String key = "\"" + name + "\":\"";
    int start = json.indexOf(key);
    assertTrue(start >= 0, "field '" + name + "' not found in: " + json);
    start += key.length();
    return json.substring(start, json.indexOf('"', start));
}
```

### 1.2 404 and 400 tests returning 401 with `"path":"/error"`

Exceptions are escaping to Spring's built-in `/error` page, and the security config blocks that page.

- Check that `GlobalExceptionHandler` exists in `com.example.lets_play.exception` and is annotated with `@RestControllerAdvice`.
- The updated `SecurityConfig` in section 2 permits `/error`, so a real failure shows up as a 4xx or 5xx instead of a misleading 401.

### 1.3 Malformed 401 JSON

The 401 body in the test output was invalid (`"message":Authentication failed...`, `path":"/error"`), and its wording differed
from the reference code. `ErrorWriter` or `RestAuthenticationEntryPoint` has typos. The format string must contain:

```
\"message\":\"%s\",\"path\":\"%s\"
```

---

## 2. Bonus features: CORS and rate limiting

### 2.1 Properties

Add to `src/main/resources/application.properties`:

```properties
app.cors.allowed-origins=${CORS_ORIGINS:https://localhost:3000}
app.rate-limit.auth-per-minute=5
app.rate-limit.general-per-minute=120
```

### 2.2 `config/CorsConfig.java`

```java
package com.example.lets_play.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String[] origins) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(Arrays.asList(origins));          // explicit list, never "*"
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setExposedHeaders(List.of("Retry-After"));
        c.setAllowCredentials(false);                          // token in a header, no cookies
        c.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", c);
        return source;
    }
}
```

### 2.3 `security/RateLimitFilter.java`

Hand-written, so there is no extra dependency to version-match. It uses a fixed one-minute window per client IP,
with a stricter limit on `/auth/**` to slow brute-force attempts and mass sign-ups.

```java
package com.example.lets_play.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;

    private static final class Counter {
        final long windowStart;
        int count;
        Counter(long windowStart) { this.windowStart = windowStart; }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final int authLimit;
    private final int generalLimit;

    public RateLimitFilter(int authLimit, int generalLimit) {
        this.authLimit = authLimit;
        this.generalLimit = generalLimit;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        boolean auth = req.getRequestURI().startsWith("/auth/");
        String key = (auth ? "auth:" : "gen:") + req.getRemoteAddr();
        int limit = auth ? authLimit : generalLimit;
        long now = System.currentTimeMillis();

        boolean[] allowed = {true};
        long[] retryAfterSeconds = {0};
        counters.compute(key, (k, c) -> {
            if (c == null || now - c.windowStart >= WINDOW_MS) c = new Counter(now);
            c.count++;
            if (c.count > limit) {
                allowed[0] = false;
                retryAfterSeconds[0] = Math.max(1, (c.windowStart + WINDOW_MS - now + 999) / 1000);
            }
            return c;
        });

        if (counters.size() > 10_000) {   // keep memory bounded
            counters.entrySet().removeIf(e -> now - e.getValue().windowStart >= WINDOW_MS);
        }

        if (!allowed[0]) {
            res.setHeader("Retry-After", String.valueOf(retryAfterSeconds[0]));
            ErrorWriter.write(req, res, HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please slow down and try again later.");
            return;
        }
        chain.doFilter(req, res);
    }
}
```

> The limiter counts per `getRemoteAddr()`. Behind a reverse proxy that is the proxy's address, so a proxy deployment
> would need forwarded-IP handling. For this project, direct access is fine.

### 2.4 `config/SecurityConfig.java` (full replacement)

Adds CORS, permits `/error`, and registers the rate limiter.

```java
package com.example.lets_play.config;

import com.example.lets_play.repository.UserRepository;
import com.example.lets_play.security.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final int authLimit;
    private final int generalLimit;

    public SecurityConfig(@Value("${app.rate-limit.auth-per-minute}") int authLimit,
                          @Value("${app.rate-limit.general-per-minute}") int generalLimit) {
        this.authLimit = authLimit;
        this.generalLimit = generalLimit;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtService jwtService,
                                           UserRepository users,
                                           RestAuthenticationEntryPoint entryPoint,
                                           RestAccessDeniedHandler deniedHandler) throws Exception {
        http
            .cors(Customizer.withDefaults())                  // uses the CorsConfigurationSource bean
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/products", "/products/**").permitAll()
                .requestMatchers("/users", "/users/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler))
            .addFilterBefore(new RateLimitFilter(authLimit, generalLimit),
                             UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwtService, users),
                             UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

### 2.5 Update the integration test

The test logs in dozens of times from one IP, so the limiter would block it. Add these to the `properties = {...}`
array of `@SpringBootTest` in `ApiIntegrationTest`:

```java
"app.rate-limit.auth-per-minute=100000",
"app.rate-limit.general-per-minute=100000",
"app.cors.allowed-origins=https://localhost:3000",
```

---

## 3. Environment variables and the `.env` file

Yes, you can use a `.env` file. Spring Boot does not read it on its own, so there are two ways to load it.

| Method | How | Notes |
|---|---|---|
| **Load into the shell** (recommended) | `set -a; source .env; set +a` | Works everywhere. Used in the commands below. |
| **Let Spring read it** | Add `spring.config.import=optional:file:.env[.properties]` to `application.properties` | Do not put quotes around values, because they are kept literally. If values do not load, use the shell method. |

Either way:

- `.env` stays out of git (it is listed in `.gitignore`).
- Commit a key-only `.env.example` so others know which variables to set.

---

## 4. Full command sequence

### Step 1: Wipe Docker (destructive)

This deletes every container, image, volume, network and build cache on the machine, **including other projects' data**.
Skip it if you use Docker for anything else.

```bash
docker ps -aq | xargs -r docker rm -f            # all containers
docker system prune -a --volumes -f              # all images, volumes, networks, build cache
docker images -a && docker volume ls             # both lists should now be empty
```

Narrow version, for this project only:

```bash
docker rm -f letsplay-mongo
docker volume rm letsplay-mongo-data
docker rmi mongo:8
```

### Step 2: Start MongoDB

```bash
docker run -d \
  --name letsplay-mongo \
  -e GLIBC_TUNABLES=glibc.pthread.rseq=1 \
  -p 127.0.0.1:27017:27017 \
  -v letsplay-mongo-data:/data/db \
  mongo:8

until mongosh --quiet --eval 'db.runCommand({ping:1}).ok' 2>/dev/null | grep -q 1; do sleep 1; done
echo "Mongo is up"
```

Keep the `GLIBC_TUNABLES` flag. It lets MongoDB start on Linux kernels 6.19 to 7.0.13, which it otherwise refuses to run on.

### Step 3: Create `.env`

```bash
cd ~/Documents/lets-play

cat > .env <<EOF
MONGODB_URI=mongodb://localhost:27017/letsplay
JWT_SECRET=$(openssl rand -hex 32)
ADMIN_EMAIL=admin@example.com
ADMIN_PASSWORD=change-me-please
SSL_KEYSTORE_PASSWORD=changeit123
CORS_ORIGINS=https://localhost:3000
EOF
chmod 600 .env

sed 's/=.*/=/' .env > .env.example          # key-only template, safe to commit
```

### Step 4: Load it and generate the HTTPS keystore

```bash
set -a; source .env; set +a

[ -f keystore.p12 ] || keytool -genkeypair -alias letsplay -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore keystore.p12 -validity 365 \
  -storepass "$SSL_KEYSTORE_PASSWORD" -keypass "$SSL_KEYSTORE_PASSWORD" \
  -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

### Step 5: Test, then run

```bash
./mvnw clean test          # Mongo must be running
./mvnw spring-boot:run     # leave this terminal open
```

### Step 6: Verify by hand (second terminal)

```bash
cd ~/Documents/lets-play && set -a; source .env; set +a
API=https://localhost:8443

# register + login
curl -sk -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'
TOKEN=$(curl -sk -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')

# products: public read 200, no token 401, with token 201
curl -ski $API/products | head -1
curl -ski -X POST $API/products -H 'Content-Type: application/json' \
  -d '{"name":"Lamp","description":"Desk lamp","price":19.99}' | head -1
curl -ski -X POST $API/products -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Lamp","description":"Desk lamp","price":19.99}' | head -1

# admin endpoints: normal user 403, admin 200
curl -ski $API/users -H "Authorization: Bearer $TOKEN" | head -1
ADMIN=$(curl -sk -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -sk $API/users -H "Authorization: Bearer $ADMIN"

# error format: 400, 404, 405
curl -sk -X POST $API/auth/register -H 'Content-Type: application/json' -d '{bad json'
curl -sk $API/products/missing
curl -sk -X PATCH $API/products/abc -H "Authorization: Bearer $TOKEN"

# rate limit: the earlier calls already used part of this minute's /auth quota, so wait first.
# Attempts 6 and 7 should print 429.
sleep 60
for i in 1 2 3 4 5 6 7; do
  curl -sk -o /dev/null -w "%{http_code}\n" -X POST $API/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"x@y.com","password":"wrongpass1"}'
done

# CORS: allowed origin gets Access-Control-Allow-Origin, other origins get 403
curl -ski -X OPTIONS $API/products -H 'Origin: https://localhost:3000' \
  -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: authorization,content-type' | head -8
curl -ski -X OPTIONS $API/products -H 'Origin: https://evil.example' \
  -H 'Access-Control-Request-Method: POST' | head -1
```

Expected statuses: 200, 401, 201, 403, 400, 404, 405, then 401 x5 followed by 429 x2.

### Stop and restart later

```bash
docker stop letsplay-mongo      # data is kept
docker start letsplay-mongo
```

---

## 5. README.md

Copy the block below into the project root as `README.md`.

````markdown
# Let's Play: secure REST API

Spring Boot + MongoDB CRUD API for users and products with JWT authentication,
role-based access (USER / ADMIN), BCrypt password hashing, HTTPS, CORS and rate limiting.

## Requirements
Java 21, Maven (or the included `./mvnw`), MongoDB 8 (Docker or local), `keytool` (ships with the JDK).

## Setup

1. **MongoDB** (the `GLIBC_TUNABLES` flag is needed on Linux kernels 6.19 to 7.0.13):
   ```bash
   docker run -d --name letsplay-mongo -e GLIBC_TUNABLES=glibc.pthread.rseq=1 \
     -p 127.0.0.1:27017:27017 -v letsplay-mongo-data:/data/db mongo:8
   ```
2. **Environment**: copy `.env.example` to `.env` and fill it in, then `set -a; source .env; set +a`.

   | Variable | Purpose | Default |
   |---|---|---|
   | `JWT_SECRET` | HMAC signing key, at least 32 bytes (**required**) | none |
   | `MONGODB_URI` | Mongo connection string | `mongodb://localhost:27017/letsplay` |
   | `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Seeds one admin on startup (password min 8 chars) | none: no admin |
   | `SSL_KEYSTORE_PASSWORD` | Keystore password | none |
   | `SSL_KEYSTORE` | Keystore location | `file:./keystore.p12` |
   | `CORS_ORIGINS` | Comma-separated allowed origins | `https://localhost:3000` |

3. **HTTPS keystore** (self-signed, development only):
   ```bash
   keytool -genkeypair -alias letsplay -keyalg RSA -keysize 2048 -storetype PKCS12 \
     -keystore keystore.p12 -validity 365 -dname "CN=localhost" \
     -ext "SAN=dns:localhost,ip:127.0.0.1"
   ```
4. **Run**: `./mvnw spring-boot:run` serves `https://localhost:8443`. Use `curl -k` for the self-signed certificate.
5. **Test**: `./mvnw test` (needs Mongo running; uses a separate `letsplay_test` database, which it wipes).

## Authentication
Register, log in, then send `Authorization: Bearer <token>`. Tokens expire after 1 hour.
Registration always creates a `USER`. Admins are created only by the startup seeder or by an admin via `PUT /users/{id}`.

## Endpoints

| Method | Path | Access | Success |
|---|---|---|---|
| POST | `/auth/register` | public | 201 |
| POST | `/auth/login` | public | 200 |
| GET | `/products`, `/products/{id}` | public | 200 |
| POST | `/products` | authenticated | 201 |
| PUT | `/products/{id}` | owner or admin | 200 |
| DELETE | `/products/{id}` | owner or admin | 204 |
| GET | `/users`, `/users/{id}` | admin | 200 |
| PUT | `/users/{id}` | admin | 200 |
| DELETE | `/users/{id}` (also deletes their products) | admin | 204 |

### Examples
```bash
curl -k -X POST https://localhost:8443/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'
# 201 {"id":"...","name":"Alice","email":"alice@example.com","role":"USER"}

curl -k -X POST https://localhost:8443/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"password123"}'
# 200 {"token":"eyJ...","tokenType":"Bearer"}

curl -k -X POST https://localhost:8443/products -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"Lamp","description":"Desk lamp","price":19.99}'
# 201 {"id":"...","name":"Lamp","description":"Desk lamp","price":19.99,"userId":"..."}
```

## Errors
Every error uses the same JSON shape:
```json
{"timestamp":"2026-11-11T10:00:00Z","status":404,"error":"Not Found","message":"Product not found","path":"/products/abc"}
```

| Status | When |
|---|---|
| 400 | Validation failure, malformed JSON, wrong field type |
| 401 | Missing, invalid or expired token; wrong email or password |
| 403 | Not the owner or an admin |
| 404 | Resource not found |
| 405 / 415 | Unsupported method or content type |
| 409 | Email already registered |
| 429 | Rate limit exceeded (`Retry-After` header set) |
| 500 | Unexpected error: generic message, details only in server logs |

## Security
- Passwords hashed with BCrypt (salted); never returned by any endpoint.
- Request and response DTOs: clients cannot set `role` or product `userId`.
- User looked up on every request, so deleted or demoted users lose access immediately.
- Queries use Spring Data derived methods with typed fields (no raw query strings), so operator payloads like `{"$ne":""}` are rejected with 400.
- HTTPS only; secrets come from environment variables and are never committed.
- Login returns the same error for unknown emails and wrong passwords.

## Rate limiting and CORS
`/auth/**`: 5 requests/minute per IP. Everything else: 120/minute per IP.
Configurable via `app.rate-limit.*`. Only origins in `CORS_ORIGINS` are allowed.
````

---

## 6. Final checklist

- [ ] `./mvnw clean test` is fully green, with the `field` helper, `/error` permit and test properties updated
- [ ] The app starts on `https://localhost:8443`
- [ ] The hand-check commands return the expected statuses (200, 401, 201, 403, 400, 404, 405, 429)
- [ ] `git ls-files | grep -Ei 'keystore|\.p12|\.env$'` prints nothing, and `.env.example` is committed
- [ ] The README matches your actual setup