package com.caciopee.loganalyzer.dto;

public class AuthRefreshRequest {

    private String refreshToken;

    public AuthRefreshRequest() {
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}