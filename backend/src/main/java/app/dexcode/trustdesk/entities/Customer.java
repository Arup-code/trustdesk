package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(onlyExplicitlyIncluded = true)
public class Customer {

    @Id
    @Column(name = "customer_id")
    @EqualsAndHashCode.Include
    @ToString.Include
    private String customerId;

    private String name;
    private String email;
    private String tier;
    private String country;

    @Column(name = "created_at")
    private LocalDate createdAt;

    private boolean verified;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags;
}
