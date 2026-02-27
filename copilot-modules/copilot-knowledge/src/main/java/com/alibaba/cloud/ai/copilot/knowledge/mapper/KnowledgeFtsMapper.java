package com.alibaba.cloud.ai.copilot.knowledge.mapper;

import com.alibaba.cloud.ai.copilot.knowledge.domain.entity.KnowledgeFts;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * FTS 全文检索 Mapper
 */
@Mapper
public interface KnowledgeFtsMapper {

    /**
     * 批量插入 FTS 记录（ON DUPLICATE KEY UPDATE 实现幂等）
     */
    @Insert({"<script>" ,
            "INSERT INTO knowledge_fts (id, user_id, file_path, content, start_line, end_line) VALUES ",
            "<foreach collection='list' item='r' separator=','>",
            "(#{r.id}, #{r.userId}, #{r.filePath}, #{r.content}, #{r.startLine}, #{r.endLine})",
            "</foreach>",
            " ON DUPLICATE KEY UPDATE content=VALUES(content), file_path=VALUES(file_path),",
            " start_line=VALUES(start_line), end_line=VALUES(end_line)",
            "</script>"})
    void batchInsert(@Param("list") List<KnowledgeFts> records);

    /**
     * 全文检索（MySQL BOOLEAN MODE）
     * 按相关性排序，限制返回数量
     *
     * @param userId 用户ID（数据隔离）
     * @param query  检索关键词（已由调用方做 ngram / boolean 处理）
     * @param limit  最大返回条数
     */
    @Select("SELECT id, user_id, file_path, content, start_line, end_line, " +
            "MATCH(content) AGAINST (#{query} IN BOOLEAN MODE) AS score " +
            "FROM knowledge_fts " +
            "WHERE user_id = #{userId} " +
            "  AND MATCH(content) AGAINST (#{query} IN BOOLEAN MODE) > 0 " +
            "ORDER BY score DESC " +
            "LIMIT #{limit}")
    List<KnowledgeFts> fullTextSearch(@Param("userId") String userId,
                                      @Param("query") String query,
                                      @Param("limit") int limit);

    /**
     * 按文件路径删除所有 FTS 记录（文件更新/删除时调用）
     */
    @Delete("DELETE FROM knowledge_fts WHERE user_id = #{userId} AND file_path = #{filePath}")
    void deleteByFilePath(@Param("userId") String userId, @Param("filePath") String filePath);

    /**
     * 删除用户的全部 FTS 记录
     */
    @Delete("DELETE FROM knowledge_fts WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") String userId);
}
