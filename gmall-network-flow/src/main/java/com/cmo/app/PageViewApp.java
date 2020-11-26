package com.cmo.app;

import com.cmo.bean.PvCount;
import com.cmo.bean.UserBehavior;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.util.Iterator;
import java.util.Random;

/**
 * @author chen
 * @topic
 * @create 2020-11-26
 */
public class PageViewApp {
    public static void main(String[] args) throws Exception {
        //1.创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(4);
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.从文件获取数据并转化为javaBean
        SingleOutputStreamOperator<UserBehavior> userBahavior = env.readTextFile("input/UserBehavior.csv")
                .map(new MapFunction<String, UserBehavior>() {
                    @Override
                    public UserBehavior map(String value) throws Exception {
                        //切分数据
                        String[] split = value.split(",");
                        return new UserBehavior(
                                Long.parseLong(split[0]),
                                Long.parseLong(split[1]),
                                Integer.parseInt(split[2]),
                                split[3],
                                Long.parseLong(split[4])
                        );
                    }
                })
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<UserBehavior>() {
                    @Override
                    public long extractAscendingTimestamp(UserBehavior element) {
                        return element.getTimestamp() * 1000L;
                    }
                });

        //3.按照Itemid过滤，分组，开窗，计算数据
        SingleOutputStreamOperator<PvCount> pvCountAgg = userBahavior
                .filter(data -> "pv".equals(data.getBehavior()))
                .map(new MapFunction<UserBehavior, Tuple2<String, Integer>>() {
                    @Override
                    public Tuple2<String, Integer> map(UserBehavior value) throws Exception {
                        Random random = new Random();
                        return new Tuple2<String, Integer>("pv" + random.nextInt(4), 1);
                    }
                })
                .keyBy(0)
                .timeWindow(Time.hours(1))
                .aggregate(new PvCountAggFunc(), new PvCountWindowFunc());


        //4.按照窗口结束时间重新分组keyBy，process
        SingleOutputStreamOperator<String> processPvCount = pvCountAgg.keyBy(data -> data.getWindowEnd())
                .process(new PvCountProcessFunc());

        //4.打印输出
        //sum.print();
        processPvCount.print();

        //5.执行任务
        env.execute();
    }

    public static class  PvCountAggFunc implements AggregateFunction<Tuple2<String,Integer>,Long,Long>{

        @Override
        public Long createAccumulator() {
            return 0L;
        }

        @Override
        public Long add(Tuple2<String, Integer> value, Long accumulator) {
            return accumulator + 1L;
        }

        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    public static class PvCountWindowFunc implements WindowFunction<Long, PvCount, Tuple, TimeWindow>{

        @Override
        public void apply(Tuple tuple, TimeWindow window, Iterable<Long> input, Collector<PvCount> out) throws Exception {
            String field = tuple.getField(0);
            out.collect(new PvCount(field,window.getEnd(),input.iterator().next()));
        }

    }
    
    public static class PvCountProcessFunc extends KeyedProcessFunction<Long,PvCount,String>{

        //定义集合状态
        private ListState<PvCount> listState;

        @Override
        public void open(Configuration parameters) throws Exception {
            //
            listState = getRuntimeContext().getListState(new ListStateDescriptor<PvCount>("pv-state",PvCount.class));
        }

        @Override
        public void processElement(PvCount value, Context ctx, Collector<String> out) throws Exception {
            //1.添加一个数据把状态添加进去
            listState.add(value);
            //2.注册定时器
            ctx.timerService().registerEventTimeTimer(value.getWindowEnd() + 1L);
        }


        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            //取出状态信息
            Iterator<PvCount> iterator = listState.get().iterator();

            //定义最终一个小时的数据总和
            Long count = 0L;

            //遍历数据
            while (iterator.hasNext()) {
                count += iterator.next().getCount();
            }
            //输出信息
            out.collect("pv:" + count);

            //清空状态
            listState.clear();

        }
    }


}
