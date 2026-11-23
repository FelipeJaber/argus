package br.com.argus.user;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import br.com.argus.shared.AuditableEntity;

@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(length = 80)
    private String sector;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
        // JPA
    }

    public User(String name, String email, String passwordHash, Role role, String sector) {
        this.name = Objects.requireNonNull(name).trim();
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.role = Objects.requireNonNull(role);
        this.sector = sector;
    }

    public static String normalizeEmail(String email) {
        return Objects.requireNonNull(email).trim().toLowerCase(Locale.ROOT);
    }

    // --- regras de autenticação ---

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Conta a falha e bloqueia temporariamente a conta ao atingir o limite. */
    public void registerFailedLogin(Instant now, int maxAttempts, Duration lockDuration) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash);
    }

    // --- administração ---

    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole);
    }

    public void changeSector(String newSector) {
        this.sector = newSector;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    // --- getters ---

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getSector() {
        return sector;
    }

    public boolean isActive() {
        return active;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
