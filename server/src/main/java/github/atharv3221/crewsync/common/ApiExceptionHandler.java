package github.atharv3221.crewsync.common;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiError handleNotFound(NotFoundException exception) {
    return ApiError.of(HttpStatus.NOT_FOUND, exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleIllegalArgument(IllegalArgumentException exception) {
    return ApiError.of(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
    return ApiError.of(HttpStatus.BAD_REQUEST, "Invalid value for '" + exception.getName() + "'");
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiError handleUnreadableBody(HttpMessageNotReadableException exception) {
    return ApiError.of(HttpStatus.BAD_REQUEST, "Malformed request body");
  }
}
