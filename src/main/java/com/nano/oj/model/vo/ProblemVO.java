package com.nano.oj.model.vo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nano.oj.model.entity.Problem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 题目视图对象
 */
@Data
public class ProblemVO implements Serializable {

    private final static Gson GSON = new Gson();

    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 标签列表 (修改为对象列表)
     */
    private List<TagVO> tags;

    /**
     * 题目答案 (通常不返回给前端，除非是管理员或者已解答)
     */
    private String answer;

    /**
     * 提交数
     */
    private Integer submitNum;

    /**
     * 通过数
     */
    private Integer acceptedNum;

    /**
     * 判题配置 (JSON 对象)
     */
    private Object judgeConfig;

    /**
     * 判题用例 (JSON 数组)
     */
    private Object judgeCase;

    /**
     * 点赞数
     */
    private Integer thumbNum;

    /**
     * 收藏数
     */
    private Integer favourNum;

    /**
     * 创建人 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建人信息 (可选)
     */
    private UserVO userVO;


    private Integer userStatus;

    /**
     * 可见性
     */
    private Integer visible;

    /**
     * 题目分数 (仅在比赛详情接口中有值)
     */
    private Integer score;

    private static final long serialVersionUID = 1L;

    /**
     * 包装类转对象
     * 把 DB 里的 JSON 字符串转成 ProblemVO
     */
    public static ProblemVO objToVo(Problem problem) {
        if (problem == null) {
            return null;
        }
        ProblemVO problemVO = new ProblemVO();
        BeanUtils.copyProperties(problem, problemVO);


        // 处理 judgeConfig
        String judgeConfigStr = problem.getJudgeConfig();
        if (judgeConfigStr != null) {
            problemVO.setJudgeConfig(GSON.fromJson(judgeConfigStr, Object.class));
        }

        // 处理 judgeCase
        String judgeCaseStr = problem.getJudgeCase();
        if (judgeCaseStr != null) {
            problemVO.setJudgeCase(GSON.fromJson(judgeCaseStr, Object.class));
        }

        return problemVO;
    }
}