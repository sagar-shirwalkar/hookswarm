package dev.hookswarm.common;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PagedResponse<T> of(List<T> content, int page, int size, long totalElements) {

        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        return new PagedResponse<>(content, page, size, totalElements, totalPages);

    }

}