package com.alibaba.cloud.ai.copilot.knowledge.enums;

/**
 * 切割策略枚举
 * @author RobustH
 */
public enum SplitterStrategy {
    /**
     * 递归字符切割 - 适合 Markdown 文档
     * 使用 RecursiveCharacterTextSplitter
     * 支持中文标点，递归式切割
     */
    RECURSIVE_CHARACTER,

    /**
     * 句子切割 - 适合 RAG 场景
     * 使用 SentenceSplitter (基于 OpenNLP)
     * 精准识别句子边界，保持语义完整性
     * 最适合检索增强生成场景
     */
    SENTENCE,

    /**
     * Token 切割 - 默认策略
     * 使用 TokenTextSplitter
     * 性能最优，适合简单文本
     */
    TOKEN,

    /**
     * 智能切割 - 代码文件
     * 使用 SmartCodeSplitter
     * 根据文件扩展名自动选择 JavaParser 或其他切割器
     */
    SMART_CODE
}
