package com.da.gallery.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.da.gallery.model.dto.picture.PictureQueryRequest;
import com.da.gallery.model.dto.picture.PictureUploadRequest;
import com.da.gallery.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.da.gallery.model.entity.User;
import com.da.gallery.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
* @author 13491
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-03-09 11:29:32
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser);

    void fillReviewParams(Picture picture, User user);

    /**
     * 校验
     *
     * @param Picture
     * @param add
     */
    void validPicture(Picture Picture, boolean add);

    /**
     * 获取查询条件
     *
     * @param PictureQueryRequest
     * @return
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest PictureQueryRequest);



    /**
     * 获取帖子封装
     *
     * @param Picture
     * @param request
     * @return
     */
    PictureVO getPictureVO(Picture Picture, HttpServletRequest request);

    /**
     * 分页获取帖子封装
     *
     * @param PicturePage
     * @param request
     * @return
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> PicturePage, HttpServletRequest request);

    /**
     * 图片审核（仅管理员可用）
     * @param pictureId
     * @param reviewStatus
     * @param reviewMessage
     * @param request
     * @return
     */
    boolean doPictureReview(Long pictureId, Integer reviewStatus, String reviewMessage, HttpServletRequest request);
}
