package com.nano.oj.model.vo;

import cn.hutool.json.JSONUtil;
import com.nano.oj.model.dto.problemsubmit.JudgeInfo;
import com.nano.oj.model.entity.QuestionSubmit;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目提交封装类 (用于返回给前端)
 */
@Data
public class ProblemSubmitVO implements Serializable {
    private Long id;
    private String language;
    private String code;

    /**
     * JudgeInfo 对象
     */
    private JudgeInfo judgeInfo;

    private Integer status;
    private Long questionId;
    private Long userId;
    private Date createTime;
    private Date updateTime;

    /**
     * 提交人信息
     */
    private UserVO userVO;

    /**
     * 题目信息
     */
    private QuestionVO questionVO;

    /**
     * 对象转包装类
     */
    public static ProblemSubmitVO objToVo(QuestionSubmit questionSubmit) {
        if (questionSubmit == null) {
            return null;
        }
        ProblemSubmitVO problemSubmitVO = new ProblemSubmitVO();
        BeanUtils.copyProperties(questionSubmit, problemSubmitVO);

        // ✨ 重点修改：手动把 String 转成 JudgeInfo 对象
        String judgeInfoStr = questionSubmit.getJudgeInfo();
        if (judgeInfoStr != null) {
            problemSubmitVO.setJudgeInfo(JSONUtil.toBean(judgeInfoStr, JudgeInfo.class));
        }

        return problemSubmitVO;
    }

    private static final long serialVersionUID = 1L;
}