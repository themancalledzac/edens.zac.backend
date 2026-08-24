# Backend cleanup tracker

Living checklist of what is still open. Check items off as they land; when an MR closes, its
detail moves to the history file (working rule 11) rather than staying here ticked.

Completed detail lives in [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md)
-- Waves 1-3 in full, and MR 12-13. This file carries only the working rules, the open MRs, and the
items carried forward out of closed waves. Keep it that way (working rule 11).

Source review: 2026-08-22, baseline `main` @ `8c28cf3`, six parallel passes (controllers/API, core collection and content services, media/upload pipeline, security/auth/config, data layer, tests/build). Every finding was verified against the code -- caller greps for dead-code claims, line-level reads for bugs. Unverified suspicions are quarantined in Appendix C.

Line numbers are from the `8c28cf3` baseline. Find symbols by name, not by line, once earlier MRs have shifted the files.

## Progress

| Wave | MRs | Status |
|---|---|---|
| 1 — Deletions | MR 1a-4 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-1--deletions) (#159, #160, #161, #162, #164). Two residuals carried forward, below. |
| 2 — Bugs | MR 5-9 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-2--bugs) (#165, #166, #168, #169, #170, #172, #173). One residual (bug #17) carried forward, below. |
| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). **Superseded by the 2026-08-24 review**: see "Open security findings" below, which now holds six items including two HIGH ones. |
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) below. **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 found the wave rule does not fit hardened files and produced working rule 12; its stale-docblock **items** (four, not one) are still open. |
| 5 — Consolidations | MR 15-19 | MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). #6 closed the `PersonRepository` carry and taught working rule 14; its own guard was later found to have a bypass (security finding S-2, closed [#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)). One MR 15 follow-up still open, below. **next: MR 19 #16** (the only real performance fix in the wave) or MR 16 #4/#5 (zero test coupling). |
| 6 — Conventions | MR 20-22 | not started |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | not started |

Three sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **0 open, 0 HIGH.** S-1 ([#192](https://github.com/themancalledzac/edens.zac.backend/pull/192)), S-2 ([#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)), S-3 ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)), S-4 ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)), S-7 ([#199](https://github.com/themancalledzac/edens.zac.backend/pull/199)), S-9 ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)), S-8 ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)), S-5 ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)) and S-6 ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)) all done. **This board is closed.** S-7 shut the invite re-activation path at both ends, S-8 finished the pair of sweeps hanging off the admin status change, and S-6 applied working rule 20 across six sites -- one more than any item had recorded. **next: nothing here.** What is left is the MR sections below, and MR 19 #3 was re-measured during S-5 and moved to "not worth doing". |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend) | 2 open, 1 answered. One is a live 404. |
| [Stale side branches](#stale-side-branches) | **New 2026-08-24.** 6 worktrees, 0 open PRs, all superseded. |

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree (32.6k lines) is larger than main (27.2k); about 8% of it tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **17** (5 high) | — |
| Security findings | **0 open** — see below. All nine closed 2026-08-24. S-7 took the last live hole; S-6 closed the board | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments (main, rule violations) | ~~370~~ **567 measured** | ~300 net (also low) |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | **10 findings** | ~2,700 (+700 optional) |
| Build/config rot | **9 findings** | ~150 |

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split, re-reviewed 2026-08-24. Waves 1-3 read "complete"
but held **eight live items**, collapsed into five entries. Since then: the `PersonRepository` entry
was closed by MR 15 #6 (decided, not deferred), and the chunked-body residual moved to **S-5** under
"Open security findings". What is left is below, plus one bug that never had a row at all.

- [ ] **Bug #17 (medium) — `updateImages` claims a batch save it does not do.** *(New row
  2026-08-24. This finding has existed since MR 12b with a full write-up in the history file and
  **no checkbox anywhere in either document** -- the exact failure this board keeps hitting. It was
  also filed as "bug #16", which collides with the shipped Selects null-body bug; renumbered #17
  here.)* Re-verified live on `main`: `ContentRepository.saveImage` is a single-row INSERT/UPDATE,
  `updateImages` calls it once per image in a loop, and the log line still reads "Batch saved {}".
  N image edits issue N statements. **Re-verified live 2026-08-24 at `ContentService:228-233`** --
  the comment says "Batch save all successfully updated images for efficiency", and the next three
  lines are `for (ContentImageEntity image : imagesToSave) { contentRepository.saveImage(image); }`.
  Premise intact, anchor refreshed. **COLD.** The quarantined comment in `ContentService` is this bug's only
  evidence and stays until the MR that adds a real batch save and fixes the log line with it.
- [ ] **Four main-dead, test-live members owed to MR 25** (deleting them means editing test call
  sites, which is why MR 1a deferred them): `ContentService.resolveCollectionDownloadEntries` 2-arg
  overload (5 test sites, verified exact), `DownloadResolution.extension`,
  `CollectionRequests.Update`'s 17-arg constructor (21 test sites, not 23),
  `DiskUploadRequest.FileEntry`'s 3-arg constructor (13 sites, not ~20). Also listed under MR 25
  below, where the counts and two corrected premises now live.

  **`AuthPrincipal`'s 4-arg constructor was a fifth entry and has been removed from this list**: it
  is **not** main-dead. `SessionService` calls it -- which the old entry admitted two lines below a
  "zero `src/main` callers" heading. Disposition is now a decision, not a deferral: leave it. All 30
  call sites are one-liners, and deleting a 3-line convenience constructor to append `, null` at 29
  clean sites is not an improvement.
- [ ] **V19's `admin_home_tile.cover_image_id`** -- **research COLD, disposition still a decision.**
  Verified 2026-08-24: `AdminHomeTileRepository` is the only Java that touches `admin_home_tile`,
  its sole statement is `SELECT tile_key, display_order`, and its `TileRow` record carries those two
  fields only. There is no INSERT or UPDATE of the table from Java anywhere. V19 names the column in
  its seed INSERT but every one of the ten seeded values is an explicit `NULL`, so "written by
  nothing" is substantively right -- the only write the column has ever had is a NULL.

  *(A first pass at this entry claimed "real values sit there and are never read". That was wrong --
  reading V19's `VALUES` list corrects it, and the "Decisions needed" row had it right all along.
  Recorded because the doc's existing row was more accurate than the fresh check, which is the
  reverse of the usual failure and worth not forgetting.)*

  Consequence: the column cannot have received a value through the application at all, so the
  confirmation query the decision row suggests only guards against a manual DB edit. Disposition is
  one migration either way -- `ALTER TABLE admin_home_tile DROP COLUMN cover_image_id;` or a comment
  marking it reserved. Recommend dropping. Still listed under "Decisions needed" because it is a
  schema call, not because anything is unresearched.

- [ ] **Whether to ship a default DB password at all** is still undecided. MR 9a fixed the separator
  and preserved the existing default, so `spring.datasource.password` now falls back to `password`
  instead of `-password` -- the one line where that fix made a default more usable rather than less.
  Also in "Decisions needed".

## Open security findings

Consolidated 2026-08-24 by the full-board review. Security work was scattered across three homes --
a Wave 3 residual here, `CollectionAccessService` filed under a comments wave, and two new findings
that had no home at all. They live here now. Every item below was traced in code on `4976220`, and
the two marked PROVEN were demonstrated by mutating the source and watching the suite.

- [x] **S-1 (HIGH). `UserStatus.DISABLED` is not enforced anywhere in the auth path.** **DONE**
  ([#192](https://github.com/themancalledzac/edens.zac.backend/pull/192)). Two guards, both
  mutation-verified: `AuthController.login` and `SessionService.resolve` now require `ACTIVE`.
  Shipped the allowlist, not `<> DISABLED`. `AuthPrincipal` untouched -- the cost report the
  guardrail asked for is in the
  [history file](2026-08-22-backend-cleanup-history.md#s-1-outcome-2026-08-24--userstatus-enforced-in-the-auth-path),
  and the short version is that the field could only ever hold `ACTIVE`. Session revocation on
  status change was deliberately NOT included; it is now **S-8**. Taught working rule 16.
- [x] **S-2 (MEDIUM). The MR 15 #6 `addMember` guard has a bypass.** **DONE**
  ([#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)). `repointMemberships` now
  carries the same `status <> 'PERSON'` test `addMember` enforces; when the target cannot hold
  memberships the source's rows are dropped rather than moved. Mutation-verified at both levels.
  Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-2-outcome-2026-08-24----the-merge-path-upholds-the-addmember-rule).
  Taught working rule 17.
- [x] **S-3 (HIGH, PROVEN untested). Bug #1's delete-person guard has no test that can fail.**
  **DONE** ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)). Two DAO tests in
  a new `dao/PersonRepositoryIntegrationTest`, no source change. Stripping `AND status = 'PERSON'`
  now reddens exactly one test out of 1,314, which both proves the new guard test and re-confirms
  that nothing else covers the predicate. `deletePersonById`'s behavior is unchanged; the cost of
  making it throw is reported, not implemented. Full write-up, including why the test did not go in
  `UserMergeIntegrationTest`, in the
  [history file](2026-08-22-backend-cleanup-history.md#s-3-outcome-2026-08-24----the-delete-person-guard-has-a-test-that-can-fail).
  Sharpened working rule 15's practical note.
- [x] **S-4 (HIGH, PROVEN untested). `ProdSecretGuard` can be unwired silently.** **DONE**
  ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)). A `@Nested class Wiring`
  in `ProdSecretGuardTest` boots real contexts with `ApplicationContextRunner`, so the container is
  what calls `verify()`. Deleting `@PostConstruct` now reddens two tests and deleting
  `@Profile("prod")` reddens a third; the duplicate `enforceAuthzDisabledThrowsEvenWithAGoodSecret`
  is gone. No source change. Full write-up, including why the item's reason for calling that test a
  duplicate was slightly wrong, in the
  [history file](2026-08-22-backend-cleanup-history.md#s-4-outcome-2026-08-24----prodsecretguard-cannot-be-unwired-silently).
- [x] **S-5 (LOW, downgraded 2026-08-24). Chunked bodies bypass the public body cap.** **DONE**
  ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)). `RateLimitFilter` read
  `getContentLengthLong()`, which is -1 for `Transfer-Encoding: chunked`, so `-1 > 16384` was false
  and a chunked body reached Jackson capped only by its 20MB `StreamReadConstraints`. An
  undeclared-length body is now refused with **411 Length Required** inside the same `tryConsume`
  branch, so the rejection still costs a token.

  **The check is `length < 0 && Transfer-Encoding present`, not `length < 0`.** A bodiless request
  reports -1 too, so the shorter condition 411s every GET on a public path -- there are none today
  (`MessagesControllerPublic` is the only controller under `/api/public/**`, and it is POST-only),
  but the filter is keyed on a path prefix and the next public endpoint inherits this branch. The
  conjunct is exact for HTTP/1.1 and **stops being exact if http2 is ever enabled**, since a
  DATA-frame body needs no `Transfer-Encoding`; http2 is off today (no `server.http2.*` property,
  and `TomcatConfig` casts to `Http11NioProtocol`). That is the one thing to re-check later.

  411 rather than 413 because a chunked request may well be under 16KB -- what is wrong with it is
  that the filter cannot measure it, which is exactly what 411 says.

  Working rule 16 came back empty and that was the finding: `getContentLength` has **two hits in the
  whole codebase, both in `RateLimitFilter`**, and both were the same expression evaluated twice.
  Unlike S-1 and S-8, the item's named site really was the only site. The guardrail held -- the
  three limiter cores were not touched, and their merge cost is re-measured under MR 19 #3 below.
  Suite 1,338 -> 1,341. Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-5-outcome-2026-08-24----a-body-with-no-declared-length-is-refused-instead-of-waved-through).

- [x] **S-6 (LOW). `CollectionAccessService.effectiveLevel` overclaims.** **DONE** ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)).
  The docblock claimed `canView` / `isClient` / `hasAtLeast` all resolved through `effectiveLevel`.
  The first two queried `RoleRepository` directly, so neither the admin sentinel nor the share branch
  reached them. Both go through `effectiveLevel` now and the docblock is true.

  **The item's scope warning was right, and short by one.** It said working rule 20 was a policy
  rather than a ruling about one service, so the fix had to sweep. The sweep found **six** sites: the
  two gallery gates (`isGalleryAccessAuthorized` password prompt, `isDownloadAuthorized` 401), three
  callers passing a bare `Long userId` (`UserSelectsService`, `UserRatingOverrideService`,
  `UserShareControllerProd.addCollection`), and one nothing had recorded -- **`UserSavesService.add`
  404s an admin** saving an image whose only home is a collection they hold no grant on, because
  `ContentRepository.isImageVisibleToUser` is `LISTED OR role grant` with no `is_admin` term. That one
  is fixed above the SQL rather than in it: the query filters on several read paths, and an identity
  rule inside it would apply where nobody looked.

  **Routing `canView` through `effectiveLevel` verbatim would have widened share links.**
  `effectiveLevel` adds two branches, not one. A flyby resolves GENERAL, so `canView` returns true for
  it and a share link would have become a second way past the gallery password prompt. `isClient` is
  safe (the GENERAL ceiling refuses CLIENT), but the two gallery gates now screen with
  `AuthPrincipal.isRealUser` first, reproducing the old `userId != null` exactly. Fourth consecutive
  item whose specified fix needed adjusting when implemented.

  **Four list-scoping sites were left alone on purpose** -- `memberCollectionIdsForUser` in the share
  picker and on the `/user` page, `findSavedImagesByUserId`, and `isChildExcluded`. They shorten a
  list rather than deny a request, and rule 20 settled bouncing, not scoping. Reasoning is in the
  history file so nobody re-derives them. Suite 1,341 -> 1,347. Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-6-outcome-2026-08-24----an-admin-stops-being-bounced-and-the-sweep-found-a-sixth-site).

- [x] **S-7 (MEDIUM). Two more session-minting paths read no status.** **DONE**
  ([#199](https://github.com/themancalledzac/edens.zac.backend/pull/199)). `InviteController.accept`
  and `WebAuthnService.finishLogin` both read status before minting. **The item's specified fix was
  wrong and shipping it verbatim would have broken a working feature**: it said "require `INVITED`",
  but `AdminUserController.regenerateInvite` mints a password-reset link for an **ACTIVE** user who
  redeems through that same endpoint, so an `INVITED`-only allowlist kills admin-issued password
  reset. Shipped `{INVITED, ACTIVE}` -- still an allowlist, and still not a `!= DISABLED` denylist,
  because `UserStatus.PERSON` exists. Taught working rule 18.

  Review moved the whole flow out of the controller: it now lives in `UserInviteService.accept`
  returning an `AcceptResult`, with the status rule as a named predicate `mayAcceptInvite` whose
  javadoc carries the reasoning. Taught working rule 19. Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-7-outcome-2026-08-24----status-is-read-before-a-session-is-minted).

- [x] **S-8 (LOW, split out of S-1 2026-08-24). `updateStatus` does not revoke live sessions.**
  **DONE** ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)). Shipped as
  `SessionService.revokeAllForStatus` over a new `UserSessionRepository.revokeAllForUser`, called
  from `AdminUserController.updateUser` on the line below `invalidateInvitesForStatus` -- the shape
  the item specified, with no branching in the controller.

  **The open judgement resolved by diverging from S-9, as the item allowed.** The session predicate
  is `SessionService.mayHoldSession`, **ACTIVE only**, not `mayAcceptInvite`'s `{INVITED, ACTIVE}`.
  `resolve` has enforced ACTIVE-only since S-1 and a test says so on purpose, so mirroring the
  invite boundary would leave an `ACTIVE -> INVITED` demotion holding `user_session` rows that can
  never resolve -- exactly the rows this item asked to tidy. The two sweeps therefore run off two
  allowlists, and INVITED is what separates them: an INVITED account may hold a live invite but may
  not hold a working session. Like S-9, the predicate serves two call sites (`resolve` and the
  sweep), so it cannot drift.

  **The cost of folding it into `updateStatus` was measured, not argued** -- the CTE was written and
  the suite run: 1 failure, `resolveRejectsSessionWhoseAccountWasDisabled`, which is the *only*
  mutation-detector for the S-1 fix. Revoking in the DAO would let the S-1 guard be deleted with the
  suite still green. Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-8-outcome-2026-08-24----a-status-change-revokes-the-sessions-already-minted).

- [x] **S-9 (LOW). Disabling a user does not invalidate their outstanding invites.** **DONE** ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)). Shipped as `UserInviteService.invalidateInvitesForStatus`, which reuses S-7's `mayAcceptInvite` predicate, so the "may this account hold a live invite" rule has one definition serving both the redemption site and the admin handler rather than two that can drift (working rule 14). Keyed on the resulting status, not on a transition, so re-applying a non-eligible status still sweeps an invite issued in between. **Zero churn to existing tests** -- all four pre-existing `invalidateInvites` assertions patch to INVITED or ACTIVE, so none observe the new call. Full write-up in the [history file](2026-08-22-backend-cleanup-history.md#s-9-outcome-2026-08-24----invites-die-with-the-account).
  Found while verifying S-7's precondition. `UserInviteService.invalidateInvites` exists and works,
  and has **exactly one caller**: `AdminUserController:292`, on the email-change path only
  (`existing.getStatus() == UserStatus.INVITED && !email.equals(existing.getEmail())`). No status
  transition calls it. So disabling an account leaves any unused, unexpired invite live for the
  remainder of its 7 days.

  This is the other end of S-7's invite hole. S-7 closes it at the redemption site by refusing a
  `DISABLED -> ACTIVE` flip; S-9 closes it at the source by killing the token when the account is
  disabled. Either alone stops the escalation, which is why this is LOW and not a duplicate of S-7 --
  it is defense in depth, and the same argument S-8 makes about session revocation.

  Deliberately **not** folded into S-7: different mechanism (invalidate-on-transition versus a guard
  at the flip), different file, and folding it in would mean editing the admin status endpoint inside
  an MR scoped to the auth paths. Ship S-7 first; this stays true either way.

  *Related:* S-8 is the same shape for sessions. If both are done, do them together -- "disabling an
  account revokes its live sessions and its outstanding invites" is one coherent change to one
  endpoint, and two separate MRs touching the same handler is worse than one.

### Verified sound, do not re-open

Attacked on 2026-08-24 and held up, recorded so the next pass does not spend the time again.

- **`RoleRepository.addMember`'s `<> 'PERSON'` denylist is correct as-is. Do not tighten it to an
  ACTIVE allowlist.** S-1's item argued the opposite -- "admitting a DISABLED account to a role is
  not a dormant grant, it is a live one" -- and used that to say the accounts-only test should be an
  allowlist. **S-1 shipping falsified its own argument.** A DISABLED account can no longer
  authenticate at either chokepoint, so a role membership it holds now grants exactly nothing until
  an admin re-enables the account, which is the definition of dormant. The live-grant premise held
  only while the auth-path hole was open, and closing that hole is what removed it. What remains is
  a real but low-severity dormancy concern already stated in `addMember`'s own docblock, and the
  same shape MR 15 #6 rated low. If someone still wants the allowlist, it needs a fresh argument,
  not this one.

- **The [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189) `/api/read/user/**`
  matcher.** All 17 replaced guards sit under the pattern; nothing shadows it (the five rules above
  are `/api/auth/**`, and `anyRequest().permitAll()` is registered after); default `StrictHttpFirewall`
  rejects `%2e`, `%2f`, `//` and `;` before matching; MVC path matching is case-sensitive; there is
  no context-path or matching-strategy override. `hasRole("USER")` is equivalent to the deleted
  `isRealUser` checks because `SessionAuthenticationFilter` is the only writer of ROLE_USER and its
  principal always carries a non-null userId.
- **A flyby principal with a non-null userId is not reachable.** `AuthPrincipal.flyby(...)` hardcodes
  `userId=null` and is the only non-null-`shareId` construction; `SessionService` is the only
  non-null-`userId` construction. Nothing asserts the invariant, but every `canView`/`isClient` call
  site null-guards independently, so breaking it would still grant nothing.

## Working rules

Learned while doing the MRs. Most apply to every item still open, not just the one that taught
them. Rules 6, 7 and 10 are comment-MR-specific and their wave is closed -- keep them for reference,
but rule 12's corollary on writing NEW comments in hardened files still applies everywhere.

1. **A property default in `application.properties` is probably dead.** `docker-compose.yml` injects
   `SPRING_DATASOURCE_*`, `EMAIL_*`, `WEBAUTHN_*`, `ADMIN_*` and more unconditionally, and an
   unconditionally-injected env var outranks the Spring property via relaxed binding. Before treating
   any config default as live, grep `docker-compose.yml` for the same key. MR 9's bug #9 looked like
   a risky behavior change until this was checked; it turned out the six placeholders are never
   consulted in any deployed path. `docker-compose.yml`'s own comment at the `EMAIL_*` block spells
   the rule out.
2. **`src/test/resources/application.properties` shadows the shipped file on the test classpath.**
   Any test asserting on shipped config must read `src/main/resources` directly. A
   `ClassPathResource("application.properties")` lookup silently reads the test stub and passes
   vacuously. The first draft of `ApplicationPropertiesPlaceholderTest` did exactly that and went
   green against the unfixed bug.
3. **`GlobalExceptionHandler` maps `IllegalArgumentException` AND `IllegalStateException` to 400.**
   Only the catch-all `@ExceptionHandler(Exception.class)` produces a 500. So for any remaining
   "wrong status" item: to make something a 400 use `IllegalStateException`, and to make something a
   500 a bare `RuntimeException` is the only route without adding a new exception type. Do not assume
   `IllegalStateException` means "server broke" here.
4. **Line numbers in this doc are from the `8c28cf3` baseline and drift as MRs land.** Find symbols
   by name. Re-verify a `file:line` before quoting it as evidence in a PR description.
5. **An item that is nothing but a list of line numbers is already dead.** Rule 4 says line refs
   drift; the corollary is that any item whose entire content is line refs cannot be recovered by
   "find it by name", because there is no name -- only a position. Wave 4 is built this way and was
   measured 64% stale before it started (see MR 12's prep note). For any such item, re-derive the
   list mechanically from the current tree and treat the doc's list as a sample of intent, not a
   worklist. **Scope widened 2026-08-24 from "any remaining Wave 4 MR" to any item whose body is a
   list of `file:line` refs.** Wave 6 is built exactly this way and is unstarted; the 2026-08-24
   sweep found its lists undercount badly (fully-qualified names 6 -> 14, `Optional.get()` 17 -> 45,
   `@Value` 3 -> 9, MR 21's Map sites 15 -> 27), each written from one file's worth of grepping. Of
   ~130 refs in open items, 38 were exact, 79 had drifted, 3 pointed past end-of-file, and 11
   carried a claim that was itself wrong.
6. **In a comments-only MR, leave trailing comments (`code; // note`) alone.** Learned twice in MR 12,
   for two different reasons, which is why it is a rule and not a preference. In 12a, removing the
   trailing comment from `updateTags(currentTags, tagUpdate, null // ...)` would let
   google-java-format collapse the call onto one line -- a genuine code reflow. In 12b the code text
   would have stayed byte-identical, but the line still shows in `git diff` as a modified code line,
   which defeats the one thing that makes this MR class cheap to review: `git diff | grep` for
   anything that is not a comment. Trailing comments belong to whichever MR touches that code for
   real. Corollary: removing a comment block that sits at the END of a method body forces removal of
   the blank line above it, because spotless drops the dangling blank before the closing brace. That
   is unavoidable -- name it in the PR rather than claiming a perfectly pure diff.
7. **A stale comment is not automatically a bug report.** The Wave 4 guardrail says a comment
   contradicting its code is a bug someone wrote down and forgot, and that is sometimes true -- bug
   #16 was found exactly that way. But across MR 12's three files, 349 comments produced exactly one
   real bug. `CollectionService` produced none: every checkable claim held under verification.
   Reporting "none found" is the correct outcome for a file, not evidence of a shallow pass. Verify
   the claim against the code before filing; do not manufacture a finding to satisfy the guardrail.

8. **A `P:` judgment note decays the same way a `D:` line number does.** Rule 5 warns about
   coordinates; MR 13a found the *instructions* rotting too. Two of its `P:` entries told a future
   session to fix things that had since been fixed -- "229 (fix wrongness with bug #10)" when bug
   #10 was already closed and the comment already correct, and "218-219 (fix staleness: RAW
   scheduling moved)" when the comment already said exactly that. Following either would have meant
   editing correct text to reintroduce a problem. Before acting on a `P:` note that asserts
   something is stale or wrong, verify the claim against the current code -- the note is as old as
   the line number next to it.

   **Sharpened 2026-08-24: a note can be falsified by the very MR it is attached to.** S-1's item
   justified a *second* change -- tightening `addMember` to an ACTIVE allowlist -- with a premise
   ("a DISABLED account in a role is a live grant, not a dormant one") that was true only while
   S-1's own hole was open. Shipping S-1 made it false. So when an item bundles "and this also
   settles X", re-check X *after* the item lands, not before: the fix may have moved the ground the
   side-argument stood on. Recorded in "Verified sound, do not re-open".
9. **Commit with explicit paths, never `git add -A`.** This repo carries untracked review docs in
   `ai_docs/reviews/`. MR 12c's commit used `git add -A` and swept
   `ai_docs/reviews/2026-07-25-open-pr-review.md` (321 lines, untracked since before the session)
   into PR #180, which merged with it. The PR's diff stopped matching its description, which is
   exactly the property that makes a comments-only MR cheap to review. Stage the files the MR names
   and nothing else.

   **Do not "fix" this by deleting that file.** Appendix B is a scorecard written against it, and
   that citation predates the sweep -- so the tracker had been depending on a doc that existed only
   on one machine. Committing it was an accident that corrected a real fragility. The defect was the
   undisclosed diff, not the file's presence, and the rule above is about staging discipline, not
   about this file.

10. **Promotion inflates. Measure a comment MR in words, not lines, and diff it against what the
    docblock already says.** MR 13b removed 37 inline comments (422 words of prose) and added 600
    words of javadoc -- **+42% prose in an MR called "debloat"** -- and nobody noticed from the diff
    stat, because `+65/-45` looks near-neutral once javadoc's `/**`, ` * ` and `<p>` overhead is
    counted as content. Line count hides prose growth; word count does not. Three causes, all
    avoidable, all caught in review (MR 13c):
    - **The same rule written into three docblocks.** The EXIF-over-XMP precedence went into
      `extractFromStream`, `extractFromExifTag` AND `extractFromXmpDirectory`. This is exactly the
      failure MR 12a already caught with the CDN invalidation comment -- three copies, fixed by
      putting it in one class-level sentence. State a rule once, where it is enforced.
    - **A dense existing docblock deleted and re-expanded.** `extractTagsAndPeopleFromXmp` had an
      accurate 3-line summary; it was replaced with 11 lines across three paragraphs saying the same
      thing plus examples. Promoting into a docblock does not mean rewriting the docblock.
    - **A fact promoted that the caller already documented.** `writeZipEntries` got the `.error.txt`
      placeholder rationale, which `zipToS3AndPresign`'s docblock one method above already stated.

    So before promoting: read the docblock you are promoting INTO, and the one on its nearest public
    caller. If either already says it, the comment is a delete, not a promote. Then check the MR in
    words. 13b+13c combined is -4 lines and -14 words with all 37 comments gone, which is what a
    debloat should look like; 13b alone was not.

11. **Outcome write-ups go in the history file, not the tracker.** The tracker grew from the
    original review to 1,729 lines, and 1,312 of those (76%) were closed-out detail: Waves 1-3 in
    full, MR 12's and MR 13's worklists, outcomes, guardrails and costed write-ups, and a 107-line
    session log. Each doc MR was adding around 110 lines to a file whose live content is about 400.
    A tracker you have to skim past three finished waves to reach the open item is not a tracker.

    So: when an MR closes, the tracker gets one line -- status, PR link, and any working rule the MR
    taught. Everything else (the worklist it re-derived, the measurements, the guardrail it wrote,
    the report a guardrail asked for) goes to
    [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md). Nothing is
    deleted; it is one link away when the evidence is actually wanted.

    The split also caught what the bloat was hiding. Waves 1-3 read "complete" but held fourteen
    unticked boxes. Six were done and never ticked (now ticked in the history file); **eight were
    live work**, including a Wave 3 security residual (the chunked-body cap bypass) and a deletion
    whose stated precondition turned out to be false. They are in "Carried forward" above, as five
    entries -- four of the eight are the same MR 25 deferral. Reconcile on the way out, not on the
    way in.

12. **Promote a fact about the method; keep a warning about a line.** Rule 10 said measure before
    promoting. MR 14 measured, and the answer changed what the wave rule should be. Promoting
    `SecurityConfig`'s 24 comments into one `filterChain` docblock costs **+24 words (+9%)** even in
    a careful draft that keeps every fact and states each rule once -- and the entire overhead is
    anchor-naming, because a docblock has to write "on `/api/auth/me`, `/api/auth/logout`, ..." for
    what an inline comment gets free from its position.

    Worse, the comments that most deserve to survive are the ones a docblock cannot hold:
    `RoleRepository`'s `\s` notes sit against the text block whose trailing `\s` they protect;
    `AdminBootstrap`'s "do not fix this into a single statement" has to be next to the two
    statements; `CollectionControllerProd` explains a `Cache-Control` call that is deliberately
    *absent*. Move any of those to a docblock and the next editor changing the line never reads it.

    So the test is not "is this inside a method body" but **what is this comment about**. A fact
    about the whole method promotes cleanly -- a no-op override, a result ordering, a swallowed
    failure. A warning attached to one line stays on that line. A comment that restates its code, or
    that the docblock already carries, is a delete. MR 14's 93 split 19 / 66 / 7 that way.

    This also explains why MR 12 and MR 13 swept so cleanly and MR 14 did not. Those files were
    narrated; these were hardened by Waves 1-3, so their comments were written on purpose. **Check
    which kind of file you are in before assuming the wave rule applies.**

    **Corollary, learned the hard way in MR 15 #2: this rule licenses keeping a comment, not
    writing a long one.** That MR added an 8-line block to `SecurityConfig` and was called out for
    it -- fairly. The rule permitted a comment there (a line-anchored warning: do not move this
    matcher inside the toggle), but only about three lines of the eight carried it. The rest
    enumerated the routes, which restates the `/api/read/user/**` pattern sitting on the next line,
    and re-explained the `hasRole` versus `authenticated()` flyby rationale that the comment FOUR
    LINES ABOVE already gave at length. That is working rule 10's "a fact the caller already
    documented", inside a single method. So when adding a comment to a hardened file: read the
    neighbouring comments first, delete anything they already say, and write only the one fact that
    cannot be recovered from the code. In a repo that just removed 500 comments, a new one has to
    earn each line.

13. **A guardrail decays like a line number, and a re-derivation is not self-verifying.** Rule 8
    said a `P:` note rots the same way a `D:` coordinate does. MR 15 #2 found both failure modes at
    once, in the freshest content on the board.

    The guardrail -- written the day before, costing three placements and naming one "the honest
    default" -- rested on a premise that was never checked against the code: that placing the
    matcher outside the dev toggle would cost local dev a login-free convenience. **Those routes
    were never login-free.** The 17 guards it was replacing had no profile check, so dev already
    401'd. Two of the three options it presented were behavior *changes* and only one preserved
    behavior; the decision it framed as a tradeoff did not exist. A guardrail that reasons from
    "SecurityConfig already does X for `/api/edit/**`" has to check whether the thing being replaced
    is conditional in the same way. It also missed that the chain already had the right precedent
    sitting four lines above -- the unconditional `hasRole("USER")` on `/api/auth/me`.

    And the re-derivation, which existed precisely to fix stale refs, introduced one of its own: it
    raised the count 17 -> 18 by grepping `isRealUser` and counting a javadoc line that says the
    word. It fixed two real drifts and added one miscount, so its net accuracy was better but not
    clean. **Re-derived facts need the same verification as the facts they replace** -- a grep is
    evidence of a string, not of a guard. Confirm a count by making the change and watching it come
    out even, not by trusting the tally that motivated it.

14. **Re-derive a duplication item by its code shape, not its helper name.** Rule 13 said a
    re-derivation needs its own verification. MR 15 #6 shows what that verification has to look
    for. The item's re-derived table was accurate -- four declarations, seven call sites, all
    confirmed before editing and even at the end -- and it was still incomplete, because it was
    built by grepping the name `currentUserId`.

    Grepping the *body* instead (`SecurityContextHolder.getContext().getAuthentication()`) finds
    two more copies of the same read under different names:
    `SyntheticCollectionResolver.currentPrincipal` is byte-identical but returns the principal, and
    `CollectionService.viewerMaySeeHidden` inlines it with an extra null check. A copy that was
    renamed, or inlined, or that returns one field more, is still a copy -- and it is the one a
    name-grep will never surface. Consolidation items are about duplicated *code*, so the
    re-derivation has to be keyed on the code.

    The corollary for counts: "four copies" and "four helpers named X" are different claims. Say
    which one the number is.

15. **A regression test that cannot fail is worse than no test, because it reports coverage.**
    Rule 13 said verify a re-derived fact by making the change and watching the count come out even.
    The same standard applies to tests: the only proof a guard test works is mutating the thing it
    guards and watching it go red. The 2026-08-24 review ran that check and two high-severity fixes
    failed it -- **all 1,304 tests pass with bug #1's `AND status = 'PERSON'` delete guard stripped,
    and all 1,304 pass with `@PostConstruct` removed from `ProdSecretGuard`**, which leaves the
    guard that stops prod booting on a default secret dead at startup.

    The shared cause is testing through a mock of the thing under test. `MetadataServiceTest`
    `verify()`s a mocked `PersonRepository`, so the SQL predicate that *is* the fix is invisible;
    `ProdSecretGuardTest` calls `verify()` reflectively on a hand-built object, so it tests the
    method and never the annotation that runs it. Both read as coverage in any count.

    So: when a fix lives in a SQL string, an annotation, or a framework wiring point, a unit test
    with that layer mocked cannot guard it -- it needs an integration or context test. When adding a
    guard test, state the mutation it is meant to catch. Two negative results from the same run,
    recorded so nobody re-derives them: the `exportDateFromFile` fix IS caught (by sibling tests,
    though not by the one named for it), and MR 15 #6's two DAO tests ARE real -- both redden when
    the guard is stripped, while 58 controller tests over the same endpoints stay green because they
    mock the repository.

    Practical note: run these with `-Dspotless.check.skip=true`. A mutation that shortens a line
    lets google-java-format reflow it, and the build then fails on formatting before a single test
    runs -- which looks exactly like a red test if you are not watching.

    **Second practical note, learned in S-3: `touch` the source after restoring it, or run `mvn
    clean test`.** Restoring with `sed -i.bak` + `mv` gives the restored file an mtime *older* than
    the `.class` compiled during the mutation run, so maven-compiler-plugin skips it and the next
    `mvn test` runs the mutated bytecode against restored source. S-3's restore looked like the
    guard was still broken. The failure mode is worse in the other direction: restore a mutation you
    meant to keep and the suite goes green on stale classes.

    **Both gaps this rule was written from are now closed** -- S-3
    ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)) and S-4
    ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)), 2026-08-24. The `1,304`
    figures above are the historical baseline and are left as written; the suite is now **1,317**.
    Stripping the delete-person predicate reddens one test, deleting `@PostConstruct` reddens two,
    and deleting `@Profile("prod")` reddens a third. The rule stands as a rule -- what it stops
    describing is a live hole on this board.

16. **Count the callers of the thing being guarded, not the callers the item named.** S-1 named
    `AuthController.login` as the login chokepoint and the whole item -- guardrail, premise checks,
    scope -- was built on that. Grepping `sessionService.create` instead found **three** callers:
    `AuthController.login`, `WebAuthnService.finishLogin`, and `InviteController.accept`, which
    flips status to ACTIVE unconditionally and so lets a disabled account re-activate itself. Two of
    the three were invisible to the item because it was keyed on the endpoint the reviewer happened
    to read, and the review that produced S-1 had already traced `users.status` exhaustively -- it
    just traced the wrong symbol, the column rather than the session constructor. They are S-7.

    This is working rule 14 pointed at security rather than duplication. Rule 14 said re-derive a
    duplication item by its code shape, not its helper name; the same failure produces an incomplete
    *guard* when the item names one entry point and the guarded resource has several. Before
    guarding an operation, grep the operation -- here `sessionService.create` -- and confirm the
    item's entry-point list is the whole list.

    The saving grace was placement, not luck. Because S-1 also guarded `SessionService.resolve`, the
    universal chokepoint, the two missed minting paths grant no access. **A guard at the read
    chokepoint covers entry points you failed to enumerate; a guard at an entry point covers only
    that entry point.** When both are available, the read chokepoint is the one that must not be
    skipped.

17. **Put the guard in the statement, not in the caller's precondition.** S-2's obvious fix was to
    constrain the merge target in `requireMergeable` the way the source is constrained. That would
    have closed the hole by breaking the feature: merging two tag-only people is a normal
    de-duplication, so a PERSON target is the ordinary case. The fix that works is one predicate
    inside `repointMemberships`'s own UPDATE, mirroring the test `addMember` runs.

    The general form: when a raw statement bypasses a guard, the repair belongs in that statement,
    not in a validation the caller runs first. A caller precondition has to be restated by every
    future caller and tends to be phrased as "refuse the operation", which is usually too blunt --
    the statement-level guard can express "do this much of it", which here is *move what the target
    can hold and drop what it cannot*.

    Corollary on disposition. Dropping rows quietly is normally wrong, but not when the rows are
    ones the guard would refuse to create, grant nothing in their current state, and exist only
    because the rule went unenforced. Check what the schema would do unaided before inventing a
    policy -- `role_member.user_id` is `ON DELETE CASCADE`, so dropping was already the default and
    repointing was the deviation. Log the count at WARN so the disposition is visible.

18. **An allowlist's form and its membership are two claims. The item usually only checked one.**
    Rule 16 said to grep the callers of the thing being guarded. S-7 shows the other half: once you
    know where the guard goes, you still have to enumerate who legitimately arrives there.

    S-7's item was specified carefully -- COLD, re-verified against `4abb28e`, every premise
    anchored -- and it still named the wrong allowlist. It said "require `INVITED`", reasoning from
    the onboarding path alone. But `AdminUserController.regenerateInvite` mints a password-reset
    link for an **ACTIVE** user who completes the same accept flow, and both its docblock and
    `UserInviteService.regenerateInvite`'s say so in as many words. Shipping the item as written
    would have closed the hole by silently breaking admin-issued password reset -- the same failure
    shape rule 17 caught in S-2, arriving through a different door.

    So before writing an allowlist: grep the endpoint for every path that reaches it and read what
    those callers say about who they serve. The membership is a claim about the product, and the
    docblocks on the calling paths are where that claim is actually recorded. The *form* (allowlist,
    not denylist) was the part the item got right, and it mattered independently: `UserStatus` has a
    fourth value, `PERSON`, so `!= DISABLED` and `{INVITED, ACTIVE}` are not the same set.

    Corollary, and the reason this is worth a rule rather than a note: a wrong allowlist fails
    *closed*, so it does not show up as a security regression. It shows up weeks later as "password
    reset is broken", far from the MR that caused it. Only the test that asserts the legitimate case
    catches it, which is why `activeUserAcceptsForPasswordReset` exists.

19. **Controllers map results to status codes. Everything else is a service, and no `//` inside
    either.** Both halves came from review of S-7 and S-9, and both were already written down --
    `.claude/CLAUDE.md` puts business logic in `services/`, and working rule 12 governs comments.
    They are here because knowing a rule and applying it under momentum are different things.

    The guard, the three writes and the session mint had all accumulated inside
    `InviteController.accept` simply because that is where the existing code sat. Moving them to
    `UserInviteService.accept` -- returning an `AcceptResult` the controller maps in a switch --
    dropped `PasswordEncoder`, `SessionService`, `@Transactional` and `@Slf4j` out of the controller
    entirely. The test suite followed the logic rather than staying put: the behavioral cases moved
    to `UserInviteServiceAcceptTest`, and `InviteControllerTest` shrank to status mapping and
    validation. **A controller test that has to mock a `PasswordEncoder` is telling you the logic is
    in the wrong file.**

    On comments: rule 12 says promote a fact about the method, keep a warning about a line. The
    comment blocks written into S-7 and S-9 were facts about the method, dressed as line warnings
    because they sat next to an `if`. The right home was the docblock -- or better, a named
    predicate. `mayAcceptInvite(status)` carries in its name what five lines of comment were
    carrying, and unlike a comment it cannot drift from the code, because it *is* the code. **Prefer
    naming the rule over explaining it.**

20. **Admin means owner. Recorded as a rule because it decides items, not just one item.**
    Settled by the user 2026-08-24, unblocking S-6: *"an 'admin' has FULL ACCESS OVER EVERYTHING.
    think of 'admin' as 'OWNER'. it should never have any password restrictions, any issues with
    ANY permissions."*

    Two things follow, and the second is the one that will bite. First, any access-control item on
    this board resolves in the permissive direction **for admins specifically** -- an admin bounced
    by a password prompt or a permission check is a bug, not a safe default. Second, and this is
    the trap: **this does not generalize to account status.** S-1, S-7 and S-8 all shipped
    ACTIVE-only allowlists and all of them are still right, because they answer "is this account
    alive at all", not "what may a live admin reach". A future session that reads rule 20 as
    "prefer permissive" and loosens `mayHoldSession` or `mayAcceptInvite` has misread it.

    Rule 18 said an allowlist's membership is a claim about the product and the docblocks on the
    calling paths are where that claim is recorded. Rule 20 is the same observation one level up:
    some product claims are not written down anywhere in the code, and the only way to get them is
    to ask. S-6 sat BLOCKED for a day on a question that took one question to answer.

## Ordering note

The original review put bug fixes first so deletions would rebase cleanly. We inverted that and started with deletions, because they are compiler-verified and carry no behavior change. The bug MRs rebase onto the deletions instead. Only one item actually collided, and it was handled: `PersonRepository.deleteById` was listed under both MR 1 and bug #1, and was held until MR 5 because it had a live caller -- dangerous code, not dead code. It shipped with MR 5 and is gone.

---

# Wave 4 — Comments and docs

Rule: no `//` comments inside method bodies in main code. Delete, or promote to a docblock. D = delete, P = promote then delete. Class-level section banners (`// ====`) are outside method bodies and are out of scope.

**Measured 2026-08-23, not estimated.** At the start of the wave `src/main` held 684 lines beginning
with `//`, of which **567 sat at indent >= 4** -- inside method bodies, which is what this rule
targets. The review's "~370 occurrences" premise was low by about 53%. MR 12 and MR 13 cleared 474
of them. MR 14 dispositioned the last 93 and kept 66 by decision (working rule 12), so 67 remain
in `src/main`. MR 12's and MR 13's worklists, outcomes and guardrails are in
the [history file](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete).

## MR 14 and the Wave 4 retro — closed

Both write-ups moved to the history file 2026-08-24, applying working rule 11 to the tracker itself
(it was carrying 55 lines of closed-out measurement for a finished wave). The retro's word-count
table and MR 14's disposition counts are at
[Wave 4 retro](2026-08-22-backend-cleanup-history.md#wave-4-retro--measured-in-words-2026-08-23) and
[MR 14 outcome](2026-08-22-backend-cleanup-history.md#mr-14-outcome-2026-08-23). MR 14 shipped as
[#187](https://github.com/themancalledzac/edens.zac.backend/pull/187) and taught working rule 12.
**Wave 4 removed 500 in-method comments for -1,026 words across seven MRs; 67 remain, 66 by
decision.** What is still open from it is below.

### Still open from MR 14 — stale docblocks

Out of scope here by design: this MR was in-method comment lines only. These are docblock rewrites,
and each needs its claim verified before acting (working rule 8).

- [ ] `filterNonListedChildCollections` (`CollectionService`) describes a context-detection mode that no longer exists.
- [ ] The "previously spread across ContentProcessingUtil" rename-history at `ContentModelConverter` and `ContentMutationUtil` -- that class is gone.
- [ ] "PARENT-shaped" vocabulary at `CollectionService`, `TagViewResolver`, `UserPageAssembler` -- dead since the enum deletion.
- Moved 2026-08-24: `CollectionAccessService.effectiveLevel` is now **S-6** under "Open security findings" -- it is an access-control item, not a docblock rewrite, and the re-review found it fails closed rather than leaking.

---

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

- [x] #1. One client-IP resolver. **DONE** -- shipped with bug #3 in MR 5 ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165)).

- [x] #2. One SecurityConfig matcher instead of the copy-pasted `isRealUser` guards. **DONE** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)). **17 guards, not 18** -- the re-derivation counted a javadoc line in `UserShareControllerProd`. The matcher went OUTSIDE the enforce-authz toggle, next to `/api/auth/me`: the guards it replaced were unconditional, so that is the only behavior-preserving placement, and the guardrail's "costs a dev convenience" was false -- dev already required a session on these routes. A flyby now gets 403 rather than 401 there, by decision. Java-only main -42; 28 controller-level assertions became `config/UserRoutesAuthorizationWebMvcTest`. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-2-outcome-2026-08-23).
- [x] #6. `currentUserId` is duplicated. **DONE** ([#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). Four copies became `config/CurrentUser.userId()`, joining `ClientIp` and `GalleryAccessCookies` as a static helper next to the security plumbing. The item's "move it onto `AuthPrincipal`" does not work -- that is a Spring-free record and this is a static context read. The null contract was left alone and costed instead: the four admin sites break local dev only, the two read-surface sites 500 a logged-out visitor, so it is two problems and not one. Java-only main -26 lines / +36 words. Two more copies of the same read were found and deliberately not folded in (`SyntheticCollectionResolver.currentPrincipal`, `CollectionService.viewerMaySeeHidden`) -- see rule 14. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-6-outcome-2026-08-24).

### The MR 15 #6 follow-up, left open on purpose

- [ ] Fold the last two copies of the same static read into `CurrentUser`: add
  `CurrentUser.principal()`, have `userId()` delegate, and rewrite
  `SyntheticCollectionResolver.currentPrincipal` (identical body, returns the principal) and
  `CollectionService.viewerMaySeeHidden` (the read inlined, plus a `p.userId() != null` check
  because it passes the whole principal to `hasAtLeast`). Mechanical and behavior-preserving. Out
  of MR 15 #6's scope because the item said four.

  Already checked, do not re-flag: grepping the read shape
  (`getContext().getAuthentication()`) across `src/main` returns six sites. `CurrentUser` is the
  consolidated one, these two are the follow-up, and the remaining three are genuinely different
  uses -- `CollaboratorAccessInterceptor` resolves an access level, `FlybySessionFilter` tests
  whether an authentication already exists, and `AuthController` serves `/api/auth/me`. None of
  the three extracts a user id.

## MR 16 — Infrastructure classes

- [ ] #3. One keyed rate limiter. **Re-derived 2026-08-24: three copies, not two** -- `RateLimitFilter.newBucket` is a third byte-identical Caffeine+Bucket4j core. **Two halves of the original wording were wrong and are corrected here.** "The same class twice" is false: `ContactMessageLimiter` carries a global daily bucket that a `KeyedRateLimiter(capacity, window, idleTtl)` signature has no slot for, and its own docblock calls that bucket the only limit an attacker cannot pick the key for. "Their TTL policies have already drifted" is also false: `ClientGalleryAccessLimiter`'s `window + 15min` idle TTL is a documented deliberate choice (an attacker must not reset it by pausing), and calling it drift invites someone to "fix" it to 2h and weaken it. **Cost is test-dominated: ~-55 source against ~84 test sites** (7 constructor sites + 24 calls in `ContactMessageLimiterTest`, 7 + 32 in `ClientGalleryAccessLimiterTest`, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`). Keep `AuthLoginLimiter` separate -- it is a `Cache<String,Integer>` counter, not Bucket4j. Low priority.

  **Cost re-measured 2026-08-24 while doing S-5, which was told to leave these cores alone. Every number above held, and one new blocker turned up.** The test-site counts are exact, not approximate: `ContactMessageLimiterTest` has 7 constructor sites and 24 `tryConsume` calls, `ClientGalleryAccessLimiterTest` has 7 and 32 `.allow(` calls -- 70 in the two dedicated tests, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`. Source is 82 + 81 lines across the two classes, and the shared part of them is small: the bucket shape (`Bandwidth.builder().capacity(n).refillIntervally(n, window)` wrapped in `Bucket.builder().addLimit(...)`) and the `Caffeine.newBuilder().maximumSize(10_000)` cache. Everything around it differs.

  **The new blocker is `Retry-After`.** `RateLimitFilter` does not just ask its bucket a yes/no question -- it calls `bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill()` to build the header (the only such call in the codebase). A `boolean allow(String key)` signature, which is the shape the other two callers want, cannot serve it. A merged class has to expose the `Bucket` or a nanos-to-refill accessor, and that is a wider API than the item's framing implies.

  **Four more things that do not merge**, all found by reading the three call sites rather than the class list. (1) Three different key functions: `email.trim().toLowerCase(Locale.ROOT)`, `ip.trim() + "|" + GalleryAccessCookies.normalizeSlug(slug)`, and `ClientIp.resolve(request)` -- so the shared class takes a pre-computed key and each caller keeps its own normalization, which is most of what looked like the duplication. (2) Three different blank-key policies: `ContactMessageLimiter` skips the per-email bucket but has already spent a global token, `ClientGalleryAccessLimiter` returns true, `RateLimitFilter` has no blank case. (3) The idle TTL cannot have a default -- `ClientGalleryAccessLimiter`'s `window + 15min` is deliberate and the other two are a fixed 2h, so it must be an explicit constructor parameter, which is the parameter most likely to be got wrong later. (4) `ClientGalleryAccessLimiter`'s package-private `Duration` constructor exists so refill-timing tests can use sub-second windows instead of sleeping for minutes; it has to survive the merge intact.

  **Verdict unchanged, with more confidence behind it: not worth doing.** The merge saves roughly 50 source lines, needs a wider API than a boolean, and rewrites ~70 test call sites -- and the four items above are each a way to quietly weaken a live limiter while the suite stays green. S-5 no longer collides with it; that file is settled.
- [ ] #4. One AWS config class. **Best value in MR 16: zero test coupling** -- nothing in `src/test` references `S3Config` or `SesConfig`, and there is no `@Import`, so the rename to `AwsClientConfig` is free. Premise verified intact 2026-08-24. `config/SesConfig.java` duplicates S3Config's credentials plumbing and borrows `aws.s3.region` for a non-S3 client. Merge the SesV2Client bean into S3Config (rename it `AwsClientConfig`), share one `AwsCredentialsProvider` bean across the four clients, and delete the catch-log-rethrow blocks. ~40 lines.
- [ ] #5. One CloudFront invalidation implementation. **The item undersells itself**: `cloudFrontClient` and `cloudFrontDistributionId` are used only inside `invalidateCloudFrontPaths`, so delegating removes two constructor dependencies (arity 10 -> 9). Test cost is ~4 lines and no mock or verify is rewritten. **Trap**: route through `invalidatePaths(List<String>)` as written -- routing through `markChanged()` swaps specific keys for two wildcards and defers to after-commit, which is a behavior change. `services/ImageProcessingService.java:838-863` re-implements what `services/ReadCacheInvalidator.java:79-106` already owns. Give `ReadCacheInvalidator` an `invalidatePaths(List<String>)` and delegate. ~25 lines.

## MR 17 — Controllers

- [ ] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.java:255-291` vs `ContentControllerProd.java:45-77`). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. **"Reuse prod's constraints" is an unpriced behavior change**: admin clamps with `Math.min(Math.max(size, 1), 200)` while prod validates with `@Min/@Max`, so admin `size=500` goes from silently returning 200 rows to a 400; defaults also differ (50 vs 30), and two frontend pages that pass no `size` would jump from 30 images to 50. **Do MR 19 #19 first** -- it is the same decision from the other direction, and #7 then shrinks to sharing the filter record. Realistic ~70 with test.
- [ ] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController:343-360` -- `addUserToRole` / `removeUserFromRole` -- vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:149-166` -- `addMember` / `removeMember`). Keep the roles-side pair. **Blocker resolved 2026-08-24: the frontend uses BOTH**, driving two different screens (`RoleDetailView.tsx` calls the roles-side route, `UserRolesSection.tsx` the users-side). So this is a coordinated cross-repo change with deploy ordering, not a backend delete -- cheapest path is making the users-side method delegate to the roles-side one, leaving components untouched. **PR #191 lowered its priority**: both pairs now route through the guarded `RoleRepository.addMember`, so this is tidiness, not security. Scope must also include that method's docblock, which says "the two admin endpoints that reach here".

## MR 18 — Services

- [ ] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`ImageUploadPipelineService.java:279-392` vs `405-524`), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. **Three copies, not two** -- the CREATE/UPDATE arms inside `processFilesFromDiskLoop` are a third. Net deletion ~110, better than the stated ~85, and all source: **zero forced test churn**.
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.java:581-653` vs `ContentMutationUtil.java:199-259`). **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, which are declared on subclasses, not `ContentEntity`. The fix needs a return-the-set signature, not a retype, and it converts `ContentServiceTest.updateGif_persistsPeopleAndLocations` into a weaker test. Realistic ~180, not ~40.
- [ ] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService.java:460-490` (`validateNoLinkCycle` and a byte-identical `parentIdsOf`). One `walk(root, neighborsFn)` helper. **Five walks, not four** -- `propagateToVisibleSubtree` is a fifth the line range missed. ~95 lines, zero test churn, pinned by 33 integration tests. **Best value in MR 18.**
- [ ] #12. `nextOrderIndex` logic. **Five places, not four** -- `TagService` is the fifth. Do it by keeping `ContentService.nextOrderIndex` as a one-line delegate, which makes test churn zero; the naive version costs 15 stub edits in `ImageUploadPipelineServiceTest` for 5 lines of dedupe. **Do it the delegate way or not at all.**
- [ ] #13. Entity-to-Record mapping and case-insensitive sort duplicated across four files (`Records.Tag` mapping at `ContentModelConverter:343`, `MetadataService:434-436`, `SyntheticCollectionResolver:153`, `ContentService:1025`; Location mapping/sort twice). Static `from(entity)` factories on the records. **Counts are 10 tag + 4 location sites, not 6+2, and the estimate is the worst on the board: net ~0 lines**, because every copy and every replacement is one line. The suggested fix also flips the layering -- `Records.java` currently imports nothing from `entity`. **The finding worth keeping is not the dedupe**: `ContentModelConverter` and `CollectionProcessingUtil` sort their output and `MetadataService`/`SyntheticCollectionResolver`/`ContentService` do not, which is a live API-ordering inconsistency. Split that out and drop the rest.

## MR 19 — Query efficiency and data layer

- [ ] #14. `convertEntityToModel` loads the same content row twice (`ContentModelConverter.java:103-118`) — `findAllByIds` already returns typed subclasses, so drop the second typed fetch. Verify COLLECTION hydration first. Called 3x per GIF/text mutation.
- [ ] #15. `getUpdateCollectionData` fetches the collection row twice and has an always-true null check (`CollectionService.java:822-848`).
- [ ] #16. `findCurrentContentCollections` is an N+1 loop. **The best value item in Wave 5** -- and worse than described. `SELECT_CONTENT_COLLECTION` inner-joins `content_collection`, so every non-COLLECTION row returns empty: a 200-image collection removing one sub-collection issues **201 queries, 200 of them wasted**. It is on the write path, not public reads, which caps the impact. **Test coupling is one mock line** and the method is private. Best fix is a single query filtered by `cc.id IN (:ids) OR cc.referenced_collection_id IN (:ids)`, better than the item's two-query suggestion.
- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (now **140-152 and 220-237**, was 85-130; the file went 130 -> 238 lines under S-7/S-9, so re-read before quoting -- into `findLiveInvite`); pagination normalization re-inlined at `CollectionService:127-130` (call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil:569-596, 939-947`); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService:697-745`); EmailService HTML skeleton twice (optional, ~35 lines).

  **Two sub-items struck 2026-08-24, both premises dead:**
  - *`ensureDimensions` twins* -- already refactored. The shared work is hoisted into
    `putDimensionsFromHeader`; what remains is two 6-line wrappers differing only by log message.
  - *EXIF-versus-ISO format detection duplicated between the two date parsers* -- **the premise is
    false**. `parseImageDate` does no format detection at all: it splits on `[: T-]` and takes
    numeric runs. There is no second copy and nothing to fold. What IS real, and was noted in the
    history file but never given a row: `parseImageDate` returns **month 13** for a nonsense date
    and builds an S3 path from it. That is a robustness bug, not a consolidation -- see the row in
    "Decisions needed".
- [ ] #18. `EquipmentRepository` repeats each SELECT column list **3-4 times per list across 3 lists** (not "6+ times" -- cameras 4x, lenses 4x, film types 3x, so ~15-20 lines not ~25) while sibling repositories hoist constants (`AppUserRepository`, `ShareLinkRepository`, `WebAuthnCredentialRepository`, `CollectionRepository` all do it right). Hoist per-entity constants. ~25 lines.
- [ ] #19. `model/ImageSearchResponse.java` is a strict subset of `model/PagedResponse.java`. Replace it with `PagedResponse<ContentModels.Image>`. **Unblocked 2026-08-24**: the frontend reads only `result.content`, never `totalElements`/`totalPages`, and ignores unknown keys, so growing the contract is safe. `AdminController` already re-wraps into `PagedResponse`, so 4 more lines vanish as a bonus. **Do this before MR 17 #7.**
- [ ] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService:912-914`. Rename the record `FilmFormatOption`, import the enum, one static factory.

---

# Wave 6 — Conventions

## MR 20 — The bare-array decision (breaking; coordinate with the frontend)

- [ ] Decide first. **17 endpoints** (the prose said 15; the item's own list has always had 17, and 17 is what a re-derivation finds) return top-level JSON arrays against the stated "objects only" rule: `AdminController:85`; `AdminUserController:153, 328, 383, 396`; `AdminRoleController:49`; `CollectionAdminController:43`; `ContentControllerProd:85, 96, 107, 118, 130`; `UserFollowsControllerProd:58`; `UserSavesControllerProd:56, 65`; `UserSelectsControllerProd:59`; `UserRatingOverrideControllerProd:58`. `CollectionAdminController:37` even documents the violation as policy. Either wrap them in one breaking-change MR, or amend `.claude/CLAUDE.md` to bless bare arrays. Today the codebase carries two contradictory conventions.

  **Frontend answer, 2026-08-24: it consumes bare arrays directly** at 20 call sites across
  `app/lib/api/{adminHome,roles,users,personal,selects,content}.ts`, typed as `T[]`. So wrapping is
  breaking for 13 of the 17. Backend cost is 17 source sites against **92 array-shape assertions in
  25 test methods across 8 files**, plus 15 frontend test files.

  **The de-risking split the item does not offer:** four of the 17 have **no frontend consumer at
  all** -- `/api/read/content/people`, `/cameras`, `/lenses` and `/api/read/user/rating-overrides`
  (the last has no backend controller test either). Those four can be wrapped today with zero
  coordination, which settles the convention question in code before negotiating the breaking 13.

## MR 21 — Untyped Map bodies and responses

- [ ] Admin write surface. **Re-derived 2026-08-24: 19 controller sites, not 15** -- the original
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
  behavior, which is the point. ~27 source sites against 59 Map-shaped lines in 17 test files; ten of
  the affected endpoints have zero test references at all.

## MR 22 — Remaining convention sweeps

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd:59` (serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the three different 429 body shapes currently in play: empty at `AuthController:65`, Map at `CollectionControllerProd:182-183`, ErrorResponse at `MessagesControllerPublic:48-52`).
- [ ] Try-catch in controllers, **two sites** (not three -- the third went with bug #15 in MR 7, [#168](https://github.com/themancalledzac/edens.zac.backend/pull/168), confirmed gone by grep): `AdminUserController.mergePreview` and `.merge` (map via `ResourceNotFoundException` plus a new `ConflictException` handler). **Both methods have zero tests**, so this is an untested behavior change on two admin endpoints -- a risk, not a saving.
- [ ] `@Value` field injection: **9 sites, not 3.** The three named (`CollectionControllerProd`, `ShareControllerProd`, `DownloadUrlService`) plus six in `S3Config` and `SesConfig` that feed `@Bean` methods -- same rule, same fix, and they fold into MR 16 #4. Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly four `ReflectionTestUtils.setField` calls. Also `@Autowired` on constructors at `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 3 comments**: only `AuthLoginLimiter` has a single constructor; the other three genuinely have two, where the second is the package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.
- [ ] Fully qualified names inline: **14 sites, not 6.** The six named (`CollectionService.isGalleryAccessAuthorized`'s parameter -- the doc's `542`, then `533`, is now `534`, which is the third correction to one ref and the reason this item now names symbols; `CollectionProcessingUtil`, `TagViewResolver`, `GalleryAccessCookies`, `ContactMessageLimiter`, `Records.java`) plus eight in the data layer the original scan missed: `BaseDao` (3), `CollectionRepository`, `EquipmentRepository` (3), `PersonRepository`. Import-only, **zero test coupling**. `Records.java` still needs consolidation #20 first (the `FilmFormat` name clash).
- [ ] `Optional.get()` -- **46 sites, not 17.** *(45 -> 46 on 2026-08-24: S-1 added `maybeUser.get().getStatus()` to `AuthController.login`, taking that file 3 -> 4. Re-derived after the merge, not estimated -- the raw sweep went 56 -> 57 and the one new line is S-1's. This is the inventory rot working rule 5 warns about, caught by the scoped sweep rather than a full pass.)* The 17 named are all still present; 29 more sit in twelve files the original scan never covered (`AdminUserController` 4, `AuthController` 4, `InviteController` 3, `ImageProcessingService` 5, `UserMergeService` 3, `UserShareControllerProd` 2, `ClientGalleryAuthService` 2, `SessionService` 2, and one each in `LocationRepository`, `TagRepository`, `AdminBootstrap`, `ImageUploadPipelineService`). A raw `.get()` sweep returns 56 lines; 11 are `AtomicInteger`/`AtomicReference`, not `Optional`. **Re-derived again 2026-08-24 after S-7/S-9, and the headline number survived for the wrong reason.** The raw sweep is still 57 and the Optional subset still 46 -- but two files moved and cancelled out: `InviteController` went **3 -> 2** (S-7 moved the accept body into the service) and `UserInviteService` went **2 -> 3** (`accept` added its own `maybeInvite.get()`). A total that holds while its components move is the most misleading state an inventory can be in, so trust the per-file breakdown here over the headline. *Re-derived a third time 2026-08-24 after S-8: raw sweep **still 57**, Optional subset **still 46**, and this time for the right reason -- S-8 added no `.get()` at all (`AdminUserController` holds at 4, `SessionService` at 2). Two consecutive checks now agree on both the total and the breakdown.* Zero test coupling. **This is not an MR** -- the doc's own "rewrite opportunistically when touching these methods" is the right disposition, now with the real denominator.
- [ ] Magic number 2500 at both resize call sites (`ImageProcessingService:192, 292`). Name it.
- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment (`JobTrackingService`). **Split the item**: making it an enum is COLD and non-breaking (Jackson serializes an enum to the same string), but costs ~45 test references across `AdminControllerTest` and `ImageUploadPipelineServiceTest`. Adding `COMPLETED_WITH_ERRORS` instead of flipping a 500-file job to FAILED over one error is
  **UNBLOCKED as of 2026-08-24** -- the check was run and there is no frontend job-status poller at
  all. `jobId`, `JobStatus`, `job.status`, `jobStatus`, `/jobs/` and `from-disk` return zero hits
  across the whole frontend `app/` tree, and no code compares against `'COMPLETED'`/`'FAILED'`/
  `'PROCESSING'`/`'PENDING'`. The backend returns a `jobId` for polling that nobody polls. So the new
  enum value breaks no consumer -- **and the more interesting finding is that the whole job-status
  endpoint may be dead**, which belongs in Appendix C rather than being fixed here.
- [ ] Verb-style routes `POST /collections/createCollection` and `POST /content/content` (plus a third the item missed, `GET /api/admin/collections/{slug}/update`). **Both confirmed live in the frontend** (`app/lib/api/collections.ts`, `app/lib/api/content.ts`, one caller each), with 61 backend test references. The alias half is COLD; the retire half needs a frontend release.
- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean with a hand-built 400 (`CollectionAdminController`). **This is an undeclared wire change**: today a failure returns 400 with a `GalleryAccessResponse` body; through an exception it returns 400 with `GlobalExceptionHandler.ErrorResponse`. 30 test references across 4 files. **Checked 2026-08-24 and the answer is yes, so this stays
  BLOCKED and is now precisely specified.** `saveGalleryAccess` in the frontend's
  `app/lib/api/collections.ts` reads `result.saved` and `result.reason` straight off the 400 body and
  rethrows as `ApiError(result.reason ?? <fallback>)`. Routing through `GlobalExceptionHandler` would
  return `ErrorResponse` instead, so `saved` and `reason` both come back undefined and the admin UI
  silently degrades to the generic fallback message. **The blocker is a frontend change, and it is
  small**: have the frontend read the `ErrorResponse` shape first, then land the backend change.

---

# Wave 7 — Structure

## MR 23 — Package moves (rename-only)

- [ ] `controller/user/` is a one-class package (`UserRatingOverrideControllerProd`) that belongs with its five siblings in `controller/prod/`.
- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and
  `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/`ContentRequests`/`CollaboratorRequests`
  (`model/`). Move the three strays into `model/`. **The strongest argument for this is not in the
  item**: `UserMergeService` imports `UserRequests` from a controller package, so a service currently
  reaches up into the controller layer. Also unlisted and the same smell: `GrantableLevel` and
  `GrantableLevelValidator` are non-controller types sitting in `controller/admin/`. Eleven files,
  zero net lines.
- [ ] Optional: drop the `*Prod` suffix now that no controller carries `@Profile` (verified: the only two `@Profile` hits under `controller/` are javadoc text saying there is no gating). **10 main classes plus 10 test classes, 23 files touched -- much the largest item in MR 23, and it should not share an MR with the two cheap moves above.**

## MR 24 — Service extraction and remaining design items

- [ ] `AdminUserController` is a service wearing a controller's clothes: two repositories and **seven** services injected (was six; S-8 added `SessionService`) plus a `frontendBaseUrl`, **481** lines (469 -> 474 -> 481 across S-9 and S-8), entity building, multi-step `@Transactional` orchestration, afterCommit hooks. Extract an `AdminUserService`. **Largest real cost in Wave 7**: ~200 source lines move, but `AdminUserControllerTest` is **1,183** lines (1,015 -> 1,097 -> 1,183 across the same two MRs) and is the hidden half.

  *Positional refs replaced with names 2026-08-24, per working rule 5 -- this item's range list had drifted twice in two days.* The `@Transactional` orchestration blocks are `createUser` (`:114`), `regenerateInvite` (`:174`), `upgradeUser` (`:208`), `updateUser` (`:279`) and `merge` (`:436`); the afterCommit hook itself is `sendInviteEmailAfterCommit` (`:468`), called from the first three. **The item is growing faster than it is being done** -- two consecutive security MRs each added to the exact class this proposes to split, and the test file has grown 168 lines in two days. That is an argument for doing it sooner, not a reason to keep re-measuring it.
- [ ] Same shape, smaller: `UserShareControllerProd:124-152` computes grant and candidate sets inline with a repository. Move it into `ShareLinkService`.
- [ ] `Synthetic.blogsOnly` is a constant at its only reachable call site (`SyntheticCollectionResolver:42-49, 86-92`), a transitional shape from the type-keyed catalog. Fold it out.
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` repeats its 3-line null check **six** times, not four; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched. **~199 source lines across 3 files, not ~60**, plus `@Mock` removal in 6 test files and a constructor arg off 4 services -- a 10-file change, so "when next touched" is right.
- [ ] Executor handling in `ImageUploadPipelineService`: `rawUploadExecutor` now runs whole disk and ingest jobs, so it is misnamed and mis-documented, and `shutdown()` shuts down both executors but awaits only `rawUploadExecutor`. **All three sub-claims verified 2026-08-24. This is a real bug (an unwaited executor on shutdown), not a design note** -- ~10 lines in one file. Promote it out of the "remaining design items" list.
- [ ] `AdminHomeService`'s AtomicReference cache has no TTL and is per-instance. Fine single-node; note it for any multi-instance future.
- [ ] Service decomposition, the standing item. **Recounted 2026-08-24 by `wc`, and the argument is
  stronger than when it was written.** The four files are `CollectionService` **1,746**,
  `ContentService` **1,014**, `ImageProcessingService` **1,390**, `CollectionProcessingUtil`
  **933** -- all four quoted numbers were stale. The total went 5,107 -> **5,083 across 24 merged
  MRs of dedicated cleanup: a net -24 lines, half a percent** -- and `ImageProcessingService`
  **grew 25**. "Waves 5-7 shrink these" is not what the data shows; the waves have been shrinking
  other files. Decide the split boundaries before the next feature lands in them. **COLD -- this
  needs a decision, not research.**

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components appears in **11** test files (not 12; 13 call sites), **7** of which have their own private helper. Same for `CollectionRequests.Update` -- the canonical record has **21** components and the deletion target is the **17**-arg compat constructor, at **24** call sites across 7 files. One `TestFixtures` class with builders. **The doc underestimates by ~2x in the good direction**: measured, those sites are **745 lines of positional construction**, replaced by roughly 120, so **~-600 net, 18 test files, and zero main files touched.**
- [ ] `services/CollectionServiceTest.java` (**2,644 lines**, not 2,412 -- it grew 232 since baseline, so the estimate below is measured against the wrong denominator): assert/verify twins where the second test re-runs the first's stubbing and re-checks with `verify` — for example `createCollection_happyPath...` (:128) versus `createCollection_verifiesEntityCreatedViaUtil` (:157); the `deleteCollection` plain-verify test (:188) is a strict subset of the inOrder version (:216). ~250 lines.
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
- [ ] `ImageUploadPipelineServiceTest`'s 1:1 verify ratio suggests some verify-only tests worth a pass.

### Positional constructors that block the `TestFixtures` pass

*(Retitled 2026-08-24. The old title said "Main-dead, test-live (carried from MR 1a)" and neither half held: `AuthPrincipal`'s 4-arg constructor has a main caller, and MR 1a's own history records it as not-dead rather than deferred.)*

These have many test callers, so deleting them rewrites working call sites to pass explicit nulls.

**The "do them in the SAME pass as the `TestFixtures` builders" claim is true for exactly one of
them** (verified 2026-08-24), not for the set. It holds for `CollectionRequests.Update`, whose 17-arg
sites are precisely the sites a builder collapses -- doing them separately rewrites the same 21 sites
twice. It does **not** hold for `FileEntry`, `resolveCollectionDownloadEntries` or
`DownloadResolution.extension`: none has a builder proposed, and none shares a call site with either
fixture target. Bundling them makes the MR bigger for no reason.

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **21** test call sites (not 23). **This is the one that must ride with the `TestFixtures` pass.**
- [ ] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor, **13** test call sites (not ~20; 28 `FileEntry` constructions across all arities).
- [x] `model/AuthPrincipal.java` -- 4-arg constructor. **DECIDED 2026-08-24: leave it.** It is not main-dead (`SessionService` calls it), so it never belonged under the old heading. All 30 call sites are one-liners; deleting a 3-line convenience constructor to append `, null` at 29 clean sites is not an improvement. Closing this rather than carrying the hedge a third time.
- [ ] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload, 5 test call sites.
- [ ] `model/DownloadResolution.java` -- the `extension` component: **5** construction sites (not 4)
  and 6 assertions in test. **"Written, never read" is misleading and the phrasing invites a
  mistake.** The record *component* is never read in main, true -- but the local `extension`
  variable in `ContentService` is load-bearing: it feeds `sanitizeFilename` and decides the download
  filename's extension. Removing the component is a 2-line change and does **not** let you delete
  the extension logic. Worse on the test side: **4 of the 6 `.extension()` assertions are the only
  coverage of the collection-ZIP original-to-web format fallback** (the two single-image ones are
  backed up by `.filename()` assertions; the ZIP ones are not). They must be rewritten as
  `.filename()` assertions, not deleted. The stale docblock claim holds -- downloads are presigned
  302 redirects, they do not "stream the response".

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
  the endpoint whose bare-array response has no test either.)*
- [x] **Two guard tests that cannot fail, proven by mutation. DONE 2026-08-24** -- S-3
  ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)) and S-4
  ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)). Bug #1's delete-person
  guard reddens one test when its SQL predicate is stripped; `ProdSecretGuard` reddens two when
  `@PostConstruct` is deleted and a third when `@Profile("prod")` is. The mutations and their
  results are recorded with the security items and in working rule 15.
- [ ] **MR 11's headline security fix is untested.** Moving eight throw sites to bare
  `RuntimeException` -- so `WebAuthnService` and `JdbcUserCredentialRepository` stop echoing
  `app_user` ids and WebAuthn handles to unauthenticated callers in a 400 body -- has zero coverage.
  There is no `JdbcUserCredentialRepositoryTest`, and `WebAuthnServiceTest` never touches those
  messages.

Verified good, for the record: `AdminUserControllerTest` is real behavior testing; the auth-table truncation fix landed in `AbstractPostgresIntegrationTest`; no tests mock the deleted `collection.type` shape.

---

## Session log

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph. Three
entries in a row ending `Next: X` means X is being avoided -- say so and either make it real work or
drop it. (Checked 2026-08-24: not currently tripped. Two entries ended `Next: MR 15 #6` and it then
shipped.) The verbose pre-split log is in the
[history file](2026-08-22-backend-cleanup-history.md).

- 2026-08-23 — shipped MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) and the tracker/history split ([#186](https://github.com/themancalledzac/edens.zac.backend/pull/186)); merged [#185](https://github.com/themancalledzac/edens.zac.backend/pull/185). Added working rules 11 and 12. Reconciled Waves 1-3 and surfaced 8 live items from "complete" waves. Re-derived MR 15 #2 (17 guards -> 18, one controller in the wrong package). Next: MR 15 #2.
- 2026-08-23 — shipped MR 15 #2 ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)); merged [#188](https://github.com/themancalledzac/edens.zac.backend/pull/188). One matcher replaced **17** guards -- yesterday's re-derivation had counted a javadoc line, and its guardrail's dev-convenience premise was false, so the placement decision it framed as a tradeoff had only one behavior-preserving answer. Added working rule 13. Wave 5's first item is closed. Next: MR 15 #6 (`currentUserId` onto `AuthPrincipal`).
- 2026-08-24 — verified [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189) merged (`9d15784`). Re-derived MR 15 #6 and it is **four copies, not three** -- `CollectionService:549` was missed entirely and is not a controller, and the item's stated fix (put it on `AuthPrincipal`) does not work, because that is a Spring-free record and this is a static context read. Wrote its guardrail: both null returns are load-bearing, for two different reasons. Corrected a stale MR 22 ref (`CollectionService:542` -> `533`) found on the way past. Added the working-rule-12 corollary on writing comments, after MR 15 #2 over-wrote one. Next: MR 15 #6.
- 2026-08-24 — shipped MR 15 #6: four `currentUserId` copies became `config/CurrentUser.userId()`
  (main -26 lines / +36 words), and **closed the `PersonRepository.findAccountUserIdsByIds` carry**
  rather than let it reach a fifth. That one was decided, not deferred: zero callers in main *and*
  test, so its "only accounts get grants" rule was unenforced; the method was the wrong shape (a
  `List` query for two single-id call sites), so it was deleted and the rule enforced at
  `RoleRepository.addMember`, the choke point both admin controllers share. Low severity and said
  so -- admin-only routes, and a PERSON row cannot log in; the risk is a dormant grant. The item's
  "move it onto `AuthPrincipal`" was rejected with a reason. The null contract was left alone and
  costed instead, and it turns out to be two separate problems, not one. Added working rule 14
  after finding two more copies of the same read that a name-grep could not see. 1304 tests green.
  Next: MR 16.

- 2026-08-24 — **full critical review of the board**, mirroring the pass the frontend ran on its own.
  Eight parallel read-only agents (ref verification, premise+estimate+COLD/BLOCKED by wave,
  adversarial security re-review, regression hunt, board reconciliation, cross-repo), then every
  correction applied in one pass. Headline findings, each verified personally rather than taken on
  report: **two new security findings** — `UserStatus.DISABLED` is enforced nowhere in the auth path
  (S-1, HIGH), and MR 15 #6's own `addMember` guard is bypassed by `UserMergeService`
  (S-2). **Two guard tests proven unable to fail by mutation** — all 1,304 tests pass with bug #1's
  delete-person guard stripped, and all 1,304 pass with `ProdSecretGuard` unwired (S-3, S-4;
  working rule 15). **Main is not shrinking**: the four MR 24 services went 5,107 → 5,083 across 24
  MRs, and `ImageProcessingService` grew. Of ~130 refs in open items, 79 had drifted, 3 were dead
  and 11 carried a wrong claim. Four premises were FALSE (`role.kind`, `AuthPrincipal` main-dead,
  MR 25's same-pass coupling, consolidation #17's EXIF/ISO half) and two sub-items were struck.
  Nothing in Wave 5 was genuinely blocked — every "blocked" item was answered by one grep of the
  sibling repo or one live request. Gave board rows to six findings that had prose and no checkbox,
  renumbered the colliding bug #16 → #17, moved `effectiveLevel` out of a comments wave into the new
  security section, and moved 56 lines of closed write-up to history per rule 11. Added working rule
  15 and widened rule 5 beyond Wave 4. Answered all three Appendix A questions and all three
  cross-repo questions; found one live broken feature (`POST /api/read/user/share/email` is called
  by the frontend and does not exist). Next: S-1, then MR 19 #16.

- 2026-08-24 (close-out) — resynced the palace backend wing (was 2 months stale at `c135980`, now
  `d4a1307`, verified by content not status: `AuthPrincipal` indexes as the current 5-arg record
  again). Parked the gallery-password decision on the user's call — neither "accept plaintext" nor
  "redesign the fingerprint", because both skip what we want the passwords to do; direction is likely
  hashed, to be designed. Scoped the park narrowly so S-1 and the `isPasswordProtected` field are not
  blocked by it. **Closed two blocked items by looking**: `COMPLETED_WITH_ERRORS` is unblocked (there
  is no frontend job poller at all — which surfaced a new lead that the whole job-status endpoint may
  be dead), and the gallery-access `saved()` item stays blocked but is now specified (the frontend
  does read `result.saved`/`result.reason` off the 400 body). **Found six stale worktree branches the
  board never knew about**, none with an open PR; three hold zero commits, and
  `0257-backend-security-bugs` turned out to be fully superseded by MR 5 *and* the origin of the S-4
  untestable-test pattern — filed with a "do not rescue it" note. Gave the three non-wave sections
  rows in the Progress table. Next: S-1.
- 2026-08-24 — shipped **S-1** ([#192](https://github.com/themancalledzac/edens.zac.backend/pull/192)):
  `UserStatus.ACTIVE` now required at `AuthController.login` and `SessionService.resolve`. Both
  guards mutation-verified per working rule 15 -- stripping the login guard turns the DISABLED
  login into a **204**, stripping the resolve guard hands back a principal for a disabled account.
  1304 tests -> 1308. Shipped the allowlist over `<> DISABLED` because it also fails closed for
  `PERSON` and `INVITED` and for whatever status is added next. Folded the login guard into the
  existing dummy-BCrypt branch rather than adding one after the password check, so the non-ACTIVE
  case keeps the same timing as unknown-email instead of opening a sharper enumeration oracle.
  `AuthPrincipal` left alone; the cost report is in the history file and the finding is that the
  field could only ever hold `ACTIVE`, since the principal is built inside `resolve` immediately
  after the guard. **Scoping S-1 found S-7**: `sessionService.create` has three callers, not the one
  the item named, and `InviteController.accept` flips status to ACTIVE unconditionally, so a
  disabled account holding an unexpired invite re-activates itself. Split revocation-on-status-change
  out as S-8. Added working rule 16. Next: S-2.
- 2026-08-24 — shipped **S-2** ([#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)):
  `repointMemberships` now carries the same `status <> 'PERSON'` test `addMember` enforces, and
  drops the source's membership rows when the target cannot hold them. Mutation-verified at two
  levels -- stripping the predicate reddens one DAO test and one service test. 1308 tests -> 1312.
  Rejected the tempting fix of constraining the merge target in `requireMergeable`: PERSON-into-
  PERSON de-duplication is a normal operation, so that would have closed the hole by breaking the
  feature. Added working rule 17. Found on the way in that `UserMergeIntegrationTest` already
  existed and the drafted `UserMergeServiceIntegrationTest` was a duplicate -- the tracker's
  "neither test added by #191 touches `repointMemberships`" understated it, since **no test anywhere
  did**. Next: S-3 or S-4, the two PROVEN-untested items.
- 2026-08-24 (close-out) — verified **both merged**: S-1 `bc01452` (#192, squash) and S-2 `f2cad5e`
  (#193, squash), all CI green. #193 needed a `rebase --onto main` because #192 squash-merged, so
  its original commits were never on `main` by SHA. Scoped drift sweep over the eight files the two
  MRs touched found **one inventory claim rotted**: `Optional.get()` 45 -> 46 sites, because S-1
  added `maybeUser.get().getStatus()` (raw sweep 56 -> 57, exactly attributable) -- corrected in
  place. **Found a premise S-1 falsified about itself**: its "a DISABLED account in a role is a live
  grant, not a dormant one" argument for tightening `addMember` to an ACTIVE allowlist died when
  S-1 closed the auth-path hole; filed under "Verified sound, do not re-open" and used to sharpen
  working rule 8. Re-verified S-3's and S-4's premises against `f2cad5e` -- all still hold, both
  now **COLD** with exact anchors (`PersonRepository.java:215`, `ProdSecretGuard.java:31`) and both
  mutation baselines corrected 1,304 -> 1,312. Next: S-3.
- 2026-08-24 — shipped **S-3** ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195))
  and **S-4** ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)), both merged,
  all CI green. 1312 -> 1317 tests, no source change in either. **Both PROVEN-untested items are now
  closed, so working rule 15 no longer describes a live hole** -- annotated in place rather than
  rewritten, since the rule stands. S-3: two DAO tests; stripping the delete-person predicate now
  reddens exactly one test of 1,317, which proves the guard *and* re-confirms nothing else covered
  it. Put it in a new `dao/PersonRepositoryIntegrationTest` rather than `UserMergeIntegrationTest`
  as the item suggested -- `dao/` is where this repo puts DAO guard tests (S-2's own included), and
  one of S-3's premises was that `find src/test -name "PersonRepository*"` returned nothing.
  Declined to make `deletePersonById` throw and costed it instead: two callers wanting opposite
  policy, `MetadataService` already converts 0 to a 404, and the change would move HTTP status into
  a DAO while making `MetadataServiceTest` test strictly less. S-4: a `@Nested class Wiring` using
  `ApplicationContextRunner` (new to this repo); `@PostConstruct` deleted reddens two, `@Profile`
  deleted reddens a third. Removed the duplicate `enforceAuthzDisabledThrowsEvenWithAGoodSecret`,
  but **corrected the item's reason for calling it one** -- its assertion *can* be false, it is just
  a wording assertion. New trap, folded into working rule 15: restoring a mutation with
  `sed -i.bak` + `mv` leaves the source older than the `.class` built during the mutation run, so
  the next `mvn test` silently runs mutated bytecode -- cost one confusing red run. **Settled S-7 by
  looking** rather than leaving it vague: all premises verified against `4abb28e` with anchors, and
  `UserStatus.INVITED` turns out to make the fix a specified allowlist rather than the product call
  the item implied. That verification opened **S-9** (disabling a user does not invalidate their
  outstanding invites; `invalidateInvites` has exactly one caller, the email-change path). Next: S-7.

- 2026-08-24 — shipped **S-7** ([#199](https://github.com/themancalledzac/edens.zac.backend/pull/199)), the last live hole on the board, and shipped **S-9** ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)). Both halves of S-7 in one MR as specified. **The item's stated fix was wrong**: "require `INVITED`" would have broken admin-issued password reset, which redeems through the same endpoint for an ACTIVE user -- caught by reading `regenerateInvite`'s docblock before writing the guard, not after. Shipped `{INVITED, ACTIVE}`; the allowlist *form* still mattered because `UserStatus.PERSON` exists. Added working rule 18. Review then moved both guards out of their controllers into `UserInviteService` and replaced the comment blocks with a named `mayAcceptInvite` predicate, which incidentally closed the S-7/S-9 drift risk the S-9 item had flagged — one rule, two call sites. Added working rule 19. Suite 1,317 -> 1,328. Also, unrelated to the board: an EC2 deploy failed on a full 8GB root volume, fixed with a disk preflight in `deploy.sh` ([#198](https://github.com/themancalledzac/edens.zac.backend/pull/198), [#201](https://github.com/themancalledzac/edens.zac.backend/pull/201)) — the threshold in #198 was set by guess, aborted a legitimate deploy, and #201 corrects it from measured numbers and makes it overridable. Next: S-8.

- 2026-08-24 — shipped **S-6** ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)). **The security board is closed**: nine items, all done. **The item told the truth about its own scope and was still short by one.** It said rule 20 is a policy, not a ruling about one service, so enumerate before fixing -- and the enumeration found six admin-denial sites against the two the item named. The sixth, `UserSavesService.add` 404ing an admin, appeared in no item on this board and its check lives in SQL rather than in `CollectionAccessService`; it is fixed above the query on purpose, because that query filters on several read paths and an `is_admin` term inside it would apply where nobody looked. **The specified fix would have widened share links if typed in verbatim**: `effectiveLevel` adds two branches, not one, so routing `canView` through it hands a flyby GENERAL and turns a share link into a second way past the gallery password prompt. The two gates screen with `AuthPrincipal.isRealUser` first, which is exactly what the old `userId != null` did. That makes **four consecutive items whose specified fix needed adjusting at implementation time** (S-7, S-8, S-5, S-6) -- recorded in the history file as a pattern rather than a run of luck. **Four list-scoping sites were deliberately not fixed** and the reasoning written down, because rule 20 settled bouncing and not scoping. Suite 1,341 -> 1,347; three mutations verified red. Next: nothing on this board.
- 2026-08-24 — shipped **S-5** ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)), which leaves **S-6 as the only item on the security board**. **The interesting part of the fix was the half the item did not specify**: it named the bypass (`getContentLengthLong()` returns -1 for chunked, so `-1 > 16384` is false) but not that a *bodiless* request reports -1 too. `MockHttpServletRequest.getContentLengthLong()` returns -1 whenever content is null, which is most of the existing suite -- so the obvious one-line fix, `reject if length < 0`, 411s every bodiless public request. The shipped guard is `length < 0 && Transfer-Encoding present`, and **the mutation run proved two pre-existing tests already caught the over-broad version**, which is the reverse of the usual working-rule-15 result. **Working rule 16 came back empty and that was worth recording**: `getContentLength` has two hits in the codebase, both in the same method, so unlike S-1 and S-8 the item's named site really was the only site. **The limiter-merge guardrail held and paid**: MR 19 #3's numbers were re-measured rather than repeated (every one held, exactly -- 7+24 and 7+32 test sites) and reading the three call sites turned up a blocker the board did not have, that `RateLimitFilter` needs `estimateAbilityToConsume` for `Retry-After` and so cannot use a `boolean allow(key)` signature. That item's verdict moved from "low priority" to "not worth doing". **Test:source was 3.6:1, the fourth near-3:1 in a row** and the smallest source change of the four, which suggests the ratio tracks the guard tests rather than the fix. Suite 1,338 -> 1,341. Next: S-6.
- 2026-08-24 — close-out pass after #204. **Fixed three drifted refs**, and for the first time one sat *outside* the merge neighborhood: `AdminRoleController:150-167` was off by one (`149-166`) and nothing in #204 touched that file, so working rule 5's third principle held only for two of the three. The others were in-neighborhood as expected -- `#8`'s `AdminUserController` pair 336-353 -> 343-360, and the bare-array sites 149/321/376/389 -> 153/328/383/396. **Wave 7's `AdminUserController` item was re-measured and then de-positionalized**: its range list had drifted twice in two days, so the four `@Transactional` ranges are now named methods with start lines. That item is also growing faster than it is being done -- 469 -> 474 -> 481 source and 1,015 -> 1,097 -> 1,183 test across two consecutive security MRs, both of which added to the exact class it proposes to split. **`Optional.get()` re-derived clean**: 57 raw / 46 Optional, and this time the breakdown agrees too, unlike the 2026-08-24 pass where two files cancelled out. **S-8's test:source ratio was 2.7:1, the third near-3:1 in a row** -- recorded in the history file as confirmation of a pattern the board had already priced into two open items. **S-6 put to the user rather than left on the board a third time, and it came back wider than asked** -- not "yes, admins through" but "admin means OWNER, never any password restriction, never any permission issue", which is now working rule 20 and widens S-6's scope from two methods to a sweep. The security section is now 2 open, 0 blocked. Next: S-5, then S-6.
- 2026-08-24 — shipped **S-8** ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)), which closes the security board down to S-5 and S-6. **The item's one open judgement went the other way from its own default**: it said mirroring S-9's `mayAcceptInvite` boundary was the default and any divergence should be argued in the MR, and the argument won -- the session predicate is `mayHoldSession`, ACTIVE-only, because `resolve` has enforced ACTIVE-only since S-1 and a test says so deliberately. `{INVITED, ACTIVE}` would have left demoted accounts holding `user_session` rows that can never resolve, which is the thing the item asked to tidy. The two sweeps now run off two allowlists on adjacent lines of one handler, and that is correct rather than sloppy. Working rule 16 applied unprompted and paid: grepping `updateStatus` found three callers, and the enumeration is why only one gets the call. **The cost report was measured rather than argued** -- the CTE was actually written and the suite run, and the single resulting failure turned out to be the only mutation-detector for S-1, so folding the revoke into the DAO would have quietly disarmed an earlier fix. Suite 1,328 -> 1,338. Next: S-5, or answer S-6's blocking question.
- 2026-08-24 — close-out pass, no code shipped. Reconciled the board after #199/#200/#202. **Fixed five drifted refs**, all in the neighborhood of what merged (working rule 5's third principle held): `#8` 333-350 -> 336-353, `#17`'s `UserInviteService` refs 85-130 -> 140-152/220-237 (that file went 130 -> 238 lines), the bare-array sites 150/318/373/386 -> 149/321/376/389, and Wave 7's two size claims (source 469 -> 474, test 1,015 -> 1,097). **`Optional.get()` held at 46 for the wrong reason** -- `InviteController` 3 -> 2 and `UserInviteService` 2 -> 3 cancelled out, so the headline was right while its components moved; the breakdown is now the source of truth, not the total. **Settled two items by looking**: bug #17 re-verified live at `ContentService:228-233` (premise intact, COLD), and `admin_home_tile.cover_image_id` researched to the end -- though a first pass at that entry got it *wrong* and the doc's existing row was more accurate, which is recorded in the item. **S-8's scope is bigger than its item implied**: there is no user-scoped session revoke primitive, only `revokeByTokenHash`, so it needs a new repository statement plus a service method, not one call. Stamped S-5 COLD, S-6 BLOCKED with the question written out, S-8 COLD. Next: S-8.

---

# Decisions needed from the user

- [ ] **Should `app.admin.enforce-authz=true` become unconditional?** *(New row 2026-08-24. MR 15 #6
  costed the `currentUserId` null contract and found it is two problems, not one: the four
  `/api/admin/**` null sites exist only because the gate falls through to `permitAll` in dev. Making
  the flag unconditional closes all four properly. That is a dev-ergonomics decision, not a
  consolidation, which is why it is here and not in Wave 5.)* Trade-off is local admin convenience
  against an always-on admin gate. The two public-read null sites are correct as they stand and
  should not be touched either way.
- [ ] **Should `parseImageDate` stay permissive?** *(New row 2026-08-24. Noted in the history file
  during MR 13 and never given a row; it also replaces the struck EXIF/ISO half of consolidation
  #17.)* It returns **month 13** for a nonsense EXIF date and builds an S3 path out of it. Either
  reject the malformed date or clamp it -- both are behavior changes, so this needs a decision and
  its own small MR with a month-13 test.
- [ ] **`isPasswordProtected` on the content-block path -- BLOCKING A FRONTEND ITEM.** *(New row
  2026-08-24, from the frontend's cross-repo review; their item C6 is waiting on this.)*
  Recommendation is **Option A: serialize `isPasswordProtected` on `ContentModels.Collection`.** The
  frontend's Option B assumed "BE-H5 already strips the protected cover" -- **that premise is false
  in both halves.** Verified: `ContentModels.Collection` carries `coverImage` and has no
  `isPasswordProtected` component, and the three BE-H5 tests are named `...retainsCoverImage` and
  assert the cover IS returned. The stale section banner above them, which still reads "coverImage
  must be stripped", is what sent the frontend down the wrong branch. Three of the four content-block
  builders (`SyntheticCollectionResolver`, `TagViewResolver`, `ContentModelConverter` before its
  downstream filter) apply no password filter at all, and no read query filters on `gallery_password`.
  **Honest scope: the exposure is latent, not live** -- it needs a collection that is both LISTED and
  password-protected, and prod convention keeps protected work UNLISTED. Nothing enforces that
  combination, though. Fix is one component plus the two-line frontend change already scoped.

- [ ] Bare-array responses: wrap them (breaking) or amend CLAUDE.md (MR 20).
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
- [x] SpotBugs: decided — delete all four artifacts. Done in MR 2. If static analysis is wanted
  later, introduce it fresh at a current version with a filter written from scratch.
- [ ] `admin_home_tile.cover_image_id`: drop in a migration or document as reserved (MR 1, deferred). **Not blocked on research** -- zero Java references, V19 seeds all ten rows explicitly NULL, and nothing since writes it. One query (`SELECT count(*) FROM admin_home_tile WHERE cover_image_id IS NOT NULL`) confirms it never received a value. This needs a decision.
- [ ] `role.kind`. **Premise FALSE, corrected 2026-08-24.** The item says it is "written as constant
  'SHARED' and read by nothing". `RoleRepository` does write `'SHARED'`, but `V45__create_roles.sql`
  writes `'PERSONAL'` in its backfill and joins on `r.kind = 'PERSONAL'` twice more. So the column
  carries **two** values in any database that ran V45 against a non-empty `user_collection`, and it
  is the only surviving marker of which roles the migration auto-created versus which an admin made
  deliberately. "Read by nothing" is true of the Java layer only. **Changes the disposition from
  "likely droppable" to "check prod first, it may be carrying provenance":**
  `SELECT kind, count(*) FROM role GROUP BY kind;`
- [x] Unknown-JSON-key policy (C8). **Answered 2026-08-24 -- nothing to research, the decision just
  needed writing down.** `FAIL_ON_UNKNOWN_PROPERTIES` is disabled (Boot's default; no
  `spring.jackson.deserialization.*` override anywhere), and it is already **pinned by a test** --
  `CollectionTypeAbsentFromWireTest` asserts it is false. So the de-facto policy is "ignore unknown
  keys", it is enforced, and flipping it would break that test and be a breaking wire change. That
  is the argument for leaving it off. Recorded.
- [ ] Partial indexes on `is_blog`/`is_client` (C7, "if scale demands"). **The item names the wrong
  measurement.** "Check request metrics" points at the `request_metric` table, which is readable
  (via `GET /api/admin/metrics/requests` or one SELECT) but counts HTTP requests per route per day
  and says nothing about whether an index helps -- and V44's own header admits those counts
  undercount because of ISR and CloudFront caching. What decides this is table size and selectivity:
  `SELECT count(*), count(*) FILTER (WHERE is_blog), count(*) FILTER (WHERE is_client) FROM
  collection;` plus `EXPLAIN ANALYZE` on the six `CollectionRepository` queries that filter on those
  flags. Confirmed: **no index on either column exists** -- V50 adds them as plain `BOOLEAN NOT NULL
  DEFAULT FALSE` with only a CHECK constraint.

---

# Cross-repo findings owed to the frontend

Raised 2026-08-24. These are backend-side answers or backend-side work that the frontend is waiting
on; the `isPasswordProtected` call is under "Decisions needed" because it needs a decision first.

- [ ] **`POST /api/read/user/share/email` does not exist, and the frontend calls it.** Verified from
  both sides: `emailShareLink` in the frontend's `app/lib/api/share.ts` POSTs to that path, the UI is
  fully built and reachable in `ShareCard.tsx`, and `UserShareControllerProd` declares only a GET,
  `POST /rotate`, and PUT/DELETE on `/collections/{collectionId}`. A grep for `share/email` across
  `controller/` returns nothing. **A signed-in user clicking the share-email button gets a 404
  today.** This is the mirror image of the usual question on this board -- a frontend caller with no
  backend endpoint. Decide whether to build it or have the frontend remove the button.
- [ ] **Actuator defense-in-depth.** The frontend was the only gate on `/api/proxy/actuator/**` and
  has now fixed its side. The backend is already sound and was verified by live probe: exposure is
  `management.endpoints.web.exposure.include=health` only, so `/actuator/env` and `/configprops` are
  not registered, and `InternalSecretFilter` 403s everything except the three health URIs. Probing
  the EC2 origin directly on 8080 with no secret returned `/actuator/health` 200 and `/actuator/env`,
  `/configprops`, `/actuator` all 403. **Two cheap hardening steps are still worth taking**, because
  working rule 1 says an injected env var outranks a property: add an explicit
  `management.endpoints.web.exposure.exclude=env,configprops,beans,mappings,heapdump,threaddump,loggers,shutdown`
  (Boot applies exclude after include, so it survives a stray `MANAGEMENT_..._INCLUDE=*` in a prod
  `.env`), and pin the shipped value with a test that reads `src/main/resources` directly per working
  rule 2. There is currently no test asserting actuator exposure at all.
- [x] **Is `collectionDate` populated? Yes, everywhere** -- list, detail and content-block paths, all
  three confirmed by live request. Backend item #157 merged as
  [#157](https://github.com/themancalledzac/edens.zac.backend/pull/157) and fixed a *third*
  projection, the narrow `findCollectionListEntries` behind `GET /api/admin/collections/metadata`,
  which had been hard-coding null and silently disabling the frontend's blog date ordering. The one
  remaining null is `Records.CollectionList.fromSibling`, by construction.

# Stale side branches

Found 2026-08-24 by `git worktree list` while resyncing the palace. **The board has never mentioned
these and none has an open PR.** Six worktrees, all created before or during this cleanup effort and
left behind while 25 MRs landed on `main` underneath them.

- [ ] **Delete the three that hold nothing.** `feat/collection-debloat` (0 ahead, 117 behind),
  `claude/auth-password-reset` (0 ahead, 38 behind) and `claude/one-way-collection-associations`
  (0 ahead, 38 behind) have **zero unique commits**. They are worktrees holding no work. Per the
  user's standing worktree rule these are theirs to remove, so this is a recommendation, not a
  cleanup to perform unasked. Note `claude/auth-password-reset` is **not** a reason to unpark the
  password decision -- it contains no commits.
- [x] **`0257-backend-security-bugs` is fully superseded -- verified, safe to delete.** Its single
  commit (2026-08-22, "close the admin delete-person and rate-limit holes (cleanup tracker MR 5)")
  is a parallel implementation of MR 5, which shipped as
  [#165](https://github.com/themancalledzac/edens.zac.backend/pull/165). All three of its
  `deletePerson_*` tests already exist on `main` under identical names.

  **And it is where the S-4 gap came from.** Its `ProdSecretGuardTest` additions are the reflective
  `invokeVerify(guard)` tests that mutation later proved cannot fail, including the duplicated
  `enforceAuthzDisabledThrowsEvenWithAGoodSecret`. So this branch is not a fix for S-3/S-4 waiting to
  be salvaged -- it is their origin. **Do not "rescue" it into an MR**; that was the tempting wrong
  move and it would re-land the untestable tests.

  One nuance worth carrying into the S-3 fix: its `deletePerson_refusesAnAccountId` test stubs
  `deletePersonById` to return 0 and asserts a 404. That covers the service's zero-rows handling,
  which is real, and leaves the SQL predicate untested, which is S-3. Both statements are true at
  once, and that is exactly why the gap survived review.
- [ ] **Two July "wip" snapshots, 147-184 commits behind.** `0217-user-upgrade-be` (1 commit,
  2026-07-28, `AdminUserController` + `UserRequests` + a 136-line `UserUpgradeIntegrationTest`) and
  `chore/log-review-followups` (1 commit, 2026-07-28, `AdminController`/`TagRepository`/
  `CollectionService`/`ContentService`). Both predate Waves 1-5, which rewrote every file they touch.
  Decide per branch: salvage the test, or delete. `0217`'s integration test is the only part likely
  to still be worth anything.

# Appendix A — Cross-repo verification (highest value)

The BFF verification pass in the frontend repo determines how much the backend findings above
actually matter in prod. **All three answered 2026-08-24 by reading
`app/api/proxy/[...path]/route.ts` in the sibling repo. All three come back clean.**

- [x] **Does the BFF strip inbound client-supplied `X-Real-IP`? Yes.** `forwardHeaders()` builds a
  fresh `Headers` and copies through a denylist covering `x-real-ip`, `x-forwarded-for`,
  `cf-connecting-ip` and the `x-vercel-ip-*` set, then re-injects a value taken from
  `x-vercel-forwarded-for` or the last `x-forwarded-for` hop, regex-validated. Every backend limiter
  keying on `X-Real-IP` is safe through this path.
- [x] **Does it restrict which paths get the internal secret? Yes.** `isProxyableApiPath` normalizes
  through `new URL(...)` and requires `/api/`, rejecting with 404 *before* the secret is attached.
  Confirmed live: `/api/proxy/actuator/env` returns 404, and so does a dot-segment walk.
- [x] **Does anything cap request body size? Yes, hand-rolled.** 16KB for JSON, 25MB for multipart,
  checked against declared `Content-Length` and then re-checked authoritatively after buffering.
  **One caveat worth carrying**: the authoritative check runs after `await req.arrayBuffer()`, so a
  body with a spoofed `Content-Length: 0` is fully buffered before rejection.

This also resolved the backend's own chunked-body question -- see **S-5**, now downgraded.

# Appendix B — Prior-review scorecard

Against `ai_docs/reviews/2026-07-25-open-pr-review.md`, backend items: 25 landed, 10 moot (superseded by the typeless phase-2 merge), **1 partial, 2 still open** (recounted 2026-08-24 against the itemization below, which never supported "2 partial, 3 open").

- Still open: C7 partial indexes on `is_blog`/`is_client` (explicitly optional). **C8 is now closed** -- the policy is recorded under "Decisions needed". **`CollectionControllerDevTest` naming drift is closed too**: MR 4 shipped as [#164](https://github.com/themancalledzac/edens.zac.backend/pull/164) and no `*DevTest*` file exists anywhere in the tree.
- Partial: `CollectionList.fromSibling` exists, but two positional construction sites remain (`CollectionRepository`, `CollectionService` -- verified still true, line refs dropped per working rule 5). Related, found 2026-08-24: `fromSibling` passes `null` for `collectionDate` by construction, which is the one place that field is still null on the wire.
- Everything else verified landed (all-blogs on `is_blog`, no-flags INFO log, `@Valid` on `TagAdminController`, typeFilter deleted, column-list dedup, flag-triplication gone, the 1.6 test adds, the 1.7 comment fixes) or mooted by V51/V52.

# Appendix C — Unverified leads

Worth a targeted check; not asserted as findings.

- [x] Possibly-dead endpoints -- **all three confirmed ALIVE 2026-08-24 by grepping the frontend.
  Do not delete any of them.** `GET /api/admin/collections/metadata` has 5 call sites
  (`app/lib/api/collections.ts` into the explore page, collections panel, collection-edit hook and
  two admin pages). Both role-membership pairs are live, driving two different screens (see MR 17
  #8). Ids-only `GET /api/read/user/saves` is called from `app/lib/api/personal.ts` into
  `CollectionPageWrapper`, and the frontend types it as a bare `number[]`.
- [x] `role.kind` -- **premise disproved 2026-08-24**, V45 also writes `'PERSONAL'`. Full correction under "Decisions needed"; it may be carrying provenance.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6.** It had zero callers in main and test, so the only-accounts-get-grants rule was documented and unenforced. The method was deleted and the rule enforced at `RoleRepository.addMember` instead. Low severity: admin-only endpoints, and a PERSON row cannot log in; the risk was a dormant grant surviving an upgrade to an account.
- [x] `collection.rows_wide` -- **premise FALSE, confirmed 2026-08-24. The frontend DOES read it**: `CollectionPageWrapper` uses `collection.rowsWide ?? LAYOUT.defaultChunkSize` as the row-packer chunk size, so dropping the column changes public rendering. Struck as a lead.
- [ ] `deleteImages`/`deleteGif` delete from S3 before the DB write inside the transaction (`ContentService:380, 543`). A DB failure orphans the row's URLs; consider afterCommit S3 deletes.
- [ ] **The image-upload job-status endpoint may be entirely dead.** *(New lead 2026-08-24, found
  while answering the `JobStatus` question.)* `POST /content/images/{id}/from-disk` returns 202 with
  a `jobId` "for polling", and `GET /api/admin/content/images/jobs/{jobId}` serves the status -- but
  the frontend never calls either. Zero hits for `jobId`, `jobs/` or `from-disk` across its `app/`
  tree. If the disk-import flow is admin-CLI-only, the whole `JobTrackingService` surface plus its
  ~45 test references may be dead weight. Confirm how disk import is actually triggered before
  acting; this is the kind of "nobody calls it" claim that is wrong when a human uses curl.
- [ ] `updateImages` builds `imageMap` with `Collectors.toMap`, which throws on a duplicate key, so two updates for the same image id in one request fail before any work happens. **Note this is a verified finding sitting in an "unverified leads" appendix** -- it was traced when proving the MR 1b guards unreachable. It belongs with bug #17 (same method) whenever that MR happens; left here with a pointer rather than moved, so the trail survives. Correction to the original wording: `GlobalExceptionHandler` maps `IllegalStateException` to **400**, not 500 (working rule 3).
- [ ] `updateImages` reports per-item errors inside one transaction. Confirm a mid-item `DataAccessException` cannot leave an item half-applied (needs a test).
- [ ] `contentDisposition` (`DownloadUrlService:126-127`) does not escape quotes in filenames. Depends on what `sanitizeFilename` strips.
- [ ] `TagService.convertTagToCollection` briefly persists under a temp slug, visible to a concurrent reader, and may burn a `-1` suffix.
- [ ] `WebAuthnController.registerStart`/`loginStart` declare `throws Exception`; serialization failures become generic 500s.
- [ ] ID-list DAO fetches have no ORDER BY. Spot-checked callers re-order, but not all 7+ call sites were traced.
- [ ] `CollectionServiceTest` was profiled in parts, not read line-by-line. **The original line ranges (937-1385, 1555-2017) are dead** -- pure positions in a file that has grown to 2,644 lines, with no symbol to recover them by. Re-derive from the current tree or drop the lead (working rule 5).

# Appendix D — Not yet started

- [ ] `ml_image_tagging` (design doc, 0% implemented) is still the largest unstarted feature. No stubs in `src/` to maintain, so it costs nothing until you start.
