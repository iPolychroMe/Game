package com.joezeo.joefgame.dao.mapper;

import com.joezeo.joefgame.dao.pojo.CommentLikeUser;
import com.joezeo.joefgame.dao.pojo.CommentLikeUserExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.RowBounds;

public interface CommentLikeUserMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    long countByExample(CommentLikeUserExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int deleteByExample(CommentLikeUserExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int deleteByPrimaryKey(Long id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int insert(CommentLikeUser record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int insertSelective(CommentLikeUser record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    List<CommentLikeUser> selectByExampleWithRowbounds(CommentLikeUserExample example, RowBounds rowBounds);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    List<CommentLikeUser> selectByExample(CommentLikeUserExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    CommentLikeUser selectByPrimaryKey(Long id);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int updateByExampleSelective(@Param("record") CommentLikeUser record, @Param("example") CommentLikeUserExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int updateByExample(@Param("record") CommentLikeUser record, @Param("example") CommentLikeUserExample example);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int updateByPrimaryKeySelective(CommentLikeUser record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table t_comment_like_user
     *
     * @mbg.generated Wed Apr 08 13:31:03 CST 2020
     */
    int updateByPrimaryKey(CommentLikeUser record);
}