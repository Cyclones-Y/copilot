package com.alibaba.cloud.ai.copilot.domain.dto;

import lombok.Data;

@Data
public class IndexRequest {
    /**
     * 项目路径 (Workspace Path)
     */
    private String workspacePath;
}
