package com.example.ecommerce.store;

import com.example.ecommerce.model.Customer;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory customer store seeded with dummy data.
 */
@Component
public class CustomerStore {

    private final Map<String, Customer> customers = new ConcurrentHashMap<>();

    public CustomerStore() {
        customers.put("CUST-1", new Customer("CUST-1", "Alice Johnson", "alice@example.com", "+1-555-0101"));
        customers.put("CUST-2", new Customer("CUST-2", "Bob Smith", "bob@example.com", "+1-555-0102"));
        customers.put("CUST-3", new Customer("CUST-3", "Carol Davis", "carol@example.com", "+1-555-0103"));
    }

    public Customer get(String customerId) {
        return customers.get(customerId);
    }

    public Collection<Customer> getAll() {
        return customers.values();
    }

    public void put(String id, Customer customer) {
        customers.put(id, customer);
    }
}
