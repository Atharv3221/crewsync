package github.atharv3221.crewsync.user;

import github.atharv3221.crewsync.common.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public User get(UUID id) {
    return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
  }

  @Transactional
  public User rename(UUID id, String newName) {
    User user = get(id);
    user.rename(newName);
    return user;
  }
}
