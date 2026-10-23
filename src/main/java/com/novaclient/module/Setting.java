package com.novaclient.module;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** A single option shown in a mod's config page. Use .in("Section") to group options under a heading. */
public abstract class Setting {
    public final String name;
    public String section = "Settings";
    protected Setting(String name) { this.name = name; }
    public Setting in(String section) { this.section = section; return this; }

    public static class Toggle extends Setting {
        public final BooleanSupplier get; public final Consumer<Boolean> set;
        public Toggle(String name, BooleanSupplier get, Consumer<Boolean> set) { super(name); this.get = get; this.set = set; }
    }

    public static class Slider extends Setting {
        public final int min, max; public final IntSupplier get; public final IntConsumer set; public final String unit;
        public Slider(String name, int min, int max, String unit, IntSupplier get, IntConsumer set) {
            super(name); this.min = min; this.max = max; this.unit = unit; this.get = get; this.set = set;
        }
    }

    /** A single-line text box (e.g. a custom name). */
    public static class Text extends Setting {
        public final String placeholder; public final int max;
        public final java.util.function.Supplier<String> get; public final Consumer<String> set;
        public Text(String name, String placeholder, int max, java.util.function.Supplier<String> get, Consumer<String> set) {
            super(name); this.placeholder = placeholder; this.max = max; this.get = get; this.set = set;
        }
    }

    /** A button that runs an action (e.g. "Re-scramble names"). */
    public static class Button extends Setting {
        public final String label; public final Runnable action;
        public Button(String name, String label, Runnable action) { super(name); this.label = label; this.action = action; }
    }

    /** Pick one of several options (e.g. Mode: Safe Anchor / Normal Anchor). */
    public static class Choice extends Setting {
        public final String[] options; public final IntSupplier get; public final IntConsumer set;
        public Choice(String name, String[] options, IntSupplier get, IntConsumer set) {
            super(name); this.options = options; this.get = get; this.set = set;
        }
    }
}
