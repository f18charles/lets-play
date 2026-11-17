package com.example.lets_play.users.dto;

import jakarta.validation.constraints.*;
import com.example.lets_play.users.models.Role;

public record UpdateUserRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 254) String email,
    @Size(min = 8, max = 72) String password,
    Role role
)