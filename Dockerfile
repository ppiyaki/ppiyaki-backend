FROM gradle:9.4-jdk21 AS build
WORKDIR /app

# 의존성 레이어 분리 — build 파일만 먼저 COPY해서 dependencies 다운로드 결과를
# Docker 레이어 캐시로 보존. 소스 변경만 있으면 의존성 재다운로드 skip.
COPY build.gradle settings.gradle /app/
RUN gradle dependencies --no-daemon > /dev/null 2>&1 || true

COPY config /app/config
COPY src /app/src

# 위 레이어에서 가져온 의존성/빌드 결과를 보존하도록 clean 제거.
# 소스가 바뀐 부분만 컴파일.
RUN gradle bootJar -x test -x checkstyleMain -x checkstyleTest --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
