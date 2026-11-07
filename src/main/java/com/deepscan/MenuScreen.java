package com.deepscan;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.*;

/**
 * Custom-drawn settings app. Input comes from the real mouse events (mouseClicked / mouseReleased),
 * the position from the render call, so buttons and sliders respond like normal widgets.
 */
public class MenuScreen extends Screen {
	private static final String[] TABS = {"SCANNER", "HIGHLIGHT", "BLOCKS", "COLORS", "RADAR", "MENU"};
	private static final String[] COLOR_NAMES = {"FOUND", "KEPT", "RING", "ACCENT", "RADAR"};
	private static final String[] CORNERS = {"Top right", "Top left", "Bottom right", "Bottom left"};
	private static final int[] PALETTE = {
		0xFF35E0FF, 0xFF3D8BFF, 0xFFB04DFF, 0xFFFF4DD8, 0xFFFF3D4D, 0xFFFFB84D,
		0xFFFFE24D, 0xFFB8FF4D, 0xFF2FD98B, 0xFFFFFFFF, 0xFF9FB6E8, 0xFF0B2A66};
	private static final String[] PRESETS = {
		"minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest",
		"minecraft:shulker_box", "minecraft:furnace", "minecraft:blast_furnace", "minecraft:crafting_table",
		"minecraft:hopper", "minecraft:rail", "minecraft:torch", "minecraft:wall_torch",
		"minecraft:oak_planks", "minecraft:cobblestone", "minecraft:stone_bricks", "minecraft:netherrack",
		"minecraft:obsidian", "minecraft:tnt", "minecraft:white_bed", "minecraft:oak_door",
		"minecraft:glass", "minecraft:iron_block", "minecraft:oak_sign", "minecraft:ladder",
		"minecraft:iron_door", "minecraft:lantern", "minecraft:chain", "minecraft:bookshelf",
		"minecraft:note_block", "minecraft:dispenser", "minecraft:piston", "minecraft:redstone_wire",
		"minecraft:oak_stairs", "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:stone_slab"};

	private final long openMs = System.currentTimeMillis();
	private final Map<String, Float> anim = new HashMap<>();
	private int tab, lastTab = -1, page, colorIdx, themeIdx;
	private float[] hsv = {0, 1, 1};
	private int mx, my;
	private boolean down, pendingClick, clicked, closeRequested;
	private long tabMs = System.currentTimeMillis();
	private String drag;

	public MenuScreen() { super(Component.literal("DeepScan")); }

	@Override public boolean isPauseScreen() { return false; }

	@Override public void onClose() {
		Config.save();
		Scanner.invalidate();
		super.onClose();
	}

	// ---------- input ----------
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) { pendingClick = true; down = true; }
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) { down = false; drag = null; }
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		if (closeRequested) { closeRequested = false; onClose(); }
	}

	// ---------- widget toolkit (immediate mode) ----------
	private boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

	private float lerp(String id, boolean target) {
		float v = anim.getOrDefault(id, target ? 1f : 0f);
		v += ((target ? 1f : 0f) - v) * 0.25f;
		anim.put(id, v);
		return v;
	}

	private int acc() { return Config.I.accentColor; }

	private void text(GuiGraphics g, String s, int x, int y, int col) { g.drawString(this.font, s, x, y, col, true); }

	private void row(GuiGraphics g, String id, int x, int y, int w, boolean hover) {
		float h = lerp(id, hover);
		g.fill(x, y, x + w, y + 19, Overlay.mix(0xFF0F1A30, 0xFF1B2D52, h));
		g.fill(x, y, x + 2, y + 19, Overlay.alpha(acc(), 0.35f + 0.65f * h));
	}

	private void toggle(GuiGraphics g, String id, String label, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover);
		if (hover && clicked) set.accept(!get.getAsBoolean());
		float k = lerp(id + "k", get.getAsBoolean());
		text(g, label, x + 7, y + 6, 0xFFE6EEFF);
		int tx = x + w - 30;
		g.fill(tx, y + 5, tx + 24, y + 14, Overlay.mix(0xFF2A3350, acc(), k));
		int kx = tx + 1 + (int) (k * 14);
		g.fill(kx, y + 6, kx + 8, y + 13, 0xFFFFFFFF);
	}

	private void slider(GuiGraphics g, String id, String label, int x, int y, int w, double min, double max,
						DoubleSupplier get, DoubleConsumer set, String fmt, double shown) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover || id.equals(drag));
		int tx = x + (int) (w * 0.42), tw = (int) (w * 0.58) - 46;
		if (hover && clicked) drag = id;
		if (id.equals(drag) && down) set.accept(min + Math.max(0, Math.min(1, (mx - tx) / (double) tw)) * (max - min));
		double t = Math.max(0, Math.min(1, (get.getAsDouble() - min) / (max - min)));
		text(g, label, x + 7, y + 6, 0xFFE6EEFF);
		g.fill(tx, y + 9, tx + tw, y + 11, 0xFF26304D);
		g.fill(tx, y + 9, tx + (int) (tw * t), y + 11, acc());
		int kx = tx + (int) (tw * t);
		g.fill(kx - 3, y + 5, kx + 3, y + 14, 0xFFFFFFFF);
		text(g, String.format(fmt, get.getAsDouble() * shown), x + w - 40, y + 6, 0xFF9FB6E8);
	}

	/** Slider whose track shows the colour range it controls. */
	private void gradSlider(GuiGraphics g, String id, String label, int x, int y, int w, int idx, Runnable apply, FloatToIntFunction colorAt) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover || id.equals(drag));
		int tx = x + (int) (w * 0.30), tw = (int) (w * 0.70) - 12;
		if (hover && clicked) drag = id;
		if (id.equals(drag) && down) { hsv[idx] = Math.max(0f, Math.min(1f, (mx - tx) / (float) tw)); apply.run(); }
		text(g, label, x + 7, y + 6, 0xFFE6EEFF);
		for (int i = 0; i < tw; i += 2) g.fill(tx + i, y + 6, tx + Math.min(tw, i + 2), y + 13, colorAt.apply(i / (float) tw));
		int kx = tx + (int) (tw * hsv[idx]);
		g.fill(kx - 2, y + 3, kx + 2, y + 16, 0xFFFFFFFF);
	}

	private interface FloatToIntFunction { int apply(float v); }

	private void cycle(GuiGraphics g, String id, String label, int x, int y, int w, String[] opts, IntSupplier get, IntConsumer set) {
		boolean hover = in(x, y, w, 19);
		row(g, id, x, y, w, hover);
		if (hover && clicked) set.accept((get.getAsInt() + 1) % opts.length);
		text(g, label, x + 7, y + 6, 0xFFE6EEFF);
		String v = opts[get.getAsInt() % opts.length];
		text(g, "< " + v + " >", x + w - 12 - this.font.width(v) - 12, y + 6, acc());
	}

	private void button(GuiGraphics g, String id, String label, int x, int y, int w, Runnable run) {
		boolean hover = in(x, y, w, 19);
		float h = lerp(id, hover);
		Overlay.chamfer(g, x - 1, y - 1, w + 2, 21, 4, Overlay.alpha(acc(), 0.25f + 0.5f * h));
		Overlay.chamfer(g, x, y, w, 19, 3, Overlay.mix(0xFF14294F, acc(), h * 0.85f));
		text(g, label, x + (w - this.font.width(label)) / 2, y + 6, 0xFFFFFFFF);
		if (hover && clicked) run.run();
	}

	// ---------- colour slots ----------
	private int getCol(int i) {
		Config c = Config.I;
		return switch (i) { case 0 -> c.foundColor; case 1 -> c.keptColor; case 2 -> c.ringColor; case 3 -> c.accentColor; default -> c.radarBg; };
	}

	private void setCol(int i, int v) {
		Config c = Config.I;
		switch (i) { case 0 -> c.foundColor = v; case 1 -> c.keptColor = v; case 2 -> c.ringColor = v; case 3 -> c.accentColor = v; default -> c.radarBg = v; }
	}

	private void syncHsv() { hsv = Overlay.toHsv(getCol(colorIdx)); }

	private void applyHsv() { setCol(colorIdx, Overlay.hsv(hsv[0], hsv[1], hsv[2])); }

	// ---------- render ----------
	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		mx = mouseX;
		my = mouseY;
		clicked = pendingClick;
		pendingClick = false;

		Config c = Config.I;
		long now = System.currentTimeMillis();
		if (tab != lastTab) { lastTab = tab; tabMs = now; if (tab == 3) syncHsv(); }
		float t = Math.min(1f, (now - openMs) / 280f), e = 1f - (1f - t) * (1f - t) * (1f - t);

		// background: dim + accent tint + twinkling stars
		g.fill(0, 0, this.width, this.height, Overlay.alpha(0xFF02060E, 0.66f * e));
		g.fill(0, 0, this.width, this.height, Overlay.alpha(acc(), 0.05f * e));
		if (c.stars) for (int i = 0; i < 90; i++) {
			int sx = (int) ((i * 7919L + 101) % this.width), sy = (int) ((i * 6151L + 57) % this.height);
			float a = (0.25f + 0.5f * (float) Math.abs(Math.sin(now * 0.0015 * c.animSpeed + i))) * e;
			g.fill(sx, sy, sx + 1, sy + 1, Overlay.alpha(0xFFFFFFFF, a));
		}

		int PW = Math.min(470, this.width - 12), PH = Math.min(292, this.height - 12);
		int px = (this.width - PW) / 2, py = (this.height - PH) / 2 + (int) ((1 - e) * 16);
		for (int i = 6; i >= 1; i--) Overlay.chamfer(g, px - i, py - i, PW + 2 * i, PH + 2 * i, 6 + i, Overlay.alpha(acc(), 0.035f * e * (7 - i) / 6f));
		Overlay.frame(g, px, py, PW, PH, 6, Overlay.alpha(acc(), 0.95f * e), Overlay.alpha(0xFF08101F, c.panelOpacity * e));
		g.fill(px + 7, py + 1, px + 100, py + PH - 1, Overlay.alpha(0xFF040A14, 0.7f * e));
		g.fill(px + 1, py + 7, px + 7, py + PH - 7, Overlay.alpha(0xFF040A14, 0.7f * e));
		Overlay.brackets(g, px + 4, py + 4, PW - 8, PH - 8, 10, Overlay.alpha(0xFFFFFFFF, 0.35f * e));

		// title block (spaced lettering, animated underline)
		text(g, "D E E P S C A N", px + 12, py + 12, 0xFFFFFFFF);
		text(g, "DEEPSLATE SCANNER", px + 12, py + 23, 0xFF5E7299);
		for (int i = 0; i < 80; i += 2) {
			float k = 0.5f + 0.5f * (float) Math.sin(now * 0.004 * c.animSpeed + i * 0.12);
			g.fill(px + 12 + i, py + 35, px + 14 + i, py + 37, Overlay.mix(0xFF14294F, acc(), k));
		}

		// tab list, Zenith-style slanted selection
		for (int i = 0; i < TABS.length; i++) {
			int ty = py + 46 + i * 24;
			boolean sel = tab == i, hover = in(px + 6, ty, 92, 20);
			float k = lerp("tab" + i, sel || hover);
			if (sel || k > 0.05f) Overlay.slant(g, px + 6, ty, 92, 20, Overlay.alpha(acc(), sel ? 0.55f : 0.3f * k));
			if (sel) g.fill(px + 6, ty, px + 8, ty + 20, 0xFFFFFFFF);
			text(g, TABS[i], px + 15 + (int) (k * 3), ty + 6, sel ? 0xFFFFFFFF : Overlay.mix(0xFF8FA4D0, 0xFFFFFFFF, k));
			if (hover && clicked) { tab = i; page = 0; }
		}
		text(g, "ESC  close", px + 12, py + PH - 16, 0xFF4F6188);

		// content
		int x = px + 112, y0 = py + 14, w = PW - 122, p = 21;
		float wipe = Math.min(1f, (now - tabMs) / 220f);
		g.fill(x, y0 - 6, x + (int) (w * wipe), y0 - 5, Overlay.alpha(acc(), 0.9f));

		switch (tab) {
			case 0 -> {
				slider(g, "rad", "Radius (chunks)", x, y0, w, 1, 16, () -> c.radiusChunks, v -> c.radiusChunks = (int) Math.round(v), "%.0f", 1);
				slider(g, "spd", "Sweep speed", x, y0 + p, w, 1, 12, () -> c.sweepSpeed, v -> c.sweepSpeed = (float) v, "%.1f", 1);
				slider(g, "miny", "Min Y", x, y0 + p * 2, w, -64, 0, () -> c.minY, v -> c.minY = (int) Math.round(v), "%.0f", 1);
				slider(g, "maxy", "Max Y", x, y0 + p * 3, w, -64, 16, () -> c.maxY, v -> c.maxY = (int) Math.round(v), "%.0f", 1);
				slider(g, "sens", "Sensitivity", x, y0 + p * 4, w, 1, 10, () -> c.sensitivity, v -> c.sensitivity = (int) Math.round(v), "%.0f", 1);
				toggle(g, "keep", "Keep highlighted", x, y0 + p * 5, w, () -> c.keepHighlighted, v -> c.keepHighlighted = v);
				slider(g, "op", "Kept opacity", x, y0 + p * 6, w, 0.05, 1, () -> c.opacity, v -> c.opacity = (float) v, "%.0f%%", 100);
				slider(g, "pulse", "Glow time (ms)", x, y0 + p * 7, w, 300, 3000, () -> c.pulseMs, v -> c.pulseMs = (int) Math.round(v), "%.0f", 1);
				slider(g, "tint", "Scan screen tint", x, y0 + p * 8, w, 0, 0.4, () -> c.scanTint, v -> c.scanTint = (float) v, "%.0f%%", 100);
				button(g, "scan", "SCAN NOW", x, y0 + p * 9 + 6, w / 2 - 3, () -> { Scanner.start(this.minecraft); closeRequested = true; });
				button(g, "clr", "CLEAR HIGHLIGHTS", x + w / 2 + 3, y0 + p * 9 + 6, w / 2 - 3, Scanner::clear);
			}
			case 1 -> {
				toggle(g, "esp", "Highlight through walls", x, y0, w, () -> c.showEsp, v -> c.showEsp = v);
				toggle(g, "fill", "Fill block faces", x, y0 + p, w, () -> c.fillFaces, v -> c.fillFaces = v);
				toggle(g, "outl", "Block outline", x, y0 + p * 2, w, () -> c.outline, v -> c.outline = v);
				toggle(g, "shade", "Face shading (3D look)", x, y0 + p * 3, w, () -> c.shadeFaces, v -> c.shadeFaces = v);
				toggle(g, "ring", "World scan ring", x, y0 + p * 4, w, () -> c.showRing, v -> c.showRing = v);
				toggle(g, "rb", "Rainbow tint", x, y0 + p * 5, w, () -> c.rainbow, v -> c.rainbow = v);
				slider(g, "gl", "Glow strength", x, y0 + p * 6, w, 0, 2, () -> c.glowStrength, v -> c.glowStrength = (float) v, "%.0f%%", 100);
				slider(g, "gs", "Glow layers", x, y0 + p * 7, w, 0, 5, () -> c.glowLayers, v -> c.glowLayers = (int) Math.round(v), "%.0f", 1);
				slider(g, "an", "Animation speed", x, y0 + p * 8, w, 0.2, 3, () -> c.animSpeed, v -> c.animSpeed = (float) v, "%.1fx", 1);
				slider(g, "rng", "Draw range (blocks)", x, y0 + p * 9, w, 8, 160, () -> c.espRange, v -> c.espRange = (int) Math.round(v), "%.0f", 1);
				slider(g, "mxd", "Max blocks drawn", x, y0 + p * 10, w, 20, 800, () -> c.maxDraw, v -> c.maxDraw = (int) Math.round(v), "%.0f", 1);
			}
			case 2 -> {
				toggle(g, "cl", "Custom list only (off = anything unnatural)", x, y0, w, () -> c.customList, v -> { c.customList = v; Scanner.invalidate(); });
				toggle(g, "ac", "Ignore ancient city blocks", x, y0 + p, w, () -> c.ignoreAncientCity, v -> { c.ignoreAncientCity = v; Scanner.invalidate(); });
				int per = 12, pages = (PRESETS.length + per - 1) / per, cw = w / 2 - 2;
				for (int i = 0; i < per; i++) {
					int idx = page * per + i;
					if (idx >= PRESETS.length) break;
					String id = PRESETS[idx];
					toggle(g, "b" + idx, id.substring(10), x + (i % 2) * (cw + 4), y0 + p * 2 + 4 + (i / 2) * p, cw,
						() -> c.targets.contains(id), v -> { if (v) c.targets.add(id); else c.targets.remove(id); Scanner.invalidate(); });
				}
				int by = y0 + p * 8 + 10;
				button(g, "pg-", "<", x, by, 30, () -> page = Math.max(0, page - 1));
				button(g, "pg+", ">", x + 34, by, 30, () -> page = Math.min(pages - 1, page + 1));
				text(g, "page " + (page + 1) + "/" + pages + "  -  any block id: config/deepscan.json", x + 72, by + 6, 0xFF7F93BD);
			}
			case 3 -> {
				int bw = w / 5 - 2;
				for (int i = 0; i < 5; i++) {
					final int ci = i;
					int bx = x + i * (bw + 2);
					boolean sel = colorIdx == i, hov = in(bx, y0, bw, 19);
					float k = lerp("cs" + i, sel || hov);
					Overlay.chamfer(g, bx, y0, bw, 19, 3, Overlay.mix(0xFF14294F, acc(), k * 0.8f));
					g.fill(bx + 3, y0 + 14, bx + bw - 3, y0 + 17, getCol(i));
					text(g, COLOR_NAMES[i], bx + (bw - this.font.width(COLOR_NAMES[i])) / 2, y0 + 3, 0xFFFFFFFF);
					if (hov && clicked) { colorIdx = ci; syncHsv(); }
				}
				// preview swatch
				int sy = y0 + p + 2;
				g.fill(x, sy, x + w, sy + 22, getCol(colorIdx));
				Overlay.brackets(g, x, sy, w, 22, 5, 0xFFFFFFFF);
				text(g, COLOR_NAMES[colorIdx] + String.format("   #%06X", getCol(colorIdx) & 0xFFFFFF), x + 8, sy + 7, hsv[2] > 0.6f && hsv[1] < 0.5f ? 0xFF000000 : 0xFFFFFFFF);
				gradSlider(g, "h", "Hue", x, y0 + p * 3 + 6, w, 0, this::applyHsv, v -> Overlay.hsv(v, hsv[1], hsv[2]));
				gradSlider(g, "s", "Saturation", x, y0 + p * 4 + 6, w, 1, this::applyHsv, v -> Overlay.hsv(hsv[0], v, hsv[2]));
				gradSlider(g, "v", "Brightness", x, y0 + p * 5 + 6, w, 2, this::applyHsv, v -> Overlay.hsv(hsv[0], hsv[1], v));
				// palette
				int sw = w / PALETTE.length;
				for (int i = 0; i < PALETTE.length; i++) {
					int bx = x + i * sw;
					boolean hov = in(bx, y0 + p * 6 + 10, sw - 2, 16);
					float k = lerp("pal" + i, hov);
					g.fill(bx, y0 + p * 6 + 10 - (int) (k * 2), bx + sw - 2, y0 + p * 6 + 26, PALETTE[i]);
					if (hov && clicked) { setCol(colorIdx, PALETTE[i]); syncHsv(); }
				}
				cycle(g, "theme", "Theme preset", x, y0 + p * 7 + 14, w, Config.THEME_NAMES, () -> themeIdx, v -> themeIdx = v);
				button(g, "apply", "APPLY THEME", x, y0 + p * 8 + 16, w, () -> { Config.applyTheme(themeIdx); syncHsv(); });
			}
			case 4 -> {
				int lw = Math.max(150, w - 176);
				toggle(g, "sr", "Show radar HUD", x, y0, lw, () -> c.showRadar, v -> c.showRadar = v);
				toggle(g, "rr", "Rotate with view", x, y0 + p, lw, () -> c.radarRotate, v -> c.radarRotate = v);
				toggle(g, "ri", "Info label", x, y0 + p * 2, lw, () -> c.radarInfo, v -> c.radarInfo = v);
				cycle(g, "rc", "Corner", x, y0 + p * 3, lw, CORNERS, () -> c.radarCorner, v -> c.radarCorner = v);
				slider(g, "rs", "Size", x, y0 + p * 4, lw, 60, 200, () -> c.radarSize, v -> c.radarSize = (int) Math.round(v), "%.0f", 1);
				slider(g, "rz", "Zoom (blocks)", x, y0 + p * 5, lw, 16, 320, () -> c.radarZoom, v -> c.radarZoom = (int) Math.round(v), "%.0f", 1);
				slider(g, "rp", "Sweep trail", x, y0 + p * 6, lw, 20, 160, () -> c.radarSpan, v -> c.radarSpan = (int) Math.round(v), "%.0f", 1);
				button(g, "rz-", "ZOOM IN", x, y0 + p * 7 + 6, lw / 2 - 2, () -> c.radarZoom = Math.max(16, c.radarZoom * 2 / 3));
				button(g, "rz+", "ZOOM OUT", x + lw / 2 + 2, y0 + p * 7 + 6, lw / 2 - 2, () -> c.radarZoom = Math.min(320, c.radarZoom * 3 / 2));
				int R = Math.min(70, (PW - 112 - lw - 40) / 2);
				if (R > 20) {
					int rcx = px + PW - 12 - R - 8, rcy = py + PH / 2 - 6;
					Overlay.drawRadar(g, this.minecraft, rcx, rcy, R, c.radarZoom, now, delta);
					String s = Scanner.hits.size() + " highlighted  |  " + c.radarZoom + " blocks";
					text(g, s, rcx - this.font.width(s) / 2, rcy + R + 10, 0xFFDDE8FF);
				}
			}
			default -> {
				slider(g, "po", "Panel opacity", x, y0, w, 0.4, 1, () -> c.panelOpacity, v -> c.panelOpacity = (float) v, "%.0f%%", 100);
				toggle(g, "st", "Menu starfield", x, y0 + p, w, () -> c.stars, v -> c.stars = v);
				slider(g, "an2", "Animation speed", x, y0 + p * 2, w, 0.2, 3, () -> c.animSpeed, v -> c.animSpeed = (float) v, "%.1fx", 1);
				cycle(g, "theme2", "Theme preset", x, y0 + p * 3, w, Config.THEME_NAMES, () -> themeIdx, v -> themeIdx = v);
				button(g, "apply2", "APPLY THEME", x, y0 + p * 4 + 4, w, () -> Config.applyTheme(themeIdx));
				button(g, "save", "SAVE SETTINGS", x, y0 + p * 5 + 8, w, Config::save);
				button(g, "reset", "RESET EVERYTHING TO DEFAULT", x, y0 + p * 6 + 12, w, () -> { Config.reset(); syncHsv(); });
				text(g, "Keys: R = scan, Right Shift = this menu (rebind in Controls)", x, y0 + p * 7 + 20, 0xFF7F93BD);
			}
		}
	}
}
