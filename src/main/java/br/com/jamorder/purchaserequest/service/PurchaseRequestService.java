package br.com.jamorder.purchaserequest.service;

import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestItemRequest;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.api.PurchaseRequestHistoryResponse;
import br.com.jamorder.purchaserequest.api.PurchaseRequestItemResponse;
import br.com.jamorder.purchaserequest.api.PurchaseRequestResponse;
import br.com.jamorder.purchaserequest.api.PurchaseRequestSummaryResponse;
import br.com.jamorder.purchaserequest.api.RegisterItemReceiptRequest;
import br.com.jamorder.purchaserequest.api.UpdatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.domain.PurchaseRequest;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItem;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestItemStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestSummary;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestHistoryRepository;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestItemRepository;
import br.com.jamorder.purchaserequest.repository.PurchaseRequestRepository;
import br.com.jamorder.user.JdbcLocalUserRepository;
import br.com.jamorder.user.LocalUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks purchase requisitions registered in TOTVS.
 *
 * <p>Lifecycle: a requisition without a TOTVS number is a {@code DRAFT} (an item noted
 * to be registered later). Informing the TOTVS number and date makes it {@code OPEN};
 * receipts then move it to {@code PARTIALLY_RECEIVED} and {@code COMPLETED}. Drafts and
 * open requisitions can be edited or cancelled.
 */
@Service
public class PurchaseRequestService {

    private static final Set<PurchaseRequestStatus> EDITABLE_STATUSES =
            Set.of(PurchaseRequestStatus.DRAFT, PurchaseRequestStatus.OPEN);
    private static final Set<PurchaseRequestStatus> RECEIVABLE_STATUSES =
            Set.of(PurchaseRequestStatus.OPEN, PurchaseRequestStatus.PARTIALLY_RECEIVED);
    private static final Set<PurchaseRequestStatus> DELETABLE_STATUSES =
            Set.of(PurchaseRequestStatus.CANCELLED, PurchaseRequestStatus.COMPLETED);

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final PurchaseRequestHistoryRepository purchaseRequestHistoryRepository;
    private final JdbcLocalUserRepository userRepository;

    public PurchaseRequestService(
            PurchaseRequestRepository purchaseRequestRepository,
            PurchaseRequestItemRepository purchaseRequestItemRepository,
            PurchaseRequestHistoryRepository purchaseRequestHistoryRepository,
            JdbcLocalUserRepository userRepository) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.purchaseRequestItemRepository = purchaseRequestItemRepository;
        this.purchaseRequestHistoryRepository = purchaseRequestHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public PurchaseRequestResponse create(CreatePurchaseRequestRequest request, String actorUsername) {
        TotvsRegistration registration = TotvsRegistration.of(request.requestNumber(), request.totvsRequestedOn());
        String requesterName = userRepository.findByUsername(actorUsername)
                .map(LocalUser::displayName)
                .orElse(actorUsername);

        PurchaseRequest saved = insertHandlingDuplicate(new PurchaseRequest(
                null,
                registration.requestNumber(),
                request.department(),
                requesterName,
                request.requestedOn(),
                registration.requestedOn(),
                request.purchaseReason(),
                request.priority(),
                blankToNull(request.costCenter()),
                registration.status(),
                request.notes(),
                null,
                null));

        insertItems(saved.id(), request.items());
        purchaseRequestHistoryRepository.insert(saved.id(), "CREATED", actorUsername,
                registration.isRegistered() ? "Requisição cadastrada." : "Rascunho criado (falta lançar no TOTVS).");
        return getById(saved.id());
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestSummaryResponse> findAll() {
        return purchaseRequestRepository.findAll().stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getById(long id) {
        PurchaseRequest purchaseRequest = findExisting(id);
        return toResponse(purchaseRequest, purchaseRequestItemRepository.findByPurchaseRequestId(id));
    }

    @Transactional
    public PurchaseRequestResponse update(long id, UpdatePurchaseRequestRequest request, String actorUsername) {
        PurchaseRequest current = findExistingForUpdate(id);
        requireStatus(current, EDITABLE_STATUSES);

        TotvsRegistration registration = TotvsRegistration.of(request.requestNumber(), request.totvsRequestedOn());
        if (current.status() == PurchaseRequestStatus.OPEN && !registration.isRegistered()) {
            throw new InvalidTotvsRegistrationException(
                    "Uma requisição já lançada no TOTVS não pode voltar a ser rascunho.");
        }

        updateHandlingDuplicate(new PurchaseRequest(
                current.id(),
                registration.requestNumber(),
                request.department(),
                current.requesterName(),
                request.requestedOn(),
                registration.requestedOn(),
                request.purchaseReason(),
                request.priority(),
                blankToNull(request.costCenter()),
                registration.status(),
                request.notes(),
                current.createdAt(),
                current.updatedAt()));

        // Editable requisitions have no receipts yet, so items can be replaced as a whole.
        purchaseRequestItemRepository.deleteByPurchaseRequestId(id);
        insertItems(id, request.items());

        boolean registeredNow = current.status() == PurchaseRequestStatus.DRAFT && registration.isRegistered();
        if (registeredNow) {
            purchaseRequestHistoryRepository.insert(id, "REGISTERED_IN_TOTVS", actorUsername,
                    "Requisição lançada no TOTVS com o número " + registration.requestNumber() + ".");
        } else {
            purchaseRequestHistoryRepository.insert(id, "UPDATED", actorUsername, "Dados da compra atualizados.");
        }
        return getById(id);
    }

    @Transactional
    public PurchaseRequestResponse registerReceipt(
            long requestId, long itemId, RegisterItemReceiptRequest request, String actorUsername) {
        // Locking the requisition serializes concurrent receipts so the final status is consistent.
        PurchaseRequest purchaseRequest = findExistingForUpdate(requestId);
        requireStatus(purchaseRequest, RECEIVABLE_STATUSES);

        PurchaseRequestItem item = purchaseRequestItemRepository.findByIdForUpdate(itemId)
                .filter(found -> found.purchaseRequestId().equals(requestId))
                .orElseThrow(() -> new PurchaseRequestItemNotFoundException(requestId, itemId));

        int totalReceived = item.receivedQuantity() + request.receivedQuantity();
        if (totalReceived > item.quantity()) {
            throw new ReceiptQuantityExceededException();
        }

        PurchaseRequestItemStatus itemStatus = totalReceived == item.quantity()
                ? PurchaseRequestItemStatus.RECEIVED
                : PurchaseRequestItemStatus.PARTIALLY_RECEIVED;
        purchaseRequestItemRepository.registerReceipt(
                itemId, totalReceived, request.receivedQuantity(), request.receivedOn(), actorUsername, itemStatus);

        boolean allReceived = purchaseRequestItemRepository.findByPurchaseRequestId(requestId).stream()
                .allMatch(existing -> existing.status() == PurchaseRequestItemStatus.RECEIVED);
        purchaseRequestRepository.updateStatus(requestId,
                allReceived ? PurchaseRequestStatus.COMPLETED : PurchaseRequestStatus.PARTIALLY_RECEIVED);

        purchaseRequestHistoryRepository.insert(requestId, "RECEIPT_REGISTERED", actorUsername,
                "Recebimento de " + request.receivedQuantity() + " unidade(s) do item " + item.description() + ".");
        return getById(requestId);
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestHistoryResponse> findHistory(long id) {
        findExisting(id);
        return purchaseRequestHistoryRepository.findByPurchaseRequestId(id).stream()
                .map(entry -> new PurchaseRequestHistoryResponse(
                        entry.action(), entry.performedBy(), entry.details(), entry.createdAt()))
                .toList();
    }

    @Transactional
    public void cancel(long id, String actorUsername) {
        PurchaseRequest current = findExistingForUpdate(id);
        requireStatus(current, EDITABLE_STATUSES);
        purchaseRequestRepository.updateStatus(id, PurchaseRequestStatus.CANCELLED);
        purchaseRequestHistoryRepository.insert(id, "CANCELLED", actorUsername, "Compra cancelada.");
    }

    @Transactional
    public void deletePermanently(long id) {
        PurchaseRequest current = findExistingForUpdate(id);
        requireStatus(current, DELETABLE_STATUSES);
        purchaseRequestItemRepository.deleteByPurchaseRequestId(id);
        purchaseRequestRepository.deleteById(id);
    }

    private PurchaseRequest findExisting(long id) {
        return purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id));
    }

    private PurchaseRequest findExistingForUpdate(long id) {
        return purchaseRequestRepository.findByIdForUpdate(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id));
    }

    private static void requireStatus(PurchaseRequest purchaseRequest, Set<PurchaseRequestStatus> allowed) {
        if (!allowed.contains(purchaseRequest.status())) {
            throw new PurchaseRequestNotOpenException();
        }
    }

    private PurchaseRequest insertHandlingDuplicate(PurchaseRequest purchaseRequest) {
        try {
            return purchaseRequestRepository.insert(purchaseRequest);
        } catch (DuplicateKeyException exception) {
            throw new DuplicatePurchaseRequestNumberException();
        }
    }

    private void updateHandlingDuplicate(PurchaseRequest purchaseRequest) {
        try {
            purchaseRequestRepository.update(purchaseRequest)
                    .orElseThrow(() -> new PurchaseRequestNotFoundException(purchaseRequest.id()));
        } catch (DuplicateKeyException exception) {
            throw new DuplicatePurchaseRequestNumberException();
        }
    }

    private void insertItems(long purchaseRequestId, List<CreatePurchaseRequestItemRequest> items) {
        items.forEach(item -> purchaseRequestItemRepository.insert(new PurchaseRequestItem(
                null,
                purchaseRequestId,
                item.description(),
                blankToNull(item.brandModel()),
                item.quantity(),
                blankToNull(item.supplierName()),
                item.totalValue(),
                0,
                null,
                null,
                PurchaseRequestItemStatus.PENDING,
                blankToNull(item.notes()),
                null,
                null)));
    }

    private PurchaseRequestResponse toResponse(PurchaseRequest purchaseRequest, List<PurchaseRequestItem> items) {
        List<PurchaseRequestItemResponse> itemResponses = items.stream()
                .map(item -> new PurchaseRequestItemResponse(
                        item.id(),
                        item.description(),
                        item.brandModel(),
                        item.quantity(),
                        item.supplierName(),
                        item.totalValue(),
                        item.receivedQuantity(),
                        item.receivedOn(),
                        item.checkedBy(),
                        item.status(),
                        item.notes()))
                .toList();
        BigDecimal totalValue = items.stream()
                .map(PurchaseRequestItem::totalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new PurchaseRequestResponse(
                purchaseRequest.id(),
                purchaseRequest.requestNumber(),
                purchaseRequest.department(),
                purchaseRequest.requesterName(),
                purchaseRequest.requestedOn(),
                purchaseRequest.totvsRequestedOn(),
                purchaseRequest.purchaseReason(),
                purchaseRequest.priority(),
                purchaseRequest.costCenter(),
                purchaseRequest.status(),
                purchaseRequest.notes(),
                totalValue,
                itemResponses,
                purchaseRequest.createdAt(),
                purchaseRequest.updatedAt());
    }

    private PurchaseRequestSummaryResponse toSummaryResponse(PurchaseRequestSummary summary) {
        return new PurchaseRequestSummaryResponse(
                summary.id(),
                summary.requestNumber(),
                summary.department(),
                summary.requesterName(),
                summary.purchaseReason(),
                summary.priority(),
                summary.status(),
                summary.requestedOn(),
                summary.totvsRequestedOn(),
                summary.itemCount(),
                summary.pendingItemCount(),
                summary.totalValue());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** TOTVS number and date are informed together; their presence defines draft vs open. */
    private record TotvsRegistration(String requestNumber, LocalDate requestedOn) {

        static TotvsRegistration of(String requestNumber, LocalDate requestedOn) {
            String number = blankToNull(requestNumber);
            if (number == null && requestedOn != null) {
                throw new InvalidTotvsRegistrationException(
                        "Informe o número da requisição do TOTVS junto com a data.");
            }
            if (number != null && requestedOn == null) {
                throw new InvalidTotvsRegistrationException(
                        "Informe a data da requisição no TOTVS junto com o número.");
            }
            return new TotvsRegistration(number, requestedOn);
        }

        boolean isRegistered() {
            return requestNumber != null;
        }

        PurchaseRequestStatus status() {
            return isRegistered() ? PurchaseRequestStatus.OPEN : PurchaseRequestStatus.DRAFT;
        }
    }
}
