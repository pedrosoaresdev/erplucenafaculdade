package br.com.lucena.erp.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Notificações persistidas no PostgreSQL (fonte oficial). O envio push via Firebase Cloud Messaging
 * é um canal adicional, plugável por {@link PushGateway}.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repo;
    private final PushGateway push;

    @Transactional
    public Notification notify(UUID userId, UUID facultyId, String category, String title, String body) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setFacultyId(facultyId);
        n.setCategory(category);
        n.setTitle(title);
        n.setBody(body);
        repo.save(n);
        push.send(userId, title, body);
        return n;
    }
}
