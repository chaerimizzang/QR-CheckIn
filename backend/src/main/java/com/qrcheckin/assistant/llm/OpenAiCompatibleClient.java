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
    private final ObjectMapper om;
    private final RestClient http;

    public OpenAiCompatibleClient(@Value("${app.assistant.base-url:}") String baseUrl,
                                  @Value("${app.assistant.model:}") String model,
                                  @Value("${app.assistant.api-key:}") String apiKey,
                                  @Value("${app.assistant.timeout-seconds:25}") int timeoutSeconds,
                                  ObjectMapper om) {
        this.baseUrl = trimSlash(baseUrl == null ? "" : baseUrl.trim());
        this.model = model == null ? "" : model.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
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
        ObjectNode body = om.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }

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
                throw new LlmException(LlmException.Kind.RATE_LIMITED, "무료 한도를 넘었어요(429)", e);
            }
            throw new LlmException(LlmException.Kind.FAILED,
                    "LLM 오류 " + e.getStatusCode().value() + ": " + shorten(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            throw new LlmException(LlmException.Kind.NETWORK,
                    "LLM 서버에 연결하지 못했어요(시간 초과 또는 네트워크 — 학교 와이파이면 핫스팟으로): " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.FAILED, "요청을 만들지 못했어요", e);
        }

        try {
            JsonNode root = om.readTree(raw == null ? "" : raw);
            JsonNode message = root.path("choices").path(0).path("message");
            if (!message.isObject()) {
                throw new LlmException(LlmException.Kind.FAILED, "응답에 message 가 없어요: " + shorten(raw));
            }
            JsonNode usage = root.path("usage");
            if (usage.isObject()) {
                log.info("[AI 도우미] 토큰 입력 {} / 출력 {}", usage.path("prompt_tokens").asInt(),
                        usage.path("completion_tokens").asInt());
            }
            return message;
        } catch (JsonProcessingException e) {
            throw new LlmException(LlmException.Kind.FAILED, "응답을 읽지 못했어요: " + shorten(raw), e);
        }
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
