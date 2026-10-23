package com.studentorgs.portal.task.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studentorgs.portal.task.dto.MemberNameResponse;
import com.studentorgs.portal.task.service.CommitteeService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;

/** Service-to-service lookups. Needs X-Internal-Token; blocked at the gateway. */
@Hidden
@RestController
@RequestMapping("/internal/committees")
@RequiredArgsConstructor
public class InternalCommitteeController {

    private final CommitteeService committeeService;

    @GetMapping("/{id}/members")
    public List<MemberNameResponse> members(@PathVariable Long id) {
        return committeeService.memberNames(id);
    }
}
