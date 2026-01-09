package com.nano.oj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.vo.ProblemVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 题目服务接口
 */
public interface ProblemService extends IService<Problem> {

    /**
     * 创建题目（包含标签关联处理）
     *
     * @param problem 题目实体
     * @param tags    标签名称列表
     * @return piID
     */
    long addProblem(Problem problem, List<String> tags);

    /**
     * 更新题目（包含标签关联处理）
     *
     * @param problem 题目实体
     * @param tags    标签名称列表
     * @return 是否成功
     */
    boolean updateProblem(Problem problem, List<String> tags);

    /**
     * 获取题目封装分页（关联查询标签）
     *
     * @param problemPage 分页数据
     * @param request     请求
     * @return 脱敏后的 VO 分页
     */
    Page<ProblemVO> getProblemVOPage(Page<Problem> problemPage, HttpServletRequest request);

    /**
     * 获取单个题目的封装（包含关联的标签信息）
     * @param problem
     * @param request
     * @return
     */
    ProblemVO getProblemVO(Problem problem, HttpServletRequest request);
}