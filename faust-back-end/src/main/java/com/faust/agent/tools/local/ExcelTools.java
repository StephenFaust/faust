package com.faust.agent.tools.local;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.faust.agent.tools.local.annotation.NativeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Excel 解析工具：输入一个 Excel 的 HTTP(S) URL，下载并解析为 JSON 返回。
 * 支持 .xls 与 .xlsx，自动以首行为表头，每个 sheet 输出表头与数据行（JSON 对象数组）。
 */
@Slf4j
@RequiredArgsConstructor
@NativeTool(name = "Excel工具集", code = "excel_tools",
	desc = "Excel 相关工具集,例如：解析excel文件", tags = {"文件解析", "常用工具"}, version = "1.0.2")
public class ExcelTools {
	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(15))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	/**
	 * 单次解析最多读取的数据行数，防止超大文件导致 OOM；-1 表示不限制
	 */
	private static final int DEFAULT_MAX_ROWS = 10000;

	@Tool(name = "read_excel", description = "根据 Excel 文件的 URL 下载并解析为 JSON。支持 .xls 与 .xlsx，以首行为表头，返回每个 sheet 的表头(headers)与数据行(data，JSON 对象数组)。")
	public String readExcel(
		@ToolParam(description = "Excel 文件的 HTTP(S) URL，例如 https://xxx/xxx.xlsx") String excelUrl,
		@ToolParam(description = "最多读取的数据行数，默认 10000，传 -1 表示不限制") Integer maxRows) {
		try {
			int limit = (maxRows == null) ? DEFAULT_MAX_ROWS : maxRows;
			byte[] bytes = download(excelUrl);
			JSONObject result = parseExcel(bytes, limit);
			return JSON.toJSONString(result, true);
		} catch (Exception e) {
			LOGGER.warn("解析 Excel 失败, url={}", excelUrl, e);
			return "解析 Excel 失败：" + e.getMessage();
		}
	}

	/**
	 * 从 URL 下载 Excel 字节流
	 */
	private byte[] download(String excelUrl) {
		try {
			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(excelUrl))
				.timeout(Duration.ofSeconds(60))
				.GET()
				.build();
			HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("下载 Excel 失败，HTTP 状态码：" + response.statusCode());
			}
			return response.body();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("下载 Excel 失败：" + e.getMessage(), e);
		} catch (Exception e) {
			throw new IllegalStateException("下载 Excel 失败：" + e.getMessage(), e);
		}
	}

	/**
	 * 解析字节流为 JSON：{ sheets: [ { name, headers, rowCount, data } ] }
	 */
	private JSONObject parseExcel(byte[] bytes, int maxRows) {
		JSONObject root = new JSONObject();
		JSONArray sheets = new JSONArray();
		try (InputStream in = new ByteArrayInputStream(bytes);
			 Workbook workbook = WorkbookFactory.create(in)) {
			DataFormatter formatter = new DataFormatter();
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			int sheetCount = workbook.getNumberOfSheets();
			for (int s = 0; s < sheetCount; s++) {
				Sheet sheet = workbook.getSheetAt(s);
				sheets.add(parseSheet(sheet, formatter, evaluator, maxRows));
			}
		} catch (Exception e) {
			throw new IllegalStateException("解析 Excel 内容失败：" + e.getMessage(), e);
		}
		root.put("sheets", sheets);
		return root;
	}

	/**
	 * 解析单个 sheet：首行作为表头，其余行映射为 JSON 对象；跳过全空行
	 */
	private JSONObject parseSheet(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator, int maxRows) {
		JSONObject sheetJson = new JSONObject();
		sheetJson.put("name", sheet.getSheetName());
		JSONArray data = new JSONArray();

		int firstRowNum = sheet.getFirstRowNum();
		int lastRowNum = sheet.getLastRowNum();
		if (lastRowNum < 0 || firstRowNum < 0) {
			sheetJson.put("headers", new JSONArray());
			sheetJson.put("rowCount", 0);
			sheetJson.put("data", data);
			return sheetJson;
		}

		// 解析表头（首行），空表头自动补 "列N"，重复表头追加序号去重
		Row headerRow = sheet.getRow(firstRowNum);
		List<String> headers = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		int colCount = headerRow == null ? 0 : headerRow.getLastCellNum();
		if (headerRow != null) {
			for (int c = 0; c < colCount; c++) {
				Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
				String h = formatter.formatCellValue(cell, evaluator).trim();
				if (h.isEmpty()) {
					h = "列" + (c + 1);
				}
				if (!seen.add(h)) {
					h = h + "_" + (c + 1);
					seen.add(h);
				}
				headers.add(h);
			}
		}

		// 解析数据行
		int dataRows = 0;
		for (int r = firstRowNum + 1; r <= lastRowNum; r++) {
			if (maxRows > 0 && dataRows >= maxRows) {
				break;
			}
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}
			boolean empty = true;
			JSONObject obj = new JSONObject();
			for (int c = 0; c < headers.size(); c++) {
				Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
				String v = formatter.formatCellValue(cell, evaluator);
				if (v != null && !v.trim().isEmpty()) {
					empty = false;
				}
				obj.put(headers.get(c), v);
			}
			if (empty) {
				continue;
			}
			data.add(obj);
			dataRows++;
		}

		sheetJson.put("headers", JSON.toJSON(headers));
		sheetJson.put("rowCount", dataRows);
		sheetJson.put("data", data);
		return sheetJson;
	}
}
