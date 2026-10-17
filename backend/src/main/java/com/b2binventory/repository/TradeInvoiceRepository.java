package com.b2binventory.repository;

import com.b2binventory.entity.TradeInvoice;
import com.b2binventory.entity.TradeInvoiceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TradeInvoiceRepository extends JpaRepository<TradeInvoice, Long> {

    /** Only the seller sees drafts; the buyer sees an invoice once it has been sent. */
    @Query("select i from TradeInvoice i where i.id = ?1 and (i.sellerBusinessId = ?2 "
            + "or (i.buyerBusinessId = ?2 and i.status <> com.b2binventory.entity.TradeInvoiceStatus.DRAFT))")
    Optional<TradeInvoice> findVisible(Long id, Long businessId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from TradeInvoice i where i.id = ?1")
    Optional<TradeInvoice> lock(Long id);

    Optional<TradeInvoice> findBySellerBusinessIdAndClientRequestId(Long sellerBusinessId, UUID clientRequestId);

    @Query("select i from TradeInvoice i where i.sellerBusinessId = ?1 and i.status in ?2 and (cast(?3 as string) is null "
            + "or lower(i.invoiceNumber) like lower(concat('%', cast(?3 as string), '%')) or i.buyerBusinessId in ?4)")
    Page<TradeInvoice> sales(Long businessId, Collection<TradeInvoiceStatus> statuses, String q,
                             Collection<Long> matchingBusinesses, Pageable pageable);

    @Query("select i from TradeInvoice i where i.buyerBusinessId = ?1 and i.status in ?2 and (cast(?3 as string) is null "
            + "or lower(i.invoiceNumber) like lower(concat('%', cast(?3 as string), '%')) or i.sellerBusinessId in ?4)")
    Page<TradeInvoice> purchases(Long businessId, Collection<TradeInvoiceStatus> statuses, String q,
                                 Collection<Long> matchingBusinesses, Pageable pageable);

    long countByBuyerBusinessIdAndStatus(Long buyerBusinessId, TradeInvoiceStatus status);

    long countBySellerBusinessIdAndStatus(Long sellerBusinessId, TradeInvoiceStatus status);
}
