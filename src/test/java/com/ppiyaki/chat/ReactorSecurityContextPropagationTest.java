package com.ppiyaki.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
@DisplayName("Reactor 스케줄러 경계를 넘어 SecurityContext가 전파된다")
class ReactorSecurityContextPropagationTest {

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("spring.reactor.context-propagation=auto 설정 시, Flux 오퍼레이터가 다른 스케줄러에서 실행돼도 SecurityContext가 유지된다")
    void securityContextPropagatesAcrossSchedulerBoundary() {
        final Long expectedPrincipal = 42L;
        final Authentication authentication = new TestingAuthenticationToken(expectedPrincipal, null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        final Object principalSeenByAsyncTask = Mono.fromCallable(() -> {
            final Authentication current = SecurityContextHolder.getContext().getAuthentication();
            return current == null ? null : current.getPrincipal();
        })
                .subscribeOn(Schedulers.boundedElastic())
                .block();

        assertThat(principalSeenByAsyncTask).isEqualTo(expectedPrincipal);
    }
}
