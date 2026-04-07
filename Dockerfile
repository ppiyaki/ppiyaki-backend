FROM gradle:8.5-jdk21 AS build
WORKDIR /app

COPY build.gradle settings.gradle /app/

RUN gradle build -x test --no-daemon > /dev/null 2>&1 || true

COPY config /app/config
COPY src /app/src

RUN gradle clean build -x test --no-daemon
