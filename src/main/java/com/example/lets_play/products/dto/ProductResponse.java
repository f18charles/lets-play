package com.example.lets_play.products.dto;

import com.example.lets_play.products.Product;

public record ProductResponse(String id, String name, String description, Double price, String userId) {
    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getPrice(), p.getUserId());
    }
}