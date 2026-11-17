package com.example.lets_play.users.dto;

import jakarta.validation.constraints.*;
import com.example.lets_play.users.models.*;

public record UserResponse(String id, String name, String email, Role role) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}