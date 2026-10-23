package com.novaclient.module;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reads/writes the public static Config fields of the bundled mods without compile-time coupling. */
final class Reflect {
    private Reflect() {}

    static Class<?> cls(String name) {
        try { return Class.forName(name); } catch (Throwable t) { return null; }
    }
    static boolean getBool(Class<?> c, String f) {
        try { Field fl = c.getField(f); return fl.getBoolean(null); } catch (Throwable t) { return false; }
    }
    static void setBool(Class<?> c, String f, boolean v) {
        try { c.getField(f).setBoolean(null, v); } catch (Throwable ignored) {}
    }
    static int getInt(Class<?> c, String f) {
        try { return c.getField(f).getInt(null); } catch (Throwable t) { return 0; }
    }
    static void setInt(Class<?> c, String f, int v) {
        try { c.getField(f).setInt(null, v); } catch (Throwable ignored) {}
    }
    static void setLong(Class<?> c, String f, long v) {
        try { c.getField(f).setLong(null, v); } catch (Throwable ignored) {}
    }
    static String str(Class<?> c, String m, int arg) {
        try { return String.valueOf(c.getMethod(m, int.class).invoke(null, arg)); } catch (Throwable t) { return "Key " + arg; }
    }
    static void call(Class<?> c, String m) {
        try { Method me = c.getMethod(m); me.invoke(null); } catch (Throwable ignored) {}
    }
}
