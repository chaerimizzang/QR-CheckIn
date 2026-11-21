package com.qrcheckin.config;

import java.time.Duration;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 화면 파일 캐시. 빌드할 때 JS·CSS 파일 이름에 내용 해시가 붙으므로(예: index-3f2a1c.js)
 * /assets 아래 파일은 바뀌지 않는다 → 1년 동안 다시 받지 않게 한다(앱을 열 때 빨라짐).
 * index.html·sw.js·manifest 는 그대로 매번 확인(no-cache)해서 새 버전을 바로 알아챈다.
 */
@Configuration
public class WebCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/icons/**")
                .addResourceLocations("classpath:/static/icons/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
    }
}
