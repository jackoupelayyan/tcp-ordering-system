package com.jackoupelayyan.ordering;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interactive command-line client for the ordering server.
 *
 * <p>Commands:
 * <pre>
 *   list                 - show the product catalog
 *   order 1:2 3:1        - order 2x product 1 and 1x product 3
 *   status ORD-XXXXXXXX   - look up an order
 *   help                 - show this help
 *   quit                 - exit
 * </pre>
 *
 * <p>Usage: {@code java ... OrderClient [host] [port]} — the port may also
 * come from the {@code PORT} environment variable; the default is 5000.
 */
public final class OrderClient {
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 5000;

    private OrderClient() {
        // entry point class
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = args.length > 1 ? Integer.parseInt(args[1]) : resolvePort();

        System.out.println("Connecting to " + host + ":" + port + " ...");
        Socket socket = new Socket();
        try {
            // Fail fast instead of hanging forever when the server is down.
            socket.connect(new InetSocketAddress(host, port), 5_000);
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
            return;
        }
        try (socket;
             BufferedReader serverIn = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverOut = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader console = new BufferedReader(
                     new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            System.out.println("Connected. Type 'help' for commands.");
            String line;
            while (true) {
                System.out.print("> ");
                System.out.flush();
                line = console.readLine();
                if (line == null) {
                    System.out.println();
                    break; // EOF (e.g. piped input ended)
                }
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase();
                switch (command) {
                    case "quit", "exit" -> {
                        System.out.println("Bye!");
                        return;
                    }
                    case "help" -> printHelp();
                    case "list" -> doList(serverIn, serverOut);
                    case "order" -> doOrder(parts, serverIn, serverOut);
                    case "status" -> doStatus(parts, serverIn, serverOut);
                    default -> System.out.println("Unknown command '" + parts[0] + "'. Type 'help'.");
                }
            }
        } catch (IOException e) {
            System.out.println("Connection failed: " + e.getMessage());
        }
    }

    private static int resolvePort() {
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env.trim());
        }
        return DEFAULT_PORT;
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  list                  show the product catalog");
        System.out.println("  order 1:2 3:1         order 2x product 1 and 1x product 3");
        System.out.println("  status ORD-XXXXXXXX    look up an order");
        System.out.println("  help                  show this help");
        System.out.println("  quit                  exit");
    }

    // ------------------------------------------------------------ commands

    @SuppressWarnings("unchecked")
    private static void doList(BufferedReader serverIn, PrintWriter serverOut) throws IOException {
        Map<String, Object> response = request(serverIn, serverOut, Json.obj("type", "LIST_PRODUCTS"));
        if (!isOk(response)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        List<Object> products = (List<Object>) data.get("products");
        System.out.printf("%-4s %-30s %10s %8s%n", "ID", "Product", "Price", "Stock");
        System.out.println("-".repeat(56));
        for (Object p : products) {
            Map<String, Object> product = (Map<String, Object>) p;
            System.out.printf("%-4d %-30s %10.2f %8d%n",
                    ((Number) product.get("id")).intValue(),
                    truncate(String.valueOf(product.get("name")), 30),
                    ((Number) product.get("price")).doubleValue(),
                    ((Number) product.get("stock")).intValue());
        }
    }

    @SuppressWarnings("unchecked")
    private static void doOrder(String[] parts, BufferedReader serverIn, PrintWriter serverOut) throws IOException {
        if (parts.length < 2) {
            System.out.println("Usage: order <productId:qty> [<productId:qty> ...]   e.g. order 1:2 3:1");
            return;
        }
        List<Object> items = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            String[] pair = parts[i].split(":");
            if (pair.length != 2) {
                System.out.println("Bad item '" + parts[i] + "'. Expected <productId:qty>, e.g. 1:2");
                return;
            }
            try {
                int productId = Integer.parseInt(pair[0]);
                int qty = Integer.parseInt(pair[1]);
                items.add(Json.obj("productId", productId, "qty", qty));
            } catch (NumberFormatException e) {
                System.out.println("Bad item '" + parts[i] + "'. Product id and qty must be numbers.");
                return;
            }
        }
        Map<String, Object> response = request(serverIn, serverOut,
                Json.obj("type", "PLACE_ORDER", "items", items));
        if (!isOk(response)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        System.out.println("Order confirmed!");
        System.out.println("  Order ID : " + data.get("orderId"));
        System.out.println("  Status   : " + data.get("status"));
        System.out.println("  Items    :");
        for (Object o : (List<Object>) data.get("items")) {
            Map<String, Object> item = (Map<String, Object>) o;
            System.out.printf("    %dx %s @ $%.2f = $%.2f%n",
                    ((Number) item.get("qty")).intValue(),
                    item.get("name"),
                    ((Number) item.get("unitPrice")).doubleValue(),
                    ((Number) item.get("lineTotal")).doubleValue());
        }
        System.out.printf("  Total    : $%.2f%n", ((Number) data.get("total")).doubleValue());
    }

    @SuppressWarnings("unchecked")
    private static void doStatus(String[] parts, BufferedReader serverIn, PrintWriter serverOut) throws IOException {
        if (parts.length != 2) {
            System.out.println("Usage: status <orderId>   e.g. status ORD-1A2B3C4D");
            return;
        }
        Map<String, Object> response = request(serverIn, serverOut,
                Json.obj("type", "ORDER_STATUS", "orderId", parts[1]));
        if (!isOk(response)) {
            return;
        }
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        System.out.println("  Order ID : " + data.get("orderId"));
        System.out.println("  Status   : " + data.get("status"));
        System.out.println("  Items    :");
        for (Object o : (List<Object>) data.get("items")) {
            Map<String, Object> item = (Map<String, Object>) o;
            System.out.printf("    %dx %s @ $%.2f = $%.2f%n",
                    ((Number) item.get("qty")).intValue(),
                    item.get("name"),
                    ((Number) item.get("unitPrice")).doubleValue(),
                    ((Number) item.get("lineTotal")).doubleValue());
        }
        System.out.printf("  Total    : $%.2f%n", ((Number) data.get("total")).doubleValue());
    }

    // ------------------------------------------------------------ helpers

    /**
     * Sends one request object and reads back one response line.
     * Returns null when the server closed the connection.
     */
    private static Map<String, Object> request(BufferedReader serverIn, PrintWriter serverOut,
                                               Map<String, Object> request) throws IOException {
        serverOut.println(Json.stringify(request));
        String line = serverIn.readLine();
        if (line == null) {
            System.out.println("Server closed the connection.");
            return null;
        }
        Object parsed = Json.parse(line);
        if (!(parsed instanceof Map)) {
            System.out.println("Got a malformed response from the server.");
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) parsed;
        return response;
    }

    private static boolean isOk(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        if ("ok".equals(response.get("status"))) {
            return true;
        }
        System.out.println("Error: " + response.get("message"));
        return false;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
