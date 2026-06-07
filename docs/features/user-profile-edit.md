---
feature: 내 정보 수정 (프로필 사진 / 이름 / 성별)
slug: user-profile-edit
status: draft
owner: @goohong
scope: user
related_issues: []
related_prs: []
last_reviewed: 2026-06-03
---

# 내 정보 수정 (프로필 사진 / 이름 / 성별)

## 1) 개요 (What / Why)
- 시니어와 보호자가 자신의 프로필(프로필 사진, 이름)을 수정할 수 있게 한다.
- 프로필 사진은 **기본 프로필 사진(1~6) 선택** 또는 **사용자가 직접 업로드한 사진** 중 하나로 설정한다.
- 시니어 본인은 성별도 수정할 수 있다.
- 보호자는 연동된 시니어의 이름·성별을 대신 수정할 수 있다 (MANAGED 시니어 등 직접 앱을 쓰지 않는 시니어 대응).
- 현재 회원가입/온보딩 이후 프로필을 다시 바꿀 수 있는 API가 없어, 잘못 입력한 이름·성별이나 프로필 사진을 변경할 수단이 필요하다.

## 2) 사용자 시나리오
- 시니어는 마이페이지에서 기본 프로필 사진(1~6 중 하나)을 고르고, 이름과 성별을 바꾸기 위해 자신의 정보를 수정한다.
- 보호자는 마이페이지에서 자신의 프로필 사진과 이름을 바꾼다 (성별은 보호자 본인 항목에 없음).
- 보호자는 연동된 시니어의 정보 화면에서 시니어의 이름·성별을 대신 수정한다.

## 3) 요구사항
### 기능 요구사항
- [ ] 본인(시니어/보호자)이 자신의 프로필 사진과 이름을 수정할 수 있다.
- [ ] 프로필 사진은 기본 프사 인덱스(1~6) 또는 직접 업로드한 사진(presigned 업로드 후 objectKey 전달) 중 하나로 설정한다.
- [ ] 기본 프사와 커스텀 업로드는 상호 배타적이다 (둘 다 보내면 400).
- [ ] 커스텀 업로드 objectKey는 `profile-image/{본인userId}/{uuid}.{ext}` 형식 + 소유자 일치만 허용한다 (위반 시 400).
- [ ] 시니어 본인은 자신의 성별도 함께 수정할 수 있다.
- [ ] 보호자는 연동된 시니어의 이름과 성별을 수정할 수 있다 (시니어 프로필 사진은 대신 수정하지 않는다).
- [ ] 보호자-시니어 연동 관계(`CareRelation`)가 없으면 시니어 정보 수정은 거부한다 (403 CARE_001).
- [ ] 프로필 사진 인덱스는 1~6 범위만 허용한다 (범위 밖이면 400).
- [ ] 인증되지 않은 요청은 거부한다 (401).

### 비기능 요구사항
- 의료정보가 아닌 프로필 정보이므로 별도 마스킹 불필요. 단, 로그에 이름/성별 원문을 남기지 않는다.
- 기존 `PUT /me/meal-times`, `PUT /{seniorId}/meal-times` 패턴과 일관성 유지.

## 4) 범위 / 비범위 (중요)
### 포함
- 기본 프로필 사진 인덱스(1~6) 저장 컬럼(`profile_image`) 추가.
- 본인 프로필 수정 API, 보호자→시니어 프로필 수정 API.
- `GET /users/me` 응답에 `gender`, `profileImage` 노출 (수정 결과 확인용).

### 포함 (추가)
- 사용자 직접 업로드 프로필 사진 (기존 presigned 인프라 `UploadPurpose.PROFILE_IMAGE` 재사용). 기본 프사 6종 에셋은 클라이언트가 인덱스로 매핑.

### 제외 (Out of Scope)
- 신규 업로드 인프라 구축 (기존 presigned 그대로 사용).
- 업로드 이미지 리사이즈/썸네일/바이러스 스캔 (file-upload.md 비범위와 동일).
- 생년월일(`birthDate`) 수정.
- 보호자가 시니어의 프로필 사진을 대신 변경하는 기능 (요구사항 명세상 "이름, 성별"만 해당).

## 5) 설계
### 5-1) 도메인 모델
- `com.ppiyaki.user.domain.User` 엔티티에 두 필드 추가:
  - `profileImage`(Integer, 1~6, nullable) — 기본 프사 선택 인덱스
  - `profileImageObjectKey`(String, nullable) — 커스텀 업로드 사진 objectKey
  - 두 필드는 상호 배타 (둘 중 하나만 non-null, 도메인 불변식으로 강제).
- 이름은 기존 `nickname`, 성별은 기존 `gender`(`Gender` enum) 사용. 신규 용어 없음.
- 권한 판정은 기존 `CareRelationRepository.findByCaregiverIdAndSeniorIdAndDeletedAtIsNull` 재사용.
- 커스텀 업로드는 기존 presigned 인프라(`UploadPurpose.PROFILE_IMAGE`, prefix `profile-image`) 재사용. 응답의 presigned GET URL은 `PhotoUrlAssembler`로 변환하되, `UserService`는 코어 빈이므로 `ObjectProvider<PhotoUrlAssembler>`를 감싼 비조건부 `ProfileImageUrlResolver`를 통해 스토리지 미설정(local/test) 시 null 반환.

### 5-2) API 엔드포인트
| Method | Path | 설명 | 인증 | Req | Res |
|---|---|---|---|---|---|
| PUT | /api/v1/users/me | 본인 프로필 수정 (이름, 프로필 사진, [성별]) | 필수 | `ProfileUpdateRequest` | `UserMeResponse` |
| PUT | /api/v1/users/{seniorId} | 보호자가 연동 시니어 정보 수정 (이름, 성별) | 필수 | `SeniorProfileUpdateRequest` | `UserMeResponse` |

- `ProfileUpdateRequest(nickname, profileImage, profileImageObjectKey, gender)` — `nickname` `@NotBlank`, `profileImage` `@Min(1) @Max(6)`(nullable), `profileImageObjectKey` nullable, 둘은 `@AssertTrue`로 상호 배타 검증, `gender`는 선택(보호자 본인은 미전송, 시니어 본인은 전송).
- `SeniorProfileUpdateRequest(nickname, gender)` — 둘 다 필수.
- 커스텀 업로드 흐름: 클라이언트가 `POST /api/v1/uploads/presigned`(purpose=PROFILE_IMAGE)로 받은 objectKey로 NCP에 PUT 업로드 → 그 objectKey를 `PUT /me`에 전달.
- 응답 `UserMeResponse`에 `gender`, `profileImage`(인덱스), `profileImageUrl`(커스텀 사진 presigned GET URL) 노출.

### 5-3) 외부 연동
- NCP Object Storage (기존 presigned 업로드/다운로드 인프라 재사용). 신규 외부 연동 없음.

### 5-4) 데이터 흐름 / 시퀀스
- 본인 수정: `PUT /me` → `userId`로 본인 조회 → (objectKey 있으면 소유권/형식 검증) → nickname/profileImage 갱신, gender는 전달된 경우만 갱신.
- 시니어 수정: `PUT /{seniorId}` → seniorId 조회 → `CareRelation` 권한 검증 → nickname/gender 갱신.

### 5-5) DB 마이그레이션
- `users` 테이블에 컬럼 2개 추가: `profile_image int null`, `profile_image_object_key varchar(255) null`.
- prod는 `JPA_DDL_AUTO=validate`이므로 릴리즈 직전 다음 SQL 직접 실행 필요:
  ```sql
  ALTER TABLE users ADD COLUMN profile_image int NULL;
  ALTER TABLE users ADD COLUMN profile_image_object_key varchar(255) NULL;
  ```

## 6) 작업 분할 (예상 PR 리스트)
- [ ] PR 1: 프로필 수정 API 일괄 구현 (엔티티 컬럼 + DTO + 서비스 + 컨트롤러 + 테스트 + schema).

## 7) 테스트 전략
- 도메인 단위 테스트: `updateProfileImage`/`updateGender` null·정상 케이스.
- E2E (RestAssured) 필수: 본인 수정 성공, 시니어 수정 성공, 범위 밖 profileImage 400, 미인증 401, 관계 없는 사용자 403, 시니어 미존재 404.

## 8) 오픈 질문
> 없음 (아래 결정 로그로 해소).

## 9) 결정 로그
- 2026-06-03: 초안 작성 (status=draft).
- 2026-06-03: 프로필 사진은 정수 인덱스(1~6) 저장. 본인/시니어 수정 통합 엔드포인트(`PUT /me`, `PUT /{seniorId}`). 성별은 시니어 본인만 수정(보호자 본인 미해당, 자기 record라 선택 필드로 수용). / 사용자 확인.
