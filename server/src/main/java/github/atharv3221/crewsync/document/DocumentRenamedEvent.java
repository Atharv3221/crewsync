package github.atharv3221.crewsync.document;

import java.util.UUID;

/** Published when a document's title changes, so open editors can show the new one. */
public record DocumentRenamedEvent(UUID docId, String title, long titleVersion) {
}
