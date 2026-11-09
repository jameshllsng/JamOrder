package br.com.jamorder.user;
import jakarta.validation.constraints.*;
public record UpdateUserRequest(@NotBlank @Size(max = 150) String displayName, @NotNull UserRole role, boolean active) { }
