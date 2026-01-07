package com.nano.oj.model.dto.user;

import lombok.Data;
import java.io.Serializable;

@Data
public class UserUpdatePasswordRequest implements Serializable {
    private String oldPassword;
    private String newPassword;
    private String checkPassword; // 确认新密码
    private static final long serialVersionUID = 1L;
}