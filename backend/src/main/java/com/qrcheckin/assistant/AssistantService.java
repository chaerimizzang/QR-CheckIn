package com.qrcheckin.assistant;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qrcheckin.assistant.AssistantChatDtos.AskRequest;
import com.qrcheckin.assistant.AssistantChatDtos.AskResponse;
import com.qrcheckin.assistant.AssistantChatDtos.StatusResponse;
import com.qrcheckin.assistant.AssistantChatDtos.Turn;
import com.qrcheckin.assistant.AssistantTemplates.PresetId;
import com.qrcheckin.assistant.llm.LlmClient;
import com.qrcheckin.assistant.llm.LlmException;
import com.qrcheckin.auth.AuthUser;
import com.qrcheckin.common.ApiException;
import com.qrcheckin.common.ErrorCode;

/**
 * AI 출결 도우미 대화 처리.
 * 1) 학생 질문 + 최근 대화를 LLM 에 보낸다
 * 2) LLM 이 도구를 요청하면 로그인한 학생 기준으로 실행해 결과를 돌려준다(최대 max-tool-calls 번)
 * 3) 답변의 숫자가 도구 결과에 없으면 한 번 고쳐 달라고 하고, 그래도 틀리면 근거 카드를 보라는 안전한 문장으로 바꾼다
 * 4) LLM 이 꺼졌거나 실패하면 추천 질문은 서버 템플릿으로 답한다
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    static final String SOURCE_AI = "AI";
    static final String SOURCE_TEMPLATE = "TEMPLATE";
    static final String SAFE_FALLBACK = "숫자를 정확히 설명하지 못했어요. 서버에서 계산한 정확한 값은 아래 근거 카드를 봐 주세요.";

    private final LlmClient llm;
    private final AssistantToolExecutor executor;
    private final AssistantTemplates templates;
    private final AssistantTools tools;
    private final AssistantRateLimiter limiter;
    private final ObjectMapper om;
    private final Clock clock;
    private final int maxToolCalls;

    public AssistantService(LlmClient llm, AssistantToolExecutor executor, AssistantTemplates templates,
                            AssistantTools tools, AssistantRateLimiter limiter, ObjectMapper om, Clock clock,
                            @Value("${app.assistant.max-tool-calls:6}") int maxToolCalls) {
        this.llm = llm;
        this.executor = executor;
        this.templates = templates;
        this.tools = tools;
        this.limiter = limiter;
        this.om = om;
        this.clock = clock;
        this.maxToolCalls = maxToolCalls;
    }

    public StatusResponse status() {
        return new StatusResponse(llm.configured(), llm.configured() ? llm.model() : null, AssistantTemplates.presets());
    }

    public AskResponse ask(AuthUser user, AskRequest req) {
        limiter.acquire(user.id());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        PresetId preset = PresetId.find(req.preset());

        if (!llm.configured()) {
            if (preset != null) {
                return template(preset, user, now);
            }
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE,
                    "AI 도우미가 아직 설정되지 않았어요. 추천 질문은 지금도 쓸 수 있어요");
        }
        try {
            return converse(user, req, now);
        } catch (LlmException e) {
            log.warn("[AI 도우미] LLM 실패({}): {}", e.kind(), e.getMessage());
            if (preset != null) {
                return template(preset, user, now);
            }
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE, e.kind() == LlmException.Kind.RATE_LIMITED
                    ? "오늘 AI 무료 사용량을 다 써서 답하기 어려워요. 추천 질문은 지금도 쓸 수 있어요"
                    : ErrorCode.ASSISTANT_UNAVAILABLE.defaultMessage());
        }
    }

    private AskResponse template(PresetId preset, AuthUser user, LocalDateTime now) {
        AssistantEvidence ev = new AssistantEvidence();
        String answer = templates.answer(preset, user, ev, now.toLocalDate());
        return new AskResponse(answer, SOURCE_TEMPLATE, false, ev.facts(), ev.actions(), now, tools.policy());
    }

    private AskResponse converse(AuthUser user, AskRequest req, LocalDateTime now) throws LlmException {
        AssistantEvidence ev = new AssistantEvidence();
        ev.allowDate(now);
        ev.allowFrom(req.message());

        ArrayNode messages = om.createArrayNode();
        messages.add(message("system", AssistantPrompt.system(now)));
        List<Turn> history = req.history() == null ? List.of() : req.history();
        for (Turn t : history) {
            messages.add(message(t.role(), t.text()));
            if ("user".equals(t.role())) {
                ev.allowFrom(t.text());
            }
        }
        messages.add(message("user", req.message()));

        int toolCalls = 0;
        String answer = null;
        // 도구 호출 묶음이 계속 와도 끝나도록 바퀴 수를 제한한다
        for (int round = 0; round <= maxToolCalls + 1 && answer == null; round++) {
            boolean toolsAllowed = toolCalls < maxToolCalls;
            JsonNode msg = llm.chat(messages, toolsAllowed ? executor.definitions() : null);
            JsonNode calls = msg.path("tool_calls");
            if (toolsAllowed && calls.isArray() && !calls.isEmpty()) {
                // Gemini 의 thought signature 가 들어 있으므로 받은 그대로 다시 넣는다
                ObjectNode echo = msg.deepCopy();
                echo.put("role", "assistant");
                messages.add(echo);
                for (JsonNode call : calls) {
                    String id = call.path("id").asText("");
                    String name = call.path("function").path("name").asText("");
                    String args = call.path("function").path("arguments").asText("");
                    String content = toolCalls < maxToolCalls
                            ? runTool(name, args, user, ev)
                            : error("이번 질문에서 쓸 수 있는 도구 횟수를 넘었어요");
                    toolCalls++;
                    ObjectNode toolMsg = om.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", id);
                    toolMsg.put("content", content);
                    messages.add(toolMsg);
                }
                continue;
            }
            String text = textOf(msg);
            if (text.isBlank()) {
                throw new LlmException(LlmException.Kind.FAILED, "빈 답변");
            }
            answer = text;
            messages.add(message("assistant", text));
        }
        if (answer == null) {
            throw new LlmException(LlmException.Kind.FAILED, "도구 호출이 끝나지 않았어요");
        }

        List<String> unknown = ev.unknownNumbers(answer);
        if (!unknown.isEmpty()) {
            log.info("[AI 도우미] 도구 결과에 없는 숫자 {} → 다시 요청", unknown);
            messages.add(message("user", "방금 답변의 숫자 " + String.join(", ", unknown)
                    + "은(는) 도구 결과에 없어요. 도구 결과에 있는 숫자만 써서 같은 내용을 다시 답해 주세요."));
            String retry = textOf(llm.chat(messages, null));
            answer = !retry.isBlank() && ev.unknownNumbers(retry).isEmpty() ? retry : SAFE_FALLBACK;
        }

        answer = clean(answer);
        boolean clarification = ev.successfulTools() == 0 && answer.contains("?");
        return new AskResponse(answer, SOURCE_AI, clarification, ev.facts(), ev.actions(), now, tools.policy());
    }

    /** 도구 실행 결과를 JSON 문자열로. 실패는 {"error": 이유}로 LLM 에 알려 준다(학생 데이터는 담지 않음) */
    private String runTool(String name, String args, AuthUser user, AssistantEvidence ev) {
        try {
            Object result = executor.execute(name, args, user);
            String json = om.writeValueAsString(result);
            ev.add(result, json);
            return json;
        } catch (ApiException e) {
            return error(e.getMessage());
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (JsonProcessingException e) {
            return error("결과를 만들지 못했어요");
        }
    }

    private String error(String message) {
        ObjectNode n = om.createObjectNode();
        n.put("error", message);
        return n.toString();
    }

    private ObjectNode message(String role, String content) {
        ObjectNode n = om.createObjectNode();
        n.put("role", role);
        n.put("content", content);
        return n;
    }

    /** content 가 문자열이거나 [{type:text, text}] 배열인 경우 모두 처리 */
    private static String textOf(JsonNode msg) {
        JsonNode c = msg.path("content");
        if (c.isTextual()) {
            return c.asText().trim();
        }
        if (c.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : c) {
                sb.append(part.path("text").asText(""));
            }
            return sb.toString().trim();
        }
        return "";
    }

    /** 지시해도 가끔 붙는 마크다운 굵게 표시 등을 걷어 낸다 */
    static String clean(String s) {
        return s.replace("**", "").replace("__", "").replaceAll("(?m)^#+\\s*", "").trim();
    }
}
