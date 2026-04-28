package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.AuthLoginRequest;
import com.caciopee.loganalyzer.dto.AuthLogoutRequest;
import com.caciopee.loganalyzer.dto.AuthRefreshRequest;
import com.caciopee.loganalyzer.dto.AuthTokenResponse;
import com.caciopee.loganalyzer.entity.RefreshSession;
import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.repository.RefreshSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountDetailsService userAccountDetailsService;
    private final JwtService jwtService;
    private final RefreshSessionRepository refreshSessionRepository;
    private final long refreshTokenDays;

    public AuthService(AuthenticationManager authenticationManager,
                       UserAccountDetailsService userAccountDetailsService,
                       JwtService jwtService,
                       RefreshSessionRepository refreshSessionRepository,
                       @Value("${app.security.jwt.refresh-token-days}") long refreshTokenDays) {
        this.authenticationManager = authenticationManager;
        this.userAccountDetailsService = userAccountDetailsService;
        this.jwtService = jwtService;
        this.refreshSessionRepository = refreshSessionRepository;
        this.refreshTokenDays = refreshTokenDays;
    }

    public AuthTokenResponse login(AuthLoginRequest request, String userAgent) {
        validateLoginRequest(request);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername().trim(),
                        request.getPassword()
                )
        );

        UserAccount user = userAccountDetailsService.getActiveUserByUsername(authentication.getName());

        String accessToken = jwtService.generateAccessToken(
                user.getUsername(),
                user.getRole().name(),
                user.getDisplayName()
        );

        String rawRefreshToken = generateRefreshToken();

        RefreshSession session = new RefreshSession();
        session.setUsername(user.getUsername());
        session.setTokenHash(hash(rawRefreshToken));
        session.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        session.setUserAgent(safeUserAgent(userAgent));
        refreshSessionRepository.save(session);

        return buildResponse(user, accessToken, rawRefreshToken);
    }

    public AuthTokenResponse refresh(AuthRefreshRequest request, String userAgent) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new BadCredentialsException("Refresh token is required");
        }

        String rawRefreshToken = request.getRefreshToken().trim();
        String tokenHash = hash(rawRefreshToken);

        RefreshSession existing = refreshSessionRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.getExpiresAt().isBefore(LocalDateTime.now())) {
            existing.setRevoked(true);
            existing.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(existing);
            throw new BadCredentialsException("Refresh token expired");
        }

        existing.setRevoked(true);
        existing.setRevokedAt(LocalDateTime.now());
        refreshSessionRepository.save(existing);

        UserAccount user = userAccountDetailsService.getActiveUserByUsername(existing.getUsername());

        String newAccessToken = jwtService.generateAccessToken(
                user.getUsername(),
                user.getRole().name(),
                user.getDisplayName()
        );

        String newRawRefreshToken = generateRefreshToken();

        RefreshSession newSession = new RefreshSession();
        newSession.setUsername(user.getUsername());
        newSession.setTokenHash(hash(newRawRefreshToken));
        newSession.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenDays));
        newSession.setUserAgent(safeUserAgent(userAgent));
        refreshSessionRepository.save(newSession);

        return buildResponse(user, newAccessToken, newRawRefreshToken);
    }

    public void logout(AuthLogoutRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return;
        }

        String tokenHash = hash(request.getRefreshToken().trim());

        refreshSessionRepository.findByTokenHashAndRevokedFalse(tokenHash).ifPresent(session -> {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);
        });
    }

    private void validateLoginRequest(AuthLoginRequest request) {
        if (request == null) {
            throw new BadCredentialsException("Login request is missing");
        }
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new BadCredentialsException("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new BadCredentialsException("Password is required");
        }
    }

    private AuthTokenResponse buildResponse(UserAccount user, String accessToken, String refreshToken) {
        AuthTokenResponse response = new AuthTokenResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType("Bearer");
        response.setExpiresInSeconds(jwtService.getAccessTokenMinutes() * 60);
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setRole(user.getRole().name());
        return response;
    }

    private String generateRefreshToken() {
        String raw = UUID.randomUUID() + ":" + UUID.randomUUID();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash refresh token", ex);
        }
    }

    private String safeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent;
    }
}