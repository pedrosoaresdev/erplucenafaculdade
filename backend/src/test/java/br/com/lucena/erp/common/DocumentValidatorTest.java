package br.com.lucena.erp.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentValidatorTest {
    @Test
    void cnpj() {
        assertThat(DocumentValidator.isValidCnpj("11.222.333/0001-81")).isTrue();
        assertThat(DocumentValidator.isValidCnpj("11222333000182")).isFalse();
        assertThat(DocumentValidator.isValidCnpj("00000000000000")).isFalse();
        assertThat(DocumentValidator.isValidCnpj("123")).isFalse();
    }

    @Test
    void cpf() {
        assertThat(DocumentValidator.isValidCpf("529.982.247-25")).isTrue();
        assertThat(DocumentValidator.isValidCpf("52998224726")).isFalse();
        assertThat(DocumentValidator.isValidCpf("11111111111")).isFalse();
    }

    @Test
    void masking() {
        assertThat(Masking.cpf("52998224725")).isEqualTo("***.982.247-**");
        assertThat(Masking.email("joao@x.com")).isEqualTo("jo***@x.com");
    }
}
