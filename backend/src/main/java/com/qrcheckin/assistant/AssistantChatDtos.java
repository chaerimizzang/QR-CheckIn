package com.qrcheckin.assistant;

import java.time.LocalDateTime;
import java.util.List;

import com.qrcheckin.assistant.AssistantDtos.Policy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** AI 도우미 질문·답변 형식 */
public final class AssistantChatDtos {

    private AssistantChatDtos() {
    }

    /** 이전 대화 한 턴(화면에만 보관하다가 다음 질문 때 함께 보낸다) */
    public record Turn(
            @NotNull @Pattern(regexp = "user|assistant") String role,
            @NotBlank @Size(max = 1500) String text) {
    }

    /**
     * @param preset 추천 질문 버튼으로 물었을 때 그 ID(RISK·ONE_MORE_ABSENCE·THIS_WEEK_REQUESTS). LLM 이 안 될 때 템플릿 답변에 쓴다
     */
    public record AskRequest(
            @NotBlank(message = "질문을 입력해 주세요") @Size(max = 300, message = "질문은 300자까지 쓸 수 있어요") String message,
            @Size(max = 6) List<@Valid Turn> history,
            String preset) {
    }

    /** 근거 카드 한 줄(서버가 도구 결과로 만든 값 — 화면은 이 숫자를 그대로 보여 준다) */
    public record Fact(String label, String value) {
    }

    /** 이동 버튼(서버가 허용한 내부 경로만) */
    public record Action(String label, String path) {
    }

    public record AskResponse(
            String answer,
            /** AI: LLM 이 쓴 문장, TEMPLATE: 서버가 만든 문장(LLM 실패·한도 초과 때) */
            String source,
            boolean needsClarification,
            List<Fact> facts,
            List<Action> actions,
            LocalDateTime asOf,
            Policy policy) {
    }

    /** 추천 질문 */
    public record Preset(String id, String question) {
    }

    public record StatusResponse(boolean available, String model, List<Preset> presets) {
    }
}
