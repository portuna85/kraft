/**
 * `/ops` 운영 화면이 다루는 데이터 모양.
 *
 * FE-078: 타입·검증·전송·UI가 한 파일(304줄)에 섞여 있어, 운영 화면을 조금 고치려 해도
 * 무관한 로직까지 함께 읽어야 했다. 검증 누락(FE-074)이 오래 눈에 띄지 않은 구조적 배경이다.
 */

export type OpsSummary = {
  service: string;
  timezone: string;
  status: string;
  latestRound: number | null;
  latestDrawDate: string | null;
  checkedAt: string;
  fresh: boolean;
};

export type WinningNumber = {
  round: number;
  drawDate: string;
  numbers: number[];
  bonusNumber: number;
  firstPrizeAmount: number;
};

/** 수동 적재 폼의 원시 입력값. 전부 문자열이라 검증 전에는 신뢰하지 않는다. */
export type ManualEntryForm = {
  round: string;
  drawDate: string;
  numbers: string;
  bonusNumber: string;
  firstPrizeAmount: string;
};

export const initialManualEntryForm: ManualEntryForm = {
  round: "",
  drawDate: "",
  numbers: "",
  bonusNumber: "",
  firstPrizeAmount: "",
};

export type ManualEntryField = keyof ManualEntryForm;

export type WinningNumberUpsertBody = {
  round: number;
  drawDate: string;
  numbers: number[];
  bonusNumber: number;
  firstPrizeAmount: number;
};
