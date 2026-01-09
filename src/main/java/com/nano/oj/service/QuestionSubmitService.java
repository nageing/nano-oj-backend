package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.dto.questionsubmit.QuestionRunRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.QuestionSubmitVO;

/**
 * 题目提交服务接口
 */
public interface QuestionSubmitService extends IService<QuestionSubmit> {

    /**
     * 题目提交
     *
     * @param questionSubmitAddRequest 提交信息
     * @param loginUser               当前登录用户
     * @return 提交记录的 ID
     */
    long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser);

    /**
     * 获取单条封装
     */
    QuestionSubmitVO getProblemSubmitVO(QuestionSubmit questionSubmit, User loginUser);

    /**
     * 分页获取封装
     */
    Page<QuestionSubmitVO> getProblemSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser);

    /**
     * 运行代码 (自测)
     */
    QuestionSubmitVO doQuestionRun(QuestionRunRequest runRequest, User loginUser);
}