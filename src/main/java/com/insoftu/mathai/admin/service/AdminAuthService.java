package com.insoftu.mathai.admin.service;

import com.insoftu.mathai.admin.dto.LoginResponse;
import com.insoftu.mathai.admin.model.AdminTotp;
import com.insoftu.mathai.admin.model.AdminUser;
import com.insoftu.mathai.admin.repository.AdminTotpRepository;
import com.insoftu.mathai.admin.repository.AdminUserRepository;
import com.insoftu.mathai.admin.security.JwtUtil;
import com.insoftu.mathai.admin.security.TotpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminAuthService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);
    private final AdminUserRepository adminUserRepository;
    private final AdminTotpRepository adminTotpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TotpUtil totpUtil;

    public AdminAuthService(AdminUserRepository adminUserRepository,
                            AdminTotpRepository adminTotpRepository,
                            PasswordEncoder passwordEncoder,
                            JwtUtil jwtUtil,
                            TotpUtil totpUtil) {
        this.adminUserRepository = adminUserRepository;
        this.adminTotpRepository = adminTotpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.totpUtil = totpUtil;
    }

    /**
     * First step of login: validates email/password.
     * If 2FA is enabled, returns requiresTwoFactor=true without a final token.
     * Otherwise, returns a JWT token directly.
     */
    public LoginResponse login(String email, String password) {
        AdminUser user = adminUserRepository.findByEmail(email)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Failed login attempt for email: {}", email);
            throw new IllegalArgumentException("Invalid email or password");
        }

        AdminTotp totp = adminTotpRepository.findByAdminUserId(user.getId()).orElse(null);

        if (totp != null && totp.isEnabled()) {
            // 2FA is enabled — issue a short-lived temp token for the 2FA step
            String tempToken = jwtUtil.createToken(user.getId(), email);
            return new LoginResponse(tempToken, true, "2FA required — enter your authenticator code");
        }

        String token = jwtUtil.createToken(user.getId(), email);
        log.info("Admin '{}' logged in successfully.", email);
        return new LoginResponse(token, false, "Login successful");
    }

    /**
     * Verifies the 2FA code and returns the final JWT if valid.
     */
    public LoginResponse verifyTwoFactor(String email, String code, String tempToken) {
        var claims = jwtUtil.validateToken(tempToken);
        if (claims == null) {
            throw new IllegalArgumentException("Session expired. Please login again.");
        }

        AdminUser user = adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        AdminTotp totp = adminTotpRepository.findByAdminUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("2FA not configured"));

        if (!totpUtil.verifyCode(code, totp.getSecret())) {
            throw new IllegalArgumentException("Invalid 2FA code");
        }

        String token = jwtUtil.createToken(user.getId(), email);
        log.info("Admin '{}' completed 2FA verification.", email);
        return new LoginResponse(token, false, "Login successful");
    }

    /**
     * Begins 2FA setup: generates a secret and returns a QR code data URI.
     */
    @Transactional
    public String beginTwoFactorSetup(UUID adminUserId, String email) {
        String secret = totpUtil.generateSecret();

        // Delete any existing incomplete setup
        adminTotpRepository.deleteByAdminUserId(adminUserId);

        AdminTotp totp = new AdminTotp(adminUserId, secret);
        adminTotpRepository.save(totp);

        return totpUtil.generateQrDataUri(email, secret);
    }

    /**
     * Verifies the setup code and enables 2FA.
     */
    @Transactional
    public void verifyAndEnableTwoFactor(UUID adminUserId, String code) {
        AdminTotp totp = adminTotpRepository.findByAdminUserId(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("2FA setup not initiated. Please scan QR code first."));

        if (!totpUtil.verifyCode(code, totp.getSecret())) {
            throw new IllegalArgumentException("Invalid verification code. Please try again.");
        }

        totp.setEnabled(true);
        adminTotpRepository.save(totp);
        log.info("2FA enabled for admin user: {}", adminUserId);
    }

    /**
     * Disables 2FA for the given admin user.
     */
    @Transactional
    public void disableTwoFactor(UUID adminUserId) {
        adminTotpRepository.deleteByAdminUserId(adminUserId);
        log.info("2FA disabled for admin user: {}", adminUserId);
    }

    /**
     * Returns whether 2FA is currently enabled.
     */
    public boolean isTwoFactorEnabled(UUID adminUserId) {
        return adminTotpRepository.findByAdminUserId(adminUserId)
                .map(AdminTotp::isEnabled)
                .orElse(false);
    }
}
