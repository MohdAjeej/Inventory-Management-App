package com.b2binventory.entity;

public enum MovementType {
    OPENING,
    ADD,
    REDUCE,
    ADJUST,
    TRANSFER_OUT,
    TRANSFER_IN,
    COUNT_ADJUST,
    DAMAGE,
    RESERVE,
    RELEASE,
    SALE,
    SALE_CANCEL,
    RETURN_IN,
    PURCHASE,
    PURCHASE_CANCEL,
    /** Stock sent back to the supplier against a trade invoice (buyer side). */
    PURCHASE_RETURN
}
