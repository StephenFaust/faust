package com.faust.repository.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.faust.entity.DataSourceConfig;
import com.faust.mapper.DataSourceConfigMapper;
import com.faust.repository.DataSourceConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class DataSourceConfigRepositoryImpl extends ServiceImpl<DataSourceConfigMapper, DataSourceConfig> implements DataSourceConfigRepository {
}
