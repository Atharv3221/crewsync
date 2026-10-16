import { describe, expect, it } from "vitest";
import { opKey, Rga, type Op } from "./index";

/** Seeded random generator, so a failing chaos run can be replayed exactly. */
function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 2 ** 32;
  };
}

function typeText(doc: Rga, pos: number, text: string): Op[] {
  const ops: Op[] = [];
  for (let i = 0; i < text.length; i++) {
    ops.push(doc.localInsert(pos + i, text[i]));
  }
  return ops;
}

function deliver(doc: Rga, ops: readonly Op[]): void {
  for (const op of ops) {
    doc.apply(op);
  }
}

describe("Rga basics", () => {
  it("inserts and deletes locally", () => {
    const doc = new Rga("a");
    typeText(doc, 0, "cat");
    doc.localInsert(1, "h");
    expect(doc.text()).toBe("chat");
    doc.localDelete(0);
    expect(doc.text()).toBe("hat");
  });

  it("a second replica reproduces the same text from the ops", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    deliver(b, typeText(a, 0, "hello"));
    expect(b.text()).toBe("hello");
  });

  it("produces flat ops: the op's own counter/site next to kind", () => {
    const doc = new Rga("A");
    const insert = doc.localInsert(0, "x");
    expect(insert).toEqual({
      kind: "insert",
      counter: 1,
      site: "A",
      origin: null,
      value: "x",
    });
    expect(opKey(insert)).toBe("insert:1@A");
  });

  it("gives a delete its own id and ticks the clock", () => {
    const doc = new Rga("A");
    doc.localInsert(0, "x");
    const del = doc.localDelete(0);
    expect(del).toEqual({
      kind: "delete",
      counter: 2,
      site: "A",
      target: { counter: 1, site: "A" },
    });
    expect(opKey(del)).toBe("delete:2@A");
    expect(doc.localInsert(0, "y").counter).toBe(3);
  });

  it("rejects anything that's not one UTF-16 code unit", () => {
    const doc = new Rga("A");
    expect(() => doc.localInsert(0, "ab")).toThrow(RangeError);
    expect(() => doc.localInsert(0, "")).toThrow(RangeError);
    expect(() => doc.localInsert(0, "😀")).toThrow(RangeError);
  });
});

describe("Rga convergence properties", () => {
  it("orders concurrent inserts at the same spot identically (commutativity)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    deliver(b, typeText(a, 0, "a"));

    const x = a.localInsert(1, "X");
    const y = b.localInsert(1, "Y");
    a.apply(y);
    b.apply(x);

    expect(a.text()).toBe(b.text());
    expect(a.text()).toBe("aYX");
  });

  it("places text typed after seeing an edit where the user typed it (Lamport clock)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    deliver(b, typeText(a, 0, "ab"));

    const x = b.localInsert(1, "x");
    a.apply(x);

    expect(b.text()).toBe("axb");
    expect(a.text()).toBe("axb");
  });

  it("ignores duplicate ops (idempotence)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    const ops = typeText(a, 0, "abc");
    ops.push(a.localDelete(1));
    deliver(b, ops);
    deliver(b, ops);
    expect(b.text()).toBe(a.text());
  });

  it("buffers ops that arrive before their dependency (causality)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    const ops = typeText(a, 0, "abc");

    b.apply(ops[2]);
    b.apply(ops[1]);
    b.apply(ops[0]);
    expect(b.pendingCount).toBe(0);
  });

  it("a delete waits for its target (causality)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    const c = new Rga("C");
    const insert = a.localInsert(0, "x");
    b.apply(insert);
    const del = b.localDelete(0);

    c.apply(del);
    expect(c.pendingCount).toBe(1);
    c.apply(insert);
    expect(c.text()).toBe("");
    expect(c.pendingCount).toBe(0);
    // the applied delete advanced C's Lamport clock past it
    expect(c.localInsert(0, "y").counter).toBeGreaterThan(del.counter);
  });

  it("converges when two sites delete the same character concurrently", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    const c = new Rga("C");
    const ops = typeText(a, 0, "xy");
    deliver(b, ops);

    const fromA = a.localDelete(0);
    const fromB = b.localDelete(0);
    expect(opKey(fromA)).not.toBe(opKey(fromB));
    a.apply(fromB);
    b.apply(fromA);
    deliver(c, [fromB, fromA, ...ops]);

    for (const doc of [a, b, c]) {
      expect(doc.text()).toBe("y");
      expect(doc.pendingCount).toBe(0);
    }
  });

  it("can insert after a character someone else deleted (tombstones)", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    deliver(b, typeText(a, 0, "ab"));

    const del = a.localDelete(0);
    const ins = b.localInsert(1, "X");
    a.apply(ins);
    b.apply(del);
    const correctSeq = "Xb";
    expect(a.text()).toBe(correctSeq);
    expect(b.text()).toBe(correctSeq);
  });
});

describe("Rga cursor helpers", () => {
  it("keeps a cursor beside the same character after a remote insert before it", () => {
    const a = new Rga("A");
    const b = new Rga("B");
    deliver(b, typeText(a, 0, "hello"));

    const anchor = b.idAt(3);
    b.apply(a.localInsert(0, "*"));
    expect(b.posOf(anchor)).toBe(4);
  });

  it("moves the cursor left when the character before it is deleted", () => {
    const doc = new Rga("A");
    typeText(doc, 0, "hello");

    const anchor = doc.idAt(3);
    doc.localDelete(2);
    expect(doc.text()).toBe("helo");
    expect(doc.posOf(anchor)).toBe(2);
  });

  it("maps the start of the document to null and back to 0", () => {
    const doc = new Rga("A");
    typeText(doc, 0, "abc");
    expect(doc.idAt(0)).toBe(null);
    expect(doc.posOf(null)).toBe(0);
  });
});

/** Random edits on 2–5 replicas over a network that reorders and duplicates messages. */
function chaosRun(seed: number): Rga[] {
  const random = seededRandom(seed);
  const randomInt = (n: number) => Math.floor(random() * n);

  const replicaCount = 2 + randomInt(4);
  const docs = Array.from(
    { length: replicaCount },
    (_, i) => new Rga(`site-${i}`),
  );
  const inboxes: Op[][] = docs.map(() => []);

  for (let step = 0; step < 400; step++) {
    const r = randomInt(replicaCount);
    const doc = docs[r];
    const length = doc.text().length;

    if (randomInt(10) < 6) {
      // 60%: a local edit, sent to every other replica
      const op =
        length === 0 || random() < 0.6
          ? doc.localInsert(
              randomInt(length + 1),
              String.fromCharCode(97 + randomInt(26)),
            )
          : doc.localDelete(randomInt(length));
      inboxes.forEach((inbox, other) => {
        if (other !== r) {
          inbox.push(op);
          if (randomInt(10) === 0) {
            inbox.push(op); // 10% duplicated
          }
        }
      });
    } else if (inboxes[r].length > 0) {
      // 40%: deliver one random waiting message = reordering
      const [op] = inboxes[r].splice(randomInt(inboxes[r].length), 1);
      doc.apply(op);
    }
  }

  // The network heals: deliver everything left, in random order.
  inboxes.forEach((inbox, i) => {
    while (inbox.length > 0) {
      const [op] = inbox.splice(randomInt(inbox.length), 1);
      docs[i].apply(op);
    }
  });
  return docs;
}

describe("Rga chaos", () => {
  it("all replicas converge under reordering and duplication (500 seeds)", () => {
    for (let seed = 1; seed <= 500; seed++) {
      const docs = chaosRun(seed);
      const expected = docs[0].text();
      for (const doc of docs) {
        expect(doc.text(), `seed ${seed}`).toBe(expected);
        expect(doc.pendingCount, `seed ${seed}`).toBe(0);
      }
    }
  });
});
