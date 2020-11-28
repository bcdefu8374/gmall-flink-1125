package com.cmo.app;

import com.cmo.bean.UserBehavior;
import com.cmo.bean.UvCount;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author chen
 * @topic
 * @create 2020-11-27
 */
public class UvCountApp {
    public static void main(String[] args) throws Exception {

        //创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        //2.从文本获取数据转化为JavaBean
        SingleOutputStreamOperator<UserBehavior> userBehavior = env.readTextFile("input/UserBehavior.csv")
                .map(line -> {
                    String[] split = line.split(",");
                    return new UserBehavior(Long.parseLong(split[0]),
                            Long.parseLong(split[1]),
                            Integer.parseInt(split[2]),
                            split[3],
                            Long.parseLong(split[4]));
                })
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<UserBehavior>() {
                    @Override
                    public long extractAscendingTimestamp(UserBehavior element) {
                        return element.getTimestamp() * 1000L;
                    }
                });

        //3.开窗一个小时
        SingleOutputStreamOperator<UvCount> apply = userBehavior.timeWindowAll(Time.hours(1)).apply(new UvCountFunc());

        //4.打印
        apply.print();

        //5.执行
        env.execute();

    }

    public static class UvCountFunc implements AllWindowFunction<UserBehavior, UvCount, TimeWindow>{

        @Override
        public void apply(TimeWindow window, Iterable<UserBehavior> values, Collector<UvCount> out) throws Exception {

            //创建HashSet用于存放userId
            HashSet<Long> UserIds = new HashSet<>();

            //2.遍历value
            Iterator<UserBehavior> iterator = values.iterator();
            while (iterator.hasNext()) {
                UserIds.add(iterator.next().getUserId());
            }

            //3.输出
            String uv = "uv";
            String windowEnd = new Timestamp(window.getEnd()).toString();
            Long count = (long)UserIds.size();
            out.collect(new UvCount(uv,windowEnd,count));
        }

    }
}
