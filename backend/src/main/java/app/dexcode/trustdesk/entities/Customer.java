package app.dexcode.trustdesk.entities;

import app.dexcode.trustdesk.persistence.JsonConverters;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    private String name;
    private String email;
    private String tier;
    private String country;

    @Column(name = "created_at")
    private Instant createdAt;

    private boolean verified;

    @Convert(converter = JsonConverters.StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> tags;

    public Customer() {}

    public Customer(String customerId, String name, String email, String tier, String country,
                    Instant createdAt, boolean verified, List<String> tags) {
        this.customerId = customerId;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.country = country;
        this.createdAt = createdAt;
        this.verified = verified;
        this.tags = tags;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Customer customer = (Customer) o;
        return Objects.equals(customerId, customer.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId);
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId='" + customerId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", tier='" + tier + '\'' +
                ", country='" + country + '\'' +
                ", createdAt=" + createdAt +
                ", verified=" + verified +
                ", tags=" + tags +
                '}';
    }

    public static CustomerBuilder builder() {
        return new CustomerBuilder();
    }

    public static class CustomerBuilder {
        private String customerId;
        private String name;
        private String email;
        private String tier;
        private String country;
        private Instant createdAt;
        private boolean verified;
        private List<String> tags;

        public CustomerBuilder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public CustomerBuilder name(String name) {
            this.name = name;
            return this;
        }

        public CustomerBuilder email(String email) {
            this.email = email;
            return this;
        }

        public CustomerBuilder tier(String tier) {
            this.tier = tier;
            return this;
        }

        public CustomerBuilder country(String country) {
            this.country = country;
            return this;
        }

        public CustomerBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public CustomerBuilder verified(boolean verified) {
            this.verified = verified;
            return this;
        }

        public CustomerBuilder tags(List<String> tags) {
            this.tags = tags;
            return this;
        }

        public Customer build() {
            return new Customer(customerId, name, email, tier, country, createdAt, verified, tags);
        }
    }
}
