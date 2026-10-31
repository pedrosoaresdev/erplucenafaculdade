package br.com.lucena.erp.common;

/** Validação de dígitos verificadores de CPF e CNPJ. */
public final class DocumentValidator {
    private DocumentValidator() {
    }

    public static String digits(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    public static boolean isValidCpf(String raw) {
        String c = digits(raw);
        if (c == null || c.length() != 11 || c.chars().distinct().count() == 1) return false;
        for (int t = 9; t < 11; t++) {
            int sum = 0;
            for (int i = 0; i < t; i++) sum += (c.charAt(i) - '0') * (t + 1 - i);
            int d = (sum * 10) % 11 % 10;
            if (c.charAt(t) - '0' != d) return false;
        }
        return true;
    }

    public static boolean isValidCnpj(String raw) {
        String c = digits(raw);
        if (c == null || c.length() != 14 || c.chars().distinct().count() == 1) return false;
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return check(c, w1, 12) && check(c, w2, 13);
    }

    private static boolean check(String c, int[] w, int pos) {
        int sum = 0;
        for (int i = 0; i < w.length; i++) sum += (c.charAt(i) - '0') * w[i];
        int r = sum % 11;
        int d = r < 2 ? 0 : 11 - r;
        return c.charAt(pos) - '0' == d;
    }
}
