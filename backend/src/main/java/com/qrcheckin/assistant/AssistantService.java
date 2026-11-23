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
import com.qrcheckin.assistant.AssistantDtos.SummaryResult;
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
    /** 추천 질문을 LLM 없이 서버가 바로 답함(preset-direct) */
    static final String SOURCE_PRESET = "PRESET";
    static final String SAFE_FALLBACK = "숫자를 정확히 설명하지 못했어요. 서버에서 계산한 정확한 값은 아래 근거 카드를 봐 주세요.";
    static final String NO_FACTS_FALLBACK = "정확한 정보를 확인하지 못했어요. 질문을 조금 더 구체적으로 해 주시거나 출결 현황·시간표 화면에서 확인해 주세요.";

    private final LlmClient llm;
    private final AssistantToolExecutor executor;
    private final AssistantTemplates templates;
    private final AssistantTools tools;
    private final AssistantRateLimiter limiter;
    private final ObjectMapper om;
    private final Clock clock;
    private final int maxToolCalls;
    private final boolean presetDirect;

    public AssistantService(LlmClient llm, AssistantToolExecutor executor, AssistantTemplates templates,
                            AssistantTools tools, AssistantRateLimiter limiter, ObjectMapper om, Clock clock,
                            @Value("${app.assistant.max-tool-calls:6}") int maxToolCalls,
                            @Value("${app.assistant.preset-direct:true}") boolean presetDirect) {
        this.llm = llm;
        this.executor = executor;
        this.templates = templates;
        this.tools = tools;
        this.limiter = limiter;
        this.om = om;
        this.clock = clock;
        this.maxToolCalls = maxToolCalls;
        this.presetDirect = presetDirect;
    }

    public StatusResponse status() {
        return new StatusResponse(llm.configured(), llm.configured() ? llm.model() : null, AssistantTemplates.presets());
    }

    public AskResponse ask(AuthUser user, AskRequest req) {
        limiter.acquire(user.id());
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        PresetId preset = PresetId.find(req.preset());

        // 추천 질문은 정해진 질문이라 서버가 바로 답한다(LLM 왕복 없이 1초 안에)
        if (preset != null && presetDirect) {
            return template(preset, user, now, SOURCE_PRESET);
        }
        if (!llm.configured()) {
            if (preset != null) {
                return template(preset, user, now, SOURCE_TEMPLATE);
            }
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE,
                    "AI 도우미가 아직 설정되지 않았어요. 추천 질문은 지금도 쓸 수 있어요");
        }
        long started = System.nanoTime();
        try {
            AskResponse res = converse(user, req, now);
            log.info("[AI 도우미] 답변 완료 {}ms", (System.nanoTime() - started) / 1_000_000);
            return res;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            // 도우미 안에서 예상 못 한 오류: '서버 오류' 대신 다시 시도 안내, 원인은 로그에 남긴다
            log.error("[AI 도우미] 처리 중 오류", e);
            if (preset != null) {
                return template(preset, user, now, SOURCE_TEMPLATE);
            }
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE);
        } catch (LlmException e) {
            log.warn("[AI 도우미] LLM 실패({}, {}ms): {}", e.kind(), (System.nanoTime() - started) / 1_000_000, e.getMessage());
            if (preset != null) {
                return template(preset, user, now, SOURCE_TEMPLATE);
            }
            throw new ApiException(ErrorCode.ASSISTANT_UNAVAILABLE, unavailableMessage(e));
        }
    }

    /** 무료 한도 초과는 분당·하루를 구분해 언제 다시 물으면 되는지 알려 준다 */
    static String unavailableMessage(LlmException e) {
        if (e.kind() != LlmException.Kind.RATE_LIMITED) {
            return ErrorCode.ASSISTANT_UNAVAILABLE.defaultMessage();
        }
        if (e.daily()) {
            return "오늘 AI 무료 사용량을 다 써서 답하기 어려워요. 추천 질문은 지금도 쓸 수 있어요";
        }
        String when = e.retryAfterSeconds() > 0 ? e.retryAfterSeconds() + "초쯤 뒤에" : "1분쯤 뒤에";
        return "질문이 한꺼번에 몰려 AI가 잠시 쉬는 중이에요. " + when + " 다시 물어봐 주세요. 추천 질문은 지금도 쓸 수 있어요";
    }

    private AskResponse template(PresetId preset, AuthUser user, LocalDateTime now, String source) {
        AssistantEvidence ev = new AssistantEvidence();
        String answer = templates.answer(preset, user, ev, now.toLocalDate());
        return new AskResponse(answer, source, false, ev.facts(), ev.actions(), now, tools.policy());
    }

    private AskResponse converse(AuthUser user, AskRequest req, LocalDateTime now) throws LlmException {
        AssistantEvidence ev = new AssistantEvidence();
        ev.allowDate(now);
        ev.allowFrom(req.message());

        // 현재 학기 출결 요약을 처음부터 넣어 두면 "출석률 몇이야?", "몇 번 더 빠져도 돼?" 같은 질문은
        // 도구 없이 LLM 한 번에 답한다. 이름·학번·이메일·교수 이름은 넣지 않는다
        SummaryResult summary = tools.attendanceSummary(user, null, false);
        String summaryJson;
        try {
            ArrayNode courses = om.valueToTree(summary.courses());
            courses.forEach(c -> ((ObjectNode) c).remove(List.of("ownerName", "semesterKey", "semesterLabel")));
            summaryJson = om.writeValueAsString(courses);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            summaryJson = "[]";
        }
        ev.allowFrom(summaryJson);

        ArrayNode messages = om.createArrayNode();
        messages.add(message("system", AssistantPrompt.system(now, summaryJson)));
        List<Turn> history = req.history() == null ? List.of() : req.history();
        for (Turn t : history) {
            messages.add(message(t.role(), t.text()));
            if ("user".equals(t.role())) {
                ev.allowFrom(t.text());
            }
        }
        messages.add(message("user", req.message()));

        int[] toolCalls = {0};
        String answer = runLoop(messages, user, ev, toolCalls);

        List<String> unknown = ev.unknownNumbers(answer);
        if (!unknown.isEmpty()) {
            log.info("[AI 도우미] 도구 결과에 없는 숫자 {} → 다시 요청", unknown);
            messages.add(message("user", "방금 답변의 숫자 " + String.join(", ", unknown)
                    + "은(는) 도구 결과에 없어요. 짐작하지 말고 필요한 도구로 확인한 뒤, 도구 결과에 있는 숫자만 써서 다시 답해 주세요."));
            String retry = runLoop(messages, user, ev, toolCalls);
            if (ev.unknownNumbers(retry).isEmpty()) {
                answer = retry;
            } else {
                log.info("[AI 도우미] 다시 요청해도 맞지 않는 숫자 {} → 안전한 문장", ev.unknownNumbers(retry));
                answer = ev.facts().isEmpty() ? NO_FACTS_FALLBACK : SAFE_FALLBACK;
            }
        }

        answer = clean(answer);
        // 도구 없이 요약으로 답했으면, 답변에 나온 수업의 요약을 근거 카드로 붙인다
        ev.addSummaryMentioned(summary, answer);
        boolean clarification = ev.facts().isEmpty() && answer.contains("?");
        return new AskResponse(answer, SOURCE_AI, clarification, ev.facts(), ev.actions(), now, tools.policy());
    }

    /** LLM 과 도구를 주고받아 최종 답변 문장을 얻는다. toolCalls[0] 은 이번 질문에서 쓴 도구 수(다시 요청할 때도 이어서 센다) */
    private String runLoop(ArrayNode messages, AuthUser user, AssistantEvidence ev, int[] toolCalls) throws LlmException {
        // 도구 호출 묶음이 계속 와도 끝나도록 바퀴 수를 제한한다
        for (int round = 0; round <= maxToolCalls + 1; round++) {
            boolean toolsAllowed = toolCalls[0] < maxToolCalls;
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
                    String content = toolCalls[0] < maxToolCalls
                            ? runTool(name, args, user, ev)
                            : error("이번 질문에서 쓸 수 있는 도구 횟수를 넘었어요");
                    toolCalls[0]++;
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
            messages.add(message("assistant", text));
            return text;
        }
        throw new LlmException(LlmException.Kind.FAILED, "도구 호출이 끝나지 않았어요");
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

    /** 지시해도 가끔 붙는 마크다운 굵게 표시, 영어 경고 단계 이름 등을 걷어 낸다 */
    static String clean(String s) {
        return s.replace("**", "").replace("__", "").replaceAll("(?m)^#+\\s*", "")
                .replaceAll("\\bFAIL\\b", "F 기준 초과")
                .replaceAll("\\bDANGER\\b", "위험")
                .replaceAll("\\bWARNING\\b", "주의")
                .replaceAll("\\bSAFE\\b", "안전")
                .trim();
    }
}
