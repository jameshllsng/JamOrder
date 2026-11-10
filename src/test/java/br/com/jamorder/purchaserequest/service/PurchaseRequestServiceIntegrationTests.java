package br.com.jamorder.purchaserequest.service;

import br.com.jamorder.department.DepartmentException;
import br.com.jamorder.department.DepartmentService;
import br.com.jamorder.department.SaveDepartmentRequest;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestItemRequest;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.api.PurchaseRequestResponse;
import br.com.jamorder.purchaserequest.api.RegisterItemReceiptRequest;
import br.com.jamorder.purchaserequest.api.UpdatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.domain.Priority;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import br.com.jamorder.user.JdbcLocalUserRepository;
import br.com.jamorder.user.LocalUser;
import br.com.jamorder.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
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

    private static final String ACTOR = "test-admin";
    private static final String SALES_USER = "test-sales-requester";
    private static final String SALES_MANAGER = "test-sales-manager";

    @Autowired
    private PurchaseRequestService purchaseRequestService;

    @Autowired
    private DepartmentService departmentService;

    @Autowired
    private JdbcLocalUserRepository userRepository;

    private long departmentId;
    private long salesDepartmentId;

    @BeforeEach
    void createDepartmentsAndUsers() {
        departmentId = createDepartment("Test Operations");
        salesDepartmentId = createDepartment("Test Sales");
        userRepository.insert(new LocalUser(null, ACTOR, "unused", "Test Admin", UserRole.ADMIN, true, null));
        userRepository.insert(new LocalUser(null, SALES_USER, "unused", "Sales Requester", UserRole.REQUESTER, true, salesDepartmentId));
        userRepository.insert(new LocalUser(null, SALES_MANAGER, "unused", "Sales Manager", UserRole.MANAGER, true, salesDepartmentId));
    }

    @Test
    void createsRegisteredRequisitionWithItemsAndCalculatesTotalValue() {
        PurchaseRequestResponse response = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Network switch", 2, new BigDecimal("1200.00")),
                item("Network cable", 10, new BigDecimal("150.50")))), ACTOR);

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(response.departmentId()).isEqualTo(departmentId);
        assertThat(response.departmentName()).startsWith("Test Operations");
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
        assertThat(response.totalValue()).isNull();
    }

    @Test
    void registeringDraftInTotvsMakesItOpenAndRecordsHistory() {
        PurchaseRequestResponse draft = purchaseRequestService.create(createRequest(null, List.of(
                item("EPI holder", 1, null))), ACTOR);
        String number = uniqueNumber();

        PurchaseRequestResponse registered = purchaseRequestService.update(draft.id(), updateRequest(number), ACTOR);

        assertThat(registered.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(registered.requestNumber()).isEqualTo(number);
        assertThat(purchaseRequestService.findHistory(draft.id(), ACTOR))
                .extracting(entry -> entry.action())
                .contains("REGISTERED_IN_TOTVS");
    }

    @Test
    void rejectsNumberWithoutTotvsDate() {
        CreatePurchaseRequestRequest request = new CreatePurchaseRequestRequest(
                uniqueNumber(), departmentId, null, LocalDate.now(), null, "Stock.", Priority.URGENT, null, null,
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

        assertThat(purchaseRequestService.getById(draft.id(), ACTOR).status()).isEqualTo(PurchaseRequestStatus.CANCELLED);
    }

    @Test
    void trashHidesRequisitionUntilRestored() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);

        purchaseRequestService.moveToTrash(open.id(), ACTOR);

        assertThat(purchaseRequestService.findAll(ACTOR)).noneMatch(summary -> summary.id().equals(open.id()));
        assertThatThrownBy(() -> purchaseRequestService.getById(open.id(), ACTOR))
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
        assertThat(purchaseRequestService.findAll(ACTOR)).anyMatch(summary -> summary.id().equals(open.id()));
        assertThat(purchaseRequestService.findInTrash()).noneMatch(deleted -> deleted.id().equals(open.id()));
        assertThat(purchaseRequestService.findHistory(open.id(), ACTOR))
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

    @Test
    void departmentUserOnlySeesAndActsOnOwnDepartment() {
        PurchaseRequestResponse operations = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);
        PurchaseRequestResponse sales = purchaseRequestService.create(createRequest(uniqueNumber(), salesDepartmentId, List.of(
                item("Headset", 1, null))), ACTOR);

        assertThat(purchaseRequestService.findAll(SALES_USER))
                .extracting(summary -> summary.id())
                .contains(sales.id())
                .doesNotContain(operations.id());
        assertThat(purchaseRequestService.getById(sales.id(), SALES_USER).departmentId()).isEqualTo(salesDepartmentId);
        assertThatThrownBy(() -> purchaseRequestService.getById(operations.id(), SALES_USER))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
        assertThatThrownBy(() -> purchaseRequestService.findHistory(operations.id(), SALES_USER))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
        assertThatThrownBy(() -> purchaseRequestService.approve(operations.id(), SALES_MANAGER))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
        assertThat(purchaseRequestService.findAll(ACTOR))
                .extracting(summary -> summary.id())
                .contains(sales.id(), operations.id());
    }

    @Test
    void departmentUserCreatesRequisitionsOnlyInOwnDepartment() {
        PurchaseRequestResponse created = purchaseRequestService.create(createRequest(null, null, List.of(
                item("Headset", 1, null))), SALES_USER);

        assertThat(created.departmentId()).isEqualTo(salesDepartmentId);
        assertThat(created.requesterName()).isEqualTo("Sales Requester");
        assertThatThrownBy(() -> purchaseRequestService.create(createRequest(null, departmentId, List.of(
                item("Headset", 1, null))), SALES_USER))
                .isInstanceOf(DepartmentException.class);
    }

    @Test
    void departmentRequesterConfirmsReceiptsOnlyInOwnDepartment() {
        PurchaseRequestResponse sales = purchaseRequestService.create(createRequest(uniqueNumber(), salesDepartmentId, List.of(
                item("Headset", 1, null))), ACTOR);
        PurchaseRequestResponse operations = purchaseRequestService.create(createRequest(uniqueNumber(), List.of(
                item("Cable", 1, null))), ACTOR);

        PurchaseRequestResponse received = purchaseRequestService.registerReceipt(sales.id(), sales.items().getFirst().id(),
                new RegisterItemReceiptRequest(1, LocalDate.now()), SALES_USER);

        assertThat(received.status()).isEqualTo(PurchaseRequestStatus.COMPLETED);
        assertThat(received.items().getFirst().checkedBy()).isEqualTo(SALES_USER);
        assertThatThrownBy(() -> purchaseRequestService.registerReceipt(operations.id(), operations.items().getFirst().id(),
                new RegisterItemReceiptRequest(1, LocalDate.now()), SALES_USER))
                .isInstanceOf(PurchaseRequestNotFoundException.class);
    }

    @Test
    void rejectsInactiveDepartmentForNewRequisition() {
        departmentService.update(departmentId, new SaveDepartmentRequest("Test Operations " + UUID.randomUUID(), false));

        assertThatThrownBy(() -> purchaseRequestService.create(createRequest(null, List.of(
                item("Cable", 1, null))), ACTOR))
                .isInstanceOf(DepartmentException.class);
    }

    @Test
    void managerApprovalIsInformationalAndCanBeRevoked() {
        PurchaseRequestResponse open = purchaseRequestService.create(createRequest(uniqueNumber(), salesDepartmentId, List.of(
                item("Headset", 1, null))), ACTOR);

        PurchaseRequestResponse approved = purchaseRequestService.approve(open.id(), SALES_MANAGER);

        assertThat(approved.status()).isEqualTo(PurchaseRequestStatus.OPEN);
        assertThat(approved.approvedBy()).isEqualTo("Sales Manager");
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(purchaseRequestService.findAll(SALES_MANAGER))
                .anySatisfy(summary -> {
                    assertThat(summary.id()).isEqualTo(open.id());
                    assertThat(summary.approvedBy()).isEqualTo("Sales Manager");
                });

        PurchaseRequestResponse revoked = purchaseRequestService.revokeApproval(open.id(), SALES_MANAGER);

        assertThat(revoked.approvedAt()).isNull();
        assertThat(revoked.approvedBy()).isNull();
        assertThat(purchaseRequestService.findHistory(open.id(), SALES_MANAGER))
                .extracting(entry -> entry.action())
                .contains("APPROVED", "APPROVAL_REVOKED");
    }

    @Test
    void cancelledRequisitionCannotBeApproved() {
        PurchaseRequestResponse draft = purchaseRequestService.create(createRequest(null, List.of(
                item("Cable", 1, null))), ACTOR);
        purchaseRequestService.cancel(draft.id(), ACTOR);

        assertThatThrownBy(() -> purchaseRequestService.approve(draft.id(), ACTOR))
                .isInstanceOf(PurchaseRequestNotOpenException.class);
    }

    private long createDepartment(String name) {
        return departmentService.create(new SaveDepartmentRequest(name + " " + UUID.randomUUID(), true)).id();
    }

    private CreatePurchaseRequestRequest createRequest(String number, List<CreatePurchaseRequestItemRequest> items) {
        return createRequest(number, departmentId, items);
    }

    private static CreatePurchaseRequestRequest createRequest(
            String number, Long departmentId, List<CreatePurchaseRequestItemRequest> items) {
        return new CreatePurchaseRequestRequest(
                number,
                departmentId,
                "Room 1",
                LocalDate.now().minusDays(1),
                number == null ? null : LocalDate.now(),
                "Fictional test data.",
                Priority.HIGH,
                "CC-100",
                null,
                items);
    }

    private UpdatePurchaseRequestRequest updateRequest(String number) {
        return new UpdatePurchaseRequestRequest(
                number,
                departmentId,
                null,
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
