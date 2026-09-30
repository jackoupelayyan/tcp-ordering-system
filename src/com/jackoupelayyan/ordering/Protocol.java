package com.jackoupelayyan.ordering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implements the JSON-lines ordering protocol. Given a parsed client request
 * (a {@code Map} produced by {@link Json#parse}), {@link #handle} returns a
 * response map shaped as {@code {"status":"ok","data":...}} or
 * {@code {"status":"error","message":"..."}}.
 *
 * <p>Keeping the protocol here — instead of inside the socket handler — means
 * it can be exercised directly by tests without opening any sockets.
 */
public final class Protocol {

    private Protocol() {
        // utility class
    }

    /**
     * Handles one parsed request.
     *
     * @param request the parsed JSON request, expected to be a JSON object
     * @param catalog shared product catalog
     * @param orders  shared order store
     * @return response map ready for {@link Json#stringify}
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> handle(Object request, Catalog catalog, OrderStore orders) {
        if (!(request instanceof Map)) {
            return error("request must be a JSON object");
        }
        Map<String, Object> req = (Map<String, Object>) request;
        Object type = req.get("type");
        if (!(type instanceof String)) {
            return error("missing or invalid 'type' field");
        }
        return switch ((String) type) {
            case "LIST_PRODUCTS" -> listProducts(catalog);
            case "PLACE_ORDER" -> placeOrder(req, catalog, orders);
            case "ORDER_STATUS" -> orderStatus(req, orders);
            default -> error("unknown request type: " + type);
        };
    }

    // ------------------------------------------------------------------ handlers

    private static Map<String, Object> listProducts(Catalog catalog) {
        List<Object> products = new ArrayList<>();
        for (Product p : catalog.all()) {
            products.add(productJson(p));
        }
        return ok(Json.obj("products", products));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> placeOrder(Map<String, Object> req, Catalog catalog, OrderStore orders) {
        Object itemsObj = req.get("items");
        if (!(itemsObj instanceof List)) {
            return error("'items' must be an array of {productId, qty} objects");
        }
        List<Object> items = (List<Object>) itemsObj;
        if (items.isEmpty()) {
            return error("'items' must not be empty");
        }

        // Parse each line into productId -> qty, preserving request order.
        Map<Integer, Integer> wanted = new LinkedHashMap<>();
        for (Object itemObj : items) {
            if (!(itemObj instanceof Map)) {
                return error("each item must be an object like {\"productId\":1,\"qty\":2}");
            }
            Map<String, Object> item = (Map<String, Object>) itemObj;
            Object idObj = item.get("productId");
            Object qtyObj = item.get("qty");
            if (!(idObj instanceof Number) || !(qtyObj instanceof Number)) {
                return error("each item needs a numeric 'productId' and numeric 'qty'");
            }
            int productId = ((Number) idObj).intValue();
            int qty = ((Number) qtyObj).intValue();
            wanted.merge(productId, qty, Integer::sum);
        }

        Catalog.ReserveOutcome outcome = catalog.reserve(wanted);
        if (!outcome.isOk()) {
            return switch (outcome.getResult()) {
                case UNKNOWN_PRODUCT ->
                        error("unknown product id: " + outcome.getProductId());
                case INVALID_QUANTITY ->
                        error("quantity must be positive for product id " + outcome.getProductId());
                case INSUFFICIENT_STOCK ->
                        error("insufficient stock for product id " + outcome.getProductId()
                                + " (only " + outcome.getDetail() + " available)");
                case OK -> error("unexpected reservation state"); // unreachable
            };
        }

        List<OrderLine> lines = new ArrayList<>();
        for (Map.Entry<Product, Integer> e : outcome.getReserved().entrySet()) {
            Product p = e.getKey();
            lines.add(new OrderLine(p.getId(), p.getName(), p.getPrice(), e.getValue()));
        }
        Order order = new Order(newOrderId(), lines);
        orders.put(order);

        List<Object> lineJson = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            lineJson.add(Json.obj(
                    "productId", line.productId(),
                    "name", line.productName(),
                    "unitPrice", line.unitPrice(),
                    "qty", line.quantity(),
                    "lineTotal", line.lineTotal()));
        }
        return ok(Json.obj(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "items", lineJson,
                "total", order.getTotal()));
    }

    private static Map<String, Object> orderStatus(Map<String, Object> req, OrderStore orders) {
        Object idObj = req.get("orderId");
        if (!(idObj instanceof String) || ((String) idObj).isBlank()) {
            return error("'orderId' must be a non-empty string");
        }
        Order order = orders.get((String) idObj);
        if (order == null) {
            return error("unknown order id: " + idObj);
        }
        return ok(orderJson(order));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> orderJson(Order order) {
        List<Object> lineJson = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            lineJson.add(Json.obj(
                    "productId", line.productId(),
                    "name", line.productName(),
                    "unitPrice", line.unitPrice(),
                    "qty", line.quantity(),
                    "lineTotal", line.lineTotal()));
        }
        return Json.obj(
                "orderId", order.getId(),
                "status", order.getStatus(),
                "items", lineJson,
                "total", order.getTotal(),
                "createdAtMillis", order.getCreatedAtMillis());
    }

    private static Map<String, Object> productJson(Product p) {
        return Json.obj(
                "id", p.getId(),
                "name", p.getName(),
                "price", p.getPrice(),
                "stock", p.getStock());
    }

    private static String newOrderId() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static Map<String, Object> ok(Object data) {
        return Json.obj("status", "ok", "data", data);
    }

    private static Map<String, Object> error(String message) {
        return Json.obj("status", "error", "message", message);
    }
}
