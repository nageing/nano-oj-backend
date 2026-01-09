package com.nano.oj.model.dto.contest;

import lombok.Data;
import java.io.Serializable;

@Data
public class ContestApplyRequest implements Serializable {
    private Long contestId;
    private String password; // 如果比赛加密，需要传密码
}