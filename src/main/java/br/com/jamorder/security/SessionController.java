package br.com.jamorder.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import br.com.jamorder.user.JdbcLocalUserRepository;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final JdbcLocalUserRepository userRepository;

    public SessionController(JdbcLocalUserRepository userRepository) { this.userRepository = userRepository; }

    @GetMapping("/csrf")
    CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getToken(), csrfToken.getHeaderName());
    }

    @GetMapping("/auth/me")
    AuthenticatedUserResponse authenticatedUser(Authentication authentication) {
        String role = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith("ROLE_"))
                .findFirst()
                .map(authority -> authority.substring("ROLE_".length()))
                .orElse("USER");
        String displayName = userRepository.findByUsername(authentication.getName())
                .map(user -> user.displayName())
                .orElse(authentication.getName());
        return new AuthenticatedUserResponse(authentication.getName(), role, displayName);
    }

    record CsrfTokenResponse(String token, String headerName) { }

    record AuthenticatedUserResponse(String username, String role, String displayName) { }
}
