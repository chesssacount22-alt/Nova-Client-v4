package com.novaclient.module;

import net.minecraft.client.option.KeyBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for everything that shows up as a card in the menu.
 * Every module gets an on/off toggle and a user-assignable keybind automatically.
 */
public abstract class Module {
    public final String name, description;
    public final Category category;
    /** Optional credit shown on the mod's options page. */
    public String author;
    /** Optional pixel-icon name for the card (see MenuScreen.ICONS); null = category icon. */
    public String icon;
    public final List<Setting> settings = new ArrayList<>();
    /** Extra keybinds (besides the on/off toggle key). Shown under KEYBINDS in the mod page and in Settings. */
    public final List<KeyOption> keys = new ArrayList<>();
    /** Set by ModuleManager.register(). Toggles the module; supports keyboard + mouse, unbound by default. */
    public KeyBinding bind;

    public enum Category { COMBAT("Combat"), UTILITY("Utility"), VISUAL("Visual");
        public final String label; Category(String l) { label = l; } }

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    public String id() { return name.toLowerCase().replaceAll("[^a-z0-9]+", "_"); }

    public abstract boolean isEnabled();
    public abstract void setEnabled(boolean on);
    /** Called after any setting changes so the module can persist its config. */
    public void save() {}
    /** Called once, on the first client tick (all other mods are initialised by then). */
    public void onStart() {}
}
