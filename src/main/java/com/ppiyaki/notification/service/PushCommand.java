package com.ppiyaki.notification.service;

import com.ppiyaki.infrastructure.messaging.fcm.PushPayload;

/**
 * 트랜잭션 <b>커밋 이후</b> 발송할 FCM 푸시 1건의 명령 객체.
 *
 * <p>알림함 record 저장(내구성 보장, 트랜잭션 안)과 FCM 발송(best-effort, 트랜잭션 밖)을 분리하기 위해,
 * 디스패처는 트랜잭션 안에서 이 명령만 만들어 반환하고 실제 발송은 relay가 커밋 뒤에 수행한다.
 *
 * <p>{@code deviceTokenId}는 발송 결과가 invalid token일 때 트랜잭션 밖에서 detach된 엔티티의
 * dirty checking에 의존하지 않고, 별도의 짧은 트랜잭션에서 id로 다시 조회해 비활성화하기 위한 식별자다.
 *
 * @param deviceTokenId 발송 대상 {@link com.ppiyaki.notification.DeviceToken}의 PK
 * @param deviceToken   FCM 등록 토큰
 * @param caregiverId   수신 보호자 id (로깅·추적용)
 * @param payload       발송할 푸시 페이로드
 */
public record PushCommand(
        Long deviceTokenId,
        String deviceToken,
        Long caregiverId,
        PushPayload payload
) {
}
