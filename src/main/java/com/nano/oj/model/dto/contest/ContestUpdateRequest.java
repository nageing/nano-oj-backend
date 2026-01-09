package com.nano.oj.model.dto.contest;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class ContestUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 说明
     */
    private String description;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 是否可见（0-公开，1-私有）
     */
    private Integer visible;

    /**
     * 关联的题目 ID 列表（更新时可能会变动）
     */
    private List<Long> problemIds;

    /**
     * 赛制
     */
    private Integer type;

    /**
     * 状态
     */
    private Integer status;
    private static final long serialVersionUID = 1L;
}