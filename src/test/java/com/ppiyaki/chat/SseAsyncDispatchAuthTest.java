package com.ppiyaki.chat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;

import com.ppiyaki.common.auth.JwtProvider;
import io.restassured.RestAssured;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 비동기 응답의 인증 회귀 가드.
 *
 * <p>SseEmitter는 스트림 종료/에러 시 컨테이너로 ASYNC/ERROR 디스패치가 한 번 더 일어난다.
 * JwtAuthenticationFilter(OncePerRequestFilter)는 기본적으로 이 두 디스패치를 건너뛰지만,
 * Spring Security 6의 AuthorizationFilter는 모든 디스패치 타입에서 동작한다.
 * 따라서 첫 이벤트 전송 전에 스트림이 실패하면 SecurityContext가 비어 익명으로 간주돼
 * 유효한 토큰임에도 가짜 401(AUTH_001)이 클라이언트에 노출됐다.
 *
 * <p>JwtAuthenticationFilter가 ASYNC/ERROR 디스패치에서도 컨텍스트를 복원하도록 고친 뒤,
 * 첫 send 전 실패가 더 이상 401로 둔갑하지 않음을 검증한다. 실제 SecurityConfig /
 * JwtAuthenticationFilter / RestAuthenticationEntryPoint / chatStreamExecutor 빈을 그대로 사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
@Import(SseAsyncDispatchAuthTest.TestSseController.class)
class SseAsyncDispatchAuthTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtProvider jwtProvider;

    private String accessToken;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        accessToken = jwtProvider.createAccessToken(1L, "SENIOR");
    }

    @Test
    @DisplayName("첫 이벤트 전송에 성공하면 200을 반환한다 (대조군)")
    void successPath_returns200() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions/1/sse-test-success")
                .then()
                .statusCode(200);
    }

    @Test
    @DisplayName("첫 이벤트 전송 전에 스트림이 실패해도 인증은 유지되어 가짜 401을 반환하지 않는다")
    void errorBeforeFirstSend_doesNotReturnSpurious401() {
        given()
                .header("Authorization", "Bearer " + accessToken)
                .when()
                .post("/api/v1/chat/sessions/1/sse-test-error-before-send")
                .then()
                .statusCode(not(401));
    }

    @TestConfiguration
    @RestController
    @RequestMapping("/api/v1/chat/sessions")
    static class TestSseController {

        private final Executor chatStreamExecutor;

        TestSseController(@Qualifier("chatStreamExecutor") final Executor chatStreamExecutor) {
            this.chatStreamExecutor = chatStreamExecutor;
        }

        @PostMapping("/{id}/sse-test-success")
        public SseEmitter sseSuccess(
                @AuthenticationPrincipal final Long userId,
                @PathVariable final Long id) {
            final SseEmitter emitter = new SseEmitter();
            chatStreamExecutor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event().data("hello userId=" + userId));
                    emitter.complete();
                } catch (final Exception e) {
                    emitter.completeWithError(e);
                }
            });
            return emitter;
        }

        @PostMapping("/{id}/sse-test-error-before-send")
        public SseEmitter sseErrorBeforeSend(
                @AuthenticationPrincipal final Long userId,
                @PathVariable final Long id) {
            final SseEmitter emitter = new SseEmitter();
            chatStreamExecutor.execute(() -> emitter.completeWithError(new RuntimeException(
                    "stream failed before first event")));
            return emitter;
        }
    }
}
