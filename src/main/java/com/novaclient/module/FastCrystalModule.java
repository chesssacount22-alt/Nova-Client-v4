package com.novaclient.module;

public class FastCrystalModule extends Module {
    private final Class<?> cfg = Reflect.cls("com.example.fastcrystal.Config");

    public FastCrystalModule() {
        super("Fast Crystal", "Faster end-crystal placing and hitting.", Category.COMBAT);
        icon = "crystal";
        author = "TheGamingAgentXd";
        settings.add(new Setting.Slider("Place delay", 0, 10, " ticks",
                () -> Reflect.getInt(cfg, "placeDelay"), v -> { Reflect.setInt(cfg, "placeDelay", v); save(); }).in("Delay"));
        settings.add(new Setting.Slider("Break delay", 0, 10, " ticks",
                () -> Reflect.getInt(cfg, "breakDelay"), v -> { Reflect.setInt(cfg, "breakDelay", v); save(); }).in("Delay"));
    }

    public static boolean available() { return Reflect.cls("com.example.fastcrystal.Config") != null; }
    @Override public boolean isEnabled() { return Reflect.getBool(cfg, "enabled"); }
    @Override public void setEnabled(boolean on) { Reflect.setBool(cfg, "enabled", on); save(); }
    @Override public void save() { Reflect.call(cfg, "save"); }
}
