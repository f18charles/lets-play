package com.example.lets_play.products;

import jakarta.validation.constraints.*;

public record ProductRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    @NotNull @Positive Double price
) {}

public record ProductResponse(String id, String name, String description, Double price, String userId) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getUserId());
    }
}