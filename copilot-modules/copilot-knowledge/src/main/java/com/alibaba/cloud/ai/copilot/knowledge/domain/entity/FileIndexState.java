package com.alibaba.cloud.ai.copilot.knowledge.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文件索引状态实体
 * 记录文件路径和内容哈希，用于增量索引检查
 *
 * @author RobustH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("file_index_state")
public class FileIndexState {

    @TableId
    private String id; // 主键，可以使用 filePath 或 UUID

    /**
     * 用户ID (多租户隔离)
     */
    private String userId;

    /**
     * 文件绝对路径 (作为业务主键)
     */
    private String filePath;

    /**
     * 内容哈希 (MD5/SHA-256)
     */
    private String contentHash;

    /**
     * 最后更新时间
     */
    private LocalDateTime lastModifiedAt;
    
    /**
     * 文件大小
     */
    private Long fileSize;

}
