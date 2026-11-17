package com.example.lets_play.users;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

public enum Role { USER, ADMIN }

@Document(collection = "users")
public class User{
    @Id
    private String id;
    private String name;
    @Indexed(unique = true)
    private String email;
    private String password;
    private Role role;

    public User() {}

    public User(String name, String email, String password, Role role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public void setId(string id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setName(string name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public void setEmail(string email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
    
    public void setRole(Role role) {
        this.role = role;
    }

    public String getRole() {
        return role;
    }
    
    public void setPassword(string password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }
}