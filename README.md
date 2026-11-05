# Micro e-com

This is a project for a tiny e-commerce api. It should only contain users and the products. This project's purpose is to act as an introduction to my entry into spring boot.

## Structure

### 1. Database Design
User and Product, have a one-to-many relationship — each user can own multiple products.
```
classDiagram
    User "1" -- "n" Product : Owns
    User : +String id
    User : +String name
    User : +String email
    User : +String password
    User : +String role
    Product : +String id
    Product : +String name
    Product : +String description
    Product : +Double price
    Product : +String userId
```

### 2. API Development

__GET__ /products           → public access (no authentication required)

__POST__ /products          → create a new product (authenticated users only)

__PUT__ /products/{id}      → restricted to product owners or admins

__DELETE__ /products/{id}   → restricted to product owners or admins

__GET__ /users              → accessible to admins only



### 3. Authentication & Authorization
JWT-based authentication using Spring Security.

Allows users to register, log in, and receive tokens for authentication.

Restricted access is based on roles:

> __Admin__: manages all users and products

> __User__: manages only their own products


### 4. Error Handling
__to be added__

### 5. Security Measures
Hash and salt passwords using BCrypt before saving to the database.

Validates and sanitizes user inputs to prevent MongoDB injection attacks.

Excludes sensitive fields (e.g., password) from API responses.

Uses HTTPS for secure data transmission.

Applied strict access control on all endpoints.