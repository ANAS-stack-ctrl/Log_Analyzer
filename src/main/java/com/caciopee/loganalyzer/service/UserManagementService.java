package com.caciopee.loganalyzer.service;

import com.caciopee.loganalyzer.dto.UserCreateRequest;
import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.entity.UserRole;
import com.caciopee.loganalyzer.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserManagementService {

    private final UserAccountRepository repo;
    private final PasswordEncoder encoder;
    private final AuditService auditService;

    public UserManagementService(UserAccountRepository repo,
                                 PasswordEncoder encoder,
                                 AuditService auditService) {
        this.repo = repo;
        this.encoder = encoder;
        this.auditService = auditService;
    }

    public UserAccount createUser(UserCreateRequest req, String admin) {
        UserAccount user = new UserAccount();
        user.setUsername(req.getUsername());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setRole(UserRole.valueOf(req.getRole()));
        user.setActive(true);

        UserAccount saved = repo.save(user);

        auditService.log(
                "CREATE_USER",
                "USER",
                saved.getId(),
                admin,
                "User created: " + saved.getUsername()
        );

        return saved;
    }

    public List<UserAccount> getAll() {
        return repo.findAll();
    }

    public UserAccount toggleActive(Long id, String admin) {
        UserAccount user = repo.findById(id).orElseThrow();
        user.setActive(!user.getActive());

        UserAccount saved = repo.save(user);

        auditService.log(
                "TOGGLE_USER",
                "USER",
                saved.getId(),
                admin,
                "Active = " + saved.getActive()
        );

        return saved;
    }
}