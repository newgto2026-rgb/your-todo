import http from 'node:http';

const PORT = Number.parseInt(process.env.AI_TODO_PROXY_PORT ?? '8787', 10);
const OLLAMA_URL = process.env.OLLAMA_URL ?? 'http://127.0.0.1:11434';
const MODEL = process.env.OLLAMA_MODEL ?? 'qwen3:4b-instruct';

const responseSchema = {
  type: 'object',
  properties: {
    items: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          title: { type: 'string' },
          assigneeId: { type: ['string', 'null'] },
          dueDate: { type: ['string', 'null'] },
          dueTimeMinutes: { type: ['integer', 'null'], minimum: 0, maximum: 1439 },
          priority: { type: ['string', 'null'], enum: ['LOW', 'MEDIUM', 'HIGH', null] },
          needsReview: { type: 'boolean' },
          reviewReason: { type: ['string', 'null'] }
        },
        required: [
          'title',
          'assigneeId',
          'dueDate',
          'dueTimeMinutes',
          'priority',
          'needsReview',
          'reviewReason'
        ],
        additionalProperties: false
      }
    }
  },
  required: ['items'],
  additionalProperties: false
};

const systemPrompt = [
  'You extract Korean natural-language todo drafts as compact JSON.',
  'Return only JSON matching the schema.',
  'Never include markdown, commentary, or partial JSON. Complete the JSON object even for long task lists.',
  'Use only known people ids. Never invent an assignee id.',
  'If assignee is unclear or multiple assignees could apply, set assigneeId null and needsReview true.',
  'If a task names multiple people, create one item per person only when the text clearly says each person must do the same task; otherwise mark review.',
  'dueTimeMinutes is minutes from midnight: 오전 7시=420, 아침 8시=480, 오전 10시=600, 오후 3시=900, 오후 11시=1380.',
  'Missing time is allowed and should be null. Missing time alone does not require review.',
  'Korean date context carries forward across adjacent tasks until another explicit date appears.',
  "Example: '내일 7시에 A하고 10시에 B하고 오늘 C' means A and B are tomorrow, C is today.",
  'A later explicit date such as "오늘" must not rewrite earlier tasks that inherited "내일".',
  'If date is not specified and cannot be inferred, dueDate must be null.',
  'Today/tomorrow must be resolved from the request now and timezone.',
  'Keep titles short verb phrases without date, time, or assignee names.',
  'The title is the final todo title saved in the app.',
  'Formalize titles into concise Korean todo phrases. Remove casual commands, particles, assignee names, dates, and times from title.',
  'Do not keep endings such as "해놔", "해야 해", "부탁해", "가야 해", "다 해놔" in title.',
  'Normalize common household tasks: "빨래" or "빨래해놔" -> "빨래하기"; "청소" or "청소해놔" -> "청소하기"; "세탁기" or "세탁기 해놔" -> "세탁기 돌리기"; "설겆이" or "설거지" -> "설거지하기"; "아이 등원" -> "아이 등원시키기".',
  'Examples: "tee는 빨래 청소 세탁기 다해놔 오늘까지야" => titles "빨래하기", "청소하기", "세탁기 돌리기".',
  'Extract every actionable task candidate, including short fragments before hesitation words like "아 맞다", "..", "아니", or "음".',
  'If the user cancels or weakens only the time phrase, keep the task and set dueTimeMinutes null.',
  'Vague time phrases such as "저녁쯤", "출근 전에", or "회사 가기 전에" are not exact times; use null unless an exact hour is present.',
  'Use dateHints for weekday phrases. Pick the first future date with that weekday unless the user clearly means a later week.',
  'Deadline suffix "까지" sets the date but not a time unless an exact time is also written.',
  'When the text says "내일까지 해야할 일이" or similar list phrasing without clearly naming the responsible person, each listed task should have assigneeId null and needsReview true.',
  'Few-shot: "아 맞다 오늘 장보고.. 저녁쯤? 아니 시간은 모르겠고 내일 회사 가기 전에 우산 챙기기" has two tasks: 장보기 due today time null, 우산 챙기기 due tomorrow time null.',
  'Few-shot for now=2026-05-10 Sunday: "네오한테 다음주 월요일까지 발표자료 정리 부탁하고, 나는 금요일 밤 11시에 결제 확인" => 발표자료 정리 due 2026-05-11 time null, 결제 확인 due 2026-05-15 time 1380.',
  'Few-shot: "neo는 내일 아침 8시에 아이 등원하고 내일까지해야할일이 빨래돌리기, 청소기돌리기, 식기세척기돌리기, 화요일날 10시에는 부모참관수업있고 나는 내일 아침 8시에 수영가야해" => 아이 등원 assignee neo due tomorrow time 480; 빨래돌리기/청소기돌리기/식기세척기돌리기 assignee null due tomorrow time null review; 부모참관수업 assignee neo due Tuesday time 600; 수영가기 assignee self due tomorrow time 480.',
  'Use priority MEDIUM unless the user clearly says urgent/important/high or low priority.',
  'If the user applies a global priority such as "중요도는 다 높아", "전부 높아", "모두 중요해", "다 낮게", or "전체 보통", apply it to every extracted item.',
  'If needsReview is false, reviewReason must be null.'
].join(' ');

const server = http.createServer(async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  if (req.method === 'GET' && req.url === '/health') {
    writeJson(res, 200, { ok: true, model: MODEL });
    return;
  }
  if (req.method !== 'POST' || req.url !== '/ai/todo-drafts') {
    writeJson(res, 404, { error: 'not_found' });
    return;
  }

  try {
    const body = await readJson(req);
    const validation = validateRequest(body);
    if (validation) {
      writeJson(res, 400, { error: validation });
      return;
    }

    const result = await parseWithRetry(body);
    writeJson(res, 200, result);
  } catch (error) {
    writeJson(res, 500, {
      error: 'ai_todo_proxy_failed',
      message: error instanceof Error ? error.message : String(error)
    });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`AI todo proxy listening on http://0.0.0.0:${PORT}`);
  console.log(`Using Ollama ${OLLAMA_URL} model ${MODEL}`);
});

async function parseWithRetry(requestBody) {
  const first = await callOllama(requestBody);
  const normalized = normalizeResponse(first, requestBody);
  if (normalized.items.length > 0) return normalized;

  const retry = await callOllama({
    ...requestBody,
    text: `${requestBody.text}\n\nRetry strictly: return one complete JSON object matching the schema. Extract every todo draft or mark unclear drafts for review.`
  });
  return normalizeResponse(retry, requestBody);
}

async function callOllama(requestBody) {
  const today = formatDateInZone(requestBody.now, requestBody.timezone, 0);
  const tomorrow = formatDateInZone(requestBody.now, requestBody.timezone, 1);
  const dateHints = buildDateHints(requestBody.now, requestBody.timezone);
  const peopleLines = requestBody.people
    .map((person) => `${person.id}: ${person.name} aliases=${person.aliases.join(', ')} self=${person.isSelf}`)
    .join('\n');
  const userPrompt = [
    `now=${requestBody.now}`,
    `timezone=${requestBody.timezone}`,
    `locale=${requestBody.locale}`,
    `today=${today}`,
    `tomorrow=${tomorrow}`,
    `dateHints=${dateHints}`,
    `Date carry-over example for this request: "내일 오전 7시에 A하고 오전 10시에 B하고 오늘 C" => A=${tomorrow}, B=${tomorrow}, C=${today}.`,
    'knownPeople:',
    peopleLines,
    'text:',
    requestBody.text
  ].join('\n');

  const response = await fetch(`${OLLAMA_URL}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: MODEL,
      stream: false,
      options: {
        temperature: 0,
        top_p: 0.1,
        num_ctx: 4096,
        num_predict: 1536
      },
      format: responseSchema,
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: userPrompt }
      ]
    })
  });

  if (!response.ok) {
    throw new Error(`Ollama request failed: ${response.status}`);
  }
  const payload = await response.json();
  const content = payload?.message?.content;
  if (typeof content !== 'string') {
    throw new Error('Ollama response did not include message.content');
  }
  const parsed = tryParseJson(content);
  if (parsed) return parsed;
  return { items: [] };
}

function tryParseJson(content) {
  try {
    return JSON.parse(content);
  } catch {
    const start = content.indexOf('{');
    const end = content.lastIndexOf('}');
    if (start >= 0 && end > start) {
      try {
        return JSON.parse(content.slice(start, end + 1));
      } catch {
        return null;
      }
    }
    return null;
  }
}

function formatDateInZone(isoInstant, timezone, addDays) {
  const base = new Date(isoInstant);
  const shifted = new Date(base.getTime() + addDays * 24 * 60 * 60 * 1000);
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: timezone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(shifted);
}

function buildDateHints(isoInstant, timezone) {
  const weekdays = {
    Sun: '일요일',
    Mon: '월요일',
    Tue: '화요일',
    Wed: '수요일',
    Thu: '목요일',
    Fri: '금요일',
    Sat: '토요일'
  };
  const base = new Date(isoInstant);
  return Array.from({ length: 14 }, (_, index) => {
    const shifted = new Date(base.getTime() + index * 24 * 60 * 60 * 1000);
    const date = formatDateInZone(shifted.toISOString(), timezone, 0);
    const weekdayKey = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      weekday: 'short'
    }).format(shifted);
    const weekday = weekdays[weekdayKey] ?? shifted.toLocaleString('ko-KR', {
      timeZone: timezone,
      weekday: 'long'
    });
    return `${date}:${weekday}`;
  }).join(', ');
}

function normalizeResponse(response, requestBody) {
  const knownIds = new Set(requestBody.people.map((person) => person.id));
  const selfId = requestBody.people.find((person) => person.isSelf)?.id ?? null;
  const items = Array.isArray(response?.items) ? response.items : [];
  const globalPriority = inferGlobalPriority(requestBody.text);
  return {
    items: items
      .map((item) => normalizeItem(item, knownIds, selfId, requestBody, globalPriority))
      .filter((item) => item.title.length > 0),
    model: MODEL,
    fallbackUsed: false
  };
}

function normalizeItem(item, knownIds, selfId, requestBody, globalPriority) {
  const rawAssignee = typeof item.assigneeId === 'string' ? item.assigneeId : null;
  const reviewReason = typeof item.reviewReason === 'string' ? item.reviewReason : null;
  let assigneeId = normalizeAssignee(rawAssignee, knownIds, selfId, item.needsReview, reviewReason);
  const rawTime = Number.isInteger(item.dueTimeMinutes) ? item.dueTimeMinutes : null;
  const boundedTime = rawTime !== null && rawTime >= 0 && rawTime <= 1439 ? rawTime : null;
  const rawDate = typeof item.dueDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(item.dueDate)
    ? item.dueDate
    : null;
  const title = typeof item.title === 'string' ? item.title.trim() : '';
  const dueDate = normalizeDueDate(rawDate, requestBody, title);
  const dueTimeMinutes = normalizeDueTimeMinutes(boundedTime, requestBody, title);
  const priority = normalizePriority(item.priority, requestBody, title, globalPriority);
  const unclearListOwner = shouldReviewUnclearListOwner(requestBody.text, title);
  if (unclearListOwner) assigneeId = null;
  const unknownExplicitAssignee = rawAssignee !== null && assigneeId === null;
  const assigneeReview = assigneeId === null && shouldReviewMissingAssignee(item.needsReview, reviewReason);
  const needsReview = Boolean(item.needsReview && assigneeReview) || assigneeReview || unknownExplicitAssignee || unclearListOwner;

  return {
    title,
    assigneeId,
    dueDate,
    dueTimeMinutes,
    priority,
    needsReview,
    reviewReason: needsReview ? reviewReason ?? (unclearListOwner ? '담당자 확인이 필요합니다.' : '확인이 필요합니다.') : null
  };
}

function normalizeAssignee(rawAssignee, knownIds, selfId, needsReview, reviewReason) {
  if (rawAssignee && knownIds.has(rawAssignee)) return rawAssignee;
  if (rawAssignee !== null) return null;
  if (selfId && !shouldReviewMissingAssignee(needsReview, reviewReason)) return selfId;
  return null;
}

function shouldReviewMissingAssignee(needsReview, reviewReason) {
  if (!needsReview) return false;
  if (!reviewReason) return true;
  return /누가|담당|사람|여러|둘 다|모두|assignee|multiple/i.test(reviewReason);
}

function normalizeDueDate(rawDate, requestBody, title) {
  if (!rawDate) return null;
  const localContextDate = inferDateFromLocalContext(title, requestBody);
  if (localContextDate) return localContextDate;

  const text = requestBody.text;
  const weekdayCorrections = [
    ['다음주\\s*월요일', '월요일'],
    ['다음\\s*월요일', '월요일'],
    ['월요일', '월요일'],
    ['화요일', '화요일'],
    ['수요일', '수요일'],
    ['목요일', '목요일'],
    ['금요일', '금요일'],
    ['토요일', '토요일'],
    ['일요일', '일요일']
  ];
  for (const [pattern, weekday] of weekdayCorrections) {
    if (!new RegExp(pattern).test(text)) continue;
    if (weekdayForDate(rawDate, requestBody.timezone) !== weekday) continue;
    const firstFutureDate = firstFutureWeekdayDate(requestBody.now, requestBody.timezone, weekday);
    if (firstFutureDate && rawDate !== firstFutureDate) return firstFutureDate;
  }
  return rawDate;
}

function normalizeDueTimeMinutes(rawTime, requestBody, title) {
  if (rawTime === null) return null;
  if (rawTime >= 12 * 60) return rawTime;

  const hour = Math.floor(rawTime / 60);
  const shouldShiftToAfternoon =
    /하원|픽업|데리러/u.test(title) ||
    (/숙제/u.test(title) && hour >= 6);

  if (!shouldShiftToAfternoon) return rawTime;
  if (hasMorningMarkerNearTask(requestBody.text, title)) return rawTime;
  return rawTime + 12 * 60;
}

function hasMorningMarkerNearTask(text, title) {
  const compactText = compactKorean(text);
  const titleTokens = compactKorean(title).match(/[가-힣a-z0-9]{2,}/giu) ?? [];
  const taskTokens = [...titleTokens, '하원', '숙제', '픽업', '데리러']
    .filter((token, index, tokens) => tokens.indexOf(token) === index);

  for (const token of taskTokens) {
    const index = compactText.indexOf(token);
    if (index < 0) continue;
    const windowStart = Math.max(0, index - 8);
    const windowEnd = Math.min(compactText.length, index + token.length + 8);
    const context = compactText.slice(windowStart, windowEnd);
    if (/오전|아침|새벽/u.test(context)) return true;
  }
  return false;
}

function inferDateFromLocalContext(title, requestBody) {
  const keyword = titleKeyword(title);
  if (!keyword) return null;

  const compactText = compactKorean(requestBody.text);
  const index = compactText.indexOf(keyword);
  if (index < 0) return null;

  const markers = findDateMarkers(compactText, requestBody);
  const marker = markers.filter((candidate) => candidate.index <= index).at(-1);
  return marker?.date ?? null;
}

function titleKeyword(title) {
  const compactTitle = compactKorean(title)
    .replace(/(하기|가기|돌리기|준비하기)$/u, '')
    .trim();
  if (compactTitle.length >= 2) return compactTitle.slice(0, Math.min(compactTitle.length, 6));
  return null;
}

function compactKorean(value) {
  return String(value ?? '').replace(/\s+/g, '').toLowerCase();
}

function normalizePriority(rawPriority, requestBody, title, globalPriority) {
  if (globalPriority) return globalPriority;

  const priority = ['LOW', 'MEDIUM', 'HIGH'].includes(rawPriority) ? rawPriority : 'MEDIUM';
  const context = findTaskContext(requestBody.text, title);
  if (/급함|급해|긴급|중요|꼭|높|high|urgent/i.test(context)) return 'HIGH';
  if (/낮|천천히|나중에|low/i.test(context)) return 'LOW';
  if (/보통|중간|medium/i.test(context)) return 'MEDIUM';
  return priority;
}

function inferGlobalPriority(text) {
  const compactText = compactKorean(text);
  const globalPrefix = '(?:중요도(?:는|를|가)?|우선순위(?:는|를|가)?)';
  const allQuantifier = '(?:다|전부|전체|모두|몽땅)';

  if (new RegExp(`${globalPrefix}${allQuantifier}?(?:높|상|high|하이)|${allQuantifier}(?:높|중요|급함|급해|urgent)`, 'i').test(compactText)) {
    return 'HIGH';
  }
  if (new RegExp(`${globalPrefix}${allQuantifier}?(?:낮|하|low|로우)|${allQuantifier}(?:낮)`, 'i').test(compactText)) {
    return 'LOW';
  }
  if (new RegExp(`${globalPrefix}${allQuantifier}?(?:중간|보통|medium|미디엄)|${allQuantifier}(?:중간|보통)`, 'i').test(compactText)) {
    return 'MEDIUM';
  }
  return null;
}

function findTaskContext(text, title) {
  const compactText = compactKorean(text);
  const tokens = taskTokensForTitle(title);
  const windows = [];
  for (const token of tokens) {
    let startIndex = 0;
    while (startIndex < compactText.length) {
      const index = compactText.indexOf(token, startIndex);
      if (index < 0) break;
      const windowStart = Math.max(0, index - 10);
      const windowEnd = Math.min(compactText.length, index + token.length + 14);
      windows.push(compactText.slice(windowStart, windowEnd));
      startIndex = index + token.length;
    }
  }
  return windows.join(' ');
}

function taskTokensForTitle(title) {
  const compactTitle = compactKorean(title)
    .replace(/(하기|가기|돌리기|준비하기|시키기|갈아주기)$/u, '')
    .trim();
  const tokens = [];
  if (compactTitle.length >= 2) tokens.push(compactTitle);
  if (compactTitle.length >= 4) tokens.push(compactTitle.slice(0, 4));
  if (/(하원|픽업|데리러)/u.test(title)) tokens.push('하원', '픽업', '데리러');
  if (/숙제/u.test(title)) tokens.push('숙제');
  return tokens.filter((token, index) => token.length >= 2 && tokens.indexOf(token) === index);
}

function shouldReviewUnclearListOwner(text, title) {
  const compactText = compactKorean(text);
  const itemIndex = findTaskIndex(compactText, title);
  if (itemIndex < 0) return false;

  const listMarkers = ['내일까지해야할일이', '내일까지해야할일은', '해야할일이', '해야할일은', '할일이', '할일은'];
  const marker = listMarkers
    .map((token) => ({ token, index: compactText.lastIndexOf(token, itemIndex) }))
    .filter((candidate) => candidate.index >= 0)
    .sort((left, right) => right.index - left.index)[0];
  if (!marker) return false;

  const afterMarker = compactText.slice(marker.index + marker.token.length, itemIndex);
  if (/나는|내가|저는|제가|본인은|본인이|tee는|tee가|neo는|neo가|네오는|네오가/u.test(afterMarker)) return false;
  if (/오늘|내일|모레|월요일|화요일|수요일|목요일|금요일|토요일|일요일/u.test(afterMarker)) return false;
  return true;
}

function findTaskIndex(compactText, title) {
  const tokens = taskTokensForTitle(title);
  const indexes = tokens.map((token) => compactText.indexOf(token)).filter((index) => index >= 0);
  if (indexes.length === 0) return -1;
  return Math.min(...indexes);
}

function findDateMarkers(compactText, requestBody) {
  const markerSpecs = [
    ['내일까지', formatDateInZone(requestBody.now, requestBody.timezone, 1)],
    ['내일', formatDateInZone(requestBody.now, requestBody.timezone, 1)],
    ['오늘', formatDateInZone(requestBody.now, requestBody.timezone, 0)],
    ['모레', formatDateInZone(requestBody.now, requestBody.timezone, 2)],
    ['월요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '월요일')],
    ['화요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '화요일')],
    ['수요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '수요일')],
    ['목요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '목요일')],
    ['금요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '금요일')],
    ['토요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '토요일')],
    ['일요일', firstFutureWeekdayDate(requestBody.now, requestBody.timezone, '일요일')]
  ];
  const markers = [];
  for (const [token, date] of markerSpecs) {
    if (!date) continue;
    let startIndex = 0;
    while (startIndex < compactText.length) {
      const index = compactText.indexOf(token, startIndex);
      if (index < 0) break;
      markers.push({ index, date });
      startIndex = index + token.length;
    }
  }
  return markers.sort((left, right) => left.index - right.index);
}

function firstFutureWeekdayDate(isoInstant, timezone, weekday) {
  const base = new Date(isoInstant);
  for (let offset = 1; offset <= 14; offset += 1) {
    const shifted = new Date(base.getTime() + offset * 24 * 60 * 60 * 1000);
    const date = formatDateInZone(shifted.toISOString(), timezone, 0);
    if (weekdayForDate(date, timezone) === weekday) return date;
  }
  return null;
}

function weekdayForDate(date, timezone) {
  const weekdayMap = {
    Sunday: '일요일',
    Monday: '월요일',
    Tuesday: '화요일',
    Wednesday: '수요일',
    Thursday: '목요일',
    Friday: '금요일',
    Saturday: '토요일'
  };
  const englishWeekday = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    weekday: 'long'
  }).format(new Date(`${date}T12:00:00Z`));
  return weekdayMap[englishWeekday] ?? null;
}

function validateRequest(body) {
  if (!body || typeof body !== 'object') return 'invalid_json';
  if (typeof body.text !== 'string' || body.text.trim().length === 0) return 'text_required';
  if (body.text.length > 2000) return 'text_too_long';
  if (typeof body.now !== 'string') return 'now_required';
  if (typeof body.timezone !== 'string') return 'timezone_required';
  if (!Array.isArray(body.people) || body.people.length === 0) return 'people_required';
  if (!body.people.some((person) => person?.isSelf === true)) return 'self_required';
  return null;
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => {
      data += chunk;
      if (data.length > 32_768) {
        reject(new Error('request_too_large'));
        req.destroy();
      }
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(data));
      } catch (error) {
        reject(error);
      }
    });
    req.on('error', reject);
  });
}

function writeJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}
