import type { CharId, Op } from "./types.ts";

/**
 * Total order over character IDs: counter first, then site as a tie-breaker.
 * Returns negative if a < b, positive if a > b, 0 if equal.
 * Every replica must compute exactly the same result for the same pair.
 */
export function compareIds(a: CharId, b: CharId): number {
  let result = 0;
  if (a.counter !== b.counter) {
    result = a.counter - b.counter;
  } else if (a.site < b.site) {
    result = -1;
  } else if (a.site > b.site) {
    result = 1;
  }
  return result;
}

/** True if both IDs point to the same character. */
export function sameId(a: CharId | null, b: CharId | null): boolean {
  let result: boolean = a === b;
  if (a !== null && b !== null) {
    result = a.counter === b.counter && a.site === b.site;
  }
  return result;
}

/** String key for using a CharId. */
export function idKey(id: CharId): string {
  return `${id.counter}:${id.site}`;
}

/** The op's own id: for an insert, also the id of the character it creates. */
export function opId(op: Op): CharId {
  return { counter: op.counter, site: op.site };
}

/** Unique key per operation, in the server's op_key format: `kind:counter@site`. */
export function opKey(op: Op): string {
  return `${op.kind}:${op.counter}@${op.site}`;
}
