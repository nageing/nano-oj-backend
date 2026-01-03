package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.common.ErrorCode;
import com.nano.oj.exception.BusinessException;
import com.nano.oj.mapper.PostMapper;
import com.nano.oj.model.dto.post.PostQueryRequest;
import com.nano.oj.model.entity.Post;
import com.nano.oj.model.entity.User;
import com.nano.oj.model.vo.PostVO;
import com.nano.oj.model.vo.UserVO;
import com.nano.oj.service.PostService;
import com.nano.oj.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 帖子服务实现
 */
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Override
    public QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        if (postQueryRequest == null) {
            return queryWrapper;
        }

        String searchText = postQueryRequest.getSearchText();
        Long questionId = postQueryRequest.getQuestionId();
        Long userId = postQueryRequest.getUserId();
        String sortField = postQueryRequest.getSortField();
        String sortOrder = postQueryRequest.getSortOrder();

        // 搜索标题或内容
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("content", searchText));
        }

        queryWrapper.eq(questionId != null, "question_id", questionId);
        queryWrapper.eq(userId != null, "user_id", userId);

        // 排序
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);

        return queryWrapper;
    }

    @Override
    public Page<PostVO> getPostVOPage(Page<Post> postPage) {
        List<Post> postList = postPage.getRecords();
        Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());

        if (postList.isEmpty()) {
            return postVOPage;
        }

        // 1. 批量转换
        List<PostVO> postVOList = postList.stream().map(PostVO::objToVo).collect(Collectors.toList());

        // 2. 填充 User 信息
        // 实际开发中可以用 Set<Long> userIds 批量查 User，这里先简单循环查
        for (PostVO postVO : postVOList) {
            Long userId = postVO.getUserId();
            User user = userService.getById(userId);
            if (user != null) {
                UserVO userVO = new UserVO();
                BeanUtils.copyProperties(user, userVO);
                postVO.setUser(userVO);
            }
        }

        postVOPage.setRecords(postVOList);
        return postVOPage;
    }
}