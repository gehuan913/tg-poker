package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("quiz_question")
public class QuizQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String category;
    private String question;
    /** JSON 数组字符串，如 ["跟注","加注","弃牌"]。 */
    private String options;
    private Integer answer;
    /** explain 是 MySQL 保留字，列名必须带反引号。 */
    @TableField("`explain`")
    private String explain;
}
