package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 教学课程（知识 / 工具说明 / 训练材料）。
 *
 * <p>内容本身由运营产出，这张表只负责承载与分类；播放量/点赞是热度的唯一来源，
 * 不参与任何资金逻辑。</p>
 */
@Data
@TableName("lesson")
public class Lesson {

    /** 自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类：STRATEGY=知识，TOOL=工具，TRAINING=训练。 */
    private String category;

    /** 标题。 */
    private String title;

    /** 摘要，列表页展示。 */
    private String summary;

    /** 封面图 URL。 */
    private String cover;

    /** 媒体类型：VIDEO=视频，ARTICLE=图文。 */
    private String mediaType;

    /** 视频/音频地址。 */
    private String mediaUrl;

    /** 时长（秒）。 */
    private Integer durationSec;

    /** 点赞数。 */
    private Integer likes;

    /** 播放/阅读数。 */
    private Integer views;

    /** 正文（图文类内容）。 */
    private String content;

    /** 排序号，越小越靠前。 */
    private Integer sortNo;
}
