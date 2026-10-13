package net.romes.romesdebug.gui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;

/**
 * "Seed Chunk Found" toast: dark navy card, glowing border, RM logo, pin + coords,
 * and a progress bar with a knob that fills over the toast's lifetime.
 */
public class ToastManager {

    private static final Identifier LOGO = Identifier.of("romesdebug", "textures/gui/logo.png");

    // colors (ARGB)
    private static final int BG_TOP    = 0xF0121A2E;
    private static final int BG_BOTTOM = 0xF01E1840;
    private static final int BORDER    = 0xFF7AA2FF;
    private static final int GLOW      = 0x287AA2FF;
    private static final int LOGO_BG   = 0xFF0A0A12;
    private static final int TITLE     = 0xFFFFFFFF;
    private static final int COORDS    = 0xFF6FB6FF;
    private static final int PIN       = 0xFFB06CFF;
    private static final int BAR_TRACK = 0x40FFFFFF;
    private static final int BAR_A     = 0xFF4FC3FF;
    private static final int BAR_B     = 0xFFB06CFF;

    private static final int W = 196, H = 52, MARGIN = 10, SLIDE_MS = 300;

    private static final class Toast {
        final String title, sub;
        final long durationMs;
        long start;
        Toast(String title, String sub, long durationMs) {
            this.title = title; this.sub = sub; this.durationMs = durationMs;
        }
    }

    private static final ArrayDeque<Toast> QUEUE = new ArrayDeque<>();
    private static Toast current;

    public static void init() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> render(ctx));
    }

    public static synchronized void push(String title, String sub, long durationMs) {
        QUEUE.add(new Toast(title, sub, Math.max(1000, durationMs)));
    }

    public static synchronized int pending() {
        return QUEUE.size() + (current != null ? 1 : 0);
    }

    public static synchronized void clear() {
        QUEUE.clear();
        current = null;
    }

    // ------------------------------------------------------------------ render

    private static synchronized void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;

        long now = System.currentTimeMillis();
        if (current == null || now - current.start > current.durationMs) {
            current = QUEUE.poll();
            if (current == null) return;
            current.start = now;
        }

        long elapsed = now - current.start;
        float progress = Math.min(1f, elapsed / (float) current.durationMs);
        float in  = ease(Math.min(1f, elapsed / (float) SLIDE_MS));
        float out = ease(Math.min(1f, (current.durationMs - elapsed) / (float) SLIDE_MS));
        float k = Math.min(in, out);

        int sw = ctx.getScaledWindowWidth();
        int x = sw - W - MARGIN + (int) ((1f - k) * (W + MARGIN * 2));
        int y = MARGIN;

        TextRenderer tr = mc.textRenderer;

        // glow
        rrect(ctx, x - 4, y - 4, W + 8, H + 8, 10, GLOW, GLOW);
        rrect(ctx, x - 2, y - 2, W + 4, H + 4, 8, GLOW, GLOW);
        // border + body
        rrect(ctx, x - 1, y - 1, W + 2, H + 2, 7, BORDER, 0xFFB06CFF);
        rrect(ctx, x, y, W, H, 6, BG_TOP, BG_BOTTOM);

        // logo box
        int lx = x + 8, ly = y + 7, ls = 34;
        rrect(ctx, lx, ly, ls, ls, 5, LOGO_BG, LOGO_BG);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, lx + 2, ly + 2, 0f, 0f, ls - 4, ls - 4, 128, 128, 128, 128);

        // text
        int tx = lx + ls + 10;
        ctx.drawText(tr, current.title, tx, y + 10, TITLE, false);
        rrect(ctx, tx, y + 24, 7, 7, 3, PIN, PIN);
        rrect(ctx, tx + 2, y + 26, 3, 3, 1, 0xFF1A1030, 0xFF1A1030);
        ctx.drawText(tr, current.sub, tx + 11, y + 23, COORDS, false);

        // progress bar
        int bx = x + 8, bw = W - 16, by = y + H - 9;
        ctx.fill(bx, by, bx + bw, by + 3, BAR_TRACK);
        int fw = (int) (bw * progress);
        for (int i = 0; i < fw; i++) {
            ctx.fill(bx + i, by, bx + i + 1, by + 3, lerp(BAR_A, BAR_B, i / (float) bw));
        }
        int kx = bx + fw;
        rrect(ctx, kx - 5, by - 4, 11, 11, 5, 0x55B06CFF, 0x55B06CFF);
        rrect(ctx, kx - 2, by - 1, 5, 5, 2, 0xFFFFFFFF, 0xFFFFFFFF);
    }

    // ----------------------------------------------------------------- helpers

    private static float ease(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    /** Rounded rectangle with a vertical gradient, drawn row by row. */
    private static void rrect(DrawContext c, int x, int y, int w, int h, int r, int top, int bot) {
        for (int i = 0; i < h; i++) {
            int inset = 0;
            if (i < r) {
                double dy = r - i - 0.5;
                inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            } else if (i >= h - r) {
                double dy = i - (h - r) + 0.5;
                inset = (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, lerp(top, bot, i / (float) Math.max(1, h - 1)));
        }
    }

    private static int lerp(int a, int b, float t) {
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24)
             | ((int) (ar + (br - ar) * t) << 16)
             | ((int) (ag + (bg - ag) * t) << 8)
             |  (int) (ab + (bb - ab) * t);
    }
}
