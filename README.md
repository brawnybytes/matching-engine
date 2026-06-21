# Trading System Matching Engine

A minimal, interview-ready **order matching engine** written in Java — no frameworks, no external dependencies, plain Maven console application.

Built as a deep-dive into exchange internals: price-time priority matching, order book data structures, and the maker/taker model used by real trading venues.

**Author:** [Rohit Kori](https://github.com/brawnybytes) ([brawnybytes](https://github.com/brawnybytes))

---

## Status

- ✅ Core matching logic (LIMIT, MARKET, partial fills, cancellation)
- ✅ Multi-symbol routing with isolated order books
- ✅ Five-scenario runnable demo
- ⬜ Unit tests (planned)
- ⬜ Concurrency / throughput benchmarking (planned)
- ⬜ FIX protocol order entry (out of scope for this version)

This is a learning/demonstration project, not a production system — see [Design Decisions](#design-decisions) below for what's intentionally left out and why.

---

## What Is a Matching Engine?

A matching engine is the core of any exchange (stock, crypto, commodities). It receives buy and sell orders from traders and pairs them together when their prices agree, producing a **trade** (execution). Every exchange from NYSE to Binance runs one at its heart.

---

## How the Application Works

### The Big Picture

```
Trader A (BUY)  ──┐
                  ▼
            MatchingEngine
                  │
                  ├── OrderBook [AAPL]  ← bids & asks for one symbol
                  ├── OrderBook [TSLA]
                  └── OrderBook [GOOGL]
                  │
                  ▼
            Trade generated → Trade Log
                  │
Trader B (SELL) ◄─┘  (both orders get filled)
```

### Symbol Isolation

`MatchingEngine` maintains **one `OrderBook` per symbol**, created lazily on the first order for that symbol:

```java
OrderBook book = books.computeIfAbsent(order.getSymbol(), OrderBook::new);
```

An AAPL order physically never enters a TSLA book — isolation is structural, not checked inside the matching logic.

---

## Order Book: Data Structure

Each `OrderBook` holds two sorted price-level maps:

```
BUY  side → TreeMap<price, Deque<Order>>   descending  (highest bid first)
SELL side → TreeMap<price, Deque<Order>>   ascending   (lowest ask first)
```

| Structure | Why |
|---|---|
| `TreeMap` | O(log n) insert/delete, O(1) peek at best bid/ask |
| `Deque` per level | FIFO queue — enforces time priority within a price level |
| `HashMap<orderId, Order>` | O(1) order lookup for cancellations |

This implements **price-time priority** — the standard used by most real exchanges:
1. Best price wins first.
2. Among equal prices, earlier orders win first.

---

## Matching Algorithm

On every incoming order, the engine runs this loop:

```
While the incoming order has remaining quantity
  AND the opposite side of the book is not empty:

    1. Peek at the best resting price on the opposite side
    2. Check if prices cross:
         BUY  order:  limit price  >=  best ask?
         SELL order:  limit price  <=  best bid?
         MARKET order: always crosses
    3. If yes → fill greedily (FIFO within the price level)
         fillQty  = min(incoming.remaining, resting.remaining)
         fillPrice = resting order's price  (maker sets the price)
         → emit a Trade
    4. Remove fully filled resting orders / empty price levels

If the incoming order still has remaining quantity:
    LIMIT  → rest it in the book at its limit price
    MARKET → cancel remainder (market orders never rest)
```

---

## Order Types

| Type | Behaviour |
|---|---|
| **LIMIT** | Specifies a price. Matches only if the spread crosses. Remainder rests in the book. |
| **MARKET** | No price. Matches against whatever is available immediately. Unfilled remainder is cancelled. |

---

## Order Lifecycle

```
NEW → (partial fills) → PARTIAL → (fully filled) → FILLED
                                                  ↘
                                              CANCELLED  (cancel request or unfilled market order)
```

---

## Maker vs Taker

Every `Trade` records which order was the **maker** and which was the **taker**:

- **Maker** — the resting order that was already in the book (adds liquidity)
- **Taker** — the incoming order that triggered the match (removes liquidity)

Real exchanges charge lower fees to makers to incentivise liquidity provision.

---

## Project Structure

```
matching-engine/
├── pom.xml
└── src/main/java/com/exchange/
    ├── Main.java                    ← 5 demo scenarios
    ├── model/
    │   ├── Order.java               ← domain object (id, symbol, side, type, price, qty)
    │   └── Trade.java               ← execution record (maker, taker, price, qty)
    └── engine/
        ├── MatchingEngine.java      ← multi-symbol router, trade log
        └── OrderBook.java           ← price-time priority matching per symbol
```

---

## Running the Demo

```bash
mvn package
java -jar target/matching-engine-1.0-SNAPSHOT.jar
```

### Demo Scenarios

The `Main.java` demo walks through five scenarios:

| # | Scenario | What it demonstrates |
|---|---|---|
| 1 | Build the book | Six orders rest with no match (spread exists) |
| 2 | Limit order crosses spread | Full fill at the maker's price |
| 3 | Partial fill | Incoming order fills partially; remainder rests |
| 4 | Market order | Sweeps the best available bid, ignores price |
| 5 | Cancel | Removes a resting order from the book |

---

## Design Decisions

**`long` for prices, not `double`**  
Floating-point arithmetic has rounding errors. All prices are stored as integer cents (`$151.50 → 15150`).

**No external dependencies**  
Pure Java standard library — `TreeMap`, `ArrayDeque`, `HashMap`. Easier to read and reason about in an interview.

**Single-threaded**  
Thread safety is intentionally omitted for clarity. Production engines typically use the **LMAX Disruptor** pattern — a single sequencer thread feeding a lock-free ring buffer — to achieve millions of orders/second without locks.

**Defensive symbol check (production hardening)**  
A useful improvement to mention: adding `assert incoming.getSymbol().equals(this.symbol)` at the top of `OrderBook.submit()` to catch routing bugs early.

---

## What a Production Engine Would Add

- **Pre-trade risk checks** — position limits, credit checks before the order enters the book
- **Order types** — stop orders, iceberg orders, fill-or-kill, immediate-or-cancel
- **Persistence** — event-sourced order log for crash recovery
- **Market data feed** — publishing best bid/ask and trade prints downstream (e.g. via Kafka)
- **Concurrency** — one thread per symbol with a Disruptor, or actor-per-book model
- **FIX protocol** — standard financial messaging format for order entry