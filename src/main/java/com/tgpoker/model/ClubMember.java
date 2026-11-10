package com.tgpoker.model;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("club_member")
public class ClubMember {

    private Long clubId;
    private Long userId;
    /** OWNER / ADMIN / MEMBER。 */
    private String role;
    /** ACTIVE / PENDING。 */
    private String status;
    private LocalDateTime joinedAt;
}
