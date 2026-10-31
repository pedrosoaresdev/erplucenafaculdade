package br.com.lucena.erp.security;

import br.com.lucena.erp.common.ApiException;
import br.com.lucena.erp.config.ErpProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Política de senhas: mínimo 8 / máximo 128 caracteres, rejeita senhas comuns (lista local) e,
 * opcionalmente, senhas vazadas via HIBP (k-anonymity: somente os 5 primeiros caracteres do SHA-1 saem do servidor).
 */
@Slf4j
@Component
public class PasswordPolicy {
    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private final Set<String> common;
    private final boolean breachedCheck;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public PasswordPolicy(ErpProperties props) {
        this.breachedCheck = props.security().breachedPasswordCheck();
        try (var reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("security/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            this.common = reader.lines().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toCollection(HashSet::new));
        } catch (IOException e) {
            throw new IllegalStateException("Lista de senhas comuns indisponível", e);
        }
    }

    /** Valida nova senha; lança ApiException com mensagem amigável quando rejeitada. */
    public void validate(String password, String confirmation, String username) {
        if (password == null || password.isBlank()) {
            throw ApiException.badRequest("PASSWORD_EMPTY", "A senha não pode ser vazia");
        }
        if (confirmation != null && !password.equals(confirmation)) {
            throw ApiException.badRequest("PASSWORD_MISMATCH", "As senhas não coincidem");
        }
        if (password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("PASSWORD_TOO_SHORT", "A senha deve ter no mínimo " + MIN_LENGTH + " caracteres");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("PASSWORD_TOO_LONG", "A senha deve ter no máximo " + MAX_LENGTH + " caracteres");
        }
        String lower = password.toLowerCase();
        if (common.contains(lower) || lower.chars().distinct().count() <= 2) {
            throw ApiException.badRequest("PASSWORD_TOO_COMMON", "Senha muito comum ou previsível. Escolha outra");
        }
        if (username != null && username.length() >= 3 && lower.contains(username.toLowerCase())) {
            throw ApiException.badRequest("PASSWORD_CONTAINS_USERNAME", "A senha não pode conter o nome de usuário/matrícula");
        }
        if (breachedCheck && isBreached(password)) {
            throw ApiException.badRequest("PASSWORD_BREACHED", "Esta senha apareceu em vazamentos de dados conhecidos. Escolha outra");
        }
    }

    boolean isBreached(String password) {
        try {
            String sha1 = HexFormat.of().withUpperCase().formatHex(
                    MessageDigest.getInstance("SHA-1").digest(password.getBytes(StandardCharsets.UTF_8)));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.pwnedpasswords.com/range/" + sha1.substring(0, 5)))
                    .timeout(Duration.ofSeconds(4)).header("Add-Padding", "true").GET().build();
            String body = http.send(req, HttpResponse.BodyHandlers.ofString()).body();
            String suffix = sha1.substring(5);
            return body.lines().anyMatch(l -> l.startsWith(suffix) && !l.endsWith(":0"));
        } catch (Exception e) {
            // Falha da integração não bloqueia a troca; as demais regras continuam valendo.
            log.warn("Verificação de senha vazada indisponível: {}", e.getClass().getSimpleName());
            return false;
        }
    }

}
