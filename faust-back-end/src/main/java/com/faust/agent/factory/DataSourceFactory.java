package com.faust.agent.factory;

import com.alibaba.druid.pool.DruidDataSource;
import com.faust.dto.agent.DataSourceInfo;
import com.faust.enums.ErrorMessageEnum;
import com.faust.exception.AgentBusinessException;
import org.springframework.stereotype.Component;

@Component
public class DataSourceFactory {


	public DruidDataSource getDateSource(DataSourceInfo dataSourceInfo) {
		return getDateSource(dataSourceInfo.getDbType(), dataSourceInfo.getUrl(),
				dataSourceInfo.getUser(), dataSourceInfo.getPwd());
	}

	public DruidDataSource getDateSource(String dbType, String url, String user, String pwd) {
		try {
			return buildDataSource(dbType, url, user, pwd);
		} catch (Exception e) {
			throw new AgentBusinessException(ErrorMessageEnum.INTERNAL_ERROR.getErrorCode(), e.getMessage());
		}
	}

	private DruidDataSource buildDataSource(String dbType, String url, String user, String pwd) {
		DruidDataSource dataSource = new DruidDataSource();
		// 基础连接配置
		dataSource.setDbType(dbType);
		dataSource.setUrl(url);
		dataSource.setUsername(user);
		dataSource.setPassword(pwd);
		dataSource.setFailFast(true);
		dataSource.setDefaultReadOnly(true);
		dataSource.setBreakAfterAcquireFailure(true);
		dataSource.setConnectionErrorRetryAttempts(0);
		// 连接池大小配置[reference:1][reference:2]
		dataSource.setInitialSize(1);      // 初始化连接数
		dataSource.setMinIdle(0);          // 最小空闲连接数
		dataSource.setMaxActive(1);       // 最大活跃连接数
		// 连接超时配置[reference:3][reference:4]
		dataSource.setMaxWait(10000);      // 获取连接最大等待时间（毫秒）
		// 连接检测与探活配置[reference:5][reference:6]// 连接最小空闲时间（毫秒）
		dataSource.setValidationQuery("SELECT 1");           // 验证连接有效性的 SQL
		return dataSource;
	}


}
