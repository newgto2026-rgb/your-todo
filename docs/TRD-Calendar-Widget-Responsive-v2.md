# TRD - Calendar Widget Responsive v2

## 1. Goal
- Upgrade the calendar home widget so it has different information density by widget size.
- Keep the compact widget focused on monthly distribution.
- Make the large widget feel close to Google Calendar's month widget by showing Todo titles inside date cells.

## 2. Product Decision
The widget is still a home-screen preview and app entry point, not a full calendar editor.

- Compact size, roughly 3x3: show month grid, today, and Todo count only.
- Expanded size, roughly 4x4: show month grid, today, and Todo title chips inside each date cell.
- Tapping a current-month date opens the Calendar screen with that date selected.
- Editing, completing, deleting, and detailed daily lists stay in the app Calendar screen.

## 3. User Value
- Compact users preserve home-screen space and quickly see which days have tasks.
- Expanded users spend more space because they want to read what is on a day without opening the app.
- Busy days should become more useful, not collapse immediately into an opaque count.

## 4. Display Policy
### 4.1 Compact
- Date number is always visible.
- Today is visually highlighted.
- Current-month dates with Todo show a small count label.
- Todo titles are not shown.

### 4.2 Expanded
- Date number is always visible.
- Today is visually highlighted.
- Current-month dates show Todo title chips.
- Each Todo chip is one line.
- Long titles are truncated by the widget host.
- A date cell has a maximum four-line Todo budget.
- If a date has one to four Todos, show up to four Todo titles.
- If a date has five or more Todos, show three Todo titles and one overflow chip, for example `+2`.

## 5. Todo Ordering Policy
Expanded date cells sort Todo previews for fast recognition:

1. Incomplete Todos before completed Todos.
2. Earlier due time before later due time when due time exists.
3. Higher priority before lower priority.
4. Newer created Todos before older created Todos.

Completed Todos can be shown only after incomplete Todos and should be visually muted.

## 6. Architecture
- Scope the implementation to `:feature:calendar:widget` and documentation.
- Keep `feature:calendar:widget -> feature:calendar:api, core:domain, core:model`.
- Do not make the widget depend on `feature:calendar:impl` or `app`.
- Do not reuse app Compose Calendar UI in Glance.
- Reuse existing monthly summary data where possible.
- Add preview-only fields to the widget state and presenter mapping.
- No database schema change is required.

## 7. Implementation Plan
- Change the widget to responsive size mode with compact and expanded size buckets.
- Pick compact or expanded rendering from `LocalSize`.
- Add Todo preview chip models to `CalendarMonthWidgetDay`.
- Map monthly summaries into compact counts and expanded preview chips.
- Render compact cells with the existing count layout.
- Render expanded cells with Google Calendar-style small chips.
- Keep date click behavior unchanged for current-month dates.
- Update widget provider sizing metadata for target cell dimensions.

## 8. Acceptance Criteria
- Compact rendering does not show Todo titles.
- Expanded rendering shows Todo title chips inside date cells.
- Expanded dates with one to four Todos show one to four titles.
- Expanded dates with five or more Todos show three titles and one `+N` overflow chip.
- Incomplete Todos appear before completed Todos.
- Completed Todo chips are visually muted when shown.
- Current-month dates are clickable and adjacent-month dates are not.
- Month navigation callbacks still work.
- Error fallback still renders.
- Long Korean titles do not create multi-line chips in code; each chip is constrained to one line.

## 9. Validation
- Run `./gradlew :feature:calendar:widget:testDebugUnitTest`.
- Run `./gradlew :feature:calendar:widget:lintDebug`.
- Run `./gradlew :app:assembleDebug` because widget provider XML and manifest merge affect packaging.
- Test cases must cover compact count rendering, expanded 4-title rendering, expanded overflow rendering, done-item ordering/muting, date click behavior, and error fallback.

## 10. Out of Scope
- Widget-side Todo completion.
- Widget-side Todo creation button.
- Multi-day Todo bars.
- Category color configuration.
- User widget settings screen.
- App Calendar screen redesign.
