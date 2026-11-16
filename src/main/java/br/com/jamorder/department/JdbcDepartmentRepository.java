package br.com.jamorder.department;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JdbcDepartmentRepository {

    private static final RowMapper<Department> ROW_MAPPER = (resultSet, rowNumber) -> new Department(
            resultSet.getLong("id"), resultSet.getString("name"), resultSet.getBoolean("active"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcDepartmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Department> findAll() {
        return jdbcTemplate.query("SELECT id, name, active FROM departments ORDER BY name", ROW_MAPPER);
    }

    public Optional<Department> findById(long id) {
        return jdbcTemplate.query("SELECT id, name, active FROM departments WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public Department insert(String name) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO departments (name) VALUES (?) RETURNING id", Long.class, name);
        return findById(id).orElseThrow();
    }

    public void update(Department department) {
        jdbcTemplate.update("UPDATE departments SET name = ?, active = ? WHERE id = ?",
                department.name(), department.active(), department.id());
    }
}
