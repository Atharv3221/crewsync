package github.atharv3221.crewsync.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void signupReturns201WithTokenAndUser() throws Exception {
    signup("{\"name\":\"Ada\",\"email\":\" Ada@Example.com\",\"password\":\"password1\"}")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isString())
        .andExpect(jsonPath("$.user.id").isString())
        .andExpect(jsonPath("$.user.name").value("Ada"))
        .andExpect(jsonPath("$.user.email").value("ada@example.com"))
        .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
  }

  @Test
  void duplicateEmailReturns409() throws Exception {
    signup("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"password1\"}");

    signup("{\"name\":\"Ada 2\",\"email\":\"ADA@example.com\",\"password\":\"password2\"}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value("An account with that email already exists"));
  }

  @Test
  void invalidSignupReturns400() throws Exception {
    signup("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"short\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Password must be at least 8 characters"));
    signup("{\"name\":\"Ada\",\"email\":\"nope\",\"password\":\"password1\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Enter a valid email address"));
  }

  @Test
  void loginReturnsTokenThatAuthenticates() throws Exception {
    signup("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"password1\"}");

    String body = login("{\"email\":\"ada@example.com\",\"password\":\"password1\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.user.name").value("Ada"))
        .andReturn().getResponse().getContentAsString();
    String token = body.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

    mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("ada@example.com"));
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSame401() throws Exception {
    signup("{\"name\":\"Ada\",\"email\":\"ada@example.com\",\"password\":\"password1\"}");

    login("{\"email\":\"ada@example.com\",\"password\":\"wrong-password\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
    login("{\"email\":\"nobody@example.com\",\"password\":\"password1\"}")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  @Test
  void authEndpointsIgnoreAStaleToken() throws Exception {
    mockMvc.perform(post("/api/auth/login").header("Authorization", "Bearer stale")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody@example.com\",\"password\":\"password1\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Invalid email or password"));
  }

  private ResultActions signup(String json) throws Exception {
    return mockMvc.perform(post("/api/auth/signup")
        .contentType(MediaType.APPLICATION_JSON).content(json));
  }

  private ResultActions login(String json) throws Exception {
    return mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON).content(json));
  }
}
