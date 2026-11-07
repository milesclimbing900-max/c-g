package com.deepscan;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

/** HUD layer: block highlights (drawn over everything so they show through walls), sweep ring, radar. Also shared UI helpers. */
public final class Overlay {
	private Overlay() {}

	// =====================================================================
	// colour helpers
	// =====================================================================
	public static int alpha(int c, float k) {
		int a = (int) Math.min(255, Math.max(0, (c >>> 24) * k));
		return (a << 24) | (c & 0xFFFFFF);
	}

	public static int hsv(float h, float s, float v) {
		h = h - (float) Math.floor(h);
		int i = (int) (h * 6);
		float f = h * 6 - i, p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
		float[] rgb = switch (i % 6) {
			case 0 -> new float[]{v, t, p};
			case 1 -> new float[]{q, v, p};
			case 2 -> new float[]{p, v, t};
			case 3 -> new float[]{p, q, v};
			case 4 -> new float[]{t, p, v};
			default -> new float[]{v, p, q};
		};
		return 0xFF000000 | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
	}

	/** @return {hue 0..1, saturation 0..1, value 0..1} */
	public static float[] toHsv(int c) {
		float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
		if (d < 1e-4f) h = 0f;
		else {
			h = max == r ? ((g - b) / d) % 6 : max == g ? (b - r) / d + 2 : (r - g) / d + 4;
			h /= 6f;
			if (h < 0) h += 1;
		}
		return new float[]{h, max <= 0 ? 0 : d / max, max};
	}

	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}

	// =====================================================================
	// shape helpers (all built from fill() so they work on any version)
	// =====================================================================
	public static void disc(GuiGraphics g, int cx, int cy, int r, int col) {
		for (int dy = -r; dy <= r; dy++) {
			int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
			g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, col);
		}
	}

	public static void ring(GuiGraphics g, int cx, int cy, int r, int thick, int col) {
		int ri = r - thick;
		for (int dy = -r; dy <= r; dy++) {
			int xo = (int) Math.sqrt((double) r * r - (double) dy * dy);
			if (Math.abs(dy) >= ri) {
				g.fill(cx - xo, cy + dy, cx + xo + 1, cy + dy + 1, col);
			} else {
				int xi = (int) Math.sqrt((double) ri * ri - (double) dy * dy);
				g.fill(cx - xo, cy + dy, cx - xi, cy + dy + 1, col);
				g.fill(cx + xi + 1, cy + dy, cx + xo + 1, cy + dy + 1, col);
			}
		}
	}

	/** Filled circular sector between angles a1 < a2 (radians, y-down screen space). Needs a2 - a1 < PI. */
	public static void sector(GuiGraphics g, int cx, int cy, int R, double a1, double a2, int col) {
		double d1x = Math.cos(a1), d1y = Math.sin(a1), d0x = Math.cos(a2), d0y = Math.sin(a2);
		for (int dy = -R; dy <= R; dy++) {
			double half = Math.sqrt((double) R * R - (double) dy * dy), lo = -half, hi = half;
			if (Math.abs(d1y) < 1e-9) { if (d1x * dy < 0) continue; }
			else { double b = d1x * dy / d1y; if (d1y > 0) hi = Math.min(hi, b); else lo = Math.max(lo, b); }
			if (Math.abs(d0y) < 1e-9) { if (-dy * d0x < 0) continue; }
			else { double b = dy * d0x / d0y; if (d0y > 0) lo = Math.max(lo, b); else hi = Math.min(hi, b); }
			if (hi > lo) g.fill(cx + (int) Math.round(lo), cy + dy, cx + Math.max((int) Math.round(hi), (int) Math.round(lo) + 1), cy + dy + 1, col);
		}
	}

	/** Rectangle with chamfered (cut) corners, like the HUD frame assets. */
	public static void chamfer(GuiGraphics g, int x, int y, int w, int h, int cut, int col) {
		for (int i = 0; i < h; i++) {
			int inset = i < cut ? cut - i : (i >= h - cut ? i - (h - cut - 1) : 0);
			g.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
		}
	}

	/** Chamfered panel: border colour + fill colour. */
	public static void frame(GuiGraphics g, int x, int y, int w, int h, int cut, int border, int fill) {
		chamfer(g, x, y, w, h, cut, border);
		chamfer(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, cut - 1), fill);
	}

	/** Corner brackets around a rectangle. */
	public static void brackets(GuiGraphics g, int x, int y, int w, int h, int len, int col) {
		g.fill(x, y, x + len, y + 1, col); g.fill(x, y, x + 1, y + len, col);
		g.fill(x + w - len, y, x + w, y + 1, col); g.fill(x + w - 1, y, x + w, y + len, col);
		g.fill(x, y + h - 1, x + len, y + h, col); g.fill(x, y + h - len, x + 1, y + h, col);
		g.fill(x + w - len, y + h - 1, x + w, y + h, col); g.fill(x + w - 1, y + h - len, x + w, y + h, col);
	}

	/** Parallelogram highlight bar (right edge slanted) like the Zenith menu selection. */
	public static void slant(GuiGraphics g, int x, int y, int w, int h, int col) {
		for (int i = 0; i < h; i++) g.fill(x, y + i, x + w - (h - 1 - i) / 2, y + i + 1, col);
	}

	// =====================================================================
	// hit colours
	// =====================================================================
	static int hitColor(Scanner.Hit h, long now) {
		Config c = Config.I;
		long age = now - h.t;
		boolean pulse = age < c.pulseMs;
		int base = pulse ? c.foundColor : c.keptColor;
		if (c.rainbow) base = hsv(now * 0.0002f * c.animSpeed + h.x * 0.013f + h.z * 0.007f, 0.7f, 1f);
		float a = pulse ? 0.55f + 0.45f * (1f - age / (float) c.pulseMs)
			: c.opacity * (0.85f + 0.15f * (float) Math.sin(now * 0.004 * c.animSpeed));
		return alpha(base, a);
	}

	static float hitGlow(Scanner.Hit h, long now) {
		Config c = Config.I;
		long age = now - h.t;
		if (age < c.pulseMs) return (1f - age / (float) c.pulseMs) * (1.2f + 0.5f * (float) Math.sin(now * 0.03)) + 0.25f;
		return 0.3f * (0.8f + 0.2f * (float) Math.sin(now * 0.004 * c.animSpeed));
	}

	// =====================================================================
	// camera + projection
	// =====================================================================
	private static final class Cam {
		Vec3 eye;
		double fx, fy, fz, rx, rz, ux, uy, uz, tan;
		int w, h;

		Cam(Vec3 eye, float yaw, float pitch, double fovDeg, int w, int h) {
			this.eye = eye;
			this.w = w;
			this.h = h;
			tan = Math.tan(Math.toRadians(fovDeg) / 2.0);
			double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
			fx = -Math.sin(yr) * Math.cos(pr);
			fy = -Math.sin(pr);
			fz = Math.cos(yr) * Math.cos(pr);
			rx = -Math.cos(yr);
			rz = -Math.sin(yr);
			ux = -rz * fy;
			uy = rz * fx - rx * fz;
			uz = rx * fy;
		}

		double[] toCam(double x, double y, double z) {
			double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
			return new double[]{dx * rx + dz * rz, dx * ux + dy * uy + dz * uz, dx * fx + dy * fy + dz * fz};
		}

		float sx(double[] p) { return (float) (w / 2.0 + (p[0] / p[2]) / (tan * w / h) * (w / 2.0)); }
		float sy(double[] p) { return (float) (h / 2.0 - (p[1] / p[2]) / tan * (h / 2.0)); }
	}

	private static final double NEAR = 0.12;

	/** Sutherland-Hodgman clip of a camera-space polygon against the near plane. */
	private static int clipNear(double[][] in, int n, double[][] out) {
		int m = 0;
		for (int i = 0; i < n; i++) {
			double[] a = in[i], b = in[(i + 1) % n];
			boolean ain = a[2] >= NEAR, bin = b[2] >= NEAR;
			if (ain) out[m++] = a;
			if (ain != bin) {
				double t = (NEAR - a[2]) / (b[2] - a[2]);
				out[m++] = new double[]{a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), NEAR};
			}
		}
		return m;
	}

	/** Scanline-fills a convex polygon. Edge pixels use `edge` when outline is on. */
	private static void poly(GuiGraphics g, float[] xs, float[] ys, int n, int fill, int edge, boolean outline, int sw, int sh) {
		float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < n; i++) { minY = Math.min(minY, ys[i]); maxY = Math.max(maxY, ys[i]); }
		int y0 = Math.max(0, (int) Math.ceil(minY)), y1 = Math.min(sh - 1, (int) Math.floor(maxY));
		for (int y = y0; y <= y1; y++) {
			double yc = y + 0.5, lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
			for (int i = 0; i < n; i++) {
				int j = (i + 1) % n;
				float ya = ys[i], yb = ys[j];
				if ((ya <= yc && yb > yc) || (yb <= yc && ya > yc)) {
					double x = xs[i] + (yc - ya) / (yb - ya) * (xs[j] - xs[i]);
					lo = Math.min(lo, x);
					hi = Math.max(hi, x);
				}
			}
			if (lo > hi) continue;
			int xa = Math.max(0, (int) Math.round(lo)), xb = Math.min(sw, (int) Math.round(hi));
			if (xb <= xa) xb = Math.min(sw, xa + 1);
			if (xb <= xa) continue;
			if (!outline) { g.fill(xa, y, xb, y + 1, fill); continue; }
			if (y == y0 || y == y1 || xb - xa < 3) { g.fill(xa, y, xb, y + 1, edge); continue; }
			g.fill(xa, y, xa + 1, y + 1, edge);
			g.fill(xa + 1, y, xb - 1, y + 1, fill);
			g.fill(xb - 1, y, xb, y + 1, edge);
		}
	}

	// =====================================================================
	// block highlight: fills the actual faces of the cube you can see
	// =====================================================================
	private static void block(GuiGraphics g, Cam cam, Scanner.Hit hit, int col, float glow, boolean fresh) {
		Config c = Config.I;
		double cx = hit.x + 0.5, cy = hit.y + 0.5, cz = hit.z + 0.5;
		double[] ctr = {cx, cy, cz};
		double[] eye = {cam.eye.x, cam.eye.y, cam.eye.z};
		int layers = fresh ? c.glowLayers : Math.min(1, c.glowLayers);
		int edgeCol = alpha(col, 1.7f);
		double[][] raw = new double[4][], clipped = new double[8][];
		float[] xs = new float[8], ys = new float[8];

		for (int axis = 0; axis < 3; axis++) {
			for (int sign = -1; sign <= 1; sign += 2) {
				if (sign * (eye[axis] - ctr[axis]) <= 0.5) continue; // face points away from the camera
				int ua = (axis + 1) % 3, va = (axis + 2) % 3;
				double[][] offs = {{-.5, -.5}, {.5, -.5}, {.5, .5}, {-.5, .5}};
				for (int i = 0; i < 4; i++) {
					double[] p = {cx, cy, cz};
					p[axis] += sign * 0.5;
					p[ua] += offs[i][0];
					p[va] += offs[i][1];
					raw[i] = cam.toCam(p[0], p[1], p[2]);
				}
				int n = clipNear(raw, 4, clipped);
				if (n < 3) continue;
				float mx = 0, my = 0;
				for (int i = 0; i < n; i++) {
					xs[i] = cam.sx(clipped[i]);
					ys[i] = cam.sy(clipped[i]);
					mx += xs[i];
					my += ys[i];
				}
				mx /= n;
				my /= n;

				float shade = !c.shadeFaces ? 1f : axis == 1 ? (sign > 0 ? 1f : 0.55f) : axis == 0 ? 0.82f : 0.68f;

				// glow: the same face expanded outwards, fading with each layer
				for (int L = layers; L >= 1; L--) {
					float k = 1f - (float) (L - 1) / (layers + 1);
					float sc = 1f + L * 0.14f;
					float[] gx = new float[n], gy = new float[n];
					for (int i = 0; i < n; i++) {
						gx[i] = mx + (xs[i] - mx) * sc;
						gy[i] = my + (ys[i] - my) * sc;
					}
					poly(g, gx, gy, n, alpha(col, 0.10f * glow * c.glowStrength * k), 0, false, cam.w, cam.h);
				}
				if (c.fillFaces) poly(g, xs, ys, n, alpha(col, shade * 0.85f), edgeCol, c.outline, cam.w, cam.h);
				else if (c.outline) poly(g, xs, ys, n, 0, edgeCol, true, cam.w, cam.h);
			}
		}
	}

	// =====================================================================
	// entry point
	// =====================================================================
	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.options.hideGui) return;
		Config c = Config.I;
		float pt = dt.getGameTimeDeltaPartialTick(false);
		long now = System.currentTimeMillis();
		int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();

		if (Scanner.active && c.scanTint > 0) g.fill(0, 0, w, h, alpha(c.ringColor, c.scanTint));

		if (c.showEsp || c.showRing) {
			Cam cam = new Cam(mc.player.getEyePosition(pt), mc.player.getViewYRot(pt), mc.player.getViewXRot(pt),
				mc.options.fov().get(), w, h);
			if (c.showRing) ringWorld(g, mc, cam, now);
			if (c.showEsp) esp(g, cam, now);
		}

		if (c.showRadar && !(mc.screen instanceof MenuScreen)) {
			int R = c.radarSize / 2, m = c.radarMargin;
			int cx = (c.radarCorner == 0 || c.radarCorner == 2) ? w - m - R : m + R;
			int cy = (c.radarCorner == 0 || c.radarCorner == 1) ? m + R : h - m - R - (c.radarInfo ? 18 : 0);
			drawRadar(g, mc, cx, cy, R, c.radarZoom, now, pt);
			if (c.radarInfo) {
				String s = Scanner.active ? "SCANNING " + (Scanner.total == 0 ? 0 : 100 * Scanner.done / Scanner.total) + "%" : "READY";
				String t = s + "  |  " + Scanner.hits.size() + " FOUND";
				int tw = mc.font.width(t) + 12, tx = cx - tw / 2, ty = cy + R + 6;
				frame(g, tx, ty, tw, 13, 3, alpha(c.accentColor, 0.9f), alpha(0xFF050B16, 0.8f));
				g.drawString(mc.font, t, tx + 6, ty + 3, 0xFFE6EEFF, true);
			}
		}
	}

	private static void esp(GuiGraphics g, Cam cam, long now) {
		Config c = Config.I;
		double r2 = (double) c.espRange * c.espRange;
		int drawn = 0;
		for (Scanner.Hit hit : Scanner.hits) {
			if (hit.t == 0) continue;
			if (drawn >= c.maxDraw) break;
			double dx = hit.x + 0.5 - cam.eye.x, dy = hit.y + 0.5 - cam.eye.y, dz = hit.z + 0.5 - cam.eye.z;
			if (dx * dx + dy * dy + dz * dz > r2) continue;
			if (dx * cam.fx + dy * cam.fy + dz * cam.fz < -1.5) continue; // behind us
			drawn++;
			boolean fresh = now - hit.t < c.pulseMs;
			block(g, cam, hit, hitColor(hit, now), hitGlow(hit, now), fresh);
		}
	}

	private static void ringWorld(GuiGraphics g, Minecraft mc, Cam cam, long now) {
		if (!Scanner.active) return;
		Config c = Config.I;
		double r = Scanner.sweepR;
		int n = (int) Math.min(260, Math.max(48, r * 1.6));
		float pulse = 0.75f + 0.25f * (float) Math.sin(now * 0.01);
		double y = mc.player.getY() + 0.1;
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			double[] p = cam.toCam(Scanner.ox + Math.cos(a) * r, y, Scanner.oz + Math.sin(a) * r);
			if (p[2] < 0.3) continue;
			int x = Math.round(cam.sx(p)), yy = Math.round(cam.sy(p));
			g.fill(x - 3, yy - 3, x + 3, yy + 3, alpha(c.ringColor, 0.12f * pulse));
			g.fill(x - 1, yy - 1, x + 1, yy + 1, alpha(c.ringColor, pulse));
		}
	}

	// =====================================================================
	// radar / map, styled after the reference: glowing white rim, thin inner rings, soft sweep wedge
	// =====================================================================
	public static void drawRadar(GuiGraphics g, Minecraft mc, int cx, int cy, int R, int zoom, long now, float pt) {
		Config c = Config.I;
		int acc = c.accentColor;

		for (int i = 3; i >= 1; i--) ring(g, cx, cy, R + i * 2, 2, alpha(acc, 0.07f * (4 - i) * Math.max(0.3f, c.glowStrength)));
		disc(g, cx, cy, R, alpha(c.radarBg, 0.82f));
		disc(g, cx, cy, (int) (R * 0.62f), alpha(0xFFFFFFFF, 0.05f));
		ring(g, cx, cy, R / 3, 1, alpha(0xFFFFFFFF, 0.28f));
		ring(g, cx, cy, R * 2 / 3, 1, alpha(0xFFFFFFFF, 0.38f));
		g.fill(cx - R, cy, cx + R + 1, cy + 1, alpha(0xFFFFFFFF, 0.10f));
		g.fill(cx, cy - R, cx + 1, cy + R + 1, alpha(0xFFFFFFFF, 0.10f));

		// sweep wedge with fading trail
		double a0 = (now * 0.0016 * c.animSpeed) % (Math.PI * 2), span = Math.toRadians(c.radarSpan);
		int N = 10;
		for (int i = 0; i < N; i++) {
			float k = 1f - i / (float) N;
			sector(g, cx, cy, R - 1, a0 - span * (i + 1) / N, a0 - span * i / N, alpha(0xFFFFFFFF, 0.30f * k * k));
		}
		for (int rr = 2; rr < R - 1; rr++) {
			int x = cx + (int) Math.round(Math.cos(a0) * rr), y = cy + (int) Math.round(Math.sin(a0) * rr);
			g.fill(x, y, x + 2, y + 2, alpha(0xFFFFFFFF, 0.9f));
		}

		// live scan radius
		if (Scanner.active) {
			int rr = (int) (Scanner.sweepR * R / zoom);
			if (rr > 2 && rr < R) ring(g, cx, cy, rr, 1, alpha(c.ringColor, 0.95f));
		}

		// highlighted blocks
		if (mc.player != null) {
			double px = mc.player.getX(), pz = mc.player.getZ(), yr = Math.toRadians(mc.player.getViewYRot(pt));
			double scale = (double) R / zoom, lim = (double) (R - 3) * (R - 3);
			int drawn = 0;
			for (Scanner.Hit hit : Scanner.hits) {
				if (hit.t == 0) continue;
				if (drawn > 500) break;
				double dx = hit.x + 0.5 - px, dz = hit.z + 0.5 - pz, sx, sy;
				if (c.radarRotate) {
					sx = (dx * -Math.cos(yr) + dz * -Math.sin(yr)) * scale;
					sy = -(dx * -Math.sin(yr) + dz * Math.cos(yr)) * scale;
				} else { sx = dx * scale; sy = dz * scale; }
				if (sx * sx + sy * sy > lim) continue;
				drawn++;
				int x = cx + (int) Math.round(sx), y = cy + (int) Math.round(sy);
				int col = hitColor(hit, now);
				float glow = hitGlow(hit, now) * c.glowStrength;
				g.fill(x - 3, y - 3, x + 4, y + 4, alpha(col, 0.16f * glow));
				g.fill(x - 2, y - 2, x + 3, y + 3, alpha(col, 0.35f * glow + 0.15f));
				g.fill(x - 1, y - 1, x + 2, y + 2, alpha(col, 1.6f));
			}
		}

		ring(g, cx, cy, R, 2, 0xFFFFFFFF); // bright rim
		disc(g, cx, cy, 5, alpha(acc, 0.35f));
		disc(g, cx, cy, 3, 0xFFFFFFFF);
	}
}
