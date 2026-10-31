package br.com.lucena.erp.platform;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plans")
@Getter
@Setter
public class Plan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String code;
    private String name;
    private Integer maxStudents;
    private Integer maxUsers;
    private BigDecimal monthlyPrice;
    private boolean active = true;
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
