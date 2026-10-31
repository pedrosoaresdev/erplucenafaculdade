package br.com.lucena.erp.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Geração de tokens aleatórios e hash SHA-256 (tokens opacos nunca são guardados em claro). */
public final class Tokens {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private Tokens() {
    }

    public static String randomUrlToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Código de recuperação no formato XXXXX-XXXXX (alfabeto sem caracteres ambíguos, ~50 bits). */
    public static String recoveryCode() {
        StringBuilder sb = new StringBuilder(11);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(RECOVERY_ALPHABET[RANDOM.nextInt(RECOVERY_ALPHABET.length)]);
        }
        return sb.toString();
    }

    public static String normalizeRecoveryCode(String input) {
        if (input == null) return "";
        String c = input.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return c.length() == 10 ? c.substring(0, 5) + "-" + c.substring(5) : c;
    }

    /** Senha temporária forte para criação/redefinição de acesso. */
    public static String temporaryPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%&*";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        return sb.toString();
    }
}
