package br.com.lucena.erp.common;

import jakarta.servlet.http.HttpServletRequest;

/** Origem da requisição (IP/agente) para logs de acesso e auditoria. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo from(HttpServletRequest request) {
        // X-Forwarded-For só é confiável atrás de proxy reverso configurado
        // (server.forward-headers-strategy=native); aqui usamos o endereço já resolvido.
        String ua = request.getHeader("User-Agent");
        if (ua != null && ua.length() > 500) {
            ua = ua.substring(0, 500);
        }
        return new ClientInfo(request.getRemoteAddr(), ua);
    }

    /** Rótulo amigável do dispositivo derivado do User-Agent. */
    public String deviceLabel() {
        if (userAgent == null) return "Desconhecido";
        String ua = userAgent.toLowerCase();
        String os = ua.contains("android") ? "Android" : ua.contains("iphone") || ua.contains("ipad") ? "iOS"
                : ua.contains("windows") ? "Windows" : ua.contains("mac os") ? "macOS" : ua.contains("linux") ? "Linux" : "Outro";
        String browser = ua.contains("edg/") ? "Edge" : ua.contains("chrome/") ? "Chrome" : ua.contains("firefox/") ? "Firefox"
                : ua.contains("safari/") ? "Safari" : "Navegador";
        return browser + " em " + os;
    }
}
