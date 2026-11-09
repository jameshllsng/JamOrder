package br.com.jamorder.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManagementService {
    private final JdbcLocalUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    public UserManagementService(JdbcLocalUserRepository repository, PasswordEncoder passwordEncoder) { this.repository = repository; this.passwordEncoder = passwordEncoder; }
    @Transactional(readOnly = true) public List<UserResponse> findAll() { return repository.findAll().stream().map(UserResponse::from).toList(); }
    @Transactional public UserResponse create(CreateUserRequest request) { if (repository.findByUsername(request.username()).isPresent()) throw new UserManagementException("Já existe um usuário com este login."); LocalUser user = new LocalUser(null, request.username(), passwordEncoder.encode(request.password()), request.displayName(), request.role(), true); repository.insert(user); return UserResponse.from(repository.findByUsername(user.username()).orElseThrow()); }
    @Transactional public UserResponse update(long id, UpdateUserRequest request, String actorUsername) { LocalUser user = repository.findById(id).orElseThrow(() -> new UserManagementException("Usuário não encontrado.")); preventUnsafeAdminChange(user, request.role(), request.active(), actorUsername); LocalUser updated = new LocalUser(user.id(), user.username(), user.passwordHash(), request.displayName(), request.role(), request.active()); repository.update(updated); return UserResponse.from(updated); }
    @Transactional public void resetPassword(long id, ResetPasswordRequest request) { if (repository.findById(id).isEmpty()) throw new UserManagementException("Usuário não encontrado."); repository.updatePassword(id, passwordEncoder.encode(request.password())); }
    private void preventUnsafeAdminChange(LocalUser user, UserRole newRole, boolean active, String actorUsername) { if (user.username().equals(actorUsername) && (!active || newRole != UserRole.ADMIN)) throw new UserManagementException("Você não pode remover seu próprio acesso administrativo."); if (user.role() == UserRole.ADMIN && user.active() && (newRole != UserRole.ADMIN || !active) && repository.countActiveAdmins() == 1) throw new UserManagementException("Deve existir pelo menos um administrador ativo."); }
}
