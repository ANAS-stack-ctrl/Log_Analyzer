package com.caciopee.loganalyzer.controller;

import com.caciopee.loganalyzer.dto.UserCreateRequest;
import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class UserManagementController {

    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public UserAccount create(@RequestBody UserCreateRequest req, Authentication auth) {
        return service.createUser(req, auth.getName());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<UserAccount> getAll() {
        return service.getAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/toggle")
    public UserAccount toggle(@PathVariable Long id, Authentication auth) {
        return service.toggleActive(id, auth.getName());
    }
}