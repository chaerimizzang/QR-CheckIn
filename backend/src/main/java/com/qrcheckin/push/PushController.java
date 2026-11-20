package com.qrcheckin.push;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.auth.AuthUser;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 수업 알림(웹 푸시) 구독 */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    public record Keys(@NotBlank @Size(max = 120) String p256dh, @NotBlank @Size(max = 60) String auth) {
    }

    /** 브라우저 PushSubscription.toJSON() 모양 그대로 받는다 */
    public record SubscribeRequest(@NotBlank @Size(max = 512) String endpoint, @NotNull @Valid Keys keys) {
    }

    public record EndpointRequest(@NotBlank @Size(max = 512) String endpoint) {
    }

    @Operation(summary = "알림용 서버 공개키(VAPID)")
    @GetMapping("/public-key")
    public Map<String, String> publicKey() {
        return Map.of("publicKey", pushService.publicKey());
    }

    @Operation(summary = "이 기기에서 수업 알림 받기")
    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SubscribeRequest req) {
        pushService.subscribe(user.id(), req.endpoint(), req.keys().p256dh(), req.keys().auth());
    }

    @Operation(summary = "이 기기에서 수업 알림 끄기")
    @PostMapping("/subscriptions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody EndpointRequest req) {
        pushService.unsubscribe(user.id(), req.endpoint());
    }

    @Operation(summary = "테스트 알림 보내기(내 기기들로)")
    @PostMapping("/test")
    public Map<String, Integer> test(@AuthenticationPrincipal AuthUser user) {
        int sent = pushService.send(java.util.List.of(user.id()),
                new PushMessage("알림이 잘 와요", "수업 10분 전에 이렇게 알려 드릴게요", "/profile", "test"));
        return Map.of("sent", sent);
    }
}
