package com.nano.oj.model.vo;

import com.nano.oj.model.entity.Contest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * 管理员视角的比赛详情（包含关联的题目列表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ContestAdminVO extends Contest {

    /**
     * 关联的题目列表
     */
    private List<ContestProblemSimpleVO> problems;

    private static final long serialVersionUID = 1L;
}