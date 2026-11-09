package br.com.jamorder.purchaserequest.service;

import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestItemRequest;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.api.PurchaseRequestResponse;
import br.com.jamorder.purchaserequest.api.RegisterItemReceiptRequest;
import br.com.jamorder.purchaserequest.api.UpdatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.domain.Priority;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseRequestServiceIntegrationTests {

    private static final String ACTOR = "test-user";

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Test
    void createsRegisteredRequisitionWithItemsAndCalculatesTotalValue() {
        PurchaseRequestResponse response = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Network switch", 2, new BigDecimal("1200.00")),
                item("Network cable", 10, new BigDecimal("150.50")))), ACTOR);

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(response.costCenter()).isEqualTo("CC-100");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> assertThat(item.status())
                .isEqualTo(PurchaseRequestItemStatus.PENDING));
        assertThat(response.totalValue()).isEqualByComparingTo("1350.50");
    }

    @Test
    void createsDraftWhenTotvsNumberIsMissing() {
        PurchaseRequestResponse response = purchaseRequestService.create(createRequest(null, List.of(
                item("EPI holder", 1, null))), ACTOR);

        assertThat(response.status()).isEqualTo(PurchaseRequestStatus.DRAFT);
        assertThat(response.requestNumber()).isNull();
        assertThat(response.totvsRequestedOn()).isNull();
        assertThat(response.items().getFirst().totalValue()).isNull();
        assertThat(response.totalValue()).isEqualByComparingTo("0");
    }

    @Test
    void registeringDraftInTotvsMakesItOpenAndRecordsHistory() {
        PurchaseRequestResponse draft = purchaseRequestService.create(createRequest(null, List.of(
                item("EPI holder", 1, null))), ACTOR);
        String number = uniqueNumber();

        PurchaseRequestResponse registered = purchaseRequestService.update(draft.id(), updateRequest(number), ACTOR);

        assertThat(registered.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(registered.requestNumber()).isEqualTo(number);
        assertThat(purchaseRequestService.findHistory(draft.id()))
                .extracting(entry -> entry.action())
                .contains("REGISTERED_IN_TOTVS");
    }

    @Test
    void rejectsNumberWithoutTotvsDate() {
        CreatePurchaseRequestRequest request = new CreatePurchaseRequestRequest(
                uniqueNumber(), "TI", LocalDate.now(), null, "Stock.", Priority.URGENT, null, null,
                List.of(item("Battery", 4, null)));

        assertThatThrownBy(() -> purchaseRequestService.create(request, ACTOR))
                .isInstanceOf(InvalidTotvsRegistrationException.class);
    }

    @Test
    void openRequisitionCannotGoBackToDraft() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Battery", 4, null))), ACTOR);

        assertThatThrownBy(() -> purchaseRequestService.update(open.id(), updateRequest(null), ACTOR))
                .isInstanceOf(InvalidTotvsRegistrationException.class);
    }

    @Test
    void draftDoesNotAcceptReceipts() {
        PurchaseRequestResponse draft = purchaseRequestService.create(createRequest(null, List.of(
                item("EPI holder", 1, null))), ACTOR);
        long itemId = draft.items().getFirst().id();

        assertThatThrownBy(() -> purchaseRequestService.registerReceipt(
                draft.id(), itemId, new RegisterItemReceiptRequest(1, LocalDate.now()), ACTOR))
                .isInstanceOf(PurchaseRequestNotOpenException.class);
    }

    @Test
    void receiptsMoveRequisitionToPartiallyReceivedAndThenCompleted() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Patch cord", 8, null),
                item("Patch panel", 1, null))), ACTOR);
        long patchCordId = open.items().get(0).id();
        long patchPanelId = open.items().get(1).id();

        PurchaseRequestResponse partial = purchaseRequestService.registerReceipt(
                open.id(), patchCordId, new RegisterItemReceiptRequest(3, LocalDate.now()), ACTOR);
        assertThat(partial.status()).isEqualTo(PurchaseRequestStatus.PARTIALLY_RECEIVED);
        assertThat(partial.items().get(0).status()).isEqualTo(PurchaseRequestItemStatus.PARTIALLY_RECEIVED);

        purchaseRequestService.registerReceipt(open.id(), patchCordId, new RegisterItemReceiptRequest(5, LocalDate.now()), ACTOR);
        PurchaseRequestResponse completed = purchaseRequestService.registerReceipt(
                open.id(), patchPanelId, new RegisterItemReceiptRequest(1, LocalDate.now()), ACTOR);

        assertThat(completed.status()).isEqualTo(PurchaseRequestStatus.COMPLETED);
        assertThat(completed.items()).allSatisfy(item -> assertThat(item.status())
                .isEqualTo(PurchaseRequestItemStatus.RECEIVED));
    }

    @Test
    void rejectsReceiptAboveRemainingQuantity() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Pen drive", 2, null))), ACTOR);
        long itemId = open.items().getFirst().id();

        assertThatThrownBy(() -> purchaseRequestService.registerReceipt(
                open.id(), itemId, new RegisterItemReceiptRequest(3, LocalDate.now()), ACTOR))
                .isInstanceOf(ReceiptQuantityExceededException.class);
    }

    @Test
    void rejectsReceiptForItemOfAnotherRequisition() {
        PurchaseRequestResponse first = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);
        PurchaseRequestResponse second = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Mouse", 1, null))), ACTOR);

        assertThatThrownBy(() -> purchaseRequestService.registerReceipt(
                first.id(), second.items().getFirst().id(), new RegisterItemReceiptRequest(1, LocalDate.now()), ACTOR))
                .isInstanceOf(PurchaseRequestItemNotFoundException.class);
    }

    @Test
    void draftCanBeCancelled() {
        PurchaseRequestResponse draft = purchaseRequestService.create(createRequest(null, List.of(
                item("EPI holder", 1, null))), ACTOR);

        purchaseRequestService.cancel(draft.id(), ACTOR);

        assertThat(purchaseRequestService.getById(draft.id()).status()).isEqualTo(PurchaseRequestStatus.CANCELLED);
    }

    @Test
    void trashHidesRequisitionUntilRestored() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);

        purchaseRequestService.moveToTrash(open.id(), ACTOR);

        assertThat(purchaseRequestService.findAll()).noneMatch(summary -> summary.id().equals(open.id()));
        assertThatThrownBy(() -> purchaseRequestService.getById(open.id()))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
        assertThatThrownBy(() -> purchaseRequestService.cancel(open.id(), ACTOR))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
        assertThat(purchaseRequestService.findInTrash())
                .anySatisfy(deleted -> {
                    assertThat(deleted.id()).isEqualTo(open.id());
                    assertThat(deleted.deletedBy()).isEqualTo(ACTOR);
                });

        PurchaseRequestResponse restored = purchaseRequestService.restoreFromTrash(open.id(), ACTOR);

        assertThat(restored.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(purchaseRequestService.findAll()).anyMatch(summary -> summary.id().equals(open.id()));
        assertThat(purchaseRequestService.findInTrash()).noneMatch(deleted -> deleted.id().equals(open.id()));
        assertThat(purchaseRequestService.findHistory(open.id()))
                .extracting(entry -> entry.action())
                .contains("DELETED", "RESTORED");
    }

    @Test
    void restoringRequisitionOutsideTrashFails() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);

        assertThatThrownBy(() -> purchaseRequestService.restoreFromTrash(open.id(), ACTOR))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
    }

    private static CreatePurchaseRequestRequest createRequest(String number, List<CreatePurchaseRequestItemRequest> items) {
        return new CreatePurchaseRequestRequest(
                number,
                "TI",
                LocalDate.now().minusDays(1),
                number == null ? null : LocalDate.now(),
                "Fictional test data.",
                Priority.HIGH,
                "CC-100",
                null,
                items);
    }

    private static UpdatePurchaseRequestRequest updateRequest(String number) {
        return new UpdatePurchaseRequestRequest(
                number,
                "TI",
                LocalDate.now().minusDays(1),
                number == null ? null : LocalDate.now(),
                "Fictional test data.",
                Priority.MEDIUM,
                null,
                null,
                List.of(item("EPI holder", 1, new BigDecimal("35.00"))));
    }

    private static CreatePurchaseRequestItemRequest item(String description, int quantity, BigDecimal totalValue) {
        return new CreatePurchaseRequestItemRequest(description, null, quantity, null, totalValue, null);
    }

    private static String uniqueNumber() {
        return "TEST-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
