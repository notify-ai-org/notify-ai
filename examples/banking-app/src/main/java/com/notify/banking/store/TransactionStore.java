package com.notify.banking.store;

import com.notify.banking.model.TransactionPayload;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory transaction store. Tracks recent transactions for velocity checks.
 */
@Component
public class TransactionStore {

    private final Map<String, TransactionPayload> transactions = new ConcurrentHashMap<>();

    public void save(TransactionPayload tx) {
        transactions.put(tx.getTransactionId(), tx);
    }

    public TransactionPayload get(String transactionId) {
        return transactions.get(transactionId);
    }

    /**
     * Count recent transactions from a given account (simple count for velocity rule).
     */
    public long countByFromAccount(String fromAccountId) {
        return transactions.values().stream()
                .filter(tx -> fromAccountId.equals(tx.getFromAccountId()))
                .count();
    }
}
