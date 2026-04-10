# API 에러 코드 목록

> 프론트엔드 팀 참고용. 모든 에러 응답은 아래 양식을 따른다.

## 응답 양식

```json
{
  "success": false,
  "error": {
    "code": "MEDICINE_001",
    "status": 404,
    "message": "Medicine not found"
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 항상 `false` |
| `error.code` | String | 에러 식별 코드 (`SCOPE_NNN` 형식) |
| `error.status` | int | HTTP 상태 코드 |
| `error.message` | String | 사람이 읽을 수 있는 에러 메시지 |

## 에러 코드

### Common
| 코드 | HTTP | 메시지 | 설명 |
|---|---|---|---|
| `COMMON_001` | 400 | Invalid input | 요청 파라미터 검증 실패 |

### Auth
| 코드 | HTTP | 메시지 | 설명 |
|---|---|---|---|
| `AUTH_001` | 401 | Invalid token | 유효하지 않은 토큰 |
| `AUTH_002` | 401 | Token expired | 만료된 토큰 |

### User
| 코드 | HTTP | 메시지 | 설명 |
|---|---|---|---|
| `USER_001` | 404 | User not found | 존재하지 않는 사용자 |

### Medicine
| 코드 | HTTP | 메시지 | 설명 |
|---|---|---|---|
| `MEDICINE_001` | 404 | Medicine not found | 존재하지 않는 약물 |

### Care Relation
| 코드 | HTTP | 메시지 | 설명 |
|---|---|---|---|
| `CARE_001` | 403 | No active care relation | 보호자-시니어 연동 관계 없음 |
| `CARE_002` | 403 | Caregiver must specify seniorId | 보호자는 seniorId 필수 |
| `CARE_003` | 403 | Only caregivers can specify seniorId | 시니어는 seniorId 지정 불가 |

## 프론트엔드 처리 가이드

```javascript
// 에러 응답 처리 예시
const response = await fetch('/api/v1/medicines', { ... });

if (!response.ok) {
  const body = await response.json();
  
  switch (body.error.code) {
    case 'AUTH_001':
    case 'AUTH_002':
      // 토큰 갱신 시도 또는 로그인 화면 이동
      break;
    case 'MEDICINE_001':
      // "약물을 찾을 수 없습니다" 알림
      break;
    case 'CARE_001':
    case 'CARE_002':
    case 'CARE_003':
      // 권한 없음 안내
      break;
    default:
      // body.error.message로 일반 에러 표시
  }
}
```

## 코드 추가 규칙

새 에러 코드를 추가할 때:
1. `ErrorCode.java` enum에 추가 (`SCOPE_NNN` 형식)
2. 이 문서의 해당 scope 섹션에 추가
3. 같은 PR에서 코드와 문서를 함께 갱신
