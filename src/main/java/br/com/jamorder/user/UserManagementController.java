package br.com.jamorder.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserManagementController {
    private final UserManagementService service;
    public UserManagementController(UserManagementService service) { this.service = service; }
    @GetMapping public List<UserResponse> findAll() { return service.findAll(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public UserResponse create(@Valid @RequestBody CreateUserRequest request) { return service.create(request); }
    @PutMapping("/{id}") public UserResponse update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest request, Authentication authentication) { return service.update(id, request, authentication.getName()); }
    @PutMapping("/{id}/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest request) { service.resetPassword(id, request); }
}
