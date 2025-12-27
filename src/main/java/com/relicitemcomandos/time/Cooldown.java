package com.relicitemcomandos.time;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Cooldown {

    private static final ConcurrentHashMap<String, ConcurrentHashMap<UUID, Long>> COOLDOWNS = new ConcurrentHashMap<>();

    private Cooldown() {
    }

    public static void start(String name, UUID uuid, int seconds) {
        long endTime = System.currentTimeMillis() + (seconds * 1000L);

        COOLDOWNS
                .computeIfAbsent(name, k -> new ConcurrentHashMap<>())
                .put(uuid, endTime);
    }

    public static boolean isInCooldown(String name, UUID uuid) {
        ConcurrentHashMap<UUID, Long> map = COOLDOWNS.get(name);
        if (map == null) return false;

        Long endTime = map.get(uuid);
        if (endTime == null) return false;

        if (endTime <= System.currentTimeMillis()) {
            map.remove(uuid);
            if (map.isEmpty()) {
                COOLDOWNS.remove(name);
            }
            return false;
        }

        return true;
    }

    public static long getRemaining(String name, UUID uuid) {
        ConcurrentHashMap<UUID, Long> map = COOLDOWNS.get(name);
        if (map == null) return 0;

        Long endTime = map.get(uuid);
        if (endTime == null) return 0;

        long remaining = endTime - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    public static void remove(String name, UUID uuid) {
        ConcurrentHashMap<UUID, Long> map = COOLDOWNS.get(name);
        if (map != null) {
            map.remove(uuid);
            if (map.isEmpty()) {
                COOLDOWNS.remove(name);
            }
        }
    }
}
