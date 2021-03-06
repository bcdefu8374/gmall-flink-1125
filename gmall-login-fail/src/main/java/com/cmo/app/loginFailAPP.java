package com.cmo.app;

import com.cmo.bean.LoginEvent;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternSelectFunction;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author chen
 * @topic
 * @create 2020-11-28
 */
public class loginFailAPP {
    public static void main(String[] args) throws Exception {
        //1.创建环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.读取文本数据转化为JavaBean
        SingleOutputStreamOperator<LoginEvent> loginEventDS = env.readTextFile("input/LoginLog.csv")
                .map(line -> {
                    String[] split = line.split(",");
                    return new LoginEvent(Long.parseLong(split[0]),
                            split[1],
                            split[2],
                            Long.parseLong(split[3]));
                })
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<LoginEvent>(Time.seconds(3)) {

                    @Override
                    public long extractTimestamp(LoginEvent element) {
                        return element.getTimestamp() * 1000L;
                    }
                });

        //3.定义一个模式，指定多个事件互相之前的关系,作用与什么流，就给它流的类型
        /*Pattern<LoginEvent, LoginEvent> loginFailPattern = Pattern.<LoginEvent>begin("start")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent value) throws Exception {
                        return "fail".equals(value.getEventType());
                    }
                })
                .next("secondFail")
                .where(new SimpleCondition<LoginEvent>() {
                    @Override
                    public boolean filter(LoginEvent value) throws Exception {
                        return "fail".equals(value.getEventType());
                    }
                }).within(Time.seconds(2));*/

        Pattern<LoginEvent, LoginEvent> pattern = Pattern.<LoginEvent>begin("start")
                .where(new SimpleCondition<LoginEvent>() {
            @Override
            public boolean filter(LoginEvent value) throws Exception {
                return "fail".equals(value.getEventType());
            }
        })
                .times(2)
                .consecutive()  //严格近邻
                .within(Time.seconds(5));

        //4.将模式序列作用到流上
        //PatternStream<LoginEvent> patternStream = CEP.pattern(loginEventDS.keyBy(data -> data.getUserId()), loginFailPattern);
        PatternStream<LoginEvent> patternStream = CEP.pattern(loginEventDS.keyBy(data -> data.getUserId()), pattern);


        //5.提取事件
        SingleOutputStreamOperator<String> result = patternStream.select(new LoginFailSelectFunc());

        //6.打印
        result.print();

        //7.执行
        env.execute();

    }


    public static class LoginFailSelectFunc implements PatternSelectFunction<LoginEvent, String> {


        @Override
        public String select(Map<String, List<LoginEvent>> map) throws Exception {
            List<LoginEvent> start = map.get("start");
//            List<LoginEvent> next = pattern.get("next");
            return "在" + start.get(0).getTimestamp() +
                    "到" + start.get(1).getTimestamp() + " " +
                    start.get(0).getUserId() +
                    "之间登录失败2次！";
        }
    }
}
