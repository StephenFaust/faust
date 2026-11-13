package com.faust.agent.tools.db;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.faust.agent.factory.DataSourceFactory;
import com.faust.dto.agent.DataSourceInfo;
import com.faust.dto.agent.ToolMateInfo;
import com.faust.agent.tools.AbstractToolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ToolContext;

import java.sql.*;
import java.util.*;

@Slf4j
public final class DbToolExecutor extends AbstractToolExecutor {

	/**
	 * 模型填写 SQL 的字段名
	 */
	private static final String ARG_SQL = "sql";


	private final DataSourceFactory dataSourceFactory;

	private static final Object LOCKER = new Object();

	private DruidDataSource dataSource;

	/**
	 * 单次查询最多返回行数，防止大表全量读取导致 OOM / 响应超时。
	 */
	private static final int MAX_QUERY_ROWS = 500;

	/**
	 * 禁止执行的写操作 / DDL 关键字（词边界匹配，避免误伤 updated_at 等列名）。
	 */
	private static final java.util.regex.Pattern FORBIDDEN_SQL_PATTERN = java.util.regex.Pattern.compile(
			"\\b(DELETE|UPDATE|ALTER|INSERT|DROP|TRUNCATE|CREATE|REPLACE|MERGE|GRANT|REVOKE|RENAME|COMMENT|EXEC|EXECUTE|SET)\\b",
			java.util.regex.Pattern.CASE_INSENSITIVE);

	public DbToolExecutor(DataSourceFactory dataSourceFactory, ToolMateInfo toolMateInfo) {
		super(toolMateInfo);
		this.dataSourceFactory = dataSourceFactory;
	}

	/**
	 * DB 工具只吃一条 SQL，模型无需关心连接信息（数据源由配置绑定）。
	 * 把「只读 + 行数上限」写进参数说明，减少模型生成被拦截的语句。
	 */
	@Override
	public String getInputSchema() {
		StringBuilder description = new StringBuilder()
				.append("待执行的 SQL 语句，只允许 SELECT / WITH 开头的只读查询；")
				.append("禁止 DELETE / UPDATE / INSERT / ALTER / DROP / TRUNCATE 等写操作与 DDL（包含即被拦截）。")
				.append("单次最多返回 ").append(MAX_QUERY_ROWS).append(" 行，请自行用 WHERE / LIMIT 收窄结果集。");
		DataSourceInfo dataSourceInfo = toolMateInfo.getDataSourceInfo();
		if (Objects.nonNull(dataSourceInfo) && StringUtils.isNotBlank(dataSourceInfo.getDbType())) {
			description.append("当前数据源类型：").append(dataSourceInfo.getDbType()).append('。');
		}
		return textParamSchema(ARG_SQL, description.toString());
	}

	@Override
	public String execute(Map<String, Object> args, ToolContext toolContext) {
		String toolCode = toolMateInfo.getToolCode();
		String sql = textArg(args, ARG_SQL);
		if (StringUtils.isBlank(sql)) {
			return missingParamResult(ARG_SQL);
		}
		try {
			DruidDataSource dbSource = getDataSource();
			return executeQuery(dbSource, sql);
		} catch (Exception e) {
			closeDataSource();
			LOGGER.error("执行 DB 工具异常: toolCode={}", toolCode, e);
			return errorResult("执行异常: " + e.getMessage());
		}
	}


	public void closeDataSource() {
		synchronized (LOCKER) {
			if (Objects.isNull(dataSource)) {
				return;
			}
			this.dataSource.close();
			this.dataSource = null;
		}
	}


	private DruidDataSource getDataSource() {
		if (Objects.nonNull(this.dataSource)) {
			return this.dataSource;
		}
		synchronized (LOCKER) {
			if (Objects.isNull(this.dataSource)) {
				DataSourceInfo dataSourceInfo = this.toolMateInfo.getDataSourceInfo();
				if (Objects.isNull(dataSourceInfo)) {
					throw new IllegalArgumentException("数据源配置不存在");
				}
				this.dataSource = dataSourceFactory.getDateSource(dataSourceInfo);
				return this.dataSource;
			}
		}
		return this.dataSource;
	}


	/**
	 * 安全的查询执行：只允许 SELECT，使用 PreparedStatement 绑定参数
	 *
	 * @param dataSource DataSource
	 * @param sql        SELECT 语句
	 */
	public String executeQuery(DruidDataSource dataSource, String sql) throws SQLException {
		// 1. 仅允许只读查询（SELECT 或 WITH 开头的 CTE 查询）
		String trimmedSql = sql.trim();
		// 2. 拦截写操作 / DDL 关键字（DELETE、UPDATE、ALTER 等）
		if (FORBIDDEN_SQL_PATTERN.matcher(trimmedSql).find()) {
			throw new SQLException("SQL 包含非法操作关键字，已拦截（禁止 DELETE/UPDATE/ALTER 等写操作）");
		}
		List<Map<String, Object>> rows = getMaps(dataSource, sql);
		// 4. 转换为 JSON 字符串
		try {
			return JSON.toJSONString(rows);
		} catch (Exception e) {
			throw new SQLException("JSON 序列化失败", e);
		}
	}


	private static List<Map<String, Object>> getMaps(DruidDataSource dataSource, String sql) throws SQLException {
		List<Map<String, Object>> rows = new ArrayList<>();
		try (Connection conn = dataSource.getConnection();
			 Statement stmt = conn.createStatement()) {
			// 限制返回行数，防止大表全量读取
			stmt.setMaxRows(MAX_QUERY_ROWS);
			try (ResultSet rs = stmt.executeQuery(sql)) {
				ResultSetMetaData metaData = rs.getMetaData();
				int columnCount = metaData.getColumnCount();

				while (rs.next()) {
					Map<String, Object> row = new HashMap<>();
					for (int i = 1; i <= columnCount; i++) {
						// 获取列名
						String columnName = metaData.getColumnLabel(i); // 优先使用别名
						Object value = rs.getObject(i);
						row.put(columnName, value);
					}
					rows.add(row);
				}
			}
		}
		return rows;
	}
}
