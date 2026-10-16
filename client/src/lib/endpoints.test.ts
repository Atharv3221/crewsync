import { describe, expect, it } from "vitest";
import { apiUrl, socketBase } from "./endpoints";

const PAGE = { protocol: "https:", host: "app.example.com" };

describe("apiUrl", () => {
  it("stays on the page's origin without a configured base", () => {
    expect(apiUrl("/api/me", "")).toBe("/api/me");
  });

  it("prefixes the configured API base", () => {
    expect(apiUrl("/api/me", "https://api.example.com")).toBe(
      "https://api.example.com/api/me",
    );
  });
});

describe("socketBase", () => {
  it("uses the page's host and matches its security without a base", () => {
    expect(socketBase("", PAGE)).toBe("wss://app.example.com");
    expect(socketBase("", { protocol: "http:", host: "localhost:5173" })).toBe(
      "ws://localhost:5173",
    );
  });

  it("derives wss from an https API base, ignoring the page", () => {
    expect(socketBase("https://api.example.com", PAGE)).toBe(
      "wss://api.example.com",
    );
  });

  it("derives ws from an http API base and keeps its port and path", () => {
    expect(socketBase("http://localhost:8080/crewsync", PAGE)).toBe(
      "ws://localhost:8080/crewsync",
    );
  });
});
