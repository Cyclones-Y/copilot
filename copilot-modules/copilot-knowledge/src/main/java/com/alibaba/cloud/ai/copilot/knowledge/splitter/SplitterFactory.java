package com.alibaba.cloud.ai.copilot.knowledge.splitter;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档切割器工厂
 * 负责创建和管理各种文档切割器实例
 * 
 * 自动从 Spring 容器收集所有 DocumentSplitter 实现，并根据策略注册
 *
 * @author RobustH
 */
@Slf4j
@Component
public class SplitterFactory {

    private final Map<SplitterStrategy, DocumentSplitter> splitterCache = new ConcurrentHashMap<>();

    // 构造函数注入所有 Splitter 实现
    public SplitterFactory(List<DocumentSplitter> splitters) {
        for (DocumentSplitter splitter : splitters) {
            SplitterStrategy strategy = splitter.getStrategy();
            if (strategy != null) {
                splitterCache.put(strategy, splitter);
                log.info("注册文档切割器: 策略={}, 类={}", strategy, splitter.getClass().getSimpleName());
            }
        }
    }

    /**
     * 根据文件类型获取默认切割器
     *
     * @param fileType 文件类型
     * @return 文档切割器
     */
    public DocumentSplitter getSplitter(KnowledgeCategory.FileType fileType) {
        return switch (fileType) {
            case CODE -> getSplitter(SplitterStrategy.SMART_CODE);
            case DOCUMENT -> getSplitter(SplitterStrategy.RECURSIVE_CHARACTER); // 文档 -> Markdown
            case CONFIG, OTHER -> getSplitter(SplitterStrategy.TOKEN);
        };
    }

    /**
     * 根据策略获取切割器实例
     *
     * @param strategy 切割策略
     * @return 文档切割器
     */
    public DocumentSplitter getSplitter(SplitterStrategy strategy) {
        DocumentSplitter splitter = splitterCache.get(strategy);
        if (splitter == null) {
            log.warn("未找到策略 {} 对应的切割器，使用默认 Token 切割器", strategy);
            return getDefaultSplitter();
        }
        return splitter;
    }

    /**
     * 根据文件类型和策略获取切割器
     *
     * @param fileType 文件类型
     * @param strategy 切割策略
     * @return 文档切割器
     */
    public DocumentSplitter getSplitter(KnowledgeCategory.FileType fileType, SplitterStrategy strategy) {
        // 代码文件强制使用 SmartCodeSplitter (特殊逻辑)
        if (fileType == KnowledgeCategory.FileType.CODE) {
            return getSplitter(SplitterStrategy.SMART_CODE);
        }
        return getSplitter(strategy);
    }

    /**
     * 根据文件路径智能选择切割器
     * 
     * 选择逻辑:
     * - .java -> SmartCodeSplitter
     * - .md, .markdown -> MarkdownSplitter
     * - .txt, .doc, .pdf (长文档) -> SentenceDocumentSplitter
     * - 其他 -> TokenDocumentSplitter
     *
     * @param filePath 文件路径
     * @return 文档切割器
     */
    public DocumentSplitter getSplitterByPath(String filePath) {
        if (filePath == null) {
            return getDefaultSplitter();
        }

        String extension = getExtension(filePath).toLowerCase();
        
        return switch (extension) {
            case ".java" -> getSplitter(SplitterStrategy.SMART_CODE);
            case ".md", ".markdown" -> getSplitter(SplitterStrategy.RECURSIVE_CHARACTER);
            case ".txt", ".doc", ".docx", ".pdf" -> getSplitter(SplitterStrategy.SENTENCE); // RAG 场景
            default -> getDefaultSplitter();
        };
    }

    /**
     * 获取 RAG 场景推荐的切割器
     * 
     * @return SentenceDocumentSplitter
     */
    public DocumentSplitter getRAGSplitter() {
        return getSplitter(SplitterStrategy.SENTENCE);
    }

    /**
     * 获取 Markdown 文档切割器
     *
     * @return MarkdownSplitter
     */
    public DocumentSplitter getMarkdownSplitter() {
        return getSplitter(SplitterStrategy.RECURSIVE_CHARACTER);
    }

    /**
     * 获取默认切割器
     *
     * @return TokenDocumentSplitter
     */
    public DocumentSplitter getDefaultSplitter() {
        // 防止无限递归：如果 TokenSplitter 也没注册，就报错
        // 但通常 TokenSplitter 作为基础组件应该总是存在的
        DocumentSplitter tokenSplitter = splitterCache.get(SplitterStrategy.TOKEN);
        if (tokenSplitter == null) {
            throw new IllegalStateException("核心组件 TokenDocumentSplitter 未注册！");
        }
        return tokenSplitter;
    }

    private String getExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot) : "";
    }
}
