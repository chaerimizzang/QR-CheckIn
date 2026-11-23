package com.qrcheckin.assistant.llm;

import java.net.http.HttpClient;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OpenAI 호환 /chat/completions 호출(Gemini·Groq 공용). 새 라이브러리 없이 Spring 의 RestClient 만 쓴다.
 * 키는 서버 설정(.env)에서만 읽고 로그·오류 메시지에 남기지 않는다.
 */
@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final String baseUrl;
    private final String model;
    private final String apiKey;
    /** Gemini 의 생각(thinking) 양. low 면 훨씬 빨리 답한다. 공급자가 거부하면 자동으로 빼고 다시 보낸다 */
    private final String reasoningEffort;
    private volatile boolean reasoningSupported = true;
    private final ObjectMapper om;
    private final RestClient http;

    public OpenAiCompatibleClient(@Value("${app.assistant.base-url:}") String baseUrl,
                                  @Value("${app.assistant.model:}") String model,
                                  @Value("${app.assistant.api-key:}") String apiKey,
                                  @Value("${app.assistant.timeout-seconds:25}") int timeoutSeconds,
                                  @Value("${app.assistant.reasoning-effort:low}") String reasoningEffort,
                                  ObjectMapper om) {
        this.baseUrl = trimSlash(baseUrl == null ? "" : baseUrl.trim());
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.trim();
        this.om = om;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public boolean configured() {
        return !baseUrl.isEmpty() && !model.isEmpty() && !apiKey.isEmpty();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public JsonNode chat(ArrayNode messages, ArrayNode tools) throws LlmException {
        if (!configured()) {
            throw new LlmException(LlmException.Kind.NOT_CONFIGURED, "LLM_BASE_URL·LLM_MODEL·LLM_API_KEY 를 .env 에 넣어 주세요");
        }
        boolean withEffort = reasoningSupported && !reasoningEffort.isEmpty();
        try {
            return send(messages, tools, withEffort);
        } catch (LlmException e) {
            // 이 모델·공급자가 reasoning_effort 를 모르면 빼고 한 번 더(다음부터는 안 붙인다)
            if (withEffort && e.getMessage() != null && e.getMessage().contains("reasoning")) {
                reasoningSupported = false;
                log.info("[AI 도우미] 이 모델은 reasoning_effort 를 지원하지 않아 빼고 보냅니다");
                return send(messages, tools, false);
            }
            throw e;
        }
    }

    private JsonNode send(ArrayNode messages, ArrayNode tools, boolean withEffort) throws LlmException {
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        if (withEffort) {
            body.put("reasoning_effort", reasoningEffort);
        }

        long started = System.nanoTime();
        String raw;
        try {
            raw = http.post()
                    .uri(baseUrl + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(om.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                String err = e.getResponseBodyAsString();
                // Gemini 는 어떤 한도인지(…PerDay… / …PerMinute…)와 retryDelay("23s")를 본문에 알려 준다
                boolean daily = err != null && err.contains("PerDay");
                int retryAfter = retryAfterSeconds(e.getResponseHeaders() == null ? null
                        : e.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER), err);
                throw new LlmException(LlmException.Kind.RATE_LIMITED,
                        "무료 한도 초과(429, " + (daily ? "하루" : "분당") + " 한도, " + retryAfter + "초 뒤): " + shorten(err),
                        e, daily, retryAfter);
            }
            throw new LlmException(LlmException.Kind.FAILED,
                    "LLM 오류 " + e.getStatusCode().value() + ": " + shorten(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new LlmException(LlmException.Kind.NETWORK,
                    "LLM 서버에 연결하지 못했어요(시간 초과 또는 네트워크 — 학교 와이파이면 핫스팟으로): " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.FAILED, "요청을 만들지 못했어요", e);
        }
        long ms = (System.nanoTime() - started) / 1_000_000;

        try {
            JsonNode root = om.readTree(raw == null ? "" : raw);
            JsonNode message = root.path("choices").path(0).path("message");
            if (!message.isObject()) {
                throw new LlmException(LlmException.Kind.FAILED, "응답에 message 가 없어요: " + shorten(raw));
            }
            JsonNode usage = root.path("usage");
            log.info("[AI 도우미] LLM {}ms · 토큰 입력 {} / 출력 {}{}", ms, usage.path("prompt_tokens").asInt(),
                    usage.path("completion_tokens").asInt(), message.path("tool_calls").isArray() ? " · 도구 요청" : "");
            return message;
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.FAILED, "응답을 읽지 못했어요: " + shorten(raw), e);
        }
    }

    private static final java.util.regex.Pattern RETRY_DELAY =
            java.util.regex.Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)(?:\\.\\d+)?s\"");

    /** Retry-After 헤더(초) 또는 Gemini 본문의 retryDelay("23s"). 모르면 0 */
    static int retryAfterSeconds(String header, String body) {
        if (header != null && header.trim().matches("\\d+")) {
            return Integer.parseInt(header.trim());
        }
        if (body != null) {
            java.util.regex.Matcher m = RETRY_DELAY.matcher(body);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return 0;
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String shorten(String s) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ");
        return one.length() > 300 ? one.substring(0, 300) + "…" : one;
    }
}
