package com.example.lets_play.users;

import com.example.lets_play.users.models.User;
import org.springframework.data.mongodb.repository.Mongorepository;
import java.util.Optional;

public interface UserRepository extends Mongorepository<User, String> {
    Optional<User> findByEmail(string email);
    boolean existsByEmail(String email);
}