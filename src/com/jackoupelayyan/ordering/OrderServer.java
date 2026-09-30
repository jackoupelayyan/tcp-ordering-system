package com.jackoupelayyan.ordering;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TCP ordering server.
 *
 * <p>Listens on a {@link ServerSocket} and hands each accepted connection to a
 * fixed-size thread pool running {@link ConnectionHandler}. A shutdown hook
 * closes the socket and drains the pool gracefully on SIGTERM / Ctrl-C.
 *
 * <p>Usage: {@code java ... OrderServer [port]} — the port may also come from
 * the {@code PORT} environment variable; the default is 5000.
 */
public final class OrderServer {
    private static final int DEFAULT_PORT = 5000;
    private static final int POOL_SIZE = 20;

    private OrderServer() {
        // entry point class
    }

    public static void main(String[] args) throws IOException {
        int port = resolvePort(args);
        Catalog catalog = new Catalog();
        OrderStore orders = new OrderStore();
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

        ServerSocket server = new ServerSocket(port);
        log("listening on port " + port + " (" + catalog.all().size() + " products loaded)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("shutdown requested, closing...");
            try {
                server.close();
            } catch (IOException ignored) {
                // already closed
            }
            pool.shutdown();
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log("bye");
        }, "shutdown-hook"));

        try {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    log("connection from " + client.getRemoteSocketAddress());
                    pool.submit(new ConnectionHandler(client, catalog, orders));
                } catch (SocketException e) {
                    // Thrown by accept() when the shutdown hook closes the socket.
                    break;
                }
            }
        } finally {
            try {
                server.close();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    private static int resolvePort(String[] args) {
        if (args.length > 0) {
            return Integer.parseInt(args[0]);
        }
        String env = System.getenv("PORT");
        if (env != null && !env.isBlank()) {
            return Integer.parseInt(env.trim());
        }
        return DEFAULT_PORT;
    }

    /** Timestamped console logging shared by the server and its handlers. */
    static void log(String message) {
        System.out.printf("[%tT] %s%n", System.currentTimeMillis(), message);
        System.out.flush();
    }
}
