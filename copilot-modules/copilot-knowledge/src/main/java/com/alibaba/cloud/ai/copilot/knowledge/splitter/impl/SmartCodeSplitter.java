package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能代码切割器
 * 根据代码文件类型自动选择合适的切割器
 * 
 * 当前支持:
 * - Java 文件: 使用 JavaParserSplitter (AST 级别切割)
 * - 其他代码文件: 使用 TokenDocumentSplitter (降级策略)
 * 
 * 未来可扩展:
 * - Python: 使用 PythonParserSplitter
 * - JavaScript/TypeScript: 使用 JSParserSplitter
 * - Go: 使用 GoParserSplitter
 *
 * @author RobustH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartCodeSplitter implements DocumentSplitter {

    private final JavaParserSplitter javaParserSplitter;
    private final TokenDocumentSplitter tokenSplitter;

    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();

    static {
        // Java 文件
        EXTENSION_MAP.put(".java", "java");
        
        // 未来可以添加更多编程语言
        // EXTENSION_MAP.put(".py", "python");
        // EXTENSION_MAP.put(".js", "javascript");
        // EXTENSION_MAP.put(".ts", "typescript");
        // EXTENSION_MAP.put(".go", "go");
        // EXTENSION_MAP.put(".rs", "rust");
    }

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        String extension = getExtension(filePath);
        String language = EXTENSION_MAP.get(extension);

        if ("java".equals(language)) {
            log.debug("使用 JavaParser 切割器: {}", filePath);
            return javaParserSplitter.split(content, filePath);
        }

        // 其他代码文件使用 Token 切割（降级策略）
        log.debug("使用 Token 切割器（代码文件降级）: {}", filePath);
        return tokenSplitter.split(content, filePath);
    }

    @Override
    public SplitterStrategy getStrategy() {
        return SplitterStrategy.SMART_CODE;
    }

    private String getExtension(String filePath) {
        if (filePath == null) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        return lastDot > 0 ? filePath.substring(lastDot).toLowerCase() : "";
    }


}
