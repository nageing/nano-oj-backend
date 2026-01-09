package com.nano.oj.model.dto.contest;

import com.nano.oj.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 排行榜查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContestRankQueryRequest extends PageRequest implements Serializable {

    /**
     * 比赛 id
     */
    private Long contestId;

    private static final long serialVersionUID = 1L;
}