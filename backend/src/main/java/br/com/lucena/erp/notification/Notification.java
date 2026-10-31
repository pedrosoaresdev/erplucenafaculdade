package br.com.lucena.erp.notification;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID facultyId;
    private UUID userId;
    private String category;
    private String title;
    private String body;
    private String link;
    private Instant readAt;
    private Instant createdAt = Instant.now();
}
