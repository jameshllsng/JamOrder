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

public record UpdatePurchaseRequestRequest(
        @Size(max = 50, message = "O número da requisição deve ter no máximo 50 caracteres.")
        String requestNumber,

        // Optional for users bound to a department: their own department is used.
        Long departmentId,

        @Size(max = 120, message = "O local de uso deve ter no máximo 120 caracteres.")
        String usageLocation,

        @NotNull(message = "A data do pedido é obrigatória.")
        @PastOrPresent(message = "A data do pedido não pode estar no futuro.")
        LocalDate requestedOn,

        @PastOrPresent(message = "A data da requisição no TOTVS não pode estar no futuro.")
        LocalDate totvsRequestedOn,

        @NotBlank(message = "O motivo da compra é obrigatório.")
        String purchaseReason,

        @NotNull(message = "A prioridade é obrigatória.")
        Priority priority,

        @Size(max = 80, message = "O centro de custo deve ter no máximo 80 caracteres.")
        String costCenter,

        @Size(max = 5000, message = "As observações devem ter no máximo 5000 caracteres.")
        String notes,

        @NotEmpty(message = "A requisição deve possuir ao menos um item.")
        List<@Valid CreatePurchaseRequestItemRequest> items) {
}
