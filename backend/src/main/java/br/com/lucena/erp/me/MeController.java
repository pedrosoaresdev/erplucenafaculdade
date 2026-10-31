package br.com.lucena.erp.me;

import br.com.lucena.erp.common.ClientInfo;
import br.com.lucena.erp.common.PageResponse;
import br.com.lucena.erp.me.MeDtos.*;
import br.com.lucena.erp.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "Meu perfil e segurança")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MeController {
    private final ProfileService service;

    @GetMapping
    public Profile me() {
        return service.profile(CurrentUser.get());
    }

    @PatchMapping
    public Profile update(@Valid @RequestBody UpdateProfile req, HttpServletRequest http) {
        return service.update(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @PutMapping("/email")
    public Profile updateEmail(@Valid @RequestBody UpdateEmail req, HttpServletRequest http) {
        return service.updateEmail(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @Operation(summary = "Reautenticação (senha atual + código do Google Authenticator) para operações sensíveis")
    @PostMapping("/step-up")
    public StepUpResponse stepUp(@Valid @RequestBody StepUpRequest req, HttpServletRequest http) {
        return service.stepUp(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @Operation(summary = "Alterar senha (requer step-up PASSWORD_CHANGE). Encerra todas as sessões.")
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePassword req, HttpServletRequest http) {
        service.changePassword(CurrentUser.get(), req, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mfa")
    public MfaStatus mfaStatus() {
        return service.mfaStatus(CurrentUser.get());
    }

    @PostMapping("/mfa/rotation/start")
    public MfaEnrollment startRotation(@Valid @RequestBody StepUpOnly req, HttpServletRequest http) {
        return service.startRotation(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @PostMapping("/mfa/rotation/confirm")
    public RecoveryCodes confirmRotation(@Valid @RequestBody CodeOnly req, HttpServletRequest http) {
        return service.confirmRotation(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<Void> disableMfa(@Valid @RequestBody StepUpOnly req, HttpServletRequest http) {
        service.disableMfa(CurrentUser.get(), req, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/mfa/recovery-codes")
    public RecoveryCodes regenerateRecoveryCodes(@Valid @RequestBody StepUpOnly req, HttpServletRequest http) {
        return service.regenerateRecoveryCodes(CurrentUser.get(), req, ClientInfo.from(http));
    }

    @GetMapping("/sessions")
    public List<SessionView> sessions() {
        return service.sessions(CurrentUser.get());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable UUID id, HttpServletRequest http) {
        service.revokeSession(CurrentUser.get(), id, ClientInfo.from(http));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    public Map<String, Integer> revokeOthers(HttpServletRequest http) {
        return Map.of("revoked", service.revokeOtherSessions(CurrentUser.get(), ClientInfo.from(http)));
    }

    @GetMapping("/access-history")
    public PageResponse<AccessView> accessHistory(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.accessHistory(CurrentUser.get(), page, size);
    }

    @GetMapping("/notifications")
    public PageResponse<NotificationView> notifications(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.notifications(CurrentUser.get(), page, size);
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", service.unreadNotifications(CurrentUser.get()));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        service.markNotificationRead(CurrentUser.get(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Void> readAll() {
        service.markAllNotificationsRead(CurrentUser.get());
        return ResponseEntity.noContent().build();
    }
}
