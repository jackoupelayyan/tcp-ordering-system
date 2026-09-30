package com.jackoupelayyan.ordering;

import java.util.List;
import java.util.Map;

/**
 * Dependency-free self test: exercises JSON parsing and the ordering protocol
 * with plain assertions. No JUnit needed — just compile and run:
 *
 * <pre>
 *   ./build.sh
 *   java -cp out com.jackoupelayyan.ordering.SelfTest
 * </pre>
 *
 * Exits 0 when everything passes, throws {@link AssertionError} otherwise.
 */
public final class SelfTest {
    private static int passed = 0;

    private SelfTest() {
        // entry point class
    }

    public static void main(String[] args) {
        testJsonParsing();
        testJsonStringify();
        testJsonErrors();
        testListProducts();
        testPlaceOrder();
        testPlaceOrderErrors();
        testOrderStatus();
        testProtocolErrors();
        testMoneyRounding();

        System.out.println();
        System.out.println("ALL " + passed + " TESTS PASSED");
    }

    // ------------------------------------------------------------------ JSON

    private static void testJsonParsing() {
        Object v = Json.parse("{\"type\":\"LIST_PRODUCTS\",\"n\":42,\"pi\":3.5,\"ok\":true,\"no\":false,\"nil\":null}");
        check(v instanceof Map, "object parses to Map");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        check("LIST_PRODUCTS".equals(m.get("type")), "string value");
        check(Long.valueOf(42).equals(m.get("n")), "integer parses to Long");
        check(Double.valueOf(3.5).equals(m.get("pi")), "decimal parses to Double");
        check(Boolean.TRUE.equals(m.get("ok")), "true literal");
        check(Boolean.FALSE.equals(m.get("no")), "false literal");
        check(m.containsKey("nil") && m.get("nil") == null, "null literal");

        Object arr = Json.parse("[1, \"two\", {\"three\": 3}, [4]]");
        check(arr instanceof List && ((List<?>) arr).size() == 4, "nested arrays/objects");
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) arr;
        check("two".equals(list.get(1)), "array string element");

        Object esc = Json.parse("{\"s\":\"a\\\"b\\\\c\\nd\\u00e9\"}");
        @SuppressWarnings("unchecked")
        Map<String, Object> em = (Map<String, Object>) esc;
        check("a\"b\\c\ndé".equals(em.get("s")), "string escapes incl. unicode");

        check(Long.valueOf(-7).equals(Json.parse(" -7 ")), "negative integer with whitespace");
        check(Double.valueOf(1e3).equals(Json.parse("1e3")), "exponent number");
    }

    private static void testJsonStringify() {
        String json = Json.stringify(Json.obj(
                "id", 1,
                "name", "Mouse \"Pro\"",
                "price", 29.99,
                "tags", Json.arr("a", "b"),
                "meta", Json.obj("inStock", true, "note", null)));
        Object back = Json.parse(json);
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) back;
        check("Mouse \"Pro\"".equals(m.get("name")), "stringify escapes quotes");
        check(Double.valueOf(29.99).equals(m.get("price")), "stringify keeps decimals");
        check(Long.valueOf(1).equals(m.get("id")), "stringify keeps integers integral");
        check("{\"a\":1}".equals(Json.stringify(Json.obj("a", 1))), "compact object output");
        check("[1,\"x\",null]".equals(Json.stringify(Json.arr(1, "x", null))), "compact array output");
    }

    private static void testJsonErrors() {
        String[] bad = {"", "{", "{\"a\":}", "[1,]", "{\"a\" 1}", "tru", "{'a':1}", "{\"a\":\"\\q\"}"};
        for (String s : bad) {
            boolean threw = false;
            try {
                Json.parse(s);
            } catch (Json.JsonParseException e) {
                threw = true;
            }
            check(threw, "rejects malformed JSON: " + s);
        }
    }

    // ------------------------------------------------------------------ protocol

    private static Catalog freshCatalog() {
        return new Catalog();
    }

    @SuppressWarnings("unchecked")
    private static void testListProducts() {
        Map<String, Object> res = Protocol.handle(
                Json.parse("{\"type\":\"LIST_PRODUCTS\"}"), freshCatalog(), new OrderStore());
        check("ok".equals(res.get("status")), "LIST_PRODUCTS status ok");
        Map<String, Object> data = (Map<String, Object>) res.get("data");
        List<Object> products = (List<Object>) data.get("products");
        check(products.size() == 6, "catalog seeds 6 products");
        Map<String, Object> first = (Map<String, Object>) products.get(0);
        check(((Number) first.get("id")).intValue() == 1, "first product id");
        check("Wireless Mouse".equals(first.get("name")), "first product name");
    }

    @SuppressWarnings("unchecked")
    private static void testPlaceOrder() {
        Catalog catalog = freshCatalog();
        OrderStore orders = new OrderStore();
        Map<String, Object> res = Protocol.handle(
                Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":1,\"qty\":2},{\"productId\":2,\"qty\":1}]}"),
                catalog, orders);
        check("ok".equals(res.get("status")), "PLACE_ORDER status ok");
        Map<String, Object> data = (Map<String, Object>) res.get("data");
        String orderId = (String) data.get("orderId");
        check(orderId != null && orderId.startsWith("ORD-"), "order id generated: " + orderId);
        // 2 * 29.99 + 1 * 89.99 = 149.97
        check(Double.valueOf(149.97).equals(data.get("total")), "order total 149.97, got " + data.get("total"));
        check(catalog.get(1).getStock() == 48, "stock decremented for product 1");
        check(catalog.get(2).getStock() == 29, "stock decremented for product 2");
        check(orders.get(orderId) != null, "order stored");
        check("CONFIRMED".equals(orders.get(orderId).getStatus()), "order status CONFIRMED");

        // Duplicate product lines in one request are merged.
        Map<String, Object> res2 = Protocol.handle(
                Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":3,\"qty\":1},{\"productId\":3,\"qty\":2}]}"),
                catalog, orders);
        check("ok".equals(res2.get("status")), "duplicate lines merged");
        check(catalog.get(3).getStock() == 22, "merged qty 3 reserved for product 3");
    }

    private static void testPlaceOrderErrors() {
        Catalog catalog = freshCatalog();
        OrderStore orders = new OrderStore();
        int stockBefore = catalog.get(1).getStock();

        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":999,\"qty\":1}]}"),
                catalog, orders, "unknown product");
        check(catalog.get(1).getStock() == stockBefore, "no stock touched on unknown product");

        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":5,\"qty\":999}]}"),
                catalog, orders, "insufficient stock");
        check(catalog.get(5).getStock() == 15, "no stock touched on insufficient stock");

        // Atomicity: one good line + one bad line => nothing reserved.
        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":1,\"qty\":1},{\"productId\":5,\"qty\":999}]}"),
                catalog, orders, "insufficient stock");
        check(catalog.get(1).getStock() == stockBefore, "reservation is all-or-nothing");

        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":1,\"qty\":0}]}"),
                catalog, orders, "positive");
        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[]}"), catalog, orders, "empty");
        checkError(Json.parse("{\"type\":\"PLACE_ORDER\"}"), catalog, orders, "array");
        checkError(Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":\"x\",\"qty\":1}]}"),
                catalog, orders, "numeric");
    }

    @SuppressWarnings("unchecked")
    private static void testOrderStatus() {
        Catalog catalog = freshCatalog();
        OrderStore orders = new OrderStore();
        Map<String, Object> placed = Protocol.handle(
                Json.parse("{\"type\":\"PLACE_ORDER\",\"items\":[{\"productId\":4,\"qty\":1}]}"),
                catalog, orders);
        String orderId = (String) ((Map<String, Object>) placed.get("data")).get("orderId");

        Map<String, Object> res = Protocol.handle(
                Json.parse("{\"type\":\"ORDER_STATUS\",\"orderId\":\"" + orderId + "\"}"), catalog, orders);
        check("ok".equals(res.get("status")), "ORDER_STATUS status ok");
        Map<String, Object> data = (Map<String, Object>) res.get("data");
        check(orderId.equals(data.get("orderId")), "status returns the right order");
        check(Double.valueOf(39.99).equals(data.get("total")), "status returns the total");

        checkError(Json.parse("{\"type\":\"ORDER_STATUS\",\"orderId\":\"ORD-NOPE123\"}"),
                catalog, orders, "unknown order id");
        checkError(Json.parse("{\"type\":\"ORDER_STATUS\"}"), catalog, orders, "orderId");
    }

    private static void testProtocolErrors() {
        Catalog catalog = freshCatalog();
        OrderStore orders = new OrderStore();
        checkError(Json.parse("{\"type\":\"DELETE_EVERYTHING\"}"), catalog, orders, "unknown request type");
        checkError(Json.parse("{}"), catalog, orders, "type");
        checkError(Json.parse("[1,2]"), catalog, orders, "JSON object");
        checkError(Json.parse("\"hello\""), catalog, orders, "JSON object");
    }

    private static void testMoneyRounding() {
        check(Money.round2(0.1 + 0.2) == 0.3, "0.1+0.2 rounds to 0.3");
        check(Money.round2(29.99 * 3) == 89.97, "29.99*3 rounds to 89.97");
        check(Money.round2(10.0) == 10.0, "whole amounts unchanged");
    }

    // ------------------------------------------------------------------ helpers

    private static void checkError(Object request, Catalog catalog, OrderStore orders, String messagePart) {
        Map<String, Object> res = Protocol.handle(request, catalog, orders);
        boolean ok = "error".equals(res.get("status"))
                && res.get("message") instanceof String
                && ((String) res.get("message")).toLowerCase().contains(messagePart.toLowerCase());
        check(ok, "error containing '" + messagePart + "', got: " + Json.stringify(res));
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError("FAILED: " + name);
        }
        passed++;
        System.out.println("PASS: " + name);
    }
}
