package com.evops.mix.algo;

import java.util.List;

/**
 * 同校相邻统计结果（巡考抽查用）。
 */
public class AdjacencyStats {
    /** 全部相邻座位对数（仅同一考场内座号相邻） */
    private final int adjacentPairs;
    /** 其中同校学员相邻的对数 */
    private final int sameSchoolPairs;
    /** 涉及同校相邻的学员人数（去重） */
    private final int sameSchoolPeople;
    /** 逐座相邻标记，下标与座次列表对齐：NONE / OK / SAME_LEFT / SAME_RIGHT / SAME_BOTH */
    private final List<String> flags;

    public AdjacencyStats(int adjacentPairs, int sameSchoolPairs, int sameSchoolPeople, List<String> flags) {
        this.adjacentPairs = adjacentPairs;
        this.sameSchoolPairs = sameSchoolPairs;
        this.sameSchoolPeople = sameSchoolPeople;
        this.flags = flags;
    }

    public int getAdjacentPairs() { return adjacentPairs; }
    public int getSameSchoolPairs() { return sameSchoolPairs; }
    public int getSameSchoolPeople() { return sameSchoolPeople; }
    public List<String> getFlags() { return flags; }
}
