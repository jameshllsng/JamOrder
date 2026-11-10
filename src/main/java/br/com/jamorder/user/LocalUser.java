package br.com.jamorder.user;

/**
 * A local account. {@code departmentId} limits which requisitions the user can see and act on;
 * {@code null} means every department. Admins always see every department.
 */
public record LocalUser(Long id, String username, String passwordHash, String displayName, UserRole role, boolean active, Long departmentId) {

    public boolean seesAllDepartments() {
        return role == UserRole.ADMIN || departmentId == null;
    }
}
