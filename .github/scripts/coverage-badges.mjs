// Turns the two coverage reports into shields.io "endpoint" JSON files (one per badge).
//   node coverage-badges.mjs <backend jacoco.csv> <frontend coverage-summary.json> <out dir>
// No dependencies, so the CI job needs nothing beyond Node.
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

// Lowest percentage that earns each colour, best first.
const BANDS = [
  [90, 'brightgreen'],
  [80, 'green'],
  [70, 'yellowgreen'],
  [60, 'yellow'],
  [50, 'orange'],
];

export const badgeColor = (percent) => (BANDS.find(([min]) => percent >= min) ?? [0, 'red'])[1];

/** Line coverage of a JaCoCo jacoco.csv: covered lines over covered plus missed, all classes. */
export function jacocoLinePercent(csvText) {
  const [header, ...rows] = csvText.trim().split(/\r?\n/);
  const columns = header.split(',');
  const covered = columns.indexOf('LINE_COVERED');
  const missed = columns.indexOf('LINE_MISSED');
  let coveredLines = 0;
  let missedLines = 0;
  for (const row of rows) {
    const cells = row.split(',');
    coveredLines += Number(cells[covered]);
    missedLines += Number(cells[missed]);
  }
  const total = coveredLines + missedLines;
  return total === 0 ? 0 : (100 * coveredLines) / total;
}

/** Line coverage of a Vitest/Istanbul coverage-summary.json. */
export const vitestLinePercent = (jsonText) => JSON.parse(jsonText).total.lines.pct;

export function badge(label, percent) {
  const whole = Math.round(percent);
  return { schemaVersion: 1, label, message: `${whole}%`, color: badgeColor(whole) };
}

function main([backendCsv, frontendJson, outDir]) {
  if (!backendCsv || !frontendJson || !outDir) {
    throw new Error('usage: coverage-badges.mjs <jacoco.csv> <coverage-summary.json> <out dir>');
  }
  const badges = {
    'backend-coverage.json': badge('backend coverage', jacocoLinePercent(readFileSync(backendCsv, 'utf8'))),
    'frontend-coverage.json': badge('frontend coverage', vitestLinePercent(readFileSync(frontendJson, 'utf8'))),
  };
  mkdirSync(outDir, { recursive: true });
  for (const [file, content] of Object.entries(badges)) {
    writeFileSync(join(outDir, file), JSON.stringify(content) + '\n');
    console.log(`${file}: ${content.message} (${content.color})`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2));
}
