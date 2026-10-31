package br.com.lucena.erp.security;

import br.com.lucena.erp.common.ApiException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {
    final PasswordPolicy policy = new PasswordPolicy(TotpServiceTest.props());

    private void rejects(String pwd, String confirm, String code) {
        assertThatThrownBy(() -> policy.validate(pwd, confirm, "joao.silva"))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void enforcesRules() {
        rejects("", "", "PASSWORD_EMPTY");
        rejects("   ", "   ", "PASSWORD_EMPTY");
        rejects("Abc!2345x", "Abc!2345y", "PASSWORD_MISMATCH");
        rejects("Ab!3456", "Ab!3456", "PASSWORD_TOO_SHORT");
        rejects("password", "password", "PASSWORD_TOO_COMMON");
        rejects("12345678", "12345678", "PASSWORD_TOO_COMMON");
        rejects("senha123", "senha123", "PASSWORD_TOO_COMMON");
        rejects("aaaaaaaaaa", "aaaaaaaaaa", "PASSWORD_TOO_COMMON");
        rejects("xJoao.Silva9!", "xJoao.Silva9!", "PASSWORD_CONTAINS_USERNAME");
        rejects("A".repeat(60) + "b!1" + "C".repeat(70), "A".repeat(60) + "b!1" + "C".repeat(70), "PASSWORD_TOO_LONG");
    }

    @Test
    void acceptsReasonablePassword() {
        assertThatCode(() -> policy.validate("Campus#Norte2026", "Campus#Norte2026", "joao.silva")).doesNotThrowAnyException();
    }
}
