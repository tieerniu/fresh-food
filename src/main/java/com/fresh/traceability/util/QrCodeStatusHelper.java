package com.fresh.traceability.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 统一二维码状态定义。
 * 当前系统仅保留 Active / Recalled / Expired 三种正式状态，
 * 旧版本遗留的 Sold 会在运行时被归一化为 Active。
 */
public final class QrCodeStatusHelper {

    public static final String ACTIVE = "Active";
    public static final String RECALLED = "Recalled";
    public static final String EXPIRED = "Expired";
    public static final String LEGACY_SOLD = "Sold";

    private static final Set<String> MANAGED_STATUSES;
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS;

    static {
        Set<String> managedStatuses = new LinkedHashSet<>();
        managedStatuses.add(ACTIVE);
        managedStatuses.add(RECALLED);
        managedStatuses.add(EXPIRED);
        MANAGED_STATUSES = Collections.unmodifiableSet(managedStatuses);

        Map<String, Set<String>> transitions = new LinkedHashMap<>();
        transitions.put(ACTIVE, Set.of(ACTIVE, RECALLED, EXPIRED));
        transitions.put(RECALLED, Set.of(RECALLED));
        transitions.put(EXPIRED, Set.of(EXPIRED));
        ALLOWED_TRANSITIONS = Collections.unmodifiableMap(transitions);
    }

    private QrCodeStatusHelper() {
    }

    public static String normalize(String status) {
        if (status == null || status.trim().isEmpty()) {
            return ACTIVE;
        }
        String trimmed = status.trim();
        if (LEGACY_SOLD.equals(trimmed)) {
            return ACTIVE;
        }
        return trimmed;
    }

    public static boolean isManagedStatus(String status) {
        return MANAGED_STATUSES.contains(normalize(status));
    }

    public static boolean canTransition(String currentStatus, String targetStatus) {
        return getAllowedTargets(currentStatus).contains(normalize(targetStatus));
    }

    public static Set<String> getAllowedTargets(String currentStatus) {
        return ALLOWED_TRANSITIONS.getOrDefault(normalize(currentStatus), Collections.emptySet());
    }
}
