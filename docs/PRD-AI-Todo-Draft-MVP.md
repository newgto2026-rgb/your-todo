# PRD: AI Todo Draft MVP

## Problem

Users often describe several tasks in one natural sentence. The app currently requires manual entry per task, which is slow when the user already has a sentence like "Neo goes to academy tomorrow at 7, I do laundry at 10, and prepare dinner today."

## Goal

Convert free-form Korean text into editable todo drafts, then let the user review and save them for themselves or friends.

## Non-goals

- AI does not directly save tasks without user review.
- MVP does not train or bundle an on-device model.
- MVP does not support shared multi-assignee tasks as a single entity. If a sentence mentions multiple people, the draft is split only when the model is confident; otherwise it requires review.

## User Flow

1. User taps the todo `+` FAB.
2. App shows two actions: detailed add and AI add.
3. User selects AI add.
4. User enters free-form text.
5. App sends the text plus known assignee candidates to the AI proxy.
6. Proxy returns structured draft tasks.
7. App shows an editable review sheet.
8. User edits title, assignee, date, time, priority, and selected state.
9. User saves selected drafts.
10. Self-assigned drafts become normal todos; friend-assigned drafts become assignment bundles.

## Acceptance Criteria

- The `+` FAB exposes detailed add and AI add choices.
- AI add opens a review-first bottom sheet.
- The AI request includes only known people ids and aliases.
- Drafts can be edited before saving.
- Drafts with missing title, missing assignee, invalid date, or invalid time cannot be saved.
- Self-assigned drafts use the existing local todo add flow.
- Friend-assigned drafts use the existing assignment bundle flow.
- AI failures show a recoverable error and keep the user's prompt.
- The proxy can be run locally against Ollama with `qwen3:4b-instruct`.

## Quality Bar

The MVP should handle:

- Multiple tasks in one sentence.
- Date context carry-over, such as "tomorrow 7 A and 10 B, today C."
- Self and friend assignee mentions.
- Missing time as a valid date-only task.
- Ambiguous assignee as review-required.

## Product Risks

- Small local models can misread Korean date context.
- User-provided friend nicknames can be duplicated or ambiguous.
- Friend assignment save requires a signed-in session and server availability.
- Local proxy is a development MVP and must not be exposed publicly without auth, rate limits, and logging policy.
