package com.da.gallery.controller;

import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.xpack.usage.Base;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.da.gallery.annotation.AuthCheck;
import com.da.gallery.common.BaseResponse;
import com.da.gallery.common.DeleteRequest;
import com.da.gallery.common.ErrorCode;
import com.da.gallery.common.ResultUtils;
import com.da.gallery.constant.UserConstant;
import com.da.gallery.exception.BusinessException;
import com.da.gallery.exception.ThrowUtils;
import com.da.gallery.model.dto.picture.*;
import com.da.gallery.model.entity.Picture;
import com.da.gallery.model.entity.User;
import com.da.gallery.model.enums.PictureReviewStatusEnum;
import com.da.gallery.model.vo.PictureTagCategory;
import com.da.gallery.model.vo.PictureVO;
import com.da.gallery.service.PictureService;
import com.da.gallery.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private PictureService pictureService;
    @Resource
    private UserService userService;

    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }


    /**
     * 删除
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldPicture.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = pictureService.removeById(id);
        return ResultUtils.success(b);
    }

    /**
     * 更新（仅管理员）
     *
     * @param pictureUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest, HttpServletRequest request) {
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        User loginUser = userService.getLoginUser(request);
        List<String> tags = pictureUpdateRequest.getTagList();
        if (tags != null) {
            picture.setTags(JSONUtil.toJsonStr(tags));
        }
        // 参数校验
        pictureService.validPicture(picture, false);
        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        long id = pictureUpdateRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        boolean result = pictureService.updateById(picture);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = pictureService.getById(id);
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 普通用户未过审的图片不允许查看
        if(!userService.isAdmin(request) && !picture.getReviewStatus().equals(PictureReviewStatusEnum.PASS.getValue())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"图片未过审");
        }
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取列表（仅管理员）
     *
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 分页获取列表（封装类）
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 普通用户只能看到审核通过的数据
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 分页获取当前用户创建的资源列表
     *
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<PictureVO>> listMyPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                               HttpServletRequest request) {
        if (pictureQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        pictureQueryRequest.setUserId(loginUser.getId());
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    // endregion

    /**
     * 编辑（用户）
     *
     * @param pictureEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        User loginUser = userService.getLoginUser(request);
        List<String> tags = pictureEditRequest.getTagList();
        if (tags != null) {
            picture.setTags(JSONUtil.toJsonStr(tags));
        }
        // 参数校验
        pictureService.validPicture(picture, false);
        // 更新编辑时间
        picture.setEditTime(new Date());
        // 补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        long id = pictureEditRequest.getId();
        // 判断是否存在
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = pictureService.updateById(picture);
        return ResultUtils.success(result);
    }

    /**
     * 审核图片（仅管理员可用）
     *
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(pictureReviewRequest == null || pictureReviewRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        Long pictureId = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        boolean result = pictureService.doPictureReview(pictureId, reviewStatus, reviewMessage, request);
        return ResultUtils.success(result);
    }

    /**
     * 获取标签分类列表
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory () {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "情感", "学习", "科技", "游戏", "汽车", "美食", "旅游", "时尚");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }

}
