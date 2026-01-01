package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.mapper.ProblemMapper;
import com.nano.oj.model.entity.Problem;
import com.nano.oj.service.ProblemService;
import org.springframework.stereotype.Service;

/**
 * 题目服务实现类
 */
@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {
}