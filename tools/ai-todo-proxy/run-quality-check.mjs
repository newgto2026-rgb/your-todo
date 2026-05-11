import fs from 'node:fs/promises';

const proxyUrl = process.env.AI_TODO_PROXY_URL ?? 'http://127.0.0.1:8787';
const cases = JSON.parse(
  await fs.readFile(new URL('./quality-cases.json', import.meta.url), 'utf8')
);

const baseRequest = {
  now: '2026-05-10T12:00:00+09:00',
  timezone: 'Asia/Seoul',
  locale: 'ko-KR',
  people: [
    { id: 'self', name: '나', aliases: ['나', '내', '본인', '저', '제', 'tee'], isSelf: true },
    { id: 'user_neo', name: 'Neo', aliases: ['neo', '네오'], isSelf: false }
  ]
};

let passed = 0;

for (const testCase of cases) {
  const response = await fetch(`${proxyUrl}/ai/todo-drafts`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ...baseRequest, text: testCase.text })
  });
  const body = await response.json();
  const result = evaluate(testCase, body);
  if (result.ok) passed += 1;

  console.log(`\n[${result.ok ? 'PASS' : 'FAIL'}] ${testCase.name}`);
  if (!result.ok) console.log(`reason: ${result.reason}`);
  console.log(JSON.stringify(body, null, 2));
}

console.log(`\n${passed}/${cases.length} quality cases passed`);
if (passed !== cases.length) process.exitCode = 1;

function evaluate(testCase, body) {
  if (!Array.isArray(body?.items)) return { ok: false, reason: 'items missing' };
  if (Number.isInteger(testCase.expectMinItems) && body.items.length < testCase.expectMinItems) {
    return { ok: false, reason: `expected at least ${testCase.expectMinItems} items` };
  }
  if (testCase.expectAllPriority) {
    if (body.items.length === 0) return { ok: false, reason: 'expected at least one item' };
    const mismatched = body.items.find((item) => item.priority !== testCase.expectAllPriority);
    if (mismatched) {
      return {
        ok: false,
        reason: `expected all priority ${testCase.expectAllPriority}, found ${mismatched.priority ?? 'null'} in "${mismatched.title ?? ''}"`
      };
    }
  }
  if (testCase.expectAnyReview) {
    return body.items.some((item) => item.needsReview)
      ? { ok: true }
      : { ok: false, reason: 'expected at least one review-required item' };
  }
  const expectations = testCase.expect ?? [];
  if (body.items.length < expectations.length) {
    return { ok: false, reason: `expected at least ${expectations.length} items` };
  }
  for (const expected of expectations) {
    const found = body.items.some((item) => matchesExpectation(item, expected));
    if (!found) {
      return { ok: false, reason: `missing expected ${JSON.stringify(expected)}` };
    }
  }
  return { ok: true };
}

function matchesExpectation(item, expected) {
  return Object.entries(expected).every(([key, value]) => {
    if (key === 'titleIncludes') {
      return typeof item.title === 'string' && item.title.includes(value);
    }
    return item[key] === value;
  });
}
