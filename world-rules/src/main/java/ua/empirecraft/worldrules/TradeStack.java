package ua.empirecraft.worldrules;

/** Ephemeral origin flag: never serialized into player items or sent to clients. */
public interface TradeStack {
    boolean empirecraft$retiredTradeItem();
    void empirecraft$markRetiredTradeItem();
}
