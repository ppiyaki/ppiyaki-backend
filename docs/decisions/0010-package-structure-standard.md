---
id: 0010
title: 패키지 구조 표준 — infrastructure 분리 / mcp 루트 / domain 서브폴더 / 명명 규칙
status: proposed
date: 2026-05-14
deciders: [@goohong]
---

# 0010. 패키지 구조 표준 — infrastructure 분리 / mcp 루트 / domain 서브폴더 / 명명 규칙

## Context

현재 구조는 `com.ppiyaki.<domain>` 도메인 우선 + 도메인 내부 `controller/service/repository` layered hybrid. 8개 도메인 + `common/` 12 sub-package로, 프로젝트 규모(1 백엔드 개발자 + AI, 모놀리스)에 합리적이지만 다음 마찰점이 누적 중이다.

1. **`common/`이 cross-cutting과 외부 어댑터를 같이 담는다**
   - cross-cutting: `auth` `exception` `entity` `logging` `ratelimit` `config`
   - 외부 어댑터: `ai` `druginfo` `mfds` `ocr` `storage`
   - 둘은 의존 방향이 다르다(전자는 모두가 의존, 후자는 도메인이 호출). 같은 폴더에 두면 *common 잡탕화*를 막을 신호가 없다.

2. **`common/mcp/`가 도메인 경계를 가로지른다**
   - `MedicationMcpTools` `MedicineMcpTools` 등이 자기 도메인 서비스를 호출한다.
   - `common → domain` 방향 의존이라 일반적으로 금기되는 패턴. 위치가 의존 방향을 오도한다.

3. **도메인 루트에 클래스가 누적된다**
   - `medication/` 루트 12개(엔티티 3 + enum 8 + 파서 1), `user/` 루트 10개.
   - Aggregate Root / Value Object / Domain Service 구분이 디렉토리로 안 보임.

4. **도메인 내부 sub-layer 명명이 비일관**
   - 변형: `chat/domain/`, `notification/push/`, `medicine/scheduler/`, `medication/event/`.
   - 각 케이스마다 합리적 사유는 있으나, "어디에 뭐가 있는지" 예측이 안 된다.

이번 ADR은 *4건 모두*의 표준을 한 번에 정한다. 실제 코드 이동은 별도 PR로 진행한다.

## Decision

### A. `infrastructure/` 루트 신설

`common/`을 *cross-cutting plumbing*으로 좁히고, 외부 시스템 어댑터는 `infrastructure/`로 분리한다.

```
com.ppiyaki/
├── <domain>/ ...
├── infrastructure/        ← NEW
│   ├── ai/                (common/ai에서 이주)
│   ├── druginfo/          (common/druginfo)
│   ├── mfds/              (common/mfds)
│   ├── ocr/               (common/ocr)
│   ├── storage/           (common/storage)
│   └── messaging/
│       └── fcm/           (notification/push에서 이주, 더 일반화 가능 시 sms/, email/)
└── common/                ← 좁힘
    ├── auth/
    ├── config/
    ├── entity/
    ├── exception/
    ├── logging/
    └── ratelimit/
```

판정 기준:
- *외부 시스템과 통신*하거나 *외부 SDK를 캡슐화*하면 → `infrastructure/`
- *모든 도메인이 의존하는 기술적 뼈대*면 → `common/`

### B. `mcp/`를 루트로 승격

`common/mcp/`를 `com.ppiyaki.mcp/`로 옮긴다. MCP tools는 도메인 서비스를 호출하는 *application-tool*에 가까우므로, common(아래)에 두면 의존 방향이 역전된다. 루트에 두면 도메인 → mcp 의존 또는 mcp → 도메인 의존이 자연스럽게 표현된다.

대안인 *도메인별 분산*(`medication/mcp/`, `medicine/mcp/`)은 검토 후 기각: MCP는 "서비스를 LLM에 노출"이라는 횡단 관심사이며, 한 곳에서 노출 표면을 점검할 수 있는 게 운영상 유리.

### C. 도메인이 비대해지면 `domain/` 서브폴더 도입

루트가 8개 클래스를 넘으면 `<domain>/domain/`을 만들어 엔티티/Value Object/Domain Service를 이주한다. 즉시 대상은 `medication/` `user/`. 다른 도메인은 임계 도달 시 처리.

```
medication/
├── controller/
├── service/
├── repository/
├── event/
└── domain/                ← NEW
    ├── MedicationSchedule.java   (엔티티)
    ├── MedicationLog.java
    ├── MedicationReminder.java
    ├── MealSlot.java             (enum/VO)
    ├── LogStatus.java
    ├── DayStatus.java
    ├── SlotStatus.java
    ├── DeliveryStatus.java
    ├── ReminderChannel.java
    ├── LogAiStatus.java
    ├── DosageUnit.java
    └── DosageParser.java         (도메인 서비스/유틸)
```

### D. 도메인 내부 sub-layer 명명 표준

다음 명명만 허용한다(필요할 때만 추가):

| 폴더명 | 용도 | 예 |
|---|---|---|
| `controller/` | Spring `@RestController`. DTO는 `controller/dto/` 하위 | 필수 |
| `service/` | application/도메인 서비스 (Spring `@Service`) | 필수 |
| `repository/` | Spring Data 인터페이스 + 사용자 정의 구현 | 필수 (대부분) |
| `domain/` | 엔티티/VO/도메인 enum/도메인 서비스 (정책: §C) | 선택 |
| `event/` | 도메인 이벤트 발행/구독, ApplicationEventPublisher payload 클래스 | 선택 |
| `scheduler/` | `@Scheduled` 빈, 배경 작업 | 선택 |

금지(또는 이전):
- `push/` `messaging/` 같은 *채널 어댑터*는 `infrastructure/messaging/<provider>/`로
- `<domain>/<external>/` 같은 외부 통합 — `infrastructure/`로

## Consequences

### 긍정적

- **의존 방향 가시화**: `infrastructure/` 분리로 outbound 의존이 명시됨. 도메인 → infrastructure 한 방향, 그 반대 금지를 정적 분석/ArchUnit으로 강제할 여지 생김.
- **common 잡탕화 차단**: cross-cutting만 남아 폴더 수가 줄고 역할이 명확.
- **MCP 의존 방향 정상화**: mcp → 도메인 방향이 디렉토리로 표현됨.
- **도메인 루트의 시각적 노이즈 감소**: medication/ user/는 즉시 정리, 나머지는 임계 도달 시.
- **명명 표준이 PR review에서 자동 지적 대상**: 임의 폴더 추가가 발생하면 reviewer가 ADR을 가리키며 합의 요구.

### 부정적

- **단발성 이주 PR 3건이 필요**(infrastructure/, mcp/, domain/). 각 PR이 import 30~50개 줄을 수정 → 다른 작업 중인 브랜치와 충돌 가능.
- **`scope:` 라벨 화이트리스트는 그대로 유지**: 새 폴더가 생긴다고 라벨이 늘지 않는다. `infrastructure/` 관련 변경은 `scope:infra`로 묶음 처리.
- **`infrastructure/`라는 이름의 길이**: import 경로가 길어진다. acceptable trade-off.
- **`domain/` 서브폴더가 모든 도메인에 강제되진 않음**: 임계 기준이 *느슨한 룰*이라 PR 시점 판단 필요.

## Alternatives (considered)

- **(A) 모두 `common/` 유지** — 잡탕화가 이미 시작됐고 12 sub-package에서 더 늘어나면 폴더 자체가 의미를 잃는다. 채택 안 함.
- **(B) `infrastructure/` 대신 `adapters/`(헥사고날 용어)** — 현 코드가 ports/adapters 분리를 완전히 따르지 않아 적용 시 오해 소지. *나중에* 헥사고날로 진화할 여지를 보존하고자 더 일반적인 `infrastructure/`를 채택.
- **(C) MCP를 도메인별 분산** — 한 곳에서 LLM 노출 표면 관리가 어려워짐. §B 본문 참고.
- **(D) `domain/` 서브폴더 모든 도메인에 강제** — 작은 도메인(`pet/`, `chat/`)에는 과한 형식. 임계 기준 도입으로 절충.

## References

- 분석 출처: 2026-05-14 패키지 구조 장단점 분석 (세션 노트)
- 관련 후속 이슈:
  - mcp 루트 이주
  - infrastructure/ 신설 및 어댑터 이주
  - medication/ domain 서브폴더 도입
  - user/ domain 서브폴더 도입
- 관련 문서: `docs/ai-harness/06-domain-model.md` §3 컨텍스트 맵
