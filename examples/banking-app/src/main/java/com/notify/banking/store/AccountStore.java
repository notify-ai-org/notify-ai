package com.notify.banking.store;

import com.notify.banking.model.Account;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory account store seeded with dummy data.
 */
@Component
public class AccountStore {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountStore() {
        accounts.put("ACC-1", new Account("ACC-1", "David Park", "david.park@example.com", "+1-555-0201", 125000.50));
        accounts.put("ACC-2", new Account("ACC-2", "Emily Chen", "emily.chen@example.com", "+1-555-0202", 45000.00));
        accounts.put("ACC-3", new Account("ACC-3", "Frank Miller", "frank.miller@example.com", "+1-555-0203", 250000.75));
    }

    public Account get(String accountId) {
        return accounts.get(accountId);
    }

    public Collection<Account> getAll() {
        return accounts.values();
    }
}
