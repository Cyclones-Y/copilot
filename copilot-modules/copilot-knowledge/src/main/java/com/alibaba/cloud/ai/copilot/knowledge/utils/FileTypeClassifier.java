package com.alibaba.cloud.ai.copilot.knowledge.utils;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 文件类型分类器
 * 根据文件扩展名识别文件类型和编程语言
 *
 * @author RobustH
 */
@Component
public class FileTypeClassifier {

    // 代码文件扩展名
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            // 后端语言
            "java", "kt", "scala", "groovy",
            "py", "rb", "php",
            "go", "rs", "c", "cpp", "cc", "cxx", "h", "hpp",
            "cs", "vb",
            // 前端语言
            "js", "jsx", "ts", "tsx",
            "vue", "svelte",
            // 其他
            "sh", "bash", "sql"
    );

    // 文档文件扩展名
    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of(
            "md", "markdown",
            "txt", "text",
            "rst", "adoc", "asciidoc",
            "pdf", "doc", "docx"
    );

    // 配置文件扩展名
    private static final Set<String> CONFIG_EXTENSIONS = Set.of(
            "json", "yaml", "yml", "toml", "ini",
            "xml", "properties", "conf", "config",
            "env", "gitignore", "dockerignore"
    );

    // 编程语言识别
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = Map.ofEntries(
            // Java 系
            Map.entry("java", "Java"),
            Map.entry("kt", "Kotlin"),
            Map.entry("scala", "Scala"),
            Map.entry("groovy", "Groovy"),
            // Python
            Map.entry("py", "Python"),
            // JavaScript/TypeScript
            Map.entry("js", "JavaScript"),
            Map.entry("jsx", "JavaScript"),
            Map.entry("ts", "TypeScript"),
            Map.entry("tsx", "TypeScript"),
            // 前端框架
            Map.entry("vue", "Vue"),
            Map.entry("svelte", "Svelte"),
            // 系统语言
            Map.entry("go", "Go"),
            Map.entry("rs", "Rust"),
            Map.entry("c", "C"),
            Map.entry("cpp", "C++"),
            Map.entry("cc", "C++"),
            Map.entry("cxx", "C++"),
            Map.entry("h", "C/C++"),
            Map.entry("hpp", "C++"),
            // 其他
            Map.entry("cs", "C#"),
            Map.entry("rb", "Ruby"),
            Map.entry("php", "PHP"),
            Map.entry("sh", "Shell"),
            Map.entry("bash", "Shell"),
            Map.entry("sql", "SQL")
    );

    /**
     * 根据文件路径识别文件类型
     */
    public KnowledgeCategory.FileType classifyFileType(String filePath) {
        String extension = getExtension(filePath).toLowerCase();

        if (CODE_EXTENSIONS.contains(extension)) {
            return KnowledgeCategory.FileType.CODE;
        }
        if (DOCUMENT_EXTENSIONS.contains(extension)) {
            return KnowledgeCategory.FileType.DOCUMENT;
        }
        if (CONFIG_EXTENSIONS.contains(extension)) {
            return KnowledgeCategory.FileType.CONFIG;
        }

        return KnowledgeCategory.FileType.OTHER;
    }

    /**
     * 识别编程语言
     */
    public String detectLanguage(String filePath) {
        String extension = getExtension(filePath).toLowerCase();
        return EXTENSION_TO_LANGUAGE.getOrDefault(extension, "Unknown");
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }

        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
            return "";
        }

        return filePath.substring(lastDotIndex + 1);
    }
}
