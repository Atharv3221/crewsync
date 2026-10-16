package github.atharv3221.crewsync.common;

public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String resource, Object id) {
    this(resource + " " + id + " not found");
  }
}
