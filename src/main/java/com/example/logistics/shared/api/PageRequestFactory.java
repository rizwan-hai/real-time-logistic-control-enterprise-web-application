package com.example.logistics.shared.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Set;

public final class PageRequestFactory {
    private PageRequestFactory() {}
    public static Pageable create(int page, int size, String sort, Set<String> allowedFields) {
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String[] parts = sort == null || sort.isBlank() ? new String[]{"createdAt", "desc"} : sort.split(",", -1);
        if (parts.length > 2 || !allowedFields.contains(parts[0])) throw new IllegalArgumentException("sort field is not allowed");
        Sort.Direction direction;
        try { direction = parts.length == 1 ? Sort.Direction.ASC : Sort.Direction.fromString(parts[1]); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("sort direction must be asc or desc"); }
        return PageRequest.of(page, size, Sort.by(direction, parts[0]));
    }
}
