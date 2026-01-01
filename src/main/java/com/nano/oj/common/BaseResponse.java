package com.nano.oj.common;

import lombok.Data;
import java.io.Serializable;

/**
 * 通用返回类
 * @param <T> 数据类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;      // 状态码 (0 成功, 其他失败)
    private T data;        // 数据
    private String message;// 消息

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }
}