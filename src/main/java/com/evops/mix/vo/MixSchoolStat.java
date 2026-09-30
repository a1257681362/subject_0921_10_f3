package com.evops.mix.vo;

import lombok.Data;

/**
 * 单个学校的相邻统计（巡考抽查用）。
 */
@Data
public class MixSchoolStat {
    private String schoolCode;
    private String schoolName;
    /** 该校学员人数 */
    private int people;
    /** 涉及的考场数 */
    private int roomCount;
    /** 该校学员相邻对数 */
    private int sameSchoolPairs;
    /** 涉及同校相邻的学员人数（去重） */
    private int involvedPeople;
}
