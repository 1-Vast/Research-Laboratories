package com.labflow.shared.api;

import java.util.List;

/** 服务端分页结果；page 从 1 开始。 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        return new PageResult<>(items, total, page, size);
    }

    public <R> PageResult<R> map(java.util.function.Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), total, page, size);
    }
}
