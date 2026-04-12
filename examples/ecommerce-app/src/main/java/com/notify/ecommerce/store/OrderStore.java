package com.notify.ecommerce.store;

import com.notify.ecommerce.model.OrderPayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory order store.
 */
@Component
public class OrderStore {

    private final Map<String, OrderPayload> orders = new ConcurrentHashMap<>();

    public void save(OrderPayload order) {
        orders.put(order.getOrderId(), order);
    }

    public OrderPayload get(String orderId) {
        return orders.get(orderId);
    }

    public int count() {
        return orders.size();
    }
}
