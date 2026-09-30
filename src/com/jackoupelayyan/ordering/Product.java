package com.jackoupelayyan.ordering;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A product in the catalog. The id, name and price are immutable; the stock
 * count is an {@link AtomicInteger} so concurrent order threads can read it
 * safely. Multi-item stock reservation is atomic — see {@link Catalog#reserve}.
 */
public final class Product {
    private final int id;
    private final String name;
    private final double price;
    private final AtomicInteger stock;

    public Product(int id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = new AtomicInteger(stock);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock.get();
    }

    void decrementStock(int qty) {
        stock.addAndGet(-qty);
    }

    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', price=" + price + ", stock=" + stock.get() + '}';
    }
}
