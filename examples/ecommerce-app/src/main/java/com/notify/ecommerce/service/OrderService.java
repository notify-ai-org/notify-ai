package com.notify.ecommerce.service;

import com.notify.agent.annotations.*;
import com.notify.agent.models.subject.EmailSubject;
import com.notify.agent.models.subject.SmsSubject;
import com.notify.agent.models.subject.Subject;
import com.notify.ecommerce.model.*;
import com.notify.ecommerce.store.CartStore;
import com.notify.ecommerce.store.CustomerStore;
import com.notify.ecommerce.store.OrderStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core e-commerce service that exercises all Notify SDK annotations.
 *
 * Events:
 *   - ORDER_PLACED      (immediate email, fraud + inventory rules)
 *   - PAYMENT_FAILED    (high-priority SMS)
 *   - ORDER_SHIPPED     (immediate email)
 *   - ABANDONED_CART     (deferred/scheduled email)
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final CustomerStore customers;
    private final OrderStore orders;
    private final CartStore carts;

    public OrderService(CustomerStore customers, OrderStore orders, CartStore carts) {
        this.customers = customers;
        this.orders = orders;
        this.carts = carts;
    }

    // ═══════════════════════════════════════════
    // EVENTS
    // ═══════════════════════════════════════════

    @Event(
        key = "ORDER_PLACED",
        description = "Customer placed an order",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "09:00-18:00",
        priority = 5,
        payload = OrderPayload.class
    )
    public OrderPayload placeOrder(OrderPayload payload) {
        log.info("📦 Order placed: {} for customer {} — ${}", payload.getOrderId(), payload.getCustomerId(), payload.getAmount());
        orders.save(payload);
        return payload;
    }

    @Event(
        key = "PAYMENT_FAILED",
        description = "Payment processing failed for an order",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "00:00-23:59",
        priority = 9,
        payload = OrderPayload.class
    )
    public OrderPayload reportPaymentFailed(OrderPayload payload) {
        log.warn("💳 Payment FAILED for order: {} — ${}", payload.getOrderId(), payload.getAmount());
        return payload;
    }

    @Event(
        key = "ORDER_SHIPPED",
        description = "Order has been shipped to the customer",
        eventType = "static",
        scheduleIntent = "immediate",
        preferredTimeWindow = "09:00-18:00",
        priority = 5,
        payload = ShipmentPayload.class
    )
    public ShipmentPayload shipOrder(ShipmentPayload payload) {
        log.info("Order shipped: {} via {} — tracking: {}", payload.getOrderId(), payload.getCarrier(), payload.getTrackingNumber());
        return payload;
    }

    @Event(
        key = "ABANDONED_CART",
        description = "Customer abandoned their shopping cart",
        eventType = "deferred",
        scheduleIntent = "deferred",
        preferredTimeWindow = "09:00-21:00",
        priority = 3,
        payload = CartPayload.class
    )
    public CartPayload abandonCart(CartPayload payload) {
        log.info("🛒 Cart abandoned: {} by customer {}", payload.getCartId(), payload.getCustomerId());
        carts.save(payload);
        return payload;
    }

    // ═══════════════════════════════════════════
    // SUBJECT SUPPLIERS
    // ═══════════════════════════════════════════

    @SubjectSupplier(event = "ORDER_PLACED", description = "Resolves order customer to email recipients")
    public List<Subject> getOrderPlacedSubjects(OrderPayload payload) {
        Customer c = customers.get(payload.getCustomerId());
        if (c == null) {
            log.warn("Customer not found: {}", payload.getCustomerId());
            return List.of();
        }
        return List.of(new EmailSubject(
            c.getId(), c.getEmail(), null, null,
            null, Map.of("firstName", c.getName())
        ));
    }

    @SubjectSupplier(event = "PAYMENT_FAILED", description = "Resolves customer to SMS for urgent payment alerts")
    public List<Subject> getPaymentFailedSubjects(OrderPayload payload) {
        Customer c = customers.get(payload.getCustomerId());
        if (c == null) return List.of();
        return List.of(new SmsSubject(
            c.getId(), c.getPhone(), null,
            Map.of("firstName", c.getName())
        ));
    }

    @SubjectSupplier(event = "ORDER_SHIPPED", description = "Resolves order to email recipients for shipment tracking")
    public List<Subject> getShipmentSubjects(ShipmentPayload payload) {
        // Look up the order to get the customer
        OrderPayload order = orders.get(payload.getOrderId());
        if (order == null) {
            log.warn("Order not found for shipment: {}", payload.getOrderId());
            return List.of();
        }
        Customer c = customers.get(order.getCustomerId());
        if (c == null) return List.of();
        return List.of(new EmailSubject(
            c.getId(), c.getEmail(), null, null,
            null, Map.of("firstName", c.getName())
        ));
    }

    @SubjectSupplier(event = "ABANDONED_CART", description = "Resolves cart owner to email for re-engagement")
    public List<Subject> getCartSubjects(CartPayload payload) {
        Customer c = customers.get(payload.getCustomerId());
        if (c == null) return List.of();
        return List.of(new EmailSubject(
            c.getId(), c.getEmail(), null, null,
            null, Map.of("firstName", c.getName())
        ));
    }

    // ═══════════════════════════════════════════
    // VOCABULARY SUPPLIER
    // ═══════════════════════════════════════════

    @VocabularySupplier(event = "ORDER_PLACED", description = "Enriches order payload with customer name and item count")
    public OrderPayload orderPlacedVocabulary(OrderPayload payload) {
        Customer c = customers.get(payload.getCustomerId());
        if (c != null) {
            // Enrich by adding shipping address if missing
            if (payload.getShippingAddress() == null || payload.getShippingAddress().isEmpty()) {
                payload.setShippingAddress("Default address for " + c.getName());
            }
        }
        return payload;
    }

    // ═══════════════════════════════════════════
    // RULES
    // ═══════════════════════════════════════════

    @Rule(name = "fraud-check", event = "ORDER_PLACED", description = "Blocks orders over $1000 as potential fraud")
    public boolean fraudCheck(OrderPayload payload) {
        boolean passed = payload.getAmount() < 1000.0;
        log.info("🔍 Fraud check for order {}: {} (amount=${})", payload.getOrderId(), passed ? "PASSED" : "BLOCKED", payload.getAmount());
        return passed;
    }

    @Rule(name = "inventory-check", event = "ORDER_PLACED", description = "Checks whether all items are in stock")
    public boolean inventoryCheck(OrderPayload payload) {
        // In-memory: always in stock
        log.info("📦 Inventory check for order {}: all {} items in stock", payload.getOrderId(), payload.getItems() != null ? payload.getItems().size() : 0);
        return true;
    }

    // ═══════════════════════════════════════════
    // CALLBACKS
    // ═══════════════════════════════════════════

    @Callback(event = "ORDER_PLACED", when = Callback.When.BEFORE)
    public void beforeOrderPlaced(OrderPayload payload) {
        log.info("⏳ [BEFORE] About to process ORDER_PLACED for order: {}", payload.getOrderId());
    }

    @Callback(event = "ORDER_PLACED", when = Callback.When.AFTER)
    public void afterOrderPlaced(OrderPayload payload) {
        log.info("✅ [AFTER] ORDER_PLACED processing complete for order: {}", payload.getOrderId());
    }
}
