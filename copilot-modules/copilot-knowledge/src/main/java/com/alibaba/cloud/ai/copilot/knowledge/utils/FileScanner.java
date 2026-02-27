package com.alibaba.cloud.ai.copilot.knowledge.utils;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * 文件扫描器
 * 负责遍历工作区，应用过滤规则（默认规则 + .gitignore），返回有效文件列表
 *
 * @author RobustH
 */
@Slf4j
@Component
public class FileScanner {

    // 默认忽略的目录和文件模式
    private static final Set<String> DEFAULT_IGNORES = Set.of(
            ".git", ".idea", ".vscode", "node_modules", "target", "build", "dist", "bin",
            ".DS_Store", "Thumbs.db", "__pycache__"
    );

    /**
     * 扫描指定目录
     *
     * @param rootPath 项目根目录
     * @return 有效文件路径列表
     */
    public List<Path> scan(Path rootPath) {
        List<Path> validFiles = new ArrayList<>();
        GitIgnoreParser gitIgnoreParser = new GitIgnoreParser(rootPath);

        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @NotNull
                @Override
                public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
                    String dirName = dir.getFileName().toString();
                    
                    // 1. 检查默认忽略规则
                    if (DEFAULT_IGNORES.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    
                    // 2. 检查 .gitignore 规则
                    if (!dir.equals(rootPath) && gitIgnoreParser.isIgnored(dir, true)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    String fileName = file.getFileName().toString();

                    // 1. 检查默认忽略规则 (如果是隐藏文件或已知垃圾文件)
                    if (fileName.startsWith(".") && !fileName.equals(".gitignore")) {
                        return FileVisitResult.CONTINUE; // 忽略隐藏文件，但在 preVisitDirectory 已经处理了隐藏目录
                    }
                    
                    // 2. 检查 .gitignore 规则
                    if (gitIgnoreParser.isIgnored(file, false)) {
                        return FileVisitResult.CONTINUE;
                    }

                    // 3. 检查是否为支持的文件类型 (这里先简单放行，由 Indexer 决定是否处理，或者在这里过滤)
                    // 为了 MVP，我们暂时只收集所有非忽略文件
                    validFiles.add(file);

                    return FileVisitResult.CONTINUE;
                }

                @NotNull
                @Override
                public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) throws IOException {
                    log.warn("Failed to visit file: {}", file, exc);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error walking file tree: {}", rootPath, e);
        }

        log.info("Scanned {} files in {}", validFiles.size(), rootPath);
        return validFiles;
    }
}
