package com.alibaba.cloud.ai.copilot.knowledge.splitter;

import com.alibaba.cloud.ai.copilot.knowledge.domain.vo.KnowledgeChunk;
import com.alibaba.cloud.ai.copilot.knowledge.enums.SplitterStrategy;

import java.util.List;

/**
 * 文档切割器接口
 * 负责将文档内容切割为知识块
 *
 * @author RobustH
 */
public interface DocumentSplitter {

    /**
     * 切割文档内容
     *
     * @param content  文档内容
     * @param filePath 文件路径
     * @return 知识块列表
     */
    List<KnowledgeChunk> split(String content, String filePath);

    /**
     * 获取该切割器对应的策略类型
     *
     * @return 切割策略
     */
    SplitterStrategy getStrategy();
}
