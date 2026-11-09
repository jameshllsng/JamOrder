package br.com.jamorder.user;
import jakarta.validation.constraints.*;
public record CreateUserRequest(@NotBlank @Size(max = 80) String username, @NotBlank @Size(min = 12, max = 100) String password, @NotBlank @Size(max = 150) String displayName, @NotNull UserRole role) { }
