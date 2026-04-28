package org.ticketing.queue.domain.model;

public enum AcquireResult {
    SUCCESS,          //  1: 슬롯+토큰 선점 성공
    NO_SLOT,          // -1: 슬롯 없음
    NOT_INITIALIZED,  // -2: 슬롯 미초기화
    PENDING,          // -3: 다른 스레드 발급 중
    ALREADY_ISSUED    // -4: 이미 발급 완료
}
