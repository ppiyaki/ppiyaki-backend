# Code Conventions

본 문서는 코드 작성 시의 **협의된 규칙**을 모아둔다. 컨벤션이 변경되면 본 문서를 먼저 PR로 수정한 뒤 코드에 반영한다. 일부 항목은 checkstyle/spotless로 자동 강제되지만 대부분은 PR 리뷰와 팀 합의로 운영한다.

## 1) 언어 / 포매팅

### 1-1) `final` 키워드
- **메서드 매개변수**: 반드시 `final`
- **지역 변수**: 반드시 `final` (재할당이 의도된 루프 변수 등 예외는 PR 리뷰에서 판단)
- **필드**: 불변이 가능하면 `final` 권장

```java
public MemberCreateResponse createMember(final MemberCreateRequest request) {
    final Member member = Member.create(request.loginId(), request.nickname());
    return MemberCreateResponse.from(member);
}
```

### 1-2) `record`
- 필드가 2개 이상이면 **멀티라인**으로 작성.

```java
public record MedicineCreateRequest(
        String name,
        Integer totalAmount,
        Integer remainingAmount,
        String durWarningText) {}
```

### 1-3) 어노테이션 순서
- **글자 길이 피라미드**(짧은 것부터 긴 것으로 내려가도록) 정렬.
- 타입/메서드/필드 각각 독립 적용.

```java
@Getter
@Entity
@Table(name = "medicines")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class Medicine extends CreatedTimeEntity { ... }
```

### 1-4) 변수명
- **풀네임**으로 작성. 약어 금지.
- DTO/엔티티 인스턴스는 **타입명을 그대로 camelCase**로 사용한다.

```java
// 좋음
final MemberCreateResponse memberCreateResponse = memberService.createMember(request);

// 나쁨
final MemberCreateResponse resp = memberService.createMember(request);
```

## 2) DTO

### 2-1) 네이밍
- 요청: `XxxRequest`
- 응답: `XxxResponse`
- 리스트 응답 변수명: **`responses`** (복수형 s)
  ```java
  final List<MedicineResponse> responses = medicineService.readAll();
  ```

### 2-2) 재활용 금지 (이번 스프린트 한정)
- **API별로 DTO를 분리**한다. 한 DTO를 여러 엔드포인트에서 공유하지 않는다.
- 초기 스프린트는 스키마 진화 속도가 빠르므로, 재사용보다 명시적 분리를 우선.
- 이후 스프린트에서 중복이 누적되면 별도 ADR로 재논의.

### 2-3) record vs class
- 요청/응답 DTO는 **record 우선**.
- 검증 어노테이션(`@NotNull`, `@Size` 등)은 record 필드에 바로 적용.

## 3) 메서드 네이밍 (Controller / Service)

| 동작 | 선호 prefix |
|---|---|
| 조회 | `read` / `get` / `find` |
| 등록 | `create` / `add` |
| 수정 | `update` / `modify` |
| 삭제 | `delete` / `remove` |

- `find` 계열은 "없을 수 있음"을 반환 타입(`Optional<T>`)으로 표현할 때 우선.
- `get` 계열은 **반드시 존재**하는 단건 조회에 사용(없으면 예외).
- `read` 계열은 목록/단건 일반 조회에 사용.

## 4) 엔티티

### 4-1) 생성자 접근 제한
- `@NoArgsConstructor(access = AccessLevel.PACKAGE)` 사용 (**package-private**).
- 기존 코드의 `AccessLevel.PROTECTED`는 점진적으로 `PACKAGE`로 마이그레이션한다. 추적: §7 오픈 항목.

### 4-2) 도메인 메서드
- 상태 전이는 반드시 **엔티티 내부의 도메인 메서드**로 캡슐화한다 (`softDelete()`, `markSent()` 등).
- Service에서 엔티티의 setter를 직접 호출하지 않는다. Setter는 원칙적으로 두지 않는다.

## 5) Lombok 사용 지침

### 5-1) 적극 사용 가능
- `@Getter` — 엔티티/DTO/도메인 객체
- `@NoArgsConstructor(access = AccessLevel.PACKAGE)` — JPA 엔티티
- `@RequiredArgsConstructor` — Service/Repository 주입
- `@Slf4j` — 로거

### 5-2) 주의해서 사용
- `@Data` — **금지**. equals/hashCode/setter가 함께 붙어 부작용이 크다.
- `@Setter` — 엔티티에는 **금지**. DTO에도 비권장(record 사용).
- `@AllArgsConstructor` — public이면 외부 결합 증가. 내부용으로만.
- `@Builder` — 생성자 파라미터가 많을 때만. 2~3개는 일반 생성자.

### 5-3) 미해결 항목
- `@ToString`, `@EqualsAndHashCode`의 사용 범위는 팀 내 추가 논의 필요 (§7).

## 6) null 검증

### 6-1) 두 레이어에서 모두 검증
- **DTO 레이어**: `@NotNull`, `@NotBlank`, `@Valid` 등 Bean Validation 어노테이션으로 1차 차단.
- **Domain 레이어**: 엔티티/값 객체의 팩토리 메서드 또는 생성자에서 `Objects.requireNonNull(...)` 혹은 명시적 예외 throw로 2차 차단.

### 6-2) Domain 검증 원칙
> Domain에서는 **매개변수로 받은 모든 값에 대해 null 검증**을 수행한다.

```java
public static Member create(final String loginId, final String nickname) {
    Objects.requireNonNull(loginId, "loginId must not be null");
    Objects.requireNonNull(nickname, "nickname must not be null");
    return new Member(loginId, nickname);
}
```

- "DTO에서 검증했으니 Domain은 생략"은 허용하지 않는다. Domain 객체는 **자체 불변식을 스스로** 지켜야 한다.

## 7) 오픈 항목 (컨벤션 미결)

| # | 주제 | 상태 |
|---|---|---|
| C1 | 기존 엔티티의 `AccessLevel.PROTECTED` → `PACKAGE` 마이그레이션 | 후속 PR로 일괄 처리 |
| C2 | `@ToString`, `@EqualsAndHashCode` 허용 범위 | 논의 필요 |
| C3 | DTO 재활용 금지는 이번 스프린트 한정. 이후 스프린트 정책 재검토 시점 | 다음 스프린트 회고 |

## 8) 참고
- 테스트 컨벤션: `07-testing-guide.md`
- 도메인 모델: `06-domain-model.md`
- PR 워크플로우: `02-agent-workflow.md`
