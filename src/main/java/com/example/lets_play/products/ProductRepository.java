package com.example.lets_play.products;

import com.example.lets_play.products.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProductRepository extends MongoRepository<Product, String> {
    List<Product> findByUserId(String userId);
    void deleteByUserId(String userId);
}