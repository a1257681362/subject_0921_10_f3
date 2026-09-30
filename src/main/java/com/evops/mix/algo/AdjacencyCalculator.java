package com.evops.mix.algo;

import com.evops.mix.common.MixConstants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 相邻关系统计：只统计同一考场内座号相邻（跨考场不算相邻）。
 */
public final class AdjacencyCalculator {

    private AdjacencyCalculator() {
    }

    /**
     * @param orderedSeats 已按考场顺序、座号顺序排好的座次列表
     */
    public static AdjacencyStats compute(List<MixAssignedSeat> orderedSeats) {
        int n = orderedSeats.size();
        boolean[] sameLeft = new boolean[n];
        boolean[] sameRight = new boolean[n];
        int pairs = 0;
        int samePairs = 0;
        Set<Integer> involved = new HashSet<>();

        for (int i = 0; i < n - 1; i++) {
            MixAssignedSeat a = orderedSeats.get(i);
            MixAssignedSeat b = orderedSeats.get(i + 1);
            // 仅同一考场内、座号相邻才算
            if (!a.getRoom().getRoomId().equals(b.getRoom().getRoomId())
                    || b.getSeatNo() != a.getSeatNo() + 1) {
                continue;
            }
            pairs++;
            String ka = SchoolKeys.of(a.getStudent());
            String kb = SchoolKeys.of(b.getStudent());
            boolean same = SchoolKeys.isCountable(ka) && ka.equals(kb);
            if (same) {
                samePairs++;
                involved.add(i);
                involved.add(i + 1);
                sameRight[i] = true;
                sameLeft[i + 1] = true;
            }
        }

        List<String> flags = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (sameLeft[i] && sameRight[i]) {
                flags.add(MixConstants.ADJ_SAME_BOTH);
            } else if (sameLeft[i]) {
                flags.add(MixConstants.ADJ_SAME_LEFT);
            } else if (sameRight[i]) {
                flags.add(MixConstants.ADJ_SAME_RIGHT);
            } else if (hasNeighbor(orderedSeats, i)) {
                flags.add(MixConstants.ADJ_OK);
            } else {
                flags.add(MixConstants.ADJ_NONE);
            }
        }
        return new AdjacencyStats(pairs, samePairs, involved.size(), flags);
    }

    /** 该座在同考场内是否有左邻或右邻（有相邻但非同校才标 OK，无任何邻座用 NONE）。 */
    private static boolean hasNeighbor(List<MixAssignedSeat> seats, int i) {
        int n = seats.size();
        if (i > 0) {
            MixAssignedSeat a = seats.get(i - 1);
            MixAssignedSeat b = seats.get(i);
            if (a.getRoom().getRoomId().equals(b.getRoom().getRoomId())
                    && b.getSeatNo() == a.getSeatNo() + 1) {
                return true;
            }
        }
        if (i < n - 1) {
            MixAssignedSeat a = seats.get(i);
            MixAssignedSeat b = seats.get(i + 1);
            if (a.getRoom().getRoomId().equals(b.getRoom().getRoomId())
                    && b.getSeatNo() == a.getSeatNo() + 1) {
                return true;
            }
        }
        return false;
    }
}
