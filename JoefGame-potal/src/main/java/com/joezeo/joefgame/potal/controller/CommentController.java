package com.joezeo.joefgame.potal.controller;

import com.joezeo.joefgame.common.dto.JsonResult;
import com.joezeo.joefgame.common.enums.CommentTypeEnum;
import com.joezeo.joefgame.common.enums.CustomizeErrorCode;
import com.joezeo.joefgame.common.dto.CommentDTO;
import com.joezeo.joefgame.common.dto.UserDTO;
import com.joezeo.joefgame.potal.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;

@RestController
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     *  提交一级评论
     */
    @PostMapping("/comment")
    public JsonResult<?> comment(@RequestBody CommentDTO commentDTO,
                              HttpSession session) {
        UserDTO user = (UserDTO) session.getAttribute("user");
        if (user == null) {
            return JsonResult.errorOf(CustomizeErrorCode.USER_NOT_LOGIN);
        }

        commentService.addComment(commentDTO, user);

        return JsonResult.okOf(null);
    }

    /**
     *  提交二级评论
     */
    @PostMapping("/comment/subComment")
    public JsonResult<?> subComment(@RequestBody CommentDTO commentDTO, HttpSession session){
        UserDTO user = (UserDTO) session.getAttribute("user");
        if (user == null) {
            return JsonResult.errorOf(CustomizeErrorCode.USER_NOT_LOGIN);
        }

        commentService.addComment(commentDTO, user);

        return JsonResult.okOf(null);
    }


    /**
     * 获取二级评论
     */
    @PostMapping("/comment/getSubcomment")
    public JsonResult<List<CommentDTO>> getSubComment(@RequestBody CommentDTO commentDTO) {

        // 查询当前id评论全部二级评论
        List<CommentDTO> commentDTOS = commentService.listByParentId(commentDTO.getId(), CommentTypeEnum.COMMENT);

        return JsonResult.okOf(commentDTOS);
    }

    @PostMapping("/comment/like")
    public JsonResult<CommentDTO> like(@RequestBody CommentDTO commentDTO){
        commentDTO = commentService.like(commentDTO.getId(), commentDTO.getUserid());
        return JsonResult.okOf(commentDTO);
    }

    @PostMapping("/comment/unlike")
    public JsonResult<CommentDTO> unlike(@RequestBody CommentDTO commentDTO){
        commentDTO = commentService.unlike(commentDTO.getId(), commentDTO.getUserid());
        return JsonResult.okOf(commentDTO);
    }
}
