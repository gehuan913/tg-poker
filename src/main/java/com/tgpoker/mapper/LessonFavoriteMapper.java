package com.tgpoker.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 教学收藏 Mapper。
 *
 * <p>表只有三列，没有建实体类；SQL 见 {@code resources/mapper/LessonFavoriteMapper.xml}。</p>
 */
@Mapper
public interface LessonFavoriteMapper {

    /** 收藏一条课程；重复收藏会因主键冲突抛异常，调用方忽略即可。 */
    int insert(@Param("userId") long userId, @Param("lessonId") long lessonId);

    /** 取消收藏。 */
    int delete(@Param("userId") long userId, @Param("lessonId") long lessonId);

    /** 我的收藏列表（带标题、封面、时长）。 */
    List<Map<String, Object>> list(@Param("userId") long userId);
}
