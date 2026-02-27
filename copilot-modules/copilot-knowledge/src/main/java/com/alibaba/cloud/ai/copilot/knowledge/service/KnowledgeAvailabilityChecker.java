package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.config.MilvusVectorStoreConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 向量数据库（Milvus）可用性检查器。
 * 通过判断 VectorStore 是否为 NoOpVectorStore 来确认 Milvus 是否连接成功。
 */
@Slf4j
@Component
public class KnowledgeAvailabilityChecker {

    @Autowired
    private VectorStore vectorStore;

    @PostConstruct
    public void init() {
        if (isAvailable()) {
            log.info("向量数据库连接正常，知识库功能已启用");
        } else {
            log.warn("向量数据库不可用，知识库功能已禁用（不影响其他功能正常使用）");
        }
    }

    /**
     * 返回向量数据库是否可用
     */
    public boolean isAvailable() {
        return !(vectorStore instanceof MilvusVectorStoreConfig.NoOpVectorStore);
    }
}
