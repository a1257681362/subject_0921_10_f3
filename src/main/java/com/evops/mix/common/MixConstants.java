package com.evops.mix.common;

/**
 * 混编模块业务常量。
 */
public final class MixConstants {

    private MixConstants() {
    }

    /** 默认项目编码。 */
    public static final String PROJECT_DEFAULT = "DEFAULT";

    // 混编算法模块状态
    public static final String MODULE_ENABLED = "ENABLED";
    public static final String MODULE_DISABLED = "DISABLED";

    // 混编算法版本状态
    /** 草稿：参数（含种子）可定，未参与排座。 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效：同一模块同时刻仅一版生效，编排只认生效版。 */
    public static final String VERSION_ACTIVE = "ACTIVE";
    /** 停用：被新版本发布顶替或人工停用；历史编排仍保留其版本号与种子。 */
    public static final String VERSION_DISABLED = "DISABLED";

    // 编排记录类别
    public static final String KIND_INITIAL = "INITIAL";
    public static final String KIND_REARRANGED = "REARRANGED";

    // 编排记录状态
    public static final String ARRANGE_ACTIVE = "ACTIVE";
    public static final String ARRANGE_SUPERSEDED = "SUPERSEDED";

    // 座次相邻标记
    /** 无相邻（单座/端点且无同校）或无学校信息。 */
    public static final String ADJ_NONE = "NONE";
    /** 左右相邻均非同校。 */
    public static final String ADJ_OK = "OK";
    public static final String ADJ_SAME_LEFT = "SAME_LEFT";
    public static final String ADJ_SAME_RIGHT = "SAME_RIGHT";
    public static final String ADJ_SAME_BOTH = "SAME_BOTH";

    /** 未填学校的学员分组键：不参与同校相邻计数。 */
    public static final String NO_SCHOOL_KEY = "__NO_SCHOOL__";

    /** 默认混编算法实现标识。 */
    public static final String IMPL_SCHOOL_SHUFFLE_V1 = "schoolShuffleV1";
}
