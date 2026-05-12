package com.bvb.sharedmodule.service.request;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PageRequestHelper {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    public static Pageable of(int page, int size, String sortBy, String sortDir) {
        size = Math.min(size, MAX_SIZE); // maximum
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        return PageRequest.of(page, size, sort);
    }

    public static Pageable of(int page, int size) {
        size = Math.min(size, MAX_SIZE);
        return PageRequest.of(page, size, Sort.by("createdAt").descending());
    }

    public static Pageable ofDefault() {
        return PageRequest.of(DEFAULT_PAGE, DEFAULT_SIZE,
                Sort.by("createdAt").descending());
    }
}