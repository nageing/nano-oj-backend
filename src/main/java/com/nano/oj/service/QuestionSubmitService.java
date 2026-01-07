package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.dto.problemsubmit.ProblemRunRequest;
import com.nano.oj.model.dto.problemsubmit.ProblemSubmitAddRequest;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.ProblemSubmitVO;

/**
 * 题目提交服务接口
 */
public interface QuestionSubmitService extends IService<QuestionSubmit> {

    /**
     * 题目提交
     *
     * @param problemSubmitAddRequest 提交信息
     * @param loginUser               当前登录用户
     * @return 提交记录的 ID
     */
    long doQuestionSubmit(ProblemSubmitAddRequest problemSubmitAddRequest, User loginUser);

    /**
     * 获取单条封装
     */
    ProblemSubmitVO getProblemSubmitVO(QuestionSubmit questionSubmit, User loginUser);

    /**
     * 分页获取封装
     */
    Page<ProblemSubmitVO> getProblemSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser);

    /**
     * 运行代码 (自测)
     */
    ProblemSubmitVO doQuestionRun(ProblemRunRequest runRequest, User loginUser);
}