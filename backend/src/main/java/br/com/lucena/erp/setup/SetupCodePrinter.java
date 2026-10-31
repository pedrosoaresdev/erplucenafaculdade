package br.com.lucena.erp.setup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Ao iniciar sem nenhum administrador Master, gera e exibe no console o código de instalação de uso único. */
@Slf4j
@Component
@Order(100) // depois do MasterBootstrap (que pode criar um Master por variáveis de ambiente)
@RequiredArgsConstructor
public class SetupCodePrinter implements ApplicationRunner {
    private final SetupService setup;

    @Override
    public void run(ApplicationArguments args) {
        SetupService.Status st = setup.status();
        if (!st.ownerConfigured()) {
            log.info("ERP_OWNER_EMAIL não definido: instalação pelo navegador desativada.");
            return;
        }
        String code = setup.issueCode();
        if (code == null) return;
        log.warn("""

                ==================================================================
                  INSTALAÇÃO INICIAL DO ERP — nenhum administrador Master existe.
                  Abra o sistema no navegador e informe:
                    e-mail do dono:        (valor de ERP_OWNER_EMAIL)
                    código de instalação:  {}
                  O código vale 24 h, aceita 5 tentativas e muda a cada reinício.
                ==================================================================""", code);
    }
}
