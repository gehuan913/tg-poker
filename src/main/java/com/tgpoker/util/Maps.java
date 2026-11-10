package com.tgpoker.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis 返回 Map 时的字段名适配。
 *
 * <p>坑：H2（DATABASE_TO_LOWER=TRUE）会把未加引号的别名转小写，`AS vpipHands` 出来是
 * `vpiphands`；MySQL 则原样保留。所以 SQL 里统一用 snake_case 别名，再在 Java 侧转驼峰，
 * 两种库都稳定。</p>
 */
public final class Maps {

    private Maps() {
    }

    public static Map<String, Object> camel(Map<String, Object> src) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (src == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : src.entrySet()) {
            out.put(camelKey(e.getKey()), e.getValue());
        }
        return out;
    }

    public static List<Map<String, Object>> camelAll(List<Map<String, Object>> list) {
        return list == null ? List.of() : list.stream().map(Maps::camel).toList();
    }

    public static String camelKey(String key) {
        if (key == null) {
            return "";
        }
        String lower = key.toLowerCase();
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : lower.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 兼容取数：先试驼峰、再试原样、最后试大写。 */
    public static long num(Map<String, Object> map, String key) {
        if (map == null) {
            return 0;
        }
        Object v = map.get(key);
        if (v == null) {
            v = map.get(key.toUpperCase());
        }
        if (v == null) {
            v = map.get(key.toLowerCase());
        }
        return v instanceof Number n ? n.longValue() : 0;
    }
}
