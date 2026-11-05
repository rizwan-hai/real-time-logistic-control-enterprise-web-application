package com.example.logistics.shared.api;

import org.springframework.data.domain.Page;
import java.util.List;

public record ApiPage<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean hasNext) {
    public static <T> ApiPage<T> from(Page<T> source) {
        return new ApiPage<>(source.getContent(), source.getNumber(), source.getSize(), source.getTotalElements(),
                source.getTotalPages(), source.hasNext());
    }
}
