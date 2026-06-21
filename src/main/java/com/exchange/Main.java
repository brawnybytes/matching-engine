package com.exchange;

import com.exchange.engine.MatchingEngine;
import com.exchange.model.Order;
import com.exchange.model.Order.Side;
import com.exchange.model.Order.Type;

/**
 * ══════════════════════════════════════════════════════════════════
 *  MINIMAL TRADING SYSTEM — MATCHING ENGINE DEMO
 *  ─────────────────────────────────────────────
 *  Run:  mvn -q package && java -jar target/matching-engine-1.0-SNAPSHOT.jar
 * ══════════════════════════════════════════════════════════════════
 *
 * This demo walks through five scenarios that cover the engine's
 * main behaviours.  Each scenario prints to stdout so you can follow
 * along in an interview setting.
 *
 * Scenarios:
 *   1. Orders rest in the book with no match yet (spread exists)
 *   2. Incoming limit order crosses the spread → full fill
 *   3. Incoming order partially fills → rests remainder
 *   4. Market order sweeps multiple price levels
 *   5. Cancel a resting order
 */
public class Main {

    // Helper: prices are stored as cents; this makes call sites readable.
    // e.g.  px(100.50)  → 10050L
    private static long px(double dollars) {
        return Math.round(dollars * 100);
    }

    public static void main(String[] args) {

        MatchingEngine engine = new MatchingEngine();
        String sym = "AAPL";

        separator("SCENARIO 1 — Build the book (no matches expected)");

        // Three sell (ask) orders at different prices
        engine.submitOrder(new Order(sym, Side.SELL, Type.LIMIT, px(152.00), 100));
        engine.submitOrder(new Order(sym, Side.SELL, Type.LIMIT, px(151.50), 200));
        engine.submitOrder(new Order(sym, Side.SELL, Type.LIMIT, px(151.00), 150));

        // Three buy (bid) orders at different prices
        Order bid1 = new Order(sym, Side.BUY, Type.LIMIT, px(150.00), 300);
        engine.submitOrder(bid1);
        engine.submitOrder(new Order(sym, Side.BUY, Type.LIMIT, px(149.50), 200));
        engine.submitOrder(new Order(sym, Side.BUY, Type.LIMIT, px(149.00), 100));

        // Snapshot: spread = 150.00 bid / 151.00 ask
        engine.printBook(sym);

        // ─────────────────────────────────────────────────────────

        separator("SCENARIO 2 — Limit order fully crosses the spread");

        // A new buy at 151.00 will match the resting sell at 151.00 (qty 150)
        // and fully fill both sides.
        engine.submitOrder(new Order(sym, Side.BUY, Type.LIMIT, px(151.00), 150));
        engine.printBook(sym);

        // ─────────────────────────────────────────────────────────

        separator("SCENARIO 3 — Partial fill; remainder rests");

        // Buy 300 @ 151.50.  The book now has 200 @ 151.50.
        // → fills 200 @ 151.50, then 100 rests as the new best bid.
        engine.submitOrder(new Order(sym, Side.BUY, Type.LIMIT, px(151.50), 300));
        engine.printBook(sym);

        // ─────────────────────────────────────────────────────────

        separator("SCENARIO 4 — Market order sweeps remaining asks");

        // Remaining asks: 100 @ 152.00.
        // Market sell of 50 → matches 50 @ top bid (currently 151.50 partial remainder).
        engine.submitOrder(new Order(sym, Side.SELL, Type.MARKET, 0, 50));
        engine.printBook(sym);

        // ─────────────────────────────────────────────────────────

        separator("SCENARIO 5 — Cancel a resting order");

        // bid1 was placed in scenario 1 at 150.00, still resting.
        engine.cancelOrder(sym, bid1.getOrderId());
        engine.printBook(sym);

        // ─────────────────────────────────────────────────────────

        separator("FINAL TRADE LOG");
        engine.printTradeLog();
    }

    // ---------------------------------------------------------------
    // Utility
    // ---------------------------------------------------------------
    private static void separator(String title) {
        System.out.println("\n" + "━".repeat(56));
        System.out.println("  " + title);
        System.out.println("━".repeat(56));
    }
}
