package ru.rtuitlab.notify.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.rtuitlab.notify.models.User;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {

    private final RestTemplate restTemplate;

    @Value("${secrets.token}")
    private String token;
    @Value("${secrets.url}")
    private String url;
//    @Value("${secrets.query}")
//    private String query;

    public UserService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Send get request for users info
     * @return List of users
     */
    public Optional<List<User>> getUsers(String query) {
        HttpEntity<String> request = getHeaders();
        try {
            log.info("Send get request for users: " + url + query);
            ResponseEntity<User[]> response = restTemplate.exchange(url + query, HttpMethod.GET, request, User[].class);
            log.info("Users have been received");
            if (response.getBody() == null) {
                log.error("Response body is null / Can't get users info from response");
                return Optional.empty();
            }
            List<User> users = Arrays.asList(response.getBody());
            log.info("Users information has been accepted. Success");
            return Optional.of(users);
        }
        catch (Exception e) {
            log.error("Get request failed: " + url + query);
        }
        return Optional.empty();
    }

    /**
     * Find user by his id in the general list of users received by method getUsers()
     * @param userId - UUID of user (String)
     * @return User entity if success or <b><u>null pointer</u></b> if user not found
     */
    public Optional<User> getUser(String userId) {
        Optional<List<User>> users = getUsers("");
        if (!users.isPresent()) {
            log.error("user list from getUsers() is empty");
            return Optional.empty();
        }
        log.info("Find user '" + userId + "' in the general list of users");
        User res = null;
        for (User user : users.get()) {
            if (user.getId().equals(userId)) {
                res = user;
                break;
            }
        }
        if (res == null) {
            log.error("Can't find user '" + userId + "' in the general list of users");
            return Optional.empty();
        }
        log.info("User '" + userId + "' has been found");
        return Optional.of(res);
    }

    /**
     * Method to get authorization and accept headers for users request
     * @return headers
     */
    private HttpEntity<String> getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add("Key", token);
        return new HttpEntity<>("body", headers);
    }


}
