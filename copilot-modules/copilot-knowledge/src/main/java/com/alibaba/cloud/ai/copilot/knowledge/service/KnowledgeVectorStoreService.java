package com.alibaba.cloud.ai.copilot.knowledge.service;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.service.KnowledgeFtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 使用 Spring AI VectorStore 抽象
 *
 * @author RobustH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeVectorStoreService {

    private final VectorStore vectorStore;
    private final KnowledgeFtsService ftsService;

    /**
     * 添加知识块到向量库
     */
    public void addKnowledge(String userId, KnowledgeChunk chunk) {
        Document document = convertToDocument(userId, chunk);
        vectorStore.add(List.of(document));
        ftsService.addBatch(userId, List.of(chunk));
        log.info("已添加知识块: 用户ID={}, 文件路径={}", userId, chunk.getFilePath());
    }

    /**
     * 批量添加知识块
     */
    public void addKnowledgeBatch(String userId, List<KnowledgeChunk> chunks) {
        List<Document> documents = chunks.stream()
                .map(chunk -> convertToDocument(userId, chunk))
                .collect(Collectors.toList());
        vectorStore.add(documents);
        ftsService.addBatch(userId, chunks);  // 同步写入 FTS
        log.info("已批量添加 {} 个知识块（向量+FTS）, 用户: {}", chunks.size(), userId);
    }

    /**
     * 搜索相关知识
     */
    public List<Document> searchKnowledge(String userId, String query, int topK) {
        log.info("执行向量搜索: userId={}, query={}, topK={}", userId, query, topK);
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()   // 不做阈值过滤，数据隔离由 user_id filter 保证
                .filterExpression(String.format("user_id == '%s'", userId))
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        log.info("向量搜索结束: userId={}, 返回 {} 条结果", userId, results.size());
        return results;
    }

    /**
     * 搜索指定文件类型的知识
     */
    public List<Document> searchKnowledgeByFileType(
            String userId, 
            String query, 
            KnowledgeCategory.FileType fileType,
            int topK) {
        
        String filter = String.format(
                "user_id == '%s' && file_type == '%s'", 
                userId, 
                fileType.name()
        );

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThresholdAll()   // 不做阈值过滤
                .filterExpression(filter)
                .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * 删除指定文件的所有知识 (用于更新文件时清理旧数据)
     */
    public void deleteKnowledgeByFilePath(String userId, String filePath) {
        String filter = String.format(
                "user_id == '%s' && file_path == '%s'", 
                userId, 
                filePath
        );
        // Spring AI 的 delete 方法通常接受 ID 列表，但在某些实现中可能支持 filter
        // 如果 VectorStore 接口不支持基于 filter 的删除，我们需要先搜索出 ID 再删除
        // 这里假设我们需要先搜索 ID
        try {
            // 搜索该文件的所有 chunk (topK 设置大一些以覆盖所有 chunks)
            // 注意: 这种方式有性能开销，但在 MVP 阶段是可接受的
            SearchRequest request = SearchRequest.builder()
                .query("") // 空查询
                .topK(1000) // 假设一个文件不超过 1000 个 chunks
                .similarityThreshold(0.0) // 匹配所有
                .filterExpression(filter)
                .build();
            
            List<Document> documents = vectorStore.similaritySearch(request);
            if (!documents.isEmpty()) {
                List<String> ids = documents.stream().map(Document::getId).collect(Collectors.toList());
                vectorStore.delete(ids);
                ftsService.deleteByFilePath(userId, filePath);  // 同步清 FTS
                log.info("已清理旧文件数据: 用户={}, 文件={}, 删除条数={}", userId, filePath, ids.size());
            }
        } catch (Exception e) {
            log.warn("清理旧文件数据失败 (可能是首次添加): {}", e.getMessage());
        }
    }

    /**
     * 删除用户的所有知识
66
     */
    public void deleteUserKnowledge(String userId) {
        String filter = String.format("user_id == '%s'", userId);
        try {
            // 搜索该用户的所有 chunk (topK 设置大一些以覆盖所有 chunks)
            SearchRequest request = SearchRequest.builder()
                .query("") // 空查询
                .topK(10000) // 假设一个用户不超过 10000 个 chunks
                .similarityThreshold(0.0) // 匹配所有
                .filterExpression(filter)
                .build();
            
            List<Document> documents = vectorStore.similaritySearch(request);
            if (!documents.isEmpty()) {
                List<String> ids = documents.stream().map(Document::getId).collect(Collectors.toList());
                vectorStore.delete(ids);
                ftsService.deleteByUserId(userId);  // 同步清 FTS
                log.info("已清理用户知识: 用户={}, 删除条数={}", userId, ids.size());
            }
        } catch (Exception e) {
            log.warn("清理用户知识失败: {}", e.getMessage());
        }
    }

    /**
     * 将 KnowledgeChunk 转换为 Spring AI Document。
     * 在内容前追加中文语义描述头（类 Continue 的 context augmentation），
     * 解决中文查询 vs 英文代码的跨语言语义鸿沟。
     */
    private Document convertToDocument(String userId, KnowledgeChunk chunk) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", userId != null ? userId : "default");
        metadata.put("file_path", chunk.getFilePath() != null ? chunk.getFilePath() : "unknown");
        metadata.put("file_type", chunk.getFileType() != null ? chunk.getFileType().name() : "OTHER");
        metadata.put("language", chunk.getLanguage() != null ? chunk.getLanguage() : "text");
        metadata.put("start_line", chunk.getStartLine() != null ? chunk.getStartLine() : 0);
        metadata.put("end_line", chunk.getEndLine() != null ? chunk.getEndLine() : 0);
        metadata.put("created_at", chunk.getCreatedAt() != null ? chunk.getCreatedAt() : System.currentTimeMillis());
        metadata.put("content_hash", chunk.getContentHash() != null ? chunk.getContentHash() : "");
        metadata.put("chunk_index", chunk.getChunkIndex() != null ? chunk.getChunkIndex() : 0);

        if (chunk.getMetadata() != null) {
            chunk.getMetadata().forEach((k, v) -> {
                if (v != null) {
                    metadata.put("meta_" + k, v);
                }
            });
        }

        // 构建带有中文语义描述头的 content，提升跨语言召回率
        String rawContent = chunk.getContent() != null ? chunk.getContent() : "";
        String enrichedContent = buildEnrichedContent(chunk, rawContent);

        return new Document(chunk.getId(), enrichedContent, metadata);
    }

    /**
     * 在原始代码内容前拼接自然语言描述头。
     * 示例：
     *   文件: StudentNotFoundException.java | 类型: 异常类 | 符号: StudentNotFoundException
     *   [原始代码...]
     */
    private String buildEnrichedContent(KnowledgeChunk chunk, String rawContent) {
        StringBuilder prefix = new StringBuilder();

        // 文件路径（取文件名）
        if (chunk.getFilePath() != null) {
            String fileName = chunk.getFilePath().replaceAll(".*[/\\\\]", "");
            prefix.append("文件: ").append(fileName);
        }

        // 符号类型中文描述
        Map<String, Object> meta = chunk.getMetadata();
        if (meta != null) {
            Object symbolType = meta.get("symbolType");
            Object symbolName = meta.get("symbolName");
            Object parentSymbol = meta.get("parentSymbol");

            if (symbolType != null) {
                String typeDesc = toChineseSymbolType(symbolType.toString());
                prefix.append(" | 类型: ").append(typeDesc);
            }
            if (symbolName != null) {
                prefix.append(" | 符号: ").append(symbolName);
            }
            if (parentSymbol != null) {
                prefix.append(" | 所属: ").append(parentSymbol);
            }
        }

        if (prefix.length() > 0) {
            return prefix + "\n" + rawContent;
        }
        return rawContent;
    }

    private String toChineseSymbolType(String symbolType) {
        return switch (symbolType.toUpperCase()) {
            case "CLASS" -> "类";
            case "INTERFACE" -> "接口";
            case "METHOD" -> "方法";
            case "FIELD" -> "字段";
            case "ENUM" -> "枚举";
            case "ANNOTATION" -> "注解";
            default -> symbolType;
        };
    }
}
