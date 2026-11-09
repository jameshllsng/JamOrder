package br.com.jamorder.shared.api;

import br.com.jamorder.purchaserequest.service.DuplicatePurchaseRequestNumberException;
import br.com.jamorder.purchaserequest.service.InvalidTotvsRegistrationException;
import br.com.jamorder.purchaserequest.service.PurchaseRequestItemNotFoundException;
import br.com.jamorder.purchaserequest.service.PurchaseRequestNotFoundException;
import br.com.jamorder.purchaserequest.service.PurchaseRequestNotOpenException;
import br.com.jamorder.purchaserequest.service.ReceiptQuantityExceededException;
import br.com.jamorder.user.UserManagementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PurchaseRequestNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(PurchaseRequestNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Requisição não encontrada.");
    }

    @ExceptionHandler(PurchaseRequestItemNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleItemNotFound(PurchaseRequestItemNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Item não encontrado nesta requisição.");
    }

    @ExceptionHandler(DuplicatePurchaseRequestNumberException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateRequestNumber(DuplicatePurchaseRequestNumberException exception) {
        return problem(HttpStatus.CONFLICT, "Já existe uma requisição com este número.");
    }

    @ExceptionHandler(PurchaseRequestNotOpenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStatus(PurchaseRequestNotOpenException exception) {
        return problem(HttpStatus.CONFLICT, "A requisição não está disponível para esta operação.");
    }

    @ExceptionHandler(ReceiptQuantityExceededException.class)
    public ResponseEntity<ProblemDetail> handleReceiptQuantityExceeded(ReceiptQuantityExceededException exception) {
        return problem(HttpStatus.CONFLICT, "A quantidade recebida excede a quantidade pendente.");
    }

    @ExceptionHandler(InvalidTotvsRegistrationException.class)
    public ResponseEntity<ProblemDetail> handleInvalidTotvsRegistration(InvalidTotvsRegistrationException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(UserManagementException.class)
    public ResponseEntity<ProblemDetail> handleUserManagement(UserManagementException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Existem campos inválidos na requisição.");
        problemDetail.setTitle("Dados inválidos");
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(problemDetail);
    }
}
