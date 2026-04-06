package com.caciopee.loganalyzer.dto;

public class AuthLogoutRequest {

    private String refreshToken;

    public AuthLogoutRequest() {
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}