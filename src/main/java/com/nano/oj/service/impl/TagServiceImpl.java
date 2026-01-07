package com.nano.oj.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.nano.oj.mapper.TagMapper;
import com.nano.oj.model.entity.Tag;
import com.nano.oj.service.TagService;
import org.springframework.stereotype.Service;

@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {
}