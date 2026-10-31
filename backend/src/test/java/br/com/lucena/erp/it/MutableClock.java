package br.com.lucena.erp.it;

import java.time.*;
import java.util.concurrent.atomic.AtomicReference;

/** Relógio controlável: permite avançar o tempo para gerar novos códigos TOTP e testar expirações. */
public class MutableClock extends Clock {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    public void advance(Duration d) {
        now.updateAndGet(i -> i.plus(d));
    }
}
