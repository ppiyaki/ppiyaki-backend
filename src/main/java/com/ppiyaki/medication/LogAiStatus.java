package com.ppiyaki.medication;

public enum LogAiStatus {

    /** Vision 추출 개수 == dosage 합 */
    COUNT_MATCH,

    /** 추출 개수 != 합 */
    COUNT_MISMATCH,

    /** dosage 파싱 실패 등으로 검증 불가 */
    COUNT_UNKNOWN,

    /** Vision API 호출 실패 (timeout, 5xx 등) */
    COUNT_FAILED
}
