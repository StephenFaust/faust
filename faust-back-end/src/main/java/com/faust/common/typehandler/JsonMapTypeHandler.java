package com.faust.common.typehandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, String>> {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i,
									Map<String, String> parameter, JdbcType jdbcType) throws SQLException {
		try {
			ps.setString(i, MAPPER.writeValueAsString(parameter));
		} catch (JsonProcessingException e) {
			throw new SQLException(e);
		}
	}

	@Override
	public Map<String, String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return parse(rs.getString(columnName));
	}

	@Override
	public Map<String, String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return parse(rs.getString(columnIndex));
	}

	@Override
	public Map<String, String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return parse(cs.getString(columnIndex));
	}

	private Map<String, String> parse(String json) {
		if (json == null || json.isEmpty()) {
			return new HashMap<>(0);
		}
		try {
			return MAPPER.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			throw new IllegalArgumentException("JSON parse error: " + json, e);
		}
	}
}
