package com.alibaba.cloud.ai.copilot.knowledge.enums;

/**
 * 知识分类
 *
 * @author RobustH
 */
public class KnowledgeCategory {

    /**
     * 文件类型枚举
     * 
     * 用途: 对文件进行分类标记，用于:
     * 1. 元数据标记 (存储到向量数据库)
     * 2. 过滤搜索 (按文件类型搜索知识)
     * 3. 默认切割器选择 (SplitterFactory.getSplitter(FileType))
     */
    public enum FileType {
        /** 代码文件 (Java, Python, JavaScript 等) */
        CODE,
        
        /** 文档文件 (Markdown, Text, PDF 等) */
        DOCUMENT,
        
        /** 配置文件 (JSON, YAML, XML 等) */
        CONFIG,
        
        /** 其他文件 */
        OTHER
    }
}
