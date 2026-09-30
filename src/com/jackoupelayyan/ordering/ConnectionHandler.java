package com.jackoupelayyan.ordering;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Handles one client connection: reads JSON request lines, dispatches them
 * through {@link Protocol}, and writes back one JSON response line per request.
 *
 * <p>Instances run on the server's thread pool, so many clients can be served
 * concurrently. The shared {@link Catalog} and {@link OrderStore} are
 * thread-safe.
 */
public final class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final Catalog catalog;
    private final OrderStore orders;

    public ConnectionHandler(Socket socket, Catalog catalog, OrderStore orders) {
        this.socket = socket;
        this.catalog = catalog;
        this.orders = orders;
    }

    @Override
    public void run() {
        String client = socket.getRemoteSocketAddress().toString();
        OrderServer.log("serving " + client);
        try (Socket s = socket; // closes on exit
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true)) {

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                Map<String, Object> response;
                try {
                    Object request = Json.parse(line);
                    OrderServer.log(client + " -> " + describe(request));
                    response = Protocol.handle(request, catalog, orders);
                } catch (Json.JsonParseException e) {
                    OrderServer.log(client + " -> malformed JSON (" + e.getMessage() + ")");
                    response = Json.obj("status", "error", "message", "malformed JSON: " + e.getMessage());
                } catch (Exception e) {
                    OrderServer.log(client + " -> internal error: " + e);
                    response = Json.obj("status", "error", "message", "internal server error");
                }
                out.println(Json.stringify(response));
            }
        } catch (IOException e) {
            OrderServer.log(client + " I/O error: " + e.getMessage());
        }
        OrderServer.log("disconnected " + client);
    }

    private static String describe(Object request) {
        if (request instanceof Map<?, ?> map) {
            Object type = map.get("type");
            return type instanceof String ? (String) type : "(no type)";
        }
        return "(not an object)";
    }
}
