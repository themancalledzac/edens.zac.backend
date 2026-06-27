package edens.zac.portfolio.backend.model;

public record AuthPrincipal(Long userId, String email, boolean mfaSatisfied) {}
