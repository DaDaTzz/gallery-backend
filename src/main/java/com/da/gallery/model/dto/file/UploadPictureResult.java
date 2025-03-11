package com.da.gallery.model.dto.file;

import lombok.Data;

import java.io.Serializable;


/**
 * 图片上传请求结果封装
 */
@Data
public class UploadPictureResult implements Serializable {

    /**
     * 图片 url
     */
    private String url;

    /**
     * 图片名称
     */
    private String name;

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

    private static final long serialVersionUID = 1L;
}