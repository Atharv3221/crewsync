import { mkdirSync, writeFileSync } from "node:fs";
import { describe, expect, it } from "vitest";
import { Rga, type Op } from "./index";

/**
 * Convergence fuzz: random replicas edit concurrently over a simulated network that delays,
 * reorders and duplicates every message. All replicas must end with identical text.
 *
 * `npm test` runs DEFAULT_SEEDS seeds. `FUZZ_SEEDS=10000 npm run bench:fuzz` runs more and writes
 * bench/results/fuzz.json. Replay one failing seed with `FUZZ_SEED=<seed>`.
 */

const DEFAULT_SEEDS = 200;
const MIN_REPLICAS = 2;
const MAX_REPLICAS = 8;
const MIN_LOCAL_OPS = 50;
const MAX_LOCAL_OPS = 300;
const DUPLICATE_CHANCE = 0.1;
const MAX_DELAY_TICKS = 40;
/** Rarely a message is held back much longer, like a client on a flaky link. */
const LONG_DELAY_CHANCE = 0.05;
const LONG_DELAY_TICKS = 400;
const ALPHABET = "abcdefghijklmnopqrstuvwxyz \n\n.";

/** mulberry32: small, fast and good enough to drive a fuzzer reproducibly. */
function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 2 ** 32;
  };
}

interface InFlight {
  readonly deliverAt: number;
  readonly to: number;
  readonly op: Op;
}

interface RunResult {
  readonly seed: number;
  readonly replicas: number;
  readonly localOps: number;
  readonly deliveries: number;
  readonly duplicates: number;
  readonly texts: readonly string[];
  readonly pending: readonly number[];
}

function fuzzRun(seed: number): RunResult {
  const random = seededRandom(seed);
  const randomInt = (n: number) => Math.floor(random() * n);

  const replicaCount =
    MIN_REPLICAS + randomInt(MAX_REPLICAS - MIN_REPLICAS + 1);
  const targetOps =
    MIN_LOCAL_OPS + randomInt(MAX_LOCAL_OPS - MIN_LOCAL_OPS + 1);
  const docs = Array.from(
    { length: replicaCount },
    (_, i) => new Rga(`site-${i}`),
  );
  let network: InFlight[] = [];
  let tick = 0;
  let localOps = 0;
  let deliveries = 0;
  let duplicates = 0;

  const delay = () =>
    random() < LONG_DELAY_CHANCE
      ? randomInt(LONG_DELAY_TICKS)
      : randomInt(MAX_DELAY_TICKS);

  const broadcast = (from: number, op: Op) => {
    localOps++;
    for (let to = 0; to < replicaCount; to++) {
      if (to !== from) {
        network.push({ deliverAt: tick + delay(), to, op });
        if (random() < DUPLICATE_CHANCE) {
          network.push({ deliverAt: tick + delay(), to, op });
          duplicates++;
        }
      }
    }
  };

  const localEdit = (r: number) => {
    const doc = docs[r];
    const length = doc.length;
    if (length === 0 || random() < 0.65) {
      // a burst of typing, sometimes a pasted run
      const run = random() < 0.8 ? 1 : 1 + randomInt(8);
      const pos = randomInt(length + 1);
      for (let i = 0; i < run; i++) {
        broadcast(
          r,
          doc.localInsert(pos + i, ALPHABET[randomInt(ALPHABET.length)]),
        );
      }
    } else {
      // backspace, sometimes a selected range
      const run = Math.min(length, random() < 0.8 ? 1 : 1 + randomInt(6));
      const pos = randomInt(length - run + 1);
      for (let i = 0; i < run; i++) {
        broadcast(r, doc.localDelete(pos));
      }
    }
  };

  /** Delivers every due message, in random order among the due ones. */
  const deliverDue = () => {
    const due = network.filter((m) => m.deliverAt <= tick);
    if (due.length > 0) {
      network = network.filter((m) => m.deliverAt > tick);
      shuffle(due, randomInt);
      for (const message of due) {
        docs[message.to].apply(message.op);
        deliveries++;
      }
    }
  };

  while (localOps < targetOps) {
    tick++;
    if (random() < 0.5) {
      localEdit(randomInt(replicaCount));
    }
    deliverDue();
  }

  // The network heals: everything still in flight arrives, shuffled.
  shuffle(network, randomInt);
  for (const message of network) {
    docs[message.to].apply(message.op);
    deliveries++;
  }

  return {
    seed,
    replicas: replicaCount,
    localOps,
    deliveries,
    duplicates,
    texts: docs.map((doc) => doc.text()),
    pending: docs.map((doc) => doc.pendingCount),
  };
}

function shuffle<T>(items: T[], randomInt: (n: number) => number): void {
  for (let i = items.length - 1; i > 0; i--) {
    const j = randomInt(i + 1);
    [items[i], items[j]] = [items[j], items[i]];
  }
}

function converged(result: RunResult): boolean {
  return (
    result.texts.every((text) => text === result.texts[0]) &&
    result.pending.every((count) => count === 0)
  );
}

function positiveInt(raw: string | undefined): number | null {
  const value = raw === undefined ? NaN : Number(raw);
  return Number.isInteger(value) && value > 0 ? value : null;
}

const singleSeed = positiveInt(process.env.FUZZ_SEED);
const requestedSeeds = positiveInt(process.env.FUZZ_SEEDS);
const seedCount = requestedSeeds ?? DEFAULT_SEEDS;
const seeds =
  singleSeed !== null
    ? [singleSeed]
    : Array.from({ length: seedCount }, (_, i) => i + 1);

describe("Rga convergence fuzz", () => {
  it(
    `converges on ${seeds.length} seed(s) with 2–8 replicas, shuffled, duplicated and delayed delivery`,
    () => {
      const started = performance.now();
      const failures: RunResult[] = [];
      let totalLocalOps = 0;
      let totalDeliveries = 0;
      let totalDuplicates = 0;
      let totalReplicas = 0;
      const replicaHistogram: Record<number, number> = {};

      for (const seed of seeds) {
        const result = fuzzRun(seed);
        totalLocalOps += result.localOps;
        totalDeliveries += result.deliveries;
        totalDuplicates += result.duplicates;
        totalReplicas += result.replicas;
        replicaHistogram[result.replicas] =
          (replicaHistogram[result.replicas] ?? 0) + 1;
        if (!converged(result)) {
          failures.push(result);
          console.error(
            `convergence fuzz: seed ${seed} diverged (${result.replicas} replicas). ` +
              `Replay with FUZZ_SEED=${seed}`,
          );
        }
      }
      const durationMs = performance.now() - started;

      if (requestedSeeds !== null && singleSeed === null) {
        const dir = new URL("../../../bench/results/", import.meta.url);
        mkdirSync(dir, { recursive: true });
        writeFileSync(
          new URL("fuzz.json", dir),
          JSON.stringify(
            {
              benchmark: "convergence-fuzz",
              date: new Date().toISOString(),
              node: process.version,
              seeds: seeds.length,
              seedRange: [seeds[0], seeds[seeds.length - 1]],
              replicas: {
                min: MIN_REPLICAS,
                max: MAX_REPLICAS,
                total: totalReplicas,
                histogram: replicaHistogram,
              },
              localOpsPerSeed: { min: MIN_LOCAL_OPS, max: MAX_LOCAL_OPS },
              totalOps: totalLocalOps,
              totalDeliveries,
              totalDuplicates,
              network: {
                duplicateChance: DUPLICATE_CHANCE,
                maxDelayTicks: MAX_DELAY_TICKS,
                longDelayChance: LONG_DELAY_CHANCE,
                longDelayTicks: LONG_DELAY_TICKS,
              },
              divergences: failures.length,
              divergentSeeds: failures.map((f) => f.seed),
              durationMs: Math.round(durationMs),
            },
            null,
            2,
          ) + "\n",
        );
      }

      expect(
        failures.map((f) => f.seed),
        "seeds that diverged (replay with FUZZ_SEED=<seed>)",
      ).toEqual([]);
    },
    // the default run takes well under a second; FUZZ_SEEDS=10000 needs much longer
    Math.max(30_000, seeds.length * 50),
  );
});
