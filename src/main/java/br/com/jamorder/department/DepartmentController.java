package br.com.jamorder.department;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Any signed-in user can list departments; only admins create or change them (see SecurityConfiguration). */
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService service;

    public DepartmentController(DepartmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<Department> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Department create(@Valid @RequestBody SaveDepartmentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public Department update(@PathVariable long id, @Valid @RequestBody SaveDepartmentRequest request) {
        return service.update(id, request);
    }
}
