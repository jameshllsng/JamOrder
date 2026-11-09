package br.com.jamorder.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public class JdbcLocalUserRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcLocalUserRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<LocalUser> findByUsername(String username) {
        return jdbcTemplate.query("SELECT id, username, password_hash, display_name, role, active FROM app_users WHERE username = ?",
                (rs, row) -> new LocalUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                        rs.getString("display_name"), UserRole.valueOf(rs.getString("role")), rs.getBoolean("active")), username)
                .stream().findFirst();
    }

    public boolean hasAnyUser() { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM app_users)", Boolean.class)); }

    public List<LocalUser> findAll() { return jdbcTemplate.query("SELECT id, username, password_hash, display_name, role, active FROM app_users ORDER BY username", (rs, row) -> new LocalUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("display_name"), UserRole.valueOf(rs.getString("role")), rs.getBoolean("active"))); }

    public Optional<LocalUser> findById(long id) { return jdbcTemplate.query("SELECT id, username, password_hash, display_name, role, active FROM app_users WHERE id = ?", (rs, row) -> new LocalUser(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("display_name"), UserRole.valueOf(rs.getString("role")), rs.getBoolean("active")), id).stream().findFirst(); }

    public void update(LocalUser user) { jdbcTemplate.update("UPDATE app_users SET display_name = ?, role = ?, active = ? WHERE id = ?", user.displayName(), user.role().name(), user.active(), user.id()); }

    public void updatePassword(long id, String passwordHash) { jdbcTemplate.update("UPDATE app_users SET password_hash = ? WHERE id = ?", passwordHash, id); }

    public long countActiveAdmins() { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users WHERE role = 'ADMIN' AND active = TRUE", Long.class); }

    public void insert(LocalUser user) {
        jdbcTemplate.update("INSERT INTO app_users (username, password_hash, display_name, role, active) VALUES (?, ?, ?, ?, ?)",
                user.username(), user.passwordHash(), user.displayName(), user.role().name(), user.active());
    }
}
