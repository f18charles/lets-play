package com.example.lets_play.security;

import com.example.lets_play.users.models.Role;

public record AuthUser(String id, String email, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}