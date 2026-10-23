package com.novaclient;

import com.novaclient.module.Module;
import com.novaclient.module.ModuleManager;
import com.novaclient.ui.MenuScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class NovaClient implements ClientModInitializer {
    public static KeyBinding.Category CATEGORY;
    public static KeyBinding openMenu;
    private boolean started;

    @Override
    public void onInitializeClient() {
        NovaConfig.load();
        CATEGORY = KeyBinding.Category.create(Identifier.of("novaclient", "main"));
        openMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
        ModuleManager.init();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!started) { started = true; ModuleManager.onStart(); }

            while (openMenu.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new MenuScreen(null));
            }
            for (Module m : ModuleManager.MODULES) {
                while (m.bind != null && m.bind.wasPressed()) {
                    if (mc.currentScreen != null) continue;
                    m.setEnabled(!m.isEnabled());
                    if (mc.player != null) {
                        mc.player.sendMessage(Text.literal(m.name + ": " + (m.isEnabled() ? "ON" : "OFF")), true);
                    }
                }
            }
        });
    }
}
