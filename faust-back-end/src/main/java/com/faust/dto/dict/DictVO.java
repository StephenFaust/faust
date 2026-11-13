package com.faust.dto.dict;

import lombok.Data;

@Data
public class DictVO {

    private Long id;
    private String dictType;
    private String dictCode;
    private String dictLabel;
    private Integer sortOrder;
}
