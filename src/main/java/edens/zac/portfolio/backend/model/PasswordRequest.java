package edens.zac.portfolio.backend.model;

import jakarta.validation.constraints.NotBlank;

public record PasswordRequest(@NotBlank(message = "Password is required") String password) {}
