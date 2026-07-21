package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "customer_id")
    private String customerId;

    private String status;

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "eligible_return_until")
    private Instant eligibleReturnUntil;

    private BigDecimal total;
    private String currency;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> items;

    public Order() {}

    public Order(String orderId, String customerId, String status, Instant placedAt,
                 Instant deliveredAt, Instant eligibleReturnUntil, BigDecimal total,
                 String currency, String paymentStatus, String trackingNumber,
                 List<Map<String, Object>> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.placedAt = placedAt;
        this.deliveredAt = deliveredAt;
        this.eligibleReturnUntil = eligibleReturnUntil;
        this.total = total;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.trackingNumber = trackingNumber;
        this.items = items;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getPlacedAt() { return placedAt; }
    public void setPlacedAt(Instant placedAt) { this.placedAt = placedAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getEligibleReturnUntil() { return eligibleReturnUntil; }
    public void setEligibleReturnUntil(Instant eligibleReturnUntil) { this.eligibleReturnUntil = eligibleReturnUntil; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public List<Map<String, Object>> getItems() { return items; }
    public void setItems(List<Map<String, Object>> items) { this.items = items; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order order = (Order) o;
        return Objects.equals(orderId, order.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", status='" + status + '\'' +
                ", placedAt=" + placedAt +
                ", deliveredAt=" + deliveredAt +
                ", eligibleReturnUntil=" + eligibleReturnUntil +
                ", total=" + total +
                ", currency='" + currency + '\'' +
                ", paymentStatus='" + paymentStatus + '\'' +
                ", trackingNumber='" + trackingNumber + '\'' +
                ", items=" + items +
                '}';
    }

    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    public static class OrderBuilder {
        private String orderId;
        private String customerId;
        private String status;
        private Instant placedAt;
        private Instant deliveredAt;
        private Instant eligibleReturnUntil;
        private BigDecimal total;
        private String currency;
        private String paymentStatus;
        private String trackingNumber;
        private List<Map<String, Object>> items;

        public OrderBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public OrderBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public OrderBuilder status(String status) { this.status = status; return this; }
        public OrderBuilder placedAt(Instant placedAt) { this.placedAt = placedAt; return this; }
        public OrderBuilder deliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; return this; }
        public OrderBuilder eligibleReturnUntil(Instant eligibleReturnUntil) { this.eligibleReturnUntil = eligibleReturnUntil; return this; }
        public OrderBuilder total(BigDecimal total) { this.total = total; return this; }
        public OrderBuilder currency(String currency) { this.currency = currency; return this; }
        public OrderBuilder paymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; return this; }
        public OrderBuilder trackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; return this; }
        public OrderBuilder items(List<Map<String, Object>> items) { this.items = items; return this; }

        public Order build() {
            return new Order(orderId, customerId, status, placedAt, deliveredAt, eligibleReturnUntil, total, currency, paymentStatus, trackingNumber, items);
        }
    }
}
