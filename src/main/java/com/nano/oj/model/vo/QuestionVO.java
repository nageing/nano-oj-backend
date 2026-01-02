package com.nano.oj.model.vo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nano.oj.model.entity.Problem;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目视图对象
 */
@Data
public class QuestionVO implements Serializable {

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
     * 标签列表 (后端是 JSON String，转成 List 给前端)
     */
    private List<String> tags;

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

    private static final long serialVersionUID = 1L;

    /**
     * 包装类转对象
     * 把 DB 里的 JSON 字符串转成对象
     *
     * @param problem
     * @return
     */
    public static QuestionVO objToVo(Problem problem) {
        if (problem == null) {
            return null;
        }
        QuestionVO questionVO = new QuestionVO();
        BeanUtils.copyProperties(problem, questionVO);

        // 处理 tags 字符串 -> List
        String tagStr = problem.getTags();
        if (tagStr != null) {
            questionVO.setTags(GSON.fromJson(tagStr, new TypeToken<List<String>>() {}.getType()));
        }

        // 处理 judgeConfig 字符串 -> Object
        String judgeConfigStr = problem.getJudgeConfig();
        if (judgeConfigStr != null) {
            questionVO.setJudgeConfig(GSON.fromJson(judgeConfigStr, Object.class));
        }

        // 处理 judgeCase 字符串 -> Object
        String judgeCaseStr = problem.getJudgeCase();
        if (judgeCaseStr != null) {
            questionVO.setJudgeCase(GSON.fromJson(judgeCaseStr, Object.class));
        }

        return questionVO;
    }
}