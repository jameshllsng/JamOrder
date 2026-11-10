package br.com.jamorder.user;
public record UserResponse(Long id, String username, String displayName, UserRole role, boolean active, Long departmentId) { static UserResponse from(LocalUser user) { return new UserResponse(user.id(), user.username(), user.displayName(), user.role(), user.active(), user.departmentId()); } }
