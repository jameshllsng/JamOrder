package br.com.jamorder.purchaserequest.service;

import br.com.jamorder.department.DepartmentException;
import br.com.jamorder.department.DepartmentService;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestItemRequest;
import br.com.jamorder.purchaserequest.api.CreatePurchaseRequestRequest;
import br.com.jamorder.purchaserequest.api.DeletedPurchaseRequestResponse;
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
import org.springframework.security.access.AccessDeniedException;
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
 * open requisitions can be edited or cancelled. Admins can move any requisition to the trash
 * and restore it later; trashed requisitions are invisible to every other operation.
 *
 * <p>Department scope: users bound to a department only see and act on that department's
 * requisitions; requisitions of other departments behave as if they did not exist.
 * Admins and users without a department see every department.
 *
 * <p>Manager approval is informational: it is recorded and shown, but never blocks or
 * changes the requisition lifecycle.
 */
@Service
public class PurchaseRequestService {

    private static final Set<PurchaseRequestStatus> EDITABLE_STATUSES =
            Set.of(PurchaseRequestStatus.DRAFT, PurchaseRequestStatus.OPEN);
    private static final Set<PurchaseRequestStatus> RECEIVABLE_STATUSES =
            Set.of(PurchaseRequestStatus.OPEN, PurchaseRequestStatus.PARTIALLY_RECEIVED);

    private final PurchaseRequestRepository purchaseRequestRepository;
    private final PurchaseRequestItemRepository purchaseRequestItemRepository;
    private final PurchaseRequestHistoryRepository purchaseRequestHistoryRepository;
    private final JdbcLocalUserRepository userRepository;
    private final DepartmentService departmentService;

    public PurchaseRequestService(
            PurchaseRequestRepository purchaseRequestRepository,
            PurchaseRequestItemRepository purchaseRequestItemRepository,
            PurchaseRequestHistoryRepository purchaseRequestHistoryRepository,
            JdbcLocalUserRepository userRepository,
            DepartmentService departmentService) {
        this.purchaseRequestRepository = purchaseRequestRepository;
        this.purchaseRequestItemRepository = purchaseRequestItemRepository;
        this.purchaseRequestHistoryRepository = purchaseRequestHistoryRepository;
        this.userRepository = userRepository;
        this.departmentService = departmentService;
    }

    @Transactional
    public PurchaseRequestResponse create(CreatePurchaseRequestRequest request, String actorUsername) {
        LocalUser actor = findActor(actorUsername);
        TotvsRegistration registration = TotvsRegistration.of(request.requestNumber(), request.totvsRequestedOn());
        long departmentId = chooseDepartment(request.departmentId(), null, actor);

        PurchaseRequest saved = insertHandlingDuplicate(new PurchaseRequest(
                null,
                registration.requestNumber(),
                departmentId,
                null,
                blankToNull(request.usageLocation()),
                actor.displayName(),
                request.requestedOn(),
                registration.requestedOn(),
                request.purchaseReason(),
                request.priority(),
                blankToNull(request.costCenter()),
                registration.status(),
                request.notes(),
                null,
                null,
                null,
                null));

        insertItems(saved.id(), request.items());
        purchaseRequestHistoryRepository.insert(saved.id(), "CREATED", actorUsername,
                registration.isRegistered() ? "Requisição cadastrada." : "Rascunho criado (falta lançar no TOTVS).");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestSummaryResponse> findAll(String actorUsername) {
        LocalUser actor = findActor(actorUsername);
        Long departmentId = actor.seesAllDepartments() ? null : actor.departmentId();
        return purchaseRequestRepository.findAll(departmentId).stream().map(this::toSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseRequestResponse getById(long id, String actorUsername) {
        return toResponse(findVisible(id, findActor(actorUsername)));
    }

    @Transactional
    public PurchaseRequestResponse update(long id, UpdatePurchaseRequestRequest request, String actorUsername) {
        LocalUser actor = findActor(actorUsername);
        PurchaseRequest current = findVisibleForUpdate(id, actor);
        requireStatus(current, EDITABLE_STATUSES);

        TotvsRegistration registration = TotvsRegistration.of(request.requestNumber(), request.totvsRequestedOn());
        if (current.status() == PurchaseRequestStatus.OPEN && !registration.isRegistered()) {
            throw new InvalidTotvsRegistrationException(
                    "Uma requisição já lançada no TOTVS não pode voltar a ser rascunho.");
        }
        long departmentId = chooseDepartment(request.departmentId(), current.departmentId(), actor);

        updateHandlingDuplicate(new PurchaseRequest(
                current.id(),
                registration.requestNumber(),
                departmentId,
                null,
                blankToNull(request.usageLocation()),
                current.requesterName(),
                request.requestedOn(),
                registration.requestedOn(),
                request.purchaseReason(),
                request.priority(),
                blankToNull(request.costCenter()),
                registration.status(),
                request.notes(),
                current.approvedAt(),
                current.approvedBy(),
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
        return toResponse(findExisting(id));
    }

    @Transactional
    public PurchaseRequestResponse registerReceipt(
            long requestId, long itemId, RegisterItemReceiptRequest request, String actorUsername) {
        // Locking the requisition serializes concurrent receipts so the final status is consistent.
        PurchaseRequest purchaseRequest = findVisibleForUpdate(requestId, findActor(actorUsername));
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
        return toResponse(findExisting(requestId));
    }

    /** Records the manager's approval. It is only informational and does not change the status. */
    @Transactional
    public PurchaseRequestResponse approve(long id, String actorUsername) {
        LocalUser actor = findActor(actorUsername);
        PurchaseRequest current = findVisibleForUpdate(id, actor);
        if (current.status() == PurchaseRequestStatus.CANCELLED) {
            throw new PurchaseRequestNotOpenException();
        }
        if (current.approvedAt() == null) {
            purchaseRequestRepository.approve(id, actor.displayName());
            purchaseRequestHistoryRepository.insert(id, "APPROVED", actorUsername, "Requisição aprovada pelo gestor.");
        }
        return toResponse(findExisting(id));
    }

    @Transactional
    public PurchaseRequestResponse revokeApproval(long id, String actorUsername) {
        PurchaseRequest current = findVisibleForUpdate(id, findActor(actorUsername));
        if (current.approvedAt() != null) {
            purchaseRequestRepository.revokeApproval(id);
            purchaseRequestHistoryRepository.insert(id, "APPROVAL_REVOKED", actorUsername, "Aprovação do gestor removida.");
        }
        return toResponse(findExisting(id));
    }

    @Transactional(readOnly = true)
    public List<PurchaseRequestHistoryResponse> findHistory(long id, String actorUsername) {
        findVisible(id, findActor(actorUsername));
        return purchaseRequestHistoryRepository.findByPurchaseRequestId(id).stream()
                .map(entry -> new PurchaseRequestHistoryResponse(
                        entry.action(), entry.performedBy(), entry.details(), entry.createdAt()))
                .toList();
    }

    @Transactional
    public void cancel(long id, String actorUsername) {
        PurchaseRequest current = findVisibleForUpdate(id, findActor(actorUsername));
        requireStatus(current, EDITABLE_STATUSES);
        purchaseRequestRepository.updateStatus(id, PurchaseRequestStatus.CANCELLED);
        purchaseRequestHistoryRepository.insert(id, "CANCELLED", actorUsername, "Compra cancelada.");
    }

    /** Moves the requisition to the trash: it disappears from regular use but stays restorable by admins. */
    @Transactional
    public void moveToTrash(long id, String actorUsername) {
        findVisibleForUpdate(id, findActor(actorUsername));
        purchaseRequestRepository.moveToTrash(id, actorUsername);
        purchaseRequestHistoryRepository.insert(id, "DELETED", actorUsername, "Requisição movida para a lixeira.");
    }

    @Transactional(readOnly = true)
    public List<DeletedPurchaseRequestResponse> findInTrash() {
        return purchaseRequestRepository.findInTrash().stream()
                .map(deleted -> new DeletedPurchaseRequestResponse(
                        deleted.id(),
                        deleted.requestNumber(),
                        deleted.departmentName(),
                        deleted.requesterName(),
                        deleted.purchaseReason(),
                        deleted.status(),
                        deleted.itemCount(),
                        deleted.totalValue(),
                        deleted.deletedAt(),
                        deleted.deletedBy()))
                .toList();
    }

    @Transactional
    public PurchaseRequestResponse restoreFromTrash(long id, String actorUsername) {
        if (!purchaseRequestRepository.restoreFromTrash(id)) {
            throw new PurchaseRequestNotFoundException(id);
        }
        purchaseRequestHistoryRepository.insert(id, "RESTORED", actorUsername, "Requisição restaurada da lixeira.");
        return toResponse(findExisting(id));
    }

    private LocalUser findActor(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Usuário desconhecido."));
    }

    private PurchaseRequest findExisting(long id) {
        return purchaseRequestRepository.findById(id).orElseThrow(() -> new PurchaseRequestNotFoundException(id));
    }

    private PurchaseRequest findVisible(long id, LocalUser actor) {
        return requireVisible(findExisting(id), actor);
    }

    private PurchaseRequest findVisibleForUpdate(long id, LocalUser actor) {
        PurchaseRequest purchaseRequest = purchaseRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PurchaseRequestNotFoundException(id));
        return requireVisible(purchaseRequest, actor);
    }

    /** Requisitions of other departments are reported as not found so their existence is not disclosed. */
    private static PurchaseRequest requireVisible(PurchaseRequest purchaseRequest, LocalUser actor) {
        if (!actor.seesAllDepartments() && !purchaseRequest.departmentId().equals(actor.departmentId())) {
            throw new PurchaseRequestNotFoundException(purchaseRequest.id());
        }
        return purchaseRequest;
    }

    /**
     * Users bound to a department always register requisitions in it. Everyone else must pick one;
     * keeping the current department of an existing requisition is allowed even if it was deactivated.
     */
    private long chooseDepartment(Long requested, Long current, LocalUser actor) {
        Long target = requested != null ? requested : current;
        if (!actor.seesAllDepartments()) {
            if (target != null && !target.equals(actor.departmentId())) {
                throw new DepartmentException("Você só pode registrar requisições do seu setor.");
            }
            target = actor.departmentId();
        }
        if (target == null) {
            throw new DepartmentException("Selecione o setor.");
        }
        if (!target.equals(current)) {
            departmentService.requireActive(target);
        }
        return target;
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

    private PurchaseRequestResponse toResponse(PurchaseRequest purchaseRequest) {
        List<PurchaseRequestItem> items = purchaseRequestItemRepository.findByPurchaseRequestId(purchaseRequest.id());
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
        // Null when no item has a value yet, so the UI can tell "not informed" apart from zero.
        BigDecimal totalValue = items.stream()
                .map(PurchaseRequestItem::totalValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal::add)
                .orElse(null);

        return new PurchaseRequestResponse(
                purchaseRequest.id(),
                purchaseRequest.requestNumber(),
                purchaseRequest.departmentId(),
                purchaseRequest.departmentName(),
                purchaseRequest.usageLocation(),
                purchaseRequest.requesterName(),
                purchaseRequest.requestedOn(),
                purchaseRequest.totvsRequestedOn(),
                purchaseRequest.purchaseReason(),
                purchaseRequest.priority(),
                purchaseRequest.costCenter(),
                purchaseRequest.status(),
                purchaseRequest.notes(),
                purchaseRequest.approvedAt(),
                purchaseRequest.approvedBy(),
                totalValue,
                itemResponses,
                purchaseRequest.createdAt(),
                purchaseRequest.updatedAt());
    }

    private PurchaseRequestSummaryResponse toSummaryResponse(PurchaseRequestSummary summary) {
        return new PurchaseRequestSummaryResponse(
                summary.id(),
                summary.requestNumber(),
                summary.departmentId(),
                summary.departmentName(),
                summary.usageLocation(),
                summary.requesterName(),
                summary.purchaseReason(),
                summary.priority(),
                summary.status(),
                summary.requestedOn(),
                summary.totvsRequestedOn(),
                summary.itemCount(),
                summary.pendingItemCount(),
                summary.totalValue(),
                summary.approvedAt(),
                summary.approvedBy(),
                summary.itemDescriptions());
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
