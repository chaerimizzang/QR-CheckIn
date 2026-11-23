package com.qrcheckin.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React 화면(static/index.html)을 Spring Boot 가 함께 제공한다.
 * 새 화면 주소를 App.jsx 에 추가하면 여기에도 꼭 추가해야 한다(없으면 새로고침 시 404 JSON).
 * /admin/... 처럼 화면 주소로 바로 들어오거나 새로고침해도 index.html 을 돌려줘서 React 라우터가 처리하게 한다.
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/signup", "/admin", "/admin/**", "/student", "/student/**", "/scan", "/check-in",
            "/profile", "/timetable", "/requests", "/attendance", "/assistant", "/me/**"})
    public String index() {
        return "forward:/index.html";
    }
}
