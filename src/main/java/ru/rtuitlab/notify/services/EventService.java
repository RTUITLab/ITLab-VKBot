package ru.rtuitlab.notify.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.rtuitlab.notify.models.*;
import ru.rtuitlab.notify.redis.RedisPublisher;
import ru.rtuitlab.notify.repositories.InviteRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EventService implements MessageHandler{

    private final RedisPublisher redisPublisher;
    private final InviteRepo inviteRepo;
    private final UserService userService;
    private final ObjectMapper om;

    public EventService(RedisPublisher redisPublisher, InviteRepo inviteRepo, UserService userService, ObjectMapper om) {
        this.redisPublisher = redisPublisher;
        this.inviteRepo = inviteRepo;
        this.userService = userService;
        this.om = om;
    }

    @Value("${database.redis.sendChannel}")
    private String channel;
    @Value("${secrets.queryAll}")
    private String query;

    /**
     * Handle messages in events channel in redis
     * If has "accept" keyword, then send message to receiveAccept()
     * Else send message to sendMessage()
     * @param message - ("accept" keyword + json object of Invite entity) / json object of Event entity
     */
    @Override
    public void handleMessage(String message) {
        log.info("Event service handle message: " + message);
        if (message.substring(0, 6).equals("accept")) {
            receiveAccept(message);
        }
        else {
            sendMessage(message);
        }
    }

    /**
     * Method that delete entity invite from DB if user accept invite
     * @param message - "accept" keyword following by space and json object of Invite entity
     */
    public void receiveAccept(String message) {
        try {
            String obj = message.substring(6, message.length());
            Invite invite = om.readValue(obj, Invite.class);
            List<Invite> invites = inviteRepo.findAllByInvitedIdAndEvent(
                    invite.getInvitedId(), invite.getEvent());
            if (invites.size() != 0) {
                inviteRepo.deleteAll(invites);
                log.info("user " + invite.getInvitedId()
                        + " accepted invite for '" + invite.getEvent() + "' event");
            } else {
                log.error("Can't delete " + message);
            }
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Method which send notification about event
     * @param message - json object of Event entity
     */
    @Override
    public void sendMessage(String message) {
        try {
            Event event = om.readValue(message, Event.class);

            sendPersonalInvites(event);

            if (event.getInvitedIds().size() < event.getSize()) {
                sendPublicInvites(event);
            }

        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * Send invites to everyone (except of already invited) if event has free spaces
     * @param event - event entity
     * @throws JsonProcessingException
     */
    private void sendPublicInvites(Event event) throws JsonProcessingException {
        Optional<List<User>> users = userService.getUsers(query);
        if (!users.isPresent()) {
            log.error("Can't get users info");
            return;
        }
        List<String> usersIds = users
                .get()
                .stream()
                .map(User::getId)
                .filter(id -> !event.getInvitedIds().contains(id))
                .collect(Collectors.toList());
        event.setInvitedIds(usersIds);
        MessageDTO messageDTO = makeMessage(
                event, "Появилось новое свободное событие '" + event.getTitle() + "'");
        redisPublisher.publish(channel, om.writeValueAsString(messageDTO));
        log.info("send public invites about " + messageDTO.getMessage().getTitle() + " event");
    }

    private void sendPersonalInvites(Event event) throws JsonProcessingException {
        Optional<List<Invite>> invites = saveInvites(getInvites(event));
        if (invites.isPresent()) {
            log.info("invites of event '" + event.getTitle() + "' have been saved");
        }
        else {
            log.error("invites of event '" + event.getTitle() + "' have not been saved");
        }
        MessageDTO messageDTO = makeMessage(
                event, "Вас пригласили на мероприятие '" + event.getTitle() + "'");
        redisPublisher.publish(channel, om.writeValueAsString(messageDTO));
        log.info("send personal invites about " + messageDTO.getMessage().getTitle() + " event");
    }



    /**
     * Convert recieved info about event to ready message for notify service
     * @param event
     * @return messageDTO
     */
    public MessageDTO makeMessage(Event event, String text) {
        Message message = new Message();
        message.setTitle(event.getTitle());
        message.setBody(text);
        message.setDate(event.getDate());

        MessageDTO messageDTO = new MessageDTO();
        messageDTO.setUsers(event.getInvitedIds());
        messageDTO.setMessage(message);
        return messageDTO;
    }

    /**
     * Get the list of invites from event entity
     * @param event
     * @return List of invite
     */
    public List<Invite> getInvites(Event event) {
        List<Invite> inviteList = new ArrayList<>();
        event.getInvitedIds().forEach(inviteId -> {
            Invite invite = new Invite();
            invite.setInvitedId(inviteId);
            invite.setDate(event.getDate());
            invite.setEvent(event.getTitle());
            inviteList.add(invite);
        });
        return inviteList;
    }

    /**
     * Save the list of invites in the database
     * @param invites - list of invite entity
     * @return list of invites that was saved in database
     */
    public Optional<List<Invite>> saveInvites(List<Invite> invites) {
        return Optional.of(inviteRepo.saveAll(invites));
    }
}
