package br.com.jamorder.purchaserequest.repository;

import br.com.jamorder.purchaserequest.domain.PurchaseRequest;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestStatus;
import br.com.jamorder.purchaserequest.domain.PurchaseRequestSummary;

import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository {

    PurchaseRequest insert(PurchaseRequest purchaseRequest);

    Optional<PurchaseRequest> findById(long id);

    Optional<PurchaseRequest> findByIdForUpdate(long id);

    List<PurchaseRequestSummary> findAll();

    Optional<PurchaseRequest> update(PurchaseRequest purchaseRequest);

    Optional<PurchaseRequest> updateStatus(long id, PurchaseRequestStatus status);

    void deleteById(long id);
}
