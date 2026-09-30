package com.evops.mix.algo;

import com.evops.common.BusinessException;
import com.evops.mix.common.MixConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * schoolShuffleV1：同校错开相邻座位的确定性混编。
 *
 * <p>做法：
 * <ol>
 *   <li>按学校分组，组内与组间顺序都用种子驱动的洗牌（Random(seed)），同种子同名册结果可复现；</li>
 *   <li>各校轮转发牌（round-robin）铺到全局座位序列，同校学员被均匀拉开，
 *       单间考场装不下时自然按比例摊到后面的多间考场；无学校学员作为中性单人组穿插；</li>
 *   <li>贪心交换下降：只在“同考场且座号相邻”的边上计同校相邻对数，
 *       每轮找一个能降低对数的交换，直到无法再降或达到最大轮数。</li>
 * </ol>
 * 随机性只来自参数种子，故（名册、考场、种子）相同则座次完全一致。
 */
@Component
public class SchoolShuffleV1 implements SeatMixAlgorithm {

    public static final int DEFAULT_MAX_ROUNDS = 500;

    /** 多起点尝试次数：从同一主种子派生，保证结果确定。 */
    private static final int MULTI_START = 4;

    @Override
    public String key() {
        return MixConstants.IMPL_SCHOOL_SHUFFLE_V1;
    }

    @Override
    public List<MixAssignedSeat> arrange(List<MixStudent> students, List<MixRoom> rooms, MixAlgoParams params) {
        int n = students.size();
        if (n == 0) {
            return new ArrayList<>();
        }
        int totalCapacity = 0;
        for (MixRoom room : rooms) {
            totalCapacity += room.getCapacity();
        }
        if (totalCapacity < n) {
            // 容量预检 / 整批拒收在服务层处理；算法不排半成品
            throw new BusinessException("CAPACITY_EXCEEDED",
                    "考位不足：报名 " + n + " 人，可用考位 " + totalCapacity + " 个");
        }

        int maxRounds = params.getMaxRounds() > 0 ? params.getMaxRounds() : DEFAULT_MAX_ROUNDS;

        // 1) 位置 -> 全局座位（考场、座号）映射：按考场顺序连续铺座
        int[] roomOfPos = new int[n];
        int[] seatNoOfPos = new int[n];
        int pos = 0;
        for (int r = 0; r < rooms.size() && pos < n; r++) {
            for (int s = 1; s <= rooms.get(r).getCapacity() && pos < n; s++) {
                roomOfPos[pos] = r;
                seatNoOfPos[pos] = s;
                pos++;
            }
        }

        boolean[] edgeWithinRoom = new boolean[Math.max(0, n - 1)];
        for (int i = 0; i < n - 1; i++) {
            edgeWithinRoom[i] = roomOfPos[i] == roomOfPos[i + 1];
        }

        // 2) 多种子起点（仍只由版本种子派生，结果确定可复现）：
        //    轮转发牌 + 贪心下降，取同校相邻对数最少的一个；首个达到 0 的立即采用。
        MixStudent[] arranged = null;
        int bestCost = Integer.MAX_VALUE;
        for (int attempt = 0; attempt < MULTI_START && bestCost > 0; attempt++) {
            Random random = new Random(diversifySeed(params.getSeed(), attempt));
            MixStudent[] candidate = buildRoundRobin(students, random);
            String[] keys = new String[n];
            for (int i = 0; i < n; i++) {
                keys[i] = SchoolKeys.of(candidate[i]);
            }
            optimize(candidate, keys, edgeWithinRoom, random, maxRounds);
            int cost = totalSameSchoolCost(keys, edgeWithinRoom);
            if (cost < bestCost) {
                bestCost = cost;
                arranged = candidate;
            }
        }

        // 3) 产出座次（按座位顺序）
        List<MixAssignedSeat> result = new ArrayList<>(n);
        for (int p = 0; p < n; p++) {
            MixRoom room = rooms.get(roomOfPos[p]);
            result.add(new MixAssignedSeat(arranged[p], room, seatNoOfPos[p], p + 1));
        }
        return result;
    }

    /** 由主种子派生第 attempt 个起点的种子（确定性，不引入系统随机源）。 */
    private long diversifySeed(long seed, int attempt) {
        if (attempt == 0) {
            return seed;
        }
        long mixed = seed ^ (0x9E3779B97F4A7C15L * (attempt + 1L));
        mixed ^= (mixed >>> 29);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 32);
        return mixed;
    }

    private int totalSameSchoolCost(String[] keys, boolean[] edgeWithinRoom) {
        int cost = 0;
        for (int i = 0; i < keys.length - 1; i++) {
            if (edgeWithinRoom[i] && isSameSchool(keys[i], keys[i + 1])) {
                cost++;
            }
        }
        return cost;
    }

    // ---------- 轮转发牌 ----------

    private MixStudent[] buildRoundRobin(List<MixStudent> students, Random random) {
        // 有学校：按校聚合；无学校：每人一个中性单人组，充当天然间隔
        Map<String, List<MixStudent>> grouped = new LinkedHashMap<>();
        List<MixStudent> neutral = new ArrayList<>();
        for (MixStudent s : students) {
            String key = SchoolKeys.of(s);
            if (SchoolKeys.isCountable(key)) {
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            } else {
                neutral.add(s);
            }
        }

        List<List<MixStudent>> decks = new ArrayList<>();
        for (List<MixStudent> g : grouped.values()) {
            List<MixStudent> shuffled = new ArrayList<>(g);
            Collections.shuffle(shuffled, random);
            decks.add(shuffled);
        }
        // 无学校学员打散成单人组，避免他们自己成片
        Collections.shuffle(neutral, random);
        for (MixStudent s : neutral) {
            List<MixStudent> single = new ArrayList<>(1);
            single.add(s);
            decks.add(single);
        }
        Collections.shuffle(decks, random);

        int n = students.size();
        MixStudent[] out = new MixStudent[n];
        int[] cursor = new int[decks.size()];
        int start = decks.isEmpty() ? 0 : random.nextInt(decks.size());
        int filled = 0;
        int guard = 0;
        int groupCount = decks.size();
        while (filled < n) {
            boolean moved = false;
            for (int step = 0; step < groupCount; step++) {
                int g = (start + step) % groupCount;
                if (cursor[g] < decks.get(g).size()) {
                    out[filled++] = decks.get(g).get(cursor[g]++);
                    moved = true;
                    if (filled == n) {
                        break;
                    }
                }
            }
            // 下一轮换个学校开头，进一步打散边界
            start = groupCount == 0 ? 0 : (start + 1) % groupCount;
            if (!moved || ++guard > n + 1) {
                break;
            }
        }
        return out;
    }

    // ---------- 贪心交换下降 ----------

    private void optimize(MixStudent[] arranged, String[] keys, boolean[] edgeWithinRoom,
                          Random random, int maxRounds) {
        int n = arranged.length;
        List<Integer> partnerOrder = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            partnerOrder.add(i);
        }

        for (int round = 0; round < maxRounds; round++) {
            // 收集仍同校相邻的边及其端点
            List<Integer> badPositions = new ArrayList<>();
            for (int i = 0; i < n - 1; i++) {
                if (edgeWithinRoom[i] && isSameSchool(keys[i], keys[i + 1])) {
                    if (!badPositions.contains(i)) {
                        badPositions.add(i);
                    }
                    if (!badPositions.contains(i + 1)) {
                        badPositions.add(i + 1);
                    }
                }
            }
            if (badPositions.isEmpty()) {
                return;
            }
            Collections.shuffle(badPositions, random);

            Collections.shuffle(partnerOrder, random);
            boolean improved = false;
            outer:
            for (int i : badPositions) {
                for (int j : partnerOrder) {
                    if (j == i) {
                        continue;
                    }
                    int a = Math.min(i, j);
                    int b = Math.max(i, j);
                    int[] edges = affectedEdges(a, b);
                    int before = edgesCost(edges, keys, edgeWithinRoom);
                    swap(keys, a, b);
                    int after = edgesCost(edges, keys, edgeWithinRoom);
                    if (after < before) {
                        swap(arranged, a, b);
                        improved = true;
                        break outer;
                    }
                    swap(keys, a, b); // 换回
                }
            }
            if (!improved) {
                return; // 局部最优：没有任何单点交换能再降
            }
        }
    }

    /** 交换 a/b（a<b）后只有贴着 a 或 b 的边可能变化；相邻交换时 a/b 共边须去重。 */
    private int[] affectedEdges(int a, int b) {
        int[] raw = {a - 1, a, b - 1, b};
        int[] distinct = new int[4];
        int count = 0;
        for (int edge : raw) {
            boolean dup = false;
            for (int k = 0; k < count; k++) {
                if (distinct[k] == edge) {
                    dup = true;
                    break;
                }
            }
            if (!dup) {
                distinct[count++] = edge;
            }
        }
        int[] result = new int[count];
        System.arraycopy(distinct, 0, result, 0, count);
        return result;
    }

    private int edgesCost(int[] edges, String[] keys, boolean[] edgeWithinRoom) {
        int n = keys.length;
        int cost = 0;
        for (int e : edges) {
            cost += edgeCost(e, n, keys, edgeWithinRoom);
        }
        return cost;
    }

    private int edgeCost(int e, int n, String[] keys, boolean[] edgeWithinRoom) {
        if (e < 0 || e >= n - 1 || !edgeWithinRoom[e]) {
            return 0;
        }
        return isSameSchool(keys[e], keys[e + 1]) ? 1 : 0;
    }

    private boolean isSameSchool(String k1, String k2) {
        return SchoolKeys.isCountable(k1) && k1.equals(k2);
    }

    private void swap(Object[] arr, int i, int j) {
        Object t = arr[i];
        arr[i] = arr[j];
        arr[j] = t;
    }
}
