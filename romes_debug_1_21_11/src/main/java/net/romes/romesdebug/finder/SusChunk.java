package net.romes.romesdebug.finder;

public final class SusChunk {
    public final int x;
    public final int z;

    public SusChunk(int x, int z) {
        this.x = x;
        this.z = z;
    }

    public long toLong() {
        return ((long) x & 0xFFFFFFFFL) | (((long) z & 0xFFFFFFFFL) << 32);
    }
}
