package net.romes.romesdebug.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.romes.romesdebug.finder.SusChunk;
import net.romes.romesdebug.finder.SeedChunkFinder;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SeedChunkCommand {

    // Donut SMP world seed
    private static final long DONUT_SEED = 6608149111735331168L;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {

        dispatcher.register(
            literal("romes")
                // /romes donut — hardcoded Donut SMP seed scan from player position
                .then(literal("donut")
                    .executes(ctx -> runDonut(ctx.getSource(), 1500, 800))
                    // /romes donut <radius> — custom radius
                    .then(argument("radius", IntegerArgumentType.integer(1, 5000))
                        .executes(ctx -> runDonut(
                            ctx.getSource(),
                            IntegerArgumentType.getInteger(ctx, "radius"),
                            800
                        ))
                        // /romes donut <radius> <rarity>
                        .then(argument("rarity", IntegerArgumentType.integer(1, 100000))
                            .executes(ctx -> runDonut(
                                ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "radius"),
                                IntegerArgumentType.getInteger(ctx, "rarity")
                            ))
                        )
                    )
                )
                // /romes seedchunk — auto-uses current world seed, scans radius 512 chunks
                .then(literal("seedchunk")
                    .executes(ctx -> runAuto(ctx.getSource()))
                    // /romes seedchunk <seed> <radius> <rarity>
                    .then(argument("seed", LongArgumentType.longArg())
                        .then(argument("radius", IntegerArgumentType.integer(1, 2048))
                            .then(argument("rarity", IntegerArgumentType.integer(1, 100000))
                                .executes(ctx -> runManual(
                                    ctx.getSource(),
                                    LongArgumentType.getLong(ctx, "seed"),
                                    IntegerArgumentType.getInteger(ctx, "radius"),
                                    IntegerArgumentType.getInteger(ctx, "rarity")
                                ))
                            )
                        )
                    )
                )
                // /romes check <seed> <chunkX> <chunkZ> — check single chunk
                .then(literal("check")
                    .then(argument("seed", LongArgumentType.longArg())
                        .then(argument("chunkX", IntegerArgumentType.integer())
                            .then(argument("chunkZ", IntegerArgumentType.integer())
                                .executes(ctx -> runCheck(
                                    ctx.getSource(),
                                    LongArgumentType.getLong(ctx, "seed"),
                                    IntegerArgumentType.getInteger(ctx, "chunkX"),
                                    IntegerArgumentType.getInteger(ctx, "chunkZ")
                                ))
                            )
                        )
                    )
                )
                // /romes help
                .then(literal("help")
                    .executes(ctx -> {
                        sendHelp(ctx.getSource());
                        return 1;
                    })
                )
        );
    }

    private static int runDonut(FabricClientCommandSource source, int radius, int rarity) {
        MinecraftClient client = source.getClient();

        double playerX = (client.player != null) ? client.player.getX() : 0;
        double playerZ = (client.player != null) ? client.player.getZ() : 0;
        int centerCX = (int) Math.floor(playerX) >> 4;
        int centerCZ = (int) Math.floor(playerZ) >> 4;

        source.sendFeedback(Text.literal("[Rome's Debug] ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Donut SMP base finder | seed: ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(Long.toString(DONUT_SEED))
                .formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal(" | radius: " + radius + " chunks | rarity: 1/" + rarity)
                .formatted(Formatting.GRAY)));

        List<SusChunk> results = SeedChunkFinder.findSeedChunks(DONUT_SEED, centerCX, centerCZ, radius, rarity);

        if (results.isEmpty()) {
            source.sendFeedback(Text.literal("[Rome's Debug] No suspicious chunks found. Try increasing radius or lowering rarity.")
                .formatted(Formatting.GREEN));
            return 1;
        }

        source.sendFeedback(Text.literal("[Rome's Debug] ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Found " + results.size() + " suspicious chunk(s) — closest first:")
                .formatted(Formatting.RED)));

        int display = Math.min(results.size(), 15);
        for (int i = 0; i < display; i++) {
            SusChunk pos = results.get(i);
            int[] blockCenter = SeedChunkFinder.chunkToBlockCenter(pos);
            double dist = SeedChunkFinder.distanceToChunk(playerX, playerZ, pos);

            // Direction hint
            double dx = blockCenter[0] - playerX;
            double dz = blockCenter[1] - playerZ;
            String dir = getCardinalDirection(dx, dz);

            source.sendFeedback(Text.literal("  #" + (i + 1) + " ")
                .formatted(Formatting.WHITE)
                .append(Text.literal("Chunk (" + pos.x + ", " + pos.z + ")")
                    .formatted(Formatting.AQUA))
                .append(Text.literal(" → XZ: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("[" + blockCenter[0] + ", " + blockCenter[1] + "]")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(" | " + String.format("%.0f", dist) + "m " + dir)
                    .formatted(Formatting.YELLOW)));
        }

        if (results.size() > 15) {
            source.sendFeedback(Text.literal("  ... and " + (results.size() - 15) + " more. Narrow with: /romes donut " + radius + " " + (rarity / 2))
                .formatted(Formatting.GRAY));
        }

        return 1;
    }

    private static String getCardinalDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        if (angle < 22.5 || angle >= 337.5) return "→E";
        if (angle < 67.5)  return "→SE";
        if (angle < 112.5) return "→S";
        if (angle < 157.5) return "→SW";
        if (angle < 202.5) return "→W";
        if (angle < 247.5) return "→NW";
        if (angle < 292.5) return "→N";
        return "→NE";
    }

    private static int runAuto(FabricClientCommandSource source) {
        MinecraftClient client = source.getClient();

        if (client.world == null) {
            source.sendError(Text.literal("[Rome's Debug] Not in a world."));
            return 0;
        }

        // Get world seed - works on singleplayer; on multiplayer requires /seed to be run first
        long seed = (client.getServer() != null) ? client.getServer().getOverworld().getSeed() : 0L;
        if (seed == 0L) {
            source.sendFeedback(Text.literal("[Rome's Debug] ")
                .formatted(Formatting.RED)
                .append(Text.literal("Could not retrieve world seed automatically. Use: ")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal("/romes seedchunk <seed> <radius> <rarity>")
                    .formatted(Formatting.AQUA)));
            return 0;
        }

        double playerX = source.getPosition().x;
        double playerZ = source.getPosition().z;
        int centerCX = (int) Math.floor(playerX) >> 4;
        int centerCZ = (int) Math.floor(playerZ) >> 4;

        return scan(source, seed, centerCX, centerCZ, 512, 1000, playerX, playerZ);
    }

    private static int runManual(FabricClientCommandSource source, long seed, int radius, int rarity) {
        MinecraftClient client = source.getClient();

        double playerX = (client.player != null) ? client.player.getX() : 0;
        double playerZ = (client.player != null) ? client.player.getZ() : 0;
        int centerCX = (int) Math.floor(playerX) >> 4;
        int centerCZ = (int) Math.floor(playerZ) >> 4;

        return scan(source, seed, centerCX, centerCZ, radius, rarity, playerX, playerZ);
    }

    private static int runCheck(FabricClientCommandSource source, long seed, int cx, int cz) {
        long popSeed = SeedChunkFinder.getChunkPopulationSeed(seed, cx, cz);
        boolean sus = SeedChunkFinder.isSeedChunk(seed, cx, cz, 1000);
        int[] blockCenter = SeedChunkFinder.chunkToBlockCenter(new SusChunk(cx, cz));

        MutableText msg = Text.literal("[Rome's Debug] Chunk ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("(" + cx + ", " + cz + ")")
                .formatted(Formatting.WHITE))
            .append(Text.literal(" | Block center: ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(blockCenter[0] + ", " + blockCenter[1])
                .formatted(Formatting.WHITE))
            .append(Text.literal("\n  Pop seed: ")
                .formatted(Formatting.GRAY))
            .append(Text.literal(Long.toHexString(popSeed).toUpperCase())
                .formatted(Formatting.LIGHT_PURPLE))
            .append(Text.literal("\n  Status: ")
                .formatted(Formatting.GRAY))
            .append(sus
                ? Text.literal("SEED CHUNK (SUS)").formatted(Formatting.RED)
                : Text.literal("Normal").formatted(Formatting.GREEN));

        source.sendFeedback(msg);
        return 1;
    }

    private static int scan(FabricClientCommandSource source, long seed, int centerCX, int centerCZ,
                             int radius, int rarity, double playerX, double playerZ) {

        source.sendFeedback(Text.literal("[Rome's Debug] ")
            .formatted(Formatting.GOLD)
            .append(Text.literal("Scanning " + (radius * 2 + 1) + "x" + (radius * 2 + 1) + " chunks for seed ")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(Long.toString(seed))
                .formatted(Formatting.AQUA))
            .append(Text.literal("...")
                .formatted(Formatting.YELLOW)));

        List<SusChunk> results = SeedChunkFinder.findSeedChunks(seed, centerCX, centerCZ, radius, rarity);

        if (results.isEmpty()) {
            source.sendFeedback(Text.literal("[Rome's Debug] No seed chunks found in this radius.")
                .formatted(Formatting.GREEN));
            return 1;
        }

        source.sendFeedback(Text.literal("[Rome's Debug] Found ")
            .formatted(Formatting.GOLD)
            .append(Text.literal(results.size() + " seed chunk(s)")
                .formatted(Formatting.RED))
            .append(Text.literal(":").formatted(Formatting.GOLD)));

        int display = Math.min(results.size(), 10);
        for (int i = 0; i < display; i++) {
            SusChunk pos = results.get(i);
            int[] blockCenter = SeedChunkFinder.chunkToBlockCenter(pos);
            double dist = SeedChunkFinder.distanceToChunk(playerX, playerZ, pos);
            long popSeed = SeedChunkFinder.getChunkPopulationSeed(seed, pos.x, pos.z);

            source.sendFeedback(Text.literal("  #" + (i + 1) + " ")
                .formatted(Formatting.WHITE)
                .append(Text.literal("Chunk (" + pos.x + ", " + pos.z + ")")
                    .formatted(Formatting.AQUA))
                .append(Text.literal(" → Block ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal("(" + blockCenter[0] + ", " + blockCenter[1] + ")")
                    .formatted(Formatting.WHITE))
                .append(Text.literal(" | dist: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(String.format("%.0f", dist) + " blocks")
                    .formatted(Formatting.YELLOW))
                .append(Text.literal(" | seed: ")
                    .formatted(Formatting.GRAY))
                .append(Text.literal(Long.toHexString(popSeed).toUpperCase())
                    .formatted(Formatting.LIGHT_PURPLE)));
        }

        if (results.size() > 10) {
            source.sendFeedback(Text.literal("  ... and " + (results.size() - 10) + " more.")
                .formatted(Formatting.GRAY));
        }

        return 1;
    }

    private static void sendHelp(FabricClientCommandSource source) {
        source.sendFeedback(Text.literal("=== Rome's Debug — Seed Chunk Finder ===").formatted(Formatting.GOLD));
        source.sendFeedback(Text.literal("/romes donut").formatted(Formatting.RED)
            .append(Text.literal(" — Donut SMP base finder (hardcoded seed, 1500 chunk radius)").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/romes donut <radius>").formatted(Formatting.RED)
            .append(Text.literal(" — Donut SMP scan with custom radius").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/romes donut <radius> <rarity>").formatted(Formatting.RED)
            .append(Text.literal(" — Donut SMP scan, custom radius + rarity").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/romes seedchunk").formatted(Formatting.AQUA)
            .append(Text.literal(" — scan around you using auto-detected seed (SP/OP only)").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/romes seedchunk <seed> <radius> <rarity>").formatted(Formatting.AQUA)
            .append(Text.literal(" — manual scan with any seed").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("/romes check <seed> <chunkX> <chunkZ>").formatted(Formatting.AQUA)
            .append(Text.literal(" — check a specific chunk").formatted(Formatting.GRAY)));
        source.sendFeedback(Text.literal("Donut seed: " + DONUT_SEED).formatted(Formatting.LIGHT_PURPLE));
    }
}
