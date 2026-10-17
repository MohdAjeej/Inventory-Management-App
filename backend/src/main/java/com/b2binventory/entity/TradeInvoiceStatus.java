package com.b2binventory.entity;

/**
 * DRAFT (seller only, no stock held) -> PENDING_APPROVAL (seller stock reserved) -> POSTED (seller stock
 * deducted, buyer stock added, sale and purchase recorded) or REJECTED / CANCELLED (reservation released).
 */
public enum TradeInvoiceStatus {
    DRAFT,
    PENDING_APPROVAL,
    POSTED,
    REJECTED,
    CANCELLED
}
