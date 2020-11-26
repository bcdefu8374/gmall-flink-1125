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
@NoArgsConstructor
@AllArgsConstructor

public class UrlViewCount {
    private String url;
    private Long windowEnd;
    private Long count;
}
