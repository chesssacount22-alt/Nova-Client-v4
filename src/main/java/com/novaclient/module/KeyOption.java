package com.novaclient.module;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** An extra keybind a module owns (e.g. Safe Anchor's "Run" key). Code: GLFW key, 1000+button = mouse, -1 = none. */
public class KeyOption {
    public final String label;
    public final IntSupplier get;
    public final IntConsumer set;
    public final IntFunction<String> name;
    public KeyOption(String label, IntSupplier get, IntConsumer set, IntFunction<String> name) {
        this.label = label; this.get = get; this.set = set; this.name = name;
    }
}
