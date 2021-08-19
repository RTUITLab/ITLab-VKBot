package ru.rtuitlab.notify.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.rtuitlab.notify.models.Feedback;
import ru.rtuitlab.notify.models.Message;
import ru.rtuitlab.notify.models.MessageDTO;
import ru.rtuitlab.notify.models.User;
import ru.rtuitlab.notify.redis.RedisPublisher;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedbackService implements MessageHandler {

    private final RedisPublisher redisPublisher;
    private final ObjectMapper om;
    private final UserService userService;

    public FeedbackService(RedisPublisher redisPublisher, ObjectMapper om, UserService userService) {
        this.redisPublisher = redisPublisher;
        this.om = om;
        this.userService = userService;
    }

    @Value("${database.redis.sendChannel}")
    private String channel;
    @Value("${secrets.queryFeedbackAdmins}")
    private String query;

    @Override
    public void handleMessage(String message) {
        log.info("Feedback service handle message: " + message);
        sendMessage(message);
    }

    @Override
    public void sendMessage(String message) {
        try {
            Feedback feedback = om.readValue(message, Feedback.class);
            if (feedback.getSender() == null || feedback.getMessage() == null) {
                log.info("Feedback service handle bad massage: " + feedback);
                return;
            }
            Optional<List<String>> usersIds = getUsersIds();
            if (!usersIds.isPresent()) {
                return;
            }
            MessageDTO messageDTO = makeMessage(feedback, usersIds.get());
            redisPublisher.publish(channel, om.writeValueAsString(messageDTO));
            log.info("feedback publish: " + messageDTO);
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    public MessageDTO makeMessage(Feedback feedback, List<String> usersIds) {
        Message message = new Message();
        message.setTitle("Обратная связь");
        String[] shortInfo = feedback.getMessage().split(" ", 8);
        shortInfo = Arrays.copyOf(shortInfo, shortInfo.length - 1);
        message.setBody(
                String.format("%s отправил обратную связь: \"%s...\"",
                        feedback.getSender(),
                        // short info for receiver - 7 words of feedback
                        String.join(" ", shortInfo)));

        MessageDTO messageDTO = new MessageDTO();

        messageDTO.setUsers(usersIds);
        messageDTO.setMessage(message);
        return messageDTO;
    }

    private Optional<List<String>> getUsersIds() {
        Optional<List<User>> users = userService.getUsers(query);
        if (!users.isPresent()) {
            log.error("Can't get users info");
            return Optional.empty();
        }

        List<String> usersIds = users
                .get()
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());

        return Optional.of(usersIds);
    }
}
