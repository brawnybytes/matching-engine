package com.exchange.engine;

import com.exchange.model.Order;
import com.exchange.model.Order.Side;
import com.exchange.model.Trade;

import java.util.*;

/**
 * OrderBook for a single trading symbol.
 *
 * ── Data structures ────────────────────────────────────────────────
 *
 *  BUY  side  → TreeMap<price, Queue<Order>>  descending  (highest bid first)
 *  SELL side  → TreeMap<price, Queue<Order>>  ascending   (lowest ask first)
 *
 *  Within a price level orders are stored in a LinkedList to preserve
 *  FIFO (time) priority — classic "price-time priority" matching.
 *
 * Interview talking point: why TreeMap?  O(log n) insert/delete on the
 * price dimension; O(1) peek at best bid/ask.  Alternative: skip list
 * (used by some real engines for better concurrent performance).
 *
 * ── Matching algorithm ─────────────────────────────────────────────
 *
 *  For each incoming order:
 *    1. Walk the opposite side as long as prices cross.
 *    2. Greedily fill against the best resting price level (FIFO within level).
 *    3. If the incoming order still has remaining qty → rest it in the book.
 *
 *  MARKET orders: always cross the spread (price = Long.MAX_VALUE for buys,
 *  0 for sells) so they match against everything available.
 */
public class OrderBook {

    private final String symbol;

    // Price-level → FIFO queue of resting orders
    // BUY:  highest price first  → reverse natural order
    private final TreeMap<Long, Deque<Order>> bids =
        new TreeMap<>(Comparator.reverseOrder());

    // SELL: lowest price first   → natural order
    private final TreeMap<Long, Deque<Order>> asks =
        new TreeMap<>();

    // Fast lookup: orderId → Order (needed for cancel)
    private final Map<Long, Order> orderIndex = new HashMap<>();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    // ---------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------

    /**
     * Submit an order to the book.
     * Returns the list of trades that were generated (may be empty).
     */
    public List<Trade> submit(Order incoming) {
        List<Trade> trades = new ArrayList<>();

        // Index the order so it can be cancelled later
        orderIndex.put(incoming.getOrderId(), incoming);

        // Try to match against the opposite side
        match(incoming, trades);

        // If the order still has remaining quantity and is a LIMIT order,
        // it rests in the book.  MARKET orders that don't fill are cancelled
        // (no resting market orders — standard exchange behaviour).
        if (incoming.isActive()) {
            if (incoming.getType() == Order.Type.LIMIT) {
                addToBook(incoming);
            } else {
                // Unfilled market order → cancel remainder
                incoming.cancel();
                System.out.printf("  [BOOK] Market order %d cancelled (no liquidity remaining)%n",
                    incoming.getOrderId());
            }
        }

        return trades;
    }

    /**
     * Cancel a resting order by id.
     * Returns true if successfully cancelled.
     */
    public boolean cancel(long orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null || !order.isActive()) {
            return false;
        }

        order.cancel();

        // Remove from price-level map
        TreeMap<Long, Deque<Order>> side = (order.getSide() == Side.BUY) ? bids : asks;
        Deque<Order> level = side.get(order.getPrice());
        if (level != null) {
            level.remove(order);          // O(n) within level — acceptable for demo
            if (level.isEmpty()) {
                side.remove(order.getPrice());
            }
        }

        return true;
    }

    // ---------------------------------------------------------------
    // Matching logic
    // ---------------------------------------------------------------

    private void match(Order incoming, List<Trade> trades) {
        // Determine which side of the book to match against
        TreeMap<Long, Deque<Order>> restingSide =
                (incoming.getSide() == Side.BUY) ? asks : bids;

        while (incoming.isActive() && !restingSide.isEmpty()) {

            // Best resting price on the opposite side
            Map.Entry<Long, Deque<Order>> bestLevel = restingSide.firstEntry();
            long bestPrice = bestLevel.getKey();

            // Price-crossing check
            // BUY  order crosses if its limit price >= best ask
            // SELL order crosses if its limit price <= best bid
            // MARKET orders always cross (we use sentinel prices)
            if (!pricesCross(incoming, bestPrice)) {
                break;  // No more matches possible
            }

            Deque<Order> queue = bestLevel.getValue();

            // Walk FIFO queue at this price level
            while (!queue.isEmpty() && incoming.isActive()) {
                Order resting = queue.peekFirst();

                // Skip already-filled / cancelled resting orders
                // (defensive; shouldn't normally happen)
                if (!resting.isActive()) {
                    queue.pollFirst();
                    continue;
                }

                // Execute the fill
                long fillQty = Math.min(incoming.remainingQty(), resting.remainingQty());
                long fillPrice = resting.getPrice(); // maker sets the price

                incoming.fill(fillQty);
                resting.fill(fillQty);

                Trade trade = new Trade(symbol,
                    resting.getOrderId(),   // maker
                    incoming.getOrderId(),  // taker
                    fillPrice, fillQty);

                trades.add(trade);

                System.out.printf("  [MATCH] %s%n", trade);

                // Remove fully filled resting order from level
                if (resting.isFilled()) {
                    queue.pollFirst();
                }
            }

            // Remove empty price level from the book
            if (queue.isEmpty()) {
                restingSide.pollFirstEntry();
            }
        }
    }

    /**
     * Does the incoming order's price cross the best resting price?
     *
     *  Market orders always cross.
     *  Limit BUY  crosses if limit price >= ask price.
     *  Limit SELL crosses if limit price <= bid price.
     */
    private boolean pricesCross(Order incoming, long restingPrice) {
        if (incoming.getType() == Order.Type.MARKET) return true;

        if (incoming.getSide() == Side.BUY) {
            return incoming.getPrice() >= restingPrice;
        } else {
            return incoming.getPrice() <= restingPrice;
        }
    }

    // ---------------------------------------------------------------
    // Book maintenance
    // ---------------------------------------------------------------

    private void addToBook(Order order) {
        TreeMap<Long, Deque<Order>> side = (order.getSide() == Side.BUY) ? bids : asks;
        // getOrDefault and put a new LinkedList if the price level is new
        side.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>()).addLast(order);
    }

    // ---------------------------------------------------------------
    // Reporting
    // ---------------------------------------------------------------

    /** Pretty-print the current state of the order book. */
    public void printBook() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.printf( "║  Order Book — %-22s║%n", symbol);
        System.out.println("╠══════════════════════════════════════╣");

        // Print asks in ascending price order, reversed for display
        List<Map.Entry<Long, Deque<Order>>> askLevels = new ArrayList<>(asks.entrySet());
        Collections.reverse(askLevels);  // highest ask on top
        for (Map.Entry<Long, Deque<Order>> entry : askLevels) {
            long qty = entry.getValue().stream().mapToLong(Order::remainingQty).sum();
            System.out.printf("║  ASK  %8.2f  %10d         ║%n",
                entry.getKey() / 100.0, qty);
        }

        System.out.println("║  ─────────── spread ──────────────  ║");

        // Print bids in descending price order (TreeMap already does this)
        for (Map.Entry<Long, Deque<Order>> entry : bids.entrySet()) {
            long qty = entry.getValue().stream().mapToLong(Order::remainingQty).sum();
            System.out.printf("║  BID  %8.2f  %10d         ║%n",
                entry.getKey() / 100.0, qty);
        }

        System.out.println("╚══════════════════════════════════════╝");
    }

    // ---------------------------------------------------------------
    // Getters (useful for testing / market-data feed)
    // ---------------------------------------------------------------

    public OptionalLong bestBid() {
        return bids.isEmpty()
            ? OptionalLong.empty()
            : OptionalLong.of(bids.firstKey());
    }

    public OptionalLong bestAsk() {
        return asks.isEmpty()
            ? OptionalLong.empty()
            : OptionalLong.of(asks.firstKey());
    }

    public String getSymbol() { return symbol; }
}
