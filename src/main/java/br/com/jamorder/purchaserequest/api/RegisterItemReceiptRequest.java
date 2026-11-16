package br.com.jamorder.purchaserequest.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record RegisterItemReceiptRequest(
        @NotNull(message = "A quantidade recebida é obrigatória.")
        @Min(value = 1, message = "A quantidade recebida deve ser maior que zero.")
        Integer receivedQuantity,

        @NotNull(message = "A data do recebimento é obrigatória.")
        @PastOrPresent(message = "A data do recebimento não pode estar no futuro.")
        LocalDate receivedOn) {
}
