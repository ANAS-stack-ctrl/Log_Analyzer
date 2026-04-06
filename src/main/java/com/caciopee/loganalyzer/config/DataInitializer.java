package com.caciopee.loganalyzer.config;

import com.caciopee.loganalyzer.entity.UserAccount;
import com.caciopee.loganalyzer.entity.UserRole;
import com.caciopee.loganalyzer.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserAccountRepository userAccountRepository,
                                PasswordEncoder passwordEncoder) {
        return args -> {
            if (userAccountRepository.count() > 0) {
                return;
            }

            userAccountRepository.save(buildUser(
                    "admin",
                    "Admin",
                    "admin@local",
                    "admin123",
                    UserRole.ADMIN,
                    passwordEncoder
            ));

            userAccountRepository.save(buildUser(
                    "analyst",
                    "Analyst",
                    "analyst@local",
                    "analyst123",
                    UserRole.ANALYST,
                    passwordEncoder
            ));

            userAccountRepository.save(buildUser(
                    "manager",
                    "Manager",
                    "manager@local",
                    "manager123",
                    UserRole.MANAGER,
                    passwordEncoder
            ));

            userAccountRepository.save(buildUser(
                    "viewer",
                    "Viewer",
                    "viewer@local",
                    "viewer123",
                    UserRole.VIEWER,
                    passwordEncoder
            ));
        };
    }

    private UserAccount buildUser(String username,
                                  String displayName,
                                  String email,
                                  String rawPassword,
                                  UserRole role,
                                  PasswordEncoder passwordEncoder) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(true);
        return user;
    }
}