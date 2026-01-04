package com.music.aha.service;

import com.music.aha.model.RefreshToken;
import com.music.aha.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository repo;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationSeconds;

    public RefreshTokenService(RefreshTokenRepository repo, @Value("${refresh.expirationSeconds}") long refreshExpirationSeconds) {
        this.repo = repo;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    public RefreshToken createFor(String username) {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken rt = new RefreshToken();
        rt.setToken(token);
        rt.setUsername(username);
        rt.setExpiryDate(Instant.now().plusSeconds(refreshExpirationSeconds));
        rt.setRevoked(false);
        return repo.save(rt);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return repo.findByToken(token);
    }

    public RefreshToken rotate(RefreshToken old) {
        repo.delete(old);
        return createFor(old.getUsername());
    }

    public void revoke(RefreshToken t) { repo.delete(t); }

    public void revokeAllForUser(String username) { repo.deleteByUsername(username); }
}