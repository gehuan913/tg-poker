package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper。
 *
 * <p>按昵称 / ID 搜索（发红包选联系人用）写在
 * {@code resources/mapper/AppUserMapper.xml}。</p>
 */
@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {

    /**
     * 搜索用户。
     *
     * @param keyword  昵称的 LIKE 模式，由调用方拼成 %xxx%
     * @param raw      原始输入，用于纯数字时按 ID 精确匹配
     * @param excludeId 排除自己
     * @param limit    最多返回条数
     */
    List<AppUser> search(@Param("keyword") String keyword,
                         @Param("raw") String raw,
                         @Param("excludeId") long excludeId,
                         @Param("limit") int limit);
}
