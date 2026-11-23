package com.qrcheckin.assistant;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qrcheckin.assistant.AssistantChatDtos.AskRequest;
import com.qrcheckin.assistant.AssistantChatDtos.AskResponse;
import com.qrcheckin.assistant.AssistantChatDtos.StatusResponse;
import com.qrcheckin.auth.AuthUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "AI 출결 도우미", description = "학생 본인 출결에 대한 질문·답변")
@RestController
@RequestMapping("/api/v1/assistant")
@PreAuthorize("hasRole('STUDENT')")
public class AssistantController {

    private final AssistantService service;

    public AssistantController(AssistantService service) {
        this.service = service;
    }

    @Operation(summary = "도우미 사용 가능 여부와 추천 질문")
    @GetMapping("/status")
    public StatusResponse status() {
        return service.status();
    }

    @Operation(summary = "질문하기: 답변 + 근거 카드 + 이동 버튼")
    @PostMapping("/messages")
    public AskResponse ask(@Valid @RequestBody AskRequest req, @AuthenticationPrincipal AuthUser user) {
        return service.ask(user, req);
    }
}
