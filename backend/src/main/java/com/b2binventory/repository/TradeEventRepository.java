package com.b2binventory.repository;

import com.b2binventory.entity.TradeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeEventRepository extends JpaRepository<TradeEvent, Long> {

    List<TradeEvent> findByInvoiceIdOrderByCreatedAtAscIdAsc(Long invoiceId);
}
