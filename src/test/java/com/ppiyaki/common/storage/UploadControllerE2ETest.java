package com.ppiyaki.common.storage;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(UploadControllerE2ETest.MockS3PresignerConfig.class)
class UploadControllerE2ETest {

    private static final String MOCK_PRESIGNED_URL = "https://kr.object.ncloudstorage.com/ppiyaki-test/prescription/1/uuid.jpg?X-Amz-Signature=mock";

    @LocalServerPort
    private int port;

    private static long userSequence = 500000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("유효한 요청이면 200 + presigned URL 응답")
    void createPresigned_success() {
        // given
        final String token = loginAsNewUser("업로드유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "purpose": "PRESCRIPTION",
                            "extension": "jpg",
                            "contentType": "image/jpeg"
                        }
                        """)
                .when()
                .post("/api/v1/uploads/presigned")
                .then()
                .statusCode(200)
                .body("objectKey", startsWith("prescription/"))
                .body("presignedUrl", is(MOCK_PRESIGNED_URL))
                .body("expiresAt", notNullValue());
    }

    @Test
    @DisplayName("허용되지 않은 확장자는 400 + COMMON_001")
    void createPresigned_invalidExtension() {
        // given
        final String token = loginAsNewUser("잘못된확장자유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "purpose": "PRESCRIPTION",
                            "extension": "gif",
                            "contentType": "image/gif"
                        }
                        """)
                .when()
                .post("/api/v1/uploads/presigned")
                .then()
                .statusCode(400)
                .body("error.code", is("COMMON_001"));
    }

    @Test
    @DisplayName("허용되지 않은 Content-Type은 400 + COMMON_001")
    void createPresigned_unsupportedContentType() {
        // given
        final String token = loginAsNewUser("잘못된MIME유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "purpose": "PRESCRIPTION",
                            "extension": "jpg",
                            "contentType": "application/pdf"
                        }
                        """)
                .when()
                .post("/api/v1/uploads/presigned")
                .then()
                .statusCode(400)
                .body("error.code", is("COMMON_001"));
    }

    @Test
    @DisplayName("purpose 누락은 400")
    void createPresigned_missingPurpose() {
        // given
        final String token = loginAsNewUser("필드누락유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "extension": "jpg",
                            "contentType": "image/jpeg"
                        }
                        """)
                .when()
                .post("/api/v1/uploads/presigned")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("인증 없이 호출하면 401 + AUTH_001")
    void createPresigned_unauthorized() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "purpose": "PRESCRIPTION",
                            "extension": "jpg",
                            "contentType": "image/jpeg"
                        }
                        """)
                .when()
                .post("/api/v1/uploads/presigned")
                .then()
                .statusCode(401)
                .body("error.code", is("AUTH_001"));
    }

    private String loginAsNewUser(final String nickname) {
        final String loginId = "uploadtest" + userSequence++;
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "loginId": "%s",
                            "password": "password1234!",
                            "nickname": "%s"
                        }
                        """.formatted(loginId, nickname))
                .when()
                .post("/api/v1/auth/signup")
                .then()
                .extract()
                .path("accessToken");
    }

    @TestConfiguration
    static class MockS3PresignerConfig {

        @Bean
        @Primary
        S3Presigner s3Presigner() throws Exception {
            final S3Presigner mockPresigner = mock(S3Presigner.class);
            final PresignedPutObjectRequest presignedResponse = mock(PresignedPutObjectRequest.class);
            when(presignedResponse.url()).thenReturn(URI.create(MOCK_PRESIGNED_URL).toURL());
            when(mockPresigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presignedResponse);
            return mockPresigner;
        }
    }
}
