package com.tgpoker.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tgpoker.model.LedgerEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LedgerEntryMapper extends BaseMapper<LedgerEntry> {
}
