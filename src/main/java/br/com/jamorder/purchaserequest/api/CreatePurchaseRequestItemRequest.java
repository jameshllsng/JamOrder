package br.com.jamorder.purchaserequest.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreatePurchaseRequestItemRequest(
        @NotBlank(message = "A descriÃ§Ã£o do item Ã© obrigatÃ³ria.")
        @Size(max = 500, message = "A descriÃ§Ã£o do item deve ter no mÃ¡ximo 500 caracteres.")
        String description,

        @Size(max = 250, message = "Marca ou modelo deve ter no mÃ¡ximo 250 caracteres.")
        String brandModel,

        @Positive(message = "A quantidade deve ser maior que zero.")
        int quantity,

        @Size(max = 200, message = "Fornecedor deve ter no mÃ¡ximo 200 caracteres.")
        String supplierName,

        @NotNull(message = "O valor total do item Ã© obrigatÃ³rio.")
        @DecimalMin(value = "0.00", message = "O valor total nÃ£o pode ser negativo.")
        BigDecimal totalValue,

        @Size(max = 2000, message = "As observaÃ§Ãµes do item devem ter no mÃ¡ximo 2000 caracteres.")
        String notes) {
}
