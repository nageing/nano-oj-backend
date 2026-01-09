package com.nano.oj.model.vo;

import cn.hutool.json.JSONUtil;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo;
import com.nano.oj.model.entity.QuestionSubmit;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 题目提交封装类 (用于返回给前端)
 */
@Data
public class QuestionSubmitVO implements Serializable {
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
    private ProblemVO problemVO;



    /**
     * 对象转包装类
     */
    public static QuestionSubmitVO objToVo(QuestionSubmit questionSubmit) {
        if (questionSubmit == null) {
            return null;
        }
        QuestionSubmitVO questionSubmitVO = new QuestionSubmitVO();
        BeanUtils.copyProperties(questionSubmit, questionSubmitVO);

        // ✨ 重点修改：手动把 String 转成 JudgeInfo 对象
        String judgeInfoStr = questionSubmit.getJudgeInfo();
        if (judgeInfoStr != null) {
            questionSubmitVO.setJudgeInfo(JSONUtil.toBean(judgeInfoStr, JudgeInfo.class));
        }

        return questionSubmitVO;
    }

    private static final long serialVersionUID = 1L;
}