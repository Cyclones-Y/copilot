package com.alibaba.cloud.ai.copilot.knowledge.domain.vo;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 知识块模型
 *
 * @author RobustH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {

    /**
     * 唯一标识
     */
    private String id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 文本内容
     */
    private String content;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件类型
     */
    private KnowledgeCategory.FileType fileType;

    /**
     * 编程语言 (对于代码文件)
     */
    private String language;

    /**
     * 代码符号名称 (函数名、类名等)
     */
    private String symbolName;

    /**
     * 起始行号
     */
    private Integer startLine;

    /**
     * 结束行号
     */
    private Integer endLine;

    /**
     * 增量索引: 内容的 SHA-256 哈希
     */
    private String contentHash;

    /**
     * 排序: Chunk 在文件中的索引位置
     */
    private Integer chunkIndex;

    /**
     * 扩展元数据: 存储 symbolType, signature 等
     */
    private Map<String, Object> metadata;

    /**
     * 创建时间
     */
    private Long createdAt;

    /**
     * 是否为代码文件
     */
    public boolean isCode() {
        return fileType == KnowledgeCategory.FileType.CODE;
    }
}
