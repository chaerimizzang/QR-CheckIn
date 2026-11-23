package com.qrcheckin.assistant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.qrcheckin.assistant.llm.LlmClient;
import com.qrcheckin.assistant.llm.LlmException;

/**
 * 서버가 켜지면 짧은 요청 하나로 LLM 키·모델·네트워크가 맞는지 확인해 로그에 남긴다.
 * (docker compose logs app 에서 "[AI 도우미]"로 확인) 서버 시작을 막지 않도록 별도 스레드에서 한다.
 */
@Component
class AssistantStartupCheck {

    private static final Logger log = LoggerFactory.getLogger(AssistantStartupCheck.class);

    private final LlmClient llm;
    private final ObjectMapper om;
    private final boolean enabled;

    AssistantStartupCheck(LlmClient llm, ObjectMapper om,
                          @Value("${app.assistant.startup-check:true}") boolean enabled) {
        this.llm = llm;
        this.om = om;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        if (!enabled) {
            return;
        }
        if (!llm.configured()) {
            log.info("[AI 도우미] 꺼짐 — .env 에 LLM_BASE_URL·LLM_MODEL·LLM_API_KEY 를 넣으면 켜져요");
            return;
        }
        Thread t = new Thread(() -> {
            ArrayNode messages = om.createArrayNode();
            messages.addObject().put("role", "user").put("content", "연결 확인입니다. '확인'이라고만 답해 주세요.");
            try {
                llm.chat(messages, null);
                log.info("[AI 도우미] 연결 확인 성공 — 모델 {}", llm.model());
            } catch (LlmException e) {
                log.warn("[AI 도우미] 연결 확인 실패({}) — {}", e.kind(), e.getMessage());
            }
        }, "assistant-startup-check");
        t.setDaemon(true);
        t.start();
    }
}
