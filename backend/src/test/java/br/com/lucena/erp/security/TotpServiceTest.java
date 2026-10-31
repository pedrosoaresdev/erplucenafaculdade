package br.com.lucena.erp.security;

import br.com.lucena.erp.config.ErpProperties;
import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {
    /** Segredo do apêndice B da RFC 6238 (SHA-1). */
    static final String RFC_SECRET = new Base32().encodeToString("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    static ErpProperties props() {
        return new ErpProperties(new ErpProperties.Security("x", "x", null, null, null, null, "ERP Teste",
                false, 5, null, 5, 30, 10, false), null, null, null);
    }

    static TotpService at(long epochSeconds) {
        return new TotpService(props(), Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    @Test
    void matchesRfc6238TestVectors() {
        // Valores de 8 dígitos da RFC truncados para 6 (mod 10^6)
        assertThat(at(59).codeAt(RFC_SECRET, 59 / 30)).isEqualTo("287082");
        assertThat(at(0).codeAt(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(at(0).codeAt(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(at(0).codeAt(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void acceptsCurrentAndAdjacentStepsOnly() {
        TotpService t = at(1_700_000_000L);
        long now = t.currentStep();
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now), null)).hasValue(now);
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now - 1), null)).hasValue(now - 1);
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now + 1), null)).hasValue(now + 1);
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now - 3), null)).isEmpty();
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now + 3), null)).isEmpty();
    }

    @Test
    void rejectsReuseOfSameOrOlderStep() {
        TotpService t = at(1_700_000_000L);
        long now = t.currentStep();
        String code = t.codeAt(RFC_SECRET, now);
        assertThat(t.verify(RFC_SECRET, code, now)).isEmpty();
        assertThat(t.verify(RFC_SECRET, t.codeAt(RFC_SECRET, now - 1), now - 1)).isEmpty();
    }

    @Test
    void rejectsMalformedCodes() {
        TotpService t = at(1_700_000_000L);
        assertThat(t.verify(RFC_SECRET, null, null)).isEmpty();
        assertThat(t.verify(RFC_SECRET, "12345", null)).isEmpty();
        assertThat(t.verify(RFC_SECRET, "abcdef", null)).isEmpty();
    }

    @Test
    void generatesDistinct160BitSecretsAndValidUri() {
        TotpService t = at(0);
        String a = t.generateSecret();
        String b = t.generateSecret();
        assertThat(a).isNotEqualTo(b).hasSize(32);
        assertThat(t.otpauthUri("aluno - fac", a)).startsWith("otpauth://totp/ERP%20Teste:aluno%20-%20fac?secret=" + a)
                .contains("issuer=ERP%20Teste").contains("digits=6").contains("period=30");
        assertThat(t.qrCodeDataUrl("otpauth://x")).startsWith("data:image/png;base64,");
        assertThat(TotpService.formatForDisplay("ABCDEFGH")).isEqualTo("ABCD EFGH");
    }
}
