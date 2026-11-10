package br.com.jamorder.department;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final JdbcDepartmentRepository repository;

    public DepartmentService(JdbcDepartmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Department> findAll() {
        return repository.findAll();
    }

    @Transactional
    public Department create(SaveDepartmentRequest request) {
        try {
            return repository.insert(request.name().trim());
        } catch (DuplicateKeyException exception) {
            throw new DepartmentException("Já existe um setor com este nome.");
        }
    }

    @Transactional
    public Department update(long id, SaveDepartmentRequest request) {
        Department current = getById(id);
        Department updated = new Department(current.id(), request.name().trim(), request.active());
        try {
            repository.update(updated);
        } catch (DuplicateKeyException exception) {
            throw new DepartmentException("Já existe um setor com este nome.");
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public Department getById(long id) {
        return repository.findById(id).orElseThrow(() -> new DepartmentException("Setor não encontrado."));
    }

    /** Validates a department chosen for a requisition or a user: it must exist and be active. */
    @Transactional(readOnly = true)
    public Department requireActive(Long id) {
        if (id == null) {
            throw new DepartmentException("Selecione o setor.");
        }
        Department department = getById(id);
        if (!department.active()) {
            throw new DepartmentException("O setor " + department.name() + " está inativo.");
        }
        return department;
    }
}
