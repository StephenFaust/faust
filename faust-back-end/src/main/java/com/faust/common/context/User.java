package com.faust.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户基本信息
 *
 * @author stephenMao
 * @date 2021/7/28 5:08 下午
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User implements Serializable {
	@Serial
	private static final long serialVersionUID = 1;

	private Long ucId;
	private String supplierCode;
	private Integer source;

	private String name;

	public static User system() {
		return User.builder()
			.ucId(0L)
			.name("SYSTEM")
			.supplierCode("")
			.source(0)
			.build();
	}

}
