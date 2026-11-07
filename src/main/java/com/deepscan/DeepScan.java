package com.deepscan;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class DeepScan implements ClientModInitializer {
	public static final String ID = "deepscan";

	@Override
	public void onInitializeClient() {
		Config.load();

		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
		KeyMapping scanKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.deepscan.scan", InputConstants.Type.KEYSYM, InputConstants.KEY_R, cat));
		KeyMapping menuKey = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.deepscan.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_RSHIFT, cat));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (scanKey.consumeClick()) Scanner.start(mc);
			while (menuKey.consumeClick()) mc.setScreen(new MenuScreen());
			Scanner.tick(mc);
		});

		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath(ID, "overlay"), Overlay::render);
	}
}
