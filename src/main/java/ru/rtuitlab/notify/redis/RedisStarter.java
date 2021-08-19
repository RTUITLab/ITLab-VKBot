package ru.rtuitlab.notify.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.rtuitlab.notify.services.*;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class RedisStarter {

    private final ReportService reportService;
    private final EventService eventService;
    private final CommentService commentService;
    private final FeedbackService feedbackService;
//    private final TestService testService;

    @Value("${database.redis.host:localhost}")
    private String host;
    @Value("${database.redis.port:6379}")
    private Integer port;
    @Value("${database.redis.password:}")
    private String password;
    @Value("#{'${database.redis.channels}'.split(',')}")
    private List<String> channels;
    @Value("${database.redis.timeout:30}")
    private Integer timeout;

    public RedisStarter(ReportService reportService, EventService eventService, CommentService commentService, FeedbackService feedbackService/*, TestService testService*/) {
        this.reportService = reportService;
        this.eventService = eventService;
        this.commentService = commentService;
        this.feedbackService = feedbackService;
//        this.testService = testService;
    }


    @PostConstruct
    private void init() {
        initListeners(channels);
    }

    private void initListeners(List<String> channels) {
        ExecutorService executorService = Executors.newFixedThreadPool(channels.size());
        executorService.execute(new Listener(reportService, channels.get(0)));
        executorService.execute(new Listener(eventService, channels.get(1)));
        executorService.execute(new Listener(commentService, channels.get(2)));
        executorService.execute(new Listener(feedbackService, channels.get(3)));
//        executorService.execute(new Listener(testService, channels.get(4)));
        log.info("All listeners have been executed");
    }


    private class Listener implements Runnable {

        private final MessageHandler messageHandler;
        private final String channel;

        private Listener(MessageHandler messageHandler, String channel) {
            this.messageHandler = messageHandler;
            this.channel = channel;
        }

        @Override
        public void run() {
            RedisListener redisListener = new RedisListenerImpl();
            redisListener.listenEvents(host, port, password, channel, timeout, messageHandler);
        }
    }
}
