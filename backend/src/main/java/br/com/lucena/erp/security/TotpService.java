package br.com.lucena.erp.security;

import br.com.lucena.erp.config.ErpProperties;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.OptionalLong;

/**
 * TOTP RFC 6238 (HMAC-SHA1, 6 dígitos, passo de 30 s) — compatível com Google Authenticator.
 * A verificação aceita ±1 passo de tolerância de relógio e devolve o passo aceito, para que o
 * chamador rejeite códigos já utilizados (anti-replay).
 */
@Service
public class TotpService {
    public static final int DIGITS = 6;
    public static final int PERIOD_SECONDS = 30;
    public static final int ALLOWED_DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final String issuer;

    public TotpService(ErpProperties props, Clock clock) {
        this.issuer = props.security().totpIssuer();
        this.clock = clock;
    }

    /** Segredo de 160 bits em Base32 (recomendação RFC 4226). */
    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return new Base32().encodeToString(bytes).replace("=", "");
    }

    public long currentStep() {
        return clock.millis() / 1000 / PERIOD_SECONDS;
    }

    public String codeAt(String base32Secret, long step) {
        byte[] key = new Base32().decode(base32Secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Verifica o código e retorna o passo de tempo correspondente.
     * Códigos de passos menores ou iguais a {@code lastUsedStep} são rejeitados (reutilização).
     */
    public OptionalLong verify(String base32Secret, String code, Long lastUsedStep) {
        if (code == null) return OptionalLong.empty();
        String normalized = code.replaceAll("\\s", "");
        if (!normalized.matches("\\d{" + DIGITS + "}")) return OptionalLong.empty();
        long now = currentStep();
        for (long step = now - ALLOWED_DRIFT_STEPS; step <= now + ALLOWED_DRIFT_STEPS; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) continue;
            if (MessageDigest.isEqual(codeAt(base32Secret, step).getBytes(StandardCharsets.US_ASCII),
                    normalized.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public String otpauthUri(String accountLabel, String base32Secret) {
        String label = enc(issuer) + ":" + enc(accountLabel);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** QR Code PNG em data URL, gerado localmente (o segredo não trafega para serviços de terceiros). */
    public String qrCodeDataUrl(String content) {
        try {
            var matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 260, 260,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Falha ao gerar QR Code", e);
        }
    }

    /** Agrupa a chave em blocos de 4 para facilitar a digitação manual. */
    public static String formatForDisplay(String secret) {
        return secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
