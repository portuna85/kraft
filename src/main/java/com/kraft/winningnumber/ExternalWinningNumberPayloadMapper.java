package com.kraft.winningnumber;

import com.kraft.common.error.ApiErrorCode;
import com.kraft.common.error.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ExternalWinningNumberPayloadMapper {

    // Parses both the old common.do format and the new lt645/selectPstLt645InfoNew.do item format.
    // KB-18: Map<?,?>로 읽기만 하므로 unchecked 캐스트가 필요 없다.
    public WinningNumberUpsertRequest toRequest(Map<?, ?> payload) {
        String returnValue = asString(payload.get("returnValue"));
        if (returnValue != null && !returnValue.isBlank() && !"success".equalsIgnoreCase(returnValue)) {
            throw new ApiException(ApiErrorCode.LOTTO_SOURCE_ROUND_NOT_FOUND, "외부 응답이 성공 상태가 아닙니다(회차 미공개로 간주).");
        }

        Integer round = asInteger(firstOf(payload, "ltEpsd", "round", "drwNo"));
        String drawDate = normalizeDate(asString(firstOf(payload, "ltRflYmd", "drawDate", "drwNoDate")));
        Integer bonusNumber = asInteger(firstOf(payload, "bnsWnNo", "bonusNumber", "bnusNo"));
        Long firstPrizeAmount = asLong(firstOf(payload, "rnk1WnAmt", "firstPrizeAmount", "firstWinamnt", "firstWinAmount"));

        List<Integer> numbers = extractNumbers(payload);

        if (round == null || drawDate == null || bonusNumber == null || firstPrizeAmount == null) {
            throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR, "외부 응답 필드가 누락되었습니다.");
        }

        Long secondPrize = asLong(firstOf(payload, "rnk2WnAmt", "secondPrize", "secondWinamnt"));
        Integer secondWinners = asInteger(firstOf(payload, "rnk2WnNope", "secondWinners", "secondPrzwnerCo"));
        Long totalSales = asLong(firstOf(payload, "rlvtEpsdSumNtslAmt", "totalSales", "totSellamnt"));
        Long firstAccumAmount = asLong(firstOf(payload, "rnk1SumWnAmt", "firstAccumAmount", "firstAccumamnt"));

        return new WinningNumberUpsertRequest(
                round,
                LocalDate.parse(drawDate),
                numbers,
                bonusNumber,
                firstPrizeAmount,
                secondPrize,
                secondWinners,
                totalSales,
                firstAccumAmount
        );
    }

    private List<Integer> extractNumbers(Map<?, ?> payload) {
        Object directNumbers = payload.get("numbers");
        if (directNumbers instanceof List<?> values) {
            List<Integer> result = values.stream().map(this::asInteger).toList();
            validateNumbers(result);
            return result;
        }
        // New format: tm1WnNo - tm6WnNo
        if (payload.containsKey("tm1WnNo")) {
            return requireNumbers(
                    asInteger(payload.get("tm1WnNo")),
                    asInteger(payload.get("tm2WnNo")),
                    asInteger(payload.get("tm3WnNo")),
                    asInteger(payload.get("tm4WnNo")),
                    asInteger(payload.get("tm5WnNo")),
                    asInteger(payload.get("tm6WnNo"))
            );
        }
        // Old format: drwtNo1 - drwtNo6
        return requireNumbers(
                asInteger(payload.get("drwtNo1")),
                asInteger(payload.get("drwtNo2")),
                asInteger(payload.get("drwtNo3")),
                asInteger(payload.get("drwtNo4")),
                asInteger(payload.get("drwtNo5")),
                asInteger(payload.get("drwtNo6"))
        );
    }

    private List<Integer> requireNumbers(Integer... nums) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] == null) {
                throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR,
                        "당첨 번호 " + (i + 1) + "번 필드가 누락되었습니다.");
            }
            result.add(nums[i]);
        }
        return Collections.unmodifiableList(result);
    }

    // KB-18: "numbers" 직접 리스트 분기는 개수를 스스로 보장하지 않는다 — drwtNo1..6/tm1WnNo..6
    // 분기는 varargs 6개 고정이라 구조적으로 항상 6개지만, 이 분기는 상류가 빈 배열이나
    // 5/7개짜리 배열을 보내도 그대로 통과시켜 왔다. 이 어댑터 밖(WinningNumberCommandService의
    // Bean Validation)에서야 잡히던 것을 여기서 먼저 명시적으로 끊는다.
    private void validateNumbers(List<Integer> numbers) {
        if (numbers.size() != 6) {
            throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR,
                    "당첨 번호는 정확히 6개여야 합니다 (실제 " + numbers.size() + "개).");
        }
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) == null) {
                throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR,
                        "당첨 번호 목록에 null 값이 포함되어 있습니다 (index " + i + ").");
            }
        }
    }

    // Converts YYYYMMDD (new API) to YYYY-MM-DD; passes through YYYY-MM-DD as-is.
    private String normalizeDate(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.length() == 8 && !raw.contains("-")) {
            return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8);
        }
        return raw;
    }

    private Object firstOf(Map<?, ?> payload, String... keys) {
        for (String key : keys) {
            if (payload.containsKey(key)) {
                return payload.get(key);
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR, "숫자 변환 실패: " + value);
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ApiErrorCode.LOTTO_SOURCE_PARSE_ERROR, "숫자 변환 실패: " + value);
        }
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
