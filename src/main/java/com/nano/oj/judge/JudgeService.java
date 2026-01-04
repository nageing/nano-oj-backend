package com.nano.oj.judge;

import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.vo.ProblemSubmitVO;

/**
 * 判题服务
 */
public interface JudgeService {

    /**
     * 判题
     * @param questionSubmitId 提交记录 ID
     * @return
     */
    QuestionSubmit doJudge(long questionSubmitId);
}