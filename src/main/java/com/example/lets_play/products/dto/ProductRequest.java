package com.example.lets_play.products.dto;

import jakarta.validation.constraints.*;

public record ProductRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    @NotNull @Positive Double price
) {}
