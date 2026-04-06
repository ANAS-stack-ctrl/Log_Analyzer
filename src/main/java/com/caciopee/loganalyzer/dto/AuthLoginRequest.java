package com.caciopee.loganalyzer.dto;

public class AuthLoginRequest {

    private String username;
    private String password;

    public AuthLoginRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}