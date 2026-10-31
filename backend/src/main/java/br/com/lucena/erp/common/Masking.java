package br.com.lucena.erp.common;

/** Mascaramento de dados pessoais (LGPD) para exibição. */
public final class Masking {
    private Masking() {
    }

    /** 12345678901 -> ***.456.789-** */
    public static String cpf(String cpf) {
        if (cpf == null || cpf.length() != 11) return null;
        return "***." + cpf.substring(3, 6) + "." + cpf.substring(6, 9) + "-**";
    }

    public static String email(String email) {
        if (email == null || !email.contains("@")) return email;
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }
}
