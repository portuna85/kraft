import { validateLottoNumbers } from "./lotto-validation";
import type {
  ManualEntryField,
  ManualEntryForm,
  WinningNumberUpsertBody,
} from "./ops-types";

export type ManualEntryValidation =
  | { ok: true; body: WinningNumberUpsertBody }
  | { ok: false; field: ManualEntryField; message: string };

/**
 * FE-074: 이전에는 검증이 전혀 없어 빈 회차가 `Number("") === 0`으로 전송되고, 번호를
 * 3개만 적어도 요청이 나갔다. 운영 데이터를 직접 쓰는 화면이라 잘못된 값이 서버에 닿기
 * 전에 막는다. 최종 판정은 여전히 서버가 한다 — 여기서는 명백히 틀린 입력만 걸러
 * 사용자에게 이유를 알린다.
 *
 * FE-078: React 밖의 순수 함수라 폼을 렌더하지 않고도 규칙만 검증할 수 있다.
 */
export function validateManualEntry(form: ManualEntryForm): ManualEntryValidation {
  const round = Number(form.round);
  if (!form.round.trim() || !Number.isInteger(round) || round < 1) {
    return { ok: false, field: "round", message: "회차는 1 이상의 정수여야 합니다." };
  }
  if (!form.drawDate.trim()) {
    return { ok: false, field: "drawDate", message: "추첨일을 입력하세요." };
  }

  // 쉼표·공백 어느 쪽으로 구분해도 받되, 빈 토큰을 걸러낸 뒤 개수를 그대로 검증에 넘긴다.
  // (NaN을 필터링해 버리면 "3, 어, 19"가 2개로 줄어 개수 오류가 엉뚱하게 보고된다.)
  const rawNumbers = form.numbers.split(/[,\s]+/).map((item) => item.trim()).filter((item) => item.length > 0);
  const numbersResult = validateLottoNumbers(rawNumbers);
  if (!numbersResult.ok) {
    return { ok: false, field: "numbers", message: numbersResult.message };
  }

  const bonusNumber = Number(form.bonusNumber);
  if (!form.bonusNumber.trim() || !Number.isInteger(bonusNumber) || bonusNumber < 1 || bonusNumber > 45) {
    return { ok: false, field: "bonusNumber", message: "보너스 번호는 1에서 45 사이의 정수여야 합니다." };
  }
  if (numbersResult.numbers.includes(bonusNumber)) {
    return { ok: false, field: "bonusNumber", message: "보너스 번호는 당첨 번호 6개와 달라야 합니다." };
  }

  const firstPrizeAmount = Number(form.firstPrizeAmount);
  if (!form.firstPrizeAmount.trim() || !Number.isFinite(firstPrizeAmount) || firstPrizeAmount < 0) {
    return { ok: false, field: "firstPrizeAmount", message: "1등 당첨금은 0 이상의 숫자여야 합니다." };
  }

  return {
    ok: true,
    body: { round, drawDate: form.drawDate, numbers: numbersResult.numbers, bonusNumber, firstPrizeAmount },
  };
}
