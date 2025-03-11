package com.da.gallery.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.da.gallery.common.ErrorCode;
import com.da.gallery.constant.CommonConstant;
import com.da.gallery.exception.BusinessException;
import com.da.gallery.exception.ThrowUtils;
import com.da.gallery.manager.FileManager;
import com.da.gallery.mapper.PictureMapper;
import com.da.gallery.model.dto.file.UploadPictureResult;
import com.da.gallery.model.dto.picture.PictureQueryRequest;
import com.da.gallery.model.dto.picture.PictureUploadRequest;
import com.da.gallery.model.entity.Picture;
import com.da.gallery.model.entity.User;
import com.da.gallery.model.enums.PictureReviewStatusEnum;
import com.da.gallery.model.enums.UserRoleEnum;
import com.da.gallery.model.vo.PictureVO;
import com.da.gallery.model.vo.UserVO;
import com.da.gallery.service.PictureService;
import com.da.gallery.service.UserService;
import com.da.gallery.utils.SqlUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
* @author 13491
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-03-09 11:29:32
*/
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
    implements PictureService{

    @Resource
    private FileManager fileManager;
    @Resource
    private UserService userService;

    /**
     * 上传图片
     * @param multipartFile
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 用于判断是新增还是更新图片
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新图片，需要校验图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 仅本人或管理员可编辑图片
            if(!oldPicture.getUserId().equals(loginUser.getId()) || userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅本人或管理员可编辑图片");
            }
        }
        // 上传图片，得到信息
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        BeanUtil.copyProperties(uploadPictureResult, picture);
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");
        return PictureVO.objToVo(picture);
    }

    /**
     * 填充审核参数（用户编辑图片/更新图片，需要重新进行审核）
     * @param picture
     * @param user
     */
    @Override
    public void fillReviewParams(Picture picture, User user){
        if(userService.isAdmin(user)){
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewerId(user.getId());
            picture.setReviewTime(new Date());
        }else {
            // 普通用户上传或编辑图片，默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }

    }


    @Override
    public void validPicture(Picture picture, boolean add) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = picture.getId();
        String introduction = picture.getIntroduction();
        String url = picture.getUrl();
        // 有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "图片 id 不能为空");
        if (StringUtils.isNotBlank(url) && url.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片url过长");
        }
        if (StringUtils.isNotBlank(introduction) && introduction.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片简介过长");
        }
    }

    /**
     * 获取查询包装类
     *
     * @param
     * @return
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tagList = pictureQueryRequest.getTagList();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        Long userId = pictureQueryRequest.getUserId();
        String searchText = pictureQueryRequest.getSearchText();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        String reviewMessage = pictureQueryRequest.getReviewMessage();

        // 拼接查询条件
        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", introduction));
        }
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.like(StringUtils.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.like(StringUtils.isNotBlank(introduction), "introduction", introduction);
        if (CollUtil.isNotEmpty(tagList)) {
            for (String tag : tagList) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        queryWrapper.eq(ObjectUtils.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjectUtils.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(category), "category", category);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjectUtils.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjectUtils.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjectUtils.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjectUtils.isNotEmpty(picFormat), "picFormat", picFormat);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }



    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 1. 关联查询用户信息
        Long userId = picture.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        pictureVO.setUserVO(userVO);
        return pictureVO;
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> PictureList = picturePage.getRecords();
        Page<PictureVO> PictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(PictureList)) {
            return PictureVOPage;
        }
        // 1. 关联查询用户信息
        Set<Long> userIdSet = PictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        List<PictureVO> PictureVOList = PictureList.stream().map(picture -> {
            PictureVO pictureVO = PictureVO.objToVo(picture);
            Long userId = picture.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUserVO(userService.getUserVO(user));
            return pictureVO;
        }).collect(Collectors.toList());
        PictureVOPage.setRecords(PictureVOList);
        return PictureVOPage;
    }


    @Override
    public boolean doPictureReview(Long pictureId, Integer reviewStatus, String reviewMessage, HttpServletRequest request) {
        // 参数校验
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        if(reviewStatusEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态不合法");
        }
        if(pictureId == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片id不能为空");
        }
        if(reviewMessage.length() > 2000){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核信息过长");
        }
        // 校验图片是否存在
        Picture picture = getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR, "图片不存在");
        // 判断状态是否重复（已是该状态）
        ThrowUtils.throwIf(picture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR, "图片状态已为该状态");
        // 权限校验，仅管理员可用
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        ThrowUtils.throwIf(!isAdmin, ErrorCode.NO_AUTH_ERROR, "权限不足，仅管理员可审核");
        // 操作数据库
        Picture reviewPicture = new Picture();
        reviewPicture.setId(pictureId);
        reviewPicture.setReviewerId(loginUser.getId());
        reviewPicture.setReviewStatus(reviewStatus);
        reviewPicture.setReviewMessage(reviewMessage);
        reviewPicture.setReviewTime(new Date());
        boolean result = this.updateById(reviewPicture);
        ThrowUtils.throwIf(!result, ErrorCode.SYSTEM_ERROR, "审核失败，系统错误");
        return true;
    }


}




