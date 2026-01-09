package com.nano.oj.mq;

import com.nano.oj.config.MqConfig;
import com.nano.oj.model.entity.Contest;
import com.nano.oj.model.entity.QuestionSubmit;
import com.nano.oj.service.ContestRankingService;
import com.nano.oj.service.ContestService;
import com.nano.oj.service.QuestionSubmitService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
@Slf4j
public class JudgeMessageConsumer {

    @Resource
    private QuestionSubmitService questionSubmitService;
    @Resource
    private ContestService contestService;
    @Resource
    private ContestRankingService contestRankingService;

    // ✅ 监听注解：指定监听哪个队列
    @RabbitListener(queues = {MqConfig.JUDGE_QUEUE}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到 MQ 消息: {}", message);
        long submitId = Long.parseLong(message);

        try {
            // 1. 拿着 ID 查数据
            QuestionSubmit submit = questionSubmitService.getById(submitId);

            // 2. 只有比赛题目才处理
            if (submit != null && submit.getContestId() != null && submit.getContestId() > 0) {
                Contest contest = contestService.getById(submit.getContestId());
                if (contest != null) {
                    // 3. 更新排行榜 (调用你刚才写的那个 Service)
                    contestRankingService.updateRanking(contest, submit);
                }
            }

            // 4. ✅ 确认消息 (告诉 MQ 我处理完了)
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 处理失败，拒绝消息 (这里设为 false 表示丢弃，防止死循环报错)
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}