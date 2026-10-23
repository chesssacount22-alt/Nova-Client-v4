package com.novaclient.module;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.novaclient.NovaConfig;

/**
 * A module described entirely by a JSON file (resources/novaclient/modules/*.json), so a new mod can be
 * added by patching the jar - no Java compile needed. It reads/writes the other mod's public static
 * boolean/int config fields by reflection.
 */
public class JsonModule extends Module {
    private final Class<?> cfg;
    private final String enabledField, saveMethod;

    private JsonModule(JsonObject o, Class<?> cfg) {
        super(str(o, "name", "Unnamed mod"), str(o, "description", ""), category(str(o, "category", "UTILITY")));
        this.cfg = cfg;
        this.enabledField = str(o, "enabledField", null);
        this.saveMethod = str(o, "saveMethod", null);
        this.author = str(o, "author", null);

        JsonArray arr = o.has("settings") ? o.getAsJsonArray("settings") : new JsonArray();
        for (JsonElement el : arr) {
            JsonObject s = el.getAsJsonObject();
            String type = str(s, "type", "toggle"), name = str(s, "name", "Option"), field = str(s, "field", "");
            String section = str(s, "section", "Settings");
            Setting made;
            switch (type) {
                case "slider" -> made = new Setting.Slider(name, num(s, "min", 0), num(s, "max", 100), str(s, "unit", ""),
                        () -> Reflect.getInt(cfg, field), v -> { Reflect.setInt(cfg, field, v); save(); });
                case "choice" -> {
                    JsonArray op = s.getAsJsonArray("options");
                    String[] opts = new String[op.size()];
                    for (int i = 0; i < opts.length; i++) opts[i] = op.get(i).getAsString();
                    made = new Setting.Choice(name, opts, () -> Reflect.getInt(cfg, field), v -> { Reflect.setInt(cfg, field, v); save(); });
                }
                default -> made = new Setting.Toggle(name, () -> Reflect.getBool(cfg, field), v -> { Reflect.setBool(cfg, field, v); save(); });
            }
            settings.add(made.in(section));
        }
    }

    public static JsonModule from(JsonObject o) {
        Class<?> c = Reflect.cls(str(o, "config", ""));
        return c == null ? null : new JsonModule(o, c);   // skipped if the mod's classes aren't in the jar
    }

    @Override public boolean isEnabled() {
        return enabledField != null ? Reflect.getBool(cfg, enabledField) : NovaConfig.getBool("mod." + id() + ".enabled", true);
    }
    @Override public void setEnabled(boolean on) {
        if (enabledField != null) Reflect.setBool(cfg, enabledField, on);
        else NovaConfig.setBool("mod." + id() + ".enabled", on);
        save();
    }
    @Override public void save() { if (saveMethod != null) Reflect.call(cfg, saveMethod); }

    private static String str(JsonObject o, String k, String def) { return o.has(k) ? o.get(k).getAsString() : def; }
    private static int num(JsonObject o, String k, int def) { return o.has(k) ? o.get(k).getAsInt() : def; }
    private static Category category(String s) {
        try { return Category.valueOf(s.toUpperCase()); } catch (Exception e) { return Category.UTILITY; }
    }
}
