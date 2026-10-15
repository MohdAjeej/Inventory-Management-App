package com.b2binventory.controller;

import com.b2binventory.dto.EmployeeDtos.*;
import com.b2binventory.dto.PageResponse;
import com.b2binventory.entity.InvitationStatus;
import com.b2binventory.service.EmployeeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/permissions")
    public PermissionCatalog permissions() {
        return employeeService.permissionCatalog();
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public PageResponse<EmployeeResponse> list(@RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return employeeService.list(q, page, Math.min(size, 200));
    }

    @GetMapping("/employees/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeResponse get(@PathVariable Long id) {
        return employeeService.get(id);
    }

    @PutMapping("/employees/{id}/role")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeResponse changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest request) {
        return employeeService.changeRole(id, request.role());
    }

    @PutMapping("/employees/{id}/permissions")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeResponse setPermissions(@PathVariable Long id, @Valid @RequestBody PermissionOverridesRequest request) {
        return employeeService.setPermissions(id, request.overrides());
    }

    @PostMapping("/employees/{id}/deactivate")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeResponse deactivate(@PathVariable Long id) {
        return employeeService.deactivate(id);
    }

    @PostMapping("/employees/{id}/reactivate")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeResponse reactivate(@PathVariable Long id) {
        return employeeService.reactivate(id);
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public PageResponse<InvitationResponse> invitations(@RequestParam(required = false) InvitationStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        return employeeService.invitations(status, page, Math.min(size, 200));
    }

    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public InvitationResponse invite(@Valid @RequestBody InviteRequest request) {
        return employeeService.invite(request);
    }

    @DeleteMapping("/invitations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE')")
    public void cancelInvitation(@PathVariable Long id) {
        employeeService.cancelInvitation(id);
    }
}
