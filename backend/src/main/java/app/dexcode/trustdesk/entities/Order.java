package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.enums.OrderStatus;
import app.dexcode.trustdesk.persistence.JsonConverters;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Order {

    @Id
    @Column(name = "order_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String orderId;

    @Column(name = "customer_id")
    private String customerId;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "placed_at")
    private LocalDate placedAt;

    @Column(name = "delivered_at")
    private LocalDate deliveredAt;

    @Column(name = "eligible_return_until")
    private LocalDate eligibleReturnUntil;

    private BigDecimal total;
    private String currency;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "tracking_number")
    private String trackingNumber;

    @Convert(converter = JsonConverters.ObjectListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<Map<String, Object>> items;

    @Builder
    public Order(String orderId, String customerId, String status, LocalDate placedAt,
                 LocalDate deliveredAt, LocalDate eligibleReturnUntil, BigDecimal total,
                 String currency, String paymentStatus, String trackingNumber,
                 List<Map<String, Object>> items) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.fromValue(status);
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

    public String getStatus() { return status == null ? null : status.name(); }
    public void setStatus(String status) { this.status = OrderStatus.fromValue(status); }
    public void setStatus(OrderStatus status) { this.status = status; }
}
