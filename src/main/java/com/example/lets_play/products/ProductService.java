package com.example.lets_play.products;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.lets_play.common.exception.ForbiddenException;
import com.example.lets_play.common.exception.ResourceNotFoundException;
import com.example.lets_play.products.dto.ProductRequest;
import com.example.lets_play.products.dto.ProductResponse;
import com.example.lets_play.security.AuthUser;

@Service 
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public List<ProductResponse> findAll() {
        return products.findAll().stream().map(ProductResponse::from).toList();
    }

    public ProductResponse findById(String id) {
        return ProductResponse.from(getOrThrow(id));
    }

    public ProductResponse create(ProductRequest req, AuthUser user) {
        Product p = new Product(req.name().trim(), req.description(), req.price(), user.id());
        return ProductResponse.from(products.save(p));
    }

    public ProductResponse update(String id, ProductRequest req, AuthUser user) {
        Product p = getOrThrow(id);
        requireOwnerOrAdmin(p, user);
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setPrice(req.price());
        return ProductResponse.from(products.save(p));
    }

    public void delete(String id, AuthUser user) {
        Product p = getOrThrow(id);
        requireOwnerOrAdmin(p, user);
        products.delete(p);
    }

    private Product getOrThrow(String id) {
        return products.findById(id).orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private void requireOwnerOrAdmin(Product p, AuthUser user) {
        if (!user.isAdmin() && !p.getUserId().equals(user.id())) {
            throw new ForbiddenException("You are not allowed to modify this product");
        }
    }
}