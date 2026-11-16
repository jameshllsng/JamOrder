package br.com.jamorder.department;

/** Invalid department choice or data; the message is shown to the user. */
public class DepartmentException extends RuntimeException {
    public DepartmentException(String message) {
        super(message);
    }
}
