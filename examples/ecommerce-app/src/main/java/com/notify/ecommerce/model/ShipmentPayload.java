package com.notify.ecommerce.model;

import com.notify.agent.annotations.Model;
import com.notify.agent.annotations.Vocabulary;

@Model(description = "Payload for shipment tracking events")
public class ShipmentPayload {

    @Vocabulary(name = "orderId", description = "Order being shipped")
    private String orderId;

    @Vocabulary(name = "trackingNumber", description = "Carrier tracking number")
    private String trackingNumber;

    @Vocabulary(name = "carrier", description = "Shipping carrier name")
    private String carrier;

    @Vocabulary(name = "estimatedDelivery", description = "Estimated delivery date (YYYY-MM-DD)")
    private String estimatedDelivery;

    public ShipmentPayload() {}

    public ShipmentPayload(String orderId, String trackingNumber, String carrier, String estimatedDelivery) {
        this.orderId = orderId;
        this.trackingNumber = trackingNumber;
        this.carrier = carrier;
        this.estimatedDelivery = estimatedDelivery;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getEstimatedDelivery() { return estimatedDelivery; }
    public void setEstimatedDelivery(String estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
}
