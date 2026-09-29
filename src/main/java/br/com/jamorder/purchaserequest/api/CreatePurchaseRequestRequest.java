package br.com.jamorder.purchaserequest.api;

import br.com.jamorder.purchaserequest.domain.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseRequestRequest(
        @NotBlank(message = "O nÃºmero da requisiÃ§Ã£o Ã© obrigatÃ³rio.")
        @Size(max = 50, message = "O nÃºmero da requisiÃ§Ã£o deve ter no mÃ¡ximo 50 caracteres.")
        String requestNumber,

        @NotBlank(message = "O setor Ã© obrigatÃ³rio.")
        @Size(max = 120, message = "O setor deve ter no mÃ¡ximo 120 caracteres.")
        String department,

        @NotBlank(message = "O solicitante Ã© obrigatÃ³rio.")
        @Size(max = 150, message = "O solicitante deve ter no mÃ¡ximo 150 caracteres.")
        String requesterName,

        @NotNull(message = "A data da solicitaÃ§Ã£o Ã© obrigatÃ³ria.")
        @PastOrPresent(message = "A data da solicitaÃ§Ã£o nÃ£o pode estar no futuro.")
        LocalDate requestedOn,

        @NotBlank(message = "O motivo da compra Ã© obrigatÃ³rio.")
        String purchaseReason,

        @NotNull(message = "A prioridade Ã© obrigatÃ³ria.")
        Priority priority,

        @Size(max = 150, message = "O comprador deve ter no mÃ¡ximo 150 caracteres.")
        String buyerName,

        @Size(max = 150, message = "O gestor deve ter no mÃ¡ximo 150 caracteres.")
        String managerName,

        @Size(max = 80, message = "O centro de custo deve ter no mÃ¡ximo 80 caracteres.")
        String costCenter,

        @Size(max = 5000, message = "As observaÃ§Ãµes devem ter no mÃ¡ximo 5000 caracteres.")
        String notes,

        @NotEmpty(message = "A requisiÃ§Ã£o deve possuir ao menos um item.")
        List<@Valid CreatePurchaseRequestItemRequest> items) {
}
