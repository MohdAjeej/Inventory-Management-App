package com.b2binventory.entity;

/** REQUESTED (buyer stock reserved) -> ACCEPTED (stock moved back) or REJECTED / CANCELLED (released). */
public enum TradeReturnStatus {
    REQUESTED,
    ACCEPTED,
    REJECTED,
    CANCELLED
}
