package com.evops.mix.algo;

import com.evops.common.BusinessException;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 混编算法实现注册表：版本按 impl_key 找到具体实现。
 * 新增算法实现只需实现 SeatMixAlgorithm 并交 Spring 管理。
 */
@Component
public class SeatMixAlgorithmRegistry {

    private final List<SeatMixAlgorithm> algorithms;
    private final Map<String, SeatMixAlgorithm> byKey = new LinkedHashMap<>();

    public SeatMixAlgorithmRegistry(List<SeatMixAlgorithm> algorithms) {
        this.algorithms = algorithms;
    }

    @PostConstruct
    void init() {
        for (SeatMixAlgorithm algorithm : algorithms) {
            byKey.put(algorithm.key(), algorithm);
        }
    }

    public SeatMixAlgorithm require(String implKey) {
        if (implKey == null || implKey.trim().isEmpty()) {
            throw new BusinessException("MIX_IMPL_REQUIRED", "算法实现标识不能为空");
        }
        SeatMixAlgorithm algorithm = byKey.get(implKey.trim());
        if (algorithm == null) {
            throw new BusinessException("MIX_IMPL_NOT_FOUND",
                    "混编算法实现不存在或未启用：" + implKey + "，可用：" + byKey.keySet());
        }
        return algorithm;
    }

    public boolean supports(String implKey) {
        return implKey != null && byKey.containsKey(implKey.trim());
    }

    public Map<String, SeatMixAlgorithm> all() {
        return byKey;
    }
}
