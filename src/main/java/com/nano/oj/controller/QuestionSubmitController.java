package com.nano.oj.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.common.ResultUtils;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo;
import com.nano.oj.model.dto.questionsubmit.QuestionRunRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.nano.oj.model.entity.Contest; // 引入 Contest
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.QuestionSubmitVO;
import com.nano.oj.service.ContestService; // 引入 ContestService
import com.nano.oj.service.QuestionSubmitService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目提交接口
 */
@Slf4j
@RestController
@RequestMapping("/problem_submit")
public class QuestionSubmitController {

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private UserService userService;

    // ✅ 新增：需要查询比赛信息来判断是否封榜
    @Resource
    private ContestService contestService;

    /**
     * 提交代码
     */
    @PostMapping("/")
    public BaseResponse<Long> doSubmit(@RequestBody QuestionSubmitAddRequest questionSubmitAddRequest,
                                       HttpServletRequest request) {
        if (questionSubmitAddRequest == null || questionSubmitAddRequest.getProblemId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        final User loginUser = userService.getLoginUser(request);

        // 调用 QuestionSubmitService
        long questionSubmitId = questionSubmitService.doQuestionSubmit(questionSubmitAddRequest, loginUser);

        return ResultUtils.success(questionSubmitId);
    }

    /**
     * 分页获取提交列表 (在此处增加 OI 赛制脱敏逻辑)
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<QuestionSubmitVO>> listProblemSubmitByPage(
            @RequestBody QuestionSubmitQueryRequest questionSubmitQueryRequest,
            HttpServletRequest request) {
        long current = questionSubmitQueryRequest.getCurrent();
        long size = questionSubmitQueryRequest.getPageSize();

        // 1. 获取分页数据 (查询数据库)
        Page<QuestionSubmit> questionSubmitPage = questionSubmitService.page(new Page<>(current, size),
                getQueryWrapper(questionSubmitQueryRequest));
        // 🔍【调试点 1】打印第一条 Entity 数据，看里面有没有 contestId
        if (!questionSubmitPage.getRecords().isEmpty()) {
            QuestionSubmit firstEntity = questionSubmitPage.getRecords().get(0);
            System.out.println("🐞 [DEBUG Entity] ID=" + firstEntity.getId() + ", ContestId=" + firstEntity.getContestId());
        }
        // 2. 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);

        // 3. 转 VO (此时里面包含了真实的分数和状态)
        Page<QuestionSubmitVO> voPage = questionSubmitService.getProblemSubmitVOPage(questionSubmitPage, loginUser);
        // 🔍【调试点 2】打印第一条 VO 数据
        if (!voPage.getRecords().isEmpty()) {
            QuestionSubmitVO firstVO = voPage.getRecords().get(0);
            System.out.println("🐞 [DEBUG VO] ID=" + firstVO.getId() + ", ContestId=" + firstVO.getContestId());
        }

        // 4. 核心：OI 赛制“暗箱操作”脱敏逻辑
        List<QuestionSubmitVO> records = voPage.getRecords();
        if (records != null && !records.isEmpty()) {
            long now = System.currentTimeMillis(); // 获取当前系统时间戳

            for (QuestionSubmitVO vo : records) {
                // 如果这条提交属于某个比赛
                log.info("#### 比赛ID:{}", vo.getContestId());
                if (vo.getContestId() != null && vo.getContestId() > 0) {
                    Contest contest = contestService.getById(vo.getContestId());

                    if (contest != null) {
                        boolean isOi = contest.getType() == 2;

                        // 不再看 status，而是直接比对时间
                        boolean isRunning = false;
                        if (contest.getStartTime() != null && contest.getEndTime() != null) {
                            long start = contest.getStartTime().getTime();
                            long end = contest.getEndTime().getTime();
                            isRunning = (now >= start && now < end);
                        }

                        // 调试日志 (确认生效后可删除)
                        log.info("#### 比赛ID:{}isOi:{}isRunning:{}", contest.getId(), isOi, isRunning);

                        if (isOi && isRunning && !isAdmin) {
                            // 开始彻底脱敏
                            vo.setScore(null);

                            JudgeInfo judgeInfo = vo.getJudgeInfo();
                            if (judgeInfo != null) {
                                judgeInfo.setTime(null);
                                judgeInfo.setMemory(null);
                                judgeInfo.setScore(null);
                                judgeInfo.setDetail(null);
                            }

                            // 状态掩盖：除了编译错误，统一显示为 "已提交" (状态码 10)
                            if (vo.getStatus() != null && vo.getStatus() != 3) {
                                // 再次确认不是编译错误再掩盖
                                if (judgeInfo != null && !"Compile Error".equals(judgeInfo.getMessage())) {
                                    vo.setStatus(10);
                                    judgeInfo.setMessage("Submitted");
                                }
                            }
                        }
                    }
                }
            }
        }

        return ResultUtils.success(voPage);
    }

    /**
     * 获取查询条件
     */
    private QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest searchRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (searchRequest == null) {
            return queryWrapper;
        }
        String language = searchRequest.getLanguage();
        Integer status = searchRequest.getStatus();
        Long questionId = searchRequest.getQuestionId();
        Long userId = searchRequest.getUserId();
        String sortField = searchRequest.getSortField();
        String sortOrder = searchRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(StringUtils.isNotBlank(language), "language", language);
        queryWrapper.eq(userId != null, "user_id", userId);
        queryWrapper.eq(questionId != null, "question_id", questionId);
        queryWrapper.eq(status != null, "status", status);

        // 排序
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);
        // 默认按创建时间倒序（最新的在最前面）
        if (StringUtils.isBlank(sortField)) {
            queryWrapper.orderByDesc("create_time");
        }

        return queryWrapper;
    }

    /**
     * 运行代码 (自测)
     */
    @PostMapping("/run")
    public BaseResponse<QuestionSubmitVO> doRun(@RequestBody QuestionRunRequest runRequest,
                                                HttpServletRequest request) {
        if (runRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 登录校验
        User loginUser = userService.getLoginUser(request);

        QuestionSubmitVO res = questionSubmitService.doQuestionRun(runRequest, loginUser);
        return ResultUtils.success(res);
    }
}