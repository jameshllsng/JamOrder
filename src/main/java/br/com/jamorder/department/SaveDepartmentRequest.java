package br.com.jamorder.department;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveDepartmentRequest(
        @NotBlank(message = "O nome do setor é obrigatório.")
        @Size(max = 120, message = "O nome do setor deve ter no máximo 120 caracteres.")
        String name,

        boolean active) {
}
