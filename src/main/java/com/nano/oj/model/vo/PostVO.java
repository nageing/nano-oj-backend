package com.nano.oj.model.vo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nano.oj.model.entity.Post;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 帖子视图对象
 */
@Data
public class PostVO implements Serializable {

    private final static Gson GSON = new Gson();

    private Long id;
    private String title;
    private String content;
    private Integer thumbNum;
    private Integer favourNum;
    private Long userId;
    private Long questionId;
    private Date createTime;
    private Date updateTime;

    /**
     * 标签列表 (JSON -> List)
     */
    private List<String> tagList;

    /**
     * 创建人信息 (头像、昵称)
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;

    /**
     * 包装类转对象
     */
    public static PostVO objToVo(Post post) {
        if (post == null) {
            return null;
        }
        PostVO postVO = new PostVO();
        BeanUtils.copyProperties(post, postVO);

        // tags 字符串转 List
        if (post.getTags() != null) {
            postVO.setTagList(GSON.fromJson(post.getTags(), new TypeToken<List<String>>() {}.getType()));
        }
        return postVO;
    }
}