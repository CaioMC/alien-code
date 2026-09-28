package dev.aliencode.adapters.toca.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.aliencode.adapters.toca.web.dto.TocaView;
import dev.aliencode.core.toca.domain.TocaNotFoundException;
import dev.aliencode.core.toca.domain.TocaProvisioningException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TocaNotFoundException.class)
    ProblemDetail notFound(TocaNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    /** A Toca foi criada mas não ficou pronta; devolve o registro dela (FAILED) para diagnóstico. */
    @ExceptionHandler(TocaProvisioningException.class)
    ProblemDetail provisioningFailed(TocaProvisioningException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setProperty("toca", TocaView.from(e.toca()));
        return problem;
    }
}
