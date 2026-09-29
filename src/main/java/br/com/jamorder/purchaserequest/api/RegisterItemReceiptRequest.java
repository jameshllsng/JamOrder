package br.com.jamorder.purchaserequest.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record RegisterItemReceiptRequest(
        @NotNull @Min(1) Integer receivedQuantity,
        @NotNull @PastOrPresent LocalDate receivedOn) {
}
