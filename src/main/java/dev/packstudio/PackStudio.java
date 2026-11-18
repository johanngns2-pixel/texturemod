package dev.packstudio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

public class PackStudio implements ClientModInitializer {
    private static KeyBinding open;

    public static Path packsDir() { return FabricLoader.getInstance().getGameDir().resolve("resourcepacks"); }
    public static Path exportDir() { return FabricLoader.getInstance().getGameDir().resolve("packstudio").resolve("exports"); }

    @Override
    public void onInitializeClient() {
        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("packstudio", "main"));
        open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.packstudio.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, cat));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            while (open.wasPressed()) c.setScreen(new StudioScreen(c.currentScreen));
        });
    }
}
