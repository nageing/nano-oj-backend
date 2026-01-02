package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.QuestionSubmitMapper;
import com.nano.oj.model.dto.problemsubmit.ProblemSubmitAddRequest;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.ProblemSubmitVO;
import com.nano.oj.model.vo.QuestionVO;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.QuestionSubmitService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import cn.hutool.core.collection.CollUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 题目提交服务实现
 */
@Service
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
        implements QuestionSubmitService {

    @Resource
    private ProblemService problemService;

    @Resource
    private UserService userService;

    /**
     * 提交代码
     *
     * @param problemSubmitAddRequest 提交信息
     * @param loginUser               当前登录用户
     * @return 提交记录 ID
     */
    @Override
    public long doQuestionSubmit(ProblemSubmitAddRequest problemSubmitAddRequest, User loginUser) {
        // 1. 校验编程语言 (暂时只支持 java, cpp, python, go)
        String language = problemSubmitAddRequest.getLanguage();
        if (!"java".equals(language) && !"cpp".equals(language) && !"python".equals(language) && !"go".equals(language)) {
            throw new RuntimeException("编程语言错误");
        }

        long problemId = problemSubmitAddRequest.getProblemId();

        // 2. 判断题目是否存在
        Problem problem = problemService.getById(problemId);
        if (problem == null) {
            throw new RuntimeException("题目不存在");
        }

        // 3. 封装提交信息
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setUserId(loginUser.getId());
        questionSubmit.setQuestionId(problemId);
        questionSubmit.setCode(problemSubmitAddRequest.getCode());
        questionSubmit.setLanguage(language);

        // 设置初始状态：0-待判题
        questionSubmit.setStatus(0);
        questionSubmit.setJudgeInfo("{}");

        boolean save = this.save(questionSubmit);
        if (!save) {
            throw new RuntimeException("数据插入失败");
        }

        return questionSubmit.getId();
    }

    /**
     * 获取单条脱敏信息 (转 VO)
     *
     * @param questionSubmit 数据库实体
     * @param loginUser      当前登录用户
     * @return 脱敏后的 VO
     */
    @Override
    public ProblemSubmitVO getProblemSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
        ProblemSubmitVO problemSubmitVO = ProblemSubmitVO.objToVo(questionSubmit);

        // 脱敏：如果不是本人，也不是管理员，则不返回代码
        long userId = loginUser.getId();
        if (userId != questionSubmit.getUserId() && !userService.isAdmin(loginUser)) {
            problemSubmitVO.setCode(null);
        }
        return problemSubmitVO;
    }

    /**
     * 分页获取脱敏信息 (批量转 VO)
     *
     * @param questionSubmitPage 分页实体
     * @param loginUser          当前登录用户
     * @return 分页 VO
     */
    @Override
    public Page<ProblemSubmitVO> getProblemSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<ProblemSubmitVO> problemSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(), questionSubmitPage.getSize(), questionSubmitPage.getTotal());

        if (CollUtil.isEmpty(questionSubmitList)) {
            return problemSubmitVOPage;
        }

        // 1. 批量转换 (先处理基本信息和脱敏)
        List<ProblemSubmitVO> problemSubmitVOList = questionSubmitList.stream()
                .map(questionSubmit -> getProblemSubmitVO(questionSubmit, loginUser))
                .collect(Collectors.toList());

        // 2. 填充关联信息 (用户信息、题目信息)
        for (ProblemSubmitVO vo : problemSubmitVOList) {
            // A. 填充提交人信息
            Long userId = vo.getUserId();
            User user = userService.getById(userId);
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                vo.setUserVO(userVO);
            }

            // B. 填充题目信息 (这样前端列表就能显示是哪道题了)
            Long questionId = vo.getQuestionId();
            Problem problem = problemService.getById(questionId);
            if (problem != null) {
                QuestionVO questionVO = QuestionVO.objToVo(problem);
                vo.setQuestionVO(questionVO);
            }
        }

        problemSubmitVOPage.setRecords(problemSubmitVOList);
        return problemSubmitVOPage;
    }
}