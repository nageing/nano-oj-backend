package com.nano.oj.model.vo;

import lombok.Data;
import java.io.Serializable;

@Data
public class TagVO implements Serializable {
    private String name;
    private String color;

    private static final long serialVersionUID = 1L;
}