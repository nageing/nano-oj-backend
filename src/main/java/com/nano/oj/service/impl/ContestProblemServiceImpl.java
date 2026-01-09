package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.mapper.ContestProblemMapper;
import com.nano.oj.model.entity.ContestProblem;
import com.nano.oj.service.ContestProblemService;
import org.springframework.stereotype.Service;

/**
 * 比赛题目关联服务实现类
 */
@Service
public class ContestProblemServiceImpl extends ServiceImpl<ContestProblemMapper, ContestProblem> implements ContestProblemService {
}