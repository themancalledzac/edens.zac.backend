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
| 5 — Consolidations | MR 15-19 | MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). #6 closed the `PersonRepository` carry and taught working rule 14; its own guard was later found to have a bypass (security finding S-2, closed [#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)). The last MR 15 follow-up closed 2026-08-24 ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210)) -- **MR 15 is fully done**; the `getContext().getAuthentication()` grep returning four sites is its completion condition and is satisfied. MR 19 #16 shipped 2026-08-25 ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) -- 201 queries to 1, and the board's suggested WHERE clause turned out to drop the parent scope. MR 19 #14 shipped 2026-08-25 ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) -- two queries to one, and **the first item in seven to need no adjustment at implementation time**, which is what broke the streak the full-board review's case rested on. **next: MR 16 #4/#5 (zero test coupling)** -- still outranked by the security board, though both HIGH findings closed 2026-08-25 (#221, #222) and what remains there is MEDIUM. |
| 6 — Conventions | MR 20-22 | not started |
| 7 — Structure | MR 23-24 | not started |
| 8 — Tests | MR 25-26 | not started |

Four sections below are not waves and had no row here until 2026-08-24, which made them invisible
to anyone navigating by this table. **"Decisions needed from the user" was the fourth and was still
missing its row until 2026-08-24's close-out** -- eight open items, invisible to this table, which
is the same failure the paragraph above was written to fix:

| Section | Status |
|---|---|
| [Open security findings](#open-security-findings) | **8 open, 0 HIGH, 2 blocked on the user** (reopened 2026-08-25 with 11; S-19 settled the same day, S-10 and S-11 shipped). The split full-board review attacked the closed set as a group and found what nine single-item reviews could not -- see S-10 through S-21 below. **Both HIGH findings are closed**: S-10 ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221)) and S-11 ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222)), both 2026-08-25, both mutation-verified. S-21 was filed while costing S-10's guardrail. Six are COLD; S-14 and S-16 are blocked on product calls named in the classification table. **next: S-15, then S-12.** S-15 is next rather than the higher-severity S-12 because it lives in the method #221 just edited and its one unknown was discharged in that pass (working rule 27) -- its stated fix names a method that does not exist. The nine originally-closed items are still closed and are listed after the new ones: S-1 ([#192](https://github.com/themancalledzac/edens.zac.backend/pull/192)), S-2 ([#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)), S-3 ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)), S-4 ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)), S-7 ([#199](https://github.com/themancalledzac/edens.zac.backend/pull/199)), S-9 ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)), S-8 ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)), S-5 ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)) and S-6 ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)). |
| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend) | **0 open. This board is closed.** All four done 2026-08-24: `collectionDate` ([#157](https://github.com/themancalledzac/edens.zac.backend/pull/157)), `isPasswordProtected` ([#209](https://github.com/themancalledzac/edens.zac.backend/pull/209)), `share/email` ([#213](https://github.com/themancalledzac/edens.zac.backend/pull/213)) and actuator hardening ([#214](https://github.com/themancalledzac/edens.zac.backend/pull/214)). **Nothing is owed to another team.** `share/email` closed the last live 404 in shipped frontend UI and taught working rule 24. **next: nothing here** -- the next item comes from the security board (S-15), not from this one. *(Corrected 2026-08-25: this row pointed at "MR 19 #16 or MR 16 #4/#5" and MR 19 #16 shipped as [#216](https://github.com/themancalledzac/edens.zac.backend/pull/216) the day before. A next-pointer inside a closed section is exactly the kind that rots unwatched, because nobody re-reads a board row marked done.)* |
| [Decisions needed from the user](#decisions-needed-from-the-user) | **7 open**, and only 3 are live questions -- `enforce-authz`, `parseImageDate`, and bare-array responses. The rest are parked, premise-corrected or research-complete-pending-disposition. Read each before treating it as a blocker. |
| [Stale side branches](#stale-side-branches) | **New 2026-08-24.** 6 worktrees, 0 open PRs, all superseded. |

Original estimate: roughly 4,500-5,000 lines removed against a few hundred added. The test tree (32.6k lines) is larger than main (27.2k); about 8% of it tests the Java compiler and Lombok.

| Category | Count | Deletable lines (est.) |
|---|---|---|
| Bugs (fix, not delete) | **17** (5 high) | — |
| Security findings | **8 open, 0 HIGH** — see below. The board reopened 2026-08-25 with 11 new findings; S-19 settled, S-10 and S-11 shipped, S-21 filed | — |
| Dead code (main) | ~60 methods/fields/files | ~1,000 |
| Inline comments (main, rule violations) | ~~370~~ **567 measured** | ~300 net (also low) |
| Duplication consolidations (main) | 20 findings | ~500 |
| Dead/boilerplate tests | **10 findings** | ~2,700 (+700 optional) |
| Build/config rot | **9 findings** | ~150 |

## Carried forward out of closed waves

Reconciled 2026-08-23 during the history split, re-reviewed 2026-08-24. Waves 1-3 read "complete"
but held **eight live items**, collapsed into five entries. Since then: the `PersonRepository` entry
was closed by MR 15 #6 (decided, not deferred), and the chunked-body residual moved to **S-5** under
"Open security findings". What is left is below, plus one bug that never had a row at all and one
found while costing #209's guardrail.

- [ ] **The `coverImage` stripping that does not exist, and the test that cannot fail.** *(New row
  2026-08-24, found while writing #209's cost report; taught **working rule 22**.)*
  `CollectionControllerProdTest` has a section headed **"Fix 1: coverImage stripped for protected
  CLIENT_GALLERY on list endpoints"**, and its test
  `getAllCollections_protectedClientGallery_returnNullCoverImage` carries the comment "stripped by
  `CollectionProcessingUtil.buildBasicModel`". **No such stripping exists** --
  `buildBasicModel` sets `coverImage` unconditionally from `coverImagesById`, verified by read.
  The test hand-builds a `CollectionModel` with `coverImage(null)` and mocks `CollectionService`, so
  it asserts only that the controller passes through what the service handed it. Strip nothing,
  change nothing, and it stays green: working rule 15's shape exactly, reporting coverage for a
  security behavior that was never written.

  **This is the second of the two comments in that file** that claim stripping. The first -- the
  BE-H5 banner -- was corrected in #209, because it is the one that crossed the repo boundary and
  produced the frontend's false premise. This one was left alone deliberately: it sits inside the
  area #209's guardrail said not to touch, and changing it is a judgement about what the tests
  should assert, not a comment fix.

  **What to decide, and it is not obvious.** Either the section is a stale record of a fix that was
  reverted or never landed, in which case delete the banner and rename the test to what it actually
  asserts (controller pass-through); or list-endpoint stripping is genuinely wanted, in which case
  the test is a specification with no implementation and the work is real. Do not resolve this by
  reading the comment. #209's cost report has what implementing it would break.

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

### Reopened 2026-08-25 by the split full-board review

The nine findings below the divider were each reviewed alone and closed. The 2026-08-25 review
attacked them **as a set**, which is the only way three of these were ever going to surface: S-10 and
S-12 are cases where one fix invalidated a premise another fix depends on, in a different file.

Verification status is stated per item and is not uniform. **S-10 and S-11 were independently
re-verified line by line before being written here**; the rest carry the reviewing agent's trace and
should be re-confirmed at implementation time, per working rule 21.

- [x] **S-10 (HIGH, verified). An admin-issued reset invite survives an email change, and redeeming
  it is account takeover.** **DONE** ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221), 2026-08-25.) `AdminUserController.updateUser` sweeps invites only when
  `existing.getStatus() == UserStatus.INVITED`, and its comment says "ACTIVE users have no pending
  onboarding invite to hijack." **S-7 made that false.** S-7 widened `mayAcceptInvite` to
  `{INVITED, ACTIVE}` precisely so `regenerateInvite` -- which has no status restriction -- can mint
  a password-reset link for an ACTIVE account.

  The sequence: admin mints reset token T for `old@example.com`; admin then corrects the account's
  email to `new@example.com`. The targeted sweep skips, because the account is ACTIVE, not INVITED.
  The general sweep on the next line, `invalidateInvitesForStatus(id, ACTIVE)`, returns 0 by
  construction, because `mayAcceptInvite(ACTIVE)` is true. Whoever still controls `old@example.com`
  POSTs T to the accept endpoint. `UserInviteService.accept` resolves the invite by `userId` alone
  and **never compares the invite's email to the account's current email**, so it sets an
  attacker-chosen password, flips the account ACTIVE and mints a session -- with ROLE_ADMIN if the
  account carries it.

  Each of the four links was read directly: `mayAcceptInvite` returns true for ACTIVE;
  `regenerateInvite` has no status gate; the sweep is gated on INVITED; `accept` never reads
  `invite.getEmail()`. **The fix is probably the email comparison in `accept`**, not a wider sweep --
  the invite records the address it was issued to, and redemption should require it still be the
  account's address. Check that against the reset flow before assuming it.

  **Guardrail: leave `mayAcceptInvite` alone and report what narrowing it would cost.** The tempting
  adjacent change is to revert S-7 and drop ACTIVE back out of the predicate, which closes this by
  removing the feature S-7 shipped -- admin-issued password resets stop working. The seam this fix
  belongs on is redemption-time identity, not the eligibility predicate. If narrowing turns out to
  be right anyway, that is a decision for the user, so report the cost rather than making it.

  **Also leave the invite sweep in `AdminUserController` alone.** Widening it to fire on every email
  change looks like the same fix and is not: it papers over the missing identity check while leaving
  `accept` willing to redeem an invite against an address it was never issued to.

  **Shipped as specified, which is the second item in a row to need no adjustment.** Every premise
  re-read on `main` before a line was written and all four held. +111/-11 across four files; suite
  1,377 -> 1,381.

  The fix is a named predicate beside `mayAcceptInvite`, per working rule 19:
  `inviteAddressMatchesAccount(invite, user)`, tested in `accept` immediately after the status test.
  Three choices worth carrying forward: it sits **after** `redeem()`, so a hijacked token is spent
  rather than left live for a second attempt (matching the status rejection); it compares
  **case-insensitively**, because every write path lowercases and a case difference is not an
  identity change; and it **null-guards the account side**, because `users.email` is nullable for
  PERSON rows since V35 and a security check must not throw.

  **Working rule 24 applied at the input end and paid.** Before believing the scope: `user_invite.email`
  is `NOT NULL` in V32 and `UserInviteRepository`'s row mapper does select it, so the value the fix
  consumes always exists and there are no legacy NULL rows to fail closed on. Then the issue-time
  check: all three minting paths write the invite email equal to the account email (`createUser`
  inserts then mints with the same value; `regenerateInvite` passes `user.getEmail()`; `upgradeUser`
  calls `updateEmail` *before* minting). Nothing legitimate diverges, so the check refuses only a
  subsequent email change -- which is the attack.

  **The guardrail's cost report, and the part the item did not name.** Full version in the
  [history file](2026-08-22-backend-cleanup-history.md#s-10-outcome-2026-08-25----redemption-time-identity-and-the-narrowing-cost-report).
  The headline: narrowing `mayAcceptInvite` removes the only password reset in the repo (grep for
  `password-reset`/`forgot` across `src/main` returns two javadoc mentions and no route) and it
  breaks *silently*, because `regenerateInvite` has no status gate and would keep returning 200 with
  a link that 410s days later at the invitee. **The cost the item missed is a second caller**:
  `invalidateInvitesForStatus` sweeps when `!mayAcceptInvite(newStatus)`, so narrowing the predicate
  would make every admin PATCH that sets status ACTIVE kill that account's outstanding invites,
  including a reset link issued moments earlier. That is a behavior change in a different method,
  invisible from S-10's own text, and it is why **working rule 29** exists.

  **Two comments corrected, because S-7 falsified them and one is this item's own premise.**
  `AdminUserController.updateUser` and `AdminUserControllerTest.changingActiveUserEmailDoesNotTouchInvites`
  both said an ACTIVE user has no pending onboarding invite to hijack. Neither behavior changed --
  the sweep is still INVITED-only and the test's assertion is unchanged -- but both now say *why*
  the sweep is scoped that way and where the ACTIVE case is handled instead. Working rule 22's
  asymmetry is the reason this was not left for later: a stale comment about a **protection** is not
  corrected by the next reader, because nobody re-derives a guarantee they have been told holds.

  **Mutation evidence.** TDD RED first: three of the four new tests fail on unfixed code. The fourth,
  `anInviteAddressDifferingOnlyInCaseStillAccepts`, passes before and after **by design** -- it
  guards the direction the fix must not break, per working rule 18's point that a wrong allowlist
  fails closed and surfaces late as "reset is broken". Stated here rather than implied, so nobody
  counts it as a regression detector it is not.

- [x] **S-11 (HIGH, verified). `ACCESS_TOKEN_SECRET` has a public default and no startup guard,
  which voids the at-rest property #213 claims.** **DONE** ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222), 2026-08-25.) `docker-compose.yml` supplies
  `${ACCESS_TOKEN_SECRET:-dev-access-token-secret}`, and `.env.example` never mentions the variable
  at all -- it lists `INTERNAL_API_SECRET` and `INTERNAL_API_SECRET_NEXT` only. `TokenCipher` derives
  its AES-256-GCM key from exactly this value (`app.access-token.secret`, sha256 of the string), and
  `ProdSecretGuard` checks `internal.api.secret` and `enforce-authz` and says nothing about it.

  So an operator who builds their `.env` from `.env.example` and deploys with compose runs prod with
  the encryption key for every `share_link.token_cipher` printed in a public repo. V58's comment says
  a dump alone yields nothing usable; in that deployment a dump plus the repo yields every live share
  link. Note the failure mode precisely: `application.properties` has **no** default for the
  placeholder, so a bare run with the variable unset refuses to start -- it is compose's default that
  turns "missing" into "publicly known", and compose is the deployment vehicle.

  **This is the cross-fix shape again.** S-4 made `ProdSecretGuard` impossible to unwire silently;
  #213 then made a second secret confidentiality-critical and never extended the guard to it.
  "No new env var" was scored as a simplification win in #213 and is also how this got past the only
  startup check the repo has. Fix is one clause in `ProdSecretGuard` plus a line in `.env.example`.

  Shipped as specified: one clause in `ProdSecretGuard` (null, blank, or the known dev default,
  mirroring the `internal.api.secret` clause) plus an `ACCESS_TOKEN_SECRET` block in `.env.example`
  naming what the value protects and what rotating it costs. +97/-11; suite 1,377 -> 1,381.

  **The compose default stays, and that was a decision, not an oversight.** Removing
  `:-dev-access-token-secret` leaves the variable set-but-empty inside the container rather than
  absent, so `TokenCipher` would derive its key from `sha256("")` -- a more predictable key, not a
  safer one. The startup guard is the right seam; compose is not.

  **The item named one consumer and the doc already knew there were three.** S-11's severity
  paragraph traced `TokenCipher` only. Grepping the property key finds `ClientGalleryAuthService`
  using the same value as the HMAC key for `generateAccessToken`, `generatePasswordAccessToken` and
  `passwordFingerprint`. That is not a new discovery -- **the "Unsettled" bullet on rotation, fifty
  lines below in this same document, already listed all three uses.** The fact was on the board and
  S-11 did not carry it, which is a section-integrity failure rather than a research gap, and the
  more useful lesson: a finding written in one section does not reach the item that needs it.

  What the second consumer adds to the severity, stated precisely so it is not overclaimed:
  `passwordFingerprint`'s own docblock says the fingerprint "is not derivable without the server
  secret", and the `gallery_access_pw_<fingerprint>` cookie **name** carries that fingerprint -- so a
  known key turns an observed cookie name into an offline dictionary attack on the gallery password.
  It is **not** a forgery bypass: both `validateAccessToken` and `validatePasswordAccessToken`
  recompute the expected HMAC from the gallery's *stored* password, so minting a valid token still
  requires knowing that password. Recorded in both directions so nobody re-derives either half.

  **Mutation evidence, which is the whole point on this file.** S-4 exists because
  `ProdSecretGuardTest`'s unit cases call `verify()` reflectively on a hand-built object and cannot
  see `@PostConstruct`. Deleting the new clause reddens **all four** new tests including the
  `ApplicationContextRunner` case, which boots a real prod context so the container is what runs the
  check. The two pre-existing wiring tests now supply a real access-token secret, so each still fails
  only for the reason it names. Source restored with `touch` afterwards per working rule 15's note on
  stale `.class` files.

- [ ] **S-12 (MEDIUM-HIGH, agent trace). Dormant `role_member` rows on a PERSON become live grants on
  upgrade.** S-2 closed `addMember` and `repointMemberships`. `AdminUserController.upgradeUser` is
  the third path and the dangerous direction: it verifies the row is PERSON, sets an email, flips it
  to INVITED and mints an invite, with no `role_member` purge. The row id never changes, so any grant
  already pointing at that PERSON survives onto the live account. `RoleRepository` names this exact
  risk in `addMember`'s own docblock. **No migration ever purged pre-guard rows**, so the
  precondition is existing prod data rather than something an attacker has to arrange.

- [ ] **S-13 (MEDIUM, agent trace). The admin update endpoint accepts `status: PERSON`.**
  `UserRequests` types the field as the bare `UserStatus` enum; the javadoc one line above says
  "INVITED / ACTIVE / DISABLED" and nothing enforces it. Two requests then make
  `PersonRepository.deletePersonById`'s `AND status = 'PERSON'` match a real account, which the
  people-delete endpoint hard-deletes with memberships cascading. It also manufactures exactly the
  `role_member`-on-a-PERSON state S-2 exists to prevent, on a path neither S-2 guard covers.

- [ ] **S-14 (MEDIUM, agent trace). S-6 turned `addCollection` from a read decision into a durable
  third-party grant.** `UserShareControllerProd.addCollection` gates on `canView`, then writes a
  `share_link_collection` row -- which is the authorization set for an unauthenticated bearer-token
  holder. Before S-6, an admin holding no role grant got 403 there. The ADMIN sentinel now makes the
  gate always say yes, so one PUT can put any collection on the site, including another client's
  password-protected gallery, behind a URL that can be forwarded to anyone. **#207's reasoning ("an
  admin can already view everything") is correct for the read gates and does not transfer to a gate
  that grants access to someone else.** This is the first item to argue a previous fix was too
  broad rather than too narrow.

- [ ] **S-15 (MEDIUM, agent trace). Completing a password reset does not revoke the account's other
  sessions.** `UserInviteService.accept` writes the new password hash and mints a new session without
  calling `revokeAllForUser`. A stolen session cookie survives the reset and keeps sliding its 60-day
  window. S-8 built the primitive and wired it only into the admin status-change path, so today the
  only way to evict a stolen session is an admin round-trip to DISABLED and back. **Resetting your
  password is the thing users do when they think they are compromised**, which is what makes this
  worth more than its severity suggests.

  **Premise re-verified 2026-08-25 while shipping S-10, which edits the same method. It holds, and
  the item's prescribed fix does not.** `accept` still writes the hash and calls
  `sessionService.create` with nothing in between. But `SessionService` has **no**
  `revokeAllForUser` -- its public surface is `create`, `resolve`, `revoke(rawToken, response)` and
  `revokeAllForStatus(userId, newStatus)`. The classification table's "S-8 already built it" is
  **wrong**: S-8 built `revokeAllForStatus`, which delegates to the repository's `revokeAllForUser`
  only when `!mayHoldSession(newStatus)`. Calling it with ACTIVE is a **no-op by construction**,
  because `mayHoldSession(ACTIVE)` is true. The repository method exists; the service does not expose
  it.

  So the work is a new public `revokeAllForUser(Long)` on `SessionService` plus one call in `accept`
  -- still small, still COLD, but not the one-liner the item implied.

  **The ordering trap the item does not name:** `accept` mints a fresh session on the same request.
  Revoke after `create` and the user is logged out by their own password reset. The revoke has to run
  before the mint, and the test has to assert that ordering rather than just that both were called.

- [ ] **S-16 (MEDIUM, agent trace). The revoke-on-status sweep covers sessions and invites and misses
  share links.** `ShareLinkService.resolveByRawToken` reads no owner status, and the scope query
  joins `share_link` to `collection_people` with no `users.status` predicate. Disable a user for
  cause: S-1 refuses their login, S-8 kills their sessions, S-9 kills their invites, and their share
  link keeps serving every collection they are tagged in to anyone holding the URL. #213 sharpens
  this by making that link durable and re-readable rather than a one-shot value.

- [ ] **S-17 (MEDIUM, agent trace). `share/email` with no rate limit is an authenticated open mail
  relay.** The board already recorded "no rate limit" as a known gap and framed it as a
  token-guessing risk. It is not: `RateLimitFilter` covers `/api/public/` only, so any signed-in user
  can POST unbounded to the endpoint, each call an SES send to an arbitrary address from
  `no-reply@zacedens.com`, DKIM-signed by the real domain, carrying a genuine clean-reputation link.
  Part of the subject line comes from the sender's own display name, which they set at invite
  acceptance. **The damage is SES reputation, and it is shared** -- a suspension takes the invite
  email and the gallery-password email down with it.

- [ ] **S-18 (MEDIUM, agent trace). #214's exclude list misses four endpoints that meet its own
  stated criterion.** The criterion is "dumps configuration, dumps process state, or mutates the
  running app". Available under `include=*` and not excluded: `caches` (has delete operations, so it
  mutates), `conditions` (full auto-config report), `flyway` (migration history) and `scheduledtasks`
  (`@EnableScheduling` is on). `InternalSecretFilter` still covers them in prod -- but #214 exists
  precisely as the layer for when it does not.

- [x] **S-19 (settled 2026-08-25, not live). The bug #3 fix swapped one spoofable header for
  another.** `ClientIp` trusts `X-Real-IP` unconditionally and its javadoc calls the header's
  presence "the trust signal" -- the same reasoning used to reject `X-Forwarded-For`. The question
  was whether the Next.js BFF forwards a client-supplied value. **Read the live frontend and it does
  not.** `forwardHeaders` in `app/api/proxy/[...path]/route.ts` now lists `x-real-ip` in its strip
  set, so a client's own header never survives the hop, and it re-injects `X-Real-IP` from
  `x-vercel-forwarded-for`'s first hop, falling back to the **last** hop of `x-forwarded-for`
  (appended by the trusted edge on CloudFront/Amplify). Its comment says outright that `x-real-ip`
  is not trusted because it is forgeable on Amplify. So `AuthLoginLimiter`'s `ip|email` key is not
  defeated by header rotation, and login brute-force limiting works.

  **The palace's copy of that file was two months stale and said the opposite** -- `x-real-ip` was
  not in the strip list in the indexed version, and the fallback chain was different. The finding
  only closed because the live file was read. Working rule 5's principle applies to indexed code as
  much as to line numbers.

  **What survives is a documentation bug, filed rather than fixed here**: the backend's `ClientIp`
  javadoc still calls the header's presence "the trust signal", which is now actively misleading --
  the frontend deliberately does not trust it and the backend's protection comes from the strip plus
  `InternalSecretFilter`, not from the header meaning anything. Correct that docblock when next in
  the file. **Cross-repo note: the frontend already solved this and the backend never heard.**

- [ ] **S-20 (MEDIUM, agent trace). "May hold a session" exists in three places and only one of them
  is `mayHoldSession`.** S-8's write-up claims the predicate serves both `resolve` and the sweep so
  it cannot drift. `AuthController` and `WebAuthnService` both inline `getStatus() != ACTIVE`. Adding
  a fifth `UserStatus` and updating `mayHoldSession` leaves both admitting it -- the exact drift
  S-9's refactor was done to prevent on the invite side.

- [ ] **S-21 (LOW, verified 2026-08-25). `regenerateInvite` mints a link for accounts that can never
  redeem it.** *(Filed while costing S-10's guardrail -- the endpoint had to be read to establish
  what narrowing `mayAcceptInvite` would break, and this fell out of the same read.)*
  `AdminUserController.regenerateInvite` looks the user up by id and mints an invite with **no status
  check at all**. `accept` refuses anything outside `{INVITED, ACTIVE}`, so for a DISABLED account the
  admin gets `200` and a URL, the invitee gets the email, clicks it, and receives `410 Gone`. Nothing
  anywhere says the account was ineligible.

  Traced, not assumed: for a PERSON row the failure is louder and differently wrong -- `users.email`
  is NULL for PERSON, `user_invite.email` is `NOT NULL` in V32, so the insert raises
  `DataIntegrityViolationException` and `GlobalExceptionHandler` turns it into a `409` reading "Data
  integrity violation: duplicate or invalid data". A schema constraint is doing the job a status
  check should do, and it reports the wrong reason.

  **No test covers this.** `AdminUserControllerTest` has a happy path and a 404; every other
  `regenerateInvite` assertion is a `verify(never())` on a different endpoint's path. So the mutation
  a fix would need to catch has nothing guarding it today.

  Fix is a status gate on the endpoint returning `409`, keyed on `UserInviteService.mayAcceptInvite`
  so the eligibility rule keeps one definition (working rule 14). **Low severity because it grants
  nothing** -- redemption is already refused; the cost is an admin who believes they sent a working
  link. **COLD.**

#### Classification of the reopened items (2026-08-25)

Every item above is stamped, so none of them reads as available and then eats a session.

| Item | State | If BLOCKED, the question and who answers it |
|---|---|---|
| S-10 | **DONE** ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221)) | -- shipped as specified 2026-08-25 |
| S-11 | **DONE** ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222)) | -- shipped as specified 2026-08-25 |
| S-12 | **COLD** | -- purge on upgrade, same shape as S-2's two fixes |
| S-13 | **COLD** | -- constrain the request enum; no consumer sends `PERSON` |
| S-14 | **BLOCKED on the user.** Is an admin allowed to put an arbitrary collection into another user's share scope? The fix depends on the answer: if no, the gate needs an ownership test rather than `canView`; if yes, this is documentation, not a bug. |
| S-15 | **COLD** | -- **corrected 2026-08-25**: `SessionService` has no `revokeAllForUser`, and `revokeAllForStatus(id, ACTIVE)` is a no-op. Add the method, then call it *before* the mint. Still no open question |
| S-16 | **BLOCKED on the user.** Should disabling an account kill its share links, or only suspend them? Revoking is destructive and not reversible by re-enabling; suspending needs a status join on every resolve. |
| S-17 | **COLD** | -- extend the limiter past `/api/public/`; the four limiters already have disjoint key spaces |
| S-18 | **COLD** | -- four names onto the exclude list, plus a test that is not self-referential (see below) |
| S-20 | **COLD** | -- route the two inlined checks through `mayHoldSession` |
| S-21 | **COLD** | -- status gate on `regenerateInvite`, keyed on `mayAcceptInvite` |

S-19 closed as not-live, above. **Two of the original eleven are blocked, and both blockers are
product calls rather than research** -- neither can be settled by reading code, which is why they are
named here in the form the user can answer.

**Updated 2026-08-25 after the close-out:** S-10 and S-11 shipped, S-21 was filed and stamped COLD,
and S-15's row was corrected. That correction is the one worth noticing -- S-15 was stamped COLD on
the strength of "S-8 already built it", and the method it named does not exist. **A COLD stamp
asserts there is no unanswered question; it does not assert the prescribed fix compiles.** Working
rule 21 already says the fix is a hypothesis, and this table is where that distinction keeps getting
lost, because a single word in a status column reads as a warranty over the whole item.

#### Tests that cannot fail (2026-08-25)

Working rule 15 says a regression test that cannot fail is worse than none because it reports
coverage. The review checked the security tests against that standard and six fail it:

- [ ] `ActuatorExposureEndToEndTest` iterates the denylist itself, so an omission like `caches` is
  structurally invisible, and it supplies its own exclude via `@SpringBootTest(properties=...)` --
  **no change to `src/main` can redden it.** It proves Boot's ordering, which was #214's point, but
  it is not a regression detector for the shipped config. Only the literal-match test links it to
  production.
- [ ] `AdminUserControllerTest.demotingUserToInvitedRevokesSessionsButKeepsInvites` cannot catch what
  its comment claims: `SessionService` is a mock and `mayHoldSession` is static. The mutation is
  caught by `SessionServiceIntegrationTest` instead. **False attribution, not a missing test** --
  worth fixing the comment so the next reader does not trust the wrong test.
- [ ] `WebAuthnServiceTest` covers DISABLED only. Rewriting the guard as `== DISABLED` stays green
  while admitting INVITED and PERSON passkey logins, both reachable. `AuthControllerTest`
  parameterizes over both and does catch it; this one should too.
- [ ] `PersonRepositoryIntegrationTest` (S-3's whole deliverable) seeds both accounts ACTIVE, so
  mutating `status = 'PERSON'` to `status <> 'ACTIVE'` passes while making every INVITED and DISABLED
  account deletable through the people-delete endpoint. The mutation S-3 stated does redden it; this
  one does not.
- [ ] `ProdSecretGuardTest.Wiring` registers the guard class by hand, so moving it out of the
  component-scanned package keeps every case green while prod boots unguarded. The two mutations S-4
  stated do redden it. **Count corrected 2026-08-25: five wiring cases, not four** -- #222 added
  `prodRefusesToStartOnTheDefaultDevAccessTokenSecret`. The item is unchanged in substance and
  slightly worse in scale: the new clause is guarded by the same hand-registration, so
  `withUserConfiguration` still stands between this test and the thing it claims to prove.
- [ ] **Nothing pins that the share-link GET is `no-store`.** #213 put a bearer token in that
  response body; the cache-control default-deny list enumerates six sibling routes and not this one.
  Adding it to `PUBLIC_ROUTES` reddens nothing, and the read cache policy sets `s-maxage` for
  CloudFront -- so the failure mode is a shared cache serving one owner's share token to another
  visitor. Default-deny protects it today; nothing guards the edit.

#### Unsettled, and how to settle each (2026-08-25)

- [ ] **Whether prod actually runs the `prod` profile.** The deployment docs contradict each other:
  `ai_deployment_strategy.md` says `SPRING_PROFILES_ACTIVE=prod`, `ai_ec2.md` says `default` in two
  places. Under `dev`, `enforce-authz=false` and `SecurityConfig` falls through to `permitAll` on
  `/api/admin/**` and `/api/edit/**`, and neither `ProdSecretGuard` nor `InternalSecretFilter`
  exists to object. S-4 proved the `@Profile("prod")` wiring works; **nothing proves prod is named
  prod.** Settle by reading `SPRING_PROFILES_ACTIVE` on the live host, or by hitting the origin
  without `X-Internal-Secret` and confirming a 403. **Re-scoped 2026-08-25:** S-19 settled and S-11's
  fix shipped, so this no longer gates either fix. What it now gates is whether S-11's guard *runs* --
  `ProdSecretGuard` is `@Profile("prod")`, so on a host named `default` the new
  `app.access-token.secret` clause is as absent as the rest of it. It is a live question about a
  live host and the only one on this board that can be answered with a single request; it survives
  only because it needs credentials or network access this session did not have.
- [ ] **Whether Tomcat surfaces `Transfer-Encoding` to `getHeader()`.** S-5's entire fix depends on
  it, and its only test uses `MockHttpServletRequest`, which returns whatever the test put in. If
  Tomcat consumes the header while installing the chunked input filter, the branch never fires and
  the bypass is still open. Settle with an integration test that POSTs a real chunked body to a
  booted server and asserts 411 -- `ActuatorExposureEndToEndTest` already has the shape.
- [ ] **`ACCESS_TOKEN_SECRET` has no rotation story.** It keys gallery HMAC tokens, gallery password
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

  **This bullet is also where S-11's missing fact was sitting.** It listed all three uses of the
  secret while S-11's own severity paragraph named only `TokenCipher`. See S-11's outcome above.
- [ ] **`SessionService.resolve` slides the session window before reading status**, so a non-ACTIVE
  account's session gets its expiry pushed forward before rejection. Latent only because S-8 revokes
  on every path that reaches it. Reordering the two blocks costs nothing.
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed 2026-08-25**, after S-6 routed
  everything through `effectiveLevel`. They are the bug S-6 fixed, still sitting in the DAO under the
  right names and still green in tests. Wave 1 deletion candidates, and the names are the hazard.

### Closed 2026-08-24

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
  and has **exactly one caller**: the targeted sweep inside `AdminUserController.updateUser`, guarded
  by `existing.getStatus() == UserStatus.INVITED` (`:304` as of #221; the doc said `:292`, drifted by
  twelve and corrected 2026-08-25 -- find it by the guard, not the number), on the email-change path only
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

21. **An item's premise is evidence. Its prescribed fix is a hypothesis. Four in a row now.**
    Added 2026-08-24 after S-6. On S-7, S-8, S-5 and S-6 -- four consecutive items -- the item
    correctly identified the problem and then specified a fix that would have shipped a bug if
    typed in verbatim:

    - **S-7** said "require `INVITED`". That kills admin-issued password reset, which redeems
      through the same endpoint as an ACTIVE user.
    - **S-8** named mirroring S-9's `{INVITED, ACTIVE}` as the default. That leaves demoted
      accounts holding `user_session` rows that can never resolve -- the exact rows the item
      asked to clear.
    - **S-5** implied "the cap never fires, so reject when the length is -1". A request with no
      body reports -1 too, so that 411s every bodiless request on a public path.
    - **S-6** said "route `canView` through `effectiveLevel`". `effectiveLevel` adds two branches,
      not one, so that also hands a share-link holder GENERAL and turns a share link into a second
      way past the gallery password prompt.

    The premise held all four times. The prescription failed all four times, and in three of them
    the failure was **a case the item's author had not enumerated** rather than a mistake in
    reasoning. So: treat the "Fix:" sentence in any remaining item as a starting hypothesis to test
    against the code, and before implementing it, ask what inputs or principals the item did not
    name. Working rule 16 is the specific instrument for this; rule 21 is why to keep reaching for
    it even when the item looks fully specified.

22. **A comment claiming a protection exists is a claim to check, not documentation to trust.**
    Rule 7 warns against manufacturing a bug report out of a stale comment. This is the inverse, and
    it costs more. `CollectionControllerProdTest` carried **two independent comments** asserting that
    `coverImage` is stripped for password-protected collections. Neither is true:
    `CollectionProcessingUtil.buildBasicModel` sets the cover unconditionally, and the three tests
    under the first banner are named `...retainsCoverImage` and assert the opposite. One of the two
    crossed a repo boundary and produced the frontend's false Option B premise -- which is what made
    `isPasswordProtected` look like a decision to be made rather than an implementation to do.

    The asymmetry is the point. A stale comment about **behavior** gets corrected the next time
    somebody reads the code, because the code contradicts it in front of them. A stale comment about
    a **protection** does not, because nobody re-derives a guarantee they have been told already
    holds. It survives every reading until someone needs it to be true.

    And the test named for the protection may be exactly the one that cannot fail.
    `getAllCollections_protectedClientGallery_returnNullCoverImage` hand-builds a model with
    `coverImage(null)` and mocks `CollectionService`, so it asserts controller pass-through and never
    the stripping it is named for. Rule 15 called that shape worse than no test; a security claim in
    the banner above it is how the shape survives review.

    So before relying on any comment saying something is filtered, stripped, gated or scoped: find
    the line that does it. If the only evidence is the comment plus a test whose own fixture supplies
    the result, the protection does not exist.

23. **"My merge did not move this ref" is not "this ref is correct."** Recorded against my own
    claim. #211's close-out ran the scoped drift sweep, found that #209's two edits sat below every
    line the board cites into those files, and reported "no ref shifted -- verified rather than
    assumed." That was true and nearly worthless. The very next sweep re-derived the same refs
    against source instead of against the diff and found **five of six already wrong**:
    `CollectionService.java` `460-490` -> `466-496` and `822-848` -> `846-915`,
    `SyntheticCollectionResolver` `153` -> `150` and `86-92` -> `97`, and `CollectionService`
    `912-914` -> `931-933`. None of that drift came from #209. All of it predated the session that
    declared the neighborhood clean.

    The third principle says the board decays fastest where work has landed, and that is still
    right -- it is why the sweep is scoped. But it licenses the wrong check if you read it as "find
    what my diff moved". **The scoped sweep selects which refs to verify; it does not tell you they
    are fine because you did not touch them.** Open the file and match the anchor text every time.

    The tell that this went wrong is a sweep that reports zero corrections. Every sweep on this
    board that actually re-derived found something: five refs, then three, then four. A clean
    result is likelier to mean the wrong question was asked than that the board is finally accurate.

24. **A specified fix can be impossible, not merely imprecise. Check that the inputs it assumes
    exist before scheduling it as next.** The `share/email` item was specified down to the file
    list -- "one `@PostMapping` on `UserShareControllerProd`, one `sendShareLinkEmail` method on
    `EmailService` alongside the two that exist, one request record. No new response type" -- and
    chosen as next precisely because that scope looked small. None of it was wrong. All of it was
    unbuildable: the endpoint would have had **nothing to put in the email**, because the frontend
    sends `{ toEmail }` alone and V56 stored only the token's hash. The real change needed a
    migration, a new crypto class, and a decision about the at-rest security property of
    `share_link` -- none of which appear anywhere in the item.

    The three facts that promoted this item from a decision to a build were all about the
    **output** end: the response record exists, the reason codes exist, both sides handle
    `email.enabled=false`. Nobody asked the corresponding question at the **input** end -- where
    does the link itself come from. An item can be verified from both sides of a repo boundary,
    as this one was, and still never have its own inputs checked.

    This makes **five consecutive items whose specified fix needed adjusting at implementation
    time** (S-7, S-8, S-5, S-6, `share/email`). The first four were imprecise; the fifth was
    impossible, which is a different failure and a worse one, because imprecision surfaces while
    you type and impossibility does not surface until you look for a value that was never there.

    So for any item about to be picked up: name the inputs its fix consumes, and confirm each one
    is reachable from where the fix will run. For a fix that sends, displays, or forwards a value,
    that means asking where the value is read from before believing the scope.

25. **When a hardening rests on a framework ordering guarantee, test the guarantee, not the
    config string.** The actuator item specified an exclude list plus "a test that reads
    `src/main/resources` directly per working rule 2". That test is necessary and it is not
    sufficient: it proves the property is present, which is not the claim the hardening makes. The
    claim is that Boot applies exclude **after** include, so the shipped list survives a stray
    `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*`. That ordering was quoted in this board and had
    never been executed here.

    Booting the app with `include=*` on top of the shipped exclude cost one test class and settled
    it: the eight endpoints 404, health still 200s. The mutation is what makes it worth keeping --
    empty the exclude and `/actuator/env` answers 200 on the app port, so the assertion
    distinguishes the two worlds instead of passing in both. Rule 15's complaint about tests whose
    own fixture supplies the result applies to config tests too, and a string-equality test on a
    property file is the easiest place to land one.

26. **Replacing a drifted line range with a fresher line range is not de-positionalizing it.** MR
    24's `UserShareControllerProd` bullet was rewritten on 2026-08-24 precisely because its range
    had drifted -- the old `124-152` overran the end of a 145-line file. The rewrite announced
    itself as "re-derived and de-positionalized" and then wrote three new numbers: `:116-128`,
    `:135-144`, `:137`. All three were dead within hours, because #213 added an endpoint to that
    file and took it from 145 to 214 lines. The rewrite bought nothing except a more confident
    tone.

    Rule 5 says find symbols by name. This is the failure mode that survives rule 5 being *known*:
    the session re-derives correctly, then writes the answer down in the form that rots. **The
    output of re-deriving a ref is a name, not a number.** Where a number genuinely helps a reader
    navigate, stamp it -- "`465-495` as of #216" -- so the next session can see at a glance whether
    it is reading a fact or an artifact.

    The tell is a bullet that says "de-positionalized" and still contains a colon followed by
    digits.

27. **An item specified while its own open question is still open is the one that needs
    adjusting.** Six items in a row needed adjustment at implementation time and MR 19 #14 did not,
    which looked like luck until the difference showed up. #14 carried an open question -- "verify
    COLLECTION hydration first" -- and the session before it **discharged the question first, then
    specified the fix**, and discharged it for all four content types rather than only the one the
    question named. The item that reached implementation had no unknowns left in it.

    The six that needed adjusting were all specified with their question still open, so the fix was
    written against an assumed answer. Working rule 21 says the premise is evidence and the fix is a
    hypothesis; this is the sharper version. **A fix specified over an open question is a hypothesis
    about the question, not about the fix.**

    Practical form: when an item says "verify X first", that sentence is the whole item until it is
    done. Do not write the fix in the same pass that raises the doubt. And when discharging it, check
    every branch the code has, not the one the question asked about -- #14's question named
    COLLECTION and the answer that mattered covered TEXT and GIF too.

28. **A stacked PR whose base merges first strands the work on a dead branch.** #219 was opened
    against `docs/close-out-216` because #217 was still open. #217 merged to `main` first, and #219
    then merged into a branch that `main` had already absorbed and moved past. Both PRs read MERGED.
    Neither `gh pr list` nor the PR state said anything was wrong. **The doc pass -- a reopened
    security board with two HIGH findings -- was not on `main` and nothing surfaced that.**

    Worse, the stranded branch could not simply be merged forward: it predated #218, so merging it
    would have reverted `ContentModelConverter`. The fix was to cherry-pick the single doc commit
    onto a fresh branch off `main`.

    Two rules come out of it. **Do not stack a docs PR on an open PR** -- wait for the base to merge
    and branch off `main`, since a docs close-out has no code dependency that justifies the risk.
    And **"the PR is merged" is not "the change is on `main`"**: verify with
    `git log origin/main --grep` or by grepping `origin/main`'s copy of the file for a string the
    change introduced. MERGED is a statement about a PR, not about `main`.

29. **A cost report has the same completeness requirement as a guard: enumerate every consumer of
    the thing being quarantined.** Rule 16 said grep the operation being guarded rather than the
    entry point the item named. Rules 13 and 21 said guardrails and prescribed fixes decay. This is
    the third face of the same failure and it showed up twice in one session, on two unrelated items.

    S-10's guardrail asked what narrowing `mayAcceptInvite` would cost. The obvious costs -- password
    reset stops working, one test reddens -- are both about `accept`, the caller the item discusses.
    The cost that is not in the item is `invalidateInvitesForStatus`, a **second caller** of the same
    predicate, which sweeps when `!mayAcceptInvite(newStatus)`. Narrowing it silently converts every
    admin PATCH setting status ACTIVE into an invite purge. Nothing in S-10 points at that method.

    S-11 named `TokenCipher` as the consumer of `app.access-token.secret`. Grepping the **property
    key** rather than the class finds `ClientGalleryAuthService` signing gallery access tokens and
    password fingerprints with it -- a second confidentiality claim the same public default voids.

    So before writing a cost report, or scoping the impact of a shared value: **grep the thing, not
    the class or method the item discusses.** For a predicate, grep the predicate. For a secret, grep
    the property key. The item names the consumer its author was reading at the time.

    The corollary is why this is worth a rule rather than a note. A guardrail exists to stop the next
    session making a change nobody costed -- so a cost report that enumerates only the callers the
    item already discussed **licenses exactly the change the guardrail was protecting against**, with
    a written analysis attached that makes it look considered.

    And the S-11 half carries a second lesson about this document rather than the code: the three
    uses of that secret were **already written down**, in the "Unsettled" bullet on rotation, fifty
    lines below S-11. A fact filed in one section does not reach the item that needs it. When filing
    a finding, grep this doc for the symbol before assuming the fact is new -- and when picking up an
    item, grep this doc for its symbols before trusting the item's own scope.

## Full-board review: run 2026-08-25, split rather than whole

Recommended 2026-08-24 on three escalation conditions. **Run 2026-08-25 as two of the three slices,
not as one pass**, because the conditions stopped being equally true:

1. **Scoped drift keeps escaping its scope** -- still true, and its fix is a mechanical unscoped ref
   sweep, the cheap slice. **Ran it.**
2. **Specified fixes keep needing adjustment** -- **the streak broke at six.** MR 19 #14 shipped
   exactly as specified. So the per-item re-estimate slice was **deferred**, on the grounds that
   condition 2 was the evidence for it and condition 2 no longer holds. Working rule 27 records what
   actually distinguishes the clean item from the six that needed adjusting.
3. **Security work merged and never re-reviewed as a set** -- fully true, does not decay, and the
   only condition with real downside risk. **Ran it, adversarially.**

What the two slices returned:

**The ref sweep.** About 30 of ~75 refs on open items had drifted, and most of the drift sits outside
any recent merge neighborhood -- which is the argument for condition 1, now measured rather than
asserted. `ImageUploadPipelineService` moved furthest at ~38 lines. `isGalleryAccessAuthorized`'s FQN
parameter drifted for the **fifth** time, which is the case for deleting that number rather than
correcting it a sixth. Counts that broke: `Optional.get()` is 47 on the #218 branch and 46 on `main`,
with `UserShareControllerProd` at 3 rather than 2 because #213 added one; MR 24's validator `@Mock`
removal is 5 test files, not 6; `CollectionService` is 1,726 lines, not 1,746, since #216. Two counts
are ambiguous rather than wrong and are now stated as both numbers -- MR 21's "19 controller sites"
is 19 endpoints but 20 lines, and MR 25's "5 `DownloadResolution` sites" is 5 in test, 7 in total.
Three premises were flagged as possibly stale and deliberately not chased; they are marked in place.

**The security re-attack.** Eleven findings, two HIGH, filed above as S-10 through S-20, plus six
security tests that cannot fail and five unsettled questions. **The two HIGH ones are both
cross-fix**: S-10 is S-7 invalidating a premise that lives in `AdminUserController`, and S-11 is
#213 making a second secret confidentiality-critical without extending the guard S-4 hardened. No
single-item review could have found either, which retires the question of whether condition 3 was
worth the spend.

The deferred slice -- re-verify premise and re-estimate every open item, sliced by wave -- is still
unrun and no longer urgent. Run it when an item is next found to be mis-specified, not on a schedule.

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

- [ ] `filterNonListedChildCollections` (`CollectionService`) describes a context-detection mode that no longer exists. **Premise flagged as possibly stale, 2026-08-25**: the docblock as it stands describes a flag-keyed derivation and explicitly warns that keying on `type == PARENT` would be wrong, which reads as already corrected for the enum deletion. Re-read it before scheduling a rewrite; this may be done.
- [ ] The "previously spread across ContentProcessingUtil" rename-history at `ContentModelConverter` and `ContentMutationUtil` -- that class is gone.
- [ ] "PARENT-shaped" vocabulary at `CollectionService`, `UserPageAssembler` -- dead since the enum deletion. **`TagViewResolver` does not contain that phrase** (2026-08-25); it says "synthetic PARENT model" and "tag-view PARENT model". The vocabulary point survives, the grep target does not.
- Moved 2026-08-24: `CollectionAccessService.effectiveLevel` is now **S-6** under "Open security findings" -- it is an access-control item, not a docblock rewrite, and the re-review found it fails closed rather than leaking.

---

# Wave 5 — Consolidations

## MR 15 — Cross-cutting

- [x] #1. One client-IP resolver. **DONE** -- shipped with bug #3 in MR 5 ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165)).

- [x] #2. One SecurityConfig matcher instead of the copy-pasted `isRealUser` guards. **DONE** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)). **17 guards, not 18** -- the re-derivation counted a javadoc line in `UserShareControllerProd`. The matcher went OUTSIDE the enforce-authz toggle, next to `/api/auth/me`: the guards it replaced were unconditional, so that is the only behavior-preserving placement, and the guardrail's "costs a dev convenience" was false -- dev already required a session on these routes. A flyby now gets 403 rather than 401 there, by decision. Java-only main -42; 28 controller-level assertions became `config/UserRoutesAuthorizationWebMvcTest`. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-2-outcome-2026-08-23).
- [x] #6. `currentUserId` is duplicated. **DONE** ([#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). Four copies became `config/CurrentUser.userId()`, joining `ClientIp` and `GalleryAccessCookies` as a static helper next to the security plumbing. The item's "move it onto `AuthPrincipal`" does not work -- that is a Spring-free record and this is a static context read. The null contract was left alone and costed instead: the four admin sites break local dev only, the two read-surface sites 500 a logged-out visitor, so it is two problems and not one. Java-only main -26 lines / +36 words. Two more copies of the same read were found and deliberately not folded in (`SyntheticCollectionResolver.currentPrincipal`, `CollectionService.viewerMaySeeHidden`) -- see rule 14. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-6-outcome-2026-08-24).

### The MR 15 #6 follow-up — closed 2026-08-24

- [x] Fold the last two copies of the same static read into `CurrentUser`. **DONE**
  ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210), squash `c1f482e`).
  `SyntheticCollectionResolver.currentPrincipal` deleted, `CollectionService.viewerMaySeeHidden`
  delegates, both files drop their `SecurityContextHolder` import. Java-only **-11 / +4**,
  behavior-preserving, no test changes. Grepping the read shape across `src/main` now returns
  **four** sites, not six -- `CurrentUser` plus the three that are not copies
  (`CollaboratorAccessInterceptor` resolves an access level, `FlybySessionFilter` tests whether an
  authentication exists, `AuthController` serves `/api/auth/me`). **The MR 15 #6 thread is now
  fully closed**, four sessions after it opened.

  Coverage was proven rather than assumed: replacing each delegation with a hard-coded `null`
  reddens 4 errors in `SyntheticCollectionResolverTest` and 3 in
  `CollectionServiceTest$EnforceVisibilityVisibilityRules`.

## MR 16 — Infrastructure classes

- [ ] #3. One keyed rate limiter. **Re-derived 2026-08-24: three copies, not two** -- `RateLimitFilter.newBucket` is a third byte-identical Caffeine+Bucket4j core. **Two halves of the original wording were wrong and are corrected here.** "The same class twice" is false: `ContactMessageLimiter` carries a global daily bucket that a `KeyedRateLimiter(capacity, window, idleTtl)` signature has no slot for, and its own docblock calls that bucket the only limit an attacker cannot pick the key for. "Their TTL policies have already drifted" is also false: `ClientGalleryAccessLimiter`'s `window + 15min` idle TTL is a documented deliberate choice (an attacker must not reset it by pausing), and calling it drift invites someone to "fix" it to 2h and weaken it. **Cost is test-dominated: ~-55 source against ~84 test sites** (7 constructor sites + 24 calls in `ContactMessageLimiterTest`, 7 + 32 in `ClientGalleryAccessLimiterTest`, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`). Keep `AuthLoginLimiter` separate -- it is a `Cache<String,Integer>` counter, not Bucket4j. Low priority.

  **Cost re-measured 2026-08-24 while doing S-5, which was told to leave these cores alone. Every number above held, and one new blocker turned up.** The test-site counts are exact, not approximate: `ContactMessageLimiterTest` has 7 constructor sites and 24 `tryConsume` calls, `ClientGalleryAccessLimiterTest` has 7 and 32 `.allow(` calls -- 70 in the two dedicated tests, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`. Source is 82 + 81 lines across the two classes, and the shared part of them is small: the bucket shape (`Bandwidth.builder().capacity(n).refillIntervally(n, window)` wrapped in `Bucket.builder().addLimit(...)`) and the `Caffeine.newBuilder().maximumSize(10_000)` cache. Everything around it differs.

  **The new blocker is `Retry-After`.** `RateLimitFilter` does not just ask its bucket a yes/no question -- it calls `bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill()` to build the header (the only such call in the codebase). A `boolean allow(String key)` signature, which is the shape the other two callers want, cannot serve it. A merged class has to expose the `Bucket` or a nanos-to-refill accessor, and that is a wider API than the item's framing implies.

  **Four more things that do not merge**, all found by reading the three call sites rather than the class list. (1) Three different key functions: `email.trim().toLowerCase(Locale.ROOT)`, `ip.trim() + "|" + GalleryAccessCookies.normalizeSlug(slug)`, and `ClientIp.resolve(request)` -- so the shared class takes a pre-computed key and each caller keeps its own normalization, which is most of what looked like the duplication. (2) Three different blank-key policies: `ContactMessageLimiter` skips the per-email bucket but has already spent a global token, `ClientGalleryAccessLimiter` returns true, `RateLimitFilter` has no blank case. (3) The idle TTL cannot have a default -- `ClientGalleryAccessLimiter`'s `window + 15min` is deliberate and the other two are a fixed 2h, so it must be an explicit constructor parameter, which is the parameter most likely to be got wrong later. (4) `ClientGalleryAccessLimiter`'s package-private `Duration` constructor exists so refill-timing tests can use sub-second windows instead of sleeping for minutes; it has to survive the merge intact.

  **Verdict unchanged, with more confidence behind it: not worth doing.** The merge saves roughly 50 source lines, needs a wider API than a boolean, and rewrites ~70 test call sites -- and the four items above are each a way to quietly weaken a live limiter while the suite stays green. S-5 no longer collides with it; that file is settled.
- [ ] #4. One AWS config class. **Best value in MR 16: zero test coupling** -- nothing in `src/test` references `S3Config` or `SesConfig`, and there is no `@Import`, so the rename to `AwsClientConfig` is free. Premise verified intact 2026-08-24. `config/SesConfig.java` duplicates S3Config's credentials plumbing and borrows `aws.s3.region` for a non-S3 client. Merge the SesV2Client bean into S3Config (rename it `AwsClientConfig`), share one `AwsCredentialsProvider` bean across the four clients, and delete the catch-log-rethrow blocks. ~40 lines.
- [ ] #5. One CloudFront invalidation implementation. **The item undersells itself**: `cloudFrontClient` and `cloudFrontDistributionId` are used only inside `invalidateCloudFrontPaths`, so delegating removes two constructor dependencies (arity 10 -> 9). Test cost is ~4 lines and no mock or verify is rewritten. **Trap**: route through `invalidatePaths(List<String>)` as written -- routing through `markChanged()` swaps specific keys for two wildcards and defers to after-commit, which is a behavior change. `services/ImageProcessingService.java:838-863` re-implements what `services/ReadCacheInvalidator.java:79-106` already owns. Give `ReadCacheInvalidator` an `invalidatePaths(List<String>)` and delegate. ~25 lines.

## MR 17 — Controllers

- [ ] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.getAllImages` (**`258-294` as of #218**) vs `ContentControllerProd.searchImages` (`45-77`, correct)). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. **"Reuse prod's constraints" is an unpriced behavior change**: admin clamps with `Math.min(Math.max(size, 1), 200)` while prod validates with `@Min/@Max`, so admin `size=500` goes from silently returning 200 rows to a 400; defaults also differ (50 vs 30), and two frontend pages that pass no `size` would jump from 30 images to 50. **Do MR 19 #19 first** -- it is the same decision from the other direction, and #7 then shrinks to sharing the filter record. Realistic ~70 with test.
- [ ] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController:343-360` -- `addUserToRole` / `removeUserFromRole` -- vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:149-166` -- `addMember` / `removeMember`). Keep the roles-side pair. **Blocker resolved 2026-08-24: the frontend uses BOTH**, driving two different screens (`RoleDetailView.tsx` calls the roles-side route, `UserRolesSection.tsx` the users-side). So this is a coordinated cross-repo change with deploy ordering, not a backend delete -- cheapest path is making the users-side method delegate to the roles-side one, leaving components untouched. **PR #191 lowered its priority**: both pairs now route through the guarded `RoleRepository.addMember`, so this is tidiness, not security. Scope must also include that method's docblock, which says "the two admin endpoints that reach here".

## MR 18 — Services

- [ ] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`processFilesFromDiskLoop`, **`316-420` as of #218**, vs `ingestFilesGroupedByDayLoop`, **`444-555`** -- the largest drift on the board, ~38 lines each), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. **Three copies, not two** -- the CREATE/UPDATE arms inside `processFilesFromDiskLoop` are a third. Net deletion ~110, better than the stated ~85, and all source: **zero forced test churn**.
- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.updateGif`, **`546-635` as of #218**, vs the three `updateImage*Optimized` helpers in `ContentMutationUtil`, **`183-243`**: Tags 183, People 205, Locations 227). **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, which are declared on subclasses, not `ContentEntity`. The fix needs a return-the-set signature, not a retype, and it converts `ContentServiceTest.updateGif_persistsPeopleAndLocations` into a weaker test. Realistic ~180, not ~40.
- [ ] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService` `validateNoLinkCycle`/`parentIdsOf` (`465-495` as of #216; find them by name). One `walk(root, neighborsFn)` helper. **Five walks, not four** -- `propagateToVisibleSubtree` is a fifth the line range missed. ~95 lines, zero test churn, pinned by 33 integration tests. **Best value in MR 18.**
- [ ] #12. `nextOrderIndex` logic. **Five places, not four** -- `TagService` is the fifth. Do it by keeping `ContentService.nextOrderIndex` as a one-line delegate, which makes test churn zero; the naive version costs 15 stub edits in `ImageUploadPipelineServiceTest` for 5 lines of dedupe. **Do it the delegate way or not at all.**
- [ ] #13. Entity-to-Record mapping and case-insensitive sort duplicated across four files (`Records.Tag` mapping at `ContentModelConverter.convertTagsToModels` (**`328` as of #218**), `MetadataService.toTagRecord` (**`431`**, Location mapping at `439`), `SyntheticCollectionResolver:150`, `ContentService`'s newly-created-tags map (**`994`**); Location mapping/sort twice). Static `from(entity)` factories on the records. **Counts are 10 tag + 4 location sites, not 6+2, and the estimate is the worst on the board: net ~0 lines**, because every copy and every replacement is one line. The suggested fix also flips the layering -- `Records.java` currently imports nothing from `entity`. **The finding worth keeping is not the dedupe**: `ContentModelConverter` and `CollectionProcessingUtil` sort their output and `MetadataService`/`SyntheticCollectionResolver`/`ContentService` do not, which is a live API-ordering inconsistency. Split that out and drop the rest.

## MR 19 — Query efficiency and data layer

- [x] #14. `convertEntityToModel` loaded the same content row twice. **DONE**
  ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)). Two queries to one on a
  method `ContentService` calls 3x per GIF or text mutation. The switch is gone and the entity
  `findAllByIds` already returned goes straight to `convertBulkLoadedContentToModel`.

  **This is the first item in seven to need no adjustment at implementation time.** Premise, ref and
  prescribed fix were all correct as written -- the ref was the only one in the previous sweep that
  had not moved, and it was still correct a day later. Working rule 21 says an item's premise is
  evidence and its fix is a hypothesis; this is the hypothesis holding. **What made the difference
  is that the previous session discharged the item's own open question before specifying the fix**,
  and did it for all four content types rather than only the one the question named. That is now
  working rule 27.

  **The method had no test at all.** `convertEntityToModel_withUnknownBlockType_shouldThrowException`
  is named for it and calls `convertRegularContentEntityToModel`. Two tests added, TEXT and
  COLLECTION, each asserting the model comes out hydrated and the typed finder is never called.
  Restoring the switch reddens both and **nothing else in the suite notices** -- 1,375 tests were
  blind to which of two queries hydrated this entity. Suite 1,375 -> 1,377, 0 checkstyle.

  One branch went with the switch: the "Failed to load typed content entity" path, reachable only if
  the row vanished between the two queries. There is no second query to lose that race now.

  **Deletion cost for the two dead finders, which is what the guardrail asked for.** Both are now at
  zero `src/main` callers. Each is a 5-line method with no javadoc, `findTextById` under the "Text
  Operations" banner and `findCollectionContentById` under "Collection Content Operations".

  | | `findTextById` | `findCollectionContentById` |
  |---|---|---|
  | `src/main` callers | 0 | 0 |
  | Test references | 2 (both added by #218) | 2 (both added by #218) |
  | Cascade | none | none |

  **The cascade is the part that looked expensive and is not.** `SELECT_CONTENT_TEXT` and
  `CONTENT_TEXT_ROW_MAPPER` each keep a user in `findAllByIds`; the two COLLECTION constants keep
  three or four. No constant, mapper or import goes dead behind them. `ContentRepositoryTest` is 78
  lines covering one random-order query, so there is no repository test surface to disturb either.
  Real cost: about 10 deleted lines, zero test edits, compiler-verified.

  **The one genuine coupling was created by #218 itself, and it argues for deleting rather than
  against.** The only references left are the `verify(contentRepository, never()).findTextById(...)`
  and `.findCollectionContentById(...)` assertions in `ContentModelConverterTest`, which exist to
  pin the single-fetch behavior. Deleting the finders deletes those two mutation-detectors -- but it
  replaces them with something stronger, because reintroducing the switch then fails to compile. The
  two tests still assert correct hydration either way. **Measured before #218, both finders had zero
  test references anywhere; that number is 2 now and both are #218's own.**

  Left in place per the guardrail: a repository deletion is Wave 1 work with its own risk profile.
  It is now a two-line change whenever Wave 1 reopens.

  **Inventory correction.** The item said `findImageById` has "4 other callers". Outside the dao
  there are **5** call sites: `ContentService` (twice, one inside its own `findImageById` wrapper),
  `ContentModelConverter`, `UserPageAssembler`, `CollectionProcessingUtil`. A sixth `ContentService`
  site calls the service wrapper, not the repository. `findGifById` keeps 2, as recorded.

- [ ] #15. `getUpdateCollectionData` fetches the collection row twice and has an always-true null check (`CollectionService.getUpdateCollectionData`, **`845-914` as of #216**, was `846-915`).
- [x] #16. `findCurrentContentCollections` N+1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)).
  201 queries -> 1. The diagnosis was exact; **the suggested fix was not, and would have shipped a
  silent bug**. `cc.id IN (:ids) OR cc.referenced_collection_id IN (:ids)` drops the parent scope
  the loop had for free by construction, so it matches blocks linked under a different parent --
  `removeContentFromCollection` is parent-scoped and would delete nothing, but `onChildUnlinked`
  would still fire role-grant propagation for a link that never existed. Test coupling was two
  stub lines, not one. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).
- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (now **140-152 and 220-237**, was 85-130; the file went 130 -> 238 lines under S-7/S-9, so re-read before quoting -- into `findLiveInvite`); pagination normalization re-inlined in `CollectionService.getCollectionWithPagination` (**`143-145` as of #218, was `142-144` then `127-130`, and it is three lines not four**; call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil.toEntity` **`566-589`** and `applyPaginationDefaults` **`924-932`** as of 2026-08-25, were `569-596, 939-947` -- **neither file was touched by #213/#214/#216, so this drift predates them**); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService:697-745`); EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml` and `buildShareLinkHtml`, the third added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional, **~50-70 lines now, not ~35**). #213's own write-up sent this consolidation to MR 24; that was wrong, it lives here and has always lived here.

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
- [ ] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService` `930-932` (as of #216, was `931-933`). Rename the record `FilmFormatOption`, import the enum, one static factory.

---

# Wave 6 — Conventions

## MR 20 — The bare-array decision (breaking; coordinate with the frontend)

- [ ] Decide first. **17 endpoints** (the prose said 15; the item's own list has always had 17, and 17 is what a re-derivation finds) return top-level JSON arrays against the stated "objects only" rule: `AdminController:85`; `AdminUserController:153, 328, 383, 396`; `CollectionAdminController:43`; `ContentControllerProd:85, 96, 107, 118, 130` -- all correct as of #218. **Six drifted, re-derived 2026-08-25 and named by symbol**: `AdminRoleController.listRoles` (`48`), `UserFollowsControllerProd.list` (`52`), `UserSavesControllerProd.list` (`50`) and `.listImages` (`56`), `UserSelectsControllerProd.list` (`55`), `UserRatingOverrideControllerProd.list` (`48`). **`UserSelectsControllerProd.list` is carried twice on this board**, here and under MR 22, and only MR 22's copy was corrected on 2026-08-24 -- deduplicate it rather than correcting it in two places. `CollectionAdminController:37` even documents the violation as policy. Either wrap them in one breaking-change MR, or amend `.claude/CLAUDE.md` to bless bare arrays. Today the codebase carries two contradictory conventions.

  **Frontend answer, 2026-08-24: it consumes bare arrays directly** at 20 call sites across
  `app/lib/api/{adminHome,roles,users,personal,selects,content}.ts`, typed as `T[]`. So wrapping is
  breaking for 13 of the 17. Backend cost is 17 source sites against **92 array-shape assertions in
  25 test methods across 8 files**, plus 15 frontend test files.

  **The de-risking split the item does not offer:** four of the 17 have **no frontend consumer at
  all** -- `/api/read/content/people`, `/cameras`, `/lenses` and `/api/read/user/rating-overrides`
  (the last has no backend controller test either). Those four can be wrapped today with zero
  coordination, which settles the convention question in code before negotiating the breaking 13.

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
  behavior, which is the point. ~27 source sites against 59 Map-shaped lines in 17 test files; ten of
  the affected endpoints have zero test references at all.

## MR 22 — Remaining convention sweeps

- [ ] `ResponseEntity<?>` twice: `UserSelectsControllerProd.list` (**`:55`, was `:59`** -- re-verified 2026-08-24; serves two different shapes from one GET — split or wrap) and `MessagesControllerPublic:43` (throw a `RateLimitedException` handled globally, which also unifies the three different 429 body shapes currently in play: empty at `AuthController` (**`75` as of #218**), Map at `CollectionControllerProd` (**`183-184`**), ErrorResponse at `MessagesControllerPublic:48-52`, correct).
- [ ] Try-catch in controllers, **two sites** (not three -- the third went with bug #15 in MR 7, [#168](https://github.com/themancalledzac/edens.zac.backend/pull/168), confirmed gone by grep): `AdminUserController.mergePreview` and `.merge` (map via `ResourceNotFoundException` plus a new `ConflictException` handler). **Both methods have zero tests**, so this is an untested behavior change on two admin endpoints -- a risk, not a saving.
- [ ] `@Value` field injection: **9 sites, not 3.** The three named (`CollectionControllerProd`, `ShareControllerProd`, `DownloadUrlService`) plus six in `S3Config` and `SesConfig` that feed `@Bean` methods -- same rule, same fix, and they fold into MR 16 #4. Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly four `ReflectionTestUtils.setField` calls. Also `@Autowired` on constructors at `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 3 comments**: only `AuthLoginLimiter` has a single constructor; the other three genuinely have two, where the second is the package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.
- [ ] Fully qualified names inline: **14 sites, not 6.** The six named (`CollectionService.isGalleryAccessAuthorized`'s parameter -- the doc's `542`, then `533`, then `534`, then `541`, **is `539` as of #218 -- the fifth correction to one ref, so stop writing the number** after S-6's javadoc, which is the **fourth** correction to one ref and the reason this item names symbols and not lines. Read the number as advisory and the symbol as the target; `CollectionProcessingUtil`, `TagViewResolver`, `GalleryAccessCookies`, `ContactMessageLimiter`, `Records.java`) plus eight in the data layer the original scan missed: `BaseDao` (3), `CollectionRepository`, `EquipmentRepository` (3), `PersonRepository`. Import-only, **zero test coupling**. `Records.java` still needs consolidation #20 first (the `FilmFormat` name clash).
- [ ] `Optional.get()` -- **47 sites as of #218, 46 on `main`; not the 17 originally named.** *(Re-derived a fourth time 2026-08-25 by the unscoped sweep, and this time a component moved without the total holding: `UserShareControllerProd` is **3, not 2** -- #213 added `buildShareUrl(token.get())`. #218 adds one in `ContentModelConverter`, exactly attributable. Raw sweep 58 on the branch, 57 on `main`; the 11 Atomic exclusions still check out.)* **The claim "the 17 named are all still present" cannot be checked and arithmetic says it is wrong**: the originally-named files now hold 14 between them, and the 17 were never enumerated, so the sentence is unverifiable by construction. Drop it rather than carry it. *(Earlier re-derivations, kept for the pattern they show -- 45 -> 46 on 2026-08-24: S-1 added `maybeUser.get().getStatus()` to `AuthController.login`, taking that file 3 -> 4. Re-derived after the merge, not estimated -- the raw sweep went 56 -> 57 and the one new line is S-1's. This is the inventory rot working rule 5 warns about, caught by the scoped sweep rather than a full pass.)* The 17 named are all still present; 29 more sit in twelve files the original scan never covered (`AdminUserController` 4, `AuthController` 4, `InviteController` 3, `ImageProcessingService` 5, `UserMergeService` 3, `UserShareControllerProd` 2, `ClientGalleryAuthService` 2, `SessionService` 2, and one each in `LocationRepository`, `TagRepository`, `AdminBootstrap`, `ImageUploadPipelineService`). A raw `.get()` sweep returns 56 lines; 11 are `AtomicInteger`/`AtomicReference`, not `Optional`. **Re-derived again 2026-08-24 after S-7/S-9, and the headline number survived for the wrong reason.** The raw sweep is still 57 and the Optional subset still 46 -- but two files moved and cancelled out: `InviteController` went **3 -> 2** (S-7 moved the accept body into the service) and `UserInviteService` went **2 -> 3** (`accept` added its own `maybeInvite.get()`). A total that holds while its components move is the most misleading state an inventory can be in, so trust the per-file breakdown here over the headline. *Re-derived a third time 2026-08-24 after S-8: raw sweep **still 57**, Optional subset **still 46**, and this time for the right reason -- S-8 added no `.get()` at all (`AdminUserController` holds at 4, `SessionService` at 2). Two consecutive checks now agree on both the total and the breakdown.* Zero test coupling. **This is not an MR** -- the doc's own "rewrite opportunistically when touching these methods" is the right disposition, now with the real denominator.
- [ ] Magic number 2500 at both resize call sites (`ImageProcessingService`, **`191` and `282` as of #218**). Name it.
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
- [ ] Same shape, smaller: `UserShareControllerProd` computes grant and candidate sets inline with a repository. Move it into `ShareLinkService`. **Re-derived 2026-08-24 and de-positionalized**: the old range `124-152` overran the end of a 145-line file. The work is two private methods -- `buildSettings` and `candidateCollections`, the latter
  holding the `memberCollectionIdsForUser` call. **Find them by name.** The 2026-08-24 pass
  "de-positionalized" this by writing fresher numbers (`:116-128`, `:135-144`, `:137`), and
  [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) invalidated all three the
  same day: the file went 145 -> 214 lines and they are now `183-197`, `204-213` and `206`. That
  is working rule 26.
- [ ] `Synthetic.blogsOnly` is a constant at its only reachable call site (`SyntheticCollectionResolver:42-49, 97`, both refs correct). **Premise flagged as FALSE, 2026-08-25**: the catalog has three entries -- false, true, false -- and `:97` is reached by both `ALL_BLOGS` (true) and `ALL_CLIENT_GALLERIES` (false), so it is not constant at that site. The fold may still be right; the stated reason is not, a transitional shape from the type-keyed catalog. Fold it out.
- [ ] `MessageService` is a pure pass-through with a speculative docblock. Keep it for layering or delete it, but drop the justification.
- [ ] The validator components (`MetadataValidator` repeats its 3-line null check **six** times, not four; `ContentValidator` is similar) are the "unnecessary utility classes" CLAUDE.md bans. Replace with bean validation on the DTOs when next touched. **~199 source lines across 3 files, not ~60**, plus `@Mock` removal in **5** test files (**re-derived 2026-08-25**, was 6: `ImageProcessingServiceTest`, `ContentServiceTest`, `ImageUploadPipelineServiceTest`, `ContentServiceDownloadTest`, `MetadataServiceTest`) and a constructor arg off 4 services, which is exact -- a 9-file change, so "when next touched" is right.
- [ ] Executor handling in `ImageUploadPipelineService`: `rawUploadExecutor` now runs whole disk and ingest jobs, so it is misnamed and mis-documented, and `shutdown()` shuts down both executors but awaits only `rawUploadExecutor`. **All three sub-claims verified 2026-08-24. This is a real bug (an unwaited executor on shutdown), not a design note** -- ~10 lines in one file. Promote it out of the "remaining design items" list.
- [ ] `AdminHomeService`'s AtomicReference cache has no TTL and is per-instance. Fine single-node; note it for any multi-instance future.
- [ ] Service decomposition, the standing item. **Recounted 2026-08-24 by `wc`, and the argument is
  stronger than when it was written.** The four files are `CollectionService` **1,726** (**re-measured 2026-08-25**; #216 took 20 lines out of the 1,746 recorded here),
  `ContentService` **1,014**, `ImageProcessingService` **1,390**, `CollectionProcessingUtil`
  **933** -- all four quoted numbers were stale. The total went 5,107 -> 5,083 across 24
  MRs of dedicated cleanup, and is **5,063 as of #218** -- a net -44 lines, under one percent, and
  `ImageProcessingService` **grew 25**. "Waves 5-7 shrink these" is not what the data shows; the waves have been shrinking
  other files. Decide the split boundaries before the next feature lands in them. **COLD -- this
  needs a decision, not research.**

---

# Wave 8 — Tests

## MR 25 — Shared fixtures and consolidation

- [ ] `new ContentModels.Image(` with 31 positional components appears in **11** test files (not 12; 13 call sites), **7** of which have their own private helper. Same for `CollectionRequests.Update` -- the canonical record has **21** components and the deletion target is the **17**-arg compat constructor, at **24** call sites across 7 files. One `TestFixtures` class with builders. **The doc underestimates by ~2x in the good direction**: measured, those sites are **745 lines of positional construction**, replaced by roughly 120, so **~-600 net, 18 test files, and zero main files touched.**
- [ ] `services/CollectionServiceTest.java` (**2,640 lines as of #218**, was 2,644, not the baseline 2,412 -- it grew 232 since baseline, so the estimate below is measured against the wrong denominator): assert/verify twins where the second test re-runs the first's stubbing and re-checks with `verify` — for example `createCollection_happyPath_savesAndReturnsUpdateResponse` (**`:136` as of #218**) versus `createCollection_verifiesEntityCreatedViaUtil` (**`:165`**); the `deleteCollection` plain-verify test (**`:196`**) is a strict subset of the inOrder version (**`:224`**). All four shifted by exactly +8, which is one edit near the top of the file. ~250 lines.
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

**Sizing note added 2026-08-24 from #209, and it applies to every item that adds a field to a
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
- [ ] `model/DownloadResolution.java` -- the `extension` component: **5** construction sites in test (not 4), **7 in total** as re-derived 2026-08-25 -- the two in `src/main` are both in `ContentService`
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

- 2026-08-24 — close-out pass, no code shipped. Reconciled the board after #199/#200/#202. **Fixed five drifted refs**, all in the neighborhood of what merged (working rule 5's third principle held): `#8` 333-350 -> 336-353, `#17`'s `UserInviteService` refs 85-130 -> 140-152/220-237 (that file went 130 -> 238 lines), the bare-array sites 150/318/373/386 -> 149/321/376/389, and Wave 7's two size claims (source 469 -> 474, test 1,015 -> 1,097). **`Optional.get()` held at 46 for the wrong reason** -- `InviteController` 3 -> 2 and `UserInviteService` 2 -> 3 cancelled out, so the headline was right while its components moved; the breakdown is now the source of truth, not the total. **Settled two items by looking**: bug #17 re-verified live at `ContentService:228-233` (premise intact, COLD), and `admin_home_tile.cover_image_id` researched to the end -- though a first pass at that entry got it *wrong* and the doc's existing row was more accurate, which is recorded in the item. **S-8's scope is bigger than its item implied**: there is no user-scoped session revoke primitive, only `revokeByTokenHash`, so it needs a new repository statement plus a service method, not one call. Stamped S-5 COLD, S-6 BLOCKED with the question written out, S-8 COLD. Next: S-8.
- 2026-08-24 — shipped **S-8** ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)), which closes the security board down to S-5 and S-6. **The item's one open judgement went the other way from its own default**: it said mirroring S-9's `mayAcceptInvite` boundary was the default and any divergence should be argued in the MR, and the argument won -- the session predicate is `mayHoldSession`, ACTIVE-only, because `resolve` has enforced ACTIVE-only since S-1 and a test says so deliberately. `{INVITED, ACTIVE}` would have left demoted accounts holding `user_session` rows that can never resolve, which is the thing the item asked to tidy. The two sweeps now run off two allowlists on adjacent lines of one handler, and that is correct rather than sloppy. Working rule 16 applied unprompted and paid: grepping `updateStatus` found three callers, and the enumeration is why only one gets the call. **The cost report was measured rather than argued** -- the CTE was actually written and the suite run, and the single resulting failure turned out to be the only mutation-detector for S-1, so folding the revoke into the DAO would have quietly disarmed an earlier fix. Suite 1,328 -> 1,338. Next: S-5, or answer S-6's blocking question.
- 2026-08-24 — close-out pass after #204. **Fixed three drifted refs**, and for the first time one sat *outside* the merge neighborhood: `AdminRoleController:150-167` was off by one (`149-166`) and nothing in #204 touched that file, so working rule 5's third principle held only for two of the three. The others were in-neighborhood as expected -- `#8`'s `AdminUserController` pair 336-353 -> 343-360, and the bare-array sites 149/321/376/389 -> 153/328/383/396. **Wave 7's `AdminUserController` item was re-measured and then de-positionalized**: its range list had drifted twice in two days, so the four `@Transactional` ranges are now named methods with start lines. That item is also growing faster than it is being done -- 469 -> 474 -> 481 source and 1,015 -> 1,097 -> 1,183 test across two consecutive security MRs, both of which added to the exact class it proposes to split. **`Optional.get()` re-derived clean**: 57 raw / 46 Optional, and this time the breakdown agrees too, unlike the 2026-08-24 pass where two files cancelled out. **S-8's test:source ratio was 2.7:1, the third near-3:1 in a row** -- recorded in the history file as confirmation of a pattern the board had already priced into two open items. **S-6 put to the user rather than left on the board a third time, and it came back wider than asked** -- not "yes, admins through" but "admin means OWNER, never any password restriction, never any permission issue", which is now working rule 20 and widens S-6's scope from two methods to a sweep. The security section is now 2 open, 0 blocked. Next: S-5, then S-6.
- 2026-08-24 — shipped **S-5** ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)), which leaves **S-6 as the only item on the security board**. **The interesting part of the fix was the half the item did not specify**: it named the bypass (`getContentLengthLong()` returns -1 for chunked, so `-1 > 16384` is false) but not that a *bodiless* request reports -1 too. `MockHttpServletRequest.getContentLengthLong()` returns -1 whenever content is null, which is most of the existing suite -- so the obvious one-line fix, `reject if length < 0`, 411s every bodiless public request. The shipped guard is `length < 0 && Transfer-Encoding present`, and **the mutation run proved two pre-existing tests already caught the over-broad version**, which is the reverse of the usual working-rule-15 result. **Working rule 16 came back empty and that was worth recording**: `getContentLength` has two hits in the codebase, both in the same method, so unlike S-1 and S-8 the item's named site really was the only site. **The limiter-merge guardrail held and paid**: MR 19 #3's numbers were re-measured rather than repeated (every one held, exactly -- 7+24 and 7+32 test sites) and reading the three call sites turned up a blocker the board did not have, that `RateLimitFilter` needs `estimateAbilityToConsume` for `Retry-After` and so cannot use a `boolean allow(key)` signature. That item's verdict moved from "low priority" to "not worth doing". **Test:source was 3.6:1, the fourth near-3:1 in a row** and the smallest source change of the four, which suggests the ratio tracks the guard tests rather than the fix. Suite 1,338 -> 1,341. Next: S-6.
- 2026-08-24 — shipped **S-6** ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)). **The security board is closed**: nine items, all done. **The item told the truth about its own scope and was still short by one.** It said rule 20 is a policy, not a ruling about one service, so enumerate before fixing -- and the enumeration found six admin-denial sites against the two the item named. The sixth, `UserSavesService.add` 404ing an admin, appeared in no item on this board and its check lives in SQL rather than in `CollectionAccessService`; it is fixed above the query on purpose, because that query filters on several read paths and an `is_admin` term inside it would apply where nobody looked. **The specified fix would have widened share links if typed in verbatim**: `effectiveLevel` adds two branches, not one, so routing `canView` through it hands a flyby GENERAL and turns a share link into a second way past the gallery password prompt. The two gates screen with `AuthPrincipal.isRealUser` first, which is exactly what the old `userId != null` did. That makes **four consecutive items whose specified fix needed adjusting at implementation time** (S-7, S-8, S-5, S-6) -- recorded in the history file as a pattern rather than a run of luck. **Four list-scoping sites were deliberately not fixed** and the reasoning written down, because rule 20 settled bouncing and not scoping. Suite 1,341 -> 1,347; three mutations verified red. Next: nothing on this board.

- 2026-08-24 — close-out pass, no code shipped. **Verified both merged**: S-5 `516c276` (#206, squash) and S-6 `d79d30f` (#207, squash), branches deleted. **The security board is closed -- nine items, zero open.** **The drift sweep found the most valuable thing on this run and it was not a line number**: the `CurrentUser` fold item is now *half done*, because S-6 needed the whole principal at two gates and added `CurrentUser.principal()` with `userId()` delegating -- exactly clauses one and two of an item neither S-6 nor the item knew about. That is the board's third principle paying out literally, and it was only visible because the sweep is scoped to the merge neighborhood. **Four refs corrected, all inside that neighborhood**: `viewerMaySeeHidden` 1531 -> 1534, the `isGalleryAccessAuthorized` FQN 534 -> 541 (**fourth** correction to one ref), `UserSelectsControllerProd.list` 59 -> 55, and `UserShareControllerProd`'s `124-152` de-positionalized after the old range was found to **overrun the end of a 145-line file**. **Added working rule 21**, hoisted from a four-item pattern rather than one item: S-7, S-8, S-5 and S-6 each had a correct premise and a prescribed fix that would have shipped a bug verbatim, and in three of the four the miss was an unenumerated input, not bad reasoning. **Fixed the log itself** -- the last five entries were in reverse order, because recent sessions prepended where the file appends. Next: `isPasswordProtected` on the content-block path, chosen over the warmer `CurrentUser` fold because the frontend's C6 is blocked on it.

- 2026-08-24 — shipped **`isPasswordProtected` on the content-block path** ([#209](https://github.com/themancalledzac/edens.zac.backend/pull/209), squash `a6550b0`), and opened the **`CurrentUser` fold** ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210), rebased onto `a6550b0`, mergeable, 1,350 green). **Working rule 21 earned its keep on its first outing**: the item said to populate the flag "where the four content-block builders construct one", and there are **two** construction sites, not four -- plus two record copy methods the item never named. `withTags` is the one that mattered, because it rebuilds the record on the synthetic-list path immediately after `fromCollectionModel`, so a faithful reading of the item would have shipped a flag reading `false` on exactly the path the frontend's C6 needs and `true` everywhere else. **The costing the guardrail asked for found the sharper fact**: a `gallery_password` filter on the read queries would not merely break a contract, it would **empty `all-client-galleries`**, since that list selects `is_client = true` and client galleries are the protected ones. **Added working rule 22**, hoisted from a second stale comment found while costing: `CollectionControllerProdTest` claims `coverImage` stripping in **two** places, the behavior exists in neither, and the test named for it cannot fail. The banner that crossed the repo boundary was fixed in #209; the other is a new row under "Carried forward", because deciding it is work rather than a comment fix. Next: merge #210, then the `share/email` 404 -- the last cross-repo item another team is waiting on.

- 2026-08-24 — close-out pass, no code shipped. **Verified merged**: #210 `c1f482e` and #211 `9e363fa`, both of which landed *while the previous close-out was being written* -- so #211 went in saying "#210 PR OPEN, not merged" and was stale before it merged. The MR 15 #6 thread is **fully closed**, four sessions after it opened. **The sweep found five drifted refs and all five indict the previous sweep**, which reported the same neighborhood clean: `CollectionService` `460-490` -> `466-496`, `822-848` -> `846-915` and `912-914` -> `931-933`, `SyntheticCollectionResolver` `153` -> `150` and `86-92` -> `97`. None of that drift came from #209 -- the previous pass checked whether its own diff had moved them, which is a different and much weaker question. **Added working rule 23** for exactly that, and the tell it names is a sweep reporting zero corrections. **Step 3 paid out biggest**: the `share/email` item said "decide whether to build it or have the frontend remove the button", and three facts settled it in one pass -- the frontend's `ShareEmailResult {sent, reason}` is field-for-field `EmailService.SendResult`, which **already exists**, and both sides already handle `email.enabled=false` gracefully, so it ships without SES configured. The item went from a product decision to a specified one-endpoint build. Also added a sizing note to MR 25: "add a field to a record" costs the record plus every `with*` copy and every positional test construction, which is why #209 was four files. Next: `share/email`.

---

- 2026-08-24 -- shipped **`share/email`** ([#213](https://github.com/themancalledzac/edens.zac.backend/pull/213)) and **actuator hardening** ([#214](https://github.com/themancalledzac/edens.zac.backend/pull/214)). **The cross-repo board is closed; nothing is owed to another team.** The headline is that **the item could not be built as specified**, and the specification was the most detailed on this board -- file list, method names, "no new response type". The endpoint would have had nothing to put in the email: the frontend sends `{ toEmail }` alone and V56 stored only the token's hash, so the share URL is not reconstructible server-side. The two ways out were rotating on send, which the item's own guardrail forbids, or storing a copy the owner can read back. **The frontend had already assumed the second and said so in a docblock** -- "a link minted before the backend stored a decryptable copy" -- which is the contract of `fec14e7`, a commit written 2026-08-14 and orphaned when it landed 14 minutes after its PR merged. This is that commit rebased, and **three of its parts had gone stale underneath it, all from MR 15 #6**: its migration number V57 is now `lowercase_text_format_type`; its `isRealUser` guard and its every-route-401 test would have reinstated the controller-level pattern #191 deliberately moved into `SecurityConfig`; and its `@Value` field violates CLAUDE.md's constructor-injection rule. That taught **working rule 24** -- five consecutive items have needed adjusting at implementation time, and this is the first that was impossible rather than imprecise. **Working rule 25 came out of the actuator MR going one step past its own spec**: the item asked to pin the shipped exclude string, but the hardening rests on "Boot applies exclude after include", an ordering this board quoted and never executed. Booting with `include=*` confirms it, and the mutation confirms the test is not vacuous -- with the exclude emptied, `/actuator/env` answers 200 on the app port. Drift sweep, scoped to the touched neighborhood per rule 23: the board carries exactly **one** line ref into these files, `EmailService:56`, correct on `main` today and becoming `:61` once #213 merges -- recorded rather than left to rot, and the ref moves to the history file with its item anyway. Suites 1,350 -> 1,361 (#213) and -> 1,357 (#214), 0 checkstyle; five mutations verified red across the two. **Not done and flagged in both the PR and the history file: `share/email` has no rate limit.** Next: nothing on the cross-repo board -- MR 19 #16 or MR 16 #4/#5.


- 2026-08-25 -- shipped **MR 19 #16** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)), the `findCurrentContentCollections` N+1: 201 queries down to 1. **The item's diagnosis was exact and its suggested fix was a silent bug.** `cc.id IN (:ids) OR cc.referenced_collection_id IN (:ids)` drops the parent scope that the loop had for free by construction, so it matches blocks linked under a different parent -- `removeContentFromCollection` is parent-scoped and deletes nothing, but `onChildUnlinked` still fires role-grant propagation for a link that never existed. Verified against real Postgres rather than Mockito, because every property at issue lives in the SQL; the drop-the-parent-scope mutation is what turns `doesNotReachIntoADifferentParentsLinks` red. **Drift sweep: nine refs corrected, one correct.** #11 `466-496`->`465-495`, #15 `846-915`->`845-914`, #20 `931-933`->`930-932`, #17 `CollectionService` `127-130`->`142-144` (and three lines, not four), #17 `CollectionProcessingUtil` `569-596`->`566-589` and `939-947`->`924-932`, MR 24's three `UserShareControllerProd` refs `116-128`/`135-144`/`137`->`183-197`/`204-213`/`206`. Only five of the nine are attributable to this session's merges; **the `CollectionProcessingUtil` pair sits in a file none of #213/#214/#216 touched**, which is the third consecutive sweep to find drift outside the neighborhood it was scoped to. **Added working rule 26** from MR 24's bullet, which "de-positionalized" itself on 2026-08-24 by writing three fresher line numbers and had all three invalidated by #213 hours later. **Step 3 settled two facts.** #14's "verify COLLECTION hydration first" is discharged for all four content types -- `findAllByIds` uses the identical `SELECT_CONTENT_*` fragment and `CONTENT_*_ROW_MAPPER` as each single-id finder, so the re-fetch is byte-identical -- which makes #14 COLD and fully specified. And #17's "EmailService HTML skeleton twice" is now **three times**, since #213 added `buildShareLinkHtml` under a guardrail not to fold it in; #213's write-up sent that consolidation to MR 24, which was wrong, and it is corrected here. Suite 1,368 -> 1,375, 0 checkstyle; three mutations verified red. Next: MR 19 #14. **Recommending a full-board review** -- see the note under "Ordering note".

- 2026-08-25 -- shipped **MR 19 #14** ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) and **ran the full-board review, split into two of its three slices**. #14 is two queries to one in `convertEntityToModel`, and it is **the first item in seven to need no adjustment at implementation time** -- premise, ref and fix all correct as written. That broke the streak which was escalation condition 2 of the review's own case, so the per-item re-estimate slice was **deferred** rather than run, and **working rule 27** records what actually separates the clean item from the six: #14's open question was discharged in a prior pass, so nothing unknown reached implementation. The method **had no test at all** (the test named for it calls a different method); the two added tests redden under the restore-the-switch mutation and **nothing else in the suite does**, so 1,375 tests were blind to which query hydrated that entity. The guardrail's deletion report is filed under the item: both dead finders cost ~10 lines and zero test edits to delete, no cascade, and **the only coupling is one #218 created** -- its own two `verify(never())` mutation-detectors, which deleting the finders replaces with a compile error, a stronger guarantee. Suite 1,375 -> 1,377, 0 checkstyle. **The unscoped ref sweep found ~30 of ~75 refs drifted**, most outside any recent merge neighborhood, which converts escalation condition 1 from an assertion into a measurement; `isGalleryAccessAuthorized`'s FQN ref drifted a **fifth** time and its number is now deleted rather than corrected, and `UserSelectsControllerProd.list` turned out to be carried twice on the board with only one copy maintained. **The security re-attack reopened the board**: 11 findings, 2 HIGH, plus 6 security tests that cannot fail and 5 unsettled questions. **Both HIGH findings are cross-fix and were independently re-verified line by line before filing.** S-10: S-7 widened `mayAcceptInvite` to admit ACTIVE, which silently falsified the comment in `AdminUserController` asserting ACTIVE users have no redeemable invite -- so an admin-issued reset link survives an email correction and `accept` never compares the invite's email to the account's, making redemption by the old inbox a takeover. S-11: #213 made `ACCESS_TOKEN_SECRET` confidentiality-critical while `docker-compose.yml` still defaults it to a value printed in the public repo and `.env.example` never mentions it, so `ProdSecretGuard` -- the thing S-4 hardened -- does not cover the secret that now protects share links at rest. **Neither was findable by a single-item review, which is the answer to whether condition 3 was worth the spend.** Next: S-10, then S-11; MR 16 #4/#5 is still the next non-security item.

- 2026-08-25 -- close-out pass, no code shipped. **The reconcile caught a stranding.** #217, #218 and
  #219 all read MERGED, and the doc pass was not on `main`: #219 was stacked on `docs/close-out-216`,
  #217 merged that branch to `main` first, and #219 then landed into a branch `main` had already
  moved past. The branch could not be merged forward either -- it predates #218 and would have
  reverted `ContentModelConverter` -- so the single doc commit was cherry-picked onto `main`. That is
  **working rule 28**, and the checkable form of it is that MERGED describes a PR, not `main`.
  **Settled two of the five unsettled questions by looking.** S-19 is closed as not-live: the live
  frontend `forwardHeaders` strips `x-real-ip` and re-injects it from an edge-controlled source, with
  a comment saying outright that the client header is not trusted -- so login brute-force limiting
  works. **The palace's copy of that file was two months stale and said the opposite**, which is the
  reason the item stayed open a day longer than it needed to. And `RoleRepository.canView`/`isClient`
  are confirmed dead in `src/main` -- the live callers are the same-named `CollectionAccessService`
  methods, which is the hazard, not a coincidence. All eleven reopened items are now stamped COLD or
  BLOCKED; **two are blocked, both on product calls the user has to make** (S-14: may an admin put an
  arbitrary collection into another user's share scope? S-16: should disabling an account revoke its
  share links or suspend them?). Next: S-10.


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
on. **The `isPasswordProtected` item shipped from here 2026-08-24** ([#209](https://github.com/themancalledzac/edens.zac.backend/pull/209)).
It had been moved here from "Decisions needed" earlier the same day, once the item was found to have
disproved its own remaining decision -- which left implementation, not a call for the user to make.
Worth keeping as precedent: an item parked for a decision may already contain the answer.

- [x] **`isPasswordProtected` on the content-block path.** **DONE** ([#209](https://github.com/themancalledzac/edens.zac.backend/pull/209), squash `a6550b0`). Option A shipped: `ContentModels.Collection` carries the flag, populated at both construction sites. The frontend's C6 is unblocked. The guardrail held -- the read-query password filter and `coverImage` stripping were left alone and costed instead. Taught **working rule 22**, and found a second stale comment worse than the banner it was sent to fix (see the new row under "Carried forward"). [Full write-up](2026-08-22-backend-cleanup-history.md#ispasswordprotected-outcome-2026-08-24----a-locked-tile-can-finally-be-drawn).

- [x] **`POST /api/read/user/share/email`.** **DONE** ([#213](https://github.com/themancalledzac/edens.zac.backend/pull/213)).
  The frontend's live 404 is closed. **The specified scope was not buildable** -- "one
  `@PostMapping`, one `sendShareLinkEmail`, one request record" leaves the endpoint with nothing to
  put in the email, because V56 stored only the token's hash and the frontend sends `{ toEmail }`
  alone. Shipped by rebasing the orphaned `fec14e7`: V58 adds `token_cipher` (AES-256-GCM on the
  existing `app.access-token.secret`, no new env var), `token_hash` keeps its lookup job untouched.
  The guardrail held -- `mintOrRotate` is never called and a test pins it. Taught **working rule
  24**. [Full write-up](2026-08-22-backend-cleanup-history.md#shareemail-outcome-2026-08-24----the-first-item-that-could-not-be-built-as-written).

- [x] **Actuator defense-in-depth.** **DONE** ([#214](https://github.com/themancalledzac/edens.zac.backend/pull/214)).
  Explicit `management.endpoints.web.exposure.exclude` shipped, plus the first test anywhere
  asserting actuator exposure. Went past the item on purpose: pinning the shipped string proves
  nothing about whether exclude beats include, so an end-to-end test boots the app with
  `include=*` and confirms the eight endpoints 404 while health still 200s. **The ordering the
  item asserted is now verified rather than quoted**, and the mutation proves it is load-bearing:
  with the exclude emptied, `/actuator/env` answers 200 on the app port.
  [Full write-up](2026-08-22-backend-cleanup-history.md#actuator-outcome-2026-08-24----the-guarantee-tested-rather-than-the-string).

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
- [ ] `deleteImages`/`deleteGif` delete from S3 before the DB write inside the transaction (`ContentService.deleteImageFromS3` before `deleteImageById`, **`354-356` as of #218**; `deleteGifFromS3` before `deleteGifById`, **`518-519`**). A DB failure orphans the row's URLs; consider afterCommit S3 deletes.
- [ ] **The image-upload job-status endpoint may be entirely dead.** *(New lead 2026-08-24, found
  while answering the `JobStatus` question.)* `POST /content/images/{id}/from-disk` returns 202 with
  a `jobId` "for polling", and `GET /api/admin/content/images/jobs/{jobId}` serves the status -- but
  the frontend never calls either. Zero hits for `jobId`, `jobs/` or `from-disk` across its `app/`
  tree. If the disk-import flow is admin-CLI-only, the whole `JobTrackingService` surface plus its
  ~45 test references may be dead weight. Confirm how disk import is actually triggered before
  acting; this is the kind of "nobody calls it" claim that is wrong when a human uses curl.
- [ ] `updateImages` builds `imageMap` with `Collectors.toMap`, which throws on a duplicate key, so two updates for the same image id in one request fail before any work happens. **Note this is a verified finding sitting in an "unverified leads" appendix** -- it was traced when proving the MR 1b guards unreachable. It belongs with bug #17 (same method) whenever that MR happens; left here with a pointer rather than moved, so the trail survives. Correction to the original wording: `GlobalExceptionHandler` maps `IllegalStateException` to **400**, not 500 (working rule 3).
- [ ] `updateImages` reports per-item errors inside one transaction. Confirm a mid-item `DataAccessException` cannot leave an item half-applied (needs a test).
- [ ] `contentDisposition` (`DownloadUrlService.contentDisposition`, **`127-128` as of #218**) does not escape quotes in filenames. Depends on what `sanitizeFilename` strips.
- [ ] `TagService.convertTagToCollection` briefly persists under a temp slug, visible to a concurrent reader, and may burn a `-1` suffix.
- [ ] `WebAuthnController.registerStart`/`loginStart` declare `throws Exception`; serialization failures become generic 500s.
- [ ] ID-list DAO fetches have no ORDER BY. Spot-checked callers re-order, but not all 7+ call sites were traced.
- [ ] `CollectionServiceTest` was profiled in parts, not read line-by-line. **The original line ranges (937-1385, 1555-2017) are dead** -- pure positions in a file that has grown to 2,644 lines, with no symbol to recover them by. Re-derive from the current tree or drop the lead (working rule 5).

# Appendix D — Not yet started

- [ ] `ml_image_tagging` (design doc, 0% implemented) is still the largest unstarted feature. No stubs in `src/` to maintain, so it costs nothing until you start.

- 2026-08-25 -- shipped **S-10** ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221))
  and **S-11** ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222)), **both HIGH
  findings on the reopened security board, both mutation-verified, both shipped as specified.** That
  makes three items in a row needing no adjustment at implementation time (MR 19 #14, S-10, S-11),
  which is the streak escalation condition 2 of the deferred full-board slice rested on -- it stays
  deferred, and working rule 27 keeps looking like the right explanation: none of the three carried an
  open question into implementation. S-10 is redemption-time identity in `UserInviteService.accept`,
  built on a named predicate rather than a comment (rule 19); its guardrail held --
  `mayAcceptInvite` and the `AdminUserController` sweep are both untouched -- and **the cost report
  the guardrail asked for found a cost the item did not name**, a second caller of the predicate
  (`invalidateInvitesForStatus`) whose behavior narrowing would silently invert. S-11 is one clause
  in `ProdSecretGuard` plus the `.env.example` line; its second consumer,
  `ClientGalleryAuthService`, **was already recorded in this doc's own "Unsettled" bullet** and S-11
  never carried it. Those two together are **working rule 29**. Filed **S-21** (LOW, verified):
  `regenerateInvite` has no status gate, so a DISABLED account gets a 200 and a link that 410s, and a
  PERSON row gets a 409 from a NOT NULL constraint doing a status check's job -- found while costing
  S-10's guardrail, untested today. Reconcile corrections: **S-15's COLD stamp rested on a method
  that does not exist** (`SessionService.revokeAllForUser`; `revokeAllForStatus(id, ACTIVE)` is a
  no-op by construction), `AdminUserController:292` had drifted to `:304` and is now de-positionalized,
  and `ProdSecretGuardTest.Wiring` is five cases rather than four. Also corrected two comments S-7
  had falsified, one of them S-10's own premise. Security board: 11 open -> 8, **0 HIGH**. Next:
  **S-15**, then S-12.
