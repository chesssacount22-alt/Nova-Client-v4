package com.novaclient.module;

import com.novaclient.NovaConfig;

/**
 * Name Scrambler by TheGamingAgentXd. Its chat commands are not registered; everything is in the menu.
 * Modes: Scramble (the original letter shuffle), Custom name, Random name (Nova's own mixins).
 */
public class NameScramblerModule extends Module {
    private static final String K_ON = "mod.name_scrambler.enabled", K_MODE = "mod.name_scrambler.mode",
            K_CUSTOM = "mod.name_scrambler.custom", K_SELF = "mod.name_scrambler.self";
    private final Class<?> cfg = Reflect.cls("com.example.namescrambler.NameScramblerClient");
    private boolean on;

    public NameScramblerModule() {
        super("Name Scrambler", "Scrambles, replaces or randomizes player names.", Category.VISUAL);
        author = "TheGamingAgentXd";
        on = Reflect.getBool(cfg, "enabled");

        settings.add(new Setting.Choice("Mode", NameOverride.MODES,
                () -> NameOverride.mode,
                v -> { NameOverride.mode = v; NovaConfig.setInt(K_MODE, v); sync(); }).in("Mode"));
        settings.add(new Setting.Text("Custom name", "Type a name (Custom name mode)", 24,
                () -> NameOverride.custom,
                v -> { NameOverride.custom = v; NovaConfig.setString(K_CUSTOM, v); }).in("Custom name"));
        settings.add(new Setting.Toggle("Also rename my own name",
                () -> NameOverride.includeSelf,
                v -> { NameOverride.includeSelf = v; NovaConfig.setBool(K_SELF, v); }).in("Names"));
        settings.add(new Setting.Button("New names", "Re-roll", () -> {
            Reflect.setLong(cfg, "salt", new java.util.Random().nextLong());   // new letter shuffle
            NameOverride.reroll();                                             // new random names
        }).in("Names"));
    }

    public static boolean available() { return Reflect.cls("com.example.namescrambler.NameScramblerClient") != null; }

    @Override public boolean isEnabled() { return on; }

    @Override public void setEnabled(boolean v) {
        on = v;
        NovaConfig.setBool(K_ON, v);
        sync();
    }

    /** Scramble mode uses the original mod; Custom/Random use Nova's mixins. Only one is active at a time. */
    private void sync() {
        Reflect.setBool(cfg, "enabled", on && NameOverride.mode == 0);
        NameOverride.enabled = on && NameOverride.mode != 0;
    }

    @Override public void onStart() {
        if (NovaConfig.has(K_ON)) on = NovaConfig.getBool(K_ON, on);
        NameOverride.mode = Math.max(0, Math.min(2, NovaConfig.getInt(K_MODE, 0)));
        NameOverride.custom = NovaConfig.getString(K_CUSTOM, "");
        NameOverride.includeSelf = NovaConfig.getBool(K_SELF, true);
        sync();
    }
}
