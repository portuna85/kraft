package com.kraft.winningnumber;

/**
 * KB-17: {@link WinningNumberCollectionService}가 수집 시도 결과를 기록하고 싶을 때
 * 발행하는 이벤트 — operationlog를 직접 호출하던 것(winningnumber→operationlog 순환의
 * 한 변)을 역전해, winningnumber는 "무슨 일이 있었는지"만 발행하고 operationlog가
 * 구독해서 자신의 저장 방식을 스스로 결정한다.
 */
public record WinningNumberCollectionLoggedEvent(
        WinningNumberOperationType operationType,
        boolean success,
        Integer round,
        String sourceDetail,
        String message) {
}
