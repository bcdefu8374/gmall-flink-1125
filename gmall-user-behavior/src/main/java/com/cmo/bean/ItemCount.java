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

public class ItemCount {

    private Long itemId;
    private Long windowEnd;
    private Long count;
}
