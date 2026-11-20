package com.qrcheckin.push;

import java.util.Map;

/** 푸시 서버로 실제 HTTP 요청을 보내는 부분. 테스트에서는 가짜 구현으로 바꾼다 */
public interface PushTransport {

    /** @return HTTP 상태 코드(연결 실패면 -1) */
    int post(String endpoint, Map<String, String> headers, byte[] body);
}
