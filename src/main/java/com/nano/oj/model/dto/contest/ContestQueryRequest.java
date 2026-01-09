package com.nano.oj.model.dto.contest;

import com.nano.oj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

/**
 * 比赛查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ContestQueryRequest extends PageRequest implements Serializable {

    /**
     * 搜索关键词 (标题/描述)
     */
    private String keyword;

    /**
     * 状态筛选 (未开始/进行中/已结束)
     */
    private Integer status;

    /**
     * 创建人
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}