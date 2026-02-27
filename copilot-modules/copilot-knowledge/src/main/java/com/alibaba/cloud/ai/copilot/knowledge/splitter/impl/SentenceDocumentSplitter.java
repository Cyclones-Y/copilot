package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.transformer.splitter.SentenceSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于句子的文档切割器
 * 使用 Spring AI Alibaba 的 SentenceSplitter
 * 
 * SentenceSplitter 特点:
 * - 基于 OpenNLP 的 SentenceDetectorME 实现
 * - 精准识别句子边界，特别适合中文和多语言文本
 * - 按句子聚合，保留语义完整性
 * - 适用于 RAG 场景，提升检索准确性
 * 
 * 应用场景:
 * - 长文本文档（技术文档、论文、报告等）
 * - 需要保持语义完整性的文本
 * - 中文和多语言混合文本
 * - RAG 检索增强生成场景
 *
 * @author RobustH
 */
@Slf4j
@Component
public class SentenceDocumentSplitter implements DocumentSplitter {

    private final SentenceSplitter sentenceSplitter;

    public SentenceDocumentSplitter(
            @Value("${copilot.knowledge.splitter.chunk-size:500}") int chunkSize) {
        log.info("初始化 SentenceDocumentSplitter: chunkSize={}", chunkSize);
        this.sentenceSplitter = new SentenceSplitter(chunkSize);
    }

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        try {
            // 创建 Spring AI Document
            Document document = new Document(content, Map.of("source", filePath));

            // 使用 SentenceSplitter 切割
            // SentenceSplitter 会：
            // 1. 使用 OpenNLP 模型识别句子边界
            // 2. 按最大 token 数聚合句子
            // 3. 保持语义完整性
            List<Document> splitDocs = sentenceSplitter.apply(List.of(document));

            log.debug("文件 {} 使用 SentenceSplitter 切割为 {} 个 chunks", filePath, splitDocs.size());

            // 转换为 KnowledgeChunk
            AtomicInteger index = new AtomicInteger(0);
            return splitDocs.stream()
                    .filter(doc -> !doc.getText().trim().isEmpty())
                    .map(doc -> createKnowledgeChunk(doc, filePath, index.getAndIncrement()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("SentenceSplitter 切割失败: {}", filePath, e);
            // 降级：返回整个文档作为单个 chunk
            return List.of(createKnowledgeChunk(content, filePath, 0));
        }
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.SENTENCE;
    }



    private KnowledgeChunk createKnowledgeChunk(Document doc, String filePath, int index) {
        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(doc.getText())
                .filePath(filePath)
                .fileType(KnowledgeCategory.FileType.DOCUMENT)
                .language(detectLanguage(doc.getText()))
                .createdAt(System.currentTimeMillis())
                .contentHash(DigestUtils.md5DigestAsHex(doc.getText().getBytes()))
                .chunkIndex(index)
                .metadata(Collections.emptyMap())
                .build();
    }

    private KnowledgeChunk createKnowledgeChunk(String content, String filePath, int index) {
        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(content)
                .filePath(filePath)
                .fileType(KnowledgeCategory.FileType.DOCUMENT)
                .language(detectLanguage(content))
                .createdAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 简单的语言检测
     * 根据内容中的字符判断是否包含中文
     */
    private String detectLanguage(String content) {
        if (content == null || content.isEmpty()) {
            return "unknown";
        }
        
        // 检测是否包含中文字符
        boolean hasChinese = content.chars()
                .anyMatch(c -> Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
        
        return hasChinese ? "zh" : "en";
    }


}
