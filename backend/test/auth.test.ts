import { describe, expect, it } from "vitest";

import { normalizeUsername, validatePassword } from "../src/auth.js";

describe("username normalization", () => {
  it("trims and case-normalizes a valid username", () => {
    expect(normalizeUsername("  Easy.Macros_01 ")).toBe("easy.macros_01");
  });

  it("rejects usernames outside the published character policy", () => {
    expect(() => normalizeUsername("ab")).toThrow("username must be");
    expect(() => normalizeUsername("easy macros")).toThrow("username must be");
  });
});

describe("password policy", () => {
  it("requires at least twelve characters", () => {
    expect(() => validatePassword("too-short")).toThrow("password must be");
    expect(() => validatePassword("a-safe-password")).not.toThrow();
  });
});
