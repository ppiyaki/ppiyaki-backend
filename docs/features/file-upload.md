---
feature: 파일 업로드 인프라
slug: file-upload
status: approved
owner: @goohong
scope: infra
related_issues: []
related_prs: []
last_reviewed: 2026-04-15
---

# 파일 업로드 인프라

## 1) 개요 (What / Why)
- 처방전 이미지, 복약 확인 사진, 프로필 이미지 등 **바이너리 파일을 서버 외부 스토리지(NCP Object Storage)에 안전하게 업로드**하는 공통 인프라.
- 백엔드는 **presigned URL을 발급**하고, 클라이언트가 직접 스토리지에 PUT 업로드 → 백엔드 대역폭·메모리 부담 제거.
- 후속 기능(처방전 OCR, 약 개수 인식, 삐약이 이미지, 프로필)의 선결 인프라.

## 2) 사용자 시나리오
- **시니어**는 처방전을 등록하기 위해 사진을 찍은 뒤, 앱이 발급받은 presigned URL로 원본 이미지를 업로드하고, 업로드된 URL을 처방전 등록 API에 전달한다.
- **시니어**는 약을 복용했음을 증빙하기 위해 약 개수가 보이는 사진을 찍고, 같은 방식으로 업로드한 뒤 복약 기록 API에 사진 URL을 전달한다.
- **보호자**는 온보딩 시 프로필 이미지를 업로드한다.

## 3) 요구사항

### 기능 요구사항
- [ ] 인증된 유저만 presigned URL을 발급받을 수 있다.
- [ ] presigned URL 발급 API는 업로드 용도(`purpose`)·파일 확장자(`extension`)·Content-Type을 받는다.
- [ ] 서버는 스토리지 내 유일한 `objectKey`를 생성(예: `{purpose}/{userId}/{uuid}.{ext}`)하여 presigned PUT URL을 반환한다.
- [ ] 응답에는 업로드 후 접근 가능한 최종 URL(또는 objectKey)을 함께 포함한다.
- [ ] presigned URL의 유효 시간은 5분으로 제한한다.
- [ ] 업로드 가능한 Content-Type은 화이트리스트(이미지 MIME 위주)로 제한한다.
- [ ] 업로드 가능한 최대 파일 크기 **10MB**를 presigned URL의 `content-length-range` 조건으로 강제한다.
- [ ] 잘못된 요청(허용 외 확장자/Content-Type, 누락 필드)은 `ErrorResponse` 포맷으로 400 반환.

### 비기능 요구사항
- **보안**: NCP 자격증명은 환경변수로만 관리. 절대 커밋 금지.
- **비용**: 버킷은 private(목록 비공개). 다운로드도 필요 시 별도 presigned GET URL 발급 (이번 스코프는 PUT만).
- **관측성**: presigned 발급 이벤트를 info 레벨 로그 (purpose, userId, objectKey)로 남김. 바이너리는 서버를 거치지 않으므로 실제 업로드 로그는 스토리지 액세스 로그로 확인.
- **테스트 용이성**: S3 호환 API를 사용하므로 LocalStack 또는 MinIO로 로컬 E2E 가능.

## 4) 범위 / 비범위

### 포함
- presigned PUT URL 발급 API (`POST /api/v1/uploads/presigned`)
- NCP Object Storage 연동 클라이언트(S3 호환, AWS SDK v2)
- 업로드 용도(`purpose`) enum 정의: `PRESCRIPTION`, `MEDICATION_LOG`, `PROFILE_IMAGE` (확장 가능)
- Content-Type 화이트리스트: `image/jpeg`, `image/png`, `image/webp` (초기)
- 환경변수 설계 및 `application.yml` 반영

### 제외 (Out of Scope)
- 이미지 **다운로드 API / presigned GET URL** — 당장은 객체 URL 직접 반환으로 충분. 민감 이미지 접근 제어는 후속 이슈에서 다룸.
- **이미지 리사이즈/썸네일** — 후속 인프라.
- **바이러스/악성 파일 스캔** — 후속.
- **업로드 완료 콜백/웹훅** — 당장은 클라이언트가 비즈니스 API(처방전 등록 등)에 objectKey를 전달하는 플로우로 충분.
- **서버를 경유하는 멀티파트 업로드** — 성능/비용 불리. presigned 전용.
- 다른 클라우드 마이그레이션 추상화 — NCP(S3 호환)로 시작하고, 필요 시 후속 ADR.

## 5) 설계

### 5-1) 도메인 모델
- 새 엔티티 없음. 업로드 결과 메타데이터(objectKey, url)는 각 도메인 엔티티가 필드로 저장(`prescriptions.image_url`, `medication_logs.photo_url` 등).
- 공통 `UploadPurpose` enum만 `common` 패키지 또는 `infra` 패키지에 신설.

### 5-2) API 엔드포인트

| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| POST | `/api/v1/uploads/presigned` | presigned PUT URL 발급 | 필수 | `PresignedUploadRequest` | `PresignedUploadResponse` |

**`PresignedUploadRequest`** (record):
```java
@NotNull UploadPurpose purpose
@NotBlank @Pattern(regexp = "jpg|jpeg|png|webp") String extension
@NotBlank String contentType
```

**`PresignedUploadResponse`** (record):
```java
String objectKey       // "prescription/42/1a2b3c...jpg" — 비즈니스 API에 전달하는 식별자
String presignedUrl    // PUT 용 URL (5분 유효)
Instant expiresAt
```
> 버킷이 private이므로 직접 접근 가능한 public URL은 응답하지 않는다. 조회는 후속 이슈의 presigned GET API를 통한다.

### 5-3) 외부 연동
- **NCP Object Storage** (S3 호환 엔드포인트: `https://kr.object.ncloudstorage.com`)
- 라이브러리: `software.amazon.awssdk:s3` (S3 호환 API 지원)
- 자격증명: `NCP_ACCESS_KEY`, `NCP_SECRET_KEY` 환경변수
- 버킷: `NCP_BUCKET_NAME` (환경별 별도 버킷 예정: `ppiyaki-dev`, `ppiyaki-prod`. **초기 연결은 prod만** — Q1 결정 로그 참조)
- 실패 처리:
  - 자격증명 오류: 부트 시 fail-fast (`@Validated` + `@NotBlank` on properties record)
  - 발급 시 스토리지 호출 실패: 500 `INTERNAL_SERVER_ERROR` + error 로그

### 5-4) 데이터 흐름

```
client                         backend                      NCP Object Storage
  |                               |                                 |
  |-- POST /uploads/presigned --->|                                 |
  |   { purpose, ext, contentType}|                                 |
  |                               |-- generate objectKey             |
  |                               |-- S3Presigner.presignPutObject-->|
  |                               |<---- presigned URL --------------|
  |<--- { presignedUrl, objectKey,|                                 |
  |       publicUrl, expiresAt }  |                                 |
  |                                                                  |
  |-- PUT presignedUrl (binary) ------------------------------------>|
  |<-- 200 OK ------------------------------------------------------|
  |                                                                  |
  |-- POST /prescriptions {objectKey} -> backend (별도 기능) ------>|
```

### 5-5) DB 마이그레이션
- 없음. 기존 엔티티의 `*_url` 컬럼을 그대로 사용.

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1 `chore(infra)`: AWS SDK v2 의존성 추가, `NcpStorageProperties`(@Validated), `application.yml` 환경변수 바인딩 + dev/prod 프로파일 샘플
- [ ] PR 2 `feat(infra)`: `S3Presigner` 빈 구성, `UploadService`, `UploadController` (`POST /api/v1/uploads/presigned`), `UploadPurpose` enum, DTO, ErrorCode 추가
- [ ] PR 3 `test(infra)`: LocalStack 또는 MinIO 기반 E2E (presigned 발급 → 실제 PUT → GET 확인). 불가 시 서비스 단위 테스트 + `S3Presigner` mock으로 Controller WebMvcTest

## 7) 테스트 전략
- **단위**: `UploadService` — objectKey 생성 규칙, 허용 Content-Type 검증
- **Controller 슬라이스**: `@WebMvcTest` + `S3Presigner` mock → 200/400/401 케이스
- **E2E**: 가능하면 LocalStack 컨테이너 구동 (Testcontainers). 외부 실제 NCP 호출은 CI에서 지양.
- **응답 포맷**: 기존 `ErrorResponse` 일관성 (`AUTH_INVALID_TOKEN`, `INVALID_INPUT` 등 재사용)

## 8) 오픈 질문
> 해소된 질문은 §9 결정 로그로 이동.

(현재 없음)

## 9) 결정 로그
- 2026-04-15: 초안 작성 (status=draft). Feature Spec 선행 작성 후 구현 착수 원칙 적용.
- 2026-04-15: Q1 결정 — 환경별 버킷 분리(`ppiyaki-dev`, `ppiyaki-prod`). **초기 연결은 prod만**. dev 버킷은 스키마/정책 합의가 서면 이후 연결.
- 2026-04-15: Q2 결정 — 최대 파일 크기 **10MB**. presigned URL 조건에 `content-length-range` 제약으로 강제.
- 2026-04-15: Q3 결정 — 업로드 완료 **서버 검증 없이 신뢰**. 비즈니스 API(처방전 등록 등)에서 objectKey를 받았을 때 HEAD 확인하지 않음. 업로드 누락은 후속 기능 로직에서 예외 처리.
- 2026-04-15: Q4 결정 — 버킷 **private**. 업로드는 이번 스코프(PUT presigned), 조회용 **presigned GET 발급은 후속 이슈**로 분리. 의료정보/개인정보 접근 통제 근거는 `docs/ai-harness/04-security-policy.md`.
- 2026-04-15: Q5 결정 — **Service 단위 테스트 + Controller `@WebMvcTest` + `S3Presigner` mock**. LocalStack/Testcontainers 도입은 추후 재검토.
