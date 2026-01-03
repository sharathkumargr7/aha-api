package com.music.aha.controller;

import com.music.aha.model.RefreshToken;
import com.music.aha.model.User;
import com.music.aha.repository.UserRepository;
import com.music.aha.service.JwtUtils;
import com.music.aha.service.RefreshTokenService;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager, UserDetailsService userDetailsService, JwtUtils jwtUtils, RefreshTokenService refreshTokenService, PasswordEncoder passwordEncoder, UserRepository userRepository) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtUtils = jwtUtils;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) return ResponseEntity.badRequest().build();
        if (userRepository.findByUsername(username).isPresent()) return ResponseEntity.status(409).build();
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRoles("ROLE_USER");
        userRepository.save(u);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        try {
            String username = body.get("username");
            String password = body.get("password");
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            UserDetails ud = userDetailsService.loadUserByUsername(username);
            String access = jwtUtils.generateAccessToken(ud.getUsername());
            RefreshToken refresh = refreshTokenService.createFor(username);
            ResponseCookie cookie = ResponseCookie.from("refreshToken", refresh.getToken())
                    .httpOnly(true).secure(true).path("/api/auth/refresh")
                    .maxAge(Duration.ofSeconds(Long.parseLong(System.getProperty("refresh.expirationSeconds", "1209600"))))
                    .sameSite("None").build();
            response.addHeader("Set-Cookie", cookie.toString());
            return ResponseEntity.ok(Map.of("accessToken", access));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String token, HttpServletResponse response) {
        if (token == null) return ResponseEntity.status(401).build();
        return refreshTokenService.findByToken(token)
                .filter(rt -> rt.getExpiryDate().isAfter(java.time.Instant.now()))
                .map(rt -> {
                    RefreshToken newRt = refreshTokenService.rotate(rt);
                    UserDetails user = userDetailsService.loadUserByUsername(newRt.getUsername());
                    String access = jwtUtils.generateAccessToken(user.getUsername());
                    ResponseCookie cookie = ResponseCookie.from("refreshToken", newRt.getToken())
                            .httpOnly(true).secure(true).path("/api/auth/refresh")
                            .maxAge(Duration.ofSeconds(Long.parseLong(System.getProperty("refresh.expirationSeconds", "1209600"))))
                            .sameSite("None").build();
                    response.addHeader("Set-Cookie", cookie.toString());
                    return ResponseEntity.ok(Map.of("accessToken", access));
                })
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "refreshToken", required = false) String token, HttpServletResponse response) {
        if (token != null) {
            refreshTokenService.findByToken(token).ifPresent(refreshTokenService::revoke);
        }
        ResponseCookie delete = ResponseCookie.from("refreshToken", "")
                .httpOnly(true).secure(true).path("/api/auth/refresh").maxAge(0).sameSite("None").build();
        response.addHeader("Set-Cookie", delete.toString());
        return ResponseEntity.ok().build();
    }
}