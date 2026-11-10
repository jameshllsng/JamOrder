package br.com.jamorder.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcLocalUserRepository {

    private static final String SELECT_COLUMNS = "SELECT id, username, password_hash, display_name, role, active, department_id FROM app_users";
    private static final RowMapper<LocalUser> ROW_MAPPER = (rs, row) -> new LocalUser(
            rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("display_name"),
            UserRole.valueOf(rs.getString("role")), rs.getBoolean("active"), rs.getObject("department_id", Long.class));

    private final JdbcTemplate jdbcTemplate;

    public JdbcLocalUserRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<LocalUser> findByUsername(String username) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE username = ?", ROW_MAPPER, username).stream().findFirst();
    }

    public boolean hasAnyUser() { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM app_users)", Boolean.class)); }

    public List<LocalUser> findAll() { return jdbcTemplate.query(SELECT_COLUMNS + " ORDER BY username", ROW_MAPPER); }

    public Optional<LocalUser> findById(long id) { return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst(); }

    public void update(LocalUser user) {
        jdbcTemplate.update("UPDATE app_users SET display_name = ?, role = ?, active = ?, department_id = ? WHERE id = ?",
                user.displayName(), user.role().name(), user.active(), user.departmentId(), user.id());
    }

    public void updatePassword(long id, String passwordHash) { jdbcTemplate.update("UPDATE app_users SET password_hash = ? WHERE id = ?", passwordHash, id); }

    public Optional<UserPalette> findPalette(String username) {
        return jdbcTemplate.query("SELECT palette FROM app_users WHERE username = ?",
                        (rs, row) -> rs.getString("palette"), username).stream()
                .filter(palette -> palette != null)
                .map(UserPalette::valueOf)
                .findFirst();
    }

    public void updatePalette(String username, UserPalette palette) {
        jdbcTemplate.update("UPDATE app_users SET palette = ? WHERE username = ?", palette.name(), username);
    }

    public long countActiveAdmins() { return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users WHERE role = 'ADMIN' AND active = TRUE", Long.class); }

    public void insert(LocalUser user) {
        jdbcTemplate.update("INSERT INTO app_users (username, password_hash, display_name, role, active, department_id) VALUES (?, ?, ?, ?, ?, ?)",
                user.username(), user.passwordHash(), user.displayName(), user.role().name(), user.active(), user.departmentId());
    }
}
