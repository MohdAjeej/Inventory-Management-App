package com.b2binventory.service;

import com.b2binventory.entity.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency arithmetic: amounts are rounded half-up to 2 decimals, unit costs to 4. Never uses floating point. */
public final class Money {

    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal amount(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** base x rate% rounded to an amount. */
    public static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return amount(base.multiply(rate).divide(HUNDRED, 6, RoundingMode.HALF_UP));
    }

    public static BigDecimal unitCost(BigDecimal amount, BigDecimal quantity) {
        return amount.divide(quantity, 4, RoundingMode.HALF_UP);
    }

    /** PAID when nothing is owed, UNPAID when nothing has been received, otherwise PARTIALLY_PAID. */
    public static PaymentStatus status(BigDecimal due, BigDecimal paid) {
        if (due.signum() <= 0 || paid.compareTo(due) >= 0) return PaymentStatus.PAID;
        if (paid.signum() <= 0) return PaymentStatus.UNPAID;
        return PaymentStatus.PARTIALLY_PAID;
    }
}
