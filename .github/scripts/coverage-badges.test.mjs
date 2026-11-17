import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { badge, badgeColor, jacocoLinePercent, vitestLinePercent } from './coverage-badges.mjs';

const SCRIPT = fileURLToPath(new URL('./coverage-badges.mjs', import.meta.url));

const CSV = [
  'GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,BRANCH_COVERED,LINE_MISSED,LINE_COVERED,COMPLEXITY_MISSED,COMPLEXITY_COVERED,METHOD_MISSED,METHOD_COVERED',
  'slotmind,com.slotmind.api,A,1,9,0,0,2,18,0,1,0,1',
  'slotmind,com.slotmind.ai,B,1,9,0,0,3,7,0,1,0,1',
].join('\n');

test('colours follow the bands, best first, and fall through to red', () => {
  assert.equal(badgeColor(100), 'brightgreen');
  assert.equal(badgeColor(90), 'brightgreen');
  assert.equal(badgeColor(89), 'green');
  assert.equal(badgeColor(75), 'yellowgreen');
  assert.equal(badgeColor(65), 'yellow');
  assert.equal(badgeColor(55), 'orange');
  assert.equal(badgeColor(10), 'red');
});

test('JaCoCo line coverage sums covered over covered plus missed across classes', () => {
  assert.equal(jacocoLinePercent(CSV), (100 * 25) / 30);
});

test('a report with no lines is 0%, not a division by zero', () => {
  assert.equal(jacocoLinePercent(CSV.split('\n')[0]), 0);
});

test('reads the total line percentage from a Vitest summary', () => {
  assert.equal(vitestLinePercent('{"total":{"lines":{"pct":96.5}}}'), 96.5);
});

test('a badge is a shields endpoint document with the colour of the rounded value', () => {
  assert.deepEqual(badge('backend coverage', 89.6), {
    schemaVersion: 1,
    label: 'backend coverage',
    message: '90%',
    color: 'brightgreen',
  });
});

test('the command line writes one JSON file per badge', () => {
  const dir = mkdtempSync(join(tmpdir(), 'badges-'));
  writeFileSync(join(dir, 'jacoco.csv'), CSV);
  writeFileSync(join(dir, 'summary.json'), '{"total":{"lines":{"pct":100}}}');

  const run = spawnSync(
    process.execPath,
    [SCRIPT, join(dir, 'jacoco.csv'), join(dir, 'summary.json'), join(dir, 'out')],
    { encoding: 'utf8' },
  );

  assert.equal(run.status, 0, run.stderr);
  assert.equal(JSON.parse(readFileSync(join(dir, 'out', 'backend-coverage.json'), 'utf8')).message, '83%');
  assert.equal(JSON.parse(readFileSync(join(dir, 'out', 'frontend-coverage.json'), 'utf8')).color, 'brightgreen');
});

test('the command line fails with a usage message when arguments are missing', () => {
  const run = spawnSync(process.execPath, [SCRIPT], { encoding: 'utf8' });

  assert.notEqual(run.status, 0);
  assert.match(run.stderr, /usage/);
});
