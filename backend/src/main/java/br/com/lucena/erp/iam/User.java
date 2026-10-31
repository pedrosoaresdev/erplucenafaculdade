package br.com.lucena.erp.iam;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    public enum Status { ACTIVE, INACTIVE, BLOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    /** null somente para administradores da plataforma (Master). */
    private UUID facultyId;
    private String username;
    private String fullName;
    private String socialName;
    private String email;
    private String phone;
    private String photoUrl;
    private String passwordHash;
    private boolean platformAdmin;
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    private boolean mustChangePassword;
    private boolean mfaEnabled;
    private int failedLoginCount;
    private Instant lockedUntil;
    private Instant passwordChangedAt;
    private Instant lastLoginAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public String displayName() {
        return socialName != null && !socialName.isBlank() ? socialName : fullName;
    }
}
