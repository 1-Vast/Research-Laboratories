package com.labflow.identity.domain;

import java.util.Locale;

/** 用户名规范化：去首尾空白后统一转小写，保证唯一性判断与数据库唯一约束一致。 */
public final class Usernames {

    private Usernames() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }
}
