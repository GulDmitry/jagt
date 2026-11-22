import c8 from "./.c8rc.json" with { type: "json" };

export default {
  mutate: c8.include,
  testRunner: "command",
  commandRunner: { command: 'node --test "test/**/*.test.js"' },
  coverageAnalysis: "off",
  reporters: ["clear-text"],
  tempDirName: ".stryker-tmp",
  cleanTempDir: true,
  thresholds: { high: 80, low: 60, break: 95 },
};
