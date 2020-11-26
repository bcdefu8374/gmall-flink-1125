package com.cmo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author chen
 * @topic
 * @create 2020-11-26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PvCount {
    private String pv;
    private Long windowEnd;
    private Long count;
}
