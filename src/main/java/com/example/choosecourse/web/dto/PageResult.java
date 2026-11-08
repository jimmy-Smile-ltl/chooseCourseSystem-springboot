package com.example.choosecourse.web.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量分页结果，避免为了一个列表引入 Spring Data。
 */
public class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int pageSize;
    private final int totalItems;
    private final int totalPages;
    private final List<Integer> pageNumbers;

    public PageResult(List<T> items, int page, int pageSize, int totalItems) {
        this.items = List.copyOf(items);
        this.pageSize = Math.max(1, pageSize);
        this.totalItems = Math.max(0, totalItems);
        this.totalPages = Math.max(1,
                (int) Math.ceil(this.totalItems / (double) this.pageSize));
        this.page = Math.min(Math.max(1, page), this.totalPages);

        int start = Math.max(1, this.page - 2);
        int end = Math.min(this.totalPages, start + 4);
        start = Math.max(1, end - 4);
        List<Integer> numbers = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        this.pageNumbers = List.copyOf(numbers);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public List<Integer> getPageNumbers() {
        return pageNumbers;
    }

    public boolean isHasPrevious() {
        return page > 1;
    }

    public boolean isHasNext() {
        return page < totalPages;
    }

    public int getFromItem() {
        return totalItems == 0 ? 0 : (page - 1) * pageSize + 1;
    }

    public int getToItem() {
        return Math.min(totalItems, page * pageSize);
    }
}
