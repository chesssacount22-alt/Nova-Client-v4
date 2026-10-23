package com.novaclient.module;

import com.novaclient.NovaConfig;

/**
 * Safe Anchor has no on/off flag of its own (it only runs from its keybinds), so Nova adds one:
 * while OFF, its run/cycle keys are unbound; turning it ON restores them.
 */
public class SafeAnchorModule extends Module {
    private static final String EN = "mod.safeanchor.enabled", RK = "mod.safeanchor.runBackup", CK = "mod.safeanchor.cycleBackup";
    private static final int NONE = Integer.MIN_VALUE;
    private final Class<?> cfg = Reflect.cls("com.example.safeanchor.Config");
    private final Class<?> keysCls = Reflect.cls("com.example.safeanchor.Keys");

    public SafeAnchorModule() {
        super("Safe Anchor", "Respawn anchor macro, run by key.", Category.COMBAT);
        int max = Math.max(1, Reflect.getInt(cfg, "MAX_DELAY_MS"));
        settings.add(new Setting.Choice("Mode", new String[]{"Safe Anchor", "Normal Anchor"},
                () -> Reflect.getBool(cfg, "safe") ? 0 : 1,
                v -> { Reflect.setBool(cfg, "safe", v == 0); save(); }).in("Mode"));
        settings.add(new Setting.Slider("Delay", 0, max, " ms", () -> Reflect.getInt(cfg, "delayMs"),
                v -> { Reflect.setInt(cfg, "delayMs", v); save(); }).in("Delay"));
        settings.add(new Setting.Toggle("Instant anchor", () -> Reflect.getBool(cfg, "instant"), v -> { Reflect.setBool(cfg, "instant", v); save(); }).in("Additional settings"));
        settings.add(new Setting.Toggle("Totem switch", () -> Reflect.getBool(cfg, "totem"), v -> { Reflect.setBool(cfg, "totem", v); save(); }).in("Additional settings"));
        settings.add(new Setting.Toggle("Hold to repeat", () -> Reflect.getBool(cfg, "hold"), v -> { Reflect.setBool(cfg, "hold", v); save(); }).in("Additional settings"));
        settings.add(new Setting.Toggle("Chat messages", () -> Reflect.getBool(cfg, "messages"), v -> { Reflect.setBool(cfg, "messages", v); save(); }).in("Additional settings"));

        keys.add(gatedKey("Run key", "runKey", RK));
        keys.add(gatedKey("Cycle mode key", "cycleKey", CK));
    }

    private String keyName(int code) { return code == -1 ? "None" : Reflect.str(keysCls, "name", code); }

    /** Run/cycle keys are temporarily unbound while the module is OFF, so read/write the stored backup then. */
    private KeyOption gatedKey(String label, String field, String backupKey) {
        return new KeyOption(label,
                () -> {
                    if (isEnabled()) return Reflect.getInt(cfg, field);
                    int b = NovaConfig.getInt(backupKey, NONE);
                    return b == NONE ? -1 : b;
                },
                v -> {
                    if (isEnabled()) { Reflect.setInt(cfg, field, v); Reflect.call(keysCls, "apply"); save(); }
                    else NovaConfig.setInt(backupKey, v);
                },
                this::keyName);
    }

    public static boolean available() { return Reflect.cls("com.example.safeanchor.Config") != null; }

    @Override public boolean isEnabled() { return NovaConfig.getBool(EN, true); }

    @Override public void setEnabled(boolean on) {
        NovaConfig.setBool(EN, on);
        applyGate();
    }

    @Override public void onStart() {
        // Nova's menu replaces Safe Anchor's own settings screen, so its "open settings" key is unbound.
        Reflect.setInt(cfg, "settingsKey", -1);
        applyGate();
    }

    private void applyGate() {
        if (!isEnabled()) {
            int run = Reflect.getInt(cfg, "runKey"), cycle = Reflect.getInt(cfg, "cycleKey");
            if (run != -1) NovaConfig.setInt(RK, run);
            if (cycle != -1) NovaConfig.setInt(CK, cycle);
            Reflect.setInt(cfg, "runKey", -1);
            Reflect.setInt(cfg, "cycleKey", -1);
        } else {
            int rb = NovaConfig.getInt(RK, NONE), cb = NovaConfig.getInt(CK, NONE);
            if (Reflect.getInt(cfg, "runKey") == -1 && rb != NONE) Reflect.setInt(cfg, "runKey", rb);
            if (Reflect.getInt(cfg, "cycleKey") == -1 && cb != NONE) Reflect.setInt(cfg, "cycleKey", cb);
            NovaConfig.setInt(RK, NONE);
            NovaConfig.setInt(CK, NONE);
        }
        Reflect.call(keysCls, "apply");
    }

    @Override public void save() {
        if (isEnabled()) { Reflect.call(cfg, "save"); return; }
        // disabled: never write the temporary "unbound" keys into safeanchor.properties
        int run = Reflect.getInt(cfg, "runKey"), cycle = Reflect.getInt(cfg, "cycleKey");
        int rb = NovaConfig.getInt(RK, NONE), cb = NovaConfig.getInt(CK, NONE);
        if (rb != NONE) Reflect.setInt(cfg, "runKey", rb);
        if (cb != NONE) Reflect.setInt(cfg, "cycleKey", cb);
        Reflect.call(cfg, "save");
        Reflect.setInt(cfg, "runKey", run);
        Reflect.setInt(cfg, "cycleKey", cycle);
    }
}
