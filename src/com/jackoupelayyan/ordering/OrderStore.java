package com.jackoupelayyan.ordering;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe in-memory store of confirmed orders, keyed by order id. */
public final class OrderStore {
    private final ConcurrentMap<String, Order> orders = new ConcurrentHashMap<>();

    public void put(Order order) {
        orders.put(order.getId(), order);
    }

    public Order get(String orderId) {
        return orders.get(orderId);
    }

    public int size() {
        return orders.size();
    }
}
