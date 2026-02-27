package com.alibaba.cloud.ai.copilot.knowledge.splitter.impl;

import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;
import com.alibaba.cloud.ai.copilot.knowledge.splitter.DocumentSplitter;

import com.alibaba.cloud.ai.copilot.knowledge.enums.KnowledgeCategory;
import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于 JavaParser 的代码切割器
 *
 * @author RobustH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JavaParserSplitter implements DocumentSplitter {

    private final TokenDocumentSplitter fallbackSplitter;
    // 使用新实例以避免并发问题
    private final JavaParser javaParser = new JavaParser();

    @Override
    public List<KnowledgeChunk> split(String content, String filePath) {
        try {
            ParseResult<CompilationUnit> result = javaParser.parse(content);
            
            if (!result.isSuccessful()) {
                log.warn("JavaParser 解析失败: {}, 使用 fallback", filePath);
                return fallbackSplitter.split(content, filePath);
            }

            CompilationUnit cu = result.getResult().get();
            List<CodeUnit> units = extractCodeUnits(cu);

            if (units.isEmpty()) {
                return fallbackSplitter.split(content, filePath);
            }

            return units.stream()
                    .map(unit -> createKnowledgeChunk(unit, filePath))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Java代码切割异常: {}", filePath, e);
            return fallbackSplitter.split(content, filePath);
        }
    }

    @Override
    public SplitterStrategy getStrategy() {
        return null;
    }

    private List<CodeUnit> extractCodeUnits(CompilationUnit cu) {
        List<CodeUnit> units = new ArrayList<>();
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        
        AtomicInteger indexCounter = new AtomicInteger(0);

        // 遍历所有顶层类型 (类, 接口, 枚举)
        for (TypeDeclaration<?> type : cu.getTypes()) {
            if (type instanceof ClassOrInterfaceDeclaration) {
                visitClass((ClassOrInterfaceDeclaration) type, packageName, units, indexCounter);
            }
            // 以后可以扩展支持 Enum, Record 等
        }
        return units;
    }

    private void visitClass(ClassOrInterfaceDeclaration classDecl, String packageName, List<CodeUnit> units, AtomicInteger indexCounter) {
        String className = classDecl.getNameAsString();
        String context = packageName.isEmpty() ? "" : "package " + packageName;

        // 1. 提取类本身的定义 (保留字段和签名，但在 Chunk 内容中去掉方法体)
        try {
            String classSignature = extractClassSignature(classDecl);
            units.add(new CodeUnit(
                    "CLASS",
                    className,
                    classSignature,
                    context,
                    classDecl.getBegin().map(p -> p.line).orElse(0),
                    classDecl.getEnd().map(p -> p.line).orElse(0),
                    indexCounter.getAndIncrement()
            ));
        } catch (Exception e) {
            log.warn("提取类签名失败", e);
        }

        // 2. 提取每个方法
        String classContext = "class " + className;
        for (MethodDeclaration method : classDecl.getMethods()) {
            units.add(new CodeUnit(
                    "METHOD",
                    method.getNameAsString(),
                    method.toString(), // 获取完整方法代码
                    classContext,
                    method.getBegin().map(p -> p.line).orElse(0),
                    method.getEnd().map(p -> p.line).orElse(0),
                    indexCounter.getAndIncrement()
            ));
        }
    }

    /**
     * 提取类的签名部分 (去除方法体内容)
     */
    private String extractClassSignature(ClassOrInterfaceDeclaration classDecl) {
         // 克隆一份以修改，避免影响原始对象（虽然我们只读，但 clone 更安全）
        ClassOrInterfaceDeclaration clone = (ClassOrInterfaceDeclaration) classDecl.clone();
        
        // 清空方法体，只保留签名
        // 对于接口方法，本身可能没 body，对于类方法，置 null 变成 ;
        clone.getMethods().forEach(m -> m.setBody(null));
        
        return clone.toString();
    }

    private KnowledgeChunk createKnowledgeChunk(CodeUnit unit, String filePath) {
        // 简单判定类型，这里假设已经进了 JavaParserSplitter，肯定是 Java 代码
        KnowledgeCategory.FileType fileType = KnowledgeCategory.FileType.CODE; 
        String language = "java";

        StringBuilder sb = new StringBuilder();
        sb.append("// File: ").append(filePath).append("\n");
        if (unit.context != null && !unit.context.isEmpty()) {
            sb.append("// Context: ").append(unit.context).append("\n");
        }
        sb.append("// ").append(unit.type).append(": ").append(unit.name).append("\n\n");
        sb.append(unit.content);

        return KnowledgeChunk.builder()
                .id(UUID.randomUUID().toString())
                .content(sb.toString())
                .filePath(filePath)
                .fileType(fileType)
                .language(language)
                .startLine(unit.startLine)
                .endLine(unit.endLine)
                .createdAt(System.currentTimeMillis())
                .contentHash(DigestUtils.md5DigestAsHex(sb.toString().getBytes()))
                .chunkIndex(unit.index)
                .metadata(createMetadata(unit))
                .build();
    }



    private java.util.Map<String, Object> createMetadata(CodeUnit unit) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        if (unit.type != null) {
            metadata.put("symbolType", unit.type);
        }
        if (unit.name != null) {
            metadata.put("symbolName", unit.name); // 冗余一份便于查看
        }
        if (unit.context != null) {
            metadata.put("parentSymbol", unit.context);
        }
        // 对于 CLASS, method.toString() 提取出的签名作为 signature
        // 对于 METHOD, method.toString() 是完整代码，这里不适合放 signature，需要另外提取
        // 简单起见，暂时不放 signature，或者在 CodeUnit 中增加 signature 字段
        return metadata;
    }

    // 内部记录类
    private record CodeUnit(
            String type,
            String name,
            String content,
            String context,
            int startLine,
            int endLine,
            int index
    ) {}
}
