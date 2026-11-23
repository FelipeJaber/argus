package br.com.argus.auth;

public enum RevocationReason {
    LOGOUT,
    LOGOUT_ALL,
    REVOKED_BY_USER,
    TOKEN_REUSE_DETECTED,
    PASSWORD_CHANGED,
    USER_DEACTIVATED,
    ROLE_CHANGED
}
