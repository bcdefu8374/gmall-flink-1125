package com.cmo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author chen
 * @topic
 * @create 2020-11-27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor

public class ChannelBehaviorCount {
    private String channel;
    private String behavior;
    private String windowEnd;
    private Long count;
}
