package com.example.lets_play.users.services;

import com.example.lets_play.users.UserRepository;
import com.example.lets_play.users.dto.*;
import com.example.lets_play.common.exception.*;
import com.example.lets_play.users.models.*;
import com.example.lets_play.products.ProductRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository users;
    private final ProductRepository products;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, ProductRepository products, PasswordEncoder encoder) {
        this.users = users;
        this.products = products;
        this.encoder = encoder;
    }

    public User register(RegisterRequest req) {
        String email = normalize(req.email());
        if (users.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        User user = new User(req.name(), email, encoder.encode(req.password()), Role.USER);
        try {
            return users.save(user);
        } catch (DuplicateKeyException e) {
            throw new DuplicateResourceException("Email is already registered");
        }
    }

    public List<UserResponse> findAll() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse findById(String id) {
        return UserResponse.from(getOrThrow(id));
    }

    public UserResponse update(String id, UpdateUserRequest req) {
        User user = getOrThrow(id);
        String email = normalize(req.email());
        if (!email.equals(user.getEmail()) && users.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }

        user.setName(req.name().trim());
        user.setEmail(email);

        if (req.password() != null) user.setPassword(encoder.encode(req.password()));
        if (req.role() != null) user.setRole(req.role());
        try {
            return UserResponse.from(users.save(user));
        } catch (DuplicateKeyException e) {
            throw new DuplicateResourceException("Email is already registered");
        }
    }

    public void delete(String id) {
        User user = getOrThrow(id);
        products.deleteByUserId(user.getId());
        users.delete(user);
    }

    private User getOrThrow(String id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User Not Found"));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}