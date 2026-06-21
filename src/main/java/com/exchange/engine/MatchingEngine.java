package com.exchange.engine;

import com.exchange.model.Order;
import com.exchange.model.Trade;

import java.util.*;

/**
 * MatchingEngine manages one OrderBook per traded symbol.
 *
 * Responsibilities:
 *   • Route incoming orders to the correct book.
 *   • Maintain a global trade log.
 *   • Provide cancel and status lookup across all books.
 *
 * Interview talking point: in a production system this layer would also
 * handle risk checks (pre-trade), position tracking, and publishing
 * execution reports to a downstream bus (e.g. Kafka).
 *
 * Thread safety: NOT thread-safe by design — kept single-threaded for
 * interview clarity.  Real engines either lock per-symbol or use a
 * sequenced disruptor pattern (LMAX architecture).
 */
public class MatchingEngine {

    // One book per symbol — created lazily on first order for that symbol
    private final Map<String, OrderBook> books = new HashMap<>();

    // Global ordered list of all executions (useful for audit / reporting)
    private final List<Trade> tradeLog = new ArrayList<>();

    // ---------------------------------------------------------------
    // Core operations
    // ---------------------------------------------------------------

    /**
     * Submit a new order.  Triggers matching and returns any trades.
     */
    public List<Trade> submitOrder(Order order) {
        System.out.printf("%n[ENGINE] Received %s%n", order);

        OrderBook book = books.computeIfAbsent(
            order.getSymbol(), OrderBook::new);

        List<Trade> trades = book.submit(order);
        tradeLog.addAll(trades);

        if (trades.isEmpty()) {
            System.out.println("  [ENGINE] No match — order resting in book");
        } else {
            System.out.printf("  [ENGINE] %d trade(s) executed%n", trades.size());
        }

        return trades;
    }

    /**
     * Cancel a resting order by id on the given symbol.
     */
    public boolean cancelOrder(String symbol, long orderId) {
        OrderBook book = books.get(symbol);
        if (book == null) return false;

        boolean cancelled = book.cancel(orderId);
        System.out.printf("%n[ENGINE] Cancel order %d → %s%n",
            orderId, cancelled ? "OK" : "FAILED (not found / already done)");
        return cancelled;
    }

    /**
     * Print the order book for a symbol.
     */
    public void printBook(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) {
            System.out.println("[ENGINE] No book found for " + symbol);
            return;
        }
        book.printBook();
    }

    /**
     * Print the full trade log.
     */
    public void printTradeLog() {
        System.out.println("\n══════════ TRADE LOG ══════════");
        if (tradeLog.isEmpty()) {
            System.out.println("  (no trades yet)");
        } else {
            tradeLog.forEach(t -> System.out.println("  " + t));
        }
        System.out.println("═══════════════════════════════");
    }

    public List<Trade> getTradeLog() { return Collections.unmodifiableList(tradeLog); }
}
