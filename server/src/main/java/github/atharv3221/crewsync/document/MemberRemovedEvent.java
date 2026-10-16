package github.atharv3221.crewsync.document;

import java.util.UUID;

/** Published when a member loses access to a document, so their live sessions can be closed. */
public record MemberRemovedEvent(UUID docId, UUID userId) {
}
