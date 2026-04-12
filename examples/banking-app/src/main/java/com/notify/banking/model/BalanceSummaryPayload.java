package com.notify.banking.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

@Model(description = "Payload for daily balance summary digest events")
public class BalanceSummaryPayload {

    @Vocabulary(name = "accountId", description = "Account for the balance summary")
    private String accountId;

    @Vocabulary(name = "balance", description = "Current account balance")
    private double balance;

    @Vocabulary(name = "currency", description = "Currency code")
    private String currency;

    @Vocabulary(name = "statementDate", description = "Statement date (YYYY-MM-DD)")
    private String statementDate;

    public BalanceSummaryPayload() {}

    public BalanceSummaryPayload(String accountId, double balance, String currency, String statementDate) {
        this.accountId = accountId;
        this.balance = balance;
        this.currency = currency;
        this.statementDate = statementDate;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatementDate() { return statementDate; }
    public void setStatementDate(String statementDate) { this.statementDate = statementDate; }
}
