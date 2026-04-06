package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.*;
import com.caciopee.loganalyzer.entity.RefreshSession;
import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.repository.RefreshSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.*;
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
    private final AuditService auditService;
    private final long refreshTokenDays;

    public AuthService(AuthenticationManager authenticationManager,
                       UserAccountDetailsService userAccountDetailsService,
                       JwtService jwtService,
                       RefreshSessionRepository refreshSessionRepository,
                       AuditService auditService,
                       @Value("${app.security.jwt.refresh-token-days}") long refreshTokenDays) {
        this.authenticationManager = authenticationManager;
        this.userAccountDetailsService = userAccountDetailsService;
        this.jwtService = jwtService;
        this.refreshSessionRepository = refreshSessionRepository;
        this.auditService = auditService;
        this.refreshTokenDays = refreshTokenDays;
    }

    public AuthTokenResponse login(AuthLoginRequest request, String userAgent) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserAccount user = userAccountDetailsService.getByUsername(authentication.getName());

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
        session.setUserAgent(userAgent);
        refreshSessionRepository.save(session);

        auditService.log(
                "LOGIN",
                "AUTH",
                null,
                user.getUsername(),
                "User logged in"
        );

        return buildResponse(user, accessToken, rawRefreshToken);
    }

    public AuthTokenResponse refresh(AuthRefreshRequest request, String userAgent) {
        String rawRefreshToken = request.getRefreshToken();
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

        UserAccount user = userAccountDetailsService.getByUsername(existing.getUsername());

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
        newSession.setUserAgent(userAgent);
        refreshSessionRepository.save(newSession);

        auditService.log(
                "REFRESH_TOKEN",
                "AUTH",
                null,
                user.getUsername(),
                "Access token refreshed"
        );

        return buildResponse(user, newAccessToken, newRawRefreshToken);
    }

    public void logout(AuthLogoutRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return;
        }

        String tokenHash = hash(request.getRefreshToken());
        refreshSessionRepository.findByTokenHashAndRevokedFalse(tokenHash).ifPresent(session -> {
            session.setRevoked(true);
            session.setRevokedAt(LocalDateTime.now());
            refreshSessionRepository.save(session);

            auditService.log(
                    "LOGOUT",
                    "AUTH",
                    null,
                    session.getUsername(),
                    "User logged out"
            );
        });
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
        return Base64.getUrlEncoder().withoutPadding()
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
}