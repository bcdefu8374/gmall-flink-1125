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
public class ApacheLog {

    private String ip;
    private String userId;
    private Long eventTime;
    private String method;
    private String url;
}
