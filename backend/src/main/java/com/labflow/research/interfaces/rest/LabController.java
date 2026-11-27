package com.labflow.research.interfaces.rest;

import com.labflow.research.domain.repository.LabDirectoryRepository;
import com.labflow.research.interfaces.rest.vo.LabVo;
import com.labflow.shared.api.ApiResponse;
import com.labflow.shared.security.CurrentUser;
import com.labflow.shared.web.TraceContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 实验室只读接口：只返回当前用户所属的实验室，避免暴露全平台组织架构。 */
@RestController
@RequestMapping("/api/v1/labs")
public class LabController {

    private final LabDirectoryRepository labs;

    public LabController(LabDirectoryRepository labs) {
        this.labs = labs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('lab:read')")
    public ApiResponse<List<LabVo>> myLabs() {
        List<LabVo> result = labs.findMyLabs(CurrentUser.requireUserId()).stream()
                .map(lab -> new LabVo(lab.id(), lab.code(), lab.name()))
                .toList();
        return ApiResponse.ok(result, TraceContext.currentTraceId());
    }
}
