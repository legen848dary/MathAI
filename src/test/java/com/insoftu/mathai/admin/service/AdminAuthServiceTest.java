package com.insoftu.mathai.admin.service;

import com.insoftu.mathai.admin.dto.LoginResponse;
import com.insoftu.mathai.admin.model.AdminTotp;
import com.insoftu.mathai.admin.model.AdminUser;
import com.insoftu.mathai.admin.repository.AdminTotpRepository;
import com.insoftu.mathai.admin.repository.AdminUserRepository;
import com.insoftu.mathai.admin.security.JwtUtil;
import com.insoftu.mathai.admin.security.TotpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AdminTotpRepository adminTotpRepository;
    @Mock private JwtUtil jwtUtil;
    @Mock private TotpUtil totpUtil;

    private PasswordEncoder passwordEncoder;
    private AdminAuthService service;

    private final UUID adminId = UUID.randomUUID();
    private final String email = "admin@example.com";
    private final String password = "password123";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new AdminAuthService(
                adminUserRepository, adminTotpRepository,
                passwordEncoder, jwtUtil, totpUtil
        );
    }

    @Test
    void shouldLoginWithoutTwoFactor() {
        AdminUser user = createAdminUser();
        when(adminUserRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.empty());
        when(jwtUtil.createToken(adminId, email)).thenReturn("jwt-token");

        LoginResponse response = service.login(email, password);

        assertFalse(response.requiresTwoFactor());
        assertEquals("jwt-token", response.token());
        assertEquals("Login successful", response.message());
    }

    @Test
    void shouldRequireTwoFactorWhenEnabled() {
        AdminUser user = createAdminUser();
        AdminTotp totp = new AdminTotp(adminId, "secret");
        totp.setEnabled(true);

        when(adminUserRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.of(totp));
        when(jwtUtil.createToken(adminId, email)).thenReturn("temp-token");

        LoginResponse response = service.login(email, password);

        assertTrue(response.requiresTwoFactor());
        assertEquals("temp-token", response.token());
        assertTrue(response.message().contains("2FA"));
    }

    @Test
    void shouldRejectInvalidPassword() {
        AdminUser user = createAdminUser();
        when(adminUserRepository.findByEmail(email)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class,
                () -> service.login(email, "wrong-password"));
    }

    @Test
    void shouldRejectUnknownUser() {
        when(adminUserRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.login(email, password));
    }

    @Test
    void shouldVerifyTwoFactorSuccessfully() {
        AdminUser user = createAdminUser();
        AdminTotp totp = new AdminTotp(adminId, "secret");
        totp.setEnabled(true);

        when(adminUserRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.of(totp));
        when(totpUtil.verifyCode("123456", "secret")).thenReturn(true);

        // Use a real JwtUtil to create temp token and validate the response
        var realJwtUtil = new JwtUtil("2fa-test-secret", 30);
        String tempToken = realJwtUtil.createToken(adminId, email);

        var realJwtService = new AdminAuthService(
                adminUserRepository, adminTotpRepository,
                passwordEncoder, realJwtUtil, totpUtil
        );

        LoginResponse response = realJwtService.verifyTwoFactor(email, "123456", tempToken);

        assertFalse(response.requiresTwoFactor());
        assertNotNull(response.token());
        assertEquals("Login successful", response.message());

        // Verify returned token is valid
        var validClaims = realJwtUtil.validateToken(response.token());
        assertNotNull(validClaims);
        assertEquals(adminId.toString(), validClaims.getSubject());
    }

    @Test
    void shouldBeginTwoFactorSetup() {
        when(totpUtil.generateSecret()).thenReturn("TESTSECRET123");
        when(totpUtil.generateQrDataUri(email, "TESTSECRET123"))
                .thenReturn("data:image/png;base64,qrcode");

        String qrUri = service.beginTwoFactorSetup(adminId, email);

        assertTrue(qrUri.startsWith("data:image/png;base64,"));
        verify(adminTotpRepository).deleteByAdminUserId(adminId);
        verify(adminTotpRepository).save(any(AdminTotp.class));
    }

    @Test
    void shouldEnableTwoFactorAfterVerification() {
        AdminTotp totp = new AdminTotp(adminId, "TESTSECRET123");
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.of(totp));
        when(totpUtil.verifyCode("123456", "TESTSECRET123")).thenReturn(true);

        service.verifyAndEnableTwoFactor(adminId, "123456");

        assertTrue(totp.isEnabled());
        verify(adminTotpRepository).save(totp);
    }

    @Test
    void shouldRejectWrongSetupCode() {
        AdminTotp totp = new AdminTotp(adminId, "TESTSECRET123");
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.of(totp));
        when(totpUtil.verifyCode("000000", "TESTSECRET123")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyAndEnableTwoFactor(adminId, "000000"));
    }

    @Test
    void shouldDisableTwoFactor() {
        service.disableTwoFactor(adminId);
        verify(adminTotpRepository).deleteByAdminUserId(adminId);
    }

    @Test
    void shouldReportTwoFactorStatus() {
        AdminTotp totp = new AdminTotp(adminId, "secret");
        totp.setEnabled(true);
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.of(totp));

        assertTrue(service.isTwoFactorEnabled(adminId));
    }

    @Test
    void shouldReportTwoFactorDisabled() {
        when(adminTotpRepository.findByAdminUserId(adminId)).thenReturn(Optional.empty());
        assertFalse(service.isTwoFactorEnabled(adminId));
    }

    private AdminUser createAdminUser() {
        AdminUser user = new AdminUser(email, passwordEncoder.encode(password));
        try {
            var idField = AdminUser.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, adminId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
