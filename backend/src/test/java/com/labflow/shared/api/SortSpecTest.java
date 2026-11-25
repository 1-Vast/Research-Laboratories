package com.labflow.shared.api;

import com.labflow.shared.error.BusinessException;
import com.labflow.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("列表排序参数")
class SortSpecTest {

    private static final Set<String> FIELDS = Set.of("id", "username", "createdAt");

    @Test
    @DisplayName("缺省按默认字段升序")
    void defaultsToAscendingId() {
        SortSpec spec = SortSpec.parse(null, FIELDS, "id");
        assertThat(spec.field()).isEqualTo("id");
        assertThat(spec.ascending()).isTrue();
    }

    @Test
    @DisplayName("解析字段与方向")
    void parsesFieldAndDirection() {
        SortSpec spec = SortSpec.parse("username,desc", FIELDS, "id");
        assertThat(spec.field()).isEqualTo("username");
        assertThat(spec.ascending()).isFalse();
        assertThat(spec.toString()).isEqualTo("username,desc");
    }

    @Test
    @DisplayName("非法排序字段被拒绝")
    void rejectsUnknownField() {
        assertThatThrownBy(() -> SortSpec.parse("passwordHash", FIELDS, "id"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("非法排序方向被拒绝")
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> SortSpec.parse("username,up", FIELDS, "id"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("asc");
    }

    @Test
    @DisplayName("多余逗号被拒绝")
    void rejectsTooManyParts() {
        assertThatThrownBy(() -> SortSpec.parse("username,asc,extra", FIELDS, "id"))
                .isInstanceOf(BusinessException.class);
    }
}
