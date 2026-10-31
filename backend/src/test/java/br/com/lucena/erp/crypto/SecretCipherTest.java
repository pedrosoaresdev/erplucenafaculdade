package br.com.lucena.erp.crypto;

import br.com.lucena.erp.config.ErpProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {
    static SecretCipher cipher(String key32) {
        String b64 = Base64.getEncoder().encodeToString(key32.getBytes());
        return new SecretCipher(new ErpProperties(new ErpProperties.Security("x", b64, null, null, null, null, "i",
                false, 5, null, 5, 30, 10, false), null, null));
    }

    @Test
    void roundTripWithRandomIv() {
        SecretCipher c = cipher("0123456789abcdef0123456789abcdef");
        String e1 = c.encrypt("JBSWY3DPEHPK3PXP");
        String e2 = c.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(e1).startsWith("v1:").isNotEqualTo(e2).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(c.decrypt(e1)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void detectsTamperingAndWrongKey() {
        SecretCipher c = cipher("0123456789abcdef0123456789abcdef");
        String e = c.encrypt("segredo");
        char[] chars = e.toCharArray();
        chars[10] = chars[10] == 'A' ? 'B' : 'A';
        assertThatThrownBy(() -> c.decrypt(new String(chars))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher("fedcba9876543210fedcba9876543210").decrypt(e)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsShortKey() {
        assertThatThrownBy(() -> cipher("curta")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void recoveryCodesAreNormalized() {
        String code = Tokens.recoveryCode();
        assertThat(code).matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        assertThat(Tokens.normalizeRecoveryCode(code.toLowerCase().replace("-", " "))).isEqualTo(code);
        assertThat(Tokens.sha256Hex("abc")).hasSize(64);
    }
}
