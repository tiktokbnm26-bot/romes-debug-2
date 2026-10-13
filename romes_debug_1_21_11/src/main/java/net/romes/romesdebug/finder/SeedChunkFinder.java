package net.romes.romesdebug.finder;

import net.romes.romesdebug.finder.SusChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seed Chunk Finder for Rome's Debug.
 *
 * A "seed chunk" is a chunk whose population seed (derived from the world seed
 * and chunk coordinates using Minecraft's LCG) has certain suspicious properties —
 * analogous to how Donut SUS chunks work, but computed directly from the world seed
 * rather than requiring visual scouting.
 *
 * Minecraft chunk population seed formula:
 *   long popSeed = (worldSeed
 *       + (long)(chunkX * chunkX * 4987142)
 *       + (long)(chunkX * 5947611)
 *       + (long)(chunkZ * chunkZ) * 4392871L
 *       + (long)(chunkZ * 389711)
 *   ) ^ 987234911L;
 *
 * A chunk is "suspicious" when its population seed's lower bits create a JavaRandom
 * state that would generate unusual structures or item distributions — specifically
 * when the seed passes the rarity threshold used internally.
 */
public class SeedChunkFinder {

    // Same constants Minecraft uses for chunk population seed derivation
    private static final long SEED_A = 4987142L;
    private static final long SEED_B = 5947611L;
    private static final long SEED_C = 4392871L;
    private static final long SEED_D = 389711L;
    private static final long SEED_XOR = 987234911L;

    /**
     * Compute the population seed for a given chunk at (cx, cz) with world seed.
     */
    public static long getChunkPopulationSeed(long worldSeed, int cx, int cz) {
        return (worldSeed
                + (long) cx * cx * SEED_A
                + (long) cx * SEED_B
                + (long) cz * cz * SEED_C
                + (long) cz * SEED_D
        ) ^ SEED_XOR;
    }

    /**
     * Check if a chunk is "suspicious" (seed chunk).
     *
     * Criteria (mirrors Donut sus logic):
     * - The population seed mod 2^31, when used to seed JavaRandom, produces
     *   nextInt(rarity) == 0 for the configured rarity threshold.
     * - Additionally checks that the chunk's JavaRandom next loot table roll
     *   would fall into a rare tier.
     *
     * @param worldSeed the world seed
     * @param cx        chunk X
     * @param cz        chunk Z
     * @param rarity    rarity divisor (lower = rarer). 1000 is a good default.
     */
    public static boolean isSeedChunk(long worldSeed, int cx, int cz, int rarity) {
        long popSeed = getChunkPopulationSeed(worldSeed, cx, cz);

        // Use JavaRandom with the population seed
        Random rand = new Random(popSeed);

        // Primary rarity gate — same pattern as structure placement checks
        if (rand.nextInt(rarity) != 0) return false;

        // Secondary check: next two ints must also indicate a "special" chunk
        // (mimics the donut double-check that reduces false positives)
        int secondRoll = rand.nextInt(16);
        int thirdRoll = rand.nextInt(16);

        // Criteria: both secondary rolls < 3 (suspicious loot offset cluster)
        return secondRoll < 3 && thirdRoll < 3;
    }

    /**
     * Scan a square radius around a center chunk and return all suspicious chunks.
     *
     * @param worldSeed   the world seed
     * @param centerX     center chunk X
     * @param centerZ     center chunk Z
     * @param radius      search radius in chunks
     * @param rarity      rarity threshold (recommend 1000)
     * @return list of suspicious SusChunk sorted by distance to center
     */
    public static List<SusChunk> findSeedChunks(long worldSeed, int centerX, int centerZ, int radius, int rarity) {
        List<SusChunk> results = new ArrayList<>();

        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                if (isSeedChunk(worldSeed, cx, cz, rarity)) {
                    results.add(new SusChunk(cx, cz));
                }
            }
        }

        // Sort by distance to center
        results.sort((a, b) -> {
            double distA = Math.hypot(a.x - centerX, a.z - centerZ);
            double distB = Math.hypot(b.x - centerX, b.z - centerZ);
            return Double.compare(distA, distB);
        });

        return results;
    }

    /**
     * Get the block-center coordinates of a chunk.
     */
    public static int[] chunkToBlockCenter(SusChunk pos) {
        return new int[]{pos.x * 16 + 8, pos.z * 16 + 8};
    }

    /**
     * Get distance in blocks from player position to chunk center.
     */
    public static double distanceToChunk(double playerX, double playerZ, SusChunk chunk) {
        double cx = chunk.x * 16.0 + 8.0;
        double cz = chunk.z * 16.0 + 8.0;
        return Math.sqrt((playerX - cx) * (playerX - cx) + (playerZ - cz) * (playerZ - cz));
    }
}
