package com.notify.ecommerce.controller;

import com.notify.ecommerce.model.CartPayload;
import com.notify.ecommerce.model.OrderPayload;
import com.notify.ecommerce.model.ShipmentPayload;
import com.notify.ecommerce.service.OrderService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller exposing endpoints to trigger e-commerce events for testing.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/place")
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody OrderPayload payload) {
        OrderPayload result = orderService.placeOrder(payload);
        return ResponseEntity.ok(Map.of(
                "status", "ORDER_PLACED",
                "orderId", result.getOrderId(),
                "amount", result.getAmount()));
    }

    @PostMapping("/payment-failed")
    public ResponseEntity<Map<String, Object>> paymentFailed(@RequestBody OrderPayload payload) {
        orderService.reportPaymentFailed(payload);
        return ResponseEntity.ok(Map.of(
                "status", "PAYMENT_FAILED",
                "orderId", payload.getOrderId()));
    }

    @PostMapping("/ship")
    public ResponseEntity<Map<String, Object>> shipOrder(@RequestBody ShipmentPayload payload) {
        orderService.shipOrder(payload);
        return ResponseEntity.ok(Map.of(
                "status", "ORDER_SHIPPED",
                "orderId", payload.getOrderId(),
                "trackingNumber", payload.getTrackingNumber()));
    }

    @PostMapping("/abandon-cart")
    public ResponseEntity<Map<String, Object>> abandonCart(@RequestBody CartPayload payload) {
        orderService.abandonCart(payload);
        return ResponseEntity.ok(Map.of(
                "status", "ABANDONED_CART",
                "cartId", payload.getCartId()));
    }
}
