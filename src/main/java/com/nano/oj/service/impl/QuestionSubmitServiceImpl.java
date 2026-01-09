package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.judge.JudgeService;
import com.nano.oj.judge.codesandbox.CodeSandbox;
import com.nano.oj.judge.codesandbox.impl.DockerCodeSandbox;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.nano.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.nano.oj.mapper.QuestionSubmitMapper;
import com.nano.oj.model.dto.questionsubmit.JudgeInfo;
import com.nano.oj.model.dto.questionsubmit.QuestionRunRequest;
import com.nano.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.QuestionSubmitVO;
import com.nano.oj.model.vo.ProblemVO;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.QuestionSubmitService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import cn.hutool.core.collection.CollUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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

    // ✨✨✨ 引入判题服务，必须加 @Lazy 避免循环依赖 ✨✨✨
    @Resource
    @Lazy
    private JudgeService judgeService;


    @Resource
    private DockerCodeSandbox dockerCodeSandbox;
    /**
     * 提交代码
     *
     * @param questionSubmitAddRequest 提交信息
     * @param loginUser               当前登录用户
     * @return 提交记录 ID
     */
    @Override
    public long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser) {
        // 1. 校验编程语言
        String language = questionSubmitAddRequest.getLanguage();
        if (!"java".equals(language) && !"cpp".equals(language) && !"python".equals(language) && !"go".equals(language)) {
            throw new RuntimeException("编程语言错误");
        }

        long problemId = questionSubmitAddRequest.getProblemId();

        // 2. 判断题目是否存在
        Problem problem = problemService.getById(problemId);
        if (problem == null) {
            throw new RuntimeException("题目不存在");
        }

        // 3. 封装提交信息
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setUserId(loginUser.getId());
        questionSubmit.setQuestionId(problemId);
        questionSubmit.setCode(questionSubmitAddRequest.getCode());
        questionSubmit.setLanguage(language);

        // 设置初始状态：0-待判题
        questionSubmit.setStatus(0);
        questionSubmit.setJudgeInfo("{}");

        boolean save = this.save(questionSubmit);
        if (!save) {
            throw new RuntimeException("数据插入失败");
        }

        Long questionSubmitId = questionSubmit.getId();

        // ✨✨✨ 关键点：异步调用判题服务 ✨✨✨
        CompletableFuture.runAsync(() -> {
            judgeService.doJudge(questionSubmitId);
        });

        return questionSubmitId;
    }

    /**
     * 获取单条脱敏信息 (转 VO)
     *
     * @param questionSubmit 数据库实体
     * @param loginUser      当前登录用户
     * @return 脱敏后的 VO
     */
    @Override
    public QuestionSubmitVO getProblemSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
        // 使用 ProblemSubmitVO.objToVo 自动转换 judgeInfo (String -> Object)
        QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);

        // 脱敏：如果不是本人，也不是管理员，则不返回代码
        long userId = loginUser.getId();
        if (userId != questionSubmit.getUserId() && !userService.isAdmin(loginUser)) {
            questionSubmitVO.setCode(null);
        }
        return questionSubmitVO;
    }

    /**
     * 分页获取脱敏信息 (批量转 VO)
     *
     * @param questionSubmitPage 分页实体
     * @param loginUser          当前登录用户
     * @return 分页 VO
     */
    @Override
    public Page<QuestionSubmitVO> getProblemSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> problemSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(), questionSubmitPage.getSize(), questionSubmitPage.getTotal());

        if (CollUtil.isEmpty(questionSubmitList)) {
            return problemSubmitVOPage;
        }

        // 1. 批量转换 (先处理基本信息和脱敏)
        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream()
                .map(questionSubmit -> getProblemSubmitVO(questionSubmit, loginUser))
                .collect(Collectors.toList());

        // 2. 填充关联信息 (用户信息、题目信息)
        for (QuestionSubmitVO vo : questionSubmitVOList) {
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
                ProblemVO problemVO = ProblemVO.objToVo(problem);
                vo.setProblemVO(problemVO);
            }
        }

        problemSubmitVOPage.setRecords(questionSubmitVOList);
        return problemSubmitVOPage;
    }

    @Override
    public QuestionSubmitVO doQuestionRun(QuestionRunRequest runRequest, User loginUser) {
        // 1. 准备参数
        String code = runRequest.getCode();
        String language = runRequest.getLanguage();
        String input = runRequest.getInput();

        if (StringUtils.isAnyBlank(code, language)) {
            throw new RuntimeException("参数为空");
        }

        // 2. 调用沙箱 (DockerCodeSandbox)
        CodeSandbox codeSandbox = dockerCodeSandbox;

        List<String> inputList = new ArrayList<>();
        // 处理输入：如果没输入，也得传个空列表进去跑
        if (input != null) {
            inputList.add(input);
        }

        ExecuteCodeRequest executeRequest = ExecuteCodeRequest.builder()
                .code(code)
                .language(language)
                .inputList(inputList)
                .build();

        ExecuteCodeResponse executeResponse = codeSandbox.executeCode(executeRequest);

        // 3. 封装返回结果
        QuestionSubmitVO vo = new QuestionSubmitVO();
        vo.setCode(code);
        vo.setLanguage(language);
        vo.setStatus(executeResponse.getStatus()); // 1-成功 2-失败

        JudgeInfo judgeInfo = new JudgeInfo();
        // 如果有输出，取第一个；如果没有，取 message (比如编译错误信息)
        if (CollUtil.isNotEmpty(executeResponse.getOutputList())) {
            judgeInfo.setMessage(executeResponse.getOutputList().get(0));
        } else {
            judgeInfo.setMessage(executeResponse.getMessage());
        }

        // 填充时间内存
        if (executeResponse.getJudgeInfo() != null) {
            judgeInfo.setTime(executeResponse.getJudgeInfo().getTime());
            judgeInfo.setMemory(executeResponse.getJudgeInfo().getMemory());
        }

        vo.setJudgeInfo(judgeInfo); // 注意这里你的 VO 里 judgeInfo 是对象还是字符串，如果是字符串记得转一下

        return vo;
    }
}