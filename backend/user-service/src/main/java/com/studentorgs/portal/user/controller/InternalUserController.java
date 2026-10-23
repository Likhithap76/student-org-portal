package com.studentorgs.portal.user.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.user.dto.UserNameResponse;
import com.studentorgs.portal.user.dto.UserStatusResponse;
import com.studentorgs.portal.user.service.UserService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/** Service-to-service lookups. Needs X-Internal-Token; blocked at the gateway. */
@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{id}/status")
    public UserStatusResponse status(@PathVariable Long id) {
        return userService.status(id);
    }

    @GetMapping("/batch")
    public List<UserNameResponse> batch(@RequestParam List<Long> ids) {
        return userService.names(ids);
    }

    @GetMapping("/active-ids")
    public List<Long> activeIds() {
        return userService.activeIds();
    }
}
