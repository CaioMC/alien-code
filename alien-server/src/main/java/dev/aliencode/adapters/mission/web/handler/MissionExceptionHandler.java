package dev.aliencode.adapters.mission.web.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.aliencode.core.mission.domain.exception.MissionNotFoundException;

/** Exceções das missões → respostas HTTP. Erros de formato (400) ficam no ApiExceptionHandler. */
@RestControllerAdvice
public class MissionExceptionHandler {

    @ExceptionHandler(MissionNotFoundException.class)
    ProblemDetail notFound(MissionNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
