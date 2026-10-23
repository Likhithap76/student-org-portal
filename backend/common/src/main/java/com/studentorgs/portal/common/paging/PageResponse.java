package com.studentorgs.portal.common.paging;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Paged list returned by every list endpoint. {@code page} is zero-based. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
