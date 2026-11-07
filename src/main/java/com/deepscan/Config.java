package com.deepscan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Every setting in the mod. Saved to config/deepscan.json. Colours are ARGB ints. */
public final class Config {
	// ---- scanner ----
	public int radiusChunks = 6;
	public float sweepSpeed = 3.5f;       // blocks per tick the radius line travels
	public int minY = -64;
	public int maxY = 0;
	public int sensitivity = 7;           // 1 = needs clusters ... 10 = lone blocks count
	public boolean keepHighlighted = true;
	public float opacity = 0.6f;          // opacity of kept highlights
	public int pulseMs = 1000;            // how long a fresh find glows
	public float scanTint = 0.10f;        // screen tint while scanning

	// ---- blocks ----
	public boolean customList = false;    // false = anything unnatural, true = only `targets`
	public boolean ignoreAncientCity = true;
	public Set<String> targets = new LinkedHashSet<>();
	public Set<String> ignored = new LinkedHashSet<>();

	// ---- highlight look ----
	public boolean showEsp = true;        // draw highlights through walls
	public boolean fillFaces = true;
	public boolean outline = true;
	public boolean shadeFaces = true;
	public boolean showRing = true;
	public boolean rainbow = false;
	public float glowStrength = 1.0f;
	public int glowLayers = 3;
	public float animSpeed = 1f;
	public int espRange = 64;
	public int maxDraw = 300;

	// ---- colours ----
	public int foundColor = 0xFF35E0FF;
	public int keptColor = 0xFFFF4DD8;
	public int ringColor = 0xFF4DA3FF;
	public int accentColor = 0xFF3D8BFF;
	public int radarBg = 0xFF0B2A66;

	// ---- radar / map ----
	public boolean showRadar = true;
	public boolean radarRotate = true;
	public boolean radarInfo = true;
	public int radarCorner = 0;           // 0 top-right, 1 top-left, 2 bottom-right, 3 bottom-left
	public int radarSize = 120;
	public int radarZoom = 96;            // blocks from centre to edge
	public int radarSpan = 70;            // degrees of the sweep trail
	public int radarMargin = 10;

	// ---- menu ----
	public float panelOpacity = 0.96f;
	public boolean stars = true;

	public static Config I = new Config();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("deepscan.json");
	}

	public static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				Config c = GSON.fromJson(Files.readString(p), Config.class);
				if (c != null) I = c;
			}
		} catch (Exception e) {
			System.err.println("[DeepScan] could not read config: " + e);
		}
		save();
	}

	public static void save() {
		try {
			Files.writeString(path(), GSON.toJson(I));
		} catch (Exception e) {
			System.err.println("[DeepScan] could not save config: " + e);
		}
	}

	public static void reset() {
		I = new Config();
		Scanner.invalidate();
		save();
	}

	// ---- colour themes: accent, found, kept, ring, radar background ----
	public static final String[] THEME_NAMES = {"OCEAN", "NEON", "CRIMSON", "EMERALD", "MONO"};
	private static final int[][] THEMES = {
		{0xFF3D8BFF, 0xFF35E0FF, 0xFFFF4DD8, 0xFF4DA3FF, 0xFF0B2A66},
		{0xFFB04DFF, 0xFF4DFFB0, 0xFFFFE24D, 0xFFFF4DD8, 0xFF1A0B33},
		{0xFFFF3D4D, 0xFFFFB84D, 0xFFFF4D8B, 0xFFFF6A4D, 0xFF330B10},
		{0xFF2FD98B, 0xFFB8FF4D, 0xFF4DFFE0, 0xFF2FD9C8, 0xFF0B3322},
		{0xFFDDE8FF, 0xFFFFFFFF, 0xFF9FB6E8, 0xFFFFFFFF, 0xFF14181F}};

	public static void applyTheme(int i) {
		int[] t = THEMES[i % THEMES.length];
		I.accentColor = t[0];
		I.foundColor = t[1];
		I.keptColor = t[2];
		I.ringColor = t[3];
		I.radarBg = t[4];
	}
}
