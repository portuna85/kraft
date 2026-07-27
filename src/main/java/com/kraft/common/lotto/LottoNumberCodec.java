package com.kraft.common.lotto;

import com.kraft.common.error.ApiException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LottoNumberCodec {

    public String toStorageValue(List<Integer> numbers) {
        return normalize(numbers).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public String toStorageValueSubset(List<Integer> numbers) {
        return normalizeSubset(numbers).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public List<Integer> fromStorageValue(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(",")).map(Integer::parseInt).toList();
    }

    public List<Integer> normalize(List<Integer> numbers) {
        if (numbers == null || numbers.size() != 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "로또 번호는 정확히 6개여야 합니다.");
        }
        if (numbers.stream().anyMatch(n -> n == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "번호 목록에 null 값이 포함되어 있습니다.");
        }

        List<Integer> sorted = numbers.stream().sorted(Comparator.naturalOrder()).toList();
        for (int number : sorted) {
            if (number < 1 || number > 45) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "로또 번호는 1부터 45 사이여야 합니다.");
            }
        }
        long distinctCount = sorted.stream().distinct().count();
        if (distinctCount != 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "로또 번호는 중복될 수 없습니다.");
        }
        return sorted;
    }

    public List<Integer> normalizeSubset(List<Integer> numbers) {
        if (numbers == null) {
            return List.of();
        }
        if (numbers.stream().anyMatch(n -> n == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "번호 목록에 null 값이 포함되어 있습니다.");
        }
        List<Integer> sorted = numbers.stream().sorted(Comparator.naturalOrder()).toList();
        for (int number : sorted) {
            if (number < 1 || number > 45) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "로또 번호는 1부터 45 사이여야 합니다.");
            }
        }
        long distinctCount = sorted.stream().distinct().count();
        if (distinctCount != sorted.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NUMBERS", "로또 번호는 중복될 수 없습니다.");
        }
        return sorted;
    }
}
