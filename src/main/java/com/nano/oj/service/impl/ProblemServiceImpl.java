package com.nano.oj.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.ProblemMapper;
import com.nano.oj.mapper.ProblemTagMapper;
import com.nano.oj.mapper.TagMapper;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.model.entity.ProblemTag;
import com.nano.oj.model.entity.Tag;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.ProblemVO;
import com.nano.oj.model.vo.TagVO;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.ProblemService;
import com.nano.oj.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目服务实现类
 */
@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    @Resource
    private UserService userService;

    @Resource
    private TagMapper tagMapper;

    @Resource
    private ProblemTagMapper problemTagMapper;

    /**
     * 创建题目（处理标签关联）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long addProblem(Problem problem, List<String> tags) {
        // 1. 插入题目
        boolean result = this.save(problem);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建题目失败");
        }

        // 2. 处理标签
        if (CollUtil.isNotEmpty(tags)) {
            saveProblemTags(problem.getId(), tags);
        }
        return problem.getId();
    }

    /**
     * 更新题目（处理标签关联）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateProblem(Problem problem, List<String> tags) {
        // 1. 更新题目基础信息
        boolean result = this.updateById(problem);
        if (!result) {
            return false;
        }

        // 2. 更新标签（如果传了 tags 参数）
        if (tags != null) {
            // 先删除该题目关联的所有旧标签
            LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ProblemTag::getProblemId, problem.getId());
            problemTagMapper.delete(wrapper);

            // 再插入新标签
            if (CollUtil.isNotEmpty(tags)) {
                saveProblemTags(problem.getId(), tags);
            }
        }
        return true;
    }

    /**
     * 辅助方法：保存标签及关联关系
     */
    private void saveProblemTags(Long problemId, List<String> tagNames) {
        // 去重
        List<String> uniqueTags = tagNames.stream().distinct().toList();

        for (String tagName : uniqueTags) {
            // 查标签是否存在
            LambdaQueryWrapper<Tag> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.eq(Tag::getName, tagName);
            Tag tag = tagMapper.selectOne(tagWrapper);

            // 不存在则创建
            if (tag == null) {
                tag = new Tag();
                tag.setName(tagName);
                tag.setUserId(-1L); // -1 代表系统/管理员自动创建
                tag.setIsDelete(0);
                tagMapper.insert(tag);
            }

            // 建立关联
            ProblemTag problemTag = new ProblemTag();
            problemTag.setProblemId(problemId);
            problemTag.setTagId(tag.getId());
            problemTagMapper.insert(problemTag);
        }
    }

    /**
     * 获取题目列表 VO（关联查询标签）
     */
    @Override
    public Page<ProblemVO> getProblemVOPage(Page<Problem> problemPage, HttpServletRequest request) {
        List<Problem> problemList = problemPage.getRecords();
        Page<ProblemVO> problemVOPage = new Page<>(problemPage.getCurrent(), problemPage.getSize(), problemPage.getTotal());

        if (CollUtil.isEmpty(problemList)) {
            return problemVOPage;
        }

        // 1. 获取当前页所有题目 ID
        List<Long> problemIds = problemList.stream().map(Problem::getId).collect(Collectors.toList());

        // 2. 查询关联表 problem_tag
        LambdaQueryWrapper<ProblemTag> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.in(ProblemTag::getProblemId, problemIds);
        List<ProblemTag> problemTags = problemTagMapper.selectList(ptWrapper);

        // 3. 提取涉及的所有 tag_id
        Set<Long> tagIds = problemTags.stream().map(ProblemTag::getTagId).collect(Collectors.toSet());

        // 4. 查询 Tag 详情，并转为 Map<Id, Tag>
        Map<Long, Tag> tagMap = new HashMap<>();
        if (CollUtil.isNotEmpty(tagIds)) {
            List<Tag> dbTags = tagMapper.selectBatchIds(tagIds);
            tagMap = dbTags.stream().collect(Collectors.toMap(Tag::getId, tag -> tag));
        }

        // 5. 将标签按 problemId 分组：Map<ProblemId, List<Tag>>
        Map<Long, List<Tag>> problemIdToTagsMap = new HashMap<>();
        for (ProblemTag pt : problemTags) {
            Long pid = pt.getProblemId();
            Long tid = pt.getTagId();
            if (tagMap.containsKey(tid)) {
                problemIdToTagsMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(tagMap.get(tid));
            }
        }

        // 6. 填充 ProblemVO
        List<ProblemVO> problemVOList = problemList.stream().map(problem -> {
            ProblemVO problemVO = ProblemVO.objToVo(problem);

            // 填充标签数据
            List<Tag> relatedTags = problemIdToTagsMap.getOrDefault(problem.getId(), new ArrayList<>());
            List<TagVO> tagVOs = relatedTags.stream().map(tag -> {
                TagVO vo = new TagVO();
                vo.setName(tag.getName());
                vo.setColor(tag.getColor());
                // 如果前端需要标签ID，也可以加上 vo.setId(tag.getId());
                return vo;
            }).collect(Collectors.toList());

            problemVO.setTags(tagVOs);

            // 填充创建人信息
            Long userId = problem.getUserId();
            User user = null;
            if (userId != null) {
                user = userService.getById(userId);
            }
            UserVO userVO = userService.getUserVO(user);
            problemVO.setUserVO(userVO);

            return problemVO;
        }).collect(Collectors.toList());

        problemVOPage.setRecords(problemVOList);
        return problemVOPage;
    }

    /**
     * 获取单个题目 VO（关联查询标签）
     */
    @Override
    public ProblemVO getProblemVO(Problem problem, HttpServletRequest request) {
        // 1. 基础转换
        ProblemVO problemVO = ProblemVO.objToVo(problem);

        // 2. 查询关联表 problem_tag
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getProblemId, problem.getId());
        List<ProblemTag> problemTags = problemTagMapper.selectList(wrapper);

        // 3. 查询具体的 Tag 信息
        List<TagVO> tagVOs = new ArrayList<>();
        if (CollUtil.isNotEmpty(problemTags)) {
            Set<Long> tagIds = problemTags.stream().map(ProblemTag::getTagId).collect(Collectors.toSet());
            List<Tag> tags = tagMapper.selectBatchIds(tagIds);

            // 转为 TagVO
            tagVOs = tags.stream().map(tag -> {
                TagVO vo = new TagVO();
                vo.setName(tag.getName());
                vo.setColor(tag.getColor());
                return vo;
            }).collect(Collectors.toList());
        }

        // 4. 填充
        problemVO.setTags(tagVOs);

        return problemVO;
    }

}