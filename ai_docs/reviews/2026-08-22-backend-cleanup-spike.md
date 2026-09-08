# Backend cleanup tracker

Living checklist of what is still open. Check items off as they land; when an MR closes, its
detail moves to the history file (working rule 11) rather than staying here ticked.

Completed detail lives in [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md).
This file carries the open work and what steers it: the Progress tables, the items carried forward,
the open security findings, the working-rule index, the open MR waves, decisions needed from
the user, stale side branches, Appendix C (open leads), and the current session's log.
Closed outcomes, the working rules' full text and the log archive live in the history file.
Keep it that way (working rule 11). **This file must not grow in an MR** -- working rule 53 gives
that a command, and the two counts it compares are stamped in the Progress metrics below.

Source review: 2026-08-22, baseline `main` @ `8c28cf3`, six parallel passes (controllers/API, core collection and content services, media/upload pipeline, security/auth/config, data layer, tests/build). Every finding was verified against the code -- caller greps for dead-code claims, line-level reads for bugs. Unverified suspicions are quarantined in [Appendix C](#appendix-c--unverified-leads).

Line numbers are from the `8c28cf3` baseline. Find symbols by name, not by line, once earlier MRs have shifted the files.

## Progress

| Wave | MRs | Status |
|---|---|---|
| 1 — Deletions | MR 1a-4 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-1--deletions) (#159, #160, #161, #162, #164). Two residuals carried forward, below. |
| 2 — Bugs | MR 5-9 | **complete, and its residual is now closed too** — [history](2026-08-22-backend-cleanup-history.md#wave-2--bugs) (#165, #166, #168, #169, #170, #172, #173). Bug #17, carried forward since 2026-08-24, shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256)). |
| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). Superseded by the 2026-08-24 review; see the security row. |
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)). **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 taught working rule 12 (superseded by rule 37 **as a comment rule only -- its protected-file list is still live**; the three counts at the Inline-comments row were re-run at the ninth close-out, and one of the three had been stale since [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) -- see the row itself); **zero stale-docblock items still open** -- the last two closed 2026-09-02 ([#303](https://github.com/themancalledzac/edens.zac.backend/pull/303)) and **Wave 4 is now complete**; the `filterNonListedChildCollections` docblock had closed 2026-08-29 as already rewritten. |
| 5 — Consolidations | MR 15-19 | **Open: MR 18 #10, MR 18 #13's sort split, MR 19 #17 members (b), (c), (e), and two MR 19 rows filed 2026-09-01 (drop the orphan `images` array; `searchImages` GIFs).** MR 15, MR 16 and MR 17 are complete; MR 16 #3 closed as decided 2026-09-01. **MR 18 #13 is unblocked and its direction was ANSWERED 2026-09-08: case-insensitive SQL (`ORDER BY lower(...)` at six sites, ~4 order tests). It is the fourteenth run's item 1.** Shipped-MR narrative: [history](2026-08-22-backend-cleanup-history.md#progress-row-narratives-wave-5-chain-moved-2026-09-01). |
| 6 — Conventions | MR 20-22 | **MR 20 closed 2026-08-30 by user decision** -- bare arrays are blessed and no endpoint changed ([history](2026-08-22-backend-cleanup-history.md#mr-20--the-bare-array-decision-closed-2026-08-30-moved-from-the-tracker)). MR 21 not started; MR 22 has one of eleven rows shipped (#29, #303). |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | **MR 25 is half done; MR 26 is 4 of 13 -- the `readAt` and `count` rows closed 2026-09-08 ([#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)), leaving 9 open of 11 counted in the section.** #27 shipped 2026-09-01 ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)) and the two guard tests closed 2026-08-24 ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195), [#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)); the Progress row said "not started" through both. Two of MR 25's four positional/arity members shipped 2026-08-31: `FileEntry` ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267)) and `resolveCollectionDownloadEntries` ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)). `DownloadResolution.extension` shipped 2026-09-02 ([#304](https://github.com/themancalledzac/edens.zac.backend/pull/304)) with all 13 refs exact, leaving **one** open member: `CollectionRequests.Update` (**22 sites as of 2026-09-01**, was 21; must ride with the `TestFixtures` pass). |

Four sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table. **"Decisions needed from the user" was the fourth and was still
missing its row until 2026-08-24's close-out** -- eight open items, invisible to this table, which
is the same failure the paragraph above was written to fix:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **2 open: S-30 (LOW), S-31 (LOW).** Both sit on the anonymous public read surface. Edit gate (rule 36): `grep -c '^- \[ \] \*\*S-'` = **2** at [#313](https://github.com/themancalledzac/edens.zac.backend/pull/313). **Rule 36 says two cells; there are three** -- this one, the Security-findings category row, and the gate line under the section head. **All three must move together.** **33 closed** (S-1..S-24, S-26..S-29, S-32..S-36; S-25 was never assigned). Highest issued is S-36. Numbered findings only; the unsettled questions have their own row. Prior states: [history](2026-08-22-backend-cleanup-history.md#open-security-findings-row-prior-states-moved-2026-09-01). |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend) | **3 open: FE-2, FE-3, FE-4.** FE-5 closed 2026-09-05 (edens.zac#351 shipped the dev-workflow note); FE-1 closed as won't-do 2026-09-01; the #294 page-size debt closed as accepted 2026-09-02. Gate: `grep -c '^- \[ \] \*\*FE-'` = **3**, measured on the review branch `docs/eleventh-run-review` (from `afa39d6f`, 2026-09-05); re-run on `main` after merge. All three are filed on the frontend board and stay open here until the frontend acts. The count lives in the section, not the heading. Prior states: [history](2026-08-22-backend-cleanup-history.md#cross-repo-row-prior-states-moved-2026-09-01). |
| [Decisions needed from the user](#decisions-needed-from-the-user) | **3 open as of 2026-09-05, and ONE is waiting on you**: how is disk import triggered (promoted from Appendix C; one sentence settles it). The other two sit under [Parked by decision](#parked-by-decision--waiting-on-nobody). Edit gate (rule 36): the count is over the section's own `- [ ] ` lines; re-run it and update this row together. Prior states: [history](2026-08-22-backend-cleanup-history.md#decisions-row-prior-states-moved-2026-09-01). |
| [Tests that cannot fail](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker) | **0 open of 6 — CLOSED 2026-08-30.** The last three shipped in one session (#239, #240, #241), each mutation-proved against `main` first. Two of the three carried a wrong premise that was corrected while closing: the share-link credential is a `Set-Cookie`, not a response-body token; and the `AdminUserControllerTest` pointer the board suggested names a test that does not redden on that mutation. Write-ups in history. |
| [Rule 37 debt](2026-08-22-backend-cleanup-history.md#rule-37-debt--r-1-closed-2026-08-30-moved-from-the-tracker) | **0 open — R-1 closed 2026-08-30 ([#238](https://github.com/themancalledzac/edens.zac.backend/pull/238)).** Taught working rule 39. The wider per-package sweep is not tracked here; it is the Inline-comments row in the category table below. |
| [Stale side branches](#stale-side-branches) | **Zero open PRs, re-run 2026-09-05** (`gh pr list --state open --json number` = `[]`). **Nine worktrees plus the main checkout, re-run 2026-09-05** (`git worktree list` = 10 rows): seven under `edens.zac.backend.worktrees/`, two under `.claude/worktrees/`; three are merged-work worktrees to remove. Four of the eight tracked branches have no `origin` ref; measure against local refs. Prior state: [history](2026-08-22-backend-cleanup-history.md#stale-side-branches-row-prior-state-moved-2026-09-01). |
| [Unsettled security questions](#unsettled-security-questions) | **2 open: U-2, U-3.** U-1 answered and U-8 closed as moot 2026-09-05; U-7 closed 2026-09-08 ([#316](https://github.com/themancalledzac/edens.zac.backend/pull/316)). Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **2**, **re-run on `main` at `2bc62a20` 2026-09-08 after [#316](https://github.com/themancalledzac/edens.zac.backend/pull/316)-[#319](https://github.com/themancalledzac/edens.zac.backend/pull/319) merged, and it holds** -- no restamp owed. Run it and update this row together. The section also holds one non-`U-` open box, the `RoleRepository.canView`/`isClient` deletion, which opens `**Delete` and cannot move this gate. Prior state: [history](2026-08-22-backend-cleanup-history.md#unsettled-security-questions-row-prior-state-moved-2026-09-01). |

**Board file sizes, the rule-53 gate.** Measured with

```
wc -l ai_docs/reviews/2026-08-22-backend-cleanup-spike.md ai_docs/reviews/2026-08-22-backend-cleanup-history.md
```

**At `a20473fd` (rule 42): tracker 1,591, history 10,889.** #309 took them to **1,585** / **11,019**. [#310](https://github.com/themancalledzac/edens.zac.backend/pull/310) takes them to **1,571** / **11,143** (`--numstat` 85 / 99): five fully-closed sections (MR 14 docblocks, MR 15, MR 16, MR 17, the closed-findings ledger) and the #309 log entry moved out; seven restamped refs, two corrected C8 numbers, the S-33 re-specification and one new row in.
Chain since the tenth close-out: `8f635d35` **1,873** / **9,774**; #303 (`efed4c63`) **1,864** / **9,854**;
#304 and #305 (`bd0e15ef`) **1,864** / **9,891**; #301 (`afa39d6f`) **1,873** / **9,915**; this review
**-282** / **+965**; #307 (`50d633e2`) **1,591** / **10,880**. **#301 grew the tracker by 9 lines
(+11 / -1), which rule 53 forbids, and nothing recorded it.** The tracker's
delta must be <= 0 in every MR that touches it (**working rule 53**); `scripts/board-gates.sh` checks it.
The #299 rebase arithmetic: [history](2026-08-22-backend-cleanup-history.md#rule-53-size-stamp-the-299-rebase-paragraph-moved-2026-09-05).

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. **Outcome at `afa39d6f`** (this branch leaves `src/` untouched): main 27,199 -> 28,190 (+991), test 32,604 -> 35,936 (+3,332), net **+4,323**, by `git ls-files 'src/test/java/**/*.java' | xargs wc -l | tail -1` and the same for `src/main`, against baseline `8c28cf3`. The deletion half landed close (Wave 1 ~1,100, MR 3-4 2,567); the "few hundred added" was about 8,000. Do not quote the original figure as a target. The Lombok and builder tests MR 3-4 targeted are gone; `model/`, `entity/` and `types/` hold 1,499 lines of wire and validation tests. The test tree grew from 1.20x main to 1.27x across the effort because guard tests ship at about 3:1 against source.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **22** (5 high) -- **22 shipped, 0 open.** Bug #32 was filed 2026-09-05 from Appendix C and closed 2026-09-06 ([#314](https://github.com/themancalledzac/edens.zac.backend/pull/314)). Gate: `grep -c '^- \[ \] \*\*Bug #'` = **0** at [#314](https://github.com/themancalledzac/edens.zac.backend/pull/314). Items **#22 through #34, with #30 closed**, are filed in the same number series but are feature dependencies, doc bugs and coverage items; they open with `**#NN` and have their own gate: `grep -c '^- \[ \] \*\*#[0-9]'` = **4** (#22, #31, #33, #34), re-measured 2026-09-08 on `docs/close-out-thirteenth-run` and unchanged -- #31 stays open on its FE and `is_film` halves even though its coverage debt is paid. **Use this wide form, never `'^- \[ \] \*\*#2'`**; the day the narrow gate went blind is rule 53's lesson and lives in history. Prior state: [history](2026-08-22-backend-cleanup-history.md#bugs-category-row-prior-state-moved-2026-09-01). | -- |
| Security findings | **2 open: S-30 (LOW), S-31 (LOW).** Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **2** at [#313](https://github.com/themancalledzac/edens.zac.backend/pull/313); edit this cell, the section-table row **and the gate line under the section head** together -- rule 36 names two, but there are three. **33 closed** (S-1..S-24, S-26..S-29, S-32..S-36; S-25 was never assigned). Numbered findings only; the unsettled questions have their own gate. Prior state: [history](2026-08-22-backend-cleanup-history.md#security-findings-category-row-prior-state-moved-2026-09-01). | -- |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments | **RE-RUN 2026-09-04 on `main` at `afa39d6f` (unchanged on this branch; `src/` untouched). Leading form: **1,386** (203 main / 1,183 test). Trailing form: **67** with the corrected scheme filter; the old command read 68 by counting a `jdbc:postgresql://` string literal.** **Both deltas reconcile line-for-line (rule 42):** main `203 -> 203`, unmoved across six merges; test `1,169 -> 1,183` is +14 = 9 written new by [#300](https://github.com/themancalledzac/edens.zac.backend/pull/300) (`MessagesControllerAdminTest` +5, `MessageRepositoryTest` +4) + 5 written new by [#301](https://github.com/themancalledzac/edens.zac.backend/pull/301) (`CollectionServiceTest` +5). **Both are rule-37 violations in merged code, not sweep misses; #299 and #302 are docs-only and moved nothing.** Use the `git grep` form below (rule 50). Measurement history, including the `3a53c0cb` reconciliation: [history](2026-08-22-backend-cleanup-history.md#inline-comment-count-measurement-history). | ~300 net (also low) |

**The two commands, exactly as run** (**rule 31**) -- they carry pipes and cannot live inside a
table cell. Leading form, **`1,386`** (203 main / 1,183 test) at `afa39d6f`:
```
git grep -c '^[[:space:]]*//' -- 'src/main/java' | awk -F: '{s+=$NF} END {print s}'
git grep -c '^[[:space:]]*//' -- 'src/test/java' | awk -F: '{s+=$NF} END {print s}'
```
Trailing form, **`67`** at `afa39d6f`. Scheme filter widened 2026-09-05: the old `grep -v 'https\?://'`
let `jdbc:postgresql://` through and read 68.
```
git grep -n '//' -- src/main/java src/test/java | grep -vE '^[^:]+:[0-9]+:[[:space:]]*//' | grep -vE '[a-z][a-z0-9+.-]*://' | wc -l
```
Run both from the repo root. **Do not use `grep -rn`**: it is 3 low on the test side (rule 50) and
unscoped it triple-counts `.claude/worktrees/`. | ~300 net (also low) |
| ^ **re-scoped 2026-08-28** | Working rule 37 makes **every** inline comment in `src/main` and `src/test` a violation. **Do not sweep this in one MR** -- take it per file. `RoleRepository` (0) and `AdminUserControllerTest` (0) are done; `SecurityConfig` holds 4 (#243) and is not. `AdminBootstrap` (6, in `services/`) and `CollectionControllerProd` (9) are the rule-12 files still owed. **One recorded exemption**: the second `coverImage` banner in `CollectionControllerProdTest` (1 of that file's 78) stays until its "Carried forward" decision lands. **#301, #303 and #305 edited three of the six files filed below and swept nothing** (rule 47, second sentence, not honoured); the six are filed as rows so a gate can see them. Closed-file chain: [history](2026-08-22-backend-cleanup-history.md#rule-37-per-file-sweep-closed-file-chain-moved-2026-09-01). | -- |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | **10 findings** | ~2,700 (+700 optional) |
| Build/config rot | **Open/closed split dropped 2026-08-31 (third close-out) because it had no backing.** The cell said "9 open" and `grep -n 'C-[0-9]'` across this file returned only the cell itself — no section, no checkboxes, no list. The original findings closed in Wave 1 as **MR 2** ([history](2026-08-22-backend-cleanup-history.md#wave-1--deletions), seven ticked items); the only C-numbered entry ever written down is **C-1, filed and closed 2026-08-30** ([#245](https://github.com/themancalledzac/edens.zac.backend/pull/245)). If config rot is worth tracking again, file items with checkboxes and give the section a gate; do not restore a count nothing can verify. Note two `C` schemes run at once: `C-1` here, and `C7`/`C8` for the Appendix C leads, which are unnumbered bullets. | ~150 |

### Rule-37 per-file sweeps, filed 2026-09-05

Counts by `git grep -c '^[[:space:]]*//' -- <file>` on `main` at `afa39d6f`. One MR each (rule 47).
The five main-side files hold 108 of the 203.

- [ ] `services/ImageProcessingService.java`: **26**. Edited by #305 without a sweep.
- [ ] `dao/ContentRepository.java`: **25**.
- [ ] `services/ContentModelConverter.java`: **21**. Edited by #303 without a sweep.
- [ ] `services/CollectionProcessingUtil.java`: **18**. Edited by #301 without a sweep.
- [ ] `controller/admin/AdminController.java`: **18**.
- [ ] `controller/prod/CollectionControllerProdTest.java`: **78**, the largest test-side holder. Keep the exempt `coverImage` banner until its decision lands.

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split, re-reviewed 2026-08-24. Waves 1-3 read "complete"
but held **eight live items**, collapsed into five entries. Since then: the `PersonRepository` entry
was closed by MR 15 #6 (decided, not deferred), and the chunked-body residual moved to **S-5** under
"Open security findings". What is left is below -- plus one bug that never had a
row at all (#17), one item found while costing #209's guardrail (the coverImage banner), and three
bugs filed 2026-08-29 (#18-#20, at the end of this section).

- [ ] **The `coverImage` stripping that does not exist, and the test that cannot fail.** *(New row
  2026-08-24, found while writing #209's cost report; taught **working rule 22**.)*
  `CollectionControllerProdTest` has a section headed "Fix 1: coverImage stripped for protected
  CLIENT_GALLERY on list endpoints" whose test asserts only controller pass-through. **No such
  stripping exists** -- `CollectionProcessingUtil.buildBasicModel` sets `coverImage`
  unconditionally from `coverImagesById`, verified by read (`:182`, with `isPasswordProtected`
  computed four lines below). Strip nothing, change nothing, and the test stays green.

  **Recorded exemption (working rule 37): this second banner is excluded from the rule-37 comment
  sweep until this decision lands** -- a sweep deleting it would erase the record of the open
  question. **The frontend already strips** on the public card path (FE #327), so "list-endpoint
  stripping is genuinely wanted" would duplicate an enforcement that already exists.

  **What to decide.** Either delete the banner and rename the test to what it asserts, or treat it
  as a specification with no implementation and do the work. Do not resolve it by reading the
  comment; #209's cost report has what implementing it would break. Background:
  [history](2026-08-22-backend-cleanup-history.md#the-coverimage-stripping-row-background-moved-2026-09-01).

- [x] **Bug #17** (medium) `updateImages` claimed a batch save it did not do — [#256](https://github.com/themancalledzac/edens.zac.backend/pull/256),
  2026-08-31. **Fixed by correcting the log line, not by building a `batchUpdate`** — the loop
  already writes per image through `saveContentTags` and `saveContentPeople`, so batching only the
  `saveImage` calls leaves the endpoint O(N) in statements. Reasoning now lives in `updateImages`'s
  docblock. Write-up in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-second-close-out--bugs-17-19-20-and-passkey-deregistration).
- [x] **Four main-dead, test-live members owed to MR 25** -- **CLOSED as an umbrella row 2026-09-01
  (tenth run).** Two shipped ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267),
  [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)); the two that remain are
  tracked as their own rows under [Positional constructors that block the `TestFixtures`
  pass](#positional-constructors-that-block-the-testfixtures-pass). **Do not re-file them here.**
  Body and the arity-scanner method:
  [history](2026-08-22-backend-cleanup-history.md#four-main-dead-test-live-members-body-moved-2026-09-01).
- [x] **V19's `admin_home_tile.cover_image_id`** -- **ANSWERED and DROPPED 2026-08-31**, shipped as
  `V59__drop_admin_home_tile_cover_image_id.sql` and verified in the tree 2026-09-01. **This box
  stayed open through five close-outs after the decision landed** and inflated the board's headline
  count by one. Research body and the premise correction it records:
  [history](2026-08-22-backend-cleanup-history.md#v19s-admin_home_tilecover_image_id-research-body-moved-2026-09-01).

### Bugs filed after the waves closed (2026-08-29)

- [x] **Bug #18** (low-medium) `updateLocation` missed the create path's slug-uniqueness check --
  **DONE** ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276), 2026-08-31). Shipped as specified; the caller-visible 409 is
  byte-identical before and after, because `GlobalExceptionHandler.handleDataIntegrity` discards
  the message. **This closed the last open bug on the board.**
  [Write-up](2026-08-22-backend-cleanup-history.md#bug-18--the-slug-check-and-an-item-that-did-not-price-its-own-payoff-276).
- [x] **Bug #19** (low) location-tagged GIFs could never surface on `/location/{slug}` — [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258),
  2026-08-31. **Direction answered by the user: surface, not refuse.** The orphan queries are now
  predicated on `content_type IN ('IMAGE', 'GIF')` instead of joining `content_image`, and
  `LocationPageResponse.images` widened to `List<ContentModel>`. **This is a cross-repo wire change
  — see the cross-repo row below.**
- [x] **Bug #20** (low) `shutdown()` stopped both executors and awaited one — [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255),
  2026-08-31. Both are stopped before either is waited on, so the grace periods overlap. Executor
  construction and the virtual-thread choice untouched; the cost report on unifying them is in the
  PR and summarised in history — **the answer was do not unify**.
- [x] **Bug #21** (low) the dimension fallback failed soft and wrote `0` — [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249),
  2026-08-31. Both defaults are `null`; frontend fallbacks untouched. Write-up, and the
  mock-default trap that made the first version of its test unable to fail, in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-close-out--s-14-s-16-s-22-s-23-s-24-and-bug-21).
- [x] **`AdminUserControllerTest`'s 73 inline comments** — **DONE** ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272), 2026-08-31), 73 -> 0, `+118 / -73`, one file. Delete-and-relocate as specified; the substantive comments are now each test's docblock. Taught **rule 46** (the checksum needs its metric named: 17 comments deleted moved the rule-37 line count by 16, because one was a trailing `code; //`). [Write-up](2026-08-22-backend-cleanup-history.md#adminusercontrollertests-73-inline-comments-272).

- [x] **#23 (doc bug)** `ai_ec2.md` carried a stale second copy of the `.env` template — **DONE** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), 2026-08-31). Both env blocks deleted and replaced with a pointer at `.env.example`; `ai_deployment_strategy.md` untouched. **This did not settle U-1** and the PR says so — see U-1, still blocked. [Write-up](2026-08-22-backend-cleanup-history.md#23--the-stale-env-template-in-ai_ec2md-269).

- [ ] **#22 (feature dependency, not a bug) -- clearing a nullable collection field through the
  existing PUT.** *(Filed 2026-08-31 from the frontend board's MA1; re-scoped 2026-09-05 from the
  frontend handoff `edens.zac` `docs/spikes/2026-features/backend-handoff-MA1-EM2.md` section 1.)*
  The PUT already serves set-a-value: every field on the shared write is null-guarded across
  `CollectionProcessingUtil` and `CollectionService`, so `{id, title}` updates title alone. The only
  gap is clearing a nullable field (description, collection date, locations), because null means
  unchanged. The work is an explicit-null marker or a `clear: [...]` list on the existing PUT, named
  in the DTO. No new verb; no `PATCH /api/edit/collections/{id}`. Six `@PatchMapping`s exist today
  (`git grep -n "PatchMapping(" -- 'src/main/java/**/controller/**'`; #300 added the sixth) and none
  is a whole-collection patch. **COLD, and no longer blocked on the frontend.** Ref detail:
  [history](2026-08-22-backend-cleanup-history.md#22-patch-route-ref-detail-moved-2026-09-01).


- [x] **#24** -- `COLLECTION` blocks carried no `locations` -- DONE ([#277](https://github.com/themancalledzac/edens.zac.backend/pull/277), 2026-08-31). [history](2026-08-22-backend-cleanup-history.md#24-outcome-moved-2026-09-08)

- [x] **#25 (same gap as #24) — `people` on `COLLECTION` content blocks was inert for exactly the
  reason `locations` was.** — **DONE**
  ([#293](https://github.com/themancalledzac/edens.zac.backend/pull/293), 2026-08-31). One record
  component on `ContentModels.Collection` plus a copy in `fromCollectionModel`, following #277
  exactly. [Write-up](2026-08-22-backend-cleanup-history.md#25-the-people-component-moved-2026-09-01).

- [x] **#26 (feature dependency, not a bug) — contact messages had no retention TTL, so PII
  accumulated forever.** — **DONE** ([#281](https://github.com/themancalledzac/edens.zac.backend/pull/281), 2026-08-31). **Shipped off, and the first
  opt-in only reports**: `app.messages.retention.days` defaults to `0` and
  `app.messages.retention.dry-run` defaults to `true`, so deploying the MR changes no behaviour at all.
  Both guards mutation-proved. **No frontend half exists or is needed** -- a retention TTL is
  configuration, not a control. [Write-up](2026-08-22-backend-cleanup-history.md#26--a-retention-ttl-shipped-off-281).

- [x] **#30 (feature dependency, not a bug) -- `messages` had no read marker.** **CLOSED 2026-09-05.**
  Backend shipped as [#300](https://github.com/themancalledzac/edens.zac.backend/pull/300) (2026-09-01); the FE half shipped as
  [edens.zac#396](https://github.com/themancalledzac/edens.zac/pull/396), merged 2026-09-03. The notify channel is the frontend board's MA4
  remainder and has no backend row. `AdminMessageView.readAt` is now consumed; see the coupling note
  in the cross-repo section. Body: [history](2026-08-22-backend-cleanup-history.md#30-outcome-2026-09-05----the-fe-half-shipped-as-edenszac396).

- [ ] **#31 (two data bugs) -- public reads returned `parents: null`, and `is_film` was unset on
  three film collections.** **Backend MERGED** ([#301](https://github.com/themancalledzac/edens.zac.backend/pull/301), 2026-09-04). The row stays open
  for two reasons: the FE half is RC1 on the frontend board, and the `is_film` half is unverified
  against data. **Guardrail -- V62 does not repair the counts on its own.** Its camera rule joins
  `content_cameras.is_film`, which only V23 (two body names) and the admin camera upsert ever set;
  ingest never flags a camera row (`ImageProcessingService.createCamera:1302`). The ingest rule is
  keyed on scanner EXIF names (`EZ Controller`, `OpticFilm 8300i`; `resolveFilmCameraDefaults:516-527`)
  and writes `is_film` on the image only, so pre-rule images whose camera row is `OpticFilm 8300i` or
  `Mamiya 645 Pro` are untouched by V62. Flyway runs V62 once; flagging a body afterwards repairs
  nothing until a V63 re-runs the second statement. **Owed, in order:** deploy (there is no CI deploy
  job; V62 runs when the EC2 image is next rebuilt), re-measure the four slugs and the camera name per
  image, then if the bodies are unflagged scanner rows, flag them via `POST /api/admin/metadata/cameras`
  and ship V63. **Coverage debt (rule 15): PAID 2026-09-08**
  ([#317](https://github.com/themancalledzac/edens.zac.backend/pull/317)). `CollectionParentListedGateIntegrationTest`
  executes the `LISTED`/`cc.visible` gate at `CollectionRepository:369` on real Postgres, both arms,
  each conjunct on its own parent plus a LISTED-and-visible control. M11 and both single-conjunct
  mutations redden it. UNLISTED went in beyond the specified case: the method's javadoc claims a
  HIDDEN *or UNLISTED* parent is a dead link, and a HIDDEN-only test passes if the gate is rewritten
  as `!= 'HIDDEN'`. **The row stays open** -- the FE half is RC1 and `is_film` still needs a deploy.
  [Write-up](2026-08-22-backend-cleanup-history.md#31s-listedonly-gate-test-2026-09-08----paid-and-the-row-still-open).
  Detail:
  [history](2026-08-22-backend-cleanup-history.md#31----parents-on-public-reads-and-the-is_film-backfill-301).

- [ ] **#33 (feature dependency) -- `notifyEmails` on `GalleryAccessRequest`.** *(Filed 2026-09-05
  from the frontend handoff `edens.zac` `docs/spikes/2026-features/backend-handoff-MA1-EM2.md`
  section 2, EM2.)* `recipient_emails` has one writer, `CollectionRepository.saveGalleryAccess`
  (`:782`), which overwrites the whole array, and `CollectionService.updateGalleryAccess` mails every
  address in it (`:1698`). The stored list and the send list are the same column. Ask: a
  `notifyEmails` component on `GalleryAccessRequest`; mail only those, store `emails` as sent.
  Owner: backend. **COLD.**

- [ ] **#34 (feature dependency) -- child summary on `ContentModels.Collection`.** *(Filed 2026-09-05
  from `edens.zac` `docs/spikes/2026-features/backend-handoff-RC3.md`.)* The record (21 components,
  `model/ContentModels.java`) carries nothing about children. Ask: `hasChildren: boolean` and
  `children: [{ id, name, slug, coverImageUrl }]` capped at N, using `name` not `title`, with the same
  `LISTED` and `cc.visible = true` gates #301 applied to `parents`. The value already exists
  server-side on the admin manage DTO (`CollectionRequests.UpdateResponse.hasChildren`, `:204`). Do
  not reuse `DisplayMode`. **COLD.**

- [x] **Bug #32** (MED) `updateImages` half-applied an item and reported it failed, or reported earlier items succeeded when nothing committed -- [#314](https://github.com/themancalledzac/edens.zac.backend/pull/314), 2026-09-06. Per-item savepoint (`TransactionTemplate` with `PROPAGATION_NESTED`), not fail-the-batch: the response already carries a per-item `errors` list, so the savepoint makes that contract true instead of deleting it. Each item's `saveImage` moved inside its own unit -- a savepoint only covers writes issued inside it, and the deferred second pass put every row update outside the one meant to protect it. Newly created tags, people and locations go into per-item sets merged only after the savepoint commits, so a rolled-back item cannot report metadata it no longer has. **Both shapes reproduced against the pre-fix code:** (a) failed with the failed item's tags still committed, (b) threw `DataIntegrityViolationException` out of the method entirely.

## Cross-repo findings owed to the frontend

**3 open as of 2026-09-05: FE-2, FE-3, FE-4.** Gate: `grep -c '^- \[ \] \*\*FE-'` = **3** at [#309](https://github.com/themancalledzac/edens.zac.backend/pull/309).

**Owed to the frontend the moment [#309](https://github.com/themancalledzac/edens.zac.backend/pull/309) deploys (not an FE- row, because it is a purge and a warning, not a defect):**
**(1)** D15 unblocks -- purge `revalidateTag('search-images')` plus the location and tag tags. Every
cached body from before the deploy may contain private-gallery images, so the purge is the fix, not
a tidy-up. **(2)** Three public surfaces return strictly fewer items now: `/search`,
`/location/[slug]` and `/tag/[slug]`. **(3)** The location page loses more than the leak: content
belonging to no collection at all is now excluded too, so an uploaded-but-unplaced image that used
to appear there will not. If the frontend was relying on that as an "everything at this location"
view, it was relying on a disclosure. **(4)** `ImageSearchFilter` gained no query parameter --
`publicOnly` is set from the route and is deliberately not bindable, so sending it does nothing.
FE-5 closed 2026-09-05 (edens.zac#351); FE-1 closed as won't-do 2026-09-01; the #294 page-size
debt closed as accepted 2026-09-02. The count lives here rather than in the heading, so correcting it
cannot break the Progress row's link.

The 2026-08-24 batch closed and lives in
[history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend). This
section was re-opened by #258 and re-derived 2026-08-31 by a full pair scan: every endpoint path
literal in `edens.zac`'s `app/lib/api/*.ts` against every `@RequestMapping`/`@*Mapping` pair under
`controller/`. **No frontend call site targets a backend route that no longer exists**, so nothing
here is a live 404. The five below are type drift and one dev-workflow change.

**All five were filed in `edens.zac`** ([#371](https://github.com/themancalledzac/edens.zac/pull/371),
docs-only, **merged 2026-08-31**) as C14, C15, C16, H7 and G6, **verified line by line 2026-09-01**.
Frontend statuses 2026-09-05: FE-2 is C14 (COLD), FE-3 is C16 (COLD), FE-4 is AU2 (startable
frontend work). They stay open here until the frontend acts on them. Filing history:
[history](2026-08-22-backend-cleanup-history.md#cross-repo-section-filing-history-moved-2026-09-01).

- [x] **FE-1** -- the location `images` array could carry GIFs -- CLOSED as won't-do 2026-09-01; the array is being dropped. [history](2026-08-22-backend-cleanup-history.md#fe-1-outcome-moved-2026-09-08)
- [ ] **FE-2: `page` and `size` are silently ignored on the location endpoint.** *(Filed 2026-08-31,
  third run; refs re-verified live 2026-09-01 against `edens.zac` `origin/main` at `f4e8e25`.)*
  **`app/lib/api/collections.ts:157`** builds `/collections/location/${slug}?page=&size=` -- the
  recorded `:150` is the function declaration, not the URL literal. `CollectionControllerProd:124-133`
  reads `collectionPage`, `collectionSize`, `imagePage`, `imageSize` (the four parameters are at
  127-130), and Spring drops the two unknown params. **Live but invisible today because the
  frontend's `PAGINATION.collectionPageSize` is 35 (`app/constants/index.ts:175`) and the backend's
  `collectionSize` default is also 35.** `imageSize` defaults to **50** and is a separate parameter;
  the recorded "both defaults are 35" did not say which two numbers it meant (rule 14). **This is the
  only FE row that was never contingent on BE-2, and the frontend board calls it the cheapest item it
  has (C14).** `getCollectionsByLocation(slug, page,
  size)` therefore accepts two arguments that do nothing, and any caller asking for a second page gets
  page 0. Fix: rename the two query params to `collectionPage` and `collectionSize`.
- [ ] **FE-3: `imageWidth` / `imageHeight` can now be null.** *(Filed 2026-08-31, third run.)*
  [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249) changed the missing-dimension
  default from `0` to `null` (**`ImageProcessingService:464-467`**, re-derived 2026-09-01; the recorded
  `469-472` lands on `setIso`). The wire type was already `Integer`,
  so only the value moved. `app/types/Content.ts:156-157` declares them `number | undefined`. **No
  runtime change** — `getContentDimensions` gates on `if (block.imageWidth && block.imageHeight)` and
  both `0` and `null` are falsy, so both land on the same fallback. Type accuracy only:
  `imageWidth?: number | null`.
- [ ] **FE-4: two admin passkey endpoints exist with no frontend consumer.** *(Filed 2026-08-31, third
  run.)* [#257](https://github.com/themancalledzac/edens.zac.backend/pull/257) added `GET` and `DELETE
  /api/admin/users/{id}/passkeys[/{credentialId}]`. The frontend has `registerPasskey` but no list and
  no deregister, and `/admin/users/[id]` has nowhere to show or revoke an authenticator. **Additive,
  not drift** — an unbuilt feature, not a defect. Note this interacts with **S-28**: the admin UI is
  where a self-lockout would happen.
- [x] **FE-5: admin and edit routes are now auth-gated in dev too.** **CLOSED 2026-09-05.** Frontend
  did it: G6 shipped as [edens.zac#351](https://github.com/themancalledzac/edens.zac/pull/351) (merged 2026-08-31); the frontend `CLAUDE.md`
  localhost-admin rule now says the backend is gated in every profile. Body: [history](2026-08-22-backend-cleanup-history.md#fe-5-outcome-2026-09-05----closed-by-edenszac351).

### Coupling the frontend relies on (recorded 2026-09-05)

- `@Max(200)` on `ImageSearchFilter:27` and `/search` sends exactly `size=200`; a bump to 201 is a frontend 400. #294 also deleted admin's clamp, so an admin passing `size > 200` gets a 400 (no current caller does).
- `ImageSearchFilter`'s default size 50 is what `/location/[slug]` and `/tag/[slug]` rely on; they send no size.
- `AdminMessageView.readAt` is consumed since edens.zac#396. Additive changes are fine; removing or renaming it breaks `/comments`.
- Bare arrays on list endpoints are settled on both boards (2026-08-30).

### Reply to the frontend (2026-09-05)

- CT D2 dismissed: `applyTypeSpecificDefaults` was deleted 2026-07-26 (`6753ca28`); the create default is UNLISTED for every collection at `CollectionProcessingUtil:613`, pinned by `CollectionProcessingUtilTest.toEntity_unlistedDefaultAppliesToAllTypes`.
- U-1 answered: production runs `prod` (see U-1).
- Two traps the frontend repeated are wrong: the `ConstraintViolationException` handler has no Hibernate source (MR 22 #29 row), and the `PARENT` docblocks warn against code that no longer compiles (Wave 4 row).
- The S-29 fix plan is on the S-29 row; on deploy the frontend owes `revalidateTag('search-images')` plus the location and tag tags (frontend D15).
- #22's answer is accepted and the row re-scoped; #30 and FE-5 closed; EM2 and RC3 filed as #33 and #34.

The two closed cross-repo debts this subsection used to hold: [history](2026-08-22-backend-cleanup-history.md#cross-repo-debts-subsection-and-the-22-duplication-note-moved-2026-09-05).

**Backend routes with no frontend consumer, for the record** — unbuilt features, not drift.
**Re-scanned 2026-09-01 in both directions against a live `edens.zac` clone, the first time this has
been possible:** all 110 backend routes (111 today; #300 added one PATCH) matched against every `.ts`/`.tsx` under `app/`, plus
`proxy.ts` and `tests/`.

Already recorded and all confirmed dead: `POST /api/admin/cache/clear`,
`GET /api/admin/metrics/requests`, `POST /api/admin/content/images/ingest`,
`POST /api/admin/content/images/{collectionId}/from-disk`,
`GET /api/admin/content/images/jobs/{jobId}`, `GET /api/read/content/film-metadata`, and FE-4's two
passkey routes.

**Nine more found dead, none previously on this list:** `GET /api/read/content/cameras`,
`GET /api/read/content/lenses`, `GET /api/read/content/people`, `GET`/`PUT /api/read/user/ratings`,
`GET /api/read/collections/{slug}/meta`, `POST /api/admin/content/tags`,
`POST /api/admin/content/people`, `POST /api/admin/content/images/create-collection`.
**`UserRatingOverrideControllerProd` is the interesting one: both of its routes are dead, so the whole
controller and its service path are unreachable from the UI.** That class already has three rows
against it -- MR 23's package move, MR 23's `*Prod` rename and MR 26's missing controller test -- and
this is a fourth reason to deal with it as one piece.

**`GET /api/read/collections/{slug}/download` is NOT dead and has been struck from this list.**
`app/lib/api/downloads.ts:28` builds it as a navigation URL rather than a `fetch`, which is why a
path-literal grep missed it; `downloadCollectionSelectionUrl` at `:41` adds the `imageIds` subset.
`GET /api/read/content/images/{id}/download` is live the same way (`downloads.ts:25`). Do not delete
either.

The `jobs/` and `from-disk` pair is the disk-import question now under "Decisions needed from the
user" (promoted from Appendix C 2026-09-05); this scan settled its frontend half.

**Item #22 is not listed here.** It is backend work the frontend is blocked on, the opposite direction
from this section, and lives under [Bugs filed after the waves closed](#bugs-filed-after-the-waves-closed-2026-08-29).
Its 2026-08-31 duplication into this section: [history](2026-08-22-backend-cleanup-history.md#cross-repo-debts-subsection-and-the-22-duplication-note-moved-2026-09-05).

## Open security findings

Consolidated 2026-08-24 by the full-board review; re-attacked as a merged set 2026-08-25, again
2026-08-29 (adversarial -- 0 HIGH, 0 MEDIUM), again 2026-08-31 (third run) and again 2026-09-01
(tenth run). **Twenty-seven closed** (S-1..S-24, S-26, S-27, S-28; **S-25 was never assigned** and
appears nowhere in either file, which is the gap behind the "28 findings" this board has quoted):
one ledger line each below, with bodies and outcomes in
[history](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29).
Per-path limiter mapping -- which limiter covers which route -- sits in history's
[S-17 outcome](2026-08-22-backend-cleanup-history.md#s-17-outcome-2026-08-28----not-as-specified-and-two-failures-of-the-same-kind).

**The unsettled questions no longer live in this section.** They moved 2026-08-31 to
[Unsettled security questions](#unsettled-security-questions) with their own gate, because four
open checkboxes sat here while this section's row and classification both said "empty" -- the
rule-36 gate greps `^- \[ \] \*\*S-` and none of them opened that way.

### Open

**Six.** S-29, S-30 and S-31 were filed 2026-09-01; S-32, S-33 and S-34 were filed 2026-09-05 by
the eleventh-run review, which proved S-29 and each sibling with an anonymous GET against a
Testcontainers boot (slice A; write-up in [history](2026-08-22-backend-cleanup-history.md#full-board-review----run-2026-09-05-eleventh-run)). Every one of the twenty-seven
closed findings lives in auth, session, role-membership, share or actuator code;
`/api/read/content/**` and `/api/read/collections/**` were never attacked as an authorization
surface. Gate: `grep -c '^- \[ \] \*\*S-'` = **2** at [#313](https://github.com/themancalledzac/edens.zac.backend/pull/313). **This is the third cell rule 36 does not name; move it with the other two.**

- [x] **S-29** (**HIGH**) anonymous `GET /api/read/content/images/search` returned every image, private client galleries included -- [#309](https://github.com/themancalledzac/edens.zac.backend/pull/309), 2026-09-05, with S-32 and S-34. A `publicOnly` flag on `ImageSearchRequest`, set from the route and never bindable, switches one `EXISTS` on a LISTED password-free membership. **Frontend owes `revalidateTag('search-images')` plus the location and tag tags** (D15). [Write-up](2026-08-22-backend-cleanup-history.md#s-29-s-32-and-s-34-outcome----2026-09-05).
- [x] **S-32** (HIGH) the location page's orphan strip returned private-gallery images on a CDN-cacheable route -- [#309](https://github.com/themancalledzac/edens.zac.backend/pull/309), 2026-09-05. **The same predicate also drops content held by no collection at all, which the row did not price** -- the public location page will shrink. **Rider still open as S-35.**
- [x] **S-33** (MED) every image on a public collection read listed every collection it belongs to, unlisted and password-protected ones included -- [#312](https://github.com/themancalledzac/edens.zac.backend/pull/312), 2026-09-06. `populateCollectionsOnContent` took the `listedOnly` flag its two neighbours already carry, so the compiler made all three call sites declare themselves: `true` at `CollectionService:160`, `false` at `CollectionService:1498` and `CollectionProcessingUtil:358`. **The row's corrected premise held -- the third call site was real, and it is the reason the flag was the right shape.** `filterNonListedChildCollections` was left alone as the row required.
- [x] **S-34** (MED) the tag view returned images from a LISTED password-protected gallery -- [#309](https://github.com/themancalledzac/edens.zac.backend/pull/309), 2026-09-05. One unconditional `AND col.gallery_password IS NULL`; both callers serve the anonymous view. **Rider still open as S-36.**
- [x] **S-35** (MED) `GET /api/read/content/locations` listed a location whose only content is a private gallery -- [#313](https://github.com/themancalledzac/edens.zac.backend/pull/313), 2026-09-06. The orphan expression is now one constant used by both the projection and the `HAVING`, so the two copies cannot drift again. **The row's prescribed fix was half right and the other half would have made it worse.** The positive `PUBLIC_COLLECTION_MEMBERSHIP` requirement is what closes it; a `gallery_password` term on the `NOT EXISTS` -- the row's other reading -- would have made an image held at the location by a LISTED protected gallery *start* counting as an orphan. A test now guards against someone making that change later. `collection_count` was checked and correctly has no password term: a LISTED protected gallery is meant to be discoverable as a tile.
- [x] **S-36** (LOW) a signed-in USER could save and re-read an image from a LISTED password-protected gallery -- [#311](https://github.com/themancalledzac/edens.zac.backend/pull/311), 2026-09-06. `AND col.gallery_password IS NULL` on the LISTED arm of `isImageVisibleToUser` and `findSavedImagesByUserId`. **The role-grant arm was left open deliberately and two tests now pin it that way:** `role_collection` grants are visibility-agnostic (`RoleGrantPropagationService.setGrant`), so they are how a named client reaches their own gated gallery -- filtering that arm would revoke access from the people the grant was issued to.
- [ ] **S-30** (LOW) **`GET /api/read/content/people` lists every row in `users`, not every tagged
  person.** `MetadataService.getAllPeople` (`:112`) calls
  `PersonRepository.findAllByOrderByPersonNameAsc` (`:49-52`), which is literally
  `SELECT id, name, created_at FROM users ORDER BY name ASC` -- no `status` predicate and no
  "is tagged in anything" predicate. Since V35 merged people and accounts into one table, this
  returns the id and display name of every account: admins, collaborators, clients, INVITED
  accounts that never onboarded, DISABLED accounts, alongside the tag-only PERSON rows the route
  was built for. The route (`ContentControllerProd.getAllPeople:65`) is anonymous under
  `permitAll` and is in `CacheControlInterceptor.PUBLIC_ROUTES`, so it is shared-cacheable too.
  Email is not exposed; the account roster by name is, plus each `users.id` -- the same id that is
  the `{id}` path variable on `/api/admin/users/**` and the `personIds` filter on S-29. **It is
  also a functional bug**: an account never tagged in a photo appears in the tag-filter dropdown
  and returns zero results.

  **Mutation:** seed a DISABLED account with no `collection_people` and no `content_image_people`
  row, assert it is absent from the response, then drop the new `WHERE` clause and watch it redden.
  A test that only asserts a tagged PERSON is present stays green and does not count.
- [ ] **S-31** (LOW) **a share opt-in is checked when it is added and never again.**
  `UserShareControllerProd.addCollection` (`:167-178`) gates the opt-in on
  `collectionAccessService.canView(principal, collectionId)`. That is the only check.
  `ShareLinkRepository.isCollectionInScope` (`:170-194`) and `findScopeCollectionIds` (`:145-162`)
  both resolve scope as `collection_people` UNION `share_link_collection`, with no join back to
  `role_member` / `role_collection`. So a revoked role grant leaves the collection's tile on the
  link. The removal side already anticipates revocation: `removeCollection`'s docblock says the
  delete is "deliberately NOT gated on the owner's current grant".

  **Why LOW.** Every consumer of the flyby's GENERAL was traced and it is inert -- each screens
  with `isRealUser` or a higher level first. The exposure is the tile metadata on the share page,
  not content access. **This may close on S-14's reasoning rather than be patched**: S-14 answered
  the neighbouring question with "answered, not patched: no second gate". It has not been asked,
  which is the only reason it is a row.

  **Mutation if patched:** grant, opt in, revoke the grant, assert the collection is absent from
  `findScopeCollectionIds` and false from `isCollectionInScope`, then drop the new `role_member`
  join from the `share_link_collection` arm of both queries.

*(S-28's full body sat under this heading, ticked, from the sixth close-out until 2026-09-01, with
its outcome paragraph orphaned under the Closed heading and attached to no bullet. Both are now one
ledger line.)*

### Closed, one ledger line each

All 27 lines moved to [history](2026-08-22-backend-cleanup-history.md#closed-one-ledger-line-each-moved-2026-09-06)
2026-09-06 by the #309 close-out. The index survives on the tracker: the three security-count
cells name every closed finding by number, and the count gate reads the open rows only.

### What the closed set is worth carrying forward

Three lessons (the gate, not the wording; the blocked-item table shape; S-14's answer to a slightly
different question), moved 2026-09-05 to [history](2026-08-22-backend-cleanup-history.md#what-the-closed-security-set-is-worth-carrying-forward-long-form-moved-2026-09-01). Tests that cannot
fail closed 2026-08-30, all six:
[history](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker).

### Verified sound, do not re-open

Attacked 2026-08-24, again 2026-08-29, and again 2026-08-31 (third run); held every time. Index
only -- the full reasoning lives
in the [history file](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29):

- **S-16's reachability claim HOLDS -- re-tested 2026-08-31 with #257's endpoints in the tree. Do
  not re-derive this.** #253 shipped one gate instead of the two the item specified, resting on the
  claim that `resolveByRawToken` is the only way a token becomes a link. Four checks -- producers of
  `AuthPrincipal.shareId`, callers of `resolveByRawToken`, consumers of a shareId, and the bypass
  looked for and not found -- all came back clean, and #257 does not touch the share path at all.
  The four checks in full:
  [history](2026-08-22-backend-cleanup-history.md#s-16s-reachability-claim-the-four-checks-moved-2026-09-01).

- **`addMember`'s `<> 'PERSON'` denylist: do not tighten it to an ACTIVE allowlist** -- S-1's
  shipping falsified the live-grant argument for one. S-22 pins the rule and names the predicate
  without changing membership.
- **The #189 `/api/read/user/**` matcher** and **the flyby-principal invariant** hold as recorded.
- **Do not unify `mayHoldSession` and `mayAcceptInvite`** -- re-verified against code 2026-08-29:
  `finishLogin` reads status fresh through the predicate, and the only non-breaking unification
  direction widens `mayHoldSession` to admit INVITED, under which the passkey door is a live hole.
- **Nothing lets a session or passkey outlive a status change today** -- `resolve` re-reads status
  and `isAdmin` on every request; deactivation sweeps then backstops; no path hard-deletes an
  ACTIVE account. (The resolve-slides-the-window-first latent is **U-4** under "Unsettled security questions".)
- **`ShareEmailLimiter` keying and placement are correct** -- session-derived `userId`, checked
  before the token lookup, disjoint key space from the other four limiters.
- **The S-set mutation pins are resistant** -- S-20's enum pin, S-3's #235 parameterization plus
  literal pin, S-17's 429 pin with the refill-observability fix, S-18's containsExactly both ways.

## Unsettled security questions

Open security questions that are not numbered findings. **Promoted out of "Open security findings"
2026-08-31 (third run)**, where the first four sat as plain checkboxes that the rule-36 gate
(`grep -c '^- \[ \] \*\*S-'`) could not see, while that section's row and its classification both
said "0 open -- the section is EMPTY". U-5 through U-8 are worse: they existed only as prose inside
closed `[x]` ledger lines, so no gate anywhere could see them and one of them (U-6) is a row the
board explicitly wrote "needs its own item" about and then never filed.

Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **3**, measured on the review branch `docs/eleventh-run-review` (from `afa39d6f`, 2026-09-05); re-run on `main` after merge. Run it and update the
section-table row together. Open: U-2 (COLD, answerable in-tree), U-3 (BLOCKED on the user), U-7
(COLD). U-1 was answered and U-8 closed as moot on 2026-09-05. Stamp history, including the two runs
it read 7: [history](2026-08-22-backend-cleanup-history.md#unsettled-security-questions-row-prior-state-moved-2026-09-01).

- [x] **U-1** -- does prod run the `prod` profile? **ANSWERED 2026-09-04: yes**, by one anonymous GET against the origin. [history](2026-08-22-backend-cleanup-history.md#u-1-outcome-moved-2026-09-08)
- [ ] **U-2 -- whether Tomcat surfaces `Transfer-Encoding` to `getHeader()`.** S-5's entire fix depends on
  it, and its only test uses `MockHttpServletRequest`, which returns whatever the test put in. If
  Tomcat consumes the header while installing the chunked input filter, the branch never fires and
  the bypass is still open. Settle with an integration test that POSTs a real chunked body to a
  booted server and asserts 411 -- `ActuatorExposureEndToEndTest`
  (`src/test/java/edens/zac/portfolio/backend/config/ActuatorExposureEndToEndTest.java`) already has
  the shape. **CLASSIFIED 2026-09-01 (tenth run): COLD, and it does NOT belong in the blocked pile
  next to U-1.** `RateLimitFilter:112` is
  `if (declaredBodyBytes < 0 && request.getHeader("Transfer-Encoding") != null)` and its only
  coverage is `RateLimitFilterTest:91` and `:121`, both `MockHttpServletRequest`, both returning
  whatever the test put in -- exactly as this item says. **It is answerable today, in-tree, with no
  credentials and no host access.** It had no bucket in the classification section at all until now.
- [ ] **U-3 -- `ACCESS_TOKEN_SECRET` has no rotation story.** It keys gallery HMAC tokens, gallery password
  fingerprints, and now share-token confidentiality. Rotating it makes every stored ciphertext
  unreadable, so the key now guarding at-rest confidentiality is one nobody can rotate -- a suspected
  compromise cannot be remediated without every owner resetting their link. Related and worth stating
  plainly: the deployment docs put the `.env` and the nightly `pg_dump` backups in the same home
  directory on the same host, so the encryption buys real protection against an S3-only leak and
  roughly one `cat` against host compromise.

  **Partly addressed 2026-08-25 by #222, and worth being precise about which part.** `.env.example`
  now states the rotation cost at the point of configuration -- rotating invalidates every stored
  share link and every live gallery unlock cookie. That makes the cost visible; it does not make the
  key rotatable. The question stays open, and it is now the last unaddressed half of S-11.

  **CLASSIFIED 2026-09-01 (tenth run): BLOCKED (user), and it needs a real judgement rather than a
  word.** The secret has three consumers, re-verified this pass: `TokenCipher:45` (the AES-GCM key
  for share-token confidentiality), `ClientGalleryAuthService:48` (gallery password fingerprints and
  HMAC tokens) and `ProdSecretGuard:33` (the boot check). Rotating it makes every stored
  `share_link.token_cipher` undecryptable and invalidates every live gallery unlock cookie. Nothing
  in the tree makes the key rotatable and no amount of reading it will settle this. It had no bucket
  in the classification section until now. `.env.example:27-28` cites `docs/runbooks/secret-rotation.md`,
  which does not exist (`ls docs/runbooks` fails); delete the sentence or write the runbook when U-3 is
  decided.

  **This bullet is also where S-11's missing fact was sitting.** It listed all three uses of the
  secret while S-11's own severity paragraph named only `TokenCipher`. See S-11's outcome in the
  [history file](2026-08-22-backend-cleanup-history.md#s-11-outcome-2026-08-25----the-guard-clause-and-a-fact-the-board-already-had).
- [x] **U-4 -- `SessionService.resolve` slid the session window before reading status** -- **DONE**
  ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270), 2026-08-31).
  [Write-up](2026-08-22-backend-cleanup-history.md#u-4--the-slide-moved-below-the-status-check-270).
- [x] **U-5 -- `ClientIp`'s javadoc called the header's presence "the trust signal"** -- **DONE**
  ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274), 2026-08-31). One docblock sentence.
  [Write-up](2026-08-22-backend-cleanup-history.md#u-5--the-trust-signal-sentence-274).
- [x] **U-6 -- whether `addCollection` should be admin-gated at all** -- **ANSWERED and DONE**
  ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), 2026-08-31): keep it and document the routing.
  [Write-up](2026-08-22-backend-cleanup-history.md#u-6--a-routing-question-whose-first-answer-rested-on-a-false-premise-278).
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed
  2026-08-25.** The deletion it recommends had no checkbox anywhere on the board until 2026-09-01.
  It is the row below.
- [ ] **Delete `RoleRepository.canView` and `isClient`.** *(Filed 2026-09-01, tenth run. The finding
  had been written down since 2026-08-25 and lived only inside the ticked bullet above, where no
  gate could see it.)* Both methods still exist (`RoleRepository.java:419` and `:439`) and still have
  **zero `src/main` callers** -- every `canView` / `isClient` hit in `src/main` is
  `collectionAccessService.*` -- re-verified 2026-09-01 on `main` at `43c6f2c6`. Six integration test
  classes assert through them, so deleting means rewriting those assertions against
  `CollectionAccessService.effectiveLevel`. **The names are the hazard**: they read like the live
  authorization check and are not. This row opens `**Delete` rather than `**U-` deliberately -- it is
  work, not a question, and it must not move the U- gate.
- [x] **U-7 -- delete the now-redundant actuator exclude list.** **DONE 2026-09-08**
  ([#316](https://github.com/themancalledzac/edens.zac.backend/pull/316)). `ProdActuatorExposureGuard`
  requires the resolved include to equal exactly `{health}` and throws from `@PostConstruct`
  otherwise, so the twelve names were unreachable under `prod`; U-1 settled 2026-09-04 that prod runs
  `prod`. Deleted `application.properties:67`, two of four `ActuatorExposureTest` cases, and
  `ActuatorExposureEndToEndTest` whole. `MUST_BE_EXCLUDED` went with them -- the row had not noticed
  it was shared. `ProdActuatorExposureGuardTest` was left alone and its cost priced on the row.
  **Working rule 34 amended in the same close-out, not in #316** -- see rule 58 below.
  [Write-up](2026-08-22-backend-cleanup-history.md#u-7-outcome-2026-09-08----the-row-said-two-files-the-code-said-two-and-a-half).
- [x] **U-8 -- whether S-18's criterion-incompleteness is now moot.** **CLOSED as moot 2026-09-05**:
  the guard refuses any include other than `health` and U-1 shows it is live. Closes with U-7.
  Body: [history](2026-08-22-backend-cleanup-history.md#u-8-outcome-2026-09-05----moot).

## Working rules

Index only, rewritten 2026-09-05. The full text of every rule, with evidence and amendments, is in
the history file: rules 1-39 under
[Working rules -- original narratives](2026-08-22-backend-cleanup-history.md#working-rules--original-narratives-moved-2026-08-29)
and rules 40-55 under [Working rules 40-55: full text](2026-08-22-backend-cleanup-history.md#working-rules-40-55-full-text-moved-from-the-board-2026-09-05).
Numbering is stable; cite rules by number. Rule 37 supersedes 6 and 12; rule 5 absorbed 4; rule
50 replaced rule 46's command. `scripts/board-gates.sh` checks rules 36, 37, 38, 42 and 53
mechanically (rule 55).

| # | Rule | Incident |
|---|---|---|
| 1 | A property default in `application.properties` is probably dead; `docker-compose.yml` overrides it | MR 9, bug #9 |
| 2 | `src/test/resources/application.properties` shadows the shipped file on the test classpath | MR 9 era |
| 3 | `GlobalExceptionHandler` maps IAE and ISE to 400; only a bare `RuntimeException` reaches 500 | wave 2 |
| 4 | RETIRED 2026-08-29, absorbed by rule 5 | -- |
| 5 | A list-of-line-numbers item is dead; re-derive by name | 2026-08-24, 38 of ~130 refs exact |
| 6 | SUPERSEDED by rule 37 (leave trailing comments alone) | MR 12 |
| 7 | A stale comment is not automatically a bug report | MR 12, 349 comments -> 1 bug |
| 8 | A `P:` note decays like a `D:` line number, and an item's own landing can falsify its side-arguments | MR 13a; S-1 |
| 9 | Commit with explicit paths, never `git add -A` | MR 12c swept a doc into #180 |
| 10 | Measure a comment MR in words, not lines | MR 13b |
| 11 | Outcome write-ups go to history, not the tracker | tracker at 1,729 lines, 76% closed detail |
| 12 | SUPERSEDED by rule 37 (promote a fact, keep a warning) | MR 14 |
| 13 | A guardrail decays; a re-derivation is not self-verifying | MR 15 #2 |
| 14 | Re-derive duplication by code shape, not helper name | MR 15 #6 |
| 15 | A test that cannot fail is worse than none; prove it by mutation | S-3/S-4, #195/#196 |
| 16 | Count callers of the operation, not the endpoint the item named | S-1 found S-7 |
| 17 | Put the guard in the statement, not the caller precondition | S-2 |
| 18 | An allowlist's form and membership are two claims | S-7 |
| 19 | Controllers map to status codes; no `//` in either | S-7/S-9 review |
| 20 | Admin means owner; does not generalize to account status | user ruling 2026-08-24 |
| 21 | A premise is evidence; a prescribed fix is a hypothesis | S-5..S-8 |
| 22 | A comment claiming a protection is a claim to check | `CollectionControllerProdTest` |
| 23 | "My merge did not move this ref" is not "this ref is correct" | #211 close-out |
| 24 | A specified fix can be impossible; check its inputs exist | `share/email` |
| 25 | Test the framework guarantee, not the config string | #214 |
| 26 | A fresher line range is not de-positionalizing | MR 24 |
| 27 | An item specified while its own question is open needs adjusting | MR 19 #14 |
| 28 | A stacked PR whose base merges first strands the work; merged is not on `main` | #219; #304/#305 |
| 29 | A cost report enumerates every consumer; grep this board for the symbol first | S-10/S-11 |
| 30 | A state-keyed sweep guards one transition direction | S-12/S-13 |
| 31 | Record a count's command exactly as run; state N raw / M code | S-20 |
| 32 | A reddened mutant is not evidence until you know why | S-13 |
| 33 | A test deriving cases from the thing under test cannot see it widen | #230 |
| 34 | An allowlist is not defence in depth if the attacker overwrites it -- **amended 2026-09-08: superseded by a boot guard that fails closed, which the rule itself named as the follow-up that would make the allowlist deletable. Do not read 34 as forbidding the deletion (U-7)** | S-18; amended #316 |
| 35 | A green unit run is not evidence a wiring change works | #233 |
| 36 | The security-count cells drift; run the escaped gate, edit them in one edit. **There are three, not two** | #231; five repeats since; miscount found #309 |
| 37 | Never write inline comments; supersedes 6 and 12 | user, 2026-08-28 |
| 38 | A close-out is two files | #229/#231/#234 |
| 39 | A push after merge goes nowhere; check the PR is OPEN | #232 (R-1); again 2026-08-31 |
| 40 | A named predicate earns its name from call sites | S-22 review |
| 41 | Stale surefire reports read like a passing run | 2026-08-31, twice |
| 42 | The comment count is a checksum; reconcile the delta against your diff | 2026-08-31 |
| 43 | A query-layer item has not priced the DTO it feeds | bug #19 |
| 44 | Diff from the commit the number was recorded at, not the latest merge | #266 / `41d928b4` |
| 45 | A "one call" fix is a claim about the diff, not the tests | S-26, #265 |
| 46 | Name which metric a comment count moved; re-run the absolute. Its command is superseded by rule 50 | #271 |
| 47 | Rule 37 applies to the touched region; file a bulk concentration as its own item | #280/#282/#283/#285 |
| 48 | Extraction estimates forget the destination file | MR 18 #11, #9 |
| 49 | Check for an existing integration test before pricing coverage | #12b |
| 50 | `grep -rn` undercounts the test side by 3; use `git grep -c` | `b02520b1` |
| 51 | `inOrder.verify` and plain `verify` differ on cardinality; settle by mutation | #296 |
| 52 | The `@Validated` proxy gap is method parameters only | #297 |
| 53 | The tracker must not grow in an MR; `wc -l` delta <= 0 | #299; broken by #301 |
| 54 | A trap, blocker or do-not-sweep guardrail needs a command beside it, the way a count does | 2026-09-04/05, four of six handoff traps wrong |
| 55 | Run `scripts/board-gates.sh` before opening a close-out PR and paste its output into the PR body | 2026-09-05, slice E |
| 55b | Rule 55's close-out PR is the rule-58 docs MR. A code MR has no counts to gate | twelfth run |
| 56 | A visibility fix on a membership join also drops rows with no membership; that is a behaviour change, price it | S-32, #309 |
| 57 | Never move board rows by line range; slice on the row markers or you sweep the neighbour | #309 nearly lost S-33 |
| 58 | A code MR touches `src` only. Tick the rows and restamp every count in ONE docs MR at the end of the run | twelfth run: four MRs from one base, three rebases, all of them the tracker and none of them `src` |


---

# Wave 4 — Comments and docs

Closed except the stale-docblock items below. The wave rule, the measured preamble, MR 14's
disposition and the retro moved to the history file 2026-08-29
([Wave 4 detail](2026-08-22-backend-cleanup-history.md#wave-4-and-wave-5-tracker-detail-moved-2026-08-29); outcomes at
[Wave 4](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete),
[retro](2026-08-22-backend-cleanup-history.md#wave-4-retro--measured-in-words-2026-08-23) and
[MR 14](2026-08-22-backend-cleanup-history.md#mr-14-outcome-2026-08-23)). The wave removed 500 in-method comments for -1,026
words across seven MRs (#177-#187); its old "567 at indent >= 4" start-of-wave measure is
superseded by the 2026-08-29 re-measure in the Progress estimate table.

### Still open from MR 14 — stale docblocks

All rows closed; moved to [history](2026-08-22-backend-cleanup-history.md#still-open-from-mr-14--stale-docblocks-all-closed-moved-2026-09-06) 2026-09-06 by the #309
close-out. Nothing here is open.

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

All rows closed; moved to [history](2026-08-22-backend-cleanup-history.md#mr-15--cross-cutting-all-closed-moved-2026-09-06) 2026-09-06 by the #309
close-out. Nothing here is open.

## MR 16 — Infrastructure classes

All rows closed; moved to [history](2026-08-22-backend-cleanup-history.md#mr-16--infrastructure-classes-all-closed-moved-2026-09-06) 2026-09-06 by the #309
close-out. Nothing here is open.

## MR 17 — Controllers

All rows closed; moved to [history](2026-08-22-backend-cleanup-history.md#mr-17--controllers-all-closed-moved-2026-09-06)
2026-09-06 by the #309 close-out. Nothing here is open.

## MR 18 — Services

- [x] #9. The from-disk and ingest background loops were ~70 lines of copy-paste -- **DONE**
  ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279), 2026-08-31), at half the advertised saving.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-9--the-shared-upload-loop-at-half-the-advertised-saving-279).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-9-tracker-body-moved-2026-09-01).
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized`. **Find both by name.** `ContentService.updateGif` spans **550-639**; the three helpers sit at `ContentMutationUtil` **176** (Tags), **198** (People), **220** (Locations), re-derived 2026-09-04 on `main` at `afa39d6f`. **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, declared on subclasses rather than `ContentEntity`, so the fix needs a return-the-set signature, not a retype, and it weakens `ContentServiceTest.updateGif_persistsPeopleAndLocations` (`ContentServiceTest.java:144`). **One thing that makes it cheaper than described**: `updateGif` never calls those setters at all -- it computes the merged set and persists ids through `saveContentTags`, `saveContentPeople` and `saveContentLocations`, so a return-the-set helper serves the gif path directly and only the image call sites gain a `setX` line. Realistic ~180, not ~40, dominated by the test rewrite. **COLD and unworked for four close-outs, and absent from every run's `Next:` since the sixth**, which makes it invisible to the leak detector. Either work it or take it off the COLD list with a reason. Ref drift chain: [history](2026-08-22-backend-cleanup-history.md#mr-18-10-ref-drift-chain-moved-2026-09-01).
- [x] #11. Four near-identical BFS walks -- **DONE** ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288), 2026-09-01, eighth
  run). Five walks, one visitor; the estimate forgot the new file.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-11--five-walks-one-visitor-and-an-estimate-that-forgot-the-new-file-288).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-11-tracker-body-moved-2026-09-01).
- [x] #12. `nextOrderIndex` logic -- **DONE** ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284), 2026-08-31). "Five places"
  was premise-corrected to three that compute the index; the item was wrong twice, in opposite
  directions. Two copies were deliberately left and closed separately as #12b.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-12--the-item-was-wrong-twice-in-opposite-directions-284).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-18-12-tracker-body-moved-2026-09-01).
- [x] **`CollectionRepositoryTest`'s 21 inline comments and `CollectionRepository`'s 12** --
  **DONE** ([#295](https://github.com/themancalledzac/edens.zac.backend/pull/295), 2026-09-01, ninth run). 33 -> 0.
  [Write-up](2026-08-22-backend-cleanup-history.md#collectionrepository-comment-concentration-295).
  Body: [history](2026-08-22-backend-cleanup-history.md#collectionrepositorytests-21-comments-tracker-body-moved-2026-09-01).
- [x] **#12b. The two `nextOrderIndex` copies #284 deliberately left in `CollectionService`** --
  **DONE** ([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291), 2026-09-01, eighth run), at a coverage price the item had wrong.
  [Write-up](2026-08-22-backend-cleanup-history.md#12b--the-last-two-nextorderindex-copies-and-a-coverage-price-that-was-wrong-291).
  Body: [history](2026-08-22-backend-cleanup-history.md#12b-tracker-body-moved-2026-09-01).
- [ ] #13. **Re-scoped 2026-09-01 (tenth-run review). The dedupe half is confirmed dead; the sort half is not the finding the board recorded, and what is left is BLOCKED on a question about the production database.**

  **Dedupe half -- DROP IT, both grounds verified at `43c6f2c6`.** Nine `Records` construction sites, 4 Tag and 5 Location; **the count holds exactly**. Tag at `ContentModelConverter:328`, `MetadataService:431`, `SyntheticCollectionResolver:152`, `ContentService:970`; Location at `ContentModelConverter:665`, `MetadataService:439`, `CollectionService:265` and `:267`, `CollectionProcessingUtil:160`. Declarations `convertTagsToModels:323`, `toTagModel:430`, `toLocationModel:438`, all exact. **Three refs drifted, and all three sit in the group the eighth close-out had flagged under rule 23 as not re-derived** -- `ContentModelConverter` Location 657 -> **665**, `SyntheticCollectionResolver` 150 -> **152**, `ContentService` 986 -> **970**. The board's own hedge was correct. Net ~0 lines, because every copy and every replacement is one line, and **the layering flip is verified rather than asserted**: `Records.java` imports only `JsonProperty`, `types.FilmFormat` and `LocalDate`, and no file anywhere under `model/` imports from `entity/`, so a static `from(entity)` factory would be the repo's first `model -> entity` import. Closed on the merits.

  **Sort half -- the recorded finding is a category error, and two of its three members are wrong.** The old text said `MetadataService`, `SyntheticCollectionResolver` and `ContentService` "do not sort". `MetadataService.getAllTags` (`:49`) and `getAllLocations` (`:368`) are ordered **in SQL** by `TagRepository.findAllByOrderByTagNameAsc` and `LocationRepository.findAllByOrderByLocationNameAsc`, both `ORDER BY <name> ASC`. `SyntheticCollectionResolver.toTagRecords` (`:148`) gets its list from `TagRepository.findTagsByCollectionIds`, which ends `ORDER BY t.tag_name ASC` (`:231`). `LocationRepository.findLocationsByContentIds` (`:180`) and `findLocationsByCollectionIds` (`:262`) order by name too, so the Java sorts in `ContentModelConverter.resolveLocations` and `CollectionProcessingUtil` are re-sorting already-sorted rows. **`toTagModel` and `toLocationModel` are single-entity mappers -- they map one row and cannot sort. Naming them as the unsorted producers was the category error.** **No endpoint returns an unordered tag or location list.** The one genuinely unordered site is `ContentService.buildUpdateResponse` (`:970`), which maps five `Set`s through `mapOrNull` over `HashSet` iteration order -- and it is the "what did we just create" echo on a mutation response, not a listing.

  **The collation question is ANSWERED and this item is UNBLOCKED.** Production sorts as **`C`**;
  re-verified 2026-09-04 on the same image tag (`postgres:16-alpine`, booted with the
  `scripts/ec2-postgres/docker-compose.yml` environment and no `POSTGRES_INITDB_ARGS`):
  `datlocprovider = c`, and `('b'),('A'),('a'),('B') ORDER BY name` gives `A,B,a,b`. **Do not answer
  this by reading `datcollate`**; it says `en_US.utf8` on both alpine and Debian. Two facts a fixer
  needs: `COLLATE "en_US.utf8"` does not exist on the alpine image and errors at runtime on prod while
  passing in CI's Debian `services.postgres` block (`ci-cd.yml:48-60`, which nothing uses); `und-x-icu`
  does exist. Testcontainers already runs alpine (`AbstractPostgresIntegrationTest:29`), so an
  ordering test is prod-faithful. Method: [history](2026-08-22-backend-cleanup-history.md#the-production-collation-answered-2026-09-02).

  **Sites, re-derived 2026-09-08 on `main` at `2bc62a20`.** Six SQL `ORDER BY <name>` sites reach a
  user without a Java re-sort: `TagRepository:58` and `:231`, `PersonRepository:50`,
  `LocationRepository:95` and `:366`, `CollectionPeopleRepository:82`. **Two drifted since 2026-09-04
  and are corrected here**: `LocationRepository:57 -> :95` (`findAllByOrderByLocationNameAsc`, decl
  `:93`) and `:337 -> :366` (`findLocationsWithVisibleContent`, decl `:346`). The other four hold
  exactly. **The count of six is correct and was re-checked, not assumed** -- `grep -n "ORDER BY"
  LocationRepository.java` returns **five** sites in that file alone (`:95`, `:218`, `:282`, `:300`,
  `:366`), and the three excluded are excluded for a reason: `:218` and `:300` are re-sorted in Java,
  and `:282` (`findCollectionLocations`) has one caller, `CollectionProcessingUtil:712`, which drops
  its result into a `HashSet` on an update path and never returns it as an ordered list. That is three public list endpoints (`/api/read/content/tags`,
  `/people`, `/locations`) plus `CollectionModel.people`, all `C`-ordered. Four Java sites use
  `compareToIgnoreCase`: `ContentModelConverter:329`, `:346`, `:666` and `CollectionProcessingUtil:161`
  (image chips and `CollectionModel.locations`, interleaved). **DIRECTION ANSWERED 2026-09-08 by the user: case-insensitive SQL.**
  `ORDER BY lower(...)` at the six sites -- 6 one-token edits plus ~4 Testcontainers order tests
  (~35 lines; `MetadataServiceTest` mocks the repository and cannot see it). This makes SQL agree
  with the four Java `compareToIgnoreCase` sites and with the frontend's `sortByName.ts:10` instead
  of fighting them. **Rejected: matching Java to `C`** (4 comparator lines, but ships uppercase-first
  lists nobody approved, and the frontend's admin pickers re-sort case-insensitively anyway, so the
  backend order would be overridden there and inconsistent everywhere else). **Rejected: closing the
  row.** No longer BLOCKED -- this is the fourteenth run's item 1. The dedupe half stays closed on
  the grounds above.

## MR 19 — Query efficiency and data layer

- [x] #14. `convertEntityToModel` loaded the same content row twice. **DONE** ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218), 2026-08-25) -- taught working rule 27. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-14-outcome-2026-08-25).
- [x] #15. `getUpdateCollectionData` fetched the collection row twice. **DONE** ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280), 2026-08-31). [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-15--the-projection-and-the-fixture-churn-nobody-predicted-280), [body](2026-08-22-backend-cleanup-history.md#mr-19-15-tracker-body-moved-2026-09-01).
- [x] #16. `findCurrentContentCollections` N+1, 201 queries to 1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) -- **the suggested fix was the bug**. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).
- [ ] #17. Smaller items. **(a) and (d) SHIPPED 2026-09-02** ([#305](https://github.com/themancalledzac/edens.zac.backend/pull/305)): token resolution is one `findLiveInvite` (`redeem` still settles single-use with the conditional `markUsedIfUnused` update at `UserInviteService:265-267`; the extraction deliberately left it in `redeem`. **Do not move it into `findLiveInvite`**; `UserInviteServiceIntegrationTest.redeemIsAtomicSingleUse` reddens if the gate goes); `uploadToS3`/`streamFileToS3` share `buildS3Key` plus `putAndBuildUrl`. **Net -4 lines, not the -14 estimated** -- the estimate assumed no docblocks on the new helpers (**rule 48**: the win is one copy of the key-and-URL shape, not the delta). **The row's old "members (a) and (d) have zero `src/test` references" was wrong and [#302](https://github.com/themancalledzac/edens.zac.backend/pull/302) had already corrected it**: only (d) is zero, both its methods being private. (a)'s `validate`/`redeem` have **16 call sites in 2 files** -- `UserInviteServiceIntegrationTest` 13, `InviteControllerTest` 3 (#302's "five files" counted files mentioning `UserInviteService`, not call sites). Nothing had to change because the extraction sits behind both unchanged public signatures, which is coverage on the extraction rather than a cost. **Three left, all to be found by name:**
  - **(b) pagination normalization re-inlined in `CollectionService.getCollectionWithPagination`** --
    find `int normalizedPage`, three lines, and **do not record a number for it**. **TRAP, verified
    2026-09-02: do not fold this into `PaginationUtil.normalizeCollectionPageable`.** That method
    defaults to `default_collection_per_page` = **10**; this site uses `DEFAULT_PAGE_SIZE` =
    `default_content_per_page` = **30**. The method whose name says "collection" is the wrong one for a
    collection's *content* page, so the obvious fold silently cuts every collection page from 30 items
    to 10. `normalizeContentPageable` or `normalizeSize(size, DEFAULT_PAGE_SIZE)` are the honest
    targets, and the site also needs the raw `offset`, which no `Pageable` helper returns.
  - **(c)** `CollectionProcessingUtil.toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other.
  - **(e)** the EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml`, `buildShareLinkHtml`; the third was added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional; 176 lines across three private builders, 0 test refs by `git grep -c 'buildHtml\|buildInviteHtml\|buildShareLinkHtml' -- src/test`; by rule 48 expect -10 to -30 net once the shared skeleton and its docblock are counted). Ref drift chain: [history](2026-08-22-backend-cleanup-history.md#mr-19-17-ref-drift-chain-moved-2026-09-01).

  **RE-DERIVED 2026-09-04 on `main` at `afa39d6f`.** `validate` **158**, `redeem` **259**, `redeem`'s
  internal caller at **213**, `findLiveInvite` **170** (shipped in #305); `toEntity` **595** with
  `return applyPaginationDefaults(entity)` at **617**, `applyPaginationDefaults` **953**; `uploadToS3`
  **715** and `streamFileToS3` **728**, callers 176/202/283/633/640/647/671 and 270/566; `buildHtml`
  **195**, `buildInviteHtml` **246**, `buildShareLinkHtml` **301**, the "mirroring" docblocks **243**
  and **298**; the pagination normalization is **`145-147`**. #301 moved the `CollectionProcessingUtil`
  refs (+29 lines) and #305 moved `streamFileToS3`.

  **The five members lived in five separate files with zero merge contention**; (a) and (d) shipped as
  [#305](https://github.com/themancalledzac/edens.zac.backend/pull/305). (b), (c) and (e) are three independent MRs. (a)'s 16 `src/test` call sites are
  in 2 files (`UserInviteServiceIntegrationTest` 13, `InviteControllerTest` 3;
  `UserInviteServiceAcceptTest` has none); they were a check on the extraction, not a cost.

  **Member (b) carries a trap the item never named. Do not take it without this.** `DEFAULT_PAGE_SIZE` at `CollectionService:106` is `default_content_per_page` = **30**. `PaginationUtil.normalizeCollectionPageable` uses `default_collection_per_page` = **10**. Reaching for the obviously-named helper silently drops the main collection read endpoint from 30 items a page to 10. **The safe call is `normalizePage(page)` plus `normalizeSize(size, DEFAULT_PAGE_SIZE)`**, which are byte-equivalent to the inlined expressions. 45 test references to `getCollectionWithPagination` are the tripwire, not the edit count.

  **Member (c) is the expensive one and the item prices it at nothing.** Dropping `toEntity`'s `defaultPageSize` parameter changes the method's arity: **11 call-site edits across 4 files** -- 8 test `toEntity(` sites (7 passing a literal `30`, plus `CollectionServiceTest:169`'s `eq(request), anyInt()` stub), `CollectionListReadRepositoryIntegrationTest:143`, and 2 in `CollectionService` (`:363`, `:386`). It is safe only because all three defaults are 30; say that in the change or someone will check.

  **Two sub-items struck 2026-08-24, both premises dead:**
  - *`ensureDimensions` twins* -- already refactored. The shared work is hoisted into
    `putDimensionsFromHeader`; what remains is two 6-line wrappers differing only by log message.
  - *EXIF-versus-ISO format detection duplicated between the two date parsers* -- **the premise is
    false**. `parseImageDate` does no format detection at all: it splits on `[: T-]` and takes
    numeric runs. There is no second copy and nothing to fold. What IS real, and was noted in the
    history file but never given a row: `parseImageDate` returns **month 13** for a nonsense date
    and builds an S3 path from it. That is a robustness bug, not a consolidation. **The pointer said "see the row in 'Decisions needed'" and that row is no longer there** -- `parseImageDate` was one of the three answered and shipped 2026-08-30 in #243, and its reasoning moved to
    [history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).
    Corrected 2026-08-31 (third run). The pointer was prose rather than a link, which is why the
    anchor check did not catch it.
- [x] #18. `EquipmentRepository` repeated each SELECT column list 3-4 times -- **DONE**
  ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282), 2026-08-31, grouped with #20). Re-priced down first: 9 of 11 lists are
  hoistable. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-18-tracker-body-moved-2026-09-01).
- [x] #19. `ImageSearchResponse` was a strict subset of `PagedResponse` -- **DONE**
  ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283), 2026-08-31). The widened response was re-verified live against the
  frontend 2026-09-01 and owes the other board nothing.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-19--pagedresponse-and-a-premise-that-is-still-soft-283).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-19-tracker-body-moved-2026-09-01).
- [x] #20. `Records.FilmFormat` shadowed the `FilmFormat` enum -- **DONE** ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282),
  2026-08-31, grouped with #18). Miscounted again on the way in.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).
  Body: [history](2026-08-22-backend-cleanup-history.md#mr-19-20-tracker-body-moved-2026-09-01).
- [x] #21. **DONE** ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), 2026-08-31) — the location endpoint's N+1, up to 150 queries where 6 do. Shipped exactly as the item specified, the second consecutive item needing no adjustment. **Taught that a re-merge warning needs its own test**: concatenating the two batches passes a test that only checks which converters were called. Item body, mutation table and reasoning in [history](2026-08-22-backend-cleanup-history.md#mr-19-21-outcome-2026-08-31----the-n1-and-the-reordering-that-would-have-ridden-along).
- [ ] **Drop the orphan `images` array from `GET /api/read/collections/location/{slug}`.**
  *(Filed 2026-09-01, tenth run, out of the answered BE-2 decision. The decision is recorded under
  [Decisions needed from the user](#decisions-needed-from-the-user); this row is the work.)*

  **Scope.** Delete `images` from `LocationPageResponse`, and with it `batchConvertOrphans`,
  `ContentRepository.findOrphanContentByLocationName` (`:414`),
  `countOrphanContentByLocationName` (`:461`) and their tests. **S-32 is the same query; fix them
  together** -- whichever lands first must not reopen the other. **Keep `totalImages`** -- one cheap
  COUNT with a plausible use. `imagePage` and `imageSize` become dead request parameters and go too.

  **Measured cost of leaving it, on `main` at `43c6f2c6`:** roughly 7 of ~15 SQL queries per
  location page load exist only to build this array (11 of ~19 if any content there is a GIF), plus
  30-60 KB of JSON generated and dropped on the floor. **The caveat:** the frontend ISR-caches that
  fetch, so the cost is paid per revalidation, not per visitor, and the performance win is modest.
  **The stronger argument is contract hygiene** -- a public response field no client reads is a
  field nobody can safely change later, and this one has already caused two rounds of cross-repo
  confusion. **Breaking for any client outside this repo; there are none**, verified by a live scan
  of `edens.zac` `origin/main` on 2026-09-01. **COLD.**
- [ ] **Teach `searchImages` to return GIFs.** *(Filed 2026-09-01, tenth run.)* This is the
  cheaper home for the GIF visibility that [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258)
  was reaching for and that BE-2's option 1 would have bought. `GET /api/read/content/images/search`
  is where every image on the location and tag grids already comes from, so widening it puts GIFs on
  both without rebuilding either page's data source. **Not yet scoped** -- price it before scheduling,
  and read the response record the query feeds first (rule 43; #258 is that rule's own evidence).
  **COLD.**

---

# Wave 6 — Conventions

*(MR 20, the bare-array decision, closed 2026-08-30 by user decision -- bare arrays are blessed and
no endpoint changed. Inventory and reasoning:
[history](2026-08-22-backend-cleanup-history.md#mr-20--the-bare-array-decision-closed-2026-08-30-moved-from-the-tracker).)*

## MR 21 — Untyped Map bodies and responses

- [ ] Admin write surface. **Re-derived 2026-08-24: 19 controller sites, not 15** -- and re-checked 2026-08-25: 19 is right *as endpoints*, but it is **20 distinct lines**, because `deleteImages` has both a Map body and a Map response. Say which unit the number is in before estimating against it -- the original
  list missed five `AdminController` Map responses that existed at the `8c28cf3` baseline
  (`deleteCollection`, `createCamera`, `deleteTag`, `deletePerson`, `deleteLocation`). Current shape:
  `AdminController` has 4 Map bodies (`deleteImages` with no `@Valid`, plus the three rename
  endpoints where a body without `"name"` passes null into the service) and 12 Map responses, one
  needing `@SuppressWarnings` to cast its own service's map back; plus `WebAuthnController`,
  `EditController` (the untyped part is the **response**, not the body -- the body is already
  typed), `ContentControllerProd` and `CollectionControllerProd`. **Eight service methods return raw
  Maps too** and are not in the doc at all (`ContentService.updateImages`/`.deleteImages`,
  `CollectionService.applyCollaboratorImageEdits`, and five `MetadataService.create*`).

  Introduce small records (`RenameRequest(@NotBlank String name)`,
  `DeleteImagesRequest(@NotEmpty List<Long>)`, and so on). **Mostly NOT a wire change**, which the
  item never says: `RenameRequest` deserializes the same `{"name":"x"}`, and a record with the same
  field names serializes identically to `Map.of("success", true)`. Only the added validation changes
  behavior, which is the point.

  **RE-DERIVED MECHANICALLY 2026-09-01 (tenth run) and every figure holds** -- the best-maintained row
  on the board, and it needs no edit beyond this stamp. `AdminController` Map responses, 12:
  `123, 236, 264, 321, 372, 381, 390, 407, 434, 473, 480, 487`. Map bodies, 4: `265` (`deleteImages`,
  no `@Valid`) and the three rename endpoints at `447, 456, 465`. Four more elsewhere:
  `WebAuthnController:145` (body), `EditController:95`, `CollectionControllerProd:174`,
  `ContentControllerProd:119` (responses; was `:113`, +6 from #309). **20 distinct lines across 19 endpoints**, `deleteImages`
  still the one endpoint contributing two. The eight raw-`Map` service methods are
  `ContentService.updateImages` **124**, `.deleteImages` **336**,
  `CollectionService.applyCollaboratorImageEdits` **723**, and `MetadataService` **57, 120, 199, 263,
  312**. Command:
  `git grep -nE 'ResponseEntity<Map<|@RequestBody.*Map<' -- 'src/main/java/edens/zac/portfolio/backend/controller'`
  (returns 20 at `afa39d6f`; **blind to a `@RequestBody` that wraps onto its own line**, which Google
  Java Format does to long parameter lists; today all four bodies sit on one line).

  **Test-side sizing, measured 2026-09-04 on `main` at `afa39d6f`:** `git grep -c 'Map<String,' -- src/test/java`
  returns **41 lines in 11 files**. Endpoints with zero test references by method name
  (`git grep -c '\b<method>\b' -- src/test/java`): **8 of 19** -- `deleteGif`, `createImagesFromDisk`,
  `ingestImages`, `deleteTag`, `deleteLocation`, `updateTag`, `updatePerson`, `getFilmMetadata`.
  The old "~27 source sites / 59 Map-shaped lines / 17 files / ten endpoints" had no command and is
  deleted.

## MR 22 — Remaining convention sweeps

- [x] **#29** -- `ContentControllerProd`'s dead `@Validated` -- DONE ([#303](https://github.com/themancalledzac/edens.zac.backend/pull/303), 2026-09-02). [history](2026-08-22-backend-cleanup-history.md#29-outcome-moved-2026-09-08)

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd.list` (**`:55`, was `:59`** -- re-verified 2026-08-24; serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the 429 handling -- **five sites in three body shapes, re-derived 2026-09-01; the row listed three sites and a session working its list would leave two behind**: empty at `AuthController:74`, `UserShareControllerProd:131` and `WebAuthnController:152`, Map at `CollectionControllerProd:183-184`, `ErrorResponse` at `MessagesControllerPublic:48-52`, correct). Note `MessagesControllerPublic` is in `controller/pub/`, not `controller/prod/`, which is worth writing down because the two refs beside it are `controller/prod`.
- [ ] Try-catch in controllers, **two catching sites** (not three -- the third went with bug #15 in MR 7, [#168](https://github.com/themancalledzac/edens.zac.backend/pull/168), confirmed gone by grep): `AdminUserController.mergePreview` (try at **`541`**) and `.merge` (try at **`567`**), three catch clauses between them (`546`, `569`, `571`); map via `ResourceNotFoundException` plus a new `ConflictException` handler. **Both methods have zero tests** -- the 1,510-line `AdminUserControllerTest` never names either -- so this is an untested behavior change on two admin endpoints. A risk, not a saving, and the one row in MR 22 that needs a decision rather than a sweep: write the tests first or accept the change. **`git grep -n 'try {' -- '.../controller'` returns a third hit, `WebAuthnController:195`. It is a `try/finally` with no catch, clearing the attempt cookie. It is not a third site** -- said here so the next reader does not "find" it and widen scope.
- [ ] `@Value` field injection: **6 sites, re-derived mechanically 2026-09-01; the recorded 9 is dead.** `AwsClientConfig:29`, `:32`, `:35` (feeding `@Bean` methods), `CollectionControllerProd:56`, `ShareControllerProd:45`, `DownloadUrlService:54`. Every other `@Value` in `src/main` is already a constructor parameter. **The "six in `S3Config` and `SesConfig`" half of the old count no longer exists and the "they fold into MR 16 #4" instruction is spent** -- both classes were merged into `AwsClientConfig` by [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261) / [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262). Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly **five** `ReflectionTestUtils.setField` calls. Also `@Autowired` on constructors at **five** classes: `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `ShareEmailLimiter`, `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 4 javadoc notes** -- only `AuthLoginLimiter` has a single constructor; the other four have a package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.
- [ ] Fully qualified names inline: **11 sites, re-derived mechanically 2026-09-01 over `src/main/java` excluding imports, package lines and javadoc; was 14.** `ContactMessageLimiter:68`, `GalleryAccessCookies:33` and `:34`, `BaseDao:87`, `:106` and `:201`, `CollectionRepository:791`, `PersonRepository:76`, `CollectionProcessingUtil:848`, `CollectionService:577` (the `isGalleryAccessAuthorized` parameter; three refs re-derived 2026-09-04), `TagViewResolver:115`. **Three dropped off and both drops are [#282](https://github.com/themancalledzac/edens.zac.backend/pull/282)**: `EquipmentRepository`'s three went when it hoisted its column constants, and `Records.java`'s one went when `Records.FilmFormat` was renamed `FilmFormatOption`. **So the recorded blocker -- "`Records.java` still needs consolidation #20 first" -- is spent. #20 shipped as #282 and this item is unblocked.** `isGalleryAccessAuthorized` has had eight corrections to its ref; **find it by name.** Import-only, **zero test coupling**.
- [ ] `Optional.get()` -- **CLASSIFIED LINE BY LINE 2026-09-01 (tenth run), which closes the
  arithmetic this item had been re-deriving for a week.** Raw sweep, exactly as run:
  `grep -rn --include='*.java' '\.get()' src/main/java | wc -l` -> **58** on `main` at `afa39d6f` (59 at `43c6f2c6`; one left with #301 or #304/#305).
  Exactly **11** are Atomic -- `JobTrackingService:172-175` (four `AtomicInteger` accessors, fields
  declared at `:30-33`), `AdminHomeService:42` (`AtomicReference`), and
  `ImageUploadPipelineService:457-459` and `:491-493` (six, reading `JobStatus`'s `AtomicInteger`
  fields through record accessors without importing the type, which is exactly why an import-based
  exclusion undercounts). **That leaves 47 `Optional.get()`, and none of them is a `Supplier`,
  `ThreadLocal` or `Future`** -- the caveat this item carried about those is discharged. Largest
  holders: `MetadataService` 6, `ContentMutationUtil` 5, `ImageProcessingService` 5, `AuthController`
  4, `AdminUserController` 4. Zero test coupling. **This is not an MR** -- the disposition is
  "rewrite opportunistically when touching these methods", now with a real denominator. **Do not
  re-derive the arithmetic; re-run the sweep only if you intend to fix lines**, and do not restamp
  the `ImageUploadPipelineService` refs, which move constantly.


- [ ] Magic number 2500 at both `resizeImage` call sites in `ImageProcessingService` (**`186` and `277` on `main` at `43c6f2c6`; a third copy sits in a docblock at `140`**). **Both drifted -5 from the recorded `191`/`282`, and they drifted after the "re-verified unchanged 2026-08-31" stamp that this row carried** -- [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)'s shared upload loop moved them. **Find them by the literal, not the line: these two refs have now drifted twice under a stamp saying they had not.** Name it.
- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment
  (`JobTrackingService`). **Split the item.** Making it an enum is COLD and non-breaking (Jackson
  serializes an enum to the same string) but costs **72 references across five files** by
  `git grep -c 'JobTrackingService\|jobTrackingService' -- src/test` (`ImageUploadPipelineServiceTest`
  46, `JobTrackingServiceTest` 12, `AdminControllerTest` 10, `AdminControllerAuthorizationWebMvcTest`
  2, `AdminControllerSetPeopleTest` 2; re-counted 2026-09-04, and the earlier 39 had no command). The trailing `// PENDING, PROCESSING, COMPLETED, FAILED` comment
  at `JobTrackingService:29` is a rule-37 violation on its own and a one-line fix that does not
  need the enum decision. Adding `COMPLETED_WITH_ERRORS` is **UNBLOCKED** -- there is no frontend
  job-status poller at all, so the new enum value breaks no consumer. **Whether the whole
  job-status surface is dead** is the disk-import question under "Decisions needed from the user".
- [ ] Verb-style routes `POST /collections/createCollection` and `POST /content/content` (plus a third the item missed, `GET /api/admin/collections/{slug}/update`). **Both confirmed live in the frontend** (`app/lib/api/collections.ts`, `app/lib/api/content.ts`, one caller each), with **62** backend test references (`git grep -c 'createCollection\|/content/content\|/update"' -- src/test/java | awk -F: '{s+=$NF} END {print s}'`, re-run 2026-09-04). All three routes re-verified exact: `AdminController:104`, `:220`, `:140`. The alias half is COLD; the retire half needs a frontend release.
- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean
  with a hand-built 400 (`CollectionAdminController`). **This is an undeclared wire change**: today
  a failure returns 400 with a `GalleryAccessResponse` body; through an exception it returns 400
  with `GlobalExceptionHandler.ErrorResponse`. **16 test references across 3 files** by `git grep -c 'GalleryAccessResponse\|saveGalleryAccess\|galleryAccess' -- src/test/java` (`CollectionServiceTest` 8, `CollectionAdminControllerTest` 7, `CollectionProcessingUtilTest` 1; the old "30 across 4" had no command). **BLOCKED, and
  precisely specified**: the frontend's `saveGalleryAccess` reads `result.saved` and `result.reason`
  straight off the 400 body, so both would come back undefined and the admin UI would silently
  degrade to a generic message. **The blocker is a small frontend change** -- have it read the
  `ErrorResponse` shape first, then land the backend change.
- [ ] **`throws Exception` on `WebAuthnController:95` and `:145`** (LOW, convention). *(Moved from
  Appendix C 2026-09-05.)* A checked-exception declaration on a controller; `handleGeneric`
  (`GlobalExceptionHandler:200-205`) already maps it to 500 with a generic body, which is right for a
  serialization fault. Wrap in an unchecked exception or narrow the signature when next in the file.

---

# Wave 7 — Structure

## MR 23 — Package moves (rename-only)

- [ ] `controller/user/` is a one-class package (`UserRatingOverrideControllerProd`) that belongs with its **nine** siblings in `controller/prod/` (**re-counted 2026-09-01; the recorded "five" is stale** -- `CollectionControllerProd`, `ContentControllerProd`, `ContentDownloadControllerProd`, `ShareControllerProd`, `UserControllerProd`, `UserFollowsControllerProd`, `UserSavesControllerProd`, `UserSelectsControllerProd`, `UserShareControllerProd`). **This class is named in four separate rows across three sections** -- this one, MR 23's `*Prod` rename below, MR 26's missing controller test, and the cross-repo scan's finding that both its routes are dead. Nothing cross-references them. Deal with it as one piece.
- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and
  `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/
  `ContentRequests`/`CollaboratorRequests` (`model/`). Move the three strays into `model/`.
  **11 files mention the three request records today** (`git grep -l 'RoleRequests\|UserRequests\|InviteRequests' -- src/main src/test`,
  2026-09-04; the recorded 13 was never re-run), zero net lines. **The strongest argument is not in
  the item**: `UserMergeService` imports `UserRequests` from a controller package, so a service
  reaches up into the controller layer. **`controller/admin/` also holds two constraint/validator
  pairs** -- `GrantableLevel` and `AccountStatus` -- so bean-validation types live there by
  precedent rather than by decision. **Decide that explicitly when this MR runs**: either a
  `validation/` package for all four, or say in the doc that constraints live beside the requests
  they constrain. Leaving it undecided is how a third pair gets added the same way. Detail:
  [history](2026-08-22-backend-cleanup-history.md#request-record-homes-long-form-moved-2026-09-01).
- [ ] Optional: drop the `*Prod` suffix now that no controller carries `@Profile` (verified: the only two `@Profile` hits under `controller/` are javadoc text saying there is no gating). **10 main classes and 9 test classes -- 19 files renamed, re-counted 2026-09-01.** The recorded "10 test classes, 23 files" was wrong: there is no `UserRatingOverrideControllerProdTest` (that gap is MR 26's own row), and nobody could re-derive 23 from any counting rule the row states. `grep -rl ControllerProd src/main src/test` does return 23, but that is files *mentioning* a `*Prod` name, not files renamed -- say which unit the number is in (rule 14). **Do not sweep `ProdSecretGuardTest` or `ProdActuatorExposureGuardTest` in; neither is a controller.** Much the largest item in MR 23, and it should not share an MR with the two cheap moves above.

## MR 24 — Service extraction and remaining design items

- [ ] `AdminUserController` is a service wearing a controller's clothes: **twelve injected fields** -- three repositories (`AppUserRepository`, `RoleRepository`, `WebAuthnCredentialRepository`), seven services, one sibling controller (`AdminRoleController`) and a `frontendBaseUrl` -- across **614** lines, with a **1,510**-line test. Re-measured 2026-09-01 on `main` at `43c6f2c6` by `wc -l` and by counting `final` fields. **Three of its four recorded numbers had rotted**, all of them sitting outside the neighbourhood of anything that merged. Extract the invite and passkey flows into services and leave the controller as routing. Prior figures: [history](2026-08-22-backend-cleanup-history.md#adminusercontroller-row-prior-figures-moved-2026-09-01).

  **Find these by name; the numbers are gone for good** -- this item's ref list drifted three times
  in four days, which is working rule 26 happening inside the item that recorded the lesson. The
  `@Transactional` orchestration blocks are `createUser`, `regenerateInvite`, `upgradeUser`,
  `updateUser` and `merge`; the afterCommit hook is `sendInviteEmailAfterCommit`, called from the
  first three.

- [ ] Same shape, smaller: `UserShareControllerProd` computes grant and candidate sets inline with
  a repository. Move it into `ShareLinkService`. **Find the two methods by name; this row has
  carried no line numbers since 2026-08-29 and that is deliberate** -- three separate
  "de-positionalized" passes wrote fresh numbers and each was invalidated within a day (working
  rule 26). They are `buildSettings`, called from two places, and `candidateCollections`, which
  holds the `memberCollectionIdsForUser` call. The file is **231** lines on `main` at `43c6f2c6`;
  the row's old "227 lines today" clause was the same mistake it documents twice and is deleted.
  Ref chain: [history](2026-08-22-backend-cleanup-history.md#usersharecontrollerprod-row-ref-chain-moved-2026-09-01).
*(`Synthetic.blogsOnly` -- **row DELETED 2026-09-01, tenth-run review.** Its premise was FALSE, its
two refs had since drifted, and the false premise was the only argument for the prescribed fold.
Nothing survived. The catalog claim itself still holds: `spec.blogsOnly()` is passed through to
`CollectionRepository.findNonEmptyOrderedByVisibilityIn`, which branches on it at `:484`. Full
reasoning: [history](2026-08-22-backend-cleanup-history.md#syntheticblogsonly-deletion-note-moved-2026-09-01).)*
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` repeats its 3-line blank-string guard **five** times -- `21-23`, `33-35`, `45-47`, `59-61`, `62-64`; **the sixth block at `65-67` is `defaultIso == null || defaultIso <= 0`, a numeric check and not a copy, so the recorded "six, not four" was six only by counting it. Re-read 2026-09-01 -- say five identical string guards plus one numeric, so nobody hunts a sixth string guard**; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched. **~199 source lines across 3 files, not ~60**, plus `@Mock` removal in **5** test files (**re-derived 2026-08-25**, was 6: `ImageProcessingServiceTest`, `ContentServiceTest`, `ImageUploadPipelineServiceTest`, `ContentServiceDownloadTest`, `MetadataServiceTest`) and a constructor arg off 4 services, which is exact -- a 9-file change, so "when next touched" is right. The three files are in `services/validator/`. The item is a rewrite onto DTO bean validation, so the net is not -199 (re-priced 2026-09-04).
- Executor handling in `ImageUploadPipelineService` -- **promoted 2026-08-29 to bug #20 under
  "Carried forward"**: a real bug (an unwaited executor on shutdown), not a design note. This list
  had carried the promotion instruction unexecuted since 2026-08-24. The misnaming half
  (`rawUploadExecutor` runs whole disk and ingest jobs) rides with the bug fix.
*(Note, not a checkbox -- demoted 2026-09-01. `AdminHomeService:33-34` declares an
`AtomicReference<List<...>>` cache, read at `:42`, written at `:47`, cleared only by `evictAll()`.
No TTL, per-instance. Fine single-node. The row prescribed nothing -- "note it for any multi-instance
future" is not work -- so it was a checkbox that could never be ticked, counting against the board's
open total forever. Premise re-verified; it is recorded here and off the gate.)*
- [ ] Service decomposition, the standing item. The four files are `CollectionService`,
  `ContentService`, `ImageProcessingService` and `CollectionProcessingUtil` -- **roughly 5,000 lines,
  and that figure has moved under one percent across 26 MRs of dedicated cleanup.** "Waves 5-7 shrink
  these" is not what the data shows; the waves have been shrinking other files. Decide the split
  boundaries before the next feature lands in them. **COLD -- this needs a decision, not research.**

  **Do not re-record the per-file line counts. They rot every run and the argument does not depend on
  them.** For the record, measured once on `main` at `43c6f2c6`: 1,756 / 990 / 1,357 / 933, total
  **5,036**. The board had carried 1,769 / 1,006 / 1,357 / 933 and a total of 5,065 -- two of four
  components and the total wrong, none of the movement attributable to anything in the recorded
  history. **Third consecutive run in which this row's numbers rotted outside the neighbourhood of
  what merged**, which is research nobody asked for on a row that is explicitly a decision.

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components: **14 test call sites across 11 test files**, by `git grep -o 'new ContentModels.Image(' -- 'src/test' | wc -l` on `main` at `43c6f2c6`. Two more sites in `src/main` are outside this item's scope and are not in the 14 -- say which figure you mean (**rule 31**). The "7 of which have their own private helper" figure is **unreproducible and should be dropped or given a command**. `CollectionRequests.Update` is the same shape: the canonical record has **22** components and the deletion target is the **17**-arg compat constructor at its **22** test call sites; **quote the [Positional constructors](#positional-constructors-that-block-the-testfixtures-pass) row, which is the maintained copy.** **Re-measured 2026-09-04 by walking each construction to its balanced closing paren: 767 lines of positional construction across 18 test files** (8 hold `Update`, 11 hold `Image`, `CollectionServiceTest` holds both; the recorded 17 was wrong when stamped) -- `ContentModels.Image` 14 sites span 364 lines, `CollectionRequests.Update` 25 sites span 403 lines in 8 files. **The recorded 745 had no stated method.** One `TestFixtures` class with builders. **Net ~-370, not ~-600**, and **price it by rule 48**: the win is one place that knows the 31-component shape instead of 39 sites, not the line delta.
- [x] `services/CollectionServiceTest.java` assert/verify twins -- **DONE** ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296),
  2026-09-01, ninth run). Both deletions were mutation-settled first, which is where rule 51 came
  from. [Write-up](2026-08-22-backend-cleanup-history.md#collectionservicetest-assertverify-twins-296).
  Body: [history](2026-08-22-backend-cleanup-history.md#collectionservicetest-twins-tracker-body-moved-2026-09-01).
- [ ] The four typeless-migration integration tests (V50Backfill 188, V51Prep 282, V52Drop 72,
  TypelessRead **164** not 113 -- 706 lines total, not 655). **Two corrections.** Only V50 and V51
  boot their own `PostgreSQLContainer`; V52 and TypelessRead share the per-JVM container and cost
  almost nothing, so the real prize is ~470 lines **and two dedicated container boots**, which the
  line count hides. And "consolidate into one end-state IT" is over-broad --
  `CollectionTypelessReadIntegrationTest` **is** the end-state IT. The move is: delete V50 and V51,
  fold V52's four assertions into it, keep `CollectionTypeAbsentFromWireTest` as the wire guard.
- [ ] **`V54FoldMigrationIntegrationTest` (181 lines) is the same shape and is not on this board.**
  *(New row 2026-08-24.)* It boots its own container to exercise the V54 fold against real data,
  and its own docblock says the shared harness migrates an empty `users` table so "a fold that
  destroyed every tag would still pass it" -- which is why it exists separately. Neither this file
  nor the history mentions V53-V57 anywhere. Decide whether it joins the consolidation above or is
  deliberately exempt, and record which.
- [ ] `ImageUploadPipelineServiceTest`'s 1:1 verify ratio suggests some verify-only tests worth a pass. **The ratio holds exactly -- 33 `@Test` and 33 `verify(` over 1,283 lines -- but the file also has 42 `assertThat(` calls, so the file-level ratio does not by itself identify a verify-only test. Count per test method, not per file (rule 14).**

### Positional constructors that block the `TestFixtures` pass

**Sizing note added 2026-08-24 from #209, and it applies to every item that adds a field to a
record.** Adding a component costs the component, **every `with*` copy method**, and **every
positional construction site in test**. None of that is visible from an item's wording. So when
sizing any "add a field to X" item, run `grep -rn "new <Record>(" src/main src/test` first and
count. That number, not the record edit, is the size -- and it is the argument for the
`TestFixtures` pass below, because each future record change pays the toll again until it lands.

record.** The `isPasswordProtected` item read as "one component plus the two-line frontend change".
The record change was indeed one line. The MR was four files and five test edits, because adding a
component to a record costs: the component, **every `with*` copy method** (two here, and the one on
the hot path was the one the item never named), and **every positional construction site in test**
(four here, across two files). None of that is visible from the item's wording.

So when sizing any "add a field to X" item on this board, run
`grep -rn "new <Record>(" src/main src/test` first and count. That number, not the record edit, is
the size. It is also the argument for the `TestFixtures` pass below: the four positional sites #209
had to touch are the same shape this section exists to remove, and each future record change pays
that toll again until it lands.

*(Retitled 2026-08-24. The old title said "Main-dead, test-live (carried from MR 1a)" and neither half held: `AuthPrincipal`'s 4-arg constructor has a main caller, and MR 1a's own history records it as not-dead rather than deferred.)*

These have many test callers, so deleting them rewrites working call sites to pass explicit nulls.

**Only `CollectionRequests.Update` is left, and it is the one that must ride with the `TestFixtures`
builders** -- its own row says why. The three that did not have to shipped standalone and are
ticked below.

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **22** test call sites, re-derived 2026-09-01 on `main` at `43c6f2c6` with a paren-balanced arity scanner over `-- 'src/test'`: 25 raw = 22 at arity 17 plus 3 at arity 22. `CollectionServiceTest` carries **8 of the 22**. **This row records no per-site line numbers, deliberately** -- the nine it used to carry all moved within one run, which is the tenth review's second lesson. Re-derive by running the scanner, not by trusting a number. **The row's own figure has held across four re-derivations**; [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296)'s body filed it as drifted only because it counted `src/main` and `src/test` against a test-only figure (**rule 31**). Earlier row text: [history](2026-08-22-backend-cleanup-history.md#collectionrequestsupdate-row-prior-text-moved-2026-09-01).

  **STOP RECORDING PER-SITE LINE NUMBERS FOR THIS FILE (rule 5, applied 2026-09-01).** All nine
  refs this row carried drifted -43 to -46 within one run, and it is the worst-drifting ref set on
  the board. Derive them by name:

  ```
  git grep -n 'new CollectionRequests.Update(' -- src/test/java/edens/zac/portfolio/backend/services/CollectionServiceTest.java
  ```

  **Its size is a moving target that grows with test coverage** -- the 22nd site arrived with a new
  file from [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291). Re-run the scanner rather than quoting the number, and record
  the method beside whatever you measure. **The scanner the board quotes, `arity2.py`, is not in
  this repo**, so the recorded command is not runnable as written (rule 31's spirit). Either commit
  it or stop quoting it.

  **CLASSIFIED 2026-09-01: BLOCKED (ordering) on the `TestFixtures` pass, not COLD.** Deleting the
  compat constructor pushes all 22 sites to the 22-arg form with five explicit nulls each, and the
  builder pass then rewrites the same 22 again. **The dependency is narrower than "the `TestFixtures`
  pass"**: `CollectionServiceTest` is the only file in both target lists, at unrelated call sites,
  so if `TestFixtures` is ever split this rides with the `Update` half only. It also owns the two
  `/* collections */` and `/* siblings */` positional labels in `updateWithSiblings`, which
  shortening the constructor removes. Per-site numbers as last measured:
  [history](2026-08-22-backend-cleanup-history.md#collectionrequestsupdate-per-site-refs-moved-2026-09-01).

- [x] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor. **DONE** ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267),
  2026-08-31). Every number re-derived on the day and every one reproduced; the arity-scanner
  method works and is worth keeping for the remaining two. Body:
  [history](2026-08-22-backend-cleanup-history.md#fileentry-3-arg-constructor-tracker-body-moved-2026-09-01).
- [x] `model/AuthPrincipal.java` -- 4-arg constructor. **DECIDED 2026-08-24: leave it.** It is not main-dead (`SessionService` calls it), so it never belonged under the old heading. All **36** call sites are one-liners (35 test plus one in `SessionService` -- **find it by name; the ref has drifted twice and the carried-forward copy of this same fact was corrected to `:181` while this one was left at `:179`**); deleting a 3-line convenience constructor to append `, null` at 35 clean sites is not an improvement. Closing this rather than carrying the hedge a third time.
- [x] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload. **DONE**
  ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31). Body:
  [history](2026-08-22-backend-cleanup-history.md#resolvecollectiondownloadentries-overload-tracker-body-moved-2026-09-01).
- [x] `model/DownloadResolution.java`, the `extension` component -- DONE ([#304](https://github.com/themancalledzac/edens.zac.backend/pull/304), 2026-09-02). [history](2026-08-22-backend-cleanup-history.md#downloadresolution-extension-outcome-moved-2026-09-08)

## MR 26 — Coverage gaps

These are worth more than the bloat they replace.

- [ ] `TokenUtil` — zero direct tests for the CSPRNG/SHA-256 code underlying every invite and share link.
- [ ] `SlugUtil` — zero tests; collisions and normalization are user-facing.
- [ ] `PaginationUtil` — zero tests; an off-by-one corrupts every paged read.
- [ ] `UserFollowsService` — mocked in its controller test, uncovered itself.
- [ ] The validators (`MetadataValidator`, `ContentImageUpdateValidator`) -- the "1-2 incidental
  references" undercounts (8-16 each), but **every one is a `@Mock` declaration or its import**, so
  the conclusion stands and is stronger: zero direct coverage on components that gate admin writes.
- [ ] **`UserRatingOverrideControllerProd` has no controller test at all** -- only a service test.
  *(New row 2026-08-24; noted in the history file after MR 15 #2 and never given one. It is also
  the endpoint whose bare-array response has no test either.)* **Re-verified 2026-08-24: still
  true, and now worth more.** There is no `src/test/.../controller/user/` directory at all. S-6
  changed this controller's call into the service to pass the whole `AuthPrincipal`, so a
  controller test would now also be the only thing pinning that an admin is not 403'd here.
- [x] **Two guard tests that cannot fail, proven by mutation. DONE 2026-08-24** -- S-3
  ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)) and S-4
  ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)). Bug #1's delete-person
  guard reddens one test when its SQL predicate is stripped; `ProdSecretGuard` reddens two when
  `@PostConstruct` is deleted and a third when `@Profile("prod")` is. The mutations and their
  results are recorded with the security items and in working rule 15.
- [x] **#27 (coverage gap) -- constraint annotations on controller parameters were untested
  repo-wide** -- **CLOSED 2026-09-01** ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297), ninth run). The audit emptied the
  item: the true population was one method, not a repo-wide gap. Taught rule 52.
  [Write-up](2026-08-22-backend-cleanup-history.md#27-outcome-2026-09-01--the-audit-emptied-the-item-297).
  Body: [history](2026-08-22-backend-cleanup-history.md#27-tracker-body-moved-2026-09-01).
*(**#29 moved to [MR 22](#mr-22--remaining-convention-sweeps) 2026-09-01.** It came out of #27's audit and was filed beside it, but deleting a dead class annotation is a two-line convention cleanup, not a
coverage gap. The heading here says "Coverage gaps" and #29 was never one.)*
- [ ] **MR 11's headline security fix is untested.** Moving **five** throw sites to bare
  `RuntimeException` -- 3 in `JdbcUserCredentialRepository` and 2 in `WebAuthnService` -- has zero
  coverage. **Re-derived 2026-09-01: five, not the eight this row claimed, and
  `JdbcUserCredentialRepository` lives in `config/`, not `dao/`.** There is no
  `JdbcUserCredentialRepositoryTest`, and `WebAuthnServiceTest` never touches those messages.
  **The protection is the exception type, not the message text** -- the ids are still in the
  messages and still reach the log. **Test the status and the body, not the message.** The
  regression to catch is anyone re-typing these as `IllegalArgumentException` or
  `IllegalStateException`, both of which map to 4xx with the message on the wire (rule 3).
- [x] **`AdminMessageView.readAt` is asserted by no test from the `SELECT` column to the JSON field.**
  **DONE 2026-09-08** ([#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)).
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-26-messages-coverage-2026-09-08----the-row-named-a-test-that-could-not-exist).
  *(Filed 2026-09-05 from slice G.)* Mutations M3 (controller passes `null` for `m.getReadAt()`), M10
  (row mapper stops setting `readAt`) and M10b (`read_at` dropped from `MessageRepository.SELECT_COLUMNS`)
  all survive `MessageRepositoryTest`, `MessagesControllerAdminTest` and `MessageServiceTest`. The
  frontend renders it on `/comments` since edens.zac#396, so a regression ships green. Owed: one
  `MessagesControllerAdminTest` assertion that a seeded read message serialises a non-null `readAt`,
  and one `MessageRepositoryTest` assertion that `findAll` maps the column.
- [x] **`MessageRepository.count` (`:98`) can ignore both filters and nothing notices.** **DONE
  2026-09-08** ([#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)).
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-26-messages-coverage-2026-09-08----the-row-named-a-test-that-could-not-exist).
  *(Filed 2026-09-05, slice G M8.)* `count` calling `appendFilters(null, null, params)` survives; the controller
  test only checks that two mocks receive the same arguments. Owed: one `MessageRepositoryTest` case
  with two rows, one read, asserting `count(true, null) == 1`.
- [ ] **`PATCH /api/admin/messages/{id}/read` with an empty `{}` body has no test.** *(Filed
  2026-09-05, slice G M4a.)* Dropping the `body.read() == null` guard at `MessagesControllerAdmin:73`,
  so `{}` NPEs to 500, survives. Owed: one controller test sending `{}` and expecting 204. Rider (LOW,
  G M6e): `q.trim()` at `MessageRepository:50` is untested; one case in `Filters`.
- [ ] **No test observes the `PutObjectRequest` in `ImageProcessingService`** (LOW). *(Filed
  2026-09-05, slice C C-4.)* `grep -rn "putObject\|PutObjectRequest" src/test/java` is empty;
  `ImageProcessingServiceTest` mocks `S3Client` and captures `ContentImageEntity`, not the request. A
  regression in bucket, key, content type or content length would not fail a test.

Verified good, for the record: `AdminUserControllerTest` is real behavior testing; the auth-table truncation fix landed in `AbstractPostgresIntegrationTest`; no tests mock the deleted `collection.type` shape.

---

# Decisions needed from the user

Returned to the tracker 2026-08-29: the #236 re-split (`32d2168`) had moved this section into the
history file, breaking the Progress links and the history file's "nothing here is open" rule.

**Three rows are open; one is a question for the user.** The disk-import question below was promoted
from Appendix C on 2026-09-05. The other two sit under
[Parked by decision](#parked-by-decision--waiting-on-nobody) at the end of this section, so this
section's open count is one question plus two parked rows.

*(Three decisions -- `enforce-authz`, `parseImageDate`, bare-array responses -- were answered and
shipped 2026-08-30 in [#243](https://github.com/themancalledzac/edens.zac.backend/pull/243). Answers and reasoning:
[history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).)*

**Closed decisions, one ledger line each.** Bodies, options and reasoning are in
[history](2026-08-22-backend-cleanup-history.md#closed-decisions-bodies-moved-2026-09-01).

- [x] **Passkey revocation** -- admin endpoints only, shipped 2026-08-31 ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)).
- [x] **SpotBugs** -- delete all four artifacts. Done in MR 2.
- [x] **`admin_home_tile.cover_image_id`** -- drop it. Shipped 2026-08-31 as
  `V59__drop_admin_home_tile_cover_image_id.sql`.
- [x] **Whether to ship a default DB password** -- drop the default. `spring.datasource.password`
  is `${POSTGRES_PASSWORD}` with no fallback, and `.env.example` marks the variable required.
- [x] **`role.kind`** -- keep it, documented as provenance. Shipped as `V60__comment_role_kind.sql`.
- [x] **Unknown-JSON-key policy (C8)** -- ignore unknown keys. Already Boot's default and already
  pinned by `CollectionTypeAbsentFromWireTest`; flipping it would be a breaking wire change.
- [x] **#28 -- prod's 30 or admin's 50 for image search?** -- **50**, answered at the top of the
  session 2026-09-01 and shipped the same day ([#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)). It widened the public default
  30 -> 50, which is the page-size debt now filed in the cross-repo section.
- [x] **BE-2 -- should the location endpoint keep serving an `images` array?** -- **drop the
  array**, answered 2026-09-01. Closes FE-1 as won't-do; the removal is a COLD item under MR 19,
  and GIFs on location pages are filed separately as a `searchImages` widening.

- [ ] **How is disk import triggered?** *(Promoted 2026-09-05 from Appendix C, where it sat as the
  "job-status endpoint may be dead" lead since 2026-08-24.)*
  `POST /api/admin/content/images/{collectionId}/from-disk` (`AdminController:389`) returns 202 with a
  `jobId` and `GET /api/admin/content/images/jobs/{jobId}` (`:419`) serves the status. No frontend
  caller exists (live scan 2026-09-01), and nothing in this repo outside `ai_docs/reviews` invokes them
  (`git grep -l -i 'from-disk\|fromDisk\|from_disk' -- '*.md' '*.sh' '*.yml' '*.json' ':!ai_docs/reviews'`
  is empty). If the answer is "by hand with curl", both endpoints and `JobTrackingService` stay. If
  "never", the whole surface plus the 72 test references counted on the MR 22 `JobStatus.status` row is
  dead weight. One sentence settles it.

### Parked by decision — waiting on nobody

*(Subsection added 2026-09-01, tenth-run review. Both rows below sat in the flat list above, which
made this section read as a queue of live questions for the user when neither of them is one. Neither
waits on anybody; both are open only because nobody has decided to do the work.)*


- [ ] **Gallery passwords — PARKED 2026-08-24 by decision. Do not act on this yet.** The item framed
  it as a binary (accept plaintext-at-rest formally, or redesign the fingerprint feature). Neither
  is being chosen, because the framing skips the question that has to come first: **what do we
  actually want these passwords to do?** Stated direction, to be designed rather than assumed: we
  will likely move to protected (hashed) passwords for user protection.

  Nobody should open an MR against this until that design exists. Recording what a future design
  pass has to reconcile, so the constraints are not re-derived from scratch:

  - Hashing at rest breaks admin re-share, which is the reason the plaintext exists. Today the admin
    manage page can display the password to re-send it. Any hashed design needs a different answer
    for re-share (regenerate-and-resend, a one-time reveal at set time, or a separate sharable
    token) and that is a product decision, not a storage one.
  - `ClientGalleryAuthService` derives the shared-unlock fingerprint cookie from the password value,
    which is what lets a parent password unlock propagated children. Hashing changes what that
    fingerprint can be derived from.
  - The per-slug cookie validates against the gallery it was issued for, and changing a password
    revokes issued cookies. Whatever replaces plaintext has to preserve that revocation property.
  - **S-1 is a separate problem and is NOT parked.** DISABLED accounts authenticating is about
    account status enforcement in the auth path, not about how gallery passwords are stored. It
    should be fixed on its own timeline.
  - The `isPasswordProtected` wire field (under this same section) is also independent: it serializes
    a boolean about whether protection exists, and never touches the password value or its storage.
    Parking the storage question does not block it.

- [ ] Partial indexes on `is_blog`/`is_client` (C7, "if scale demands"). **PARKED by decision, not a
  question for the user -- moved into this subsection 2026-09-01.** The row's own text calls it an
  explicit "not until scale demands it" and gives the measurement recipe; nothing and nobody is
  waiting on an answer, and filing it as a decision needed from the user inflated that count.
  **Re-verified 2026-09-01: no `CREATE INDEX` in any migration mentions `is_blog` or `is_client`.**
  **The item names the wrong measurement.** "Check request metrics" points at the `request_metric` table, which is readable
  (via `GET /api/admin/metrics/requests` or one SELECT) but counts HTTP requests per route per day
  and says nothing about whether an index helps -- and V44's own header admits those counts
  undercount because of ISR and CloudFront caching. What decides this is table size and selectivity:
  `SELECT count(*), count(*) FILTER (WHERE is_blog), count(*) FILTER (WHERE is_client) FROM
  collection;` plus `EXPLAIN ANALYZE` on the six `CollectionRepository` queries that filter on those
  flags. Confirmed: **no index on either column exists** -- V50 adds them as plain `BOOLEAN NOT NULL
  DEFAULT FALSE` with only a CHECK constraint.

# Stale side branches

Returned to the tracker 2026-08-29 alongside "Decisions needed" -- it carries an open worklist.

**Re-run 2026-09-05.** **Zero open PRs** (`gh pr list --state open --json number` = `[]`); #301
merged as `afa39d6f` on 2026-09-04 and moved refs in `CollectionService`, `CollectionRepository`,
`CollectionProcessingUtil`, `CollectionModel`, `CollectionServiceTest` and `CollectionRepositoryTest`,
all corrected by the eleventh-run review. **Nine worktrees plus the main checkout** (`git worktree
list` = 10 rows, after this review's three locked reviewer worktrees were removed): seven under
`edens.zac.backend.worktrees/`, two under `.claude/worktrees/`. The 2026-09-02 open-PR paragraph and
its lesson (a board refile invalidates an in-flight branch that edits the board):
[history](2026-08-22-backend-cleanup-history.md#stale-side-branches-the-2026-09-02-open-pr-paragraph-moved-2026-09-05). **Four of the eight branches here have no `origin` ref**, so
`git rev-list --left-right --count origin/main...origin/<branch>` errors out on them. Measure
against local refs instead:

```
git rev-list --left-right --count main...<branch>
```

At `43c6f2c6` (behind / ahead): `feat/collection-debloat` **221 / 0**, `claude/auth-password-reset`
**142 / 0**, `claude/one-way-collection-associations` **142 / 0**, `0359-fe-ma1-collection-patch`
**49 / 1**, `0257-backend-security-bugs` **133 / 1**, `0217-user-upgrade-be` **288 / 1**,
`chore/log-review-followups` **251 / 1**, `fix/s18-actuator-exclude` **65 / 3**. **Every "0 ahead"
verdict holds** (all eight ahead-counts reproduced 2026-09-04); the behind-counts are not worth restamping. Worktree inventory:
[history](2026-08-22-backend-cleanup-history.md#stale-side-branches-tenth-run-re-run-long-form-moved-2026-09-01).

- [ ] **Three merged-work worktrees to remove, all clean.** Re-run 2026-09-05: `0392-sd7-people`
  ([#293](https://github.com/themancalledzac/edens.zac.backend/pull/293)), `ma4-mark-as-read` ([#300](https://github.com/themancalledzac/edens.zac.backend/pull/300)) and `rc1-parents-isfilm`
  ([#301](https://github.com/themancalledzac/edens.zac.backend/pull/301)). `pr281` and the two `agent-*` worktrees the tenth run listed are gone. Each
  is ahead by squash artefact only. `git worktree remove` each; per the user's standing worktree rule
  these are theirs to remove, so this is a recommendation, not a cleanup to perform unasked.
- [ ] **Two dirty worktrees on zero-commit branches -- look before removing.**
  `.claude/worktrees/auth-password-reset` has 2 modified files and
  `.claude/worktrees/one-way-siblings` has 4, both 142 behind `main`, both on branches with **zero
  unique commits and no remote branch**. Their only content is uncommitted working-tree changes in
  directories nobody has opened in over a week -- unreviewed work with no commit and no PR. **The
  board described both as "worktrees holding no work", which is true of the commits and false of the
  working tree.** Read them or discard them deliberately; do not `worktree remove` blind. Note
  `claude/auth-password-reset` is still **not** a reason to unpark the gallery-password decision.
- [x] **Settled and safe to delete, kept for the traps they document:** `feat/collection-debloat`
  (0 ahead), `0359-fe-ma1-collection-patch` ([#252](https://github.com/themancalledzac/edens.zac.backend/pull/252)
  merged 2026-08-31, and item #22's text is folded into the tracker so nothing depends on the
  branch -- it still reports 1 ahead because #252 was squash-merged, and **the 0-ahead test does not
  apply to any squash-merged branch on this board**), plus the two below. Not deleted; per the worktree
  rule the removal is the user's.
- [x] **`0257-backend-security-bugs` is fully superseded -- verified, safe to delete.** Its single
  commit is a parallel implementation of MR 5, which shipped as [#165](https://github.com/themancalledzac/edens.zac.backend/pull/165).
  **And it is where the S-4 gap came from** -- its `ProdSecretGuardTest` additions are the
  reflective tests mutation later proved cannot fail. **Do not "rescue" it into an MR.** Detail and
  the one nuance worth carrying into the S-3 fix:
  [history](2026-08-22-backend-cleanup-history.md#0257-backend-security-bugs-verification-moved-2026-09-01).
- [ ] **Two July "wip" snapshots, both local-only.** **No remote branch, so `origin/...` commands
  fail on both -- measure against local refs.** `0217-user-upgrade-be` (1 commit,
  2026-07-28, `AdminUserController` + `UserRequests` + a 136-line `UserUpgradeIntegrationTest`) and
  `chore/log-review-followups` (1 commit, 2026-07-28, `AdminController`/`TagRepository`/
  `CollectionService`/`ContentService`). Both predate Waves 1-5, which rewrote every file they touch.
  Decide per branch: salvage the test, or delete. `0217`'s integration test is the only part likely
  to still be worth anything.
- [x] **`fix/s18-actuator-exclude` -- SETTLED 2026-08-30, safe to delete; it holds nothing unique.**
  R-1 landed its two stranded commits via [#238](https://github.com/themancalledzac/edens.zac.backend/pull/238) and `44a9d81`'s content was already
  on `main` via #232's squash. Verified by content, not by commit count. **Trap worth keeping:**
  `git log origin/main..origin/fix/s18-actuator-exclude --oneline` still returns **3** and always
  will, because a squash merge never makes a branch commit an ancestor of `main`. Diff the content
  of the files it touched instead. Superseded original text:
  [history](2026-08-22-backend-cleanup-history.md#fixs18-actuator-exclude-original-text-moved-2026-09-01).

# Appendix C — Unverified leads

Worth a targeted check; not asserted as findings. **Re-read lead by lead 2026-09-05 (eleventh run):
one lead is open.** Seven of the eight were filed 2026-08-22, none had been promoted or dismissed in
eight runs, and three closed by reading the one method they named. A lead that survives two runs
without a command run against it is dismissed, not restamped.

**Four leads resolved and struck 2026-08-24 to 2026-09-01.** Bodies in
[history](2026-08-22-backend-cleanup-history.md#appendix-c-resolved-leads-moved-2026-09-01).

- [x] Possibly-dead endpoints -- **all three confirmed ALIVE 2026-08-24. Do not delete any.**
- [x] `role.kind` -- **premise disproved 2026-08-24**; V45 also writes `'PERSONAL'`.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6**.
- [x] `collection.rows_wide` -- **premise FALSE**; the frontend reads it as the row-packer chunk size.

**Seven leads settled 2026-09-05.** Outcomes in [history](2026-08-22-backend-cleanup-history.md#c1-outcome-2026-09-05----dismissed-the-order-is-a-documented-trade) and the three entries after it.

- [x] C1, S3 delete before the DB write -- **DISMISSED, decided in code.** `ContentService:331-333`'s docblock records the trade: a failed S3 delete aborts the item and leaves the row rather than orphaning the object.
- C2, the job-status endpoint may be dead -- **moved to "Decisions needed from the user"** as one question: how is disk import triggered?
- [x] C3, duplicate image ids in one `updateImages` -- **struck.** `findImagesByIds` (`ContentRepository:304`, was `:290` before #309) returns one row per distinct id, so `toMap` at `ContentService:151` cannot throw; last-write-wins is acceptable.
- C4, per-item errors inside one transaction -- **promoted to Bug #32** under "Bugs filed after the waves closed".
- [x] C5, `contentDisposition` quotes -- **DISMISSED.** `sanitizeFilename` (`ContentService:881`, strip at `:888`) removes `"`, `\` and control characters; all three callers (`:780`, `:834`, `:856`) route through it.
- [x] C6, the temp-slug race -- **DISMISSED.** `convertTagToCollection` (`TagService:48`) and `createCollection` (`CollectionService:356`) are both `@Transactional` with no `REQUIRES_NEW` anywhere in `src/main`; under READ COMMITTED the uncommitted row is invisible, the slug is overwritten at `TagService:87` before commit, and nothing persists a suffix counter.
- C7, `throws Exception` on two WebAuthn controller methods -- **moved to MR 22** as a LOW convention row.
- [ ] C8, ID-list DAO fetches have no ORDER BY. **Re-run 2026-09-08 on `main` at `75e6bec8`:** `git grep -n -i 'in (:[a-zA-Z]*ids)' -- src/main | wc -l` = **28**, unchanged across #311-#314. It was 28 at `afa39d6f` too, so the 27 recorded 2026-09-04 was a miscount when written, not drift. The two unordered by-id fetchers are `ContentRepository.findImagesByIds` (**`:310`**, was `:304`, +6 from #311's docblock) and `CollectionRepository.findByIds` (**`:805`**, unmoved -- that file was never touched; the `:809` recorded 2026-09-04 was wrong when written).

  **All thirteen caller refs re-run 2026-09-08 on `main` at `75e6bec8`** (`git grep -n "findImagesByIds\|\.findByIds(" -- src/main/java`); the seven that had been carried as unverified since 2026-09-04 are now verified, and five of the thirteen had drifted under this run. `findImagesByIds`, 7 callers: `CollectionProcessingUtil` 106, 271, **440** (was 419), **511** (was 490), `ContentService` **169** (was 149), **739** (was 670), `TagViewResolver` 78. `findByIds`, 6 callers: `UserShareControllerProd` 228, `CollectionProcessingUtil` 260, **412** (was 406), `CollectionService` 1570, `ContentService` **809** (was 740), `UserPageAssembler` 154. `TagViewResolver:47` already documents the unordered result and re-keys. **What settles it:** read the other 12 callers for an order-dependent `.stream()`.

*(A `CollectionServiceTest` "read it line by line" lead was DROPPED 2026-09-01 under working rule 5;
detail: [history](2026-08-22-backend-cleanup-history.md#appendix-c-collectionservicetest-lead-drop-note-moved-2026-09-01). Appendix D was
deleted 2026-09-01: the largest unstarted feature is still `ml_image_tagging`, and it costs nothing
until someone starts it.)*

---

## Next run (set 2026-09-08, thirteenth close-out)

Ordered. **Rule 58 applies: code MRs touch `src` only; one docs MR at the end ticks the rows and
restamps every count once.** The twelfth-run list, whose items 1-3 are now shipped
([#316](https://github.com/themancalledzac/edens.zac.backend/pull/316),
[#317](https://github.com/themancalledzac/edens.zac.backend/pull/317),
[#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)):
[history](2026-08-22-backend-cleanup-history.md#next-run-list-twelfth-close-out-version-moved-2026-09-08).

**Nothing to ask first.** MR 18 #13's direction was answered 2026-09-08 and is recorded on its row.
If a new question appears mid-run, batch it into the opening message -- an answer arriving at the
end of a session is an answer wasted.

1. **MR 18 #13, case-insensitive ordering.** Direction answered: `ORDER BY lower(...)` at the six
   SQL sites -- `TagRepository:58` and `:231`, `PersonRepository:50`, `LocationRepository:57` and
   `:337`, `CollectionPeopleRepository:82` -- plus ~4 Testcontainers order tests (~35 lines).
   **The six refs were re-derived 2026-09-08 on `main` at `2bc62a20` and two were corrected**
   (`LocationRepository:57 -> :95`, `:337 -> :366`); the row carries the current numbers and the
   reasoning for why six is the right count. Spot-check them rather than re-deriving from scratch. **Guardrail: `MetadataServiceTest` mocks the
   repository and cannot see ordering** -- the tests must be Testcontainers, and must redden when
   `lower(` is removed from a site. The dedupe half stays closed; do not reopen it.
2. **MR 26's `PATCH /{id}/read` empty-body row.** `MessagesControllerAdmin:73`; dropping the
   `body.read() == null` guard so `{}` NPEs to 500 currently survives. One controller test sending
   `{}` and expecting 204. Rider (LOW, G M6e): `q.trim()` at `MessageRepository:50`, one case in
   `Filters`. MR 26 9 -> 8. Cheap, and it sits in a file this run already opened.
3. **U-2**, the last COLD security question answerable in-tree.
4. **#22, #33, #34** as the frontend needs them.

**Not in this run, and why.** MR 25's `CollectionRequests.Update` is BLOCKED (ordering) on the
`Update` half of the `TestFixtures` pass. The `coverImage` row and `V54FoldMigrationIntegrationTest`
wait on judgements. U-3 is BLOCKED on the user. MR 18 #10 has been COLD and unworked since the sixth
close-out; it is named here so it stops sliding silently.

### Classification of the open board (stamped 2026-09-08, thirteenth close-out)

**65 open** by `grep -c '^- \[ \] '` on `docs/close-out-thirteenth-run`: from **68 measured on `main` at `bad67029`**, -3 ticked (U-7, and MR 26's `readAt` and `count` rows). Nothing was filed this run. **Confirmed 2026-09-08 on `main` at `2bc62a20`**: all four merged in order (#316, #317, #318, then #319), and all five gates re-run there hold as stamped -- 65 open, `U-` 2, `#NN` 4, `Bug #` 0, MR 26 9. Rule 42's restamp is discharged; nothing is owed. #31 was not ticked: its coverage debt is paid but its FE and `is_film` halves are not. Prior stamp, 68 at [#314](https://github.com/themancalledzac/edens.zac.backend/pull/314): from **69 measured on `main` at `bb07e121`**, -1 ticked (Bug #32). #310 stamped this cell as 71 while still on its own branch and the number never matched `main` -- that is rule 42's restamp, missed once. Before that, from 72 at `a20473fd`: -3 ticked (S-29, S-32, S-34) and +2 filed (S-35, S-36, the two riders #309 did not close). Prior state, from 65 on `main` at `afa39d6f`: -8 ticked (U-1, U-8, #30, FE-5, C1, C3, C5, C6), -3 moved out of Appendix C (C2, C4, C7), +3 `S-` (S-32, S-33, S-34), +2 `#NN` (#33, #34), +1 Bug #32, +1 decision (disk import), +1 MR 22 (C7), +4 MR 26 coverage rows, +6 rule-37 per-file sweeps.

**Next run: MR 18 #13, now that its direction is answered.** The thirteenth run closed the last
actuator question and paid two coverage debts, one of them the `listedOnly` gate the eleventh-run
review found executed by nothing: U-7 ([#316](https://github.com/themancalledzac/edens.zac.backend/pull/316)),
#31's gate test ([#317](https://github.com/themancalledzac/edens.zac.backend/pull/317)) and MR 26's
`readAt`/`count` rows ([#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)).
**The security board is now 2 open questions and zero open findings.**

**Rule 58 worked.** The thirteenth run ran three code MRs off one base with no rebase and no
conflict, because none of them touched this file. The twelfth run's four MRs also shared no source
file, but all four ticked rows here, so each conflicted with its predecessor on merge: three rebases
in a chain, each blocking the next. **A code MR touches `src` only.** The tracker is edited once, after
every code MR has landed, by one docs MR that ticks all the rows and restamps every count against a
`main` that already holds the code. The tracker sitting one run behind in between is intended, and
rule 42's restamp becomes part of that MR instead of a separate chore. Cost and reasoning:
[history](2026-08-22-backend-cleanup-history.md#rule-58-and-what-the-run-cost-without-it).

- **HIGH: none open**, and no `S-` finding of any severity is open. S-29, S-32 and S-34 closed 2026-09-05 ([#309](https://github.com/themancalledzac/edens.zac.backend/pull/309)); S-36, S-33, S-35 and Bug #32 closed 2026-09-06 ([#311](https://github.com/themancalledzac/edens.zac.backend/pull/311), [#312](https://github.com/themancalledzac/edens.zac.backend/pull/312), [#313](https://github.com/themancalledzac/edens.zac.backend/pull/313), [#314](https://github.com/themancalledzac/edens.zac.backend/pull/314)).
- **COLD:** S-30, S-31; MR 18 #10; MR 19 #17 (b), (c), (e); the orphan `images` array; `searchImages`
  GIFs; MR 25's `ContentModels.Image` pass, its four typeless-migration ITs and the verify ratio; U-2;
  MR 21; ten of MR 22's eleven rows; MR 23's three moves; five of MR 24's rows; MR 26's eleven
  nine remaining coverage gaps; the six rule-37 per-file sweeps; #22, #33, #34; the `RoleRepository` deletion;
  Appendix C's C8; the three Stale-side-branches rows (they wait on the user's standing worktree rule,
  not on a question).
- **BLOCKED (ordering):** MR 25's `CollectionRequests.Update`, on the `Update` half of the `TestFixtures` pass.
- **BLOCKED (user):** U-3; the `coverImage` stripping row; `V54FoldMigrationIntegrationTest`; MR 22's
  try-catch row; the disk-import question. **MR 18 #13's direction was answered 2026-09-08 and is no longer blocked.**
- **BLOCKED (other repo):** FE-2, FE-3, FE-4; #31's FE half (RC1).
- **PARKED by decision:** gallery passwords and C7's partial indexes.

The tenth close-out's classification and its arithmetic: [history](2026-08-22-backend-cleanup-history.md#classification-of-the-open-board-tenth-close-out-stamp-moved-2026-09-05).

## Full-board review -- RUN 2026-09-05 (eleventh run)

Eight read-only slices, three in isolated worktrees with throwaway tests and mutations; one apply
pass; one docs MR; zero code changes. Slices: (A) the public read surface, (B) PR #301, (C) the
rule-28 recovery and the #303/#304/#305 proofs, (D1) recorded numbers and gate shapes, (D2) the #299
refile, Appendix C and the estimate column, (E) working-rule compliance, (F) the collation claim and
U-1, (G) a 25-mutation regression hunt. Headline findings:

- S-29 proven by an anonymous GET, and three siblings on the same surface filed (S-32 HIGH, S-33 MED, S-34 MED).
- U-1 answered from outside the host: production runs `prod`. U-7 unblocked, U-8 moot.
- Four of the handoff's six traps were wrong (no Hibernate flush; the `PARENT` docblocks warn against code that cannot compile; `markUsedIfUnused` existed only in a PR body; the +14 comments were written by #300 and #301). Taught rule 54.
- 22 drifted anchors, three false status sentences, one wrong-shape gate, the estimate headline sign-inverted (+4,323 net), and #301 grew the tracker by 9 against rule 53.
- The #306 recovery is byte-complete; every mutation proof reproduces; 1,526 tests green on `afa39d6f`.
- `readAt` is unguarded end to end (G-1); the `listedOnly` gate is executed by no test (G-5).

Write-up: [history](2026-08-22-backend-cleanup-history.md#full-board-review----run-2026-09-05-eleventh-run).

## Full-board review — RUN 2026-09-01 (tenth run)

Summary moved 2026-09-05: [history](2026-08-22-backend-cleanup-history.md#full-board-review-tenth-run-board-summary-moved-2026-09-05). Agent-by-agent detail:
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-09-01-tenth-run).

## Full-board review — RUN 2026-08-31 (third run)

Summary moved 2026-09-05: [history](2026-08-22-backend-cleanup-history.md#full-board-review-third-run-board-summary-moved-2026-09-05). Detail:
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-08-31-third-run).

## Board integrity

- [ ] **14 cross-file anchors on the tracker do not resolve to any heading in the history file.**
  *(Filed 2026-09-06 during the #309 close-out, after four anchors written by #309 and this MR were
  found broken the same way and fixed.)* The pattern is `--` in a heading: GitHub turns each space
  into its own hyphen and drops the em dash, so `S-29, S-32 and S-34 outcome -- 2026-09-05` anchors
  as `...outcome----2026-09-05`, four hyphens, and every hand-written link guesses one. **Command
  (it prints the broken list):** `python3 - <<'EOF'` with the slugifier recorded in the #310 PR body
  -- lowercase, drop everything but word characters, spaces and hyphens, then replace each space
  with one hyphen, no collapsing. **Trap:** a slugifier that collapses runs of whitespace reports 46
  false positives and hides the real count. The figure was 18 before the closed-findings ledger
  moved; that move repaired one by accident. **Re-run 2026-09-08 on `main` at `75e6bec8`: 15 broken
  of 126 history links, then 14 after this MR's section moves repaired one** -- two more repaired incidentally by #310's section moves. The trap fired
  again during that re-run: a collapsing slugifier reported 40 and named three of #310's moved-section
  links as broken when all three resolve. **Do not re-derive this count; run the recorded slugifier.**
  The 15: `12b-`, `24-`, `26-`, `adminusercontrollertests-`, `bug-18-`, `mr-18-11-`, `mr-18-12-`,
  `mr-18-9-`, `mr-19-15-`, `mr-19-18-and-20-`, `mr-19-19-`, `u-4-`, `u-5-`, `u-6-`, and the `-277`
  locations one.

## Session log

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph.

**Leak detector.** Three entries in a row ending `Next: X` means X is being avoided -- say so and
either make it real work or drop it. **Checked 2026-09-05: not tripped.** The 2026-09-01 reading
(TRIPPED on U-1, which "needs host access") was wrong: U-1 needed one anonymous GET, and it is
answered. MR 18 #10 is named under "Not in this run" so the detector can see it. The two detector
corrections recorded 2026-09-01: [history](2026-08-22-backend-cleanup-history.md#session-log-leak-detector-reading-moved-2026-09-01).

**Retention rule (stated 2026-08-29).** The current session's entries stay here; every close-out
moves the rest to the history file's log archive in the same pass. A close-out MR that grows this
log without moving the older entries is the lapse signal. The archive has three parts, all in the
history file: the [pre-split log](2026-08-22-backend-cleanup-history.md#session-log) (from 2026-08-22), the
[newer archive](2026-08-22-backend-cleanup-history.md#session-log-archive--entries-moved-2026-08-31) (2026-08-30 onward) and the
[2026-09-05 move](2026-08-22-backend-cleanup-history.md#session-log-archive-entries-moved-2026-09-05). **Link all three.**

### 2026-09-08 -- thirteenth run. Three coverage/deletion MRs, and rule 58's first clean run

Three code MRs plus a close-out, all merged: **U-7** ([#316](https://github.com/themancalledzac/edens.zac.backend/pull/316)),
**#31's `listedOnly` gate test** ([#317](https://github.com/themancalledzac/edens.zac.backend/pull/317)),
**MR 26's `readAt`/`count` rows** ([#318](https://github.com/themancalledzac/edens.zac.backend/pull/318)),
close-out [#319](https://github.com/themancalledzac/edens.zac.backend/pull/319). `main` is `2bc62a20`.
All five gates re-run on `main` after merge and all five hold as stamped: **65 open / `U-` 2 /
`#NN` 4 / `Bug #` 0 / MR 26 9**. **The security board is now 2 open questions and zero open
findings of any severity.**

**Rule 58's first run, and it worked.** Three code MRs off one base, no rebase, no conflict, because
none of them touched this file. Compare the twelfth run's three chained rebases. The rule is
confirmed, not just adopted.

**Rule 58 also overrode a row instruction for the first time.** U-7's row said to amend working rule
34 in the same MR. Rule 34's index line lives here, in the file the close-out edits to tick three
rows and restamp five counts, so amending it from the code branch reproduces exactly the conflict
rule 58 prevents. The amendment went in [#319](https://github.com/themancalledzac/edens.zac.backend/pull/319).
**Rows written before 2026-09-06 may carry more instructions that predate rule 58; expect to
re-target them rather than follow them.**

**Two rows specified a test that could not do the job, from opposite directions.** MR 26's row asked
for a `MessageRepositoryTest` assertion that `findAll` maps `read_at` -- that class mocks
`NamedParameterJdbcTemplate`, so the row mapper never meets a `ResultSet` and `SELECT_COLUMNS` never
executes, which is *why* M10 and M10b survived it. Satisfying the row's letter would have been rule
15's own complaint. U-7's row said "delete the two S-18 test files" when the deletion touched two and
a half: `MUST_BE_EXCLUDED` was a shared constant left orphaned. Generalised on both items: **a row
naming both a mutation and a host test class has assumed that class can run the mutation.**

**One coverage loss taken deliberately and named.** `ActuatorExposureEndToEndTest.health_isStillReachable`
was the only test that booted the app and proved `/actuator/health` serves 200;
`InternalSecretFilterTest:52` only proves the filter passes the URI through. The deployment probe is
now uncovered. Recorded on U-7's write-up rather than discovered later.

**Reconciliation after the merge.** The close-out omitted its own session-log entry -- this one --
and therefore skipped the retention move as well; both repaired in
[#320](https://github.com/themancalledzac/edens.zac.backend/pull/320). Two of MR 18 #13's six
`ORDER BY` refs had drifted and are fixed: `LocationRepository:57 -> :95` and `:337 -> :366`.
**That drift sits outside the merge neighbourhood** -- the three code MRs touched one main file and
five test files -- so the scoped sweep would not have found it, and did not; re-deriving the next
run's item 1 refs on purpose did. The item's count of six is correct as written and was re-verified.

Next: MR 18 #13.

### 2026-09-08 -- twelfth run. The public-read visibility family and the last open bug

Moved to [history](2026-08-22-backend-cleanup-history.md#2026-09-08----twelfth-run-the-public-read-visibility-family-and-the-last-open-bug)
2026-09-08 under the retention rule. Four code MRs plus a close-out ([#311](https://github.com/themancalledzac/edens.zac.backend/pull/311)-[#315](https://github.com/themancalledzac/edens.zac.backend/pull/315)),
all merged; it taught rule 58.

### 2026-09-06 -- #309 close-out. Reconciled the board against what merged

Moved to [history](2026-08-22-backend-cleanup-history.md#session-log-entry-309-close-out-moved-2026-09-08)
2026-09-08 under the retention rule. Docs only ([#310](https://github.com/themancalledzac/edens.zac.backend/pull/310)); seven refs restamped.

### 2026-09-05 -- S-29 + S-32 + S-34 closed. First code MR since #301

Moved to [history](2026-08-22-backend-cleanup-history.md#session-log-entry-309-moved-2026-09-06)
2026-09-06 under the retention rule. Outcome: #308 and #309, both merged.

### 2026-09-05 -- eleventh run. Full critical review

Moved to [history](2026-08-22-backend-cleanup-history.md#session-log-entry-eleventh-run-moved-2026-09-05)
by the #309 close-out under the retention rule. Docs only; its outcome is the board above.