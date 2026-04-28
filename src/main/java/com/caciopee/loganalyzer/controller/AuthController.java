package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.AuthLoginRequest;
import com.caciopee.loganalyzer.dto.AuthLogoutRequest;
import com.caciopee.loganalyzer.dto.AuthRefreshRequest;
import com.caciopee.loganalyzer.dto.AuthTokenResponse;
import com.caciopee.loganalyzer.dto.UserSummaryResponse;
import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.service.AuthService;
import com.caciopee.loganalyzer.service.UserAccountDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final UserAccountDetailsService userAccountDetailsService;

    public AuthController(AuthService authService,
                          UserAccountDetailsService userAccountDetailsService) {
        this.authService = authService;
        this.userAccountDetailsService = userAccountDetailsService;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody AuthLoginRequest request,
                                   HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody AuthRefreshRequest request,
                                     HttpServletRequest httpRequest) {
        return authService.refresh(request, httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody AuthLogoutRequest request) {
        authService.logout(request);
    }

    @GetMapping("/me")
    public UserSummaryResponse me(Authentication authentication) {
        UserAccount user = userAccountDetailsService.getActiveUserByUsername(authentication.getName());

        UserSummaryResponse response = new UserSummaryResponse();
        response.setUsername(user.getUsername());
        response.setDisplayName(user.getDisplayName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole().name());

        return response;
    }
}