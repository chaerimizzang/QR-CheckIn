# QR-CheckIn 서버 이미지 (화면은 static 에 이미 빌드돼 있어서 Node.js 불필요)
# 1단계: Gradle 로 실행 파일(jar) 만들기
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
# Windows 에서 받은 파일은 줄바꿈이 CRLF 일 수 있어 gradlew 실행이 깨진다 → LF 로 바꾼다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
# --mount=type=cache: Gradle 라이브러리를 캐시해 두 번째 빌드부터 빨라진다
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon \
    && cp build/libs/*[!n].jar /app.jar

# 2단계: 실행용 가벼운 이미지
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
