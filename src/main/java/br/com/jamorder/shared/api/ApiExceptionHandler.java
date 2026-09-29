package br.com.jamorder.shared.api;

import br.com.jamorder.purchaserequest.service.DuplicatePurchaseRequestNumberException;
import br.com.jamorder.purchaserequest.service.PurchaseRequestNotFoundException;
import br.com.jamorder.purchaserequest.service.PurchaseRequestNotOpenException;
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
        return problem(HttpStatus.NOT_FOUND, "RequisiÃ§Ã£o nÃ£o encontrada.");
    }

    @ExceptionHandler(DuplicatePurchaseRequestNumberException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateRequestNumber(DuplicatePurchaseRequestNumberException exception) {
        return problem(HttpStatus.CONFLICT, "JÃ¡ existe uma requisiÃ§Ã£o com este nÃºmero.");
    }

    @ExceptionHandler(PurchaseRequestNotOpenException.class)
    public ResponseEntity<ProblemDetail> handleInvalidStatus(PurchaseRequestNotOpenException exception) {
        return problem(HttpStatus.CONFLICT, "A requisiÃ§Ã£o nÃ£o estÃ¡ disponÃ­vel para esta operaÃ§Ã£o.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleInvalidArgument(IllegalArgumentException exception) {
        return problem(HttpStatus.CONFLICT, "A quantidade recebida excede a quantidade pendente.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.put(field, error.getDefaultMessage());
        });

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Existem campos invÃ¡lidos na requisiÃ§Ã£o.");
        problemDetail.setTitle("Dados invÃ¡lidos");
        problemDetail.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        return ResponseEntity.status(status).body(problemDetail);
    }
}
