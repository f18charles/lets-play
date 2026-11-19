package com.example.lets_play.users;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.lets_play.common.exception.AuthenticationFailedException;
import com.example.lets_play.security.JWTService;
import com.example.lets_play.users.dto.AuthResponse;
import com.example.lets_play.users.dto.LoginRequest;
import com.example.lets_play.users.models.User;

@Service 
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JWTService jwt;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JWTService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email().trim().toLowerCase()).orElse(null);
        boolean ok = encoder.matches(req.password(), user != null ? user.getPassword() : dummyHash);
        if (user == null || !ok) {
            throw new AuthenticationFailedException("Invalid email or password");
        }
        return new AuthResponse(jwt.generateToken(user));
    }
}
