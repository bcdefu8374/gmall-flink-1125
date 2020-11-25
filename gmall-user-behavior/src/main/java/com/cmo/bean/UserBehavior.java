package com.cmo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author chen
 * @topic
 * @create 2020-11-25
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserBehavior {

    private Long userId;
    private Long itemId;
    private Integer categoryId;
    private String behavior;
    private Long timestamp;
}
