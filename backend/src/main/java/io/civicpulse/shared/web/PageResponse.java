package io.civicpulse.shared.web;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(List<T> content, PageMetadata page) {

    public static <T> PageResponse<T> of(List<T> content, int number, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(List.copyOf(content), new PageMetadata(number, size, totalElements, totalPages));
    }

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return of(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    public record PageMetadata(int number, int size, long totalElements, int totalPages) {
    }
}
