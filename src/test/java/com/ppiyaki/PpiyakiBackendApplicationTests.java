package com.ppiyaki;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "spring.ai.openai.api-key=test-dummy-key")
class PpiyakiBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
