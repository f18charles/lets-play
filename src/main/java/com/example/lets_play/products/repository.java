package com.example.lets_play.products;

import org.springframework.data.mongodb.repository.Mongorepository;
import java.util.List;

public interface ProductRepository extends Mongorepository<Product, String> {
    List<Product> findByUserId(String userId);
    void deleteByUserId(String userId);
}