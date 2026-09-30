package com.evops.mix.algo;

/**
 * 混编算法运行参数（从已发布版本的冻结参数中解析）。
 */
public class MixAlgoParams {
    private final long seed;
    private final int maxRounds;

    public MixAlgoParams(long seed, int maxRounds) {
        this.seed = seed;
        this.maxRounds = maxRounds;
    }

    public long getSeed() { return seed; }
    public int getMaxRounds() { return maxRounds; }
}
