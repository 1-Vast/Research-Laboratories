package com.labflow.shared.api;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;

import java.util.Locale;
import java.util.Set;

/**
 * 列表排序参数：只接受白名单字段，避免把前端字符串直接拼进排序（注入/异常风险）。
 * 支持 `field` 或 `field,asc|desc`，缺省升序。
 */
public record SortSpec(String field, boolean ascending) {

    public static SortSpec parse(String raw, Set<String> allowedFields, String defaultField) {
        if (raw == null || raw.isBlank()) {
            return new SortSpec(defaultField, true);
        }
        String[] parts = raw.split(",");
        if (parts.length > 2) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序参数格式应为 field[,asc|desc]");
        }
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "不支持的排序字段，可选：" + String.join(",", allowedFields));
        }
        boolean ascending = true;
        if (parts.length == 2) {
            String direction = parts[1].trim().toLowerCase(Locale.ROOT);
            if (!direction.equals("asc") && !direction.equals("desc")) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "排序方向只能是 asc 或 desc");
            }
            ascending = direction.equals("asc");
        }
        return new SortSpec(field, ascending);
    }

    @Override
    public String toString() {
        return field + (ascending ? ",asc" : ",desc");
    }
}
