package com.cmo.app;

import com.cmo.bean.ItemCount;
import com.cmo.bean.UserBehavior;
import org.apache.commons.compress.utils.Lists;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.WindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;

/**
 * @author chen
 * @topic
 * @create 2020-11-25
 */
public class HotItemApp {
    public static void main(String[] args) throws Exception {

        //获取执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        //设置eventTime方法
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);

        //2.从文件获取数据转化为JavaBean
        SingleOutputStreamOperator<UserBehavior> userDS = env.readTextFile("input/UserBehavior.csv")
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
                                Long.parseLong(split[4]));
                    }
                })
                .assignTimestampsAndWatermarks(new AscendingTimestampExtractor<UserBehavior>() {
                    @Override
                    public long extractAscendingTimestamp(UserBehavior element) {
                        //指定那个字段为时间字段
                        return element.getTimestamp() * 1000L;
                    }
                });

        //3.按照"pv"过滤，按照itemID分组,开窗，计算
        SingleOutputStreamOperator<ItemCount> itemCountDS = userDS
                .filter(data -> "pv".equals(data.getBehavior()))
                .keyBy(data -> data.getItemId())
                .timeWindow(Time.hours(1), Time.minutes(5))
                .aggregate(new ItemIdCountAggFunc(), new ItemCountWindowFunc());

        //4.按照windowEnd分组，排序输出
        SingleOutputStreamOperator<String> result = itemCountDS.keyBy("windowEnd")
                .process(new itemTopNProcessFunc(5));

        //5.打印
        result.print();

        //6.执行
        env.execute();

    }


    //自定义增量聚合函数
    public static class ItemIdCountAggFunc implements AggregateFunction<UserBehavior,Long,Long>{

        //初始化
        @Override
        public Long createAccumulator() {
            return 0L;
        }

        //每调用一次就+1
        @Override
        public Long add(UserBehavior value, Long accumulator) {
            return accumulator + 1L;
        }

        //
        @Override
        public Long getResult(Long accumulator) {
            return accumulator;
        }

        @Override
        public Long merge(Long a, Long b) {
            return a + b;
        }
    }

    //自定义窗口函数
    public static class ItemCountWindowFunc implements WindowFunction<Long, ItemCount,Long, TimeWindow>{

        @Override
        public void apply(Long itemId, TimeWindow window, Iterable<Long> input, Collector<ItemCount> out) throws Exception {

            long end = window.getEnd();
            Long count = input.iterator().next();
            out.collect(new ItemCount(itemId,end,count));
        }
    }

    //自定排序KeyedProcessFunction
    public static class itemTopNProcessFunc extends KeyedProcessFunction<Tuple, ItemCount, String> {

        //topN属性
        private Integer topsize;

        public itemTopNProcessFunc(){

        }

        public itemTopNProcessFunc(Integer topsize){
            this.topsize = topsize;
        }

        //定义listState用于存放相同的Key[windowEnd]的数据
        private ListState<ItemCount> listState;

        @Override
        public void open(Configuration parameters) throws Exception {
            listState =getRuntimeContext().getListState(new ListStateDescriptor<ItemCount>("list-state",ItemCount.class));
        }

        @Override
        public void processElement(ItemCount value, Context ctx, Collector<String> out) throws Exception {
            //每来一条数据，将数据存入集合状态
            listState.add(value);
            //注册定时器
            ctx.timerService().registerProcessingTimeTimer(value.getWindowEnd() + 1L);
        }

        @Override
        public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) throws Exception {
            //1.取出状态中所有数据
            Iterator<ItemCount> iterator = listState.get().iterator();
            //取出来是一个迭代器，放入list数组中好排序
            ArrayList<ItemCount> itemCounts = Lists.newArrayList(iterator);

            //2.排序
            itemCounts.sort(new Comparator<ItemCount>() {
                @Override
                public int compare(ItemCount o1, ItemCount o2) {
                    if (o1.getCount() > o2.getCount()){
                        return -1;
                    }else if (o1.getCount() < o2.getCount()){
                        return 1;
                    }else {
                        return 0;
                    }
                }
            });

            StringBuilder sb = new StringBuilder();
            sb.append("===========================\n");
            sb.append("当前窗口结束时间：" + new Timestamp(timestamp - 1L)).append("\n");

            //3.取topNsize条数据输出
            for (int i = 0; i < Math.min(topsize, itemCounts.size()); i++) {
                //取出数据
                ItemCount itemCount = itemCounts.get(i);
                sb.append("Top ").append(i + 1);
                sb.append("ItemId= ").append(itemCount.getItemId());
                sb.append("商品热度：").append(itemCount.getCount());
                sb.append("\n");
            }
            sb.append("===========================\n\n");

            //4.清空状态，如果不清空就会越积越多，如果是放内存就会崩掉
            listState.clear();

            Thread.sleep(1000);

            //5.输出数据
            out.collect(sb.toString());
        }



    }

}
