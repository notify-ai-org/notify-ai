package com.notify.banking.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

@Model(description = "Payload for fund transfer events")
public class TransactionPayload {

    @Vocabulary(name = "transactionId", description = "Unique transaction identifier")
    private String transactionId;

    @Vocabulary(name = "fromAccountId", description = "Source account for the transfer")
    private String fromAccountId;

    @Vocabulary(name = "toAccountId", description = "Destination account for the transfer")
    private String toAccountId;

    @Vocabulary(name = "amount", description = "Transfer amount")
    private double amount;

    @Vocabulary(name = "currency", description = "Currency code (e.g. USD, EUR)")
    private String currency;

    @Vocabulary(name = "type", description = "Transaction type (WIRE, ACH, INTERNAL)")
    private String type;

    public TransactionPayload() {}

    public TransactionPayload(String transactionId, String fromAccountId, String toAccountId, double amount, String currency, String type) {
        this.transactionId = transactionId;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(String fromAccountId) { this.fromAccountId = fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public void setToAccountId(String toAccountId) { this.toAccountId = toAccountId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
