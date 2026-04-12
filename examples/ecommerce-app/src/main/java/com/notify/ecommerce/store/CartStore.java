package com.notify.ecommerce.store;

import com.notify.ecommerce.model.CartPayload;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cart store.
 */
@Component
public class CartStore {

    private final Map<String, CartPayload> carts = new ConcurrentHashMap<>();

    public void save(CartPayload cart) {
        carts.put(cart.getCartId(), cart);
    }

    public CartPayload get(String cartId) {
        return carts.get(cartId);
    }

    public void remove(String cartId) {
        carts.remove(cartId);
    }
}
