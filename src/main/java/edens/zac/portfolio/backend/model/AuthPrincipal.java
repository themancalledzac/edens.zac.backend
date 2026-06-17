package edens.zac.portfolio.backend.model;

import edens.zac.portfolio.backend.types.Role;

public record AuthPrincipal(Long userId, String email, Role role, boolean mfaSatisfied) {}
