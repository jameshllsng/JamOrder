package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.DeletedPurchaseRequestSummary;
import br.com.jamorder.purchaserequest.domain.PurchaseRequest;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestSummary;

import java.util.List;
import java.util.Optional;

/**
 * Lookups ignore requisitions moved to the trash, except the trash-specific methods.
 */
public interface PurchaseRequestRepository {

    PurchaseRequest insert(PurchaseRequest purchaseRequest);

    Optional<PurchaseRequest> findById(long id);

    Optional<PurchaseRequest> findByIdForUpdate(long id);

    /** @param departmentId only requisitions of this department, or every department when {@code null} */
    List<PurchaseRequestSummary> findAll(Long departmentId);

    Optional<PurchaseRequest> update(PurchaseRequest purchaseRequest);

    Optional<PurchaseRequest> updateStatus(long id, PurchaseRequestStatus status);

    void approve(long id, String approvedBy);

    void revokeApproval(long id);

    boolean moveToTrash(long id, String deletedBy);

    List<DeletedPurchaseRequestSummary> findInTrash();

    boolean restoreFromTrash(long id);
}
