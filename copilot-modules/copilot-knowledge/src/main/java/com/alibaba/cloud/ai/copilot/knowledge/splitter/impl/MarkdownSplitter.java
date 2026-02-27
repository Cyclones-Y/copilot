package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Markdown 文档切割器
 * 使用 Spring AI Alibaba 的 RecursiveCharacterTextSplitter
 * 
 * RecursiveCharacterTextSplitter 特点:
 * - 递归式文本分割，按分隔符优先级切割
 * - 默认分隔符: \n\n, \n, 。, ！, ？, ；, ，, 空格
 * - 针对中文语境优化
 *
 * @author RobustH
 */
@Slf4j
@Component
public class MarkdownSplitter implements DocumentSplitter {

    private final RecursiveCharacterTextSplitter textSplitter;

    public MarkdownSplitter(
            @Value("${copilot.knowledge.splitter.chunk-size:500}") int chunkSize,
            @Value("${copilot.knowledge.splitter.chunk-overlap:50}") int chunkOverlap) {
        log.info("初始化 MarkdownSplitter: chunkSize={}, chunkOverlap={}", chunkSize, chunkOverlap);
        this.textSplitter = new RecursiveCharacterTextSplitter();
    }

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        try {
            // 使用 RecursiveCharacterTextSplitter 切割文本
            List<String> chunks = textSplitter.splitText(content);

            log.debug("Markdown 文件 {} 切割为 {} 个 chunks", filePath, chunks.size());

            // 转换为 KnowledgeChunk
            AtomicInteger index = new AtomicInteger(0);
            return chunks.stream()
                    .filter(chunk -> !chunk.trim().isEmpty()) // 过滤空 chunk
                    .map(chunk -> createKnowledgeChunk(chunk, filePath, index.getAndIncrement()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Markdown 切割失败: {}", filePath, e);
            // 降级：返回整个文档作为单个 chunk
            return List.of(createKnowledgeChunk(content, filePath, 0));
        }
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.RECURSIVE_CHARACTER;
    }



    private KnowledgeChunk createKnowledgeChunk(String content, String filePath, int index) {
        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .filePath(filePath)
                .fileType(KnowledgeCategory.FileType.DOCUMENT)
                .language("markdown")
                .createdAt(System.currentTimeMillis())
                .contentHash(DigestUtils.md5DigestAsHex(content.getBytes())) // 使用 MD5, Spring 自带工具
                .chunkIndex(index)
                .metadata(Collections.emptyMap())
                .build();
    }

}
