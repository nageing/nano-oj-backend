package com.nano.oj.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.gson.Gson;
import com.nano.oj.common.BaseResponse;
import com.nano.oj.model.dto.problem.DeleteRequest;
import com.nano.oj.model.dto.problem.ProblemAddRequest;
import com.nano.oj.model.dto.problem.ProblemUpdateRequest;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.User;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.nano.oj.model.dto.problem.ProblemQueryRequest;
import org.apache.commons.lang3.StringUtils; // 用来判断字符串是否为空

import java.util.List;

/**
 * 题目接口
 */
@RestController
@RequestMapping("/problem")
public class ProblemController {

    @Resource
    private ProblemService problemService;

    @Resource
    private UserService userService;

    private final static Gson GSON = new Gson();

    /**
     * 创建题目
     */
    @PostMapping("/add")
    public BaseResponse<Long> addProblem(@RequestBody ProblemAddRequest problemAddRequest, HttpServletRequest request) {
        if (problemAddRequest == null) {
            throw new RuntimeException("参数为空");
        }

        User loginUser = userService.getLoginUser(request);
        Problem problem = new Problem();
        BeanUtils.copyProperties(problemAddRequest, problem);

        // ✨ 使用提取出的通用方法，消除重复代码警告
        this.setJsonValues(problem, problemAddRequest.getTags(), problemAddRequest.getJudgeCase(), problemAddRequest.getJudgeConfig());

        problem.setUserId(loginUser.getId());
        problem.setThumbNum(0);
        problem.setFavourNum(0);

        boolean result = problemService.save(problem);
        if (!result) {
            throw new RuntimeException("创建失败");
        }

        return new BaseResponse<>(0, problem.getId(), "创建成功");
    }

    /**
     * 更新题目
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateProblem(@RequestBody ProblemUpdateRequest problemUpdateRequest, HttpServletRequest request) {
        if (problemUpdateRequest == null || problemUpdateRequest.getId() <= 0) {
            throw new RuntimeException("参数错误");
        }

        Problem problem = new Problem();
        BeanUtils.copyProperties(problemUpdateRequest, problem);

        // ✨ 复用同一个方法
        this.setJsonValues(problem, problemUpdateRequest.getTags(), problemUpdateRequest.getJudgeCase(), problemUpdateRequest.getJudgeConfig());

        User loginUser = userService.getLoginUser(request);
        long id = problemUpdateRequest.getId();
        Problem oldProblem = problemService.getById(id);
        if (oldProblem == null) {
            throw new RuntimeException("题目不存在");
        }
        if (!loginUser.getId().equals(oldProblem.getUserId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new RuntimeException("无权修改");
        }

        boolean result = problemService.updateById(problem);
        return new BaseResponse<>(0, result, "修改成功");
    }

    /**
     * 删除题目
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteProblem(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new RuntimeException("参数错误");
        }

        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        Problem oldProblem = problemService.getById(id);
        if (oldProblem == null) {
            throw new RuntimeException("题目不存在");
        }

        if (!loginUser.getId().equals(oldProblem.getUserId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new RuntimeException("无权删除");
        }

        boolean b = problemService.removeById(id);
        return new BaseResponse<>(0, b, "删除成功");
    }

    /**
     * 根据 id 获取题目
     */
    @GetMapping("/get")
    public BaseResponse<Problem> getProblemById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new RuntimeException("参数错误");
        }
        Problem problem = problemService.getById(id);
        if (problem == null) {
            throw new RuntimeException("题目不存在");
        }
        return new BaseResponse<>(0, problem, "获取成功");
    }

    /**
     * 分页获取题目列表（用户、管理员通用）
     */
    @PostMapping("/list/page")
    public BaseResponse<Page<Problem>> listProblemByPage(@RequestBody ProblemQueryRequest problemQueryRequest, HttpServletRequest request) {
        long current = problemQueryRequest.getCurrent();
        long pageSize = problemQueryRequest.getPageSize();

        // 限制爬虫：如果一次要查 20 条以上，强行限制为 20 条，防止服务器压力过大
        if (pageSize > 20) {
            pageSize = 20;
        }

        // 1. 构建查询条件
        QueryWrapper<Problem> queryWrapper = new QueryWrapper<>();
        Long id = problemQueryRequest.getId();
        String title = problemQueryRequest.getTitle();
        String content = problemQueryRequest.getContent();
        List<String> tags = problemQueryRequest.getTags();
        Long userId = problemQueryRequest.getUserId();
        String sortField = problemQueryRequest.getSortField();
        String sortOrder = problemQueryRequest.getSortOrder();

        // 拼接查询条件 (如果不为空，才拼接到 SQL 里)
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userId != null, "user_id", userId);

        // 标签查询 (tags 数据库存的是 ["Java", "困难"] 这种 JSON 字符串)
        // 如果要查包含 "Java" 的，就用 like '%"Java"%'
        if (tags != null) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        // 未删除的才能查
        // queryWrapper.eq("is_delete", 0); // MyBatis Plus @TableLogic 逻辑删除会自动加这个，不用手动写

        // 2. 排序
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), sortOrder.equals("ascend"), sortField);

        // 3. 分页查询
        Page<Problem> problemPage = problemService.page(new Page<>(current, pageSize), queryWrapper);

        // 4. 脱敏 (可选)
        // 题目列表其实不需要要把所有的字段都返回（比如 content 和 answer 太长了可以不返回）
        // 这里暂时先全返回，后面优化再改

        return new BaseResponse<>(0, problemPage, "查询成功");
    }

    /**
     * ✨ 这是一个私有辅助方法
     * 专门处理 JSON 字段转换，避免代码重复
     */
    private void setJsonValues(Problem problem, List<String> tags, Object judgeCase, Object judgeConfig) {
        if (tags != null) {
            problem.setTags(GSON.toJson(tags));
        }
        if (judgeCase != null) {
            problem.setJudgeCase(GSON.toJson(judgeCase));
        }
        if (judgeConfig != null) {
            problem.setJudgeConfig(GSON.toJson(judgeConfig));
        }
    }
}