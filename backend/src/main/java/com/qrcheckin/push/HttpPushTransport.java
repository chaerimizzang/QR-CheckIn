package com.qrcheckin.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HttpPushTransport implements PushTransport {

    private static final Logger log = LoggerFactory.getLogger(HttpPushTransport.class);

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public int post(String endpoint, Map<String, String> headers, byte[] body) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(req::header);
            HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.info("푸시 서버 응답 {}: {}", res.statusCode(), res.body());
            }
            return res.statusCode();
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("푸시 전송 실패: {}", e.toString());
            return -1;
        }
    }
}
