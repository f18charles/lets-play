package com.example.lets_play.products;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.lets_play.products.dto.ProductRequest;
import com.example.lets_play.products.dto.ProductResponse;
import com.example.lets_play.security.AuthUser;

import jakarta.validation.Valid;



@RestController 
@RequestMapping("/products")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProductResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable  String id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest req, @AuthenticationPrincipal AuthUser user) {
        return service.create(req, user);
    }
    
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody ProductRequest req, @AuthenticationPrincipal AuthUser user) {
        return service.update(id, req, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id, @AuthenticationPrincipal AuthUser user) {
        service.delete(id, user);
    }
}
