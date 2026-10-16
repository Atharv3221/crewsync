package github.atharv3221.crewsync.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "crewsync.allowed-origin-patterns=https://app.example.com")
@AutoConfigureMockMvc
class CorsTest {

  private static final String ALLOWED = "https://app.example.com";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void preflightFromAnAllowedOriginAllowsTheAuthAndJsonHeadersWithoutCredentials()
      throws Exception {
    mockMvc.perform(options("/api/docs")
            .header(HttpHeaders.ORIGIN, ALLOWED)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            "authorization, content-type"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  @Test
  void preflightAskingForAnotherHeaderIsRejected() throws Exception {
    mockMvc.perform(options("/api/docs")
            .header(HttpHeaders.ORIGIN, ALLOWED)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-custom"))
        .andExpect(status().isForbidden());
  }

  @Test
  void preflightFromAnotherOriginIsRejected() throws Exception {
    mockMvc.perform(options("/api/docs")
            .header(HttpHeaders.ORIGIN, "https://evil.example.com")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void anUnauthenticatedCallStillCarriesTheCorsHeaderSoTheClientCanRead401()
      throws Exception {
    mockMvc.perform(get("/api/me").header(HttpHeaders.ORIGIN, ALLOWED))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
  }
}
