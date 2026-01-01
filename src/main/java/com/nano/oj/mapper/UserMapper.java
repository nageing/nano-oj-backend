package com.nano.oj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nano.oj.model.entity.User;

/**
 * 用户数据库操作
 * 继承 BaseMapper<User> 后，自动拥有 CRUD 能力
 */
public interface UserMapper extends BaseMapper<User> {
    // 这里即便什么都不写，MyBatis Plus 也已经帮你写好了增删改查
}