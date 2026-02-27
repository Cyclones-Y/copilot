package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.utils.FileTypeClassifier;
import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Token 文档切割器
 * 基于 Spring AI TokenTextSplitter 实现
 *
 * @author RobustH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenDocumentSplitter implements DocumentSplitter {

    private final FileTypeClassifier fileTypeClassifier;

    @Value("${copilot.knowledge.splitter.chunk-size:2000}")
    private int chunkSize;

    @Value("${copilot.knowledge.splitter.chunk-overlap:400}")
    private int chunkOverlap;

    @Value("${copilot.knowledge.splitter.min-chunk-size:100}")
    private int minChunkSize;

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        // 识别文件类型和语言
        KnowledgeCategory.FileType fileType = fileTypeClassifier.classifyFileType(filePath);
        String language = fileTypeClassifier.detectLanguage(filePath);

        log.debug("切割文档: 路径={}, 类型={}, 语言={}, 长度={}", 
                filePath, fileType, language, content.length());

        // 创建 TokenTextSplitter
        TokenTextSplitter tokenTextSplitter = new TokenTextSplitter(
                chunkSize,
                minChunkSize,
                0,  // minChunkLengthToEmbed
                5,  // maxNumChunks
                true // keepSeparator
        );

        // 将内容转换为 Document
        ByteArrayResource resource = new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
        TextReader textReader = new TextReader(resource);
        List<Document> documents = textReader.get();

        // 使用 TokenTextSplitter 切割
        List<Document> splitDocuments = tokenTextSplitter.apply(documents);

        // 转换为 KnowledgeChunk
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (int i = 0; i < splitDocuments.size(); i++) {
            Document doc = splitDocuments.get(i);
            KnowledgeChunk chunk = KnowledgeChunk.builder()
                    .id(UUID.randomUUID().toString())
                    .content(doc.getText())
                    .filePath(filePath)
                    .fileType(fileType)
                    .language(language)
                    .startLine(1)  // 待优化: 从 metadata 中提取行号
                    .endLine(1)    // 待优化: 从 metadata 中提取行号
                    .createdAt(System.currentTimeMillis())
                    .contentHash(DigestUtils.md5DigestAsHex(doc.getText().getBytes()))
                    .chunkIndex(i)
                    .metadata(java.util.Collections.emptyMap())
                    .build();
            
            chunks.add(chunk);
        }

        log.debug("文档已切割为 {} 个知识块: {}", chunks.size(), filePath);
        return chunks;
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.TOKEN;
    }
}
