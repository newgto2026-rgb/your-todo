# PRD - Person Todo Visibility MVP

## 1. Document Info
- Document: Product Requirements - Person Todo Visibility MVP
- Product: `YourTodo` Android app + `yourtodo-server`
- Android branch: `codex/todo-visibility-share-prd-trd`
- Date: 2026-05-17
- Status: Planner/Designer/QA/Architect debate reflected, pre-implementation contract

## 2. One Line
`친구 할일` is a read-only way to see a friend's Todo flow when that friend turns on `내 할일 보여주기`.

The friend's Todo never enters my Todo space. It is not assigned to me, not editable, not completable, and not counted as my work. It appears only as contextual information in Friends and Calendar when there is something useful to show.

## 3. Final Decisions
- MVP is person-level visibility, not item-level sharing.
- User-facing viewer label: `친구 할일`.
- Owner-facing switch label: `내 할일 보여주기`.
- Internal concept: `PersonVisibility`.
- Core permission model: `VisibilityGrant`.
- Item projection model: `ObservedTodo`.
- No new bottom tab.
- No deep "people view" flow for MVP.
- Friends tab is the home. Active friend rows handle lightweight relationship settings directly in the list.
- Viewer sees the friend list first. Each active friend row can expand to show that friend's visible todos.
- If there are no friend todos to show, the app does not force a separated empty area.
- Calendar does not use a complex source filter in MVP. It conditionally separates `내 할일` and `친구 할일` in the visible agenda area, while preserving an expandable calendar body.
- Widget does not show `친구 할일` in MVP.
- Public/private item policy is out of scope. MVP only decides whether this friend can see my Todo flow or not.
- Revoke immediately blocks server read paths. Client cache is purged on foreground, sync, or purge event.

## 4. User Problem
Users sometimes want a friend, family member, or teammate to know what they are working on without turning those tasks into shared work. This can reduce repeated check-in messages and help people coordinate around plans.

The risk is semantic confusion. If the feature looks like `공유받은 할일`, `받은 일`, `할당`, or `공동 할일`, users may think the friend's tasks became their own tasks. YourTodo must keep `내 할일` as the primary execution space. Friend todos are contextual read-only information.

## 5. Goals
- Owner can turn `내 할일 보여주기` on/off for a specific friend from Friends.
- Viewer can expand an active friend row directly inside Friends when that friend has displayable `ObservedTodo`.
- Viewer does not need to enter a separate people directory before seeing friend todos.
- `친구 할일` is visually and behaviorally separate from my Todo.
- Calendar can show friend todos, but only as a separated contextual section when friend todos exist in the current date/window.
- Empty friend Todo expansion areas are not shown by default.
- ObservedTodo is excluded from Todo tab, Today, Completed, Widget, productivity metrics, assignment flows, and my notifications.
- Revoke stops server access immediately.
- Online clients purge observed data quickly; offline clients purge on reconnect.

## 6. Non-Goals
- Todo-level public/private controls.
- Todo-level share target selection.
- Per-viewer item exceptions.
- A viewer acceptance flow such as `보기 시작`.
- A separate people directory for observed todos.
- Viewer editing, completing, deleting, rescheduling, or setting reminders on ObservedTodo.
- Copying ObservedTodo into my Todo automatically.
- Read receipts, seen status, or last viewed time.
- Notifications for a friend's Todo creation, update, completion, or reminder.
- Showing friend todos in the existing Widget in MVP.
- Reusing assignment/direct assignment semantics.
- Adding a new bottom navigation tab.

## 7. Terms
| Term | Meaning |
|---|---|
| Owner | User who owns the original Todo |
| Viewer | Friend who can see the Owner's Todo flow |
| PersonVisibility | Internal feature concept for person-level Todo visibility |
| VisibilityGrant | Directional permission from Owner to Viewer |
| ObservedTodo | Read-only projection of Owner's Todo shown to Viewer |
| 친구 할일 | User-facing label for ObservedTodo inside a friend's expanded row or Calendar section |

## 8. Product Principles
### 8.1 Person-level, not item-level
MVP asks `이 친구에게 내 할일을 보여줄까?`, not `이 할일을 공유할까?`.

### 8.2 My Todo remains pure
ObservedTodo is never merged into my Todo, counts, completion, Today, Completed, Widget, or assignment surfaces.

### 8.3 Direct in Friends
The main owner action is a compact switch on the active friend row: `내 할일 보여주기`.

The viewer's main surface is the existing friend list. The user expands a specific active friend row to see that friend's visible todos.

### 8.4 Conditional separation
The UI separates friend todos only when friend todos exist in the current context.

If there are no displayable friend todos:
- Friends keeps the normal friend list as the main experience, without a separate empty `친구 할일` block.
- Calendar keeps the normal calendar/agenda without an empty `친구 할일` section.
- Widget stays as my Todo only.

### 8.5 Calendar remains a real calendar
Calendar has limited space, but friend todos must not shrink the core calendar into a permanent one-week view. The calendar body should support expanded/collapsed states independently from agenda sections.

Recommended calendar states:
- Expanded calendar: full month grid, matching the existing Calendar mental model.
- Compact calendar: selected week strip for users who want more agenda space.
- Agenda: `내 할일` and `친구 할일` sections below the calendar body, each shown only when it has items.

Friend todos should not force Calendar into compact mode. The user controls calendar expansion.

### 8.6 Widget is conservative
Widget space is smaller and more privacy-sensitive. MVP excludes friend todos from Widget. A future version may introduce a separate `친구 할일` widget, but the existing my Todo widget remains owned-only.

### 8.7 Read-only by construction
ObservedTodo has no checkbox, edit, delete, complete, reminder, or assignment actions.

### 8.8 Outgoing visibility is not a Todo list
The product must distinguish two different concepts:

| Concept | User meaning | UI shape |
|---|---|---|
| `내 할일 보여주기` | I am allowing this friend to see my Todo flow | Active friend row relationship setting with switch/status |
| `친구 할일` | I can see a friend's Todo flow | Expanded area inside that friend's active row |

Outgoing visibility should never be presented as `공유한 할일` item rows. It is a relationship permission state, not a task surface. This prevents users from thinking they selected or published individual Todos.

Incoming friend todos may look close to Todo rows for scanability, but they must differ through structure and affordance:
- they live inside the friend's expanded row, not above the friend list or under `내 할일`;
- they inherit owner context from the expanded friend row and still show owner identity when needed;
- they have no checkbox or swipe/row actions;
- they use lighter visual weight than owned Todo rows;
- they never use assignment/share language.

### 8.9 Friend request acceptance has priority
Existing friend request rows already have `수락` and `거절` actions. `내 할일 보여주기` must not appear on pending request rows because it would compete with friendship acceptance.

Visibility controls appear only after friendship is active:
- Pending incoming request: show requester identity + `수락`/`거절`.
- Pending outgoing request: show receiver identity + pending state.
- Active friend: show friendship row with lightweight relationship settings.

### 8.10 Existing Todo share dialog remains untouched
YourTodo already has a friend selection / Todo sharing dialog for sending assigned/shared Todos. This MVP should not reuse, replace, or redesign that dialog.

List-level responsibilities:
- `할일 보내기`: opens the existing Todo share/assignment dialog.
- `자동수락`: controls whether this friend's sent Todos enter my list automatically.
- `내 할일 보여주기`: controls whether this friend can read my Todo flow.

The row must make direction explicit:
- `자동수락` is about **받는 할일** and can affect my Todo list.
- `내 할일 보여주기` is about **보여주는 할일** and does not add anything to the friend's Todo list.

## 9. User Flows
### 9.1 Owner turns visibility on
1. User opens Friends.
2. User finds an active friend row.
3. User turns on the compact `내 할일 보여주기` switch in that row.
4. A lightweight confirmation appears only when needed for first use or risk acknowledgement.
5. Server creates or reactivates the `VisibilityGrant`.
6. Friend can see displayable active Todo projections immediately.

Recommended copy:
- Switch: `내 할일 보여주기`
- Status on: `{nickname}이 내 할일을 볼 수 있어요`
- First-use helper: `친구 할일로만 보이며, 상대의 내 할일에는 추가되지 않아요.`

Avoid:
- `공유`
- `초대`
- `받은 할일`
- `공동 할일`
- `할당`
- `공개/비공개`

### 9.2 Viewer expands a friend row to see that friend's todos
1. Viewer opens Friends.
2. Friends shows the friend list before any friend Todo rows.
3. If an active friend has displayable todos, that friend row shows a compact `친구 할일` affordance such as `할일 3개`.
4. Expanding the friend row reveals all displayable todos from that friend inline.
5. Items can be grouped by due state such as `오늘`, `예정`, `날짜 없음`.
6. Each row omits owned-Todo controls.
7. Tapping a row opens a read-only detail sheet.

Default behavior:
- No visible friend todos for a friend: do not show an expand affordance for that friend.
- Some friend todos: show the affordance on that friend's row.
- Many friend todos for one friend: expanded state shows all displayable items. Do not cap the list to a preview count.
- Friend rows expand independently. Expanding `민지` should not require showing or hiding `준호`'s todos.

Recommended copy:
- Row affordance examples: `친구 할일 3개`, `오늘 1개`, `펼치기`
- Helper: `내 할일에는 추가되지 않아요`
- Empty detail status, only when user explicitly enters the surface: `지금 볼 수 있는 친구 할일이 없어요`

### 9.3 Calendar shows conditional sections
1. Calendar loads owned and observed items for the selected window.
2. Calendar body can be expanded as a month grid or collapsed as a selected-week strip.
3. If only owned items exist, Calendar shows the normal owned agenda.
4. If owned and observed items both exist, agenda separates them:

```text
내 할일
- 병원 예약
- 장보기

친구 할일
- 민지 · 발표 자료 정리
- 준호 · 항공권 확인
```

5. If only observed items exist, Calendar may show only `친구 할일`.
6. If no observed items exist, no `친구 할일` header appears.
7. If `친구 할일` would crowd the agenda, the section may start collapsed with count/nearest due summary and expand inline.

Calendar row rules:
- Owned rows keep checkbox/action affordances.
- Friend rows have no checkbox.
- Friend rows show owner name/avatar and have no checkbox/action controls.
- Friend rows do not need a persistent `보기만` badge.
- Month grid can use a distinct marker shape for friend todos, but the agenda section separation is the primary distinction.
- Calendar body expansion and `친구 할일` section expansion are independent states.

### 9.4 Owner turns visibility off
1. Owner turns off `내 할일 보여주기` for a friend.
2. Server revokes the `VisibilityGrant`.
3. Server read path blocks immediately after revoke commit.
4. Viewer app removes observed rows from Friends and Calendar on purge event, foreground, or next sync.

Recommended copy:
- Dialog title: `내 할일 보여주기를 끌까요?`
- Description: `서버 접근은 즉시 차단됩니다. 오프라인 기기에 남은 내용은 다시 연결되면 제거됩니다.`
- CTA: `끄기`

### 9.5 Widget policy
MVP Widget shows only my Todo.

Reasoning:
- Widget has too little room to explain source clearly.
- Users may act quickly from Widget and mistake a friend's Todo for their own.
- Widget may be visible on launcher/lock-like surfaces where friend Todo privacy is more sensitive.
- Existing widget mental model is `내 할일`.

Future option:
- A separate opt-in `친구 할일` widget can be considered later.
- It must have no checkboxes or complete actions.
- It should show owner chips on every row.
- It should open Friends > `친구 할일`, not Todo detail in my Todo.

## 10. Information Architecture
### 10.1 Bottom Navigation
No new bottom tab.

```text
전체
오늘
완료
캘린더
친구
```

### 10.2 Friends
Friends remains the main relationship surface.

Owner controls:
- Pending request rows keep only friendship request actions such as `수락` and `거절`.
- Active friend rows handle `자동수락` and `내 할일 보여주기` as two compact relationship-setting rows/chips.
- `할일 보내기` continues to open the existing Todo share/assignment dialog.
- The new visibility MVP must not change the existing Todo share/assignment dialog.
- Owner-side status is shown on the friend/person row, not as a list of shared Todo rows.

Viewer surface:
- The friend list appears before any friend Todo rows.
- `친구 할일` appears inside each active friend's expandable row, only when that friend has displayable observed items or when the user explicitly opens that friend context.
- The first interaction is expand/collapse on a friend row, not navigation into a person directory and not opening a global friend Todo section.
- Viewer-side rows are Todo-like for readability, but source identity and missing actions must make them feel observational.

### 10.3 Calendar
No complex source filter in MVP.

Calendar agenda composes sections from available data:
- Calendar body has expanded month and compact week states.
- `내 할일` if owned items exist.
- `친구 할일` if observed items exist.
- No empty friend section.
- `친구 할일` can be collapsed by default when Calendar space is tight.
- Calendar body does not become permanently week-only because of friend todos.

### 10.4 Widget
Existing Widget remains owned-only in MVP.

## 11. Exposure Scope
MVP does not evaluate item-level public/private policy. If `VisibilityGrant.status == ACTIVE`, server provides the allowed projection of Owner's active personal Todos.

Included:
- Owner-owned personal Todo.
- Active, non-deleted, non-archived Todo.
- Today/upcoming Todo.
- No-date Todo in the matching friend's expanded row.
- Date/occurrence Todo in Calendar.
- Recurrence occurrence projection.

Excluded:
- Deleted/archived Todo.
- Assignment/direct assignment/Todo received from others.
- Full historical archive.
- Reminder settings.
- Recurrence rule source details.
- Read receipt or viewer activity.

## 12. Notification Policy
Allowed:
- Visibility turned on/off state update inside the app.
- Optional lightweight in-app notice that a friend started showing their Todo flow.

Not allowed:
- Friend Todo created notification.
- Friend Todo updated notification.
- Friend Todo completed notification.
- Friend reminder notification.
- Read receipt notification.

Push payload must not include Todo title, due date, memo, status, or other Todo content.

## 13. Acceptance Criteria
- [ ] Owner can turn `내 할일 보여주기` on/off for each active friend.
- [ ] `내 할일 보여주기` is not shown on pending friend request rows with `수락`/`거절`.
- [ ] Active grant makes allowed friend Todo projections available to the viewer.
- [ ] Friends shows the friend list before any friend Todo rows.
- [ ] Viewer sees `친구 할일` by expanding the matching active friend row.
- [ ] Expanded friend row shows all displayable ObservedTodo for that friend without an arbitrary preview cap.
- [ ] No visible friend todos for a friend means no expand affordance for that friend.
- [ ] `친구 할일` rows show owner identity without a persistent `보기만` badge.
- [ ] `친구 할일` rows have no checkbox, complete, edit, delete, reminder, or assignment actions.
- [ ] Calendar separates `내 할일` and `친구 할일` only when both/each section has items.
- [ ] Calendar body supports expanded month and compact week states.
- [ ] Friend todos do not force Calendar to remain week-only.
- [ ] Calendar does not show an empty `친구 할일` section.
- [ ] Calendar can collapse `친구 할일` into a count/nearest summary when space is tight.
- [ ] Widget does not show ObservedTodo in MVP.
- [ ] ObservedTodo is excluded from Todo tab, Today, Completed, productivity metrics, and assignment/direct assignment flows.
- [ ] Revoke blocks server observed read paths immediately.
- [ ] Online clients purge revoked observed data from Friends and Calendar.
- [ ] Offline clients purge revoked observed data on reconnect.
- [ ] Todo-level public/private UI does not exist in MVP.
- [ ] Read receipt, seen status, and last viewed time are not stored or displayed.

## 14. Success Metrics
- `내 할일 보여주기` switch enable rate among active friends.
- Per-friend `친구 할일` expand rate when items exist.
- Calendar friend section exposure-to-open rate.
- Confusion reports involving assignment/share wording.
- Zero incidents where ObservedTodo is counted as my Todo.
- Zero stale observed data incidents after revoke.

## 15. Risks
- Person-level visibility has a wider blast radius than item-level sharing.
- Without public/private policy, users may underestimate what becomes visible.
- Calendar can confuse friend todos with my schedule if source styling is weak.
- Widget inclusion would be especially easy to misread; MVP avoids it.
- Assignment/direct assignment wording may leak into the mental model.
- Offline cache cannot be physically removed until reconnect; copy must avoid overpromising.

## 16. Future Candidates
- Todo-level public/private policy.
- Per-friend item exceptions.
- Explicit hidden/mute controls for viewer-side cleanup.
- Copy ObservedTodo into my Todo as a new independent Todo.
- Separate opt-in `친구 할일` Widget.
- Viewer opt-in Calendar display controls if Calendar becomes crowded.
