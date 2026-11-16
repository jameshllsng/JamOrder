package br.com.jamorder.user;
import jakarta.validation.constraints.*;
public record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 100) String password) { }
