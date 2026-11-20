package com.qrcheckin.push;

/**
 * 알림 한 건. 서비스 워커(sw.js)가 받아서 화면에 띄운다.
 * @param tag 같은 tag 의 알림은 새 알림이 이전 알림을 덮는다
 * @param url 알림을 누르면 열 화면
 */
public record PushMessage(String title, String body, String url, String tag) {
}
