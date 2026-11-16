package br.com.jamorder.security;

import br.com.jamorder.department.Department;
import br.com.jamorder.department.JdbcDepartmentRepository;
import br.com.jamorder.user.JdbcLocalUserRepository;
import br.com.jamorder.user.LocalUser;
import br.com.jamorder.user.UserPalette;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final JdbcLocalUserRepository userRepository;
    private final JdbcDepartmentRepository departmentRepository;

    public SessionController(JdbcLocalUserRepository userRepository, JdbcDepartmentRepository departmentRepository) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    @GetMapping("/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    /** Includes the user's department so the UI can lock the department of new requisitions. */
    @GetMapping("/auth/me")
    AuthenticatedUserResponse authenticatedUser(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.substring("ROLE_".length()))
                .orElse("USER");
        Optional<LocalUser> user = userRepository.findByUsername(authentication.getName());
        String displayName = user.map(LocalUser::displayName).orElse(authentication.getName());
        Optional<Department> department = user
                .filter(found -> !found.seesAllDepartments())
                .flatMap(found -> departmentRepository.findById(found.departmentId()));
        UserPalette palette = userRepository.findPalette(authentication.getName()).orElse(null);
        return new AuthenticatedUserResponse(authentication.getName(), role, displayName,
                department.map(Department::id).orElse(null), department.map(Department::name).orElse(null), palette);
    }

    /** Every signed-in user can choose their own palette. */
    @PutMapping("/auth/me/palette")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updatePalette(@Valid @RequestBody UpdatePaletteRequest request, Authentication authentication) {
        userRepository.updatePalette(authentication.getName(), request.palette());
    }

    record CsrfTokenResponse(String token, String headerName) { }

    record UpdatePaletteRequest(@NotNull UserPalette palette) { }

    /** {@code departmentId} is null when the user can see every department; {@code palette} is null until the user picks one. */
    record AuthenticatedUserResponse(String username, String role, String displayName, Long departmentId, String departmentName,
                                     UserPalette palette) { }
}
