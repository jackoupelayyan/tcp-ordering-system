package com.jackoupelayyan.ordering;

/** Tiny money helper: rounds to two decimal places (cents). */
public final class Money {
    private Money() {
        // utility class
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
