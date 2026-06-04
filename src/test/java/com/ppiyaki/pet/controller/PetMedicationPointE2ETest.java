package com.ppiyaki.pet.controller;

import com.ppiyaki.common.auth.JwtProvider;
import com.ppiyaki.medication.domain.DosageUnit;
import com.ppiyaki.medication.domain.MealSlot;
import com.ppiyaki.medication.domain.MedicationSchedule;
import com.ppiyaki.medication.repository.MedicationScheduleRepository;
import com.ppiyaki.medicine.Medicine;
import com.ppiyaki.medicine.repository.MedicineRepository;
import com.ppiyaki.pet.BadgeType;
import com.ppiyaki.pet.Pet;
import com.ppiyaki.pet.repository.BadgeRepository;
import com.ppiyaki.pet.repository.PetRepository;
import com.ppiyaki.user.domain.CareMode;
import com.ppiyaki.user.domain.User;
import com.ppiyaki.user.domain.UserRole;
import com.ppiyaki.user.repository.UserRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openai.api-key=sk-test-placeholder",
        "openai.model=gpt-test",
        "ncp.storage.endpoint=https://kr.object.ncloudstorage.com",
        "ncp.storage.region=kr-standard",
        "ncp.storage.access-key=test-access-key",
        "ncp.storage.secret-key=test-secret-key",
        "ncp.storage.bucket-name=ppiyaki-test"
})
@DisplayName("복약 인증 → 펫 포인트/streak 누적 E2E")
class PetMedicationPointE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MedicineRepository medicineRepository;
    @Autowired
    private MedicationScheduleRepository scheduleRepository;
    @Autowired
    private PetRepository petRepository;
    @Autowired
    private BadgeRepository badgeRepository;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private static long userSequence = 950000L;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("아/점/저 3건을 모두 인증하면 포인트가 10점씩 3회(30점) 누적되고 streak이 1로 증가한다")
    void everyTaken_accumulatesPointAndStreak() {
        // given — 펫이 연동된 시니어 + 아/점/저 schedule 3건
        final Long petId = seedPet();
        final Long seniorId = seedSeniorWithPet(petId);
        final String seniorToken = jwtProvider.createAccessToken(seniorId, UserRole.SENIOR.name());
        final Long medicineId = seedMedicine(seniorId);
        final Long breakfast = seedSchedule(medicineId, MealSlot.BREAKFAST);
        final Long lunch = seedSchedule(medicineId, MealSlot.LUNCH);
        final Long dinner = seedSchedule(medicineId, MealSlot.DINNER);
        final LocalDate today = LocalDate.now();

        // when — 3건 순차 인증
        certifyTaken(seniorToken, breakfast, today);
        certifyTaken(seniorToken, lunch, today);
        certifyTaken(seniorToken, dinner, today);

        // then — 첫 1건만 반영되고 멈추던 버그(포인트 10/streak 0) 회귀 방지
        final Pet pet = petRepository.findById(petId).orElseThrow();
        Assertions.assertThat(pet.getPoint()).isEqualTo(30L);
        Assertions.assertThat(pet.getStreak()).isEqualTo(1);
        Assertions.assertThat(pet.getLastTakenDate()).isEqualTo(today);
        // FIRST_STEP 뱃지는 1개만 (중복 INSERT 시도가 포인트 트랜잭션을 깨지 않아야 함)
        Assertions.assertThat(badgeRepository.existsByPetIdAndBadgeType(petId, BadgeType.FIRST_STEP)).isTrue();
    }

    // --- helpers ---

    private void certifyTaken(final String seniorToken, final Long scheduleId, final LocalDate today) {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + seniorToken)
                .body("""
                        {"scheduleId": %d, "targetDate": "%s", "status": "TAKEN"}
                        """.formatted(scheduleId, today))
                .when()
                .put("/api/v1/medication-logs")
                .then()
                .statusCode(200);
    }

    // --- fixtures ---

    private Long seedPet() {
        return transactionTemplate.execute(status -> petRepository.save(Pet.create()).getId());
    }

    private Long seedSeniorWithPet(final Long petId) {
        return transactionTemplate.execute(status -> {
            final User senior = User.createSenior("포인트시니어" + userSequence++, (LocalDate) null);
            senior.changeCareMode(CareMode.AUTONOMOUS);
            senior.assignPet(petId);
            return userRepository.save(senior).getId();
        });
    }

    private Long seedMedicine(final Long seniorId) {
        return transactionTemplate.execute(status -> {
            final Medicine medicine = new Medicine(seniorId, null, "테스트약", 30, 30, "ITEM-1", null);
            return medicineRepository.save(medicine).getId();
        });
    }

    private Long seedSchedule(final Long medicineId, final MealSlot slot) {
        return transactionTemplate.execute(status -> {
            final MedicationSchedule schedule = new MedicationSchedule(
                    medicineId, slot, BigDecimal.ONE, DosageUnit.TABLET,
                    "DAILY", LocalDate.now(), null);
            return scheduleRepository.save(schedule).getId();
        });
    }
}
