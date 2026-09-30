# TCP Ordering System

A multi-threaded **Java TCP client/server ordering system** speaking a tiny
**JSON-lines protocol** (one JSON object per line, one JSON response per
request). The server keeps an in-memory product catalog and order store;
the client is an interactive command-line shop.

Originally built as a university networking course project (University of
Jordan) and rebuilt here as a clean, dependency-free portfolio version.

**Zero dependencies** — compiles and runs with plain `javac`/`java`.
The small JSON parser/writer in `Json.java` exists precisely so no
external library is needed.

## Features

- `ServerSocket` + fixed-size thread pool serving many clients concurrently
- Thread-safe in-memory catalog (`ConcurrentHashMap`) and order store
- **Atomic order reservation** — an order either reserves every line item or
  nothing at all (no partial stock decrements under concurrency)
- JSON-lines protocol with clear `ok` / `error` responses
- Interactive CLI client (`list`, `order`, `status`, `help`, `quit`)
- Graceful shutdown via shutdown hook (Ctrl-C / SIGTERM)
- Timestamped server-side request logging
- Dependency-free self tests (`SelfTest`) — no JUnit required

## Architecture

```
┌──────────────┐   TCP socket, UTF-8,   ┌───────────────────────────────┐
│  OrderClient │ ◄─── JSON lines ──────► │          OrderServer          │
│  (CLI REPL)  │   one object per line   │  ServerSocket + thread pool   │
└──────────────┘                         │    └─► ConnectionHandler     │
                                         │          (one per client)     │
                                         │             │                 │
                                         │             ▼                 │
                                         │         Protocol.handle()     │
                                         │        / stateless /          │
                                         │       /     │       \         │
                                         │  Catalog  OrderStore  Json    │
                                         │ (products) (orders) (parser) │
                                         └───────────────────────────────┘
```

- **ConnectionHandler** is pure I/O: read a line, parse, dispatch, write a
  line. All business logic lives in **`Protocol`**, which is why the protocol
  can be unit-tested without opening a single socket.
- The catalog uses a `ConcurrentHashMap` for lock-free reads; `reserve()`
  is `synchronized` so multi-item stock checks + decrements are atomic.

## Protocol spec

Every message is a single JSON object terminated by `\n`.

### Requests

| Request | Example |
|---|---|
| `LIST_PRODUCTS` | `{"type":"LIST_PRODUCTS"}` |
| `PLACE_ORDER` | `{"type":"PLACE_ORDER","items":[{"productId":1,"qty":2},{"productId":5,"qty":1}]}` |
| `ORDER_STATUS` | `{"type":"ORDER_STATUS","orderId":"ORD-45D16876"}` |

### Responses

Success: `{"status":"ok","data":{...}}` — Error: `{"status":"error","message":"..."}`

| Request | Success `data` | Error cases |
|---|---|---|
| `LIST_PRODUCTS` | `{"products":[{"id":1,"name":"Wireless Mouse","price":29.99,"stock":50}, ...]}` | — |
| `PLACE_ORDER` | `{"orderId":"ORD-45D16876","status":"CONFIRMED","items":[{"productId":1,"name":"Wireless Mouse","unitPrice":29.99,"qty":2,"lineTotal":59.98}, ...],"total":189.97}` | malformed JSON · `items` not an array · unknown product id · non-positive qty · insufficient stock |
| `ORDER_STATUS` | `{"orderId":"...","status":"CONFIRMED","items":[...],"total":189.97,"createdAtMillis":...}` | missing `orderId` · unknown order id |

Any malformed JSON line gets `{"status":"error","message":"malformed JSON: ..."}`
instead of dropping the connection, and an unknown `"type"` gets
`{"status":"error","message":"unknown request type: ..."}`.

## Project structure

```
tcp-ordering-system/
├── build.sh                 # javac build (zero dependencies)
├── run-server.sh            # start the server (port arg or $PORT, default 5000)
├── run-client.sh            # start the interactive client ([host], $PORT)
├── src/com/jackoupelayyan/ordering/
│   ├── Json.java            # minimal JSON parser/writer (no deps)
│   ├── Protocol.java        # request dispatch + response building (testable, no I/O)
│   ├── ConnectionHandler.java # per-connection socket loop
│   ├── OrderServer.java     # ServerSocket, thread pool, shutdown hook
│   ├── OrderClient.java     # interactive CLI
│   ├── Catalog.java         # thread-safe product catalog + atomic reservation
│   ├── OrderStore.java      # thread-safe order store
│   ├── Product.java         # product model
│   ├── Order.java           # order model
│   ├── OrderLine.java       # order line record
│   ├── Money.java           # cents rounding helper
│   └── SelfTest.java        # dependency-free tests, run with plain `java`
├── README.md
├── LICENSE
└── .gitignore
```

## Build and run

Requirements: any JDK 17+.

```bash
./build.sh            # compiles everything into out/

# Terminal 1 — server (port 5000, or PORT=6000 ./run-server.sh)
./run-server.sh

# Terminal 2 — client
./run-client.sh
# ./run-client.sh 192.168.1.10   # against a remote host
```

Run the self tests (60 assertions, no JUnit):

```bash
./build.sh
java -cp out com.jackoupelayyan.ordering.SelfTest
```

## Example session

Raw protocol exchange with the running server (from a real session):

```
→ {"type":"LIST_PRODUCTS"}
← {"status":"ok","data":{"products":[{"id":1,"name":"Wireless Mouse","price":29.99,"stock":50},
   {"id":2,"name":"Mechanical Keyboard","price":89.99,"stock":30},
   {"id":3,"name":"USB-C Hub","price":45.5,"stock":25},
   {"id":4,"name":"Laptop Stand","price":39.99,"stock":40},
   {"id":5,"name":"Noise-Cancelling Headphones","price":129.99,"stock":15},
   {"id":6,"name":"Webcam 1080p","price":59.99,"stock":20}]}}

→ {"type":"PLACE_ORDER","items":[{"productId":1,"qty":2},{"productId":5,"qty":1}]}
← {"status":"ok","data":{"orderId":"ORD-45D16876","status":"CONFIRMED",
   "items":[{"productId":1,"name":"Wireless Mouse","unitPrice":29.99,"qty":2,"lineTotal":59.98},
            {"productId":5,"name":"Noise-Cancelling Headphones","unitPrice":129.99,"qty":1,"lineTotal":129.99}],
   "total":189.97}}

→ {"type":"ORDER_STATUS","orderId":"ORD-45D16876"}
← {"status":"ok","data":{"orderId":"ORD-45D16876","status":"CONFIRMED","items":[...],"total":189.97,...}}

→ {"type":"PLACE_ORDER","items":[{"productId":5,"qty":999}]}
← {"status":"error","message":"insufficient stock for product id 5 (only 14 available)"}

→ not even json
← {"status":"error","message":"malformed JSON: expected 'true' at index 0"}
```

The interactive client wraps the same protocol in friendly commands:

```
$ ./run-client.sh
Connecting to localhost:5000 ...
Connected. Type 'help' for commands.
> list
ID   Product                         Price    Stock
--------------------------------------------------------
1    Wireless Mouse                   29.99       50
2    Mechanical Keyboard              89.99       30
...
> order 1:2 5:1
Order confirmed!
  Order ID : ORD-45D16876
  Status   : CONFIRMED
  Items    :
    2x Wireless Mouse @ $29.99 = $59.98
    1x Noise-Cancelling Headphones @ $129.99 = $129.99
  Total    : $189.97
> status ORD-45D16876
  Order ID : ORD-45D16876
  Status   : CONFIRMED
  ...
  Total    : $189.97
> quit
Bye!
```

And the server logs every request with a timestamp:

```
[20:01:11] listening on port 5000 (6 products loaded)
[20:01:19] connection from /127.0.0.1:58582
[20:01:19] /127.0.0.1:58582 -> LIST_PRODUCTS
[20:01:19] /127.0.0.1:58582 -> PLACE_ORDER
[20:01:19] /127.0.0.1:58582 -> malformed JSON (expected 'true' at index 0)
[20:01:23] shutdown requested, closing...
[20:01:23] bye
```

## Design notes

- **No frameworks, no JSON library.** The protocol only needs a JSON subset,
  so a ~200-line recursive-descent parser beats pulling in a dependency —
  and it keeps the project buildable anywhere with just a JDK.
- **Protocol separated from transport.** `Protocol.handle()` takes a parsed
  request and returns a response map; it knows nothing about sockets, which
  makes it directly unit-testable (`SelfTest`).
- **Money** is rounded to cents at every step to avoid `0.1 + 0.2`-style
  floating point artifacts in totals.

## Future improvements

- Persist the catalog and orders (file or embedded DB) so a restart doesn't
  wipe stock and order history
- `BigDecimal` instead of `double` for money
- Order lifecycle (`PLACED` → `SHIPPED` → `DELIVERED`) instead of instant `CONFIRMED`
- Authentication / per-client sessions
- TLS for the socket layer
- Length-prefixed framing or a max line length to harden against huge inputs

## License

MIT — see [LICENSE](LICENSE).
