package com.evops.mix.algo;

import java.util.List;

/**
 * 座次混编算法：把学员排进有序考场，输出一人一座。
 * 实现必须保证对相同（学员、考场、参数）结果确定可复现——随机性只允许来自参数中的种子。
 */
public interface SeatMixAlgorithm {

    /** 实现标识，对应 t_mix_algorithm_version.impl_key。 */
    String key();

    /**
     * 执行混编。
     *
     * @param students 学员（含学校）
     * @param rooms    已确定使用顺序的考场
     * @param params   冻结参数（含种子）
     * @return 与学员数量等长的座次（含考场、座号、全批次连续序号）
     */
    List<MixAssignedSeat> arrange(List<MixStudent> students, List<MixRoom> rooms, MixAlgoParams params);
}
