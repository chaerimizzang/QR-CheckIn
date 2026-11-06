package com.qrcheckin.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * React 화면(static/index.html)을 Spring Boot 가 함께 제공한다.
 * /admin/... 처럼 화면 주소로 바로 들어오거나 새로고침해도 index.html 을 돌려줘서 React 라우터가 처리하게 한다.
 */
@Controller
public class SpaController {

    @GetMapping({"/login", "/signup", "/admin", "/admin/**", "/student", "/student/**", "/scan", "/check-in"})
    public String index() {
        return "forward:/index.html";
    }
}
