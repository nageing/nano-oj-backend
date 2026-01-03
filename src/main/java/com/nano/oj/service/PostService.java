package com.nano.oj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.nano.oj.model.dto.post.PostQueryRequest;
import com.nano.oj.model.entity.Post;
import com.nano.oj.model.vo.PostVO;

/**
 * 帖子服务接口
 */
public interface PostService extends IService<Post> {

    /**
     * 获取查询条件
     */
    QueryWrapper<Post> getQueryWrapper(PostQueryRequest postQueryRequest);

    /**
     * 分页获取帖子封装
     */
    Page<PostVO> getPostVOPage(Page<Post> postPage);
}