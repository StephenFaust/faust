package com.faust.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.faust.entity.DataSourceConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataSourceConfigMapper extends BaseMapper<DataSourceConfig> {
}
