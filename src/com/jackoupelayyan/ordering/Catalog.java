package com.jackoupelayyan.ordering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory product catalog.
 *
 * <p>Products live in a {@link ConcurrentHashMap} so any number of connection
 * threads can read them. Stock reservation is the one operation that must be
 * atomic across multiple products (an order either reserves everything or
 * nothing), so {@link #reserve} is synchronized: it validates every line first
 * and only then decrements stock, all while holding the catalog lock.
 */
public final class Catalog {

    /** Outcome of an atomic stock-reservation attempt. */
    public enum ReserveResult {
        OK,
        UNKNOWN_PRODUCT,
        INVALID_QUANTITY,
        INSUFFICIENT_STOCK
    }

    private final Map<Integer, Product> products = new ConcurrentHashMap<>();

    public Catalog() {
        seed();
    }

    private void seed() {
        add(new Product(1, "Wireless Mouse", 29.99, 50));
        add(new Product(2, "Mechanical Keyboard", 89.99, 30));
        add(new Product(3, "USB-C Hub", 45.50, 25));
        add(new Product(4, "Laptop Stand", 39.99, 40));
        add(new Product(5, "Noise-Cancelling Headphones", 129.99, 15));
        add(new Product(6, "Webcam 1080p", 59.99, 20));
    }

    private void add(Product product) {
        products.put(product.getId(), product);
    }

    public Product get(int productId) {
        return products.get(productId);
    }

    /** Returns all products ordered by id. */
    public List<Product> all() {
        List<Product> list = new ArrayList<>(products.values());
        list.sort((a, b) -> Integer.compare(a.getId(), b.getId()));
        return Collections.unmodifiableList(list);
    }

    /**
     * Atomically reserves stock for every entry of {@code items} (productId -&gt; qty).
     *
     * <p>If any line is invalid the whole reservation fails and no stock is
     * touched. On success the reserved quantities are returned in the same
     * iteration order so the caller can build the order lines.
     */
    public synchronized ReserveOutcome reserve(Map<Integer, Integer> items) {
        // Validate everything first — no state changes yet.
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            int productId = entry.getKey();
            int qty = entry.getValue();
            Product product = products.get(productId);
            if (product == null) {
                return new ReserveOutcome(ReserveResult.UNKNOWN_PRODUCT, productId, 0);
            }
            if (qty <= 0) {
                return new ReserveOutcome(ReserveResult.INVALID_QUANTITY, productId, qty);
            }
            if (product.getStock() < qty) {
                return new ReserveOutcome(ReserveResult.INSUFFICIENT_STOCK, productId, product.getStock());
            }
        }
        // All lines are valid — commit.
        Map<Product, Integer> reserved = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> entry : items.entrySet()) {
            Product product = products.get(entry.getKey());
            product.decrementStock(entry.getValue());
            reserved.put(product, entry.getValue());
        }
        return new ReserveOutcome(ReserveResult.OK, 0, 0, reserved);
    }

    /** Details about a reservation attempt. */
    public static final class ReserveOutcome {
        private final ReserveResult result;
        private final int productId;
        /** For INSUFFICIENT_STOCK: the stock actually available. Otherwise 0/unused. */
        private final int detail;
        private final Map<Product, Integer> reserved;

        private ReserveOutcome(ReserveResult result, int productId, int detail) {
            this(result, productId, detail, Map.of());
        }

        private ReserveOutcome(ReserveResult result, int productId, int detail, Map<Product, Integer> reserved) {
            this.result = result;
            this.productId = productId;
            this.detail = detail;
            this.reserved = reserved;
        }

        public ReserveResult getResult() {
            return result;
        }

        public boolean isOk() {
            return result == ReserveResult.OK;
        }

        public int getProductId() {
            return productId;
        }

        public int getDetail() {
            return detail;
        }

        /** product -&gt; reserved qty, only meaningful when {@link #isOk()}. */
        public Map<Product, Integer> getReserved() {
            return reserved;
        }
    }

    /** For tests: replaces the whole catalog with the given products. */
    synchronized void resetForTest(Collection<Product> seed) {
        products.clear();
        for (Product p : seed) {
            add(p);
        }
    }
}
