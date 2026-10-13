package net.romes.romesdebug.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.romes.romesdebug.finder.SusChunk;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.romes.romesdebug.RomesAddon;
import net.romes.romesdebug.finder.SeedChunkFinder;
import net.romes.romesdebug.gui.ToastManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SeedSusChunks extends Module {

    // must be declared before the settings (used in their onChanged lambdas)
    private volatile boolean dirty = true;
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private volatile List<SusChunk> results = List.of();
    private int lastScanCX = Integer.MIN_VALUE, lastScanCZ = Integer.MIN_VALUE;
    private boolean warnedBadSeed = false;
    private final Set<Long> notified = new HashSet<>();

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "romes-seed-scan");
        t.setDaemon(true);
        return t;
    });

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgNotify = settings.createGroup("Notifications");

    // ---- General ----
    private final Setting<String> seed = sgGeneral.add(new StringSetting.Builder()
        .name("seed")
        .description("World seed. Defaults to the Donut SMP seed.")
        .defaultValue("6608149111735331168")
        .onChanged(v -> { dirty = true; warnedBadSeed = false; notified.clear(); })
        .build());

    private final Setting<Integer> scanRadius = sgGeneral.add(new IntSetting.Builder()
        .name("scan-radius")
        .description("Scan radius in chunks around you.")
        .defaultValue(1500).min(1).max(2048).sliderMin(50).sliderMax(2048)
        .onChanged(v -> dirty = true)
        .build());

    private final Setting<Integer> rarity = sgGeneral.add(new IntSetting.Builder()
        .name("rarity")
        .description("1 in N rarity gate. Lower = more chunks flagged.")
        .defaultValue(800).min(1).max(100000).sliderMin(1).sliderMax(5000)
        .onChanged(v -> dirty = true)
        .build());

    private final Setting<Integer> rescanDistance = sgGeneral.add(new IntSetting.Builder()
        .name("rescan-distance")
        .description("Rescan after moving this many chunks from the last scan center.")
        .defaultValue(512).min(16).max(2048).sliderMin(16).sliderMax(1024)
        .build());

    // ---- Render ----
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the chunk column is drawn.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color").description("Main ESP color.")
        .defaultValue(new SettingColor(255, 30, 30, 255)).build());

    private final Setting<Integer> fillAlpha = sgRender.add(new IntSetting.Builder()
        .name("fill-alpha").description("Chunk column fill opacity.")
        .defaultValue(18).min(0).max(255).sliderMax(100).build());

    private final Setting<Boolean> beam = sgRender.add(new BoolSetting.Builder()
        .name("beam").description("Vertical red beam at the chunk center.")
        .defaultValue(true).build());

    private final Setting<Double> beamWidth = sgRender.add(new DoubleSetting.Builder()
        .name("beam-width").description("Beam thickness in blocks.")
        .defaultValue(0.4).min(0.05).max(3).sliderMax(2).visible(beam::get).build());

    private final Setting<Boolean> rings = sgRender.add(new BoolSetting.Builder()
        .name("rings").description("Rotating dashed rings + expanding pulse on the ground.")
        .defaultValue(true).build());

    private final Setting<Double> ringSize = sgRender.add(new DoubleSetting.Builder()
        .name("ring-size").description("Outer ring radius in blocks.")
        .defaultValue(12.0).min(2).max(64).sliderMax(32).visible(rings::get).build());

    private final Setting<Double> spinSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("spin-speed").description("Ring rotation speed.")
        .defaultValue(0.6).min(0).max(5).sliderMax(3).visible(rings::get).build());

    private final Setting<Boolean> tracers = sgRender.add(new BoolSetting.Builder()
        .name("tracers").description("Line from your crosshair to each chunk.")
        .defaultValue(true).build());


    private final Setting<Boolean> spokes = sgRender.add(new BoolSetting.Builder()
        .name("spokes").description("Thin lines radiating up and out from the beam base.")
        .defaultValue(true).build());

    private final Setting<Boolean> dome = sgRender.add(new BoolSetting.Builder()
        .name("dome").description("Faint rotating arcs over the chunk.")
        .defaultValue(true).build());

    private final Setting<Boolean> diamonds = sgRender.add(new BoolSetting.Builder()
        .name("diamonds").description("Small floating wireframe diamonds around the chunk.")
        .defaultValue(true).build());

    private final Setting<Integer> maxChunks = sgRender.add(new IntSetting.Builder()
        .name("max-chunks").description("Max chunks drawn at once.")
        .defaultValue(40).min(1).max(500).sliderMax(200).build());

    private final Setting<Integer> maxDistance = sgRender.add(new IntSetting.Builder()
        .name("max-distance").description("Don't draw chunks farther than this (blocks).")
        .defaultValue(1500).min(64).max(30000).sliderMax(5000).build());

    // ---- Notifications ----
    private final Setting<Boolean> notify = sgNotify.add(new BoolSetting.Builder()
        .name("toast").description("Show a \"Seed Chunk Found\" toast with the RM logo.")
        .defaultValue(true).build());

    private final Setting<Integer> notifyDistance = sgNotify.add(new IntSetting.Builder()
        .name("toast-distance").description("Only announce chunks within this many blocks of you.")
        .defaultValue(1000).min(64).max(30000).sliderMax(5000).visible(notify::get).build());

    private final Setting<Double> toastSeconds = sgNotify.add(new DoubleSetting.Builder()
        .name("toast-duration").description("How long each toast stays (seconds).")
        .defaultValue(5.0).min(1).max(15).sliderMax(10).visible(notify::get).build());

    private final Setting<Integer> maxQueued = sgNotify.add(new IntSetting.Builder()
        .name("max-queued").description("Max toasts waiting at once (prevents spam).")
        .defaultValue(3).min(1).max(10).sliderMax(6).visible(notify::get).build());

    public SeedSusChunks() {
        super(RomesAddon.CATEGORY, "seed-sus-chunks",
            "Highlights seed-flagged (sus) chunks with a red beam, rings and outlines.");
    }

    @Override
    public void onActivate() {
        dirty = true;
    }

    @Override
    public void onDeactivate() {
        results = List.of();
        lastScanCX = lastScanCZ = Integer.MIN_VALUE;
        notified.clear();
        ToastManager.clear();
    }

    @Override
    public String getInfoString() {
        return scanning.get() ? "scanning" : Integer.toString(results.size());
    }

    // ---------------------------------------------------------------- scanning

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;

        checkNotify();

        boolean moved = lastScanCX == Integer.MIN_VALUE
            || Math.abs(cx - lastScanCX) > rescanDistance.get()
            || Math.abs(cz - lastScanCZ) > rescanDistance.get();

        if (!(dirty || moved)) return;
        if (!scanning.compareAndSet(false, true)) return;

        long seedValue;
        try {
            seedValue = Long.parseLong(seed.get().trim());
        } catch (NumberFormatException e) {
            scanning.set(false);
            if (!warnedBadSeed) { error("Invalid seed: %s", seed.get()); warnedBadSeed = true; }
            dirty = false;
            return;
        }

        dirty = false;
        lastScanCX = cx;
        lastScanCZ = cz;

        final int radius = scanRadius.get();
        final int rar = rarity.get();
        final int fcx = cx, fcz = cz;

        POOL.submit(() -> {
            try {
                results = SeedChunkFinder.findSeedChunks(seedValue, fcx, fcz, radius, rar);
            } finally {
                scanning.set(false);
            }
        });
    }

    private void checkNotify() {
        if (!notify.get()) return;
        List<SusChunk> list = results;
        if (list.isEmpty()) return;

        double px = mc.player.getX(), pz = mc.player.getZ();
        double maxD = notifyDistance.get();

        for (SusChunk pos : list) {
            if (ToastManager.pending() >= maxQueued.get()) break;
            if (notified.contains(pos.toLong())) continue;

            int mx = pos.x * 16 + 8, mz = pos.z * 16 + 8;
            if (Math.hypot(mx - px, mz - pz) > maxD) continue;

            notified.add(pos.toLong());
            ToastManager.push("Seed Chunk Found", mx + ", " + mz, (long) (toastSeconds.get() * 1000));
        }
    }

    // --------------------------------------------------------------- rendering

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        List<SusChunk> list = results;
        if (list.isEmpty()) return;

        double px = mc.player.getX(), pz = mc.player.getZ();
        double t = System.nanoTime() / 1_000_000_000.0;
        double rot = t * spinSpeed.get();
        double pulse = (t * 0.5) % 1.0;

        int bottom = mc.world.getBottomY();
        int top = bottom + mc.world.getHeight();

        Vec3d start = mc.player.getEyePos().add(mc.player.getRotationVec(1.0f));

        SettingColor c = color.get();
        Color line = new Color(c.r, c.g, c.b, c.a);
        Color fill = new Color(c.r, c.g, c.b, fillAlpha.get());
        Color beamFill = new Color(c.r, c.g, c.b, 120);
        Color dim = new Color(c.r, c.g, c.b, Math.max(40, c.a / 3));

        int drawn = 0;
        for (SusChunk pos : list) {
            if (drawn >= maxChunks.get()) break;

            double x0 = pos.x * 16.0, z0 = pos.z * 16.0;
            double mx = x0 + 8.0, mz = z0 + 8.0;
            double pzPrev = 0;
            if (Math.hypot(mx - px, mz - pz) > maxDistance.get()) continue;
            drawn++;

            // ground height (falls back to player Y if the chunk isn't loaded)
            double gy = mc.player.getY();
            if (mc.world.getChunkManager().isChunkLoaded(pos.x, pos.z)) {
                gy = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING, (int) mx, (int) mz);
            }

            // chunk column
            event.renderer.box(x0, bottom, z0, x0 + 16, top, z0 + 16, fill, line, shapeMode.get(), 0);

            // beam
            if (beam.get()) {
                double w = beamWidth.get() / 2.0;
                event.renderer.box(mx - w, gy, mz - w, mx + w, top, mz + w, beamFill, line, ShapeMode.Both, 0);
            }

            // rings
            if (rings.get()) {
                double r = ringSize.get();
                thickRing(event, mx, gy + 0.1, mz, r, 36, rot, 0.65, line);
                thickRing(event, mx, gy + 0.1, mz, r * 0.5, 24, -rot * 1.4, 0.5, line);
                thickRing(event, mx, gy + 0.1, mz, r * 1.6, 48, rot * 0.5, 0.35, dim);

                // expanding pulse
                Color pc = new Color(c.r, c.g, c.b, (int) (200 * (1.0 - pulse)));
                ring(event, mx, gy + 0.1, mz, 1.0 + pulse * r * 2.2, 64, 0, 1.0, pc);
            }

            // spokes
            if (spokes.get()) {
                double R = ringSize.get() * 1.6;
                for (int i = 0; i < 8; i++) {
                    double a = rot * 0.4 + i * (Math.PI / 4.0);
                    double h = 6 + (i % 3) * 5;
                    event.renderer.line(mx, gy + 1, mz,
                        mx + Math.cos(a) * R, gy + h, mz + Math.sin(a) * R, dim);
                }
            }

            // dome arcs
            if (dome.get()) {
                double R2 = ringSize.get() * 2.5;
                for (int i = 0; i < 3; i++) {
                    double a = rot * 0.25 + i * (Math.PI / 3.0);
                    double ca = Math.cos(a), sa = Math.sin(a);
                    double px0 = 0, py0 = 0; boolean first = true;
                    for (int j = 0; j <= 24; j++) {
                        double u = Math.PI * j / 24.0;
                        double hr = R2 * Math.cos(u), hy = 26 * Math.sin(u);
                        double wx = mx + ca * hr, wz = mz + sa * hr, wy = gy + hy;
                        if (!first) {
                            event.renderer.line(px0, py0, pzPrev, wx, wy, wz, dim);
                        }
                        px0 = wx; py0 = wy; pzPrev = wz; first = false;
                    }
                }
            }

            // floating diamonds
            if (diamonds.get()) {
                java.util.Random dr = new java.util.Random(pos.toLong());
                for (int i = 0; i < 10; i++) {
                    double a = dr.nextDouble() * Math.PI * 2;
                    double rr = 5 + dr.nextDouble() * ringSize.get() * 2.0;
                    double hy = gy + 2 + dr.nextDouble() * 14 + Math.sin(t * 1.5 + i) * 0.6;
                    diamond(event, mx + Math.cos(a) * rr, hy, mz + Math.sin(a) * rr, 0.7, line);
                }
            }

            // tracer
            if (tracers.get()) {
                event.renderer.line(start.x, start.y, start.z, mx, gy + 1.0, mz, line);
            }
        }
    }

    private void diamond(Render3DEvent e, double x, double y, double z, double sz, Color col) {
        double w = sz * 0.6;
        double[][] eq = { {x + w, z}, {x, z + w}, {x - w, z}, {x, z - w} };
        for (int i = 0; i < 4; i++) {
            double[] a = eq[i], b = eq[(i + 1) % 4];
            e.renderer.line(a[0], y, a[1], b[0], y, b[1], col);
            e.renderer.line(x, y + sz, z, a[0], y, a[1], col);
            e.renderer.line(x, y - sz, z, a[0], y, a[1], col);
        }
    }

    private void thickRing(Render3DEvent e, double cx, double y, double cz, double r,
                           int segs, double rot, double dash, Color col) {
        ring(e, cx, y, cz, r, segs, rot, dash, col);
        ring(e, cx, y, cz, r + 0.12, segs, rot, dash, col);
        ring(e, cx, y, cz, r + 0.24, segs, rot, dash, col);
    }

    private void ring(Render3DEvent e, double cx, double y, double cz, double r,
                      int segs, double rot, double dash, Color col) {
        double step = Math.PI * 2.0 / segs;
        for (int i = 0; i < segs; i++) {
            double a0 = rot + i * step;
            double a1 = a0 + step * dash;
            e.renderer.line(
                cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r,
                cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r,
                col);
        }
    }
}
