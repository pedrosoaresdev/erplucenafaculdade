package br.com.lucena.erp.notification;

import java.util.UUID;

/** Canal de push (ex.: Firebase Cloud Messaging). */
public interface PushGateway {
    void send(UUID userId, String title, String body);
}
