package com.qrcheckin.assistant.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * LLM 공급자 연결. 요청·응답은 OpenAI 호환 /chat/completions 형식(messages, tools, tool_calls).
 * Gemini·Groq 무료 등급이 모두 이 형식을 지원해서 구현은 하나(OpenAiCompatibleClient)로 충분하다.
 */
public interface LlmClient {

    /** 주소·모델·키가 모두 설정됐는지 */
    boolean configured();

    /** 설정된 모델 ID(로그·상태 표시용) */
    String model();

    /**
     * 대화를 보내고 choices[0].message 를 그대로 돌려준다.
     * (Gemini 는 도구 호출에 thought signature 를 붙이므로, 다음 요청에 이 노드를 수정 없이 다시 넣어야 한다)
     *
     * @param tools 비어 있거나 null 이면 도구 없이 답만 받는다
     */
    JsonNode chat(ArrayNode messages, ArrayNode tools) throws LlmException;
}
