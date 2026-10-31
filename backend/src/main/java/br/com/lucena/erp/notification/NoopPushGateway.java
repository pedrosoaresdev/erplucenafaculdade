package br.com.lucena.erp.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Implementação padrão enquanto o Firebase Cloud Messaging não estiver configurado:
 * a notificação continua disponível na central de notificações do ERP (PostgreSQL).
 */
@Slf4j
@Component
public class NoopPushGateway implements PushGateway {
    @Override
    public void send(UUID userId, String title, String body) {
        log.debug("Push não configurado (FCM); notificação {} mantida somente no ERP", title);
    }
}
