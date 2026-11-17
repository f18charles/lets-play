package com.example.lets_play.users.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}