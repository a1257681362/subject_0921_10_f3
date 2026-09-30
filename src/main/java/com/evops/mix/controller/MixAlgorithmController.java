package com.evops.mix.controller;

import com.evops.common.ApiResponse;
import com.evops.mix.dto.MixAlgorithmRequest;
import com.evops.mix.dto.MixAlgorithmVersionRequest;
import com.evops.mix.entity.MixAlgorithm;
import com.evops.mix.entity.MixAlgorithmVersion;
import com.evops.mix.service.MixAlgorithmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 混编算法模块：模块（按项目）+ 版本（草稿/生效/停用）+ 状态留痕。
 */
@RestController
@RequestMapping("/api/mix/algorithms")
public class MixAlgorithmController {

    private final MixAlgorithmService algorithmService;

    public MixAlgorithmController(MixAlgorithmService algorithmService) {
        this.algorithmService = algorithmService;
    }

    // ---------- 模块 ----------

    @PostMapping
    public ApiResponse<MixAlgorithm> createModule(@Valid @RequestBody MixAlgorithmRequest request) {
        return ApiResponse.ok(algorithmService.createModule(request));
    }

    @GetMapping
    public ApiResponse<List<MixAlgorithm>> listModules(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(algorithmService.listModules(projectCode));
    }

    @GetMapping("/{moduleId}")
    public ApiResponse<MixAlgorithm> getModule(@PathVariable Long moduleId) {
        return ApiResponse.ok(algorithmService.getModule(moduleId));
    }

    @PostMapping("/{moduleId}/disable")
    public ApiResponse<MixAlgorithm> disableModule(@PathVariable Long moduleId) {
        return ApiResponse.ok(algorithmService.disableModule(moduleId));
    }

    @PostMapping("/{moduleId}/enable")
    public ApiResponse<MixAlgorithm> enableModule(@PathVariable Long moduleId) {
        return ApiResponse.ok(algorithmService.enableModule(moduleId));
    }

    // ---------- 版本 ----------

    /** 新建草稿版本（定实现标识与含种子的参数，发布时冻结）。 */
    @PostMapping("/{moduleId}/versions")
    public ApiResponse<MixAlgorithmVersion> createDraft(@PathVariable Long moduleId,
                                                        @Valid @RequestBody MixAlgorithmVersionRequest request) {
        return ApiResponse.ok(algorithmService.createDraftVersion(moduleId, request));
    }

    @GetMapping("/{moduleId}/versions")
    public ApiResponse<List<MixAlgorithmVersion>> listVersions(@PathVariable Long moduleId) {
        return ApiResponse.ok(algorithmService.listVersions(moduleId));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<MixAlgorithmVersion> getVersion(@PathVariable Long versionId) {
        return ApiResponse.ok(algorithmService.getVersion(versionId));
    }

    /** 发布：草稿→生效，自动停用同模块旧生效版。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<MixAlgorithmVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(algorithmService.publishVersion(versionId));
    }

    /** 人工停用生效版本。 */
    @PostMapping("/versions/{versionId}/disable")
    public ApiResponse<MixAlgorithmVersion> disable(@PathVariable Long versionId) {
        return ApiResponse.ok(algorithmService.disableVersion(versionId));
    }
}
