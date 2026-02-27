package com.alibaba.cloud.ai.copilot.knowledge.domain.entity;

import lombok.Data;

/**
 * FTS 全文检索表实体
 */
@Data
public class KnowledgeFts {

    /** chunk ID（与向量库 ID 一致） */
    private String id;

    /** 用户 ID，用于数据隔离 */
    private String userId;

    /** 文件路径 */
    private String filePath;

    /**
     * 全文检索内容：title + signature + 代码内容 拼接
     */
    private String content;

    /** 起始行号 */
    private Integer startLine;

    /** 结束行号 */
    private Integer endLine;
}
