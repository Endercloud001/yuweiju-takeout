package com.codeying.common.page;

import lombok.Data;

import java.util.List;

/**
 * 分页结果统一封装。
 *
 * @param <T> 记录类型
 * @author Endercloud
 */
@Data
public class PageData<T> {
    /** 总记录数 */
    private Long total;
    /** 当前页数据 */
    private List<T> records;
}

