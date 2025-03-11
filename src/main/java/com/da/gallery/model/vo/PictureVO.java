package com.da.gallery.model.vo;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.da.gallery.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片
 * @TableName picture
 */
@TableName(value ="picture")
@Data
public class PictureVO implements Serializable {

    private static final long serialVersionUID = -8564456489565022467L;
    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签
     */
    private List<String> tagList;

    /**
     * 图片体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 创建用户信息
     */
    private UserVO userVO;

    /**
     * 审核状态
     */
    private Integer reviewStatus;



    /**
     * 包装类转对象
     *
     * @param PictureVO
     * @return
     */
    public static Picture voToObj(PictureVO PictureVO) {
        if (PictureVO == null) {
            return null;
        }
        Picture Picture = new Picture();
        BeanUtils.copyProperties(PictureVO, Picture);
        List<String> tagList = PictureVO.getTagList();
        Picture.setTags(JSONUtil.toJsonStr(tagList));
        return Picture;
    }

    /**
     * 对象转包装类
     *
     * @param Picture
     * @return
     */
    public static PictureVO objToVo(Picture Picture) {
        if (Picture == null) {
            return null;
        }
        PictureVO PictureVO = new PictureVO();
        BeanUtils.copyProperties(Picture, PictureVO);
        PictureVO.setTagList(JSONUtil.toList(Picture.getTags(), String.class));
        return PictureVO;
    }

}