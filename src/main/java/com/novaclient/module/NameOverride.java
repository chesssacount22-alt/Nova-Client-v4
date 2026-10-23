package com.novaclient.module;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Custom / random player names used by the Name Scrambler card. Read by the two Nova name mixins. */
public final class NameOverride {
    private NameOverride() {}

    public static final String[] MODES = {"Scramble", "Custom name", "Random name"};
    /** true while Nova itself should rename players (modes Custom / Random). */
    public static volatile boolean enabled = false;
    public static volatile int mode = 0;                 // 0 scramble, 1 custom, 2 random
    public static volatile String custom = "";
    public static volatile boolean includeSelf = true;
    public static volatile long seed = new Random().nextLong();

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> GENERATED = ConcurrentHashMap.newKeySet();

    private static final String[] ADJ = {"Swift", "Silent", "Crimson", "Frosty", "Lucky", "Shadow", "Brave", "Cosmic",
            "Rusty", "Mighty", "Sneaky", "Golden", "Wild", "Lazy", "Neon", "Salty", "Tiny", "Grumpy", "Misty", "Atomic"};
    private static final String[] NOUN = {"Fox", "Wolf", "Panda", "Falcon", "Creeper", "Golem", "Otter", "Tiger", "Pixel",
            "Ninja", "Dragon", "Moth", "Bandit", "Yeti", "Comet", "Cactus", "Raven", "Bear", "Gecko", "Lynx"};

    /** @return the replacement name, or null to leave the name alone. */
    public static String replace(String name, boolean self) {
        if (!enabled || name == null || name.isEmpty()) return null;
        if (self && !includeSelf) return null;
        if (mode == 1) {
            String c = custom == null ? "" : custom.trim();
            return c.isEmpty() ? null : c;
        }
        if (mode == 2) {
            if (GENERATED.contains(name)) return null;            // already one of ours
            String out = CACHE.computeIfAbsent(name, NameOverride::generate);
            GENERATED.add(out);
            return out;
        }
        return null;
    }

    /** New random names (and a new scramble for Scramble mode, see NameScramblerModule). */
    public static void reroll() {
        seed = new Random().nextLong();
        CACHE.clear();
        GENERATED.clear();
    }

    private static String generate(String key) {
        Random r = new Random(key.hashCode() * 31L ^ seed);
        return ADJ[r.nextInt(ADJ.length)] + NOUN[r.nextInt(NOUN.length)] + r.nextInt(100);
    }
}
