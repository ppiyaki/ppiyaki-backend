FROM gradle:8.5-jdk21 AS build
WORKDIR /app

COPY build.gradle settings.gradle /app/
RUN gradle dependencies --no-daemon

COPY config /app/config

COPY src /app/src
RUN gradle clean build -x test -x checkstyleMain -x checkstyleTest --no-daemon

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]

EXPOSE 8080
