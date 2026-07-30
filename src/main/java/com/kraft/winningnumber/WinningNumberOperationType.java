package com.kraft.winningnumber;

// KB-17: operationlog에 있던 시절 winningnumber→operationlog 순환의 한 변을 이뤘다 —
// 이 값은 "무엇에 대한 로그인가"를 나타내는 winningnumber 도메인 어휘라 이 패키지가
// 소유하는 게 맞다. operationlog는 로그를 남길 때 이 타입을 가져다 쓰기만 한다.
public enum WinningNumberOperationType {
    EXTERNAL_COLLECT,
    MANUAL_UPSERT
}
