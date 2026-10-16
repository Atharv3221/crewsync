package github.atharv3221.crewsync.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes an {@link ApiError} from places outside Spring MVC, such as security filters. */
@Component
@RequiredArgsConstructor
public class ApiErrorWriter {

  private final JsonMapper jsonMapper;

  public void write(HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    jsonMapper.writeValue(response.getOutputStream(), ApiError.of(status, message));
  }
}
