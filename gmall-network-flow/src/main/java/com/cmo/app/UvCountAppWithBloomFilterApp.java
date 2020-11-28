package com.cmo.app;

import com.cmo.bean.UserBehavior;
import com.cmo.bean.UvCount;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.timestamps.AscendingTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessAllWindowFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.Trigger;
import org.apache.flink.streaming.api.windowing.triggers.TriggerResult;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;
import redis.clients.jedis.Jedis;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Iterator;

/**
 * @author chen
 * @topic
 * @create 2020-11-27
 */
public class UvCountAppWithBloomFilterApp {
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
        SingleOutputStreamOperator<UvCount> result = userBehavior
                .timeWindowAll(Time.hours(1))
                .trigger(new MyTrigger())
                .process(new UvWithBloomFilterWindowFun());

        //4.打印
        result.print();

        //5.执行
        env.execute();

    }

    public static class MyTrigger extends Trigger<UserBehavior,TimeWindow>{

        @Override
        public TriggerResult onElement(UserBehavior element, long timestamp, TimeWindow window, TriggerContext ctx) throws Exception {
            return TriggerResult.FIRE_AND_PURGE;
        }

        @Override
        public TriggerResult onProcessingTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
            return TriggerResult.CONTINUE;
        }

        @Override
        public TriggerResult onEventTime(long time, TimeWindow window, TriggerContext ctx) throws Exception {
            return TriggerResult.CONTINUE;
        }

        @Override
        public void clear(TimeWindow window, TriggerContext ctx) throws Exception {

        }
    }

    public static class UvWithBloomFilterWindowFun extends ProcessAllWindowFunction<UserBehavior,UvCount,TimeWindow>{

        //定义redis连接
        Jedis jedis;

        //定义一个布隆过滤器
        MyBloomFilter myBloomFilter;

        //定义string类型的uvCount
        String uvCountRedisKey;

        @Override
        public void open(Configuration parameters) throws Exception {
            jedis = new Jedis("hadoop102", 6379);

            //定义布隆过滤器的容量
            myBloomFilter = new MyBloomFilter(1L << 29);//64M 一般要给一个数据集合是它本身的3-10倍，这里我们就定义了一个hashCode，我们是使用一个的3-10倍

            //redis里面存放的结构，如果数据量很大，使用String好一些，如果后期考虑要删除，也是使用String类型的。如果是hash就不能定义超时时间。
            //bitMap也要存入redis，redis天然支持bitMap，这大大减少了布隆过滤 器的大小 。

            //定义rediscountKey
            uvCountRedisKey = "UvCount";
        }

        @Override
        public void process(Context context, Iterable<UserBehavior> elements, Collector<UvCount> out) throws Exception {

            //1.获取窗口信息并指定UVCountRedisKey的Field，同时指定BitMap的RedisKey
            String windowEnd = new Timestamp(context.window().getEnd()).toString();
            String bitMapRedisKey ="UvBitMap:" + windowEnd;

            //2.判断当前的userId是否存在
            Long offset = myBloomFilter.hash(elements.iterator().next().getUserId() + "");
            Boolean exist = jedis.getbit(bitMapRedisKey, offset);

            //3.如果不存在，向Redis中累加数据，并将BitMap中对应的位置改为true
            if (!exist){
                jedis.hincrBy(uvCountRedisKey,windowEnd,1L);
                jedis.setbit(bitMapRedisKey,offset, true);
            }

            //4.取出Redis中对应的Count值，发送
            long count = Long.parseLong(jedis.hget(uvCountRedisKey, windowEnd));
            //5.发送数据
            out.collect(new UvCount("uv",windowEnd,count));
        }

        @Override
        public void close() throws Exception {
            jedis.close();
        }
    }

    public static class MyBloomFilter{
        //定义布隆 过滤器的容量,此时需要2的整次幂。
        private Long cap;

        public MyBloomFilter() {
        }

        public MyBloomFilter(Long cap) {
            this.cap = cap;
        }

        //要跟容量取模，得到一个hashcode，在正常生产 环境中要写三个
        public Long hash(String value){
            //定义一个结果值
            int result = 0;

            for (char c : value.toCharArray()) {
                result = result * 31 + c; //如果使用质数更不容易产生碰撞，越高位的数乘31乘的就越多。
            }

            //位与运算，比取模要效率高。
            return result & (cap - 1);
        }
    }
}
