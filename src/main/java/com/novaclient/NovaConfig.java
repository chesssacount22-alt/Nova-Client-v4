package com.novaclient;

import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Client-wide settings (GUI colour, blur) + small key/value store used by modules. */
public final class NovaConfig {
    private NovaConfig() {}

    public static int r = 61, g = 139, b = 255;
    public static boolean blur = true;
    public static boolean sidebarCollapsed = false;

    private static final Properties EXTRA = new Properties();
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("novaclient.properties"); }

    public static int accent() { return 0xFF000000 | (r << 16) | (g << 8) | b; }
    public static void setAccent(int rgb) { r = (rgb >> 16) & 255; g = (rgb >> 8) & 255; b = rgb & 255; }

    public static String getString(String k, String def) { return EXTRA.getProperty(k, def); }
    public static void setString(String k, String v) { EXTRA.setProperty(k, v); save(); }
    public static boolean has(String k) { return EXTRA.containsKey(k); }
    public static boolean getBool(String k, boolean def) { return Boolean.parseBoolean(EXTRA.getProperty(k, String.valueOf(def))); }
    public static void setBool(String k, boolean v) { EXTRA.setProperty(k, String.valueOf(v)); save(); }
    public static int getInt(String k, int def) {
        try { return Integer.parseInt(EXTRA.getProperty(k, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
    }
    public static void setInt(String k, int v) { EXTRA.setProperty(k, String.valueOf(v)); save(); }

    public static void load() {
        try {
            Path f = file();
            if (!Files.exists(f)) return;
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(f)) { p.load(in); }
            EXTRA.putAll(p);
            r = clamp(Integer.parseInt(p.getProperty("gui_r", "61")));
            g = clamp(Integer.parseInt(p.getProperty("gui_g", "139")));
            b = clamp(Integer.parseInt(p.getProperty("gui_b", "255")));
            blur = Boolean.parseBoolean(p.getProperty("blur", "true"));
            sidebarCollapsed = Boolean.parseBoolean(p.getProperty("sidebar_collapsed", "false"));
        } catch (Exception ignored) {}
    }

    public static void save() {
        try {
            EXTRA.setProperty("gui_r", String.valueOf(r));
            EXTRA.setProperty("gui_g", String.valueOf(g));
            EXTRA.setProperty("gui_b", String.valueOf(b));
            EXTRA.setProperty("blur", String.valueOf(blur));
            EXTRA.setProperty("sidebar_collapsed", String.valueOf(sidebarCollapsed));
            try (OutputStream out = Files.newOutputStream(file())) { EXTRA.store(out, "Nova Client settings"); }
        } catch (Exception ignored) {}
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
