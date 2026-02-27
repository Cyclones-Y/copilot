package com.alibaba.cloud.ai.copilot.knowledge.mapper;

import com.alibaba.cloud.ai.copilot.knowledge.domain.entity.FileIndexState;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface IndexStateMapper extends BaseMapper<FileIndexState> {
    
    @Select("SELECT * FROM file_index_state WHERE file_path = #{filePath} LIMIT 1")
    Optional<FileIndexState> findByFilePath(String filePath);
    
    // deleteByFilePath 可以通过 QueryWrapper 实现，或者自定义 SQL
}
