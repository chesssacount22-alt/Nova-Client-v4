package com.novaclient.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novaclient.NovaClient;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final List<Module> MODULES = new ArrayList<>();
    private ModuleManager() {}

    public static void init() {
        // Add new modules here. One line each.
        if (FastCrystalModule.available()) register(new FastCrystalModule());
        if (SafeAnchorModule.available()) register(new SafeAnchorModule());
        if (NameScramblerModule.available()) register(new NameScramblerModule());
        loadJsonModules();
    }

    /** Picks up every resources/novaclient/modules/*.json descriptor (mods added by patching the jar). */
    private static void loadJsonModules() {
        FabricLoader.getInstance().getModContainer("novaclient").flatMap(c -> c.findPath("novaclient/modules")).ifPresent(dir -> {
            if (!Files.isDirectory(dir)) return;
            try (var st = Files.list(dir)) {
                st.filter(p -> p.toString().endsWith(".json")).sorted().forEach(ModuleManager::loadOne);
            } catch (Exception ignored) {}
        });
    }

    private static void loadOne(Path p) {
        try (Reader r = Files.newBufferedReader(p)) {
            JsonModule m = JsonModule.from(JsonParser.parseReader(r).getAsJsonObject());
            if (m != null) register(m);
        } catch (Exception ignored) {}
    }

    public static void register(Module m) {
        m.bind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.toggle." + m.id(), InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, NovaClient.CATEGORY));
        MODULES.add(m);
    }

    public static void onStart() { for (Module m : MODULES) m.onStart(); }
}
