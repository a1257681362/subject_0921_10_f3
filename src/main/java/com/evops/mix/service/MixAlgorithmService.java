package com.evops.mix.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.mix.algo.MixAlgoParams;
import com.evops.mix.algo.SeatMixAlgorithmRegistry;
import com.evops.mix.common.MixConstants;
import com.evops.mix.dto.MixAlgorithmRequest;
import com.evops.mix.dto.MixAlgorithmVersionRequest;
import com.evops.mix.entity.MixAlgorithm;
import com.evops.mix.entity.MixAlgorithmVersion;
import com.evops.mix.mapper.MixAlgorithmMapper;
import com.evops.mix.mapper.MixAlgorithmVersionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 混编算法模块 / 版本 / 状态：
 * - 模块按项目建档，模块状态 ENABLED / DISABLED；
 * - 版本 DRAFT → ACTIVE → DISABLED：发布即冻结参数（含种子），同模块同时刻仅一版生效；
 * - 停用不删版本，历史编排保留版本号与种子，座次可复现。
 */
@Service
public class MixAlgorithmService {

    private final MixAlgorithmMapper algorithmMapper;
    private final MixAlgorithmVersionMapper versionMapper;
    private final SeatMixAlgorithmRegistry registry;
    private final ObjectMapper objectMapper;

    public MixAlgorithmService(MixAlgorithmMapper algorithmMapper,
                               MixAlgorithmVersionMapper versionMapper,
                               SeatMixAlgorithmRegistry registry,
                               ObjectMapper objectMapper) {
        this.algorithmMapper = algorithmMapper;
        this.versionMapper = versionMapper;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    // ---------- 模块 ----------

    @Transactional
    public MixAlgorithm createModule(MixAlgorithmRequest request) {
        MixAlgorithm module = new MixAlgorithm();
        module.setCode(request.getCode().trim());
        module.setName(request.getName().trim());
        module.setProjectCode(normalizeProject(request.getProjectCode()));
        module.setDescription(request.getDescription());
        module.setStatus(MixConstants.MODULE_ENABLED);
        module.setRemark(request.getRemark());
        try {
            algorithmMapper.insert(module);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_MIX_MODULE_CODE", "混编算法模块编码已存在：" + module.getCode());
        }
        return module;
    }

    public List<MixAlgorithm> listModules(String projectCode) {
        QueryWrapper<MixAlgorithm> wrapper = new QueryWrapper<MixAlgorithm>()
                .orderByAsc("project_code", "code");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim());
        }
        return algorithmMapper.selectList(wrapper);
    }

    public MixAlgorithm getModule(Long moduleId) {
        MixAlgorithm module = algorithmMapper.selectById(moduleId);
        if (module == null) {
            throw new BusinessException("MIX_MODULE_NOT_FOUND", "混编算法模块不存在：id=" + moduleId);
        }
        return module;
    }

    @Transactional
    public MixAlgorithm disableModule(Long moduleId) {
        MixAlgorithm module = getModule(moduleId);
        module.setStatus(MixConstants.MODULE_DISABLED);
        algorithmMapper.updateById(module);
        return module;
    }

    @Transactional
    public MixAlgorithm enableModule(Long moduleId) {
        MixAlgorithm module = getModule(moduleId);
        module.setStatus(MixConstants.MODULE_ENABLED);
        algorithmMapper.updateById(module);
        return module;
    }

    // ---------- 版本 ----------

    /** 新建草稿版本：定实现标识与参数（含种子）；种子为空系统生成，参数随版本冻结。 */
    @Transactional
    public MixAlgorithmVersion createDraftVersion(Long moduleId, MixAlgorithmVersionRequest request) {
        MixAlgorithm module = getModule(moduleId);
        String implKey = request.getImplKey() == null || request.getImplKey().trim().isEmpty()
                ? MixConstants.IMPL_SCHOOL_SHUFFLE_V1
                : request.getImplKey().trim();
        if (!registry.supports(implKey)) {
            throw new BusinessException("MIX_IMPL_NOT_FOUND",
                    "混编算法实现不存在或未启用：" + implKey + "，可用：" + registry.all().keySet());
        }

        long seed = request.getSeed() == null
                ? ThreadLocalRandom.current().nextLong()
                : request.getSeed();
        int maxRounds = request.getMaxRounds() == null || request.getMaxRounds() <= 0
                ? 500
                : request.getMaxRounds();

        MixAlgorithmVersion version = new MixAlgorithmVersion();
        version.setAlgorithmId(module.getId());
        version.setVersionNo(nextVersionNo(module.getId()));
        version.setStatus(MixConstants.VERSION_DRAFT);
        version.setImplKey(implKey);
        version.setParamsJson(writeParams(seed, maxRounds));
        version.setRemark(request.getRemark());
        version.setOperator(OperatorHolder.current());
        versionMapper.insert(version);
        return version;
    }

    /** 发布：DRAFT → ACTIVE；同模块旧 ACTIVE 自动置 DISABLED，同时刻仅一版生效。 */
    @Transactional
    public MixAlgorithmVersion publishVersion(Long versionId) {
        MixAlgorithmVersion version = requireVersion(versionId);
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        if (MixConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("MIX_VERSION_ALREADY_ACTIVE",
                    "版本已是生效状态，无需重复发布：" + version.getVersionNo());
        }
        if (MixConstants.VERSION_DISABLED.equals(version.getStatus())) {
            throw new BusinessException("MIX_VERSION_DISABLED",
                    "停用版本不可重新发布，请新建版本：" + version.getVersionNo());
        }
        MixAlgorithm module = getModule(version.getAlgorithmId());
        if (!MixConstants.MODULE_ENABLED.equals(module.getStatus())) {
            throw new BusinessException("MIX_MODULE_DISABLED",
                    "模块 " + module.getCode() + " 已停用，不能发布版本，请先启用模块");
        }

        List<MixAlgorithmVersion> actives = versionMapper.selectList(new QueryWrapper<MixAlgorithmVersion>()
                .eq("algorithm_id", version.getAlgorithmId())
                .eq("status", MixConstants.VERSION_ACTIVE));
        for (MixAlgorithmVersion active : actives) {
            active.setStatus(MixConstants.VERSION_DISABLED);
            active.setDisabledBy(operator);
            active.setDisabledAt(now);
            versionMapper.updateById(active);
        }

        version.setStatus(MixConstants.VERSION_ACTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);
        return version;
    }

    /** 人工停用生效版（停用后该模块在重新发布前无生效版本，无法编排）。 */
    @Transactional
    public MixAlgorithmVersion disableVersion(Long versionId) {
        MixAlgorithmVersion version = requireVersion(versionId);
        if (!MixConstants.VERSION_ACTIVE.equals(version.getStatus())) {
            throw new BusinessException("MIX_VERSION_NOT_ACTIVE",
                    "仅生效版本可停用：" + version.getVersionNo());
        }
        version.setStatus(MixConstants.VERSION_DISABLED);
        version.setDisabledBy(OperatorHolder.current());
        version.setDisabledAt(LocalDateTime.now());
        versionMapper.updateById(version);
        return version;
    }

    @Transactional(readOnly = true)
    public List<MixAlgorithmVersion> listVersions(Long moduleId) {
        getModule(moduleId);
        return versionMapper.selectList(new QueryWrapper<MixAlgorithmVersion>()
                .eq("algorithm_id", moduleId)
                .orderByDesc("id"));
    }

    public MixAlgorithmVersion getVersion(Long versionId) {
        return requireVersion(versionId);
    }

    /**
     * 取项目当前生效版本：优先项目精确模块，其次 DEFAULT 通用模块；
     * 模块必须 ENABLED。找不到或多模块无法唯一确定时抛错。
     */
    @Transactional(readOnly = true)
    public MixAlgorithmVersion requireActiveVersion(String projectCode) {
        String project = normalizeProject(projectCode);
        MixAlgorithm module = resolveModule(project);
        MixAlgorithmVersion active = versionMapper.selectOne(new QueryWrapper<MixAlgorithmVersion>()
                .eq("algorithm_id", module.getId())
                .eq("status", MixConstants.VERSION_ACTIVE)
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (active == null) {
            throw new BusinessException("NO_ACTIVE_MIX_VERSION",
                    "混编模块 " + module.getCode() + " 尚无生效版本，请先建草稿并发布");
        }
        return active;
    }

    private MixAlgorithm resolveModule(String project) {
        List<MixAlgorithm> exact = enabledModules(project);
        if (exact.size() == 1) {
            return exact.get(0);
        }
        if (exact.size() > 1) {
            List<MixAlgorithm> withActive = filterWithActive(exact);
            if (withActive.size() == 1) {
                return withActive.get(0);
            }
            throw new BusinessException("MULTIPLE_MIX_MODULES",
                    "项目 " + project + " 存在多个启用的混编模块，无法唯一选择，请在编排时指定算法版本");
        }
        if (!MixConstants.PROJECT_DEFAULT.equals(project)) {
            List<MixAlgorithm> general = enabledModules(MixConstants.PROJECT_DEFAULT);
            if (general.size() == 1) {
                return general.get(0);
            }
            if (general.size() > 1) {
                List<MixAlgorithm> withActive = filterWithActive(general);
                if (withActive.size() == 1) {
                    return withActive.get(0);
                }
            }
        }
        throw new BusinessException("NO_MIX_MODULE_FOR_PROJECT",
                "项目 " + project + " 尚未配置启用的混编算法模块，请先建档并发布版本");
    }

    private List<MixAlgorithm> enabledModules(String project) {
        return algorithmMapper.selectList(new QueryWrapper<MixAlgorithm>()
                .eq("project_code", project)
                .eq("status", MixConstants.MODULE_ENABLED)
                .orderByAsc("code"));
    }

    private List<MixAlgorithm> filterWithActive(List<MixAlgorithm> modules) {
        List<MixAlgorithm> result = new ArrayList<>();
        for (MixAlgorithm module : modules) {
            Long count = versionMapper.selectCount(new QueryWrapper<MixAlgorithmVersion>()
                    .eq("algorithm_id", module.getId())
                    .eq("status", MixConstants.VERSION_ACTIVE));
            if (count != null && count > 0) {
                result.add(module);
            }
        }
        return result;
    }

    // ---------- 参数解析 ----------

    public MixAlgoParams readParams(MixAlgorithmVersion version) {
        try {
            JsonNode node = objectMapper.readTree(version.getParamsJson());
            long seed = node.path("seed").asLong(0L);
            int maxRounds = node.path("maxRounds").asInt(500);
            return new MixAlgoParams(seed, maxRounds);
        } catch (Exception e) {
            throw new BusinessException("MIX_PARAMS_BROKEN",
                    "算法版本参数无法解析：" + version.getVersionNo() + "：" + e.getMessage());
        }
    }

    private String writeParams(long seed, int maxRounds) {
        try {
            java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
            params.put("seed", seed);
            params.put("maxRounds", maxRounds);
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new BusinessException("MIX_PARAMS_SERIALIZE_FAILED", "算法参数序列化失败：" + e.getMessage());
        }
    }

    // ---------- 内部 ----------

    private MixAlgorithmVersion requireVersion(Long versionId) {
        if (versionId == null) {
            throw new BusinessException("MIX_VERSION_ID_REQUIRED", "算法版本 id 不能为空");
        }
        MixAlgorithmVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("MIX_VERSION_NOT_FOUND", "混编算法版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 版本号：模块 code + -R + 递增序号（如 SCHOOL_SHUFFLE-R1）。 */
    private String nextVersionNo(Long moduleId) {
        MixAlgorithm module = getModule(moduleId);
        String prefix = module.getCode() + "-R";
        int max = 0;
        for (MixAlgorithmVersion v : versionMapper.selectList(
                new QueryWrapper<MixAlgorithmVersion>().eq("algorithm_id", moduleId))) {
            String no = v.getVersionNo();
            if (no != null && no.startsWith(prefix)) {
                try {
                    max = Math.max(max, Integer.parseInt(no.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // 非本规则序号忽略
                }
            }
        }
        return prefix + (max + 1);
    }

    private String normalizeProject(String projectCode) {
        if (projectCode == null || projectCode.trim().isEmpty()) {
            return MixConstants.PROJECT_DEFAULT;
        }
        return projectCode.trim().toUpperCase();
    }
}
