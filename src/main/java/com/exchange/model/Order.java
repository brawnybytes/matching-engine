package com.exchange.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable-ish Order value object.
 *
 * Fields that change during matching (filledQty) are mutable by design —
 * in a real system you would prefer immutable snapshots + event sourcing.
 *
 * Interview talking point: why long for price?  Floating-point arithmetic
 * causes rounding errors in financial systems; we represent price as integer
 * cents (or ticks) to keep arithmetic exact.
 */
public class Order {

    // ---------------------------------------------------------------
    // Enums — kept inside the class to keep the file count small
    // ---------------------------------------------------------------

    public enum Side   { BUY, SELL }
    public enum Type   { LIMIT, MARKET }
    public enum Status { NEW, PARTIAL, FILLED, CANCELLED }

    // ---------------------------------------------------------------
    // Auto-incrementing order-id counter (good enough for demo)
    // ---------------------------------------------------------------
    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------
    private final long    orderId;
    private final String  symbol;
    private final Side    side;
    private final Type    type;
    private final long    price;        // price in cents; 0 for MARKET orders
    private final long    quantity;     // total requested quantity
    private       long    filledQty;    // how much has traded so far
    private       Status  status;
    private final Instant createdAt;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------
    public Order(String symbol, Side side, Type type, long price, long quantity) {
        this.orderId   = ID_SEQ.getAndIncrement();
        this.symbol    = symbol;
        this.side      = side;
        this.type      = type;
        this.price     = price;
        this.quantity  = quantity;
        this.filledQty = 0;
        this.status    = Status.NEW;
        this.createdAt = Instant.now();
    }

    // ---------------------------------------------------------------
    // Business helpers
    // ---------------------------------------------------------------

    /** Remaining quantity that still needs to be matched. */
    public long remainingQty() {
        return quantity - filledQty;
    }

    /** Called by the engine each time a partial or full fill occurs. */
    public void fill(long qty) {
        if (qty <= 0 || qty > remainingQty()) {
            throw new IllegalArgumentException("Invalid fill qty: " + qty);
        }
        filledQty += qty;
        status = (filledQty == quantity) ? Status.FILLED : Status.PARTIAL;
    }

    public boolean isFilled()    { return status == Status.FILLED; }
    public boolean isCancelled() { return status == Status.CANCELLED; }
    public boolean isActive()    { return !isFilled() && !isCancelled(); }

    public void cancel() { this.status = Status.CANCELLED; }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public long    getOrderId()   { return orderId; }
    public String  getSymbol()    { return symbol; }
    public Side    getSide()      { return side; }
    public Type    getType()      { return type; }
    public long    getPrice()     { return price; }
    public long    getQuantity()  { return quantity; }
    public long    getFilledQty() { return filledQty; }
    public Status  getStatus()    { return status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return String.format("Order[id=%d %s %s %s px=%d qty=%d filled=%d %s]",
            orderId, symbol, side, type, price, quantity, filledQty, status);
    }
}
