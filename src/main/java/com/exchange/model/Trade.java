package com.exchange.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A Trade records a single execution (match) between a resting order
 * (maker) and an aggressing order (taker).
 *
 * Interview talking point: maker vs taker distinction matters for fee
 * schedules on real exchanges — makers add liquidity, takers remove it.
 */
public class Trade {

    private static final AtomicLong TRADE_SEQ = new AtomicLong(1);

    private final long    tradeId;
    private final String  symbol;
    private final long    makerOrderId;   // resting order in the book
    private final long    takerOrderId;   // incoming aggressive order
    private final long    price;          // execution price (maker's limit price)
    private final long    quantity;       // matched quantity
    private final Instant executedAt;

    public Trade(String symbol, long makerOrderId, long takerOrderId,
                 long price, long quantity) {
        this.tradeId      = TRADE_SEQ.getAndIncrement();
        this.symbol       = symbol;
        this.makerOrderId = makerOrderId;
        this.takerOrderId = takerOrderId;
        this.price        = price;
        this.quantity     = quantity;
        this.executedAt   = Instant.now();
    }

    // ---------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------
    public long    getTradeId()      { return tradeId; }
    public String  getSymbol()       { return symbol; }
    public long    getMakerOrderId() { return makerOrderId; }
    public long    getTakerOrderId() { return takerOrderId; }
    public long    getPrice()        { return price; }
    public long    getQuantity()     { return quantity; }
    public Instant getExecutedAt()   { return executedAt; }

    @Override
    public String toString() {
        return String.format(
            "Trade[id=%d %s maker=%d taker=%d px=%d qty=%d]",
            tradeId, symbol, makerOrderId, takerOrderId, price, quantity);
    }
}
