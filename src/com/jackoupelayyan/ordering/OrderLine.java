package com.jackoupelayyan.ordering;

/** One line of an order: which product, how many, at what unit price. */
public record OrderLine(int productId, String productName, double unitPrice, int quantity) {
    public double lineTotal() {
        return Money.round2(unitPrice * quantity);
    }
}
