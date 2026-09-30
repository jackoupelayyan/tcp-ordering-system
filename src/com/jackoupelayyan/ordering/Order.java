package com.jackoupelayyan.ordering;

import java.util.List;

/**
 * An immutable, confirmed order. Orders are created by {@link Protocol} when a
 * PLACE_ORDER request succeeds and are stored in {@link OrderStore}.
 */
public final class Order {
    private final String id;
    private final List<OrderLine> lines;
    private final double total;
    private final String status;
    private final long createdAtMillis;

    public Order(String id, List<OrderLine> lines) {
        this.id = id;
        this.lines = List.copyOf(lines);
        double sum = 0.0;
        for (OrderLine line : lines) {
            sum += line.lineTotal();
        }
        this.total = Money.round2(sum);
        this.status = "CONFIRMED";
        this.createdAtMillis = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public List<OrderLine> getLines() {
        return lines;
    }

    public double getTotal() {
        return total;
    }

    public String getStatus() {
        return status;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    @Override
    public String toString() {
        return "Order{id='" + id + "', lines=" + lines.size() + ", total=" + total + ", status='" + status + "'}";
    }
}
