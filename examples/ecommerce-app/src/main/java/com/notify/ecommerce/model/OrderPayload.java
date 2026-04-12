package com.notify.ecommerce.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

import java.util.List;

@Model(description = "Payload for order placement and payment events")
public class OrderPayload {

    @Vocabulary(name = "orderId", description = "Unique order identifier")
    private String orderId;

    @Vocabulary(name = "customerId", description = "Customer who placed the order")
    private String customerId;

    @Vocabulary(name = "amount", description = "Total order amount in USD")
    private double amount;

    @Vocabulary(name = "items", description = "List of item names in the order")
    private List<String> items;

    @Vocabulary(name = "shippingAddress", description = "Delivery address for the order")
    private String shippingAddress;

    public OrderPayload() {}

    public OrderPayload(String orderId, String customerId, double amount, List<String> items, String shippingAddress) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.items = items;
        this.shippingAddress = shippingAddress;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
}
