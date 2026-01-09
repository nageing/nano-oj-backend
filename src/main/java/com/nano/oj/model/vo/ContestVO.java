package com.nano.oj.model.vo;

import com.nano.oj.model.entity.Contest;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 比赛视图对象
 */
@Data
public class ContestVO implements Serializable {

    private Long id;
    private String title;
    private String description;
    private Date startTime;
    private Date endTime;
    private Integer status;
    private Integer type;
    private Long userId;
    private String CreatorName;
    // 是否需要密码 (返回给前端用于展示"锁"图标，不返回真实密码)
    private Boolean hasPwd;

    private UserVO creator; // 创建人信息

    private Boolean hasJoined;
    private List<ProblemVO> problems;
    private static final long serialVersionUID = 1L;

    public static ContestVO objToVo(Contest contest) {
        if (contest == null) return null;
        ContestVO contestVO = new ContestVO();
        BeanUtils.copyProperties(contest, contestVO);
        // 如果 pwd 字段不为空，标记为 true
        contestVO.setHasPwd(contest.getPwd() != null && !contest.getPwd().isEmpty());
        return contestVO;
    }
}