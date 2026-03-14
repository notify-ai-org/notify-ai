package com.example.ecommerce.model;

import com.example.agent.annotations.Model;
import com.example.agent.annotations.Vocabulary;

import java.util.List;

@Model(description = "Payload for abandoned cart events")
public class CartPayload {

    @Vocabulary(name = "cartId", description = "Unique cart identifier")
    private String cartId;

    @Vocabulary(name = "customerId", description = "Customer who owns the cart")
    private String customerId;

    @Vocabulary(name = "items", description = "Items left in the cart")
    private List<String> items;

    @Vocabulary(name = "lastActivityAt", description = "Timestamp of last cart activity (ISO-8601)")
    private String lastActivityAt;

    public CartPayload() {}

    public CartPayload(String cartId, String customerId, List<String> items, String lastActivityAt) {
        this.cartId = cartId;
        this.customerId = customerId;
        this.items = items;
        this.lastActivityAt = lastActivityAt;
    }

    public String getCartId() { return cartId; }
    public void setCartId(String cartId) { this.cartId = cartId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    public String getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(String lastActivityAt) { this.lastActivityAt = lastActivityAt; }
}
