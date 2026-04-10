package com.ppiyaki.medicine.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "kakao.client-id=test-client-id",
        "kakao.client-secret=test-client-secret",
        "kakao.token-uri=http://localhost:19877/oauth/token",
        "kakao.user-info-uri=http://localhost:19877/v2/user/me"
})
class MedicineControllerE2ETest {

    private static final int WIREMOCK_PORT = 19877;
    private static WireMockServer wireMockServer;

    @LocalServerPort
    private int port;

    private static long kakaoIdSequence = 200000L;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        wireMockServer.resetAll();
        stubKakaoTokenEndpoint();
    }

    private String loginAsNewUser(final String nickname) {
        final long kakaoId = kakaoIdSequence++;
        stubKakaoUserInfoEndpoint(kakaoId, nickname);

        return RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "code": "test-auth-code",
                            "redirectUri": "http://localhost/callback"
                        }
                        """)
                .when()
                .post("/api/v1/auth/kakao")
                .then()
                .extract()
                .path("accessToken");
    }

    @Test
    @DisplayName("약물 등록 시 201 응답과 생성된 약물 정보를 반환한다")
    void create_success() {
        // given
        final String token = loginAsNewUser("등록유저");

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "name": "타이레놀정",
                            "totalAmount": 30,
                            "remainingAmount": 25,
                            "durWarningText": "공복 복용 시 위장장애 주의"
                        }
                        """)
                .when()
                .post("/api/v1/medicines")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", is("타이레놀정"))
                .body("totalAmount", is(30))
                .body("remainingAmount", is(25))
                .body("durWarningText", is("공복 복용 시 위장장애 주의"));
    }

    @Test
    @DisplayName("본인 약물 목록을 조회한다")
    void readAll_success() {
        // given
        final String token = loginAsNewUser("목록유저");
        createMedicine(token, "타이레놀정", 30, 25);
        createMedicine(token, "비타민C", 60, 50);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines")
                .then()
                .statusCode(200)
                .body("responses", hasSize(2));
    }

    @Test
    @DisplayName("약물 상세 조회에 성공한다")
    void readById_success() {
        // given
        final String token = loginAsNewUser("상세유저");
        final Integer medicineId = createMedicine(token, "오메가3", 90, 80);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("name", is("오메가3"))
                .body("totalAmount", is(90));
    }

    @Test
    @DisplayName("약물 정보를 수정한다")
    void update_success() {
        // given
        final String token = loginAsNewUser("수정유저");
        final Integer medicineId = createMedicine(token, "아스피린", 20, 15);

        // when & then
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "remainingAmount": 10
                        }
                        """)
                .when()
                .patch("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("name", is("아스피린"))
                .body("remainingAmount", is(10));
    }

    @Test
    @DisplayName("약물을 삭제한다")
    void delete_success() {
        // given
        final String token = loginAsNewUser("삭제유저");
        final Integer medicineId = createMedicine(token, "삭제대상약", 10, 5);

        // when & then
        RestAssured.given()
                .header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/medicines/" + medicineId)
                .then()
                .statusCode(200)
                .body("deletedMedicineId", is(medicineId))
                .body("deletedScheduleCount", is(0));
    }

    @Test
    @DisplayName("인증 없이 약물 API 호출 시 401 응답")
    void unauthorized() {
        RestAssured.given()
                .when()
                .get("/api/v1/medicines")
                .then()
                .statusCode(401);
    }

    private Integer createMedicine(
            final String token,
            final String name,
            final int totalAmount,
            final int remainingAmount
    ) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token)
                .body("""
                        {
                            "name": "%s",
                            "totalAmount": %d,
                            "remainingAmount": %d
                        }
                        """.formatted(name, totalAmount, remainingAmount))
                .when()
                .post("/api/v1/medicines")
                .then()
                .extract()
                .path("id");
    }

    private void stubKakaoTokenEndpoint() {
        wireMockServer.stubFor(post(urlPathEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "access_token": "kakao-access-token-mock",
                                    "token_type": "bearer",
                                    "expires_in": 3600
                                }
                                """)));
    }

    private void stubKakaoUserInfoEndpoint(final Long kakaoId, final String nickname) {
        wireMockServer.stubFor(get(urlPathEqualTo("/v2/user/me"))
                .withHeader("Authorization", equalTo("Bearer kakao-access-token-mock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "id": %d,
                                    "kakao_account": {
                                        "profile": {
                                            "nickname": "%s"
                                        }
                                    }
                                }
                                """.formatted(kakaoId, nickname))));
    }
}
