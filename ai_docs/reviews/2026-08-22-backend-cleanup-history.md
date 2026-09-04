# Backend cleanup — completed work

Closed-out detail split out of [`2026-08-22-backend-cleanup-spike.md`](2026-08-22-backend-cleanup-spike.md)
on 2026-08-23 and grown by every close-out since. **Nothing here is open** -- true again as of
2026-08-29: the `32d2168` re-split had misfiled the open "Decisions needed" and "Stale side
branches" sections here for a day, and both are back in the tracker, along with Appendices C and D.
This file holds: Waves 1-3 and every closed MR and security outcome, the closed security findings'
tracker bodies (moved 2026-08-29), the closed cross-repo board, Appendices A and B, the full-board
review reports, the working rules' original narratives, the session-log archive, and the
[tenth-run refile](#tracker-detail-moved-2026-09-01-the-tenth-run-refile-working-rules-11-and-53)
of every closed row body and "Prior text" chain the tracker had been accreting in place. The tracker
links into these sections when a working rule cites the MR that taught it; read them for evidence,
not for a worklist.

Waves 1-3 and MR 12-13 are complete. Every item in this file is either shipped or was reconciled
into the tracker as carried-forward work.

Line numbers throughout are from the `8c28cf3` baseline and have drifted. Working rules 4 and 5 in
the tracker explain how much.

## Ordering note (moved from the tracker 2026-08-29)

The original review put bug fixes first so deletions would rebase cleanly. We inverted that and started with deletions, because they are compiler-verified and carry no behavior change. The bug MRs rebase onto the deletions instead. Only one item actually collided, and it was handled: `PersonRepository.deleteById` was listed under both MR 1 and bug #1, and was held until MR 5 because it had a live caller -- dangerous code, not dead code. It shipped with MR 5 and is gone.

---


# Wave 1 — Deletions

Zero behavior change. The build is the proof.

## MR 1a — Dead code, compiler-verified (862 lines) — DONE ([PR #159](https://github.com/themancalledzac/edens.zac.backend/pull/159))

Everything checked off here was re-verified by caller-grep across main AND test before deletion.

### Corrections to the original review

The review counted callers in `src/main` only. Six items it listed as dead are live in the test
suite, so deleting them is test churn, not dead-code removal. They moved to MR 25, where the
`TestFixtures` work collapses the same call sites, rather than inflating a pure-deletion MR:

- `AuthPrincipal` 4-arg constructor — 1 main caller (`SessionService`) and 28 test call sites. Not
  dead. Its javadoc claimed "30 existing call sites", which was roughly right; the stale count was
  removed and the constructor kept.
- `CollectionRequests.Update` 17-arg — 0 main callers (`CollaboratorRequests.toUpdate` uses the
  22-arg canonical form), 23 test call sites.
- `DiskUploadRequest.FileEntry` 3-arg — 0 main callers, ~20 test call sites.
- `ContentService.resolveCollectionDownloadEntries` 2-arg — 0 main callers, 5 test call sites.
- `DownloadResolution.extension` — never read in main, but 4 construction and 6 assertion sites in
  test.
- `CollectionRepository.hasChildCollections` — the review did not mention that 10 test references
  existed. Deleted anyway, with the two integration tests re-pointed at the live derivation.

One item was found dead that the review missed: `CollectionRepository.findAllByOrderByCollectionDateDesc()`
(the no-arg overload) became unreachable once `CollectionService.getAllCollectionsOrderedByDate` went.

### Dead DAO methods (~400 lines)

- [x] `dao/EquipmentRepository.java` — 16 methods: `findCameraByName`, `findCamerasByNameContainingIgnoreCase`, `existsByCameraName`, `existsByCameraNameIgnoreCase`, `findCamerasOrderByUsageCount`, `findLensByName`, `findLensesByNameContainingIgnoreCase`, `existsByLensName`, `findLensesOrderByUsageCount`, `findFilmTypeByName`, `findFilmTypeByDisplayName`, `findFilmTypesBySearchTerm`, `findAllFilmTypesOrderByDefaultIso`, `existsByFilmTypeName`, `existsByFilmTypeDisplayName`, `findFilmTypesOrderByUsageCount` (lines 110-438). JPA query-method names mechanically ported to JDBC, superseded by the `*IgnoreCase` and `findAll*OrderBy*` variants. ~134 lines.
- [x] `dao/CollectionRepository.java` — 6 methods: `hasChildCollections` (256-281; the service derives this from `findAllReferencedCollectionIdsByParentId` now), `findContentByCollectionIdAndContentType` (869-886), `shiftContentOrderIndices` (948-964), `findContentByCollectionIdAndOrderIndex` (973-984), `updateContentOrderIndexForContent` (1027-1042), `deleteContentById` (1120-1125). ~95 lines.
  - [x] Fix `updateContentVisibleForContent`'s "mirror" javadoc, which cites a deleted method.
  - [x] Re-point the `hasChildCollections` tests at the live derivation.
- [x] `dao/TagRepository.java` — 8 methods: `findByTagName`, `findByTagNameContainingIgnoreCase`, `existsByTagName`, `addCollectionTag`, `removeCollectionTag`, `deleteContentTags`, `addContentTag`, `removeContentTag` (47-481). ~59 lines.
  - [x] Fix `saveCollectionTags`' javadoc, which still cites `addCollectionTag`.
- [x] `dao/PersonRepository.java` — 4 methods: `findByPersonName`, `findByPersonNameContainingIgnoreCase`, `existsByPersonName`, `findAllOrderByImageCountDesc` (41-114). ~45 lines.
- [x] `dao/PersonRepository.java` — `findAccountUserIdsByIds`. DEFERRED to MR 5. Its being dead may mean the only-accounts-get-grants rule lost its enforcement point; confirm before deleting (Appendix C).
- [x] `dao/PersonRepository.java` — `deleteById`. DEFERRED to MR 5. Not dead: `MetadataService.deletePerson` calls it. Deleting it is part of bug #1's fix.
- [x] `dao/CollectionRepository.java` — `findAllByOrderByCollectionDateDesc()` (no-arg overload). Not in the original review; became unreachable when `getAllCollectionsOrderedByDate` was deleted. The 2-arg overload stays.
- [x] `dao/ContentRepository.java` — 4 methods: `findByOriginalFilenames` (233-241), `findMostRecentImageIdByPersonId` (322-344), `deleteTextById` (972-980), `deleteCollectionContentById` (1196-1204). ~50 lines.
- [x] `dao/CollectionPeopleRepository.java` — `findPeopleForCollection` (32-44). The batch variant is the live one. ~13 lines.

### Ghost JPA leftovers on entities (~170 lines)

- [x] `entity/ContentCameraEntity.java`, `ContentLensEntity.java`, `ContentFilmTypeEntity.java`, `ContentPersonEntity.java` — `Set<ContentImageEntity> contentImages`, `Set<CollectionEntity> collections`, `getImageCount()`, `getCollectionCount()`, `getTotalUsageCount()`. No row mapper populates them, zero callers. Pre-JDBC-migration leftovers; ContentPersonEntity's are doubly stale since V35 dropped `content_people`. ~85 lines.
- [x] `entity/ContentEntity.java:36-44` — `setContentTypeFromSubclass()`, a JPA lifecycle idea with no lifecycle.
- [x] `types/FilmType.java` — the whole 73-line enum is dead. `ContentFilmTypeEntity`'s javadoc names itself as the replacement. Delete the file.
- [x] `model/FilmTypeDTO.java` — zero references (`ContentFilmTypeModel` is live). Delete the file.
- [x] `model/Records.java:60-64` — `CollectionSummary`, zero references.
- [x] `types/ContentType.java:11-21` — `contentName` field duplicates `name()`, zero callers. Also delete the commented-out `CODE("Code")` at line 14.

### Dead service code (~130 lines)

- [x] `services/CollectionService.java:805-814` — `getAllCollectionsOrderedByDate()`. No callers; the like-named endpoint calls `getAllCollections`.
- [x] `services/CollectionService.java:492-505` — `findById(Long)`. Test-only.
- [x] `services/validator/ContentImageUpdateValidator.java:48-56` — `validateFilmFormatRequired`. Zero callers.
- [x] `services/MetadataService.java:420-430` — `findOrCreateLocation`, `findLocationById`. Zero callers.
- [x] `services/ContentService.java:825-829` — `resolveCollectionDownloadEntries` 2-arg overload. MOVED TO MR 25 (5 test call sites). *(Tracked live on the tracker under "Carried forward" / MR 25; this archived copy is not a second open item.)*
- [x] `services/ContentService.java:165-168, 171-173, 241-243` — three unreachable guard branches in `updateImages`. MOVED TO MR 1b: proving unreachability is a control-flow judgment, not a mechanical deletion.
- [x] `services/ContentService.java:587, 604, 625` — `updateGif`'s `setTags`/`setPeople`/`setLocations` dead writes. MOVED TO MR 1b.
- [x] `services/ContentService.java:98-104` — `createTag`/`createPerson` pass-throughs. Have `AdminController` call `MetadataService` directly.
- [x] `services/ContentMutationUtil.java:112-119, 177-183` — back-compat entity-typed overloads. MOVED TO MR 1b (requires changing two call sites, not just deleting).
- [x] `types/CollectionVisibility.java:37-39` — `requiresLocalEnv()`. Only its own test calls it.
- [x] `services/ImageProcessingService.java:143-149` — `prepareImageForUpload` single-arg overload. Zero callers.
- [x] `model/DownloadResolution.java:13-14` — `extension` component written, never read; docblock also stale. MOVED TO MR 25 (10 test sites). *(Tracked live on the tracker under "Carried forward" / MR 25.)*
- [x] `services/VideoVariantPlanner.java:27-46` — `VideoVariantPlan`'s target-side fields are always the constants. MOVED TO MR 1b (a record reshape, not a deletion).

### Dead constructors and config (~120 lines)

- [x] `model/CollectionRequests.java:303-305` — the 2-arg `GalleryAccessRequest` constructor. Zero callers anywhere; every site passes the propagation flag.
- [x] `model/AuthPrincipal.java` — removed the stale "30 existing call sites" claim from the 4-arg constructor's javadoc. The constructor itself stays (see corrections above).
- [x] `model/CollectionRequests.java:119-160` — the 17-arg `Update` constructor. MOVED TO MR 25. *(Tracked live on the tracker under "Carried forward" / MR 25.)*
- [x] `model/DiskUploadRequest.java:44-46` — the 3-arg `FileEntry` constructor. MOVED TO MR 25. *(Tracked live on the tracker under "Carried forward" / MR 25.)*
- [x] `config/WebConfigProd.java` — `addCorsMappings` registers nothing. The class's only runtime effect is a log line. Delete the file.
- [x] `config/ProdSecretGuard.java:15-19` — `@RequiredArgsConstructor` with no final fields generates nothing. Delete the annotation and move the `@Value` to a constructor parameter.
- [x] `config/ImageIoConfig.java:15-42` — `registerPlugins` registers nothing, it only logs SPI discovery. Rename to say what it does; keep the diagnostics.
- [x] V19's `admin_home_tile.cover_image_id` column: written by nothing, read by nothing (`AdminHomeService` resolves covers by strategy). DEFERRED — a schema change does not belong in a pure-deletion MR. Drop it in a migration or document it as reserved. *(Tracked live on the tracker under "Decisions needed from the user".)*

## MR 1b — Dead code, behavior-adjacent (~60 lines) — DONE

Split out of MR 1a because each item needs a correctness judgment rather than a caller-grep.
All four claims were verified against the control flow across `src/main` AND `src/test`, and all
four held. Build green: 1365 tests, 0 failures, 0 checkstyle violations.

- [x] `services/ContentService.updateImages` — all three guard branches confirmed unreachable, deleted.
  - `imageId == null`: `ContentImageUpdateValidator.validate` runs over every update in a loop
    *before* the per-item loop and throws on a null id. Nothing reaches the guard.
  - `image == null`: every non-null id is in `imageIds`, and the pre-loop containment check throws
    `ResourceNotFoundException` for any id missing from `imageMap`. `Collectors.toMap` rejects null
    values, so a present key cannot map to null. Unreachable *because* of the first guard's
    invariant, so the two had to be judged together rather than independently.
  - `catch (ClassCastException)`: `findImagesByIds` joins `content_image`, and
    `CONTENT_IMAGE_ROW_MAPPER` hardcodes `.contentType(ContentType.IMAGE)` rather than reading the
    column. So `convertRegularContentEntityToModel` always takes the `case IMAGE` arm, whose return
    type is `ContentModels.Image`, and the outer cast cannot fail. No other statement in the try
    block casts. A hypothetical CCE now lands in the general `catch (Exception)` arm, which records
    the same per-item error.
  - The two invariants the guards stood for are now recorded in the `updateImages` docblock. Without
    that, the absence of a null check reads as an oversight.
- [x] `services/ContentService.updateGif` — `setTags`/`setPeople`/`setLocations` confirmed dead, deleted.
  `saveGif`'s UPDATE branch writes `content_gif` scalars only. The response is rebuilt by
  `convertEntityToModel`, which reloads the row through `findGifById` into a *new* instance, and
  `convertGifToModel` re-queries tags/people/locations itself. The `gif` instance the setters wrote
  to is discarded. The merge results are still used to persist the join rows -- only the setter
  calls went.
- [x] `services/ContentMutationUtil` — both entity-typed overloads deleted, call sites pass ids.
  The review predicted two call sites; there were two in main plus seven in
  `ContentMutationUtilTest`. Unlike the MR 25 constructors, this test rewrite was a strict
  improvement (`image` to `1L`) and let nine now-pointless `ContentImageEntity.builder()` lines go
  with it, so it did not need to wait on `TestFixtures`. `RuleBMixedContentIntegrationTest` already
  used the id form.
- [x] `services/VideoVariantPlanner` — `VideoVariantPlan` shrunk to the two booleans.
  Both construction sites passed the constants: `compute` and the probe-failure fallback in
  `ImageProcessingService`. The two read sites now reference `FULL_MAX_LONGEST_SIDE` /
  `WEB_MAX_LONGEST_SIDE` directly. `VideoVariantPlannerTest`'s two target-side assertions became
  tautologies and went.

Test-coupled constructor removals that were also split out of MR 1a moved to MR 25, not here --
see "Main-dead, test-live constructors" there. They rewrite the same call sites that MR 25's
`TestFixtures` builders collapse, so doing them separately is wasted work.

## MR 2 — Build and config rot (~180 lines) — DONE

Build green: 1365 tests, 0 failures, 0 checkstyle violations. Test count is unchanged from MR 1b.

- [x] `pom.xml` — `spring-boot-starter-thymeleaf`: zero references, no templates directory. Deleted.
- [x] SpotBugs: deleted all four artifacts (user decision). The plugin block in `pom.xml`,
  `spotbugs-exclude.xml`, the Dockerfile COPY, and the commented-out CI step.
  - A fifth artifact the review did not count also went: the CI "Upload SpotBugs results" step,
    which uploaded `target/spotbugsXml.xml` — a file nothing produces. Its `if: always()` kept it
    from failing the job, so it had been silently no-opping.
  - Re-enabling was rejected as out of scope for a config-rot MR: it surfaces an unknown pile of
    findings needing triage. The inherited exclude filter was also very broad — `<Class name="~.*\$.*"/>`
    excluded every nested class, which in this codebase means every record in `Records.java`,
    `CollectionRequests.java`, and `ContentModels.java`. Any future reintroduction should write a
    filter from scratch rather than restore that one.
- [x] H2 fossil in test resources. Confirmed inert: no H2 dependency and no JPA starter in `pom.xml`,
  so every `spring.jpa.*` and H2 property was dead.
  - The review's "delete both halves" was wrong about the second half. `application-test.properties`
    is mostly live config — Flyway settings and the AWS, email, and WebAuthn stubs the context needs
    to start. Only four lines existed to neutralize H2. Those four went; the rest stayed.
  - The base `src/test/resources/application.properties` likewise carries live settings
    (`app.access-token.secret`, the two contact rate limits). Only the H2/JPA block went.
  - Three stale comments referencing "the H2 test props" were rewritten.
- [x] A fifth H2 fossil the review missed: `src/test/java/.../ApplicationTests.java`. `@Disabled`
  since the PostgreSQL migration, body is an empty `contextLoads()`, and it carried its own H2
  `@TestPropertySource`. It never ran at all — surefire includes `**/*Test.java` and the file is
  `*Tests.java`, so it was not even being collected as a skip. Deleted.
- [x] `pom.xml` — both spring-milestones repository blocks. Everything resolves from Central at GA
  versions; build is green without them.
- [x] `application.properties` — `spring.codec.max-in-memory-size`, a WebFlux property in a servlet
  app. Deleted as rot. Whether to add a real body cap stays open in MR 11.
- [x] `application.properties` lines 1, 11-13 — the `:-` defaults. Deferred to MR 9 and fixed there
  as bug #9. The paired decision about shipping a default DB password at all is still open; it is
  recorded in the tracker under "Decisions needed from the user" (one home as of 2026-08-29).
- [x] `testRequests/` — deleted the directory. All three files were stale Postman fixtures with zero
  references anywhere in the repo. `updateCollection.json` carried `visible`/`priority`/`coverImageUrl`
  (now `visibility`/`rating`/`coverImageId`); `createCollection.json` also carried `type`, dead since
  the typeless migration.
- [x] `.idea/` — untracked with `git rm --cached`, files preserved on disk so the IDE keeps working.
  The review named only the `.iml`; eight files were tracked. `.gitignore` already lists `.idea` and
  `*.iml` — the commits predate those rules.
  - Not done: `.claude/settings.local.json` still allowlists `Bash(mvn spotbugs:check:*)`. It is a
    local permission entry, not build config, and harmless.

Checkstyle config is healthy (all 49 suppressions are commented with reasons) and the CI pipeline is sound. `grep TODO|FIXME|XXX|HACK` across `src/main`: zero hits.

## MR 3 — Dead tests, wholesale (1,510 lines) — DONE

Build green: 1298 tests, 0 failures, 0 checkstyle violations. 67 tests removed (1365 -> 1298). The estimate of ~1,800 lines counted the baseline before MR 1a-2 trimmed imports; the seven files are 1,510 lines.

- [x] `model/CollectionBaseModelTest.java` (504 lines) — response-DTO "validation" that never runs in prod (`CollectionModel` is never a `@RequestBody`), Lombok round-trips, plus visible rot: `PriorityValidationTests` loops 1..4 but never sets the field, so four assertions are empty.
- [x] `model/CollectionModelTest.java` (541 lines) — same category, including toString substring assertions.
- [x] `entity/ContentEntityTest.java` (65), `ContentImageEntityTest.java` (139), `ContentGifEntityTest.java` (137) — builder/Lombok templates. `ContentEntityTest` opens with `// TODO: Verify tomorrow if this is a valid test file` (it is not) and contains `testTimestampsAreNotTestedDirectly`.
- [x] `services/ImageMetadataExtractionTest.java` (93) — a `@Disabled` diagnostic dump that never runs in CI.
- [x] `model/RecordsTest.java` (31) — asserts that record accessors hold constructor args.

## MR 4 — Test trims (1,057 lines net) — DONE

Build green: 1249 tests, 0 failures, 0 checkstyle violations. 49 tests removed (1298 -> 1249).

- [x] `model/CollectionUpdateRequestTest.java`: 1,004 -> 301 lines. Kept the four Jackson
  wire-contract guards, the four validation tests, and the two `contentPerPage` bound checks.
  `CollectionRequests.Update` is `@RequestBody @Valid` in `AdminController:117`, so unlike
  `CollectionModel` its constraints do execute in prod and the validation tests are worth keeping.
  Deleted the builder, display-mode, cover-image, tag, person, and collection blocks — all
  positional-constructor round-trips. Also inlined the fully-qualified `JavaTimeModule` as an import.
- [x] `entity/ContentTextEntityTest.java`: deleted whole (133 lines). The condition on
  `testFormatTypeValues` failed — it builds three entities and asserts `getFormatType()` returns the
  string it just set. `formatType` is a plain `String` field with a Lombok getter, so there is no
  real behavior there.
- [x] `entity/CollectionEntityTest.java`: 240 -> 41 lines, keeping only `testGetTotalPages`, which
  covers the entity's one hand-written method including its null and zero handling.
  Also deleted `testPasswordProtectionStateIsDerivedFromGalleryPassword`, which the review did not
  call out: despite the name it only asserts `setGalleryPassword`/`getGalleryPassword` round-trips.
  The derivation it claims to cover lives in `CollectionProcessingUtil`, not the entity.
- [x] `types/ContentTypeTest.java`: 59 -> 38 lines, keeping the two `forValue` blocks. Dropped
  `enum_ShouldHaveCorrectValues` (declaration order) and `getValue_ShouldReturnEnumName`
  (`getValue()` is `return this.name()`). `forValue_WithInvalidValue_ShouldReturnText` still pins the
  current lenient behavior and carries a comment pointing at bug #13, which will rewrite it.
- [x] Renamed the three `*DevTest` classes. All three inject `AdminController`, so they now follow
  the package's existing `AdminController<Area>Test` pattern:
  `CacheControllerDevTest` -> `AdminControllerCacheTest`,
  `AdminHomeControllerDevTest` -> `AdminControllerHomeTilesTest`,
  `CollectionControllerDevTest` -> `AdminControllerCollectionsTest`.
  `ai_docs/ai_cicd.md:274` referenced the old name plus two files that no longer exist
  (`ContentControllerDevTest`, `ContentProcessingUtilTest`); corrected.

---

# Wave 2 — Bugs

Ordered by severity. All are small diffs.

## MR 5 — Security bugs — DONE

Build green: 1303 tests, 0 failures, 0 checkstyle violations (1298 -> 1303; the X-Forwarded-For
test was replaced 1:1, and five tests were added).

- [x] Bug #1 (high). Admin "delete person" can delete a real user account. `dao/PersonRepository.java:190-195` (`deleteById`) runs `DELETE FROM users WHERE id = :id` with no status guard, and `MetadataService.deletePerson` (`services/MetadataService.java:175`) calls it unconditionally. Since V35 merged people into `users`, passing an account user's id to the admin delete-person endpoint destroys the account — sessions, passkeys, invites, saves, follows, and share links all cascade. The safe primitive already exists: `deletePersonById` (PersonRepository:265-271) guards with `AND status = 'PERSON'`. Point `deletePerson` at it, 404 on 0 rows, and delete the unguarded `deleteById`.
  - Done as described. `deletePersonById` now returns the row count. `deletePerson` keeps its
    existing `findById` 404 check -- that check cannot tell a person tag from an account, since
    `findById` selects from `users` with no status filter, which is precisely why the guard is
    needed. The association deletes run first, so the throw on 0 rows rolls them back with the
    transaction (`ResourceNotFoundException extends RuntimeException`).
  - Three regression tests added to `MetadataServiceTest`: the guarded-delete happy path, the
    account id that must 404, and the unknown id that must not touch associations.
- [x] Bug #3 (high). `RateLimitFilter` still trusts client-supplied X-Forwarded-For as a fallback key. `config/RateLimitFilter.java:128-138` reads X-Real-IP first (correct), then falls back to the first hop of client-supplied XFF (133-136). Whenever X-Real-IP is absent, an attacker sends a fresh fake XFF per request and the per-IP 500/hour limit on `/api/public/messages` stops existing, while churning the 10k Caffeine cache to evict legitimate buckets. `CollectionControllerProd.java:207-224` documents and implements the correct policy. Delete the XFF branch.
  - `RateLimitFilterTest.xForwardedForFirstHopIsUsedAsIp` asserted the vulnerable behavior. It is
    replaced by `spoofedXForwardedForCannotMintFreshBuckets`, which sends three requests from one
    `remoteAddr` with a different spoofed X-Forwarded-For each time and requires the third to 429.
    Under the old code each spoofed value drew its own bucket and none of them ever 429'd.
- [x] Extract `ClientIp.resolve(HttpServletRequest)` into `config/` while fixing bug #3 (consolidation #1, pulled forward because bug #3 motivates it). Replaces four private copies with two contradictory trust policies: `RateLimitFilter:128-138`, `AuthController:127-133`, `WebAuthnController:210-216`, `CollectionControllerProd:217-224`. Implement the `CollectionControllerProd` policy; carry over its javadoc, the only one explaining the trust model.
  - Done. All four private copies are gone. Only `RateLimitFilter` actually had the XFF branch; the
    other three already implemented the correct policy, so this was a true consolidation for them
    and the bug fix for the filter.
- [x] Bug #7 (medium). `app.admin.enforce-authz=false` in prod silently opens `/api/admin` and `/api/edit`. `config/SecurityConfig.java:65-76` falls through to `permitAll`, `EditAccessWebConfig.java:38-40` skips the interceptor, and `ProdSecretGuard.java:21-27` does not check the flag. One wrong env var proxies unauthenticated visitors into the whole write surface. Extend `ProdSecretGuard` to refuse prod + enforce-authz=false.
  - Done. The guard is already `@Profile("prod")`, so the new check costs nothing outside prod. Two
    tests added, one of which asserts the failure message names the toggle rather than the secret --
    the two checks must stay independent.
- [x] Delete `PersonRepository.findAccountUserIdsByIds` once the only-accounts-get-grants rule is confirmed enforced elsewhere (carried from MR 1). NOT DONE -- the precondition is false. *(CLOSED: MR 15 #6 / [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191) deleted the method and put the guard on `RoleRepository.addMember`; its bypass became S-2, closed by #193.)*
  - The rule is not enforced anywhere. Both `RoleRepository.addMember` call sites
    (`AdminRoleController:152`, `AdminUserController:335`) pass a path-variable user id straight
    through, and `addMember` inserts into `role_member` with no status filter. `findAccountUserIdsByIds`
    is the only primitive that expresses the rule and it has zero callers.
  - Not exploitable today: a `status='PERSON'` row has no password hash and no WebAuthn handle, so
    nobody can authenticate as it and the stray `role_member` row grants nothing to anyone. It is a
    data-integrity gap, not an access-control hole -- which is why this was left alone rather than
    given a new validation rule inside a bug-fix MR.
  - Decision needed: either enforce the rule at the two `addMember` call sites (making the method
    live) or delete the method and drop the rule. Deleting it while the rule is unenforced would
    remove the only tool for a gap nobody is tracking. Carried to MR 10.

## MR 6 — Upload pipeline bugs — DONE

Build green: 1262 tests, 0 failures, 0 checkstyle violations (net +8: 17 tests added, 9 removed
with `BooleanExtractor`). Each new test was run against the unfixed code first and fails there.

- [x] Bug #2 (high). The upload OOM guard covers only the multipart path; disk and ingest jobs run unbounded. `services/ImageUploadPipelineService.java:74-79, 144, 162, 197-204`. `uploadSemaphore` (permits=1) is acquired only in `createImagesParallel`. `processFilesFromDisk` and `ingestFilesGroupedByDay` each submit a background loop to an unbounded virtual-thread executor with no semaphore and no cap on concurrent jobs; each loop does full `ImageIO.read` decodes (~130-180 MB heap per 45MP JPEG). This is the same shape as the historical 20+15 concurrent-Lightroom-request OOM — that fix only landed on the multipart endpoint. A disk job can also stack with a semaphore-holding multipart upload. Gate the per-file prepare step in both loops on the same semaphore.
  - Done. Both loops now call `prepareFromDiskGuarded`, which holds the permit across the decode
    and S3 phase only and releases before the DB work, so concurrent jobs interleave instead of one
    job holding the permit for its whole run. `createImagesParallel` still holds it for its whole
    batch -- that path decodes `PARALLEL_BATCH_SIZE` files at once and has to bound the batch.
  - The two acquire sites are now one `acquireUploadPermit()` helper, throwing
    `IllegalStateException` rather than the bare `RuntimeException` the multipart path used (same
    concern as the `validateAndEnsureUniqueSlug` item in MR 9).
  - `processFilesFromDisk_concurrentJobs_serializeThePrepareStep` counts peak concurrent entries
    into the mocked prepare step across two overlapping jobs and requires 1. Without the permit it
    observes 2.
- [x] Bug #10 (medium). Upload dedupe SKIP is unreachable across export sessions. `services/ImageProcessingService.java:229-230, 308, 359-369` — `lastExportDate` is set to `now()` at prepare time (the comment claims file-mtime), so a re-export is always "newer" and always takes the UPDATE branch. The skip feature only fires for duplicates within one batch. Also, on SKIP the image is never linked to the target collection. Use the file's mtime (disk path) or a plugin-sent export date (multipart), and link on SKIP.
  - Split outcome. The from-disk path is fixed; the multipart path is not, and cannot be here.
  - Fixed: `prepareImageFromDisk` now takes `lastExportDate` from the exported JPEG's mtime
    (`exportDateFromFile`), so a re-export is newer and a re-send is not. Falls back to `now()`
    when mtime is unreadable, which keeps the image eligible for update rather than skipping a
    real re-export.
  - Fixed: SKIP now links the existing image to the target collection in both loops, via a shared
    `linkIfNotLinked`. SKIP still does no keyword rewrite and no RAW re-upload -- the stored image
    is unchanged, only its collection membership is added.
  - NOT fixed: the multipart path still stamps `now()`. A `MultipartFile` carries no mtime and the
    Lightroom plugin sends no export date, so there is nothing to read. Fixing it needs a wire
    change in the plugin repo; adding an unused optional request field here would be dead until
    that ships. The misleading comment claiming mtime is replaced with the real reason. Carried:
    add an export-date field to the multipart upload contract when the plugin can send one.
  - `exportDateFromFile` is package-private for tests. The surrounding prepare step converts to
    WebP through `libwebp-imageio`, whose native binary is x86_64-only and will not load on an
    arm64 Mac, so `prepareImageFromDisk` cannot run end-to-end in this test suite.
- [x] Bug #11 (medium). Job cleanup expires running jobs. `services/JobTrackingService.java:127-141` removes any job older than 1 hour with no status check, so a long ingest job vanishes from polling mid-flight. Skip PENDING and PROCESSING.
  - Done. Cleanup now removes only COMPLETED and FAILED jobs. A job stuck in PROCESSING is never
    reclaimed, which is the deliberate trade: a leaked `JobStatus` is a UUID and five counters,
    while dropping a live job makes it 404 on the next poll and the client sees it vanish rather
    than finish.
  - `cleanupExpiredJobs` delegates to a package-private `removeFinishedJobsStartedBefore(cutoff)`
    so tests supply a cutoff instead of aging a job by an hour.
- [x] Bug #12 (medium). `blackAndWhite`/`isFilm` XMP extraction very likely never fires. `services/ImageMetadata.java:120-131` configures both as `xmp:subject` — keywords live in `dc:subject`, and `subject` is an array property that `getProperty` will not flatten. Meanwhile `FILTERED_KEYWORDS` (`ImageMetadataExtractor:44`) strips "monochrome"/"blackandwhite"/"film" from tags because they are "already handled", so the signal is dropped entirely. Extract the flags from the parsed keyword list before filtering. Verify against one real Lightroom export.
  - Done. `recordKeywordFlags` sets both flags off the parsed keyword list before
    `FILTERED_KEYWORDS` strips those keywords from the tags, in both the hierarchical and the flat
    `dc:subject` branch. `FILTERED_KEYWORDS` is now derived from the two flag keyword sets, so a
    keyword cannot set a flag and also survive as a tag.
  - Matching is exact against the keyword sets, not the old substring test. "Film Noir" stays a
    tag; only "film" becomes the flag. The substring version would have set the flag while leaving
    the keyword in the tag list.
  - The dead `BLACK_AND_WHITE` / `IS_FILM` entries are gone from the `ImageMetadata` field table
    along with `BooleanExtractor`, which had no other user. Nine tests pinning that never-fired
    behavior went with it.
  - Verified against synthetic XMP, not a real Lightroom export: `ImageMetadataExtractorKeywordFlagTest`
    builds real JPEGs and splices an XMP APP1 segment after SOI, covering both the hierarchical and
    flat keyword layouts. Confirming against one real export is still worth doing.
  - Flagged for the MR 15-19 consolidation wave: `ImageMetadata.ExifTags.none()` now has no caller
    in main (only `ImageMetadataTest`). Left in place as the symmetric counterpart of the live
    `XmpProperty.none()`.

## MR 7 — Validation and wire contracts — DONE

Build green: 1272 tests, 0 failures, 0 checkstyle violations (+6). Every new test was run against
the unfixed code first; five of the six fail there, and the sixth is called out below.

One finding in this MR was wrong as written. See bug #4.

- [x] Bug #4 (high). Admin image-patch endpoint never validates list elements. `controller/admin/AdminController.java:236` uses `@RequestBody @Valid List<ContentImageUpdateRequest>` — `@Valid` on a List validates the list object, never its elements. The repo's own `GlobalExceptionHandler` documents this trap (`config/GlobalExceptionHandler.java:93-99`), and the collaborator route already uses the working form (`List<CollaboratorRequests.@Valid CollaboratorImageUpdate>`, `EditController.java:99`). Also `ContentImageUpdateRequest.rating` has no `@Min`/`@Max` while the collaborator DTO constrains 0-5. Fix both.
  - PARTLY WRONG AS FILED. The premise -- "`@Valid` on a List validates the list object, never its
    elements" -- does not hold on Spring Boot 3.5.16. Spring 6.1's `HandlerMethodValidator`
    cascades into the elements for either placement. Probed directly with a two-endpoint controller
    under standalone MockMvc: `@Valid List<T>` and `List<@Valid T>` both returned 400 with
    `HandlerMethodValidationException` for an element violating `@NotNull`. The `@NotNull` on
    `ContentImageUpdateRequest.id` was already enforced on this route.
  - The `GlobalExceptionHandler` docblock this finding was derived from asserted the old rule. It
    is corrected in this MR, so the next reader does not inherit the same false premise.
  - Genuinely fixed: `ContentImageUpdateRequest.rating` had no bound while the collaborator DTO
    constrained 0-5 and `content_image` carries `CHECK (rating >= 0 AND rating <= 5)`. Now
    `@Min(0) @Max(5)`, and the javadoc that said "(1-5)" says 0-5.
  - The parameter still moved to `List<@Valid ContentImageUpdateRequest>`. That is a readability
    and consistency change matching `EditController.patchImages`, not a behavior fix.
  - `updateImages_elementMissingId_is400` passes against the unfixed code. It is kept as coverage
    and its comment says so.
- [x] Bug #13 (medium). `ContentType.forValue` silently coerces bad values to TEXT. `types/ContentType.java:28-41` is the Jackson `@JsonCreator` for every request carrying a contentType, so a typo'd discriminator becomes a valid TEXT block instead of a 400. Throw, like `CollectionVisibility.forValue` does. `ContentTypeTest.forValue_WithInvalidValue_ShouldReturnText` pins the current wrong behavior — update it with the fix.
  - Done. `forValue` throws `IllegalArgumentException` naming the value and the valid options, and
    tolerates lowercase input the way `CollectionVisibility.forValue` does -- the serialized form
    is uppercase, so case variance is a client quirk while an unknown name is a real error.
    `GlobalExceptionHandler` maps `IllegalArgumentException` to 400.
  - `ContentTypeTest.forValue_WithInvalidValue_ShouldReturnText` pinned the coercion and is
    replaced by a throwing test, a case-insensitivity test, and one asserting the message names the
    valid options. The now-unused `@Slf4j` came off the enum.
- [x] Bug #14 (medium). `TextFormType` wire value and stored value disagree. `types/TextFormType.java:37-40` serializes lowercase ("markdown"); `services/ContentService.java:462` stores `name()` (uppercase "MARKDOWN"); `entity/ContentTextEntity.java:25-29` documents lowercase. Store `getValue()` and normalize existing rows if any are uppercase.
  - Done. `createTextContent` stores `getValue()` ("markdown") and defaults to "plain" instead of
    "PLAIN". Nothing in Java compares the column -- every read path hands it straight to the
    client -- so the only symptom was two different spellings on the wire depending on which path
    created the block.
  - `V57__lowercase_text_format_type.sql` lowercases existing `content_text.format_type` rows.
    Idempotent; rows already lowercase are untouched. Table and column verified against
    `ContentRepository`'s INSERT before writing the migration.
- [x] Bug #15 (medium). `UserRatingOverrideService` throws `java.lang.SecurityException`, which `GlobalExceptionHandler` does not map. `services/UserRatingOverrideService.java:36-39` — the controller compensates with the repo's only try-catch (`UserRatingOverrideControllerProd.java:46-52`). Throw `AccessDeniedException` (mapped to 403, and what sibling `UserSelectsService:78` uses) and delete the try-catch.
  - Done. `UserRatingOverrideService.upsert` throws `AccessDeniedException` (mapped to 403 by
    `GlobalExceptionHandler`, and what sibling `UserSelectsService` uses) instead of
    `java.lang.SecurityException`, which nothing mapped. The repo's only controller try-catch is
    gone, along with the `@Slf4j` that existed only to log inside it.
- [x] Bug #16 (medium). Selects add endpoint accepts a fully-null body. `controller/prod/UserSelectsControllerProd.java:29, 34` — no `@NotNull`, no `@Valid`, so `{}` reaches `userSelectsService.add(userId, null, null)`. Both sibling controllers validate. Match them.
  - Done. `AddSelectRequest` constrains both fields `@NotNull` and the parameter takes `@Valid`,
    matching `UserFollowsControllerProd`. `{}` now 400s instead of reaching
    `userSelectsService.add(userId, null, null)`.

## MR 8 — Data correctness

- [x] Bug #5 (high). `updateCollectionPeople` rebuilds the exact id-only-entity pattern the tags docblock documents as data-corrupting. `services/CollectionService.java:972-994` fabricates `ContentPersonEntity` instances carrying only an id; `equals`/`hashCode` key on `personName`, so a RETAINED person can survive twice in the set with the same id. The 15-line docblock on the adjacent `updateCollectionTags` (926-940) describes this exact mechanism as the historical tag bug that rolled back whole saves. It currently avoids the PK collision only because the DAO happens to `.distinct()` — any DAO change re-arms it. Load full entities like the tag path does, and add `.distinct()`.

  **Done.** `updateCollectionPeople` now loads full entities via the new
  `CollectionPeopleRepository.findPeopleForCollection`, mirroring `TagRepository.findCollectionTags`
  on the tag side, and `.distinct()` was added to the id mapping. This orphaned
  `CollectionRepository.findCollectionPersonIds` (the broken line was its only caller), so it was
  deleted. Three regression tests in `CollectionServiceTest.UpdateCollectionPeople`; all three fail
  on the pre-fix code.

  `CollectionPeopleRepository.setPeopleForCollection`'s `.distinct()` was left in place per
  instruction. What removing it would change, given the fix: nothing observable today. The service
  now hands it a de-duplicated list, and its other two callers
  (`CollectionService.setCollectionPeople` from the admin manage page, and
  `regeneratePeopleFromContents`, whose ids come from a `SELECT DISTINCT`) cannot produce repeats
  either. Before the fix it was load-bearing: it was the only reason the duplicate id never hit the
  `collection_people` primary key, which is what made this bug #5 (silent) rather than the
  rollback-the-whole-save failure the identical tag defect caused. Keeping it costs one stream op
  and keeps the DAO safe against a future caller that passes a raw list.
- [x] Bug #6 (medium). `getLocationPage` builds a wrong orphan-exclusion list when `collectionPage > 0`. `services/CollectionService.java:226-231` — the shortcut condition ignores `collectionPage`; on page 1 with few collections, `allCollectionIds` is `[]` and the orphan queries either return wrong "orphans" or 500 on an empty `NOT IN`. Public endpoint, client-supplied params. Fix: `collectionPage == 0 && totalCollections <= collectionSize`.

  **Done** (shipped separately from bug #5, as its own MR). Applied the prescribed condition.
  One correction to the finding above: it does not 500. Both
  `ContentRepository.findOrphanImagesByLocationName` and `countOrphanImagesByLocationName` guard
  `excludeCollectionIds != null && !isEmpty()` and just omit their `NOT EXISTS` clause on an empty
  list, so the failure is silent rather than loud -- on any `collectionPage > 0` where
  `totalCollections <= collectionSize`, the endpoint returned every image at the location as an
  "orphan", including images in the collections listed on the same response. Regression test:
  `CollectionServiceTest.GetLocationPage.getLocationPage_pastFirstPage_stillExcludesCollectionImages`.

## MR 9 — Config bugs and low-priority fixes

Scope note. The heading undersells what is in here: 14 items, of which only bug #8 and bug #9 are
config. The other 12 are unrelated low-priority fixes spread across controllers, image processing,
S3 streaming, and two DAOs. Ship them as two MRs -- bugs #8 and #9 first, the remaining 12 after --
rather than one 14-item MR nobody can review.

The split happened. Bugs #8 and #9 shipped as MR 9a
([#172](https://github.com/themancalledzac/edens.zac.backend/pull/172)); the remaining 12 items
shipped as MR 9b ([#173](https://github.com/themancalledzac/edens.zac.backend/pull/173), 22 files,
+707/-145). **MR 9 is complete, and with it Wave 2.**

Bug #9 is the only item here carrying a decision that is not an implementer's to make. The `:-`
placeholder fix is mechanical, but it arrives bundled with the question of whether to keep shipping
a default DB password, and that is a judgment call for the repo owner. MR 9a fixed the separators
and deliberately left that question open -- see the decision item below.

- [x] Bug #8 (medium). Tomcat maxSwallowSize integer overflow. `config/TomcatConfig.java:18` — `2 * 1024 * 1024 * 1024` overflows to negative, which Tomcat treats as unlimited swallow. Use `Integer.MAX_VALUE` or a deliberate constant.
  Confirmed by a failing test before the fix: the connector came up with
  `maxSwallowSize=-2147483648`. Every place Tomcat reads the setting guards it with
  `maxSwallowSize > -1` (`Http11Processor.checkMaxSwallowSize`, `IdentityInputFilter`,
  `ChunkedInputFilter`, `BufferedInputFilter`), so the overflow did not raise the 2GB cap, it
  removed the cap entirely and left Tomcat willing to swallow an unbounded aborted body. Now
  `Integer.MAX_VALUE`, with both connector values named as constants and the stale `// 2GB` comment
  gone. Regression test: `TomcatConfigTest`, which applies the customizer to a real connector and
  asserts the value stays positive.
- [x] Bug #9 (medium). Bash-style `:-` defaults in `application.properties` lines 1, 11-13 — `${POSTGRES_HOST:-localhost}` resolves to the literal `-localhost` when unset, because Spring's separator is `:`. Six placeholders. Also reconsider shipping a default DB password at all. Deferred here from MR 2, which
  deleted the rot in this file but left this one alone because it is a behavior change: today an
  unset `POSTGRES_HOST` silently yields the literal `-localhost`, and fixing the separator makes it
  actually fall back to `localhost`. Anything currently depending on the broken value changes
  behavior. The paired DB-password decision is flagged in the scope note above.

  All six fixed to Spring's `:` separator. Confirmed by failing tests first: with no environment set,
  `spring.datasource.url` resolved to `jdbc:postgresql://-localhost:-5432/-edens_zac`,
  `spring.datasource.username` to `-zedens`, and `spring.profiles.active` to `-default`. Regression
  test: `ApplicationPropertiesPlaceholderTest`, which also scans both shipped property files for the
  `${VAR:-` spelling. It reads `src/main/resources` directly, because
  `src/test/resources/application.properties` shadows the shipped file on the test classpath and a
  `ClassPathResource` lookup would pass vacuously. `application-dev.properties` was already clean.

  Blast radius turned out smaller than the deferral note assumed: in every deployed path these six
  placeholders are shadowed and never consulted. `docker-compose.yml` injects
  `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`
  unconditionally, and sets `SPRING_PROFILES_ACTIVE` with `:?` so an unset value aborts the compose
  run outright; CI sets the same four. The defaults are reachable only when Spring runs directly
  (`mvn spring-boot:run`, IDE, bare `java -jar`) without those variables, and nothing could have
  depended on the old values there -- `//-localhost:-5432` is not a connectable URL. The `:-`
  spelling elsewhere in the repo (`docker-compose.yml`, `deploy.sh`, `scripts/*.sh`) is correct and
  was left alone; bash is what interprets those.
- The still-open decision -- whether to ship a default DB password at all -- moved to the
  tracker's "Decisions needed from the user" on 2026-08-29, options included. One home instead of
  three.
- [x] `downloadCollection` bypasses the response machinery with raw `sendError`/`setStatus` (`ContentDownloadControllerProd.java:94-158`). Convert to `ResponseEntity<Void>` like its sibling `downloadImage`.
  Now `ResponseEntity<Void>`, each exit copied from `downloadImage`: 401 via
  `ResponseEntity.status(UNAUTHORIZED)`, 404 via `notFound()`, and the redirect via
  `status(FOUND).location(url)`. The method keeps `throws IOException` -- `zipToS3AndPresign`
  declares it, and GlobalExceptionHandler's catch-all maps it to a 500. The four in-body comment
  blocks were promoted into the method docblock. No new test: `ContentDownloadControllerProdTest`
  and `ContentDownloadAuthTest` already assert 401, 404 and 302-with-Location here, and all of
  those still pass unchanged.
- [x] Admin message delete returns 204 for a nonexistent id (`MessagesControllerAdmin.java:49-54`). Return 404 on 0 rows, like `deleteRole`.
  Copied `AdminRoleController.deleteRole` verbatim. The endpoint had no test at all before; the new
  `DeleteMessage` tests cover both 204 and 404, and adding them exposed that the test class was
  handing the controller a null `MessageService`.
- [x] Text-content creation maps a service null to 400 instead of 500 (`AdminController.java:224-226`).
  Now a bare `RuntimeException`, which is the only route to a 500 here: GlobalExceptionHandler maps
  `IllegalArgumentException` and `IllegalStateException` both to 400, and only the catch-all
  `Exception` handler produces 500. Note this moves in the opposite direction to the
  `validateAndEnsureUniqueSlug` item below, on purpose -- a duplicate slug is the caller's fault
  (400), a service returning null is not (500). Worth recording: the null branch is unreachable
  today. `ContentService.createTextContent` either throws or returns a non-null model, so this
  corrects the status a defensive guard would report rather than a 400 any caller has seen.
- [x] `convertToWebP` leaks the ImageWriter on exception (`ImageProcessingService.java:990-994`). Dispose in a finally block.
  `writer.dispose()` was the last statement in the try-with-resources body, so a throw from
  `writer.write` skipped it. Now in a `finally`. Checked the rest of the method as well: the
  `ImageOutputStream` was already in try-with-resources and there is no `ImageReader` here, so this
  was a single-resource fix. Not meaningfully testable -- the method is private and the failure
  path needs `writer.write` to throw, which means injecting a broken ImageIO SPI.
- [x] `S3MultipartOutputStream.close()` after `abort()` tries to complete a dead upload (`S3MultipartOutputStream.java:109-133`). Early-return when aborted.
  The class already tracked `aborted`; `close()` only checked `closed`. Added the early return.
  The one live caller (`DownloadUrlService.zipToS3AndPresign`) was not hitting this, because
  `ZipOutputStream.close()` had not yet delegated at that point. The bug is real for any caller
  doing abort-then-close directly or through try-with-resources, which the `OutputStream` contract
  invites. Regression test: `S3MultipartOutputStreamTest.close_afterAbort_doesNotCompleteUpload`.
- [x] Full-image decode just to read dimensions (`ImageMetadataExtractor.java:318-340`). Use `ImageReader.getWidth(0)`/`getHeight(0)` header reads; the pipeline decodes the same file again right after.
  Both fallbacks (`ensureDimensions` for `MultipartFile`, `ensureDimensionsFromPath` for `Path`)
  now share one `putDimensionsFromHeader` that opens an `ImageInputStream` and reads
  `getWidth(0)`/`getHeight(0)`. Stream in try-with-resources, reader disposed in a finally. Both
  `ensureDimensions` methods went package-private so tests can drive them, matching
  `ImageProcessingService.recordRenditionDimensions`. New `ImageMetadataExtractorTest` asserts the
  header read returns exactly what `ImageIO.read` returned for the same file.
- [x] `EditController.patchImages` runs a two-phase write with no spanning transaction (`EditController.java:96-126`). Push it into one transactional `CollectionService` method. The `updates == null` check there is dead.
  New `CollectionService.applyCollaboratorImageEdits` holds the guard, the canonical writes and the
  scoped visibility writes in one `@Transactional` method, so the batch is all-or-nothing. The
  controller is now a delegate. The dead `updates == null` check is gone: `@RequestBody` is
  required by default, so Spring rejects an absent or null payload before the handler runs. The
  `updates.isEmpty()` check stays, since an empty array does reach the method. Removing the
  two-phase write also left `EditController` with no use for `ContentService`, so that field and
  its import went too. Tests moved with the logic: `EditControllerTest` now asserts delegation, and
  a new `ApplyCollaboratorImageEdits` block in `CollectionServiceTest` covers the canonical/visible
  split, the guard running before any write, a failed visibility write propagating, and that the
  method still carries `@Transactional`.
- [x] `parseBooleanOrDefault` docblock and logic disagree (`ImageMetadataExtractor.java:458-463`).
  The logic was correct and the docblock was wrong, so the docblock was rewritten. It claimed a
  default "if parsing fails", but `Boolean.parseBoolean` has no failure case -- an unrecognized
  non-blank value returns false, not the default, and only null or blank returns the default. That
  is what both callers want: they read a flag whose only written value is the literal "true" and
  both pass false as the default. One small behavior change came with it -- the value is now
  trimmed before matching, so `" true "` returns true where it used to return false.
- [x] Locale-less `toLowerCase()` on emails in `AuthController.java:61` and `WebAuthnController.java:143`, while the limiters use `Locale.ROOT` for the same key.
  Both now use `Locale.ROOT`, matching `AuthLoginLimiter.key()` and `ContactMessageLimiter`. Before
  this, a Turkish default locale would have lowercased the controller's email differently from the
  limiter key for the same address. Tests set the default locale to tr-TR and assert both the
  lookup and the limiter see the same lowercased address.
- [x] `validateAndEnsureUniqueSlug` throws a bare `RuntimeException`, producing a 500 (`CollectionProcessingUtil:813`). `IllegalStateException` matches the codebase.
  Now `IllegalStateException`, which GlobalExceptionHandler maps to 400. The bare `RuntimeException`
  had no handler and fell through to the catch-all 500 with the generic "An unexpected error
  occurred" body. Regression test in `CollectionProcessingUtilTest`.
- [x] `updateCameraFilmMetadata` is the only DAO mutation without `@Transactional` (`EquipmentRepository:213-224`).
  Added, matching `saveCamera`/`saveLens`/`saveFilmType`. Confirmed it was the only mutation in the
  class missing it. Not unit-testable here -- the annotation is only observable through a Spring
  context with a real transaction manager, and these repository tests are plain Mockito.
- [x] `updateRating` returns an always-true boolean (`CollectionService.java:673-680`). Make it void.

---

## 2026-08-31 second close-out — bugs #17, #19, #20 and passkey deregistration

Four MRs, all merged the same day: [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255) (bug #20),
[#256](https://github.com/themancalledzac/edens.zac.backend/pull/256) (bug #17),
[#257](https://github.com/themancalledzac/edens.zac.backend/pull/257) (passkey deregistration),
[#258](https://github.com/themancalledzac/edens.zac.backend/pull/258) (bug #19).

### Estimate versus actual

| Item | Board estimate | Actual | Read |
|---|---|---|---|
| Bug #20 | "~10 lines in one file, plus the test" | +61/-16, 2 files | Right, once the test is counted. The board named the test; the estimate did not price it. |
| Bug #17 | not sized | +6/-14, 1 file | Net negative. The fix was a log string. |
| Passkey | "largest COLD piece of real feature work" | +260/-11, 5 files | Right, and the only item this run whose size matched its adjective. |
| Bug #19 | "widen the orphan queries" | +249/-55, 5 files | **Under-scoped by the board.** "Widen the queries" hid a response-type change and a cross-repo break. |

**The failure mode to carry forward: an item that describes a fix at the query layer has not
priced the DTO the query feeds.** Bug #19's text stopped at the SQL. The SQL was the smallest part
-- the orphan query returns `ContentImageEntity`, `LocationPageResponse.images` was typed
`List<ContentModels.Image>`, and the frontend narrows to `ContentImageModel[]`. Any remaining item
that says "widen"/"generalize" a query should be re-read for the same gap. **MR 19 #19**
(`ImageSearchResponse` -> `PagedResponse`) is the closest sibling and already names its wire
consequence, so it is priced correctly; nothing else on the board currently has this shape.

### Where the board's premises held, and where they did not

- **Bug #20's premise held exactly.** `shutdown()` awaited `rawUploadExecutor` alone; verified by read and by the mutation below.
- **Bug #17's premise held**, and the item was right to leave the fix open. The decision needed evidence the item did not carry: the loop's *other* per-image writes.
- **Bug #19's premise held on the SQL and missed the response type entirely** (above).
- **The passkey item's "check whether removing the last credential leaves it able to authenticate" was the right question** and had a non-obvious answer -- see the decision below.

### Decisions, with reasoning

**Bug #17 -- correct the log line, do not build a `batchUpdate`.** Not because it was smaller.
`updateImages` already issues per-image statements inside its loop via
`contentMutationUtil.updateImageTagsOptimized` -> `tagRepository.saveContentTags(image.getId(), ...)`
and `updateImagePeopleOptimized` -> `contentRepository.saveContentPeople(image.getId(), ...)`, plus
`collectionRepository.removeContentFromCollection` in a nested loop. Batching only the `saveImage`
calls removes one O(N) term from a method with several and pays for it with a second persistence
path for images beside `saveImage`. If image writes are ever worth batching, the case has to cover
the tag and people writes too -- that is a different item with a different size.

**Passkey -- removing an account's last credential is ALLOWED.** Refusing it would block the one
case the endpoint exists for: a single registered authenticator, and that authenticator
compromised. A guard there leaves "disable the whole account" as the only remedy, which is the gap
being closed. The consequence is reported rather than left to be discovered: the DELETE returns
`{remainingPasskeys, passwordLoginAvailable}`, and `remaining == 0 && !passwordLoginAvailable` also
logs at WARN. Recovery is `POST /api/admin/users/{id}/invite`.

**Bug #20 -- do not unify the two executors** (the guardrail's requested cost report).
`imageProcessingExecutor` serves one submit site inside a request (`createImagesParallel`, via
`CompletableFuture.supplyAsync`); `rawUploadExecutor` serves three that outlive the HTTP response
(`processFilesFromDisk`, `ingestFilesGroupedByDay`, the RAW upload). Unifying costs: (1) one
shutdown wait covering both, its budget set by the slower background work, so request-path work
queues behind it; (2) the loss of being able to `shutdownNow()` one class of work while the other
drains -- which is exactly what the fix relies on. It buys nothing in return:
`newVirtualThreadPerTaskExecutor` is unbounded, so there is no pool contention to reclaim. The
misnaming (`rawUploadExecutor` runs whole disk and ingest jobs) is a **rename, independent of
unification**, and is the cheap half if anyone wants it.

### Scope deliberately left out

- **`JdbcUserCredentialRepository.delete(Bytes)` is still a no-op** and its docblock still says so deliberately. Spring Security's built-in WebAuthn management filter is **not** registered -- `SecurityConfig` matches only this app's `/api/auth/webauthn/**` controller -- so nothing calls it and there is no hidden user-facing passkey delete. Making it real would add a second delete path with no caller, and the user chose against a self-service surface. **Do not re-investigate.**
- **No user-facing passkey list-and-remove**, per the 2026-08-30 decision. The admin `GET .../passkeys` exists only because the DELETE takes a credential id an admin has no other way to learn.
- **`ContentRepository.saveImage`'s INSERT/UPDATE branch untouched**, per bug #17's guardrail. Recorded for whoever revisits it: every entity in `imagesToSave` comes from `findImagesByIds`, so its id is non-null and only the UPDATE branch is reachable from there. A future batch would not need the INSERT half.
- **`ImageUploadPipelineServiceTest`'s 105 inline comments left in place.** Sweeping them would have put a 105-line comment diff on top of a 10-line bug fix; the board's own guidance is to take rule-37 debt per package. It is the largest single-file rule-37 concentration found so far and is a candidate for the first test-side sweep.

### Traps

- **A unit test cannot prove a deregistered passkey stops working.** `WebAuthnServiceTest` mocks `WebAuthnRelyingPartyOperations`, so `finishLogin` never reaches a credential lookup and any assertion there passes against a live credential. The real chokepoint is `finishLogin` -> `operations.authenticate` -> `JdbcUserCredentialRepository.findByCredentialId(Bytes)` -> the row. That is what the integration test drives.
- **Every fix here was mutation-proved before shipping, and each failed at its own guard** (working rules 15, 32, 41; surefire reports deleted first). Bug #20's test reddened at `Expecting AtomicBoolean(false) to have value: true`. The passkey test reddened at `expected: null but was: ImmutableCredentialRecord` with the delete predicate neutered to `AND 1 = 0`. Bug #19 reddened 3 of 4 with the predicate put back to `content_type = 'IMAGE'`.
- **Bug #17 shipped with NO test, deliberately.** It changes a log string; a test asserting "saveImage is called once per image" passes against `main` unchanged and could not fail (working rule 15). Recorded here so nobody reads the absence as an oversight and no one credits it with coverage it does not have.
- **Working rule 39 fired again, and the check caught it.** #255 and #256 had squash-merged within minutes of being opened, before the third item started. Checking `gh pr list --state merged` before branching item 4 is what kept it off a stale `main`.

### What held -- do not re-investigate

- `JdbcUserCredentialRepository.delete()` being a no-op is deliberate and unreachable (above).
- The rule-12 protected files (`RoleRepository` 10, `AdminBootstrap` 6, `CollectionControllerProd` 9) re-run twice on 2026-08-31 and unchanged both times.
- The trailing-`//` count is 74 by the recorded portable command, re-run and confirmed.
- `MR 19 #17`'s `CollectionService.getCollectionWithPagination` pagination refs (`143-145`) re-verified by anchor text on 2026-08-31 after #258 edited that file -- still correct, not drifted.

## Session log

**This is the older half of the archive** (entries from 2026-08-22). The newer half is
[Session log archive — entries moved 2026-08-31](#session-log-archive--entries-moved-2026-08-31),
5,300 lines below. Both are linked from the tracker; note added 2026-08-31 (third run), when the
tracker's single "the archive" link was found to resolve only to this half.

- 2026-08-22 — shipped MR 5-8 and bug #6 (#165, #166, #168, #169, #170). Wave 1 already complete.
- 2026-08-22 — recorded MR 9's real scope and split it in two (#171).
- 2026-08-22 — shipped MR 9a, bugs #8 and #9 (#172). Decided: keep the default DB password
  *(bug #9's scope call -- keep the existing default while fixing the separator; whether to ship
  any default at all stayed open and lives in the tracker's "Decisions needed")*. Corrected
  the doc's premise that bug #9 was a risky behavior change. Next: MR 9b.
- 2026-08-22 — shipped MR 9b, the remaining 12 items (#173). **Wave 2 complete.** Corrected MR 1a's
  stale IN REVIEW heading and MR 11's already-deleted codec item. Added the Working rules section.
  Next: MR 10.
- 2026-08-23 — merged the doc-only scope update (#174), then opened both Wave 3 MRs. They landed
  out of order: MR 11 merged first (#176), MR 10 second (#175). No harm done -- the two branches
  were cut from the same base and touch disjoint code, and MR 11 was deliberately kept off the
  Progress table and session log so the pair could merge in either order without a conflict. That
  precaution is the only reason the inversion cost nothing; the next multi-MR wave should keep it.
  **Wave 3 complete.**
- 2026-08-23 — MR 10 (#175): decided to keep gallery passwords in plaintext, with the cost of moving
  off it written up in the MR 10 section. Found that the email and propagation flows use the
  submitted password rather than the stored one, so they were never part of the plaintext
  requirement -- the tracker had implied a wider dependency than exists.
- 2026-08-23 — MR 11 (#176): split `IllegalStateException` into client-actionable 400s and bare
  `RuntimeException` 500s rather than genericising every message. Reverted one reclassification when
  its test failed: MR 9b had deliberately made `validateAndEnsureUniqueSlug` a 400 a commit earlier.
  Recorded the convention in the `handleIllegalState` javadoc.
- 2026-08-23 — reconciled the board before handing off Wave 4. Found MR 12's line lists 64% stale
  (only 55 of 152 sampled refs still land on a comment) and the Wave 4 premise low by 53% (567
  in-method comments measured, not ~370). Rewrote MR 12 as a mechanical re-derivation with a
  three-way split, added Working rule 5, filed the Wave 3 chunked-body residual. MR 10 (#175) was
  still open at the time of writing; this doc update ships inside it, so the "Wave 3 complete" row
  becomes true exactly when it merges. Next: MR 12a, `CollectionService` only.
- 2026-08-23 — shipped MR 12a (#177) and MR 12b (#178). **Filed bug #16**: `updateImages` comments
  and logs a "batch save" that is a per-image loop, on a method otherwise meticulous about N+1 on
  every read path. Corrected the doc's per-file counts three times running (144 not 148, 58 not 61,
  51 not 58, and 12c is 68 not 82) -- the note's totals include class-member-level comments the Wave
  4 header excludes. Measured MR 13 at 154, where the over-count pattern inverts, and split it 13a/13b
  in the doc before anyone starts it. Added Working rules 6 and 7. Next: MR 12c.
- 2026-08-23 — shipped MR 12c (#180), completing MR 12. 68 comments across twelve files, promoted
  into 11 javadocs, diff comment-only with zero blank-line churn. **No bugs found** -- every
  checkable claim held, which Working rule 7 says to report as the outcome it is. Corrected the
  note's "12 identical CDN comments" to 11 (it listed 11). Left
  `CollectionAccessService.effectiveLevel` alone per the 12c guardrail and reported instead:
  `canView`/`isClient` take a raw `Long userId`, never touch `effectiveLevel`, and are safe only
  because a flyby principal's null userId cannot match `rm.user_id = :userId` -- an accident nothing
  asserts. Filed as a Wave 3 follow-up. Next: MR 13a.
- 2026-08-23 — shipped MR 13a (#181). 117 comments across the two media-pipeline services, into 14
  javadocs. **The sizing note's 117 was exact** -- the first Wave 4 estimate needing no correction,
  breaking a four-in-a-row over-count streak. Found a stale docblock by a comment/comment
  contradiction rather than a comment/code one: `saveProcessedImages` said "in a single transaction"
  while the PHASE 2 comment four methods above it (and the code) said per-image transactions. The
  comment being deleted was the accurate one, which is how it surfaced -- a sweep is unusually good
  at this because it puts both halves in view at once. Also found two `P:` instructions already
  stale in the OTHER direction ("fix wrongness with bug #10" -- bug #10 is fixed and the comment is
  now correct; and the RAW-scheduling staleness note -- already accurate), so Working rule 5's decay
  applies to the `P:` judgment notes too, not just the `D:` line numbers. Next: MR 13b.
- 2026-08-24 — reconciled the board after #180 and #181 merged. Followed up on the `git add -A`
  sweep and found the swept file is cited by Appendix B, from before the sweep -- so it belongs
  tracked and the accident fixed a real fragility. Rule 9 amended to say so, because as first
  written it invited a future session to delete a doc the tracker cites. Java diffs were +69/-70 (12c) and
  +131/-131 (13a). Re-derived MR 13b at **37, exactly the estimate** -- second exact call running,
  so the Wave 4 over-count pattern is done; stop discounting the remaining numbers. Measured 13b's
  line refs as the most decayed in the doc: `ImageMetadataExtractor` **1 of 24 (4%)**, worse than
  `ContentService`'s 8%, and `ImageMetadata` 0 of 5. Replaced them with a re-derived worklist.
  Corrected consolidation #17's refs, which were stale in the same file (`ensureDimensions` is 364
  not 318-340; the repeated date parse is 428/467, not 350-371 which is `recordKeywordFlags`).
  Added Working rule 8 (`P:` notes decay like `D:` refs) and Working rule 9 (never `git add -A`;
  12c's commit swept an untracked 321-line review doc into #180). Next: MR 13b.
- 2026-08-23 — shipped MR 13b (#183), which completes MR 13 and leaves 93 in-method comments in
  `src/main`, all MR 14's. 37 comments into 13 javadocs, no bugs found. The re-derived 37 was exact,
  making three exact Wave 4 calls in a row -- the over-count era is over and the doc's remaining
  numbers should be read straight. Two comments the worklist filed as redundant narration turned out
  load-bearing: the XMP first-wins rule across multiple directories, and the EXIF-over-XMP precedence
  contract. That is the inverse of MR 13a's lesson -- 13a found `P:` notes telling it to fix things
  already fixed, 13b found `D:` notes telling it to delete things worth keeping. **Working rule 8
  generalizes: a `D:`/`P:` classification decays like a line number does, in both directions. Re-read
  the comment before trusting either verdict.**
  Costed the date-parsing fold the guardrail deferred and found the guardrail's own premise wrong:
  only `parseExifDateToLocalDateTime` detects format, `parseImageDate` sidesteps detection with a
  permissive split, so the two share knowledge and not code. Probed the built class -- they diverge
  only on malformed input, where `parseImageDate` returns month **13** and builds an S3 path from it.
  Folding is therefore a behavior change with an S3-pathing consequence, not a refactor; re-scoped
  that half of consolidation #17 accordingly. Next: MR 14.
- 2026-08-23 — shipped MR 13c after #183 merged, correcting 13b rather than carrying the defect into
  MR 14. Prompted by the right question: the PRs kept growing while being called "debloat". Splitting
  13b's `+161/-53` showed 88 lines of it was this tracker and only +20 was Java -- but measuring the
  Java **in words** rather than lines showed +178 (+42%), because javadoc's `/**`, ` * ` and `<p>`
  overhead makes real prose growth read as a near-neutral line count. **Line count is the wrong
  metric for a comment MR.** Three causes, now Working rule 10: the EXIF-over-XMP precedence rule
  written into three separate docblocks (the same three-copies failure MR 12a caught with the CDN
  invalidation comment); a dense 3-line docblock deleted and re-expanded to 11 saying the same thing;
  and a fact promoted into a private method that its public caller already documented. 13c cut -24
  lines / -192 words with nothing lost, leaving 13b+13c at -4 lines / -14 words with all 37 comments
  gone. Next: MR 14, which has 93 comments and would have repeated this at three times the scale.
- 2026-08-23 — reconciled after #183 and #184 both merged, and closed out MR 13. Ran the word-count
  retro across all six Wave 4 MRs: **-975 words total**, with 13b the only inflation, so the trend was
  sound and the defect was localized. 13a is the instructive one -- **+2 lines, -75 words** -- proving
  line count is the wrong instrument rather than just that 13b was careless. Re-derived MR 14 at **93
  comments across 20 files**, no split needed (12c shipped 82 as one). Measured its refs at **68%,
  the best in Wave 4**, which inverts the discount working rule 5 taught: **decay tracks edit churn,
  not doc age** -- Waves 1-3 and MR 12/13 all landed on `services/`, leaving `controller/`, `config/`
  and `dao/` refs pristine. Distribution is bimodal (eleven files 100%, six 0%), so the average lies;
  per-file table is in the MR 14 section. Found `AdminController` now has zero in-method comments, so
  its item is done by attrition. Verified both of MR 14's checkable claims (the `password_hash`
  docblock and the CLAUDE.md `@Profile` line) still hold. Flagged `ContentService:227` as MR 14's
  guardrail -- it is bug #16's only evidence and reads exactly like the narration MR 14 deletes.
  Next: MR 14.

---

# Wave 3 — Security hardening

## Posture summary

Strong for a solo-operated backend, and clearly designed rather than accreted. CloudFront in front, a BFF-only shared-secret perimeter in prod (constant-time compared, rotation-aware, startup-guarded against the dev default), role-gated security chain, a fail-closed per-collection collaborator interceptor, and strictly self-scoped user endpoints. Token and session hygiene is genuinely good: 256-bit SecureRandom everywhere, SHA-256 at rest for sessions/invites/share links, constant-time compares, bcrypt with a dummy-hash timing equalizer, single-use WebAuthn challenges, HttpOnly/Secure/SameSite=Strict cookies with sliding 60-day and absolute 90-day windows. The flyby share principal is carefully contained (no authorities, excluded by `hasRole("USER")`, GENERAL ceiling pinned in code, share-before-admin ordering) — no escape was found.

The weaknesses cluster in two places: the client-IP trust seam (bug #3 plus four duplicated resolvers, both in MR 5) and the gallery-password subsystem (MR 10). The rest is config rot.

Verified clean, for the record: no SQL injection anywhere in scope (named parameters throughout, no string concatenation into queries); no hardcoded secrets beyond the dev default that `ProdSecretGuard` refuses in prod; `app.access-token.secret` fails to start without a value; limiter maps are all Caffeine-bounded with TTLs; filter ordering and double-registration guards are correct; the collaborator gate covers every `/api/edit/**` mapping; no IDOR on the user-scoped surface.

On the standing X-Forwarded-For question: mostly fixed, one remnant. Every consumer now keys off X-Real-IP first (`RateLimitFilter:129-132`, `AuthController:128-131`, `WebAuthnController:211-214`, `CollectionControllerProd:218-221`), matching the agreed BFF fix. The remnant is bug #3. Delete it and the April finding is closed on the backend side. Whether the BFF strips inbound client-supplied X-Real-IP cannot be proven from this repo — see Appendix C.

## MR 10 — Gallery password subsystem — DONE ([PR #175](https://github.com/themancalledzac/edens.zac.backend/pull/175))

It followed MR 9a's shape: a mechanical fix bundled with a decision that is not an implementer's to
make. The decision is recorded below with the numbers behind it rather than settled silently.

Why this one next: Wave 3 has only two MRs, and MR 10 is the one with a real design question in it.
MR 11 is mostly small and one of its four items already turned out half-done. Also, the tracker's
own posture summary names the gallery-password subsystem as one of only two weak clusters in an
otherwise strong security posture, and the other one (the client-IP trust seam) already closed in
MR 5.

**Guardrail: do not hash the stored gallery password.** This is the obvious-looking fix and it is
wrong here. `passwordFingerprint` HMACs the plaintext password so that two galleries sharing a
password produce the same fingerprint, which is what lets one unlock cookie open a whole password
group without storing any group identifier. bcrypt is salted, so hashing the stored value destroys
that property and takes the shared-unlock feature with it. Leave the plaintext storage alone and
report what changing it would cost — if the fingerprint feature is worth less than the plaintext
risk, that is the owner's call to make with the numbers in front of them, not a change to slip into
a cleanup MR.

Verified 2026-08-22, line refs below still resolve: the plaintext compare is
`ClientGalleryAuthService.java:68`, `generateAccessToken` is at 77, `validateAccessToken` at 92,
`passwordFingerprint` at 133, `generatePasswordAccessToken` at 147, `validatePasswordAccessToken`
at 164.

Note on the second item: the file already uses `MessageDigest.isEqual` for both HMAC comparisons
(lines 117 and 182). Only the password equality at line 68 is a plain `.equals()`, so that item is a
one-line change, not a sweep.

- [x] Decide and record: gallery passwords are stored and compared in plaintext (`services/ClientGalleryAuthService.java:59-68` and flows). **Decision: keep plaintext.** Recorded in the class javadoc on `ClientGalleryAuthService` and costed out below.
- [x] At minimum, switch the compare to `MessageDigest.isEqual` for consistency with the rest of the codebase. One-line change at the former line 68; behavior identical, timing no longer leaks the matching prefix length.
- [x] Changing a gallery password does not revoke issued per-slug cookies. Fixed: the signed payload is now `slug|fingerprint|expiry` via a private `slugTokenPayload` helper shared by `generateAccessToken` and `validateAccessToken`, so the two cannot drift. `generateAccessToken` took a second `password` parameter; its only main caller is `buildAccessCookies`, which already had the value in hand.

### Why the fingerprint and not a version counter

The tracker offered both. The fingerprint needs no schema change and is already computed on every
read path; a version counter needs a new column, a migration, and an increment wired into all four
password write sites. Same guarantee, less surface.

### Deploy note

Binding the fingerprint changes the signed payload, so every per-slug gallery cookie already in a
visitor's browser stops validating at deploy. Visitors inside an active 24h window re-enter the
password once. The password-fingerprint cookie is unaffected -- it always bound the fingerprint by
construction, which is why only the per-slug token was broken.

### The plaintext decision, costed

Two live consumers read the *stored* password and would break under a one-way hash:

1. `passwordFingerprint(entity.getGalleryPassword())` -- the shared-unlock cookie group, reached
   from `CollectionService:545` and `ContentDownloadControllerProd:196` via
   `GalleryAccessCookies.hasValidAccess`.
2. `CollectionModel.galleryPassword` (`CollectionService:890`) -- the admin manage page shows the
   current password so the owner can tell a client what it is.

Two that look like they need plaintext but do not. Both use `request.password()`, the value the
admin just submitted, never a re-read of storage:

- `sendGalleryPasswordEmail` (`CollectionService:1727`).
- `propagatePasswordToChildrenIfRequested` (`CollectionService:1767`).

So emailing the password to a client and propagating it to children both survive a hash. Only the
shared unlock and the admin display do not.

**bcrypt alone** costs the shared-unlock feature outright. bcrypt is salted, so two galleries with
the same password no longer produce a common key, and a PARENT password stops opening its
propagated children without a prompt per gallery. The admin display goes too.

**The one design that keeps both** is storing bcrypt for verification *and* persisting the HMAC
fingerprint as its own column, so the fingerprint is derivable from storage without plaintext. What
that costs:

- One Flyway migration adding `gallery_password_fingerprint`, backfilled by HMAC-ing the existing
  plaintext column -- feasible precisely because it is plaintext today -- then clearing the
  plaintext in the same migration.
- **Secret rotation becomes destructive.** The fingerprint is keyed by `app.access-token.secret`.
  Today rotating that secret costs one round of invalidated cookies, because fingerprints are
  recomputed from plaintext on every read. With the plaintext gone, stored fingerprints computed
  under the old secret can never be recomputed, so rotation permanently breaks shared unlock for
  existing galleries unless the old secret is retained forever. This is the non-obvious cost and
  the main reason to hesitate.
- The admin page loses "show me the password" and becomes "set a new one", which changes how the
  owner hands passwords to clients.
- Touches: 1 migration, `ClientGalleryAuthService`, `CollectionRepository` (row mapper, insert,
  `updateGalleryPassword`, `saveGalleryAccess`), `CollectionService:890`, `CollectionModel`, plus
  the frontend manage page. Roughly a day plus a migration against live data.

**Recommendation: not worth it at current exposure.** These passwords gate photo galleries only,
the gallery owner picks them rather than the client, they carry no account privileges, and the
database is not internet-reachable. The usual argument for hashing -- users reuse passwords across
services -- does not apply while the owner is the one choosing them. Revisit if gallery passwords
ever become client-chosen, or if the database's exposure changes.

## MR 11 — Public surface hardening — DONE ([PR #176](https://github.com/themancalledzac/edens.zac.backend/pull/176))

- [x] ~~`spring.codec.max-in-memory-size=64KB` (`application.properties:72-73`) is a WebFlux property in a servlet app.~~ **The deletion already happened in MR 2** ([#161](https://github.com/themancalledzac/edens.zac.backend/pull/161)); verified absent from `application.properties` on 2026-08-22. **Decision: the cap is worth adding, and it is in.** Folded into `RateLimitFilter` rather than a second filter, since that filter already owns the `/api/public/**` predicate. See "Why the cap earned its place" below.
- [x] `IllegalStateException` handler echoes internal messages to clients as 400s. Split "bad request" from "broken invariant" rather than genericising the message, which would have thrown away genuinely useful client errors. See "How the split was drawn" below.
- [x] Contact-message table has no global growth cap. Added a global daily bucket to `ContactMessageLimiter` (`app.contact.rate-limit-global-per-day`, default 1000), checked before the per-email bucket. It is the only one of the three limits whose key an attacker cannot choose.
- [x] Share and invite raw tokens travel as URL path segments and will sit in access logs (`ShareControllerProd.java:55-66`, `InviteController.java:51-52, 75-78`). Re-confirmed 2026-08-23: accepted design for shareability, keep log retention short. No code change, as the item anticipated.

### Why the cap earned its place

Bean Validation already bounds the contact payload to 5320 characters, so the cap looked redundant.
It is not: `@Valid` runs after Jackson has materialised the whole body, so the 20MB string default
is reachable on every request, and `RateLimitFilter` allows 500/h per IP. That is 10GB through the
parser per hour per address, on a box that has already been OOM'd once by concurrent large
requests. 16KB clears the real payload by 3x.

The check runs after the rate-limit bucket is consumed, so oversized requests still count against
the sender's hourly budget instead of being rejected for free.

Known gap, deliberate: the cap reads `Content-Length`, so chunked requests arrive without one and
still reach Jackson, bounded only by the container's own post limit. Rejecting chunked outright
would be the complete fix but risks breaking a legitimate proxy, and the tracker asked for the
Content-Length filter specifically. Filed as the one open Wave 3 residual:

- [x] **Wave 3 residual — chunked bodies bypass the public body cap.** `RateLimitFilter` reads *(CLOSED: promoted to S-5 and shipped as #206, 2026-08-24. See the S-5 ledger line on the tracker.)*
  `getContentLengthLong()`, which is -1 for `Transfer-Encoding: chunked`, so a chunked request
  reaches Jackson uncapped. Options: reject chunked on `/api/public/**` outright (complete, small
  risk of breaking a proxy that chunks), or wrap the input stream in a counting guard (complete, no
  client-visible behavior change, more code). Verify first whether anything in front -- CloudFront
  or the BFF -- already normalizes chunked to a fixed length, which would close this for free.
  Decide before adding code.

### How the split was drawn

`IllegalStateException` keeps its 400 and keeps echoing the thrower's message, because most
throwers are genuinely client-actionable and their messages are worth reading: "a collection
already owns slug 'x'", "cannot merge an identity into itself", "no in-flight login challenge".
Genericising all of them to protect a handful of leaky ones would have been a net loss.

What moved to a bare `RuntimeException` (500, generic body, stack trace logged) is the set that
were server faults wearing a 400:

- `WebAuthnService` -- "Authenticated handle has no app_user: <handle>", "Principal has no app_user:
  <userId>". These leaked internal identifiers to unauthenticated callers and are the sharp end of
  this item.
- `JdbcUserCredentialRepository` -- three throwers leaking app_user ids and WebAuthn handles.
- `TokenUtil`, `ImageProcessingService` -- "SHA-256 unavailable". A JVM-level impossibility, never
  the caller's fault.
- `ImageUploadPipelineService` -- "Upload interrupted while waiting for semaphore".
- `CollectionProcessingUtil` -- "Failed to find or create location with name: X". A data-layer
  failure, not bad input.
- `ContentService.castContentModel` -- a type mismatch is a programming error.

**Not changed, deliberately:** `validateAndEnsureUniqueSlug`'s "Could not generate a unique slug
after 100 attempts". MR 9b moved this one from `RuntimeException` to `IllegalStateException` on
purpose, one commit before this MR. Its test caught the reversal. Re-deciding a call that shipped
last week is not this MR's business; if 500 is the right status there it should be argued on its
own.

The convention is now recorded in the `handleIllegalState` javadoc so the next throw site picks the
right type without rediscovering this.

Two things noticed while in here and deliberately not fixed. `MessagesControllerPublic:43` returns
`ResponseEntity<?>` against the typed-return rule -- already tracked in MR 22, which also has the
better fix (a `RateLimitedException` handled globally, unifying the three different 429 body shapes
in play). Not duplicated here. And `JdbcUserCredentialRepository` lives in `config/`, not `dao/` as
this doc claimed; the path is corrected above.

---

---

# Wave 4 — MR 12 and MR 13 (complete)

## MR 12 — Comment debloat: core services

### Prep note (2026-08-23) — read before touching the lists below

The line lists in this section are from the `8c28cf3` baseline and eleven MRs have landed on those
files since. Measured drift: of 152 sampled `D:` refs, **only 55 still land on a `//` comment --
36%**. Per file: `ContentService` 8%, `CollectionService` 15%, `MetadataService` 55%,
`CollectionProcessingUtil` 81%, and `ContentModelConverter` / `PaginationUtil` / `TagViewResolver`
100%. Following these numbers would delete live code.

Keep the lists as the record of intent -- they still say which files matter, roughly how dense each
one is, and which specific comments were judged worth promoting rather than deleting. That judgment
is the part worth preserving. Do not use them as coordinates.

Re-derive the worklist instead:

```bash
grep -rn --include="*.java" -E "^\s{4,}//" src/main/java/edens/zac/portfolio/backend/services/
```

Current inline counts for this MR's files (indent >= 4, banners excluded): CollectionService 148,
ContentService 61, CollectionProcessingUtil 58, MetadataService 16, ContentModelConverter 13,
SyntheticCollectionResolver 14, UserPageAssembler 10, TagViewResolver 8, ContentMutationUtil 6,
PaginationUtil 4, TagService 3, UserMergeService 3, CollectionAccessService 3, CollectionFlags 1,
ContentImageUpdateValidator 1. Total 349.

At 349 comments across 15 files this is too big for one MR. Split it: `CollectionService` alone
(148) is one MR, `ContentService` + `CollectionProcessingUtil` (119) a second, the remaining twelve
small files (82) a third. That matches how MR 9 had to be split once its real size was known.

The `P:` (promote) entries are the ones to recover by hand from the lists below, since they encode a
judgment a grep cannot reproduce -- find each by reading the named docblock, not by line number.

### Guardrail for MR 12: the diff contains comment lines and nothing else

The tempting wrong move here is not deleting too many comments. It is touching the code underneath
them. Deleting a comment puts your eyes on a badly named variable, a method that wants extracting,
or a branch the comment was apologising for -- and a "while I'm here" fix feels free because the MR
is already zero-risk.

It is not free. A comment MR is the only kind whose correctness the compiler and the test suite can
fully confirm: if the diff is comments only, a green build **is** the proof. Add one non-comment
line and that guarantee is gone for the whole MR, across hundreds of hunks nobody will review
closely because they are "just comments". Wave 2 spent five MRs on bugs precisely because behavior
changes need their own scrutiny; smuggling one in here gets it none.

So: delete and promote comments only. No renames, no extractions, no simplifications, no reordering.
`git diff` on the MR should show only removed `//` lines and added javadoc.

The report half, which matters more than the prohibition: **some of these comments are describing
real bugs.** The review already caught one (`ImageProcessingService:229`, "fix wrongness with bug
#10" in MR 13). Expect more -- a comment that contradicts the code it sits above is a bug report
someone wrote and forgot. When you find one, file it as a numbered item in this doc and say so in
the PR. Do not fix it inline, and do not delete the comment either; leave it until its own MR lands,
because the stale comment is the only remaining evidence of the problem.

Same rule for the `// ====` section banners: out of scope per the Wave 4 header, leave them, except
the stale "TYPE-SPECIFIC PROCESSING" one at `CollectionProcessingUtil:927` which the header already
condemns.

- [x] `CollectionService.java` — **MR 12a, done.** All 144 in-method comments removed; the
      load-bearing ones promoted into 13 javadocs. See "MR 12a outcome" below. The original list is
      kept underneath as the record of intent, not as coordinates.
  - Original (stale, `8c28cf3`) — D: 127, 141, 146, 213, 233, 244, 290, 303, 306, 309, 317, 330, 336, 344, 347, 384, 393, 397, 496, 503, 567, 582, 585, 590, 595, 601, 606, 610, 651, 744, 765, 774, 777, 782, 809, 812, 820, 899, 907, 911, 917, 970, 989, 1011, 1044, 1047, 1062, 1074, 1082, 1112, 1137, 1140, 1334, 1399, 1411, 1446, 1456, 1473, 1485, 1520, 1553. P: 103/109/113 (resolution order, into the `getCollectionWithPagination` docblock), 135 (spec D1 invariant), 149, 152, 223 (keep with bug #6), 299/325/740 (CDN invalidation, three copies, into one class-level sentence), 357 (checkstyle-load-bearing final), 415, 574, 613, 620, 628/635, 828, 840, 865, 1016, 1028, 1056, 1100, 1107, 1121, 1164. Delete as redundant with the docblock: 124/280, 1344/1348, 1567 (stale), 1659.

### MR 12a outcome (2026-08-23) — shipped as [#177](https://github.com/themancalledzac/edens.zac.backend/pull/177)

Diff: 127 insertions, 152 deletions, one file (plus this doc). Every changed line is a comment line -- verified
mechanically, not by eye. `mvn clean install` green (1315 tests, 0 failures), `mvn checkstyle:check`
0 violations, `mvn spotless:apply` produced no reformatting.

Count correction to the prep note: `CollectionService` held **144** comments at indent >= 4, not 148.
148 is the count of all lines starting with `//`, which includes the four class-member-level lines
that the Wave 4 header puts out of scope. Worth applying the same correction when sizing 12b and 12c.

Every promoted `P:` entry survived re-derivation and landed in a javadoc, including the ones the
original list singled out: the resolution order and spec-D1 invariant (`getCollectionWithPagination`,
which had no docblock at all), the bug #6 first-page shortcut (`getLocationPage`, also undocumented),
the three CDN-invalidation copies collapsed into one class-level sentence, the checkstyle-load-bearing
`final` on `parentEntity`, and the `selfProvider` cache-proxy rationale.

Two deliberate exceptions, both worth carrying into 12b and 12c:

1. **Two trailing comments kept** (`updateCollectionTags`, `updateCollectionPeople`: `null // No
   tracking needed for collection updates`). The odd line break in those calls exists only because
   of the trailing comment. Removing it lets google-java-format collapse the call onto one line --
   a non-comment diff line, which would break the guardrail's whole guarantee. Not worth it for two
   comments; they belong to whichever MR touches that code for real.
2. **The class-member comment on `selfProvider` left in place** (out of scope per the Wave 4
   header). Its content is now also in the `getUpdateCollectionData` docblock, where the
   self-invocation actually happens. Deliberate duplication -- each is load-bearing at its own site.

**No comment in this file turned out to describe an undiscovered bug.** The guardrail said to expect
some; in `CollectionService` there were none. Every comment that made a checkable claim
(`populateSiblings(model, true)` being LISTED-only, the `collectionPage == 0` shortcut, D8 clearing
running first and unconditionally, both join-row writers running the cycle check and the password
propagation) was verified against the code and held. One staleness found, already filed:

- Confirms the MR 14 item for the `filterNonListedChildCollections` docblock. It is still stale, and
  the deleted inline comment above `collectionRepository.findByIds(referencedIds)` -- "used for both
  context detection and visibility filter" -- shows why: the batch-loaded children no longer feed any
  context detection, since `parentIsProtected` now reads `model.getIsPasswordProtected()` directly.
  The docblock still describes the removed derivation. The docblock itself was **not** touched here;
  it is MR 14's to rewrite. Its line reference has moved off `1536-1547` -- re-derive it by name.
- [x] `CollectionProcessingUtil.java` — **MR 12b, done.** 51 in-method comments removed plus the stale `TYPE-SPECIFIC PROCESSING` banner. Original (stale) list — D: 88, 93, 98, 111, 120, 160, 168, 226, 233, 244, 273, 291, 305, 316, 328, 336, 393, 400, 414, 430, 486, 616, 645, 693, 696, 699, 793, 804, 875, 883, 891, 904, trailing 801, 810. P: 188, 587, 593. Delete-redundant: 515, 626.
- [x] `ContentService.java` — **MR 12b, done.** 57 of 58 in-method comments removed; one kept deliberately as bug #16's evidence. Original (stale) list — D: 114, 123, 134, 139, 144, 151, 159, 175, 181, 189, 198, 206, 210, 219, 224, 230, 233, 250, 278, 297, 305, 315, 322, 331, 379, 382, 450, 458, 465, 468, 477, 504, 507, 510, 638, 934, 940. P: 417 (batch-vs-N+1, into the `searchImages` docblock), 977 (Rule B, into the `linkContentToCollection` docblock). Delete-redundant: 347, 579, 597, 614, 854.

### MR 12b outcome (2026-08-23) — shipped as [#178](https://github.com/themancalledzac/edens.zac.backend/pull/178)

Diff: 82 insertions, 121 deletions, two files (plus this doc). Build green (1315 tests, 0 failures), checkstyle 0
violations, spotless produced no reformatting.

Re-derived counts: `ContentService` 58 and `CollectionProcessingUtil` 51, so 109 in scope rather than
the prep note's 119 -- the same over-count as 12a, and for the same reason. `CollectionProcessingUtil`
also carries 21 comments at indent < 4, of which 18 are the six legitimate section banners (left in
place per the Wave 4 header) and 3 were the stale `TYPE-SPECIFIC PROCESSING` banner, removed as the
prep note explicitly authorizes. That banner now heads a section containing exactly one method,
`applyPaginationDefaults`, which is not type-specific at all.

Guardrail note, so 12c inherits the rule: the diff is comment lines plus **two blank lines**, and
nothing else. Both blanks were forced. Each sat immediately before a comment block that ended a
method body, so removing the comment left a dangling blank line before the closing brace that
spotless drops. Not a code change, but worth naming rather than claiming a perfectly pure diff.

Two trailing comments in `validateAndEnsureUniqueSlug` (`// Slug is unique`, `// Limit to prevent
infinite loop`) were removed and then deliberately restored. Unlike 12a's pair there was no reflow
risk here -- the code text stayed byte-identical -- but stripping a trailing comment still shows up
as a modified code line, which defeats the cheap "grep the diff for non-comment lines" audit that
makes this MR class worth running. Same rule as 12a: trailing comments belong to whichever MR
touches that code for real.

One promoted comment corrected a docblock as a side effect, which is worth flagging since it edges
on MR 14's territory. `populateCollectionsOnContent`'s docblock said "on image content items"; the
method has handled GIFs for some time, and the inline comment being promoted said exactly that. The
docblock now reads "image AND GIF content items". This is the promote operation, not a separate fix
-- but if 12c or 13 hits the same shape, prefer promoting over rewriting and say so.

### Bug #16 (medium, found by MR 12b) — `updateImages` claims a batch save it does not do

`services/ContentService.java` — the comment reading `// Batch save all successfully updated images
for efficiency` sits above a loop that calls `contentRepository.saveImage(image)` once per image, and
the log line underneath reports `"Batch saved {} updated images"`. `ContentRepository.saveImage` is a
single-row INSERT/UPDATE and the repository has no batch variant, so a batch of N image edits issues
N statements.

What makes this a real defect rather than a wording slip: `updateImages` is meticulous about N+1 on
every read path -- it batch-fetches the images, then their current tags, people and locations in one
query each, with two comments explicitly labelled OPTIMIZED -- and then does exactly N+1 on the write
path. The comment and the log are the only evidence anyone intended otherwise.

Not fixed here, and **the comment is deliberately left in place** per the Wave 4 guardrail: it is the
only remaining marker of the problem until its own MR lands. That MR should add a batch save to
`ContentRepository` and fix the log line with it. Both `updateImages` and the collaborator edit path
(`CollectionService.applyCollaboratorImageEdits`, which routes through `updateImages`) benefit.

Not to be confused with consolidation item #15, which separately notes that `getUpdateCollectionData`
fetches the collection row twice. That one is already filed and untouched.
### MR 12c worklist — re-derived 2026-08-23, use this instead of the line refs below

Measured on `3111e00` (after 12a and 12b merged), `grep -cE '^[[:space:]]{4,}//'` with banners
excluded. **68 total, not the 82 the prep note estimated** -- the third consecutive over-count, and
for the same reason every time: the note's per-file totals include class-member-level comments that
the Wave 4 header puts out of scope.

| File | In-method comments |
|---|---|
| `MetadataService.java` | 16 |
| `SyntheticCollectionResolver.java` | 10 |
| `UserPageAssembler.java` | 10 |
| `TagViewResolver.java` | 8 |
| `ContentModelConverter.java` | 6 |
| `PaginationUtil.java` | 4 |
| `ContentMutationUtil.java` | 3 |
| `TagService.java` | 3 |
| `UserMergeService.java` | 3 |
| `CollectionAccessService.java` | 3 |
| `CollectionFlags.java` | 1 |
| `validator/ContentImageUpdateValidator.java` | 1 |

Three of these -- `ContentModelConverter`, `PaginationUtil`, `TagViewResolver` -- are the files the
prep note measured at 100% line-reference accuracy, so their `D:`/`P:` lists below are actually
usable rather than needing full re-derivation. The rest are not.

`MetadataService` is the one with real shape to it: 12 identical "Metadata mutation: drop the CDN
copy" comments collapse into a single class-docblock sentence, exactly as 12a did for
`CollectionService`'s three CDN copies. That pattern is now established -- follow it.

**Guardrail for 12c: leave `CollectionAccessService.effectiveLevel` alone.** Its item below says the
comment should go "into the `effectiveLevel` docblock being rewritten anyway" -- that phrase is a
leftover from when MR 12 was scoped alongside a consolidation, and the rewrite it refers to is not
part of Wave 4. Twelve small files with 68 comments between them is a low-risk mechanical MR right
up until someone decides one of them wants restructuring, and `effectiveLevel` is the access-control
resolution function, which is the worst possible place in this set to take an unreviewed detour.
Promote the comment into the docblock as it stands, and if the method genuinely needs reworking,
report what changing it would do rather than doing it here.

The same instinct will come up on `CollectionFlags` and `PaginationUtil`, both of which are small
enough to feel rewritable in one sitting. Same answer: comments only.

The original per-file lists follow, kept as the record of intent (which comments were judged worth
promoting) rather than as coordinates. **All twelve done in [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)** -- see "MR 12c outcome" below.

- [x] `ContentModelConverter.java` — D: 379, 386, 473, 480, trailing 170. P: 654.
- [x] `ContentMutationUtil.java` — D: 384. P: 132.
- [x] `SyntheticCollectionResolver.java` — P: 38 (trimmed), 78, 95.
- [x] `TagService.java` — P: 65. D: 116.
- [x] `TagViewResolver.java` — D: 57, 65, 84. P: 71 (ordering-restoration rationale).
- [x] `MetadataService.java` — D: the 12 identical "Metadata mutation: drop the CDN copy..." comments at 55, 76, 101, 123, 144, 169, 192, 260, 313, 384, 410. Replace with one class-docblock sentence. P: 208.
- [x] `PaginationUtil.java` — D: 24, 27, 42, 45.
- [x] `UserPageAssembler.java` — P: 95 (V35 identity-merge, load-bearing), 117. D: 127. Trailing 206 is acceptable as a switch-arm note.
- [x] `UserMergeService.java` — P: 87. `CollectionAccessService.java` — P: 68, into the `effectiveLevel` docblock being rewritten anyway. `CollectionFlags.java` — D: 79, trailing 24.
- [x] `validator/ContentImageUpdateValidator.java` — D: 31. (51 died with the dead method in MR 1.)

### MR 12c outcome (2026-08-23) — shipped as [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)

All 68 in-method comments removed across the twelve files; every file now measures 0. The
load-bearing ones were promoted into 11 javadocs, 6 of them newly added.

**The diff is comment lines and nothing else, with zero blank-line churn.** `git diff -U0` filtered
for anything that is not a `//`, `/**`, `*` or `*/` line returns empty, and the blank-line count is
0 -- so Working rule 6's "name the dangling blank line in the PR" caveat did not apply here. Build
green, 1315 tests, 0 failures. For a comments-only diff that is the whole proof.

**No bugs found.** Per Working rule 7 this is the correct outcome to report, not a shallow pass.
Every checkable claim in the 68 held under verification: the 11 CDN-invalidation comments matched
their `markChanged()` calls, `deletePerson`'s guarded-delete reasoning matched the zero-row check,
`TagViewResolver`'s ordering-restoration comment matched the re-key-and-re-stream code, and
`UserMergeService`'s gross-versus-net counts matched `countImageTags`/`countCollectionTags`.

Count correction, the fourth in a row but the first in the other direction: the note said "the 12
identical CDN comments" and then listed 11 line numbers. There are 11. The file's total of 16 is
unchanged -- 11 CDN + 3 guarded-delete + 2 upsert.

Two comments the original lists marked `P:` were deleted instead, because the docblock above them
already said the same thing: `ContentMutationUtil`'s "merge with existing locations" (the
`associateLocationsByName` docblock already says "Additive: merges with the content's existing
locations rather than replacing them") and `PaginationUtil`'s four normalization notes.

#### The `effectiveLevel` report the guardrail asked for

The guardrail said to promote `CollectionAccessService:68` as it stands and report what changing the
method would do rather than doing it. Promoted as instructed; the method body is untouched. The
finding, which confirms the unverified lead already filed at the bottom of this doc:

**`canView` and `isClient` do not resolve through `effectiveLevel`, and the `effectiveLevel` docblock
says they do.** They are two-line passthroughs to `RoleRepository.canView`/`isClient`, and -- unlike
`hasAtLeast`, which really does call `effectiveLevel` -- they take a raw `Long userId` rather than an
`AuthPrincipal`. A principal is never in scope, so the GENERAL share-link ceiling cannot apply to
them.

Five call sites are affected: `UserShareControllerProd:92`, `ContentDownloadControllerProd:192`,
`UserRatingOverrideService:37`, `UserSelectsService:77`, `CollectionService:539`.

They are safe today, by two independent accidents. A flyby principal carries a null `userId`, and
`rm.user_id = :userId` never matches on NULL under SQL comparison semantics, so both queries count 0
and return false. On top of that, three of the five sites guard first anyway -- `UserShareControllerProd`
with `AuthPrincipal.isRealUser`, and `ContentDownloadControllerProd` and `CollectionService` with an
explicit `userId != null`.

Neither accident is asserted anywhere. No test pins the null-userId behavior of
`RoleRepository.canView`, and nothing stops a future caller from resolving a share principal to a
real user id before calling `canView` -- at which point a link holder gets view access the GENERAL
ceiling was written to deny, and the docblock will still claim the ceiling covers them.

What changing it would cost: routing both through `effectiveLevel` means changing their signatures
from `Long userId` to `AuthPrincipal`, updating those five call sites, and accepting that `canView`
becomes `hasAtLeast(principal, id, GENERAL)` -- which changes its result for a share principal from
false to true inside the share's scope. That is a real behavior change on the download and selects
paths, and it needs its own MR with its own tests. Filed as a Wave 3 follow-up rather than done here.
The cheap half -- correcting the docblock to say that only `hasAtLeast` and
`CollaboratorAccessInterceptor` resolve through `effectiveLevel`, and that `canView`/`isClient` are
safe only via the null-userId accident -- is also deliberately not done here, because a docblock that
describes access-control behavior should change in the MR that verifies that behavior, not in a
comment sweep.

## MR 13 — Comment debloat: media pipeline

### Sizing, measured 2026-08-23 on `3111e00` — split this before starting

**154 in-method comments, and the over-count pattern INVERTS here.** Every Wave 4 file measured so
far came in smaller than the doc implied; MR 13 comes in larger. Do not carry the "the estimates are
high" lesson into this MR.

| File | In-method comments |
|---|---|
| `ImageProcessingService.java` | 62 |
| `ImageUploadPipelineService.java` | 55 |
| `ImageMetadataExtractor.java` | 22 |
| `ImageMetadata.java` | 5 |
| `DownloadUrlService.java` | 4 |
| `S3MultipartOutputStream.java` | 2 |
| `EmailService.java` | 2 |
| `ReadCacheInvalidator.java` | 2 |
| `JobTrackingService.java` | 0 |

At 154 this is larger than `CollectionService` alone was (144), and MR 12 had to be split at 349.
Decide the split up front rather than discovering it mid-MR, which is what happened to both MR 9 and
MR 12. The natural fault line is obvious: `ImageProcessingService` + `ImageUploadPipelineService` are
117 of the 154, so 13a is those two and 13b is the remaining seven (37).

Path correction: `ImageMetadata.java` is in `services/`, not `model/` as the list below implies.
`JobTrackingService` has zero in-method comments -- its item below is a class-docblock fix only.

- [x] **MR 13a, done ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)).** `ImageProcessingService.java` — original (stale) list, D: 165, 170, 177, 184, 195, 208, 268, 273, 280, 287, 295, 348, 371, 377, 1278, 1287, 1295, 1303, 1330, 1339, 1347, 1355. P: 218-219 (fix staleness: RAW scheduling moved to `ImageUploadPipelineService`), 224, 229 (fix wrongness with bug #10), 343-344, 357-358, 382-383 (fix staleness: it is `ContentMutationUtil` now), 385, 388, 394-396, 408-410, 446-447, 461-463, 469, 591-592, 603-604, 611-612, 622, 630-631, 656-657, 762, 764, 798-799. Also fix the `deleteImageFromS3` docblock (769-772): web keys are content-hashed now.
- [x] **MR 13a, done ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)).** `ImageUploadPipelineService.java` — original (stale) list, D: 143, 161, 191, 229, 332, 336, 473, 656, 664, 698, 725, 757. P: 115, 130-131, 136, 192, 197-198, 207-209, 255-257, 288, 295, 298-299, 304, 318, 324, 381, 410, 417, 420-421, 431-432, 438-439, 459, 465, 512, 612-614, 710-711, 782. The 410-471 duplicates disappear with consolidation #9 (MR 18).
### MR 13a outcome (2026-08-23) — merged as [#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)

Java diff: 2 files, +131/-131. (MR 12c, [#180](https://github.com/themancalledzac/edens.zac.backend/pull/180), was 12 files, +69/-70.)

All 117 in-method comments removed from `ImageProcessingService` (62) and `ImageUploadPipelineService`
(55); both files now measure 0. The sizing note's 117 was exact -- the first Wave 4 estimate that
needed no correction. Promoted into 14 javadocs, 3 of them newly added on private methods
(`processFilesFromDiskLoop`, `ingestFilesGroupedByDayLoop`, and the `contentHash` expansion).

Diff is comment lines and nothing else. Two blank-line removals, both the separating blank that
belonged to a removed comment block rather than the Working-rule-6 dangling-blank case: one in
`prepareImageForUpload` after the RAW-deferral note, one in the dedupe UPDATE branch. No code
reflow. Build green, 1315 tests, 0 failures.

**One stale doc found and fixed** (a doc fix, in Wave 4's scope -- not a filed code bug).
`saveProcessedImages`'s docblock opened "Save prepared images to database in a single transaction",
which contradicts both the code and the PHASE 2 comment four methods above it: each image saves in
its own transaction via the `@Transactional` repository methods, precisely so one failure cannot
cascade. The comment being deleted was the accurate one, which is how the contradiction surfaced.
Corrected the docblock.

**Two instructions in the list below were already stale.** "P: 229 (fix wrongness with bug #10)" --
bug #10 is fixed and checked off; the comment at that site now correctly describes multipart having
no mtime and always taking the UPDATE branch, and `exportDateFromFile` carries the mtime rationale
in a docblock already. Nothing to fix. "P: 218-219 (fix staleness: RAW scheduling moved to
`ImageUploadPipelineService`)" -- that comment already said the scheduling is deferred and carried
through `PreparedImageData`, so it was accurate too, and was promoted as-is.

The `deleteImageFromS3` docblock fix the list asked for was real and is done: it claimed re-uploads
land on identical S3 keys "which is the norm -- keys are deterministic from filename/year/month".
True for the original and RAW keys, but the web key is content-hashed via `hashedWebFilename`, so a
changed image gets a new key and only a byte-identical re-export reuses the old one. Docblock now
says which is which.

The `410-471` duplicate block the list flags for consolidation #9 (MR 18) is untouched, as are the
two loops it refers to. Their new docblocks are deliberately written so the ingest one points at the
from-disk one for the shared behavior, which should make that consolidation easier to read, not
harder.

### MR 13b worklist — re-derived 2026-08-24 on `57a5506`, use this instead of the line refs below

**37 total, exactly what the sizing note predicted** -- the second Wave 4 estimate in a row needing
no correction. The over-count pattern that ran through MR 12 is over; take the remaining numbers at
face value.

**The line refs below are the most decayed in the whole doc.** Measured against the current tree:

| File | Comments | Doc's line refs still landing on a `//` |
|---|---|---|
| `ImageMetadataExtractor.java` | 22 | **1 of 24 (4%)** |
| `ImageMetadata.java` | 5 | **0 of 5** |
| `DownloadUrlService.java` | 4 | 2 of 4 |
| `S3MultipartOutputStream.java` | 2 | 0 of 1 |
| `EmailService.java` | 2 | 1 of 1 |
| `ReadCacheInvalidator.java` | 2 | 1 of 1 |
| `JobTrackingService.java` | 0 | class-doc fix only |

4% is the worst yet measured -- worse than `ContentService`'s 8%, which was the previous record.
Do not read the lists below as coordinates. The real comments are:

- [x] `ImageMetadataExtractor.java` (22) — extraction narration at 128, 135, 139, 180, 183, 203,
      211, 217 is redundant with the method names and goes. The load-bearing set is the XMP keyword
      logic at 274-318: hierarchical subjects first because Lightroom writes category parents,
      `"People|Jane Doe"` to a person, `"Weather|sunset"` to the leaf tag, the person-name filter
      (Lightroom emits people BOTH as `People|Name` and as standalone keywords), and the flat
      `dc:subject` fallback with no people distinction. That belongs in the
      `extractTagsAndPeopleFromXmp` docblock. Date-format notes at 431, 474-498 explain EXIF
      `YYYY:` versus ISO `YYYY-` detection -- promote, see the guardrail.
- [x] `ImageMetadata.java` (5) — 231, 256, 261, 268, 274, all describing the shutter-speed
      formatting ladder (pass through an existing fraction, `>= 1 sec` displays as-is, `< 1 sec`
      becomes `1/N`). One docblock on the formatter.
- [x] `DownloadUrlService.java` (4) — both blocks are load-bearing and promote: 104-105 (S3 keys are
      unique but `original_filename` is not, so entries are sequence-prefixed or `ZipOutputStream`
      throws on duplicate names) and 113-114 (a per-image failure writes a placeholder rather than
      tearing down the whole ZIP).
- [x] `S3MultipartOutputStream.java` (2) — 129-130, why the empty-`completedParts` guard exists
      despite a well-formed ZIP always having an end-of-central-directory record.
- [x] `EmailService.java` (2) — 140-141, the `SesV2Exception` versus `SdkClientException` split.
- [x] `ReadCacheInvalidator.java` (2) — 81-82, why the failure logs at debug rather than warn
      (expected until the CloudFront API origin is enabled).
- [x] `JobTrackingService.java` (0) — no in-method comments. The class docblock (now at 14-18, not
      14-15) says "background disk upload processing"; it also tracks ingest jobs, since
      `ingestFilesGroupedByDay` calls `createJob`. One-line fix.

### Guardrail for 13b: leave the date-parsing duplication in `ImageMetadataExtractor` alone

Promoting the comments at 431 and 474-498 puts both date parsers on screen at once, and the
duplication is obvious: `parseImageDate` (428) and `parseExifDateToLocalDateTime` (467) each do
their own EXIF-versus-ISO format detection. Folding them together will feel like the natural
finish to the promotion, because the docblock you are writing has to describe the same rule twice.

It is not in scope. That duplication is already filed as part of consolidation #17, which owns it
along with the `ensureDimensions` twins in the same file. Taking it here would move a filed
consolidation into a comments-only MR, and 13b's green build proves nothing about a refactor.

Promote the format-detection rule into both docblocks as it stands, and report what folding them
would cost -- specifically whether `parseImageDate`'s `int[]` return and
`parseExifDateToLocalDateTime`'s `LocalDateTime` return can share a parse step without one of them
growing a conversion that costs more than the duplication does.

The original per-file lists follow, kept as the record of intent rather than as coordinates.

- [x] `ImageMetadata.java` — D: 245, 270, 275, 282, 288.
- [x] `ImageMetadataExtractor.java` — D: 114, 121, 166, 169, 189, 197, 203, 208, 259, 299, 353, 396, 401, 409, 411, 415, 420, 443. P: 75, 98, 125, 269, 275, 286-287.
- [x] `S3MultipartOutputStream.java` — P: 119-120. `DownloadUrlService.java` — P: 91, 93, 104-105, 113-114. `EmailService.java` — P: 140-141. `ReadCacheInvalidator.java` — P: 81-82. `JobTrackingService.java` — the class doc at 14-15 also tracks ingest; update it.

### MR 13b outcome (2026-08-23) — shipped as [#183](https://github.com/themancalledzac/edens.zac.backend/pull/183)

Java diff: 7 files, +65/-45. All 37 in-method comments removed; all seven files now measure 0.
The re-derived 37 was exact -- third Wave 4 estimate in a row needing no correction. 93 in-method
comments left in `src/main`, all of them MR 14's.

Promoted into 13 javadocs, 2 of them newly added on private methods (`DownloadUrlService.writeZipEntries`,
`ImageMetadataExtractor.extractFromStream`). Diff is comment lines and nothing else -- verified by
`git diff -U0` filtered for anything that is not a `//`, `*`, `/**`, `*/` or blank line, which came
back empty both before and after `spotless:apply`. No blank-line removals: none of the 37 sat at the
end of a method body, so Working rule 6's dangling-blank case did not arise. Build green, 1315 tests,
0 failures -- the same count as 13a, as a comments-only MR should be.

**No bugs found.** Per Working rule 7 that is the reportable outcome, not a shallow pass. Every
checkable claim in the 37 held against its code, including the two the worklist called out as
load-bearing (the XMP person-name filter and the ZIP sequence prefix), both verified by reading the
branch they describe.

**Two comments were doing more than the worklist credited them with**, and both became docblock
sentences rather than deletions. `extractFromStream`'s "stop after first non-empty result" is a
first-wins rule across multiple XMP directories -- a file with several gets one keyword set, not a
merge, and nothing else in the file said so. The "Only set if not already extracted from EXIF" pair
at the two extractor methods is the EXIF-over-XMP precedence contract; the worklist listed both as
redundant narration to delete.

`JobTrackingService`'s class-doc fix was real and is done. Verified before editing per Working rule 8:
`createJob` has exactly two callers, `processFilesFromDisk` and `ingestFilesGroupedByDay`, so
"background disk upload processing" did understate it. Doc now names both pipelines.

### The date-parsing fold, costed (guardrail follow-up)

The guardrail's premise is off, and that is the main finding. It says the two parsers "each do their
own EXIF-versus-ISO format detection." Only one does. `parseExifDateToLocalDateTime` detects on
`charAt(4)` (`:` is EXIF, `-` is ISO). `parseImageDate` detects nothing -- it splits on `[: T-]` and
reads the first two numeric runs, which lands identically on both formats. What the two share is the
knowledge that EXIF uses `:` where ISO uses `-`, not a line of code. There is no common parse step to
extract, so the duplication being folded is about two lines of *documentation*, which 13b just wrote
into both docblocks.

Measured on the built class, the two disagree wherever the input is malformed:

| Input | `parseImageDate` | `parseExifDateToLocalDateTime` |
|---|---|---|
| `2024:05:15 14:30:00` | 2024/5 | `2024-05-15T14:30` |
| `2024-05-15T14:30:00` | 2024/5 | `2024-05-15T14:30` |
| `2024:05:15` | 2024/5 | `2024-05-15T00:00` |
| `2024:05` | 2024/5 | `null` |
| `2024:13:45 99:99:99` | **2024/13** | `null` |

So the fold is a behavior change in both directions, which is why it does not belong in a
comments-only MR:

- **Routing `parseImageDate` through the strict parser tightens it.** Truncated-but-usable dates that
  yield a year and month today would return `null`, fall through to the modify-date branch, and end
  at `LocalDate.now()`. That silently moves which `year/month` prefix an image lands under in S3, for
  exactly the malformed inputs least likely to be covered by a test.
- **It also fixes something.** `parseImageDate` currently returns month 13 for a nonsense date and
  builds an S3 path out of it. Not filed as a bug -- no comment contradicted the code, and it needs
  malformed EXIF to reach -- but it is a real robustness gap and the fold would close it.

On the specific question the guardrail asks: the `int[]` and `LocalDateTime` returns cannot share a
parse step cheaply. A shared `Optional<LocalDateTime>` core makes `parseImageDate` strict, which is
the regression above; keeping today's tolerance means retaining the permissive split inside the core
as a fallback, so both paths survive and an abstraction is added over them. A shared
"normalize EXIF to ISO" helper is worse: `parseExifDateToLocalDateTime` already does that inline, and
`parseImageDate` needs two integers, not normalized text, so the helper would add a call and a string
allocation while removing nothing.

Recommendation for consolidation #17: keep the `ensureDimensions` twins in scope -- that is the real
duplication in this file -- and re-scope the date pair from "fold the two parsers" to "decide whether
`parseImageDate` should stay permissive." That is a behavior decision with an S3-pathing consequence,
not a refactor, and it wants its own MR and a test for the month-13 case.

### MR 13c outcome (2026-08-23) — density pass over 13b's own docblocks

Java diff: 4 files, +25/-49, **net -24 lines and -192 words of prose.** No comments removed and none
added -- this MR only tightens the javadoc 13b wrote. Build green, 1315 tests, 0 failures.

Filed because the question "we keep saying debloat, but the diffs keep growing" turned out to be
correct once the numbers were separated. 13b's headline `+161/-53` was 88 lines of this tracker and
only +20 of Java, so the doc was not the problem -- but measured in words the Java was **+178 (+42%)**,
which is the opposite of a debloat. Working rule 10 records the three causes and the check that
catches them.

Combined, 13b+13c is what the MR should have been the first time: all 37 inline comments gone, 13
docblocks carrying the load-bearing rules, and **-4 lines / -14 words** overall.

Nothing was lost in the tightening. Every rule 13b identified as load-bearing is still documented --
the XMP first-wins rule, EXIF-over-XMP precedence, the person-name filter, the ZIP sequence prefix,
both date-format rules -- each now stated once, in the method that enforces it, instead of two or
three times across the file. Two docblocks (`S3MultipartOutputStream.close`,
`ImageMetadata.ShutterSpeedExtractor`) were left alone: they were already dense, and cutting them
further would have cost information rather than restatement.

---

## MR 14 outcome (2026-08-23)

Re-derived on `51fede9`: 93 in-method comments across 20 files, matching the count the tracker
predicted exactly -- the first Wave 4 worklist that did not need re-deriving. Disposition:

| | count | |
|---|---|---|
| deleted | 7 | restated the code, or the docblock already said it |
| promoted | 19 | a fact about the whole method |
| kept inline | 66 | a warning attached to one specific line |
| quarantined | 1 | `ContentService:227`, bug #16's only evidence |

Java-only **+4 lines / -51 words**. Twenty files became thirteen; seven went to zero
(`ClientGalleryAuthService`, `TextFormType`, `DatabaseInfoLogger`, `ClientIp`,
`JdbcPublicKeyCredentialUserEntityRepository`, `RequestMetricInterceptor`, `CollectionRepository`).

### The seven deletes

Three in `ClientGalleryAuthService` (`// Not password-protected -- allow access` above
`if (getGalleryPassword() == null) return true;`, and two like it), two in `TextFormType`
(`// Try to match by enum name first` above `valueOf(value.toUpperCase())`), one in
`DatabaseInfoLogger` (`// Log environment-configured values` above three `log.info` calls), and
`ClientIp:34` -- whose class javadoc already stated the same fact almost verbatim, which is
precisely the check working rule 10 asks for.

### The nineteen promotes

`JdbcPublicKeyCredentialUserEntityRepository.save`/`delete` (5 lines) -- both are whole-method
no-ops, and an `@Override` no-op is exactly what a docblock is for. `DatabaseInfoLogger` (1),
`RequestMetricInterceptor.afterCompletion` (2), `ContentRepository.searchImages` (3, a
caller-visible ordering contract), `CollectionRepository.findContentByContentIdsIn` (3, likewise),
and `AdminUserController.upgradeUser` (3) and `.merge` (2), both folded into docblocks that already
existed -- so each was compressed against what the docblock already said rather than appended.

### Why 66 stayed

The measurement is in working rule 12. `SecurityConfig`'s 24 were the test case the guardrail asked
for: a careful promoted draft that keeps every fact and states each rule once still came to 289
words against the inline 265, **+24 words (+9%)**, plus about ten lines of javadoc scaffolding. All
of the overhead is anchor-naming. A docblock must write "on `/api/auth/me`, `/api/auth/logout`,
`/api/auth/webauthn/register/**` and `/api/edit/**`" to say what an inline comment says by sitting
on the line.

The other durable cases: `RoleRepository`'s three `\s` notes sit against the text block whose
trailing `\s` they protect, and an editor deleting that `\s` would never read a docblock;
`AdminBootstrap`'s "Do not fix this into a single statement without preserving that property" has to
be next to the two statements; `CollectionControllerProd`'s six lines explain a `Cache-Control` call
that is deliberately *absent*, which no docblock can point at.

### Bug #16, re-verified

Still real. `ContentRepository.saveImage` is a single-row INSERT/UPDATE; the loop in `updateImages`
calls it once per image; the log line still reads "Batch saved {} updated images". The only
`batchUpdate` in `ContentRepository` is in `saveContentPeople`, a different table. N image edits
issue N statements. The comment stays put.

---

## MR 15 #2 outcome, 2026-08-23

One `requestMatchers("/api/read/user/**").hasRole("USER")` replacing **17** per-method `isRealUser`
guards across six controllers. Java-only **main +21/-63 (net -42)**, **test +193/-191**, total -40.
The item's "~51 lines" was exact for the guards themselves: 17 x 3 = 51, and the rest of the 63 is
two now-unused `HttpStatus` imports plus three docblocks this change made false.

### The guardrail's premise was false

The guardrail costed three placements and called "outside the toggle" the honest default that
"takes away a dev convenience". Verified against `143f471`: **there is no such convenience.** The 17
guards were unconditional -- plain `if (!AuthPrincipal.isRealUser(principal))` with no profile check
-- so an anonymous `GET /api/read/user/me/page` already 401'd in dev. Placing the matcher outside
the toggle changes nothing in any profile.

It also picked the wrong precedent. `SecurityConfig` already carries an **unconditional**
`hasRole("USER")` matcher outside the toggle, for `/api/auth/me` and `/api/auth/logout`.
`/api/edit/**` sits inside the toggle because it is a *write* surface dev wants login-free. An
unconditional session-required read surface matches `/api/auth/me`, so the matcher went next to it.
"Outside + dev-only permitAll" was rejected on the same finding: it would have *added* a dev
convenience these routes never had.

This is working rule 8 at the scale of a whole guardrail, not one `P:` note. Verified before acting;
the note was as old as the line numbers next to it.

### The count was 17, not 18

The 2026-08-23 re-derivation raised 17 to 18 and said the doc "missed the one at 34" in
`UserShareControllerProd`. Line 34 is a **javadoc line**, which a grep for `isRealUser` picks up:

```
 * identity is enforced here with {@code AuthPrincipal.isRealUser} rather than by a matcher. That
```

Its other two corrections hold and were worth having: `UserSelectsControllerProd` really had drifted
35/46/62 -> 37/48/64, and `UserRatingOverrideControllerProd` really is in `controller/user/` at
41/54. Removing the guards mechanically confirmed the total: 1 + 3 + 4 + 3 + 4 + 2 = 17.

That same docblock had to be rewritten, along with two on `AuthPrincipal` -- all three asserted that
identity is enforced in the controller "rather than by a matcher", which this MR inverts.

### Two costs the item did not price

**28 assertions across 6 test classes pinned the 401 at the controller.** All five MockMvc tests
used `MockMvcBuilders.standaloneSetup`, which builds no security chain, and
`UserShareControllerProdTest` called controller methods directly -- so none of them could survive
the move. `FlybyWriteLockoutTest` (90 lines) existed solely to pin these guards and was deleted
outright. Replaced by `config/UserRoutesAuthorizationWebMvcTest`: four tests on the real chain
against the **real** controllers as beans, not stubs, because the risk this MR introduces is a route
sitting outside the matcher's path pattern and only real `@RequestMapping` values catch that. It
keeps the `verifyNoInteractions` property the deleted class was protecting.

**A flyby's status changes 401 -> 403** on these 17 endpoints, accepted by decision. A share-link
holder is authenticated but holds no authorities, so `hasRole` denies it as
authenticated-but-unauthorized. `FlybyAccessWebMvcTest:83-84` already documents exactly this split
for `/api/admin/**`. Anonymous stays 401. Both are now pinned.

Verified equivalent otherwise: `SessionAuthenticationFilter:39` and `WebAuthnService:221` both grant
`ROLE_USER`, and `SessionService.resolve:146` always builds a non-null `userId`, so
`hasRole("USER")` and `isRealUser` accept exactly the same callers.

### Verification

Mutation-checked rather than assumed: with the matcher stripped, `UserRoutesAuthorizationWebMvcTest`
fails; restored, the full suite is 1,302 green with 0 checkstyle violations.

`UserRatingOverrideControllerProd` still has **no controller test at all** (only a service test) --
a coverage gap for MR 26, not introduced here.

---

## MR 15 #6 outcome, 2026-08-24

Four `currentUserId` copies became `config/CurrentUser.userId()`. Java-only main **-26 lines,
**+36 words**. Shipped with a second commit closing the `PersonRepository` carry.

The re-derived table was right this time -- four declarations, seven call sites, all verified
against `abcf549` before the edit, and the count came out even when the change was made (rule 13's
test). `CollectionService:549` was the copy the original three-copy item missed, and it is the one
that matters most, because it is not a controller and it is the caller whose null is hardest to
tighten.

### Where it went, and why not `AuthPrincipal`

The item's stated fix was "move it onto `AuthPrincipal`". That does not survive contact with the
code and was not done. `AuthPrincipal` is a Spring-free record in `model/`; this helper is a static
`SecurityContextHolder` read, not a property of a principal instance. Putting it there drags Spring
Security's context into a model type, and every test that constructs an `AuthPrincipal` would be
constructing something that can read ambient global state.

`config/` was the answer: `ClientIp`, `GalleryAccessCookies` and `FlybyCookies` are already static
final helper classes there, next to the security plumbing. No new package, no new pattern.

### The null-contract report the guardrail asked for

The contract was left alone, as instructed. Tightening it to `orElseThrow` would cost, concretely:

| Call site | What null means today | Cost of throwing |
|---|---|---|
| `AdminUserController:230` (audit log) | dev, gate open | log line loses its actor; harmless |
| `AdminUserController:330` `addMember` | dev, gate open | breaks local role assignment |
| `AdminRoleController:65` `createRole` | dev, gate open | breaks local role creation |
| `AdminRoleController:120` `setGrant` | dev, gate open | breaks local grant editing |
| `AdminRoleController:148` `addMember` | dev, gate open | breaks local role assignment |
| `ContentDownloadControllerProd:190` | anonymous visitor | 500 on every anonymous download |
| `CollectionService:538` | anonymous visitor | 500 on every anonymous gallery visit |

So the four admin sites break local development only, and the two read-surface sites break
production for logged-out visitors. **These are not one contract with one fix.** The admin four
would be closed properly by making `app.admin.enforce-authz=true` unconditional -- a Wave 3-shaped
decision about dev ergonomics, not a consolidation -- and the read-surface two are correct as they
stand and should never throw. Anyone revisiting this should split it in two before touching either.

### Two more copies of the same read, not folded in

Scope was the four named `currentUserId` helpers, so these were left alone, but they are the same
static read and belong to whoever picks up the follow-up:

- `SyntheticCollectionResolver:146` `currentPrincipal()` -- identical body, returns the principal
  instead of `.userId()`. `CurrentUser.userId()` is exactly this plus a field read.
- `CollectionService:1531` `viewerMaySeeHidden` -- the same read inlined, plus a `p.userId() != null`
  check, because it passes the whole principal to `hasAtLeast`.

Folding both in means adding `CurrentUser.principal()` and having `userId()` delegate to it. It is
mechanical and behavior-preserving. It was not done here because the item said four.

**This is also what the re-derivation missed, twice over.** It found four copies by grepping the
helper *name*; these two are the same code under different names. That is working rule 14.

### The `PersonRepository.findAccountUserIdsByIds` decision

Decided, not carried a fifth time. **It is a real gap and a low-severity one, and the method was
the wrong shape to fix it.** Verified: zero callers in `src/main` *and* `src/test` -- the only hit
in the tree was its own declaration.

Its docblock claimed it preserved the "only account-backed persons receive a role membership" rule.
Nothing called it, so the rule was documented and unenforced -- which is what the tracker correctly
spotted, and why "delete it as dead code" was the wrong disposition three times running.

Severity, stated honestly: both endpoints are `/api/admin/**` behind `hasRole("ADMIN")`, so the
actor is already an admin, and a tag-only `PERSON` row has a null email and password_hash so it
cannot log in. Admitting one grants nobody anything today. The risk is a **dormant grant** -- if
that person is later upgraded to an account, it inherits the role silently.

The fix went to `RoleRepository.addMember`, the single choke point both controllers already share,
rather than into two controllers (which would add the duplication MR 15 exists to remove). The
deleted method took a `List` and both call sites pass one id, which is why wiring it in was always
awkward and why it kept being deferred instead of used. Existing integration tests seed `ACTIVE`
users and were unaffected; two new tests cover a `PERSON` row and an unknown id.

### Verification

`mvn clean install` green at each commit. 1302 tests before, **1304 after**, 0 failures.

## S-1 outcome, 2026-08-24 — `UserStatus` enforced in the auth path

Two guards, both mutation-verified. `AuthController.login` and `SessionService.resolve` now require
`UserStatus.ACTIVE`. 1304 tests before, **1308 after**.

### The allowlist was the free choice, and it is the one that fails closed

The item said allowlist versus `<> 'DISABLED'` was free, having already disproved the
onboarding objection. Shipped the allowlist (`!= ACTIVE`), for a reason the item did not give:
`UserStatus` has four values, and only one of them should be able to act. A denylist admits `PERSON`
-- a tag-only identity with no login account -- and admits `INVITED`, which is reachable with a
password hash still attached because an admin can PATCH an ACTIVE user back to INVITED without
clearing it. It also means the next status added to the enum is admitted by default. This is the
same fail-closed reasoning S-1 used to settle the MR 15 #6 allowlist question, applied to itself.

### Where the login guard went, and why the position matters

Into the existing `maybeUser.isEmpty() || passwordHash == null` branch as a third disjunct, not as a
new branch after the password check. That branch already performs a dummy BCrypt against a constant
hash to equalize response time between unknown-email and wrong-password. Folding the status test in
means a non-ACTIVE account pays the identical cost and returns the identical 401, so the fix does
not open the user-enumeration timing oracle the surrounding code exists to close. A separate guard
placed after `passwordEncoder.matches` would have returned fast on a correct password for a disabled
account, which is a worse oracle than the one being defended against -- it distinguishes "disabled
with the right password" from every other failure.

The test pins this: `verify(passwordEncoder, never()).matches("correct", "{bcrypt}$2a$10$hash")`.
The real hash is never consulted, so the guard is provably ahead of the password check.

### Mutation results (working rule 15)

Both guards were stripped and the suite re-run, per rule 15's standard that a guard test only counts
if it reddens.

| Mutation | Result |
|---|---|
| Remove the status test from `SessionService.resolve` | **2 failures** -- `resolveRejectsSessionWhoseAccountWasDisabled`, `resolveRejectsSessionWhoseAccountWasReturnedToInvited` |
| Remove `getStatus() != ACTIVE` from `AuthController.login` | **2 failures** -- both `loginForNonActiveAccountReturns401AndCreatesNoSession` cases, `Status expected:<401> but was:<204>` |

The 204 is the finding stated as a test result: without the guard, a DISABLED account with a correct
password logs in successfully.

Two details that decide whether these tests can fail at all. The login test stubs the real hash to
return true and marks the stub `lenient()` -- without that stub the mutated code would consult an
unstubbed matcher, get `false`, and 401 anyway, producing a test that passes either way. And the
resolve tests must be integration tests: `AppUserRepository` is mocked in every unit test that
touches this path, and a mock returns whatever entity the test built regardless of the column, so
the guard would be invisible. Rule 15's "testing through a mock of the thing under test" again.

The resolve test also asserts the session row is still unrevoked and unexpired after the rejection,
which is what makes it a status test rather than an accidental pass through the revocation branch.

### What was NOT done, stated rather than left ambiguous

**Session revocation on status change is not in this MR.** The item scoped it as defense in depth
and asked for an explicit statement of which half shipped. `AppUserRepository.updateStatus` is still
a bare `UPDATE`. The hole it would close is already closed for access: `resolve` reads status fresh
on every request, so a disabled account's live sessions stop resolving on their next request without
anyone touching `user_session`. What revocation would add is tidying the rows and cutting the window
to zero rather than to one request. Still open, now S-8.

### The `AuthPrincipal` cost, reported instead of implemented

The guardrail asked for the price of putting status on the principal rather than paying it. It is
higher than the call-site count suggests, and the field would be inert.

Construction sites: **3 in main** (two of them are `AuthPrincipal`'s own `client` and `flyby`
factories; the third is `SessionService`) and **30 across 21 test files**, all through the 4-arg
convenience constructor the board already decided to keep. So the mechanical cost is 33 edits, or
zero real edits and one hidden defect: give the 4-arg constructor a hardcoded `ACTIVE` default and
all 30 test principals silently become ACTIVE, which is a field no test can ever exercise.

The reason not to do it is stronger than the count. **The field could only ever hold `ACTIVE`.**
`AuthPrincipal` is built once per request, inside `resolve`, immediately after the guard that
rejects every non-ACTIVE account. Any code reading `principal.status()` runs strictly after that
check has passed, so the field is a constant with a getter. A test asserting on it cannot fail --
rule 15's "reports coverage" defect, built in by construction.

And the fan-out is the shape MR 15 #2 just deleted: **21 `@AuthenticationPrincipal` parameters
across 9 files** plus **6 `SecurityContextHolder` reads**, each a place someone could add a status
check, none of which would be reachable. That is 17 copy-pasted guards growing back into 27, one
quarter after one matcher replaced them.

### Verification

`mvn clean install` green -- spotless and checkstyle included. **1308 tests, 0 failures** (1304
before; the 4 new are 2 parameterized login cases and 2 resolve cases).


## S-2 outcome, 2026-08-24 -- the merge path upholds the `addMember` rule

One predicate plus a trailing delete, mutation-verified at both the DAO and the service level.
1308 tests -> 1312.

### The fix, and why it is not a target constraint

The obvious move is to constrain the merge target in `requireMergeable` the way the source is
constrained. That is wrong: de-duplicating two tag-only people is a normal operation, and a PERSON
target is the ordinary case, not the suspicious one. Blocking it would break the feature to close
the hole.

So the guard went where the rule already lives -- the SQL. `repointMemberships` now carries the same
`status <> 'PERSON'` test `addMember` enforces:

    UPDATE role_member SET user_id = :tgt WHERE user_id = :src
      AND EXISTS (SELECT 1 FROM users WHERE id = :tgt AND status <> 'PERSON')

and a trailing `DELETE FROM role_member WHERE user_id = :src` clears whatever the guard refused to
move, returning the count so `UserMergeService` can log it at WARN.

Dropping rather than refusing is the right disposition because of what these rows are. A
`role_member` row pointing at a PERSON is a row `addMember` will not create; it grants nothing while
it points at a PERSON; and it exists only because the rule went unenforced for the feature's whole
life. Moving it onto another PERSON would carry that illegal state across the merge instead of
ending it. Dropping also matches what the schema does unaided -- `role_member.user_id` is `ON DELETE
CASCADE` (V45), so anything left on the source vanishes when `deletePersonById` runs a line later.

The trailing delete is therefore redundant in the merge path and kept anyway, so the method leaves
the table consistent on its own rather than depending on a cascade three migrations away and a
caller that happens to delete the source next. It is a no-op on every merge into an account.

### Mutation results (working rule 15)

Stripping the `EXISTS ... status <> 'PERSON'` predicate reddens **two tests at two levels**:

| Test | Level |
|---|---|
| `RoleRepositoryIntegrationTest.repointMembershipsDropsMembershipsWhenTargetIsNotAnAccount` | DAO |
| `UserMergeIntegrationTest.mergeIntoPersonTargetDropsMembershipRatherThanCarryingItAcross` | service |

Both hold a positive counterpart asserting an account target still collects the membership, so the
guard cannot be "passed" by breaking the merge outright.

Both tests need a `role_member` row pointing at a PERSON, which `addMember` refuses to create, so
both insert it with raw JDBC. That is not a shortcut -- it is the finding's precondition, and it is
exactly the shape of the pre-`4976220` production rows.

### The service tests went into the existing file

`UserMergeIntegrationTest` already existed with the seed helpers and a class docblock describing
this exact round-trip; its three tests simply never touched `role_member`. A new
`UserMergeServiceIntegrationTest` was drafted first and thrown away -- it would have duplicated the
seeding and split merge coverage across two files in a repo mid-way through a duplication wave. The
tracker's claim that "neither test added by #191 touches `repointMemberships`" was right but
understated: **no test anywhere did**, in either file.

## S-3 outcome, 2026-08-24 -- the delete-person guard has a test that can fail

Two DAO tests, no source change. 1312 tests -> 1314.

### Mutation results (working rule 15)

The item's premise was re-proven before the fix and disproven after it. Stripping `AND status =
'PERSON'` from `PersonRepository.deletePersonById` and running the **whole suite**:

| | Result |
|---|---|
| Before (tracker's 2026-08-24 run, 1304 tests) | all pass |
| After (this MR, 1314 tests) | **1 failure**, `PersonRepositoryIntegrationTest.deleteLeavesARealAccountStanding` |

One failure and only one, which is the useful form of the result: it confirms the new test catches
the mutation *and* re-confirms that nothing else in 1313 tests does. `MetadataServiceTest`'s
`deletePerson_refusesAnAccountId` stays green under the mutation because it stubs
`deletePersonById` to return 0 -- it tests the service's 0-to-404 conversion, which is real, and
never reaches the SQL.

`deleteRemovesATagOnlyPerson` is the positive counterpart and also stays green under the mutation,
which is correct: stripping the predicate does not stop a PERSON row from deleting. Its job is the
opposite mutation -- without it, a `deletePersonById` that deleted nothing at all would pass the
guard test.

### The test went in a new DAO file, not `UserMergeIntegrationTest`

The item nominated `UserMergeIntegrationTest` on warm-context grounds: it already seeds PERSON and
ACTIVE rows, and S-2 had just worked inside `deletePersonById`'s caller. Both true. It still went
to a new `dao/PersonRepositoryIntegrationTest` instead, for two reasons.

The first is that `src/test/.../dao/` is where this repo puts DAO-level guard tests, including
S-2's own: `RoleRepositoryIntegrationTest` holds the DAO half of the S-2 pair and
`UserMergeIntegrationTest` holds the service half. S-3's guard is a `PersonRepository` predicate
with no service-level counterpart to pair with, so the DAO file is the whole of it. Of the 33 files
in that directory, `PersonRepository` was the one guarded DAO with none.

The second is that one of S-3's three premises was `find src/test -name "PersonRepository*"`
returning nothing. Closing the finding without changing that leaves the premise reading true to the
next reviewer, and the next mutation run finds coverage in a file named for a different service.

This does not contradict S-2's thrown-away `UserMergeServiceIntegrationTest`. That draft was a
*service* test duplicating the seeding in an existing service test file. This is a DAO test in the
directory that had no file for this DAO.

### What making `deletePersonById` throw would cost, reported instead of implemented

The guardrail asked for the cost rather than the change. Grepping the method (working rule 16) gives
**two** callers in `src/main`, and they want opposite things from a throw.

| Caller | Uses the return how | What a throw does to it |
|---|---|---|
| `MetadataService.deletePerson:181` | `if (... == 0) throw new ResourceNotFoundException(...)` | nothing, if the DAO throws the same type; the 404 is already this behavior |
| `UserMergeService.merge:78` | discards it | adds a third exception to a method whose javadoc lists two, on a path `requireMergeable` already guarantees is unreachable |

So the throw buys almost nothing at the caller that motivates it. `MetadataService` already converts
0 into a 404, and the transaction rollback the method's docblock describes happens either way --
same transaction, same rollback, only the throw site moves down a layer.

The cost is concentrated in three places:

- **Policy moves into the DAO.** `PersonRepository` extends `BaseDao`, and every sibling returns a
  count, an `Optional`, or void. For the throw to preserve today's 404 it has to be
  `ResourceNotFoundException`, which is a `config`-package web concern; anything else changes the
  admin API's status. Per working rule 3, `IllegalStateException` would make it a 400 -- exactly the
  behavior change the guardrail rules out.
- **The two callers want different policy.** `MetadataService` wants a 404. `UserMergeService` wants
  "this cannot happen"; if it ever does, `requireMergeable` and the SQL predicate disagree, and
  surfacing an invariant break as a 404 on the admin merge endpoint is the wrong answer. One shared
  throw cannot serve both without a catch at one of them.
- **Test coverage goes down, not up.** `MetadataServiceTest` has three sites stubbing the int
  return. `deletePerson_refusesAnAccountId` becomes "stub a throw, assert it propagates", which
  tests strictly less than the 0-to-404 conversion it tests now, because that conversion no longer
  exists.

The one real gap a throw would close is `UserMergeService.merge` silently discarding a 0. It is
unreachable today -- `requireMergeable` throws `IllegalStateException` unless the source is a PERSON
-- and if it is worth closing it is cheaper to close there, with `if (deletePersonById(sourceId) ==
0) throw new IllegalStateException(...)` at that one call site, than by changing a primitive two
callers share. Not filed as an item; noted here so the next reader does not re-derive it.

### Not folded in

S-4 stayed out, per the item. Same working rule, different mechanism (a Spring annotation, not a SQL
predicate) and a different test type (context, not integration).

## S-4 outcome, 2026-08-24 -- `ProdSecretGuard` cannot be unwired silently

Four context tests and one deletion, no source change. 1314 tests -> 1317.

### The gap was the annotation, not the method

`ProdSecretGuard.verify()` is what stops prod booting on a default or blank `internal.api.secret`,
or with `app.admin.enforce-authz=false`. Its six tests all routed through one `invokeVerify` helper
doing `getDeclaredMethod("verify").setAccessible(true).invoke(...)`, so every one of them tested the
method body on a hand-built object and not one could see the `@PostConstruct` that makes the
container call it. Delete the annotation and the guard is dead at startup with the suite green.

The fix is a `@Nested class Wiring` using `ApplicationContextRunner` -- a real context, the bean
registered as a bean, the container calling `verify()`. `ApplicationContextRunner` was not used
anywhere in this repo before; it is the right tool here because the alternative, a `@SpringBootTest`
on the prod profile, would have to stand up the whole prod application -- datasource, S3,
CloudFront, `InternalSecretFilter` -- to assert one bean refuses to construct.

### Mutation results (working rule 15)

Two mutations, two distinct reddenings:

| Mutation | Result |
|---|---|
| delete `@PostConstruct` (the item's mutation) | 2 failures out of 1317: `prodRefusesToStartOnTheDefaultDevSecret`, `prodRefusesToStartWithTheAuthzGateOff` |
| delete `@Profile("prod")` | 1 failure: `guardIsNotRegisteredOutsideProd` |

`prodStartsOnARealSecret` is the control and asserts `hasSingleBean(ProdSecretGuard.class)`, not just
`hasNotFailed()`. Without that assertion a context where the bean was never registered -- profile
typo, `withUserConfiguration` dropped -- would read exactly like a guard that passed, and both
failure tests would then be failing for a reason unrelated to the guard.

`guardIsNotRegisteredOutsideProd` is not scope creep: `@Profile("prod")` and `@PostConstruct` are
the same wiring, and a guard that throws on every profile would break dev boot rather than protect
prod. The `@Profile` mutation demonstrates it -- with the annotation gone the non-prod context does
not merely gain a bean, it fails to start.

### The duplicate test, removed -- and the item's reason for it was slightly wrong

`enforceAuthzDisabledThrowsEvenWithAGoodSecret` built the identical
`new ProdSecretGuard(REAL_SECRET, false)` as `enforceAuthzDisabledThrows` directly above it and
asserted `hasMessageNotContaining("internal.api.secret")`.

The item said its distinguishing assertion "cannot be false". Checked rather than repeated (working
rule 8): it **can** be false -- reword the authz message to mention `internal.api.secret` and it
fails. That is a message-wording assertion, not a behavior one, and it is the only thing the test
uniquely covered.

The real reason it goes is that the independence it claims to test is already tested by the pair
around it: `defaultDevSecretThrows` (bad secret, authz on) and `enforceAuthzDisabledThrows` (good
secret, authz off) demonstrate the two checks fire independently, and the second pins the message
positively. Deleted.

### The tests went in the existing file

`ProdSecretGuardTest` already existed and is where anyone looks for this class's coverage. The
wiring tests went into a `@Nested class Wiring` inside it rather than a separate
`ProdSecretGuardContextTest`, so the reflective tests and the tests that exist *because* the
reflective ones cannot see the annotation sit next to each other. That is the opposite call from
S-3, and for the opposite reason: S-3 had no file to join.


---

# Moved from the tracker 2026-08-24 (working rule 11)

These were closed-out write-ups still sitting in the tracker. Content unchanged.

## S-7 outcome, 2026-08-24 -- status is read before a session is minted

Shipped as [#199](https://github.com/themancalledzac/edens.zac.backend/pull/199). Both halves in one
MR, as the item specified. Suite 1,317 -> 1,322.

### The item's specified fix was wrong, and wrong in the direction that hides

The item was as carefully written as anything on this board: marked COLD, re-verified against
`4abb28e`, every premise anchored to a `file:line`. It said the fix was "require `INVITED` at the
flip -- an allowlist, the same shape S-1 shipped, not a `<> DISABLED` denylist", and it explicitly
argued this was *not* a product call because `UserStatus.INVITED` already answers it.

The form was right. The membership was not. `AdminUserController.regenerateInvite` mints a
password-reset link for an **ACTIVE** user, who completes the same accept flow -- its own docblock
says "a resend for an `INVITED` user, a password-reset for an `ACTIVE` one (both complete the same
accept flow)", and `UserInviteService.regenerateInvite`'s says the same. An `INVITED`-only allowlist
would have closed the security hole by breaking admin-issued password reset.

That failure mode is worse than it sounds, which is why it became working rule 18: **a wrong
allowlist fails closed.** It produces no security regression and no error at the guard. It surfaces
later, somewhere else, as "password reset stopped working", with nothing pointing back at the MR
that caused it. The only thing that catches it at review time is a test asserting the *legitimate*
case, which is why `activeUserAcceptsForPasswordReset` exists and why it is worth its lines.

Shipped `{INVITED, ACTIVE}`. The allowlist form still earned its keep independently: `UserStatus` has
a fourth value, `PERSON` (tag-only identity, no login account, added V35), so `!= DISABLED` and
`{INVITED, ACTIVE}` are genuinely different sets and the denylist would let a PERSON row become a
real account.

### The WebAuthn half granted nothing, and was still worth closing

`finishLogin` minted an `mfa=true` session for any account with a registered passkey, reading no
status. S-1's `SessionService.resolve` guard made that session dead on its next request, so nothing
was granted -- which is exactly why it stayed invisible. It is closed anyway: a guard at the read
chokepoint covers entry points you failed to enumerate (working rule 16), but that is a reason to
keep the chokepoint, not a licence to mint sessions the system will refuse.

Worth recording: `WebAuthnServiceTest`'s `admin` fixture had **no status set at all**. The happy-path
test only passes after adding `.status(ACTIVE)`, which is a small proof that the guard is real
rather than decorative.

### Review moved it all out of the controller

The first draft put the guard, the three writes and the session mint inside `InviteController.accept`
-- not by decision, but because that is where the surrounding code already sat. Review called it,
correctly, on two counts: logic does not belong in a controller, and the guard carried a five-line
inline comment.

The flow now lives in `UserInviteService.accept`, returning an `AcceptResult` the controller maps in
a switch expression. `PasswordEncoder`, `SessionService`, `@Transactional` and `@Slf4j` all left the
controller. The status rule became `mayAcceptInvite`, a named predicate carrying its reasoning in a
docblock instead of a comment block.

The tests moved with the logic rather than staying put -- the behavioral cases to a new
`UserInviteServiceAcceptTest`, leaving `InviteControllerTest` with status mapping and validation
only, 12 tests down to 7. The tell was there in the original: **a controller test that mocks a
`PasswordEncoder` is reporting that the logic is in the wrong file.** Taught working rule 19.

### Mutation results (working rule 15)

Run twice -- once against the original controller-resident guard, and again after the refactor moved
it, because relocating a guard invalidates its earlier verification.

| Mutation | Reddens |
|---|---|
| `mayAcceptInvite` always true | `disabledUserIsRejectedAndNothingIsWritten`, `personRowIsRejected` |
| Narrow it to `INVITED` alone | `activeUserAcceptsForPasswordReset` |
| Rewrite as `!= DISABLED` | `personRowIsRejected` |
| Delete the `finishLogin` check | `finishLoginOnNonActiveAccountIsRejectedAndCreatesNoSession` |

Restored-source run confirmed green after each, per rule 15's stale-bytecode note.

### S-9 came out of it with one predicate instead of two

S-9 ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)) had flagged a drift risk:
the "may this account hold a live invite" rule would exist in two files with no shared definition.
Extracting `mayAcceptInvite` for S-7's refactor gave S-9 somewhere to call, so
`invalidateInvitesForStatus` reuses it. One rule, two call sites, no drift -- resolved rather than
deferred, and the reason S-9 is now stacked on S-7.


## S-9 outcome, 2026-08-24 -- invites die with the account

Shipped as [#200](https://github.com/themancalledzac/edens.zac.backend/pull/200), its own MR as
scoped, stacked on S-7. Suite 1,322 -> 1,328.

### The cost report the item was owed

The instruction was to leave the admin status endpoint alone and report what invalidating invites
there would cost before doing it. Measured, not estimated:

| | |
|---|---|
| Source | 8 lines across two files. No new dependency -- `userInviteService` was already injected into `AdminUserController` and already called in this very method (the email-change branch). |
| Query | One extra `UPDATE`, no new shape. `updateUser` is `@Transactional` and `invalidateInvitesForStatus` joins it. |
| Existing test churn | **Zero.** Verified by running `AdminUserControllerTest` before touching any test: 46 green. |
| New tests | Three. |

The zero-churn result is the interesting one and it was not luck. All four pre-existing
`invalidateInvites` assertions patch to `INVITED` or `ACTIVE`, so none of them can observe a
DISABLED-triggered call. The existing suite already pinned the *negative* side of this rule, which is
why the "sweep on every status write" mutation reddens five tests -- four of which nobody wrote for
this MR. Guard tests written months earlier did real work here.

### Keyed on the resulting status, not on a transition

The obvious implementation compares before and after and fires on `not-DISABLED -> DISABLED`. That
misses a window: an invite issued while the account was *already* disabled is never swept. Keying on
the resulting status covers it, and costs nothing -- `invalidateUnusedForUser` affects zero rows when
there is nothing to kill. `reDisablingAlreadyDisabledUserStillSweepsInvites` reddens under exactly
the transition-test rewrite, so the choice is pinned rather than merely commented.

### The drift risk resolved instead of deferred

The item's own text warned that S-7 and S-9 would encode the same "may this account hold a live
invite" rule in two files with no shared definition -- working rule 14's failure, arriving by
construction rather than by accident. The first draft did exactly that.

Review of S-7 forced the rule out of `InviteController` and into a named predicate,
`UserInviteService.mayAcceptInvite`. That gave S-9 something to call:
`invalidateInvitesForStatus` tests eligibility through the same predicate the redemption site uses.
One rule, two call sites, and the two cannot disagree. This is why S-9 ended up stacked on S-7
rather than independent -- worth the merge-order cost, and the layering fix and the drift fix turned
out to be the same edit.

### Mutation results (working rule 15)

| Mutation | Reddens |
|---|---|
| Never sweep | `invalidateInvitesForStatusSweepsWhenLeavingTheInviteLifecycle`, `invalidateInvitesForStatusSweepsForPersonToo` |
| Sweep unconditionally | `invalidateInvitesForStatusLeavesEligibleAccountsAlone` |
| (at the controller) delete the delegating call | the two `AdminUserControllerTest` delegation tests |

### S-8 is now the natural next item

`AppUserRepository.updateStatus` still does not revoke live sessions. It is the same shape as this,
in the same handler, on the lines S-9 just changed. The tracker argued from the start that "disabling
an account revokes its live sessions and its outstanding invites" is one coherent change and that two
MRs touching the same handler is worse than one. They were split on instruction; the consequence is
that S-8 now edits code written today. Do it while it is fresh.


## S-8 outcome, 2026-08-24 -- a status change revokes the sessions already minted

Shipped as [#204](https://github.com/themancalledzac/edens.zac.backend/pull/204), merged
2026-08-24. Suite 1,328 -> 1,338 (+10). Real diff **+405 / -27 across 8 files** -- 75 changed lines
of source, 200 of test, 157 of doc.

**The test:source ratio was 2.7:1, and that is the third consecutive item where it landed near
3:1.** S-9's three tests against eight source lines, S-7's suite move, and now this. The board has
already priced two open items this way -- MR 19 #3 (`~-55 source against ~84 test sites`) and
Wave 7's `AdminUserController` extraction (`~200 source lines move, but the test file is the hidden
half`) -- so this is confirmation, not a new correction. Treat any remaining item quoting a
source-only number as understating by roughly 3x unless it says otherwise.

`SessionService.revokeAllForStatus(userId, newStatus)` over a new
`UserSessionRepository.revokeAllForUser`, called from `AdminUserController.updateUser` on the line
directly below `invalidateInvitesForStatus` -- the shape the item specified, and no branching in the
controller (working rule 19).

### The predicate diverges from S-9's, and that was the whole judgement

The item left one thing open: which statuses revoke, with "mirror S-9's `mayAcceptInvite` boundary"
named as the default and any divergence to be argued here. It diverges.

`mayAcceptInvite` is `{INVITED, ACTIVE}`. The session predicate,
`SessionService.mayHoldSession`, is **ACTIVE only** -- because that is what `SessionService.resolve`
has enforced since S-1, and `resolveRejectsSessionWhoseAccountWasReturnedToInvited` was written to
say so deliberately. Mirroring `mayAcceptInvite` would leave an `ACTIVE -> INVITED` demotion holding
live `user_session` rows that can never resolve again -- precisely the rows the item asked to tidy.

So the two sweeps run off two different allowlists, and `INVITED` is the status that separates them:
**an INVITED account may hold a live invite, but may not hold a working session.** Both halves of
that sentence are now pinned by a test that reddens if the other allowlist is substituted.

Same one-definition-two-call-sites discipline S-9 used, though: `mayHoldSession` is called from
`resolve` as well as from `revokeAllForStatus`, so the "may this account hold a session" rule cannot
drift between the read site and the sweep site (working rule 14).

### Working rule 16 applied: three callers, one call site

The item named `updateUser`. Grepping `appUserRepository.updateStatus` rather than trusting that
found three callers, and the enumeration is the reason only one gets the call:

| Caller | Resulting status | Needs the sweep? |
|---|---|---|
| `AdminUserController.upgradeUser` | `PERSON -> INVITED` | No. A `PERSON` row has no password and no login; provably zero sessions to revoke. |
| `UserInviteService.accept` | `-> ACTIVE` | No. `ACTIVE` is the allowlist; the call would be a no-op by construction. |
| `AdminUserController.updateUser` | admin's choice | **Yes.** The only caller that can land on an ineligible status with sessions outstanding. |

Rule 16 says a guard at a read chokepoint covers entry points you failed to enumerate. That still
holds here and is why this stayed LOW: `resolve` rejects all three regardless.

### The cost report the item was owed

The instruction was to leave `AppUserRepository.updateStatus` alone and report what putting the
revocation inside that statement would cost. **Measured, not estimated** -- the experiment was run.

`updateStatus` was rewritten as a Postgres data-modifying CTE, so the revocation really is inside
the one statement:

```sql
WITH u AS (
  UPDATE users SET status = :status, updated_at = now() WHERE id = :id RETURNING id
)
UPDATE user_session SET revoked_at = now()
WHERE user_id = (SELECT id FROM u) AND revoked_at IS NULL
```

Full suite against that: **1,338 run, 1 failure --
`SessionServiceIntegrationTest.resolveRejectsSessionWhoseAccountWasDisabled:132`**, on its
`assertThat(session.getRevokedAt()).isNull()` line.

**That single failure is the cost, and it is larger than one red test.** That assertion is not
incidental; the test's docblock says in as many words that it keeps the session row live -- unrevoked
and unexpired -- so that the status test in `resolve` is the only thing that can reject it, and that
stripping the status test is the mutation it exists to catch. It is the sole mutation-detector for
the S-1 fix. Revoke inside `updateStatus` and the row is dead on arrival, so **the S-1 guard could
be deleted and the suite would stay green.** Working rule 15 is exactly about this: a test that
reports coverage it no longer has is worse than no test.

The rest, in descending order:

| | |
|---|---|
| Policy siting | "Which statuses revoke" moves into a DAO, where nobody reading the admin endpoint can see it, and applies to all three `updateStatus` callers rather than the one that needs it. |
| `accept` ordering | `UserInviteService.accept` calls `updateStatus(userId, ACTIVE)` and *then* `sessionService.create`. An unconditional in-statement revoke is survivable only because the mint happens to come last. A future reorder logs the user out at the moment they finish onboarding. |
| Table ownership | `AppUserRepository` would write `user_session`, a table it does not own. Nothing else in `dao/` writes across tables like this. |
| The non-CTE alternative | Injecting `UserSessionRepository` into `AppUserRepository` -- a DAO-to-DAO dependency with no precedent in this codebase. |

Against that, the thing the in-statement version would genuinely buy: atomicity with the status
write for callers outside a transaction. It buys nothing here, because `updateUser` is already
`@Transactional` and `revokeAllForStatus` joins that transaction.

Working rule 17 says the repair for a bypassed guard belongs in the statement, not the caller's
precondition. It does not apply: nothing bypasses anything here. `updateStatus` is a plain write,
and session revocation is a policy decision about that write, not a missing predicate inside it.

### What it cost where it actually went

| | |
|---|---|
| Source | 3 files. New repository statement (12 lines), predicate + service method (~40 lines with javadoc), one delegating line in the controller. |
| New dependency | One. `SessionService` injected into `AdminUserController` -- a 10th constructor arg, and the one real piece of churn in the MR. |
| Query | One extra `UPDATE`, joining `updateUser`'s existing transaction. Zero rows when there is nothing to revoke. |
| Existing test churn | **Zero assertions changed.** One mock field and one constructor arg in `AdminUserControllerTest.setUp`. |
| New tests | 10 -- 4 repository, 3 service, 3 controller. |

### Mutation results (working rule 15)

| Mutation | Reddens |
|---|---|
| Delete the controller's delegating call | all 3 new `AdminUserControllerTest$UpdateUser` tests |
| Widen `mayHoldSession` to `mayAcceptInvite`'s set | `revokeAllForStatusRevokesOnDemotionToInvited` **and** `resolveRejectsSessionWhoseAccountWasReturnedToInvited` -- the divergence is guarded at the sweep site and the read site independently |
| Drop `user_id` from the `UPDATE` | `revokeAllForUserLeavesOtherUsersSessionsAlone` (this mutation logs out every user on the site) |
| Drop `revoked_at IS NULL` | `revokeAllForUserSkipsAlreadyRevokedSessions` |

All four verified red, then restored with `touch` per working rule 15's second practical note.


## S-5 outcome, 2026-08-24 -- a body with no declared length is refused instead of waved through

Shipped as [#206](https://github.com/themancalledzac/edens.zac.backend/pull/206), merged
2026-08-24. Suite 1,338 -> 1,341 (+3). Real diff **+70 / -11 across 2 files** -- 13 changed lines of
source code, 10 of javadoc, 47 of test.

**Test:source stayed near 3:1 for the fourth item running** -- 47 test lines against 13 of source
code, or 3.6:1. S-9, S-7 and S-8 were the first three. The pattern the S-8 write-up recorded as
"confirmation, not a new correction" now has a fourth data point, and this is the smallest source
change of the four, which suggests the ratio is driven by the guard tests rather than by fix size.

`RateLimitFilter` read `getContentLengthLong()` and compared it to `MAX_PUBLIC_BODY_BYTES`. That
call returns **-1** for a chunked request, and `-1 > 16384` is false, so `Transfer-Encoding:
chunked` was a one-header bypass of the cap: the body went to Jackson bounded only by its 20MB
`StreamReadConstraints`. The fix rejects an undeclared-length body with **411 Length Required**
before the size comparison, inside the same `tryConsume` branch so the rejection still costs the
caller a token.

### The check is not "reject -1", and that distinction is the whole design

A request carrying no body at all also reports -1. `MockHttpServletRequest.getContentLengthLong()`
returns -1 whenever `content` is null, which is every bodiless request in the suite. Keying the
rejection on the missing length alone therefore 411s every GET on a public path -- there are none
today, `MessagesControllerPublic` is the only controller under `/api/public/**` and it is
POST-only, but the filter is keyed on a path prefix and the next public endpoint added inherits
whatever this branch does.

So the guard is `declaredBodyBytes < 0 && request.getHeader("Transfer-Encoding") != null`. For
HTTP/1.1 that is exact rather than approximate: chunked is the only way to send a body without a
`Content-Length`, and it is not legal to do so without the header. HTTP/2 would break the
equivalence -- a DATA-frame body needs no `Transfer-Encoding` -- but http2 is not enabled here
(no `server.http2.*` property, and `TomcatConfig` casts the protocol handler to
`Http11NioProtocol`). **If http2 is ever turned on, this branch stops covering the case it was
written for.** That is the one thing to re-check rather than assume.

### 411, not 413

413 would be a lie. A chunked request may well be under 16KB; what is wrong with it is that the
filter cannot tell. 411 says exactly that, and it tells a legitimate caller what to change.

### The severity call the item made held up

The item downgraded this to LOW on the finding that the BFF sends
`new Uint8Array(await req.arrayBuffer())`, so undici always sets a fixed `Content-Length` and
nothing chunked leaves it. Chunked can only arrive direct-to-EC2, which requires the internal
secret. Nothing in this MR changes that reasoning, and nothing found while doing it contradicts it.
The fix is worth having because the filter now enforces what its own javadoc claims, not because a
live hole was open.

### Working rule 16: one site, and it was checked rather than assumed

`grep -rn "getContentLength"` over `src/main` and `src/test` returns **two hits, both inside
`RateLimitFilter`**, and both were the same expression evaluated twice in the old code (once for
the comparison, once for the log). There is no second body-length reader in the codebase, so unlike
S-1 and S-8 this item's named site really was the only site. Recorded because the rule is worth
running even when it comes back empty -- the empty result is the finding.

### The guardrail held: the three limiter cores were not touched

The item and the user both said to leave MR 19 #3 alone and report the cost instead. It is reported
under that item on the board, re-measured rather than repeated, and it turned up one thing the
board did not have: `RateLimitFilter` needs `bucket.estimateAbilityToConsume(1)` for its
`Retry-After` header, so the `boolean allow(String key)` signature the merge implies does not fit
all three callers.

### Mutation results (working rule 15)

| Mutation | Reddens |
|---|---|
| Delete the 411 branch entirely (the pre-S-5 state) | `chunkedBodyIsRejectedWith411` and `chunkedRejectionStillConsumesTheRateLimitBudget` -- and nothing else, so the two new guards are the only detectors |
| Drop the `Transfer-Encoding` conjunct, leaving `declaredBodyBytes < 0` | `requestWithNoBodyIsNotMistakenForChunked`, plus `firstTwoRequestsPass` and `differentIpsHaveIndependentBuckets` |

The second row is worth reading carefully: **two pre-existing tests already caught the over-broad
fix**, so the new precision test is not the only thing standing between the repo and a 411 on every
bodiless public request. It was kept anyway because those two tests are named for rate limiting and
would send a future reader hunting in the wrong place. Both mutations were restored with `touch`
per working rule 15's second practical note.


## S-6 outcome, 2026-08-24 -- an admin stops being bounced, and the sweep found a sixth site

Shipped as [#207](https://github.com/themancalledzac/edens.zac.backend/pull/207), merged
2026-08-24. Suite 1,341 -> 1,347 (+6), stacked on S-5. Real diff **+315 / -83 across 21 files** -- 11 source, 10
test. This closes the security board.

**The item said its scope was wider than two methods, and it was right by exactly one site.** It
called for enumerating before fixing (working rule 16, pointed at a policy rather than a guard). The
enumeration found **six** places an admin is denied, not the two `CollectionAccessService` methods
the item named. The sixth -- `UserSavesService.add` -- was in no item on this board.

### The six sites

| # | Site | What an admin got | Why the flag was lost |
|---|---|---|---|
| 1 | `CollectionService.isGalleryAccessAuthorized` | password prompt on any protected gallery | read `CurrentUser.userId()` |
| 2 | `ContentDownloadControllerProd.isDownloadAuthorized` | 401 on download | read `CurrentUser.userId()` |
| 3 | `UserSelectsService.requireCollectionAccess` | 403 starring an image | service took a bare `Long userId` |
| 4 | `UserRatingOverrideService.upsert` | 403 rating an image | service took a bare `Long userId` |
| 5 | `UserSavesService.add` | **404** saving an image | `ContentRepository.isImageVisibleToUser` SQL has no `is_admin` term |
| 6 | `UserShareControllerProd.addCollection` | 403 opting into their own share | had the principal and called `.userId()` on it |

Sites 1 and 2 are the two the item described in prose. Sites 3, 4 and 6 are the same root cause one
frame up. Site 5 is the one nothing had recorded, and it is the odd one out twice over: the denial
is a 404 rather than a 403, and the check lives in SQL rather than in `CollectionAccessService`.

### Site 5's fix sits above the SQL, deliberately

`ContentRepository.isImageVisibleToUser` is `LISTED OR role grant`. Adding an `is_admin` term to that
statement was the obvious move and is the wrong one: the query filters on several read paths, not
only this authorization decision, so an identity rule inside it would apply in places nobody
checked. The bypass is a `!principal.isAdmin() &&` in `UserSavesService.add` instead. Non-admins take
the identical query they always did, so the deliberate 404-not-403 choice there (no enumeration
oracle) is untouched.

### The trap: routing `canView` through `effectiveLevel` also routes the share branch

The item specified "route `canView` and `isClient` through `effectiveLevel`". Done literally that is
correct for `isClient` and **widens `canView`**. `effectiveLevel` adds two branches, not one: the
admin sentinel and the share branch. A share-link holder resolves GENERAL, so:

- `isClient` through `effectiveLevel` -- a flyby is capped at GENERAL, `GENERAL.atLeast(CLIENT)` is
  false, nothing changes. Safe.
- `canView` through `effectiveLevel` -- a flyby returns **true**, and `isGalleryAccessAuthorized`
  would have accepted a share link as an alternative to the gallery password prompt.

Nobody asked for that. The two gallery gates screen with `AuthPrincipal.isRealUser` before asking,
which reproduces exactly what the old `userId != null` did and holds the change to the admin
sentinel alone. `sharePrincipal_isNeverAsked_andStillFacesTheCookieGate` is the guard, and it
reddens when the screen is removed.

**This is the fourth item in a row whose specified fix needed adjusting at implementation time** --
S-7's `INVITED`-only allowlist, S-8's predicate divergence, S-5's bare `length < 0`, now this. Four
for four is no longer a run of luck. Treat the fix text in a board item as a hypothesis to test
against the code, not a specification to type in.

### The SQL was checked before the swap, not after

`canView` == `hasAtLeast(GENERAL)` and `isClient` == `hasAtLeast(CLIENT)` had to hold for a session
principal or the swap would have changed more than intended. Both do, for reasons worth writing
down. `RoleRepository.canView` counts rows in `role_member JOIN role_collection`; `highestLevel`
takes the max level over the same join. GENERAL is rank 0, the floor, so "holds any grant" and "at
least GENERAL" are the same question. `isClient` counts rows with `rank >= CLIENT.rank`, and a max is
`>= CLIENT` exactly when some row is. Same join, same answer.

### What was left alone, and why

Four sites scope a **list** rather than deny a request. An admin sees a shorter list; nothing 4xx's.

- `UserShareControllerProd:133` and `UserPageAssembler:64` -- both scope to
  `memberCollectionIdsForUser`. The `/user` page is "your galleries", not "all galleries", and a
  share picker offering an admin every collection on the site is a worse default than a short list.
- `ContentRepository.findSavedImagesByUserId` -- re-applies the LISTED-or-grant filter on read, so an
  admin's saved image drops out of their list if it stops being LISTED. Arguably wrong, but a filter,
  not a denial.
- `CollectionService.isChildExcluded` -- a `private static` with no principal argument, dropping
  HIDDEN children from parent responses. Reaching it needs a signature change for a case nobody has
  hit.

Recorded so the next person does not re-derive them. Working rule 20 is about an admin being
**bounced**; list scoping is a different question and that ruling did not settle it.

### Mutation results (working rule 15)

| Mutation | Reddens |
|---|---|
| `canView` back to `roleRepository.canView(principal.userId(), ...)` | `adminSatisfiesCanViewAndIsClientWithNoGrantAtAll`, `canViewResolvesThroughEffectiveLevel`, `shareHolderCanViewButNeverCountsAsClient` |
| Drop the `isRealUser` screen in `isGalleryAccessAuthorized`, leaving a bare null check | `sharePrincipal_isNeverAsked_andStillFacesTheCookieGate` |
| Drop `!principal.isAdmin() &&` in `UserSavesService.add` | `addSkipsTheVisibilityCheckForAnAdmin` |

All three verified red, then restored with `touch` per working rule 15's second practical note. The
two gate tests capture the argument and assert `isAdmin()` on it rather than stubbing an exact
principal: the failure being guarded is a **stripped** principal reaching the check, and an
exact-match stub cannot tell that apart from no call at all.


## isPasswordProtected outcome, 2026-08-24 -- a locked tile can finally be drawn

Shipped as [#209](https://github.com/themancalledzac/edens.zac.backend/pull/209), squash `a6550b0`.
`ContentModels.Collection` carries an `isPasswordProtected` component, serialized under exactly that
name. The frontend's C6 is unblocked. Java-only **+9 / -3** in `src/main`, plus 5 tests.

### Working rule 21 on its first outing: "four builders" was two, and the two that mattered were not builders

The item prescribed "add an `isPasswordProtected` component to `ContentModels.Collection` and
populate it where the four content-block builders construct one". Grepping construction rather than
builders gives a different list:

| Site | Kind | Populated from |
|---|---|---|
| `ContentModels.Collection.fromCollectionModel` | construction | `CollectionModel.getIsPasswordProtected()` |
| `ContentModelConverter.buildCollectionRecord` | construction | `referencedCollection.getGalleryPassword() != null` |
| `Collection.withTags` | **copy** | threads the component |
| `Collection.withOrderIndex` | **copy** | threads the component |

The item's four "builders" are `SyntheticCollectionResolver`, `TagViewResolver`, `UserPageAssembler`
and `ContentModelConverter` -- but the first three all call the same static factory, so there is one
site behind three of them. That miscount is harmless. The copy methods are not.

`withTags` runs on the synthetic-list path **immediately after** `fromCollectionModel`
(`SyntheticCollectionResolver:108`). A record copy that omitted the new component would compile,
serialize, and read `false` on `all-collections` and `all-blogs` while reading correctly on every
other path -- so the flag would be wrong on precisely the list the frontend asked for it for, and
right everywhere a spot-check would look. This is rule 21's "ask what inputs the item did not
enumerate", where the unenumerated input is a *method*, not a caller. The precedent already existed
one test away: `resolveAllCollectionsKeepsVisibilityThroughTagEnrichment` was written for the same
hazard on `visibility`.

Rule 14's corollary applies verbatim here and is worth restating in record terms: **on a record, the
`with*` copy methods are construction sites.** A grep for `new ContentModels.Collection(` finds them;
a grep for "builders" does not.

### The nullability the item did not mention

`CollectionModel.isPasswordProtected` is a `Boolean`, not a `boolean`, and `CollectionModel.builder()`
leaves it null. `SyntheticCollectionResolverTest` builds models exactly that way. So
`fromCollectionModel` reads it through `Boolean.TRUE.equals(...)`; plain unboxing would have NPE'd on
the first anonymous request to a synthetic list. The guard test covers both a set flag and a null one
in one assertion, which is why it is `containsExactly(true, false)` rather than a single value.

### The data was checked at every path rather than assumed

The flag is only as good as `gallery_password` being loaded. Verified:

- Every query feeding `batchConvertToBasicModels` and `findByIds` runs through `COLLECTION_ROW_MAPPER`,
  whose canonical `COLLECTION_COLUMN_NAMES` list includes `gallery_password` and whose mapper sets it.
  That covers `findNonEmptyListedOrOwnedOrderByDate`, `findNonEmptyOrderedByVisibilityIn`,
  `findClientGalleriesAndQualifyingParents` and `findCollectionsByTagId`.
- `ContentCollectionEntity.referencedCollection` is an **id-only stub** -- `ContentRepository:143`
  sets nothing but the id. Reading `getGalleryPassword()` off it returns null for a protected gallery.
  Both callers hydrate first: the singular path refetches on a null title, the batch path substitutes
  from `referencedCollectionsById`. `buildCollectionRecord`'s docblock now says so, because that is a
  warning about a line and not a fact about the method (working rule 12).
- The one narrow projection that omits `gallery_password`, `findCollectionListEntries` at
  `CollectionRepository:606`, does not build content blocks. It backs
  `GET /api/admin/collections/metadata`.

### The cost report the guardrail was owed

The guardrail named two adjacent changes and said to leave both alone and write down what they would
take. Both are worse than the item's framing suggested.

**A `gallery_password` filter on the read queries does not merely break a contract -- it empties a
list.** `findClientGalleriesAndQualifyingParents` backs the `all-client-galleries` synthetic slug and
selects `is_client = true` plus derived parents. Client galleries *are* the password-protected work.
Filtering protected rows out of the read queries removes the entire content of that list, and the
wedding-wrapper parents with it. The item costed this as a contract break with the frontend; the
contract break is real but secondary. Two further costs: the frontend has already scoped its half of
C6 against a serialized flag, and the nested case already has a boundary --
`CollectionService.filterNonListedChildCollections` drops a protected child from an unprotected
parent -- so a query-level filter would be a second enforcement point for a rule that already has one.

**Stripping `coverImage` reddens tests that were written on purpose and splits list from detail.**
Three tests assert the cover IS returned: `getCollectionBySlug_protectedNoCookie_retainsCoverImage`
and its invalid-cookie and valid-cookie siblings. The detail response returns the cover for a
protected gallery today, so stripping it on list paths only would make the two disagree about the
same collection. What is actually withheld from an unauthorized viewer is `content` and
`contentCount`, which those same tests pin.

**The exposure both changes were aimed at stays latent, not live.** It needs a collection that is
simultaneously LISTED and password-protected. Prod convention keeps protected work UNLISTED, and
nothing enforces that -- which is the honest residual, unchanged by #209. #209 makes the state
*visible* to the client rather than impossible, which is what the frontend asked for.

### The second stale comment, and why it is worse than the banner

The item sent this MR to fix one stale comment: the BE-H5 section banner reading "coverImage must be
stripped", sitting above three tests named `...retainsCoverImage`. That was fixed, and the
replacement says what is actually withheld and records that the old text crossed a repo boundary.

Writing the cost report turned up a **second** one in the same file, and it is the worse of the two.
`CollectionControllerProdTest` has a section headed "Fix 1: coverImage stripped for protected
CLIENT_GALLERY on list endpoints", whose test comment names the stripper:
`CollectionProcessingUtil.buildBasicModel`. That method sets `coverImage` unconditionally. The test
hand-builds a model with `coverImage(null)` and mocks `CollectionService`, so it asserts controller
pass-through and could not fail if stripping were added, removed, or never written.

Two independent comments in one file claiming the same protection, neither true, is what made the
frontend's Option B premise false **in both halves** rather than just one. That produced **working
rule 22**. The row is left open under "Carried forward" rather than fixed here: it sits inside the
guarded area, and deciding whether the section is a stale record or an unimplemented specification is
work, not a comment edit.

### Mutation results (working rule 15)

| Mutation | Reddens |
|---|---|
| Drop the component from `Collection.withTags` | `resolveAllCollectionsKeepsPasswordProtectedThroughTagEnrichment` |
| `fromCollectionModel` passes `false` | same test |
| `buildCollectionRecord` passes `false` | `buildCollectionModelWithBatchData_reportsPasswordProtection` |

All three verified red, then restored. **A restore lesson worth recording**: the first attempt used
`git checkout` on a file holding uncommitted work and destroyed the whole change, not the mutation.
Working rule 15's practical notes cover `touch` and stale classes but assume the change is committed.
It is: **commit the MR before mutating it**, then `git checkout` is a safe restore.

`buildCollectionModelWithBatchData` had **no test coverage at all** before this -- zero references
across `src/test`. The two tests added for the flag are the first tests that method has ever had.

### #210 is the natural next item, and it is already open

The `CurrentUser` fold was the warmer item all along and was passed over only because another team
was blocked on this one. It is now open as
[#210](https://github.com/themancalledzac/edens.zac.backend/pull/210), rebased onto `a6550b0`.
After that, the `share/email` 404 is the last cross-repo item anyone is waiting on.

## CurrentUser fold outcome, 2026-08-24 -- the MR 15 #6 thread closes four sessions later

Shipped as [#210](https://github.com/themancalledzac/edens.zac.backend/pull/210), squash `c1f482e`.
Java-only **-11 / +4**, behavior-preserving, no test changes.
`SyntheticCollectionResolver.currentPrincipal` deleted, `CollectionService.viewerMaySeeHidden`
delegates to `CurrentUser.principal()`, both files drop their `SecurityContextHolder` import.

### The estimate was right for once, and the reason is worth keeping

This item was written expecting to build the shared helper. By the time it was picked up, S-6 had
already added `CurrentUser.principal()` for its own reasons, so what remained was delete-and-delegate
and the MR came in almost exactly as re-scoped. **The item got cheaper while sitting still**, which
is the opposite of the usual direction on this board and the payoff for re-verifying an item's scope
at pickup rather than trusting the version written when it was filed.

### Coverage proven, not assumed

Working rule 15 applies to refactors too, in a weaker form: the risk is not a fix that cannot fail
but a delegation nothing exercises. Replacing each call with a hard-coded `null` reddens **4 errors
in `SyntheticCollectionResolverTest`** and **3 in `CollectionServiceTest$EnforceVisibilityVisibilityRules`**,
so both folded sites are under real coverage.

### The grep that defines "done"

`getContext().getAuthentication()` across `src/main` now returns **four** sites, down from six.
`CurrentUser` is the consolidated one; the other three are not copies and none extracts a user id --
`CollaboratorAccessInterceptor` resolves an access level, `FlybySessionFilter` tests whether an
authentication already exists, `AuthController` serves `/api/auth/me`. That grep is the item's
completion condition and it is now satisfied. Do not re-open this on a future sweep.

### What the close-out that followed got wrong

#211 reported the merge neighborhood clean after checking whether #209's diff had moved any cited
ref. It had not. But five refs into those same files were **already** wrong from earlier drift, and
the next sweep found all five. That produced **working rule 23**: "my merge did not move this ref"
is not "this ref is correct", and a sweep reporting zero corrections is evidence of the wrong
question rather than an accurate board.

## `share/email` outcome, 2026-08-24 -- the first item that could not be built as written

**Shipped:** [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213). Closes the live
404 the frontend has been hitting since its PR #251 merged.

### The specification was complete and unbuildable

The item named the files, the method, and the response type: "one `@PostMapping` on
`UserShareControllerProd`, one `sendShareLinkEmail` method on `EmailService` alongside the two that
exist, one request record. No new response type." It was chosen as next because that scope looked
small.

The endpoint would have had **nothing to put in the email**. `emailShareLink` sends `{ toEmail }`
and nothing else, and V56 stored only `token_hash`, so the share URL cannot be reconstructed
server-side. `ShareSettings.token` said as much in its own javadoc: "the raw value is unrecoverable
once issued".

Only two ways out existed, and the item forbade one of them. Rotating on send would have revoked
the link every prior recipient holds -- silently, since the sender sees `sent: true` either way.
The other was storing a copy the owner can read back.

**The frontend had already made that choice and written it down.** Its `ShareSettings.token`
docblock reads *"Null when it cannot be recovered -- a link minted before the backend stored a
decryptable copy"*. That is not a hypothetical; it is the contract of `fec14e7`, a commit written
2026-08-14 and orphaned when it landed 14 minutes after its PR merged. The frontend shipped against
a backend design that never got merged, and the board recorded the resulting 404 without noticing
why the two sides disagreed.

This is working rule 24. Verifying an item from both sides of a repo boundary -- which this one had
been -- checks the **output** end. Nobody asked where the link itself comes from.

### What shipped

`fec14e7` rebased onto `04c0c2d`. V58 adds `token_cipher`; `token_hash` keeps its job as the unique
lookup index, so `resolveByRawToken` is untouched. AES-256-GCM keyed on the existing
`app.access-token.secret` -- **no new env var**. `TokenCipher` sits alongside `TokenUtil`, and the
split is the point: hashing is right when the server only needs to RECOGNISE a credential,
encryption when its owner needs to READ it back.

The guardrail held. `revealToken` is a read; `mintOrRotate` is never called and a test pins it. An
unrecoverable link is a 409, not a silent no-op. No `afterCommit` hook, unlike the invite flow,
because nothing is written and a rollback cannot erase the token.

### Three adaptations the orphaned commit could not have known about

All three trace to MR 15 #6 ([#191](https://github.com/themancalledzac/edens.zac.backend/pull/191))
landing after it.

1. Its migration was numbered V57, which `main` now uses for `lowercase_text_format_type`.
   Renumbered to V58, along with every V57 reference in the share files.
2. Its `emailLink` opened with an `isRealUser` guard, and it carried a test pinning a 401 from every
   share route. #191 moved that enforcement into `SecurityConfig`'s `/api/read/user/**` matcher and
   **deleted exactly those assertions** -- 28 of them across six test classes. Applying the commit
   verbatim would have reinstated a pattern this repo removed on purpose. Both dropped;
   `/share/email` added to `UserRoutesAuthorizationWebMvcTest`, where the rest of the surface lives.
3. `frontendBaseUrl` was a `@Value` field. Converted to a constructor argument matching
   `AdminUserController`, since CLAUDE.md forbids field injection.

A cherry-pick that applies with two cosmetic conflicts is not a cherry-pick that is correct. The
conflicts were in javadoc; every real adaptation was in code that merged cleanly.

### What changing the lifecycle would do -- asked for, and not done

Rotating on send revokes every prior recipient's link **and** every cookie minted from it, since the
flyby cookie value IS the raw token. One row per user, rotation in place, so opt-ins survive and
access does not.

What did change is storage, not lifecycle. `share_link` stops being useless on its own: a database
dump **plus** the configured `ACCESS_TOKEN_SECRET` now yields live share links. Encrypted rather
than plaintext because the key lives in configuration, so a dump alone still yields nothing -- the
property hashing was there to provide.

**One deploy consequence.** Rotating `ACCESS_TOKEN_SECRET` no longer affects only gallery HMAC
tokens. Share links keep resolving, because lookup is still by hash, but every stored token becomes
unreadable: every owner's page falls back to "reset to get a new link" and `/email` 409s. The same
applies to `share_link` rows that already exist, which have no ciphertext. Degradation, not
breakage, by design.

Unchanged: mint/rotate semantics, the GENERAL ceiling, scope resolution, cookie lifetime.

### Deliberately not done

The three email senders stay duplicated rather than folded into a shared template -- that is MR 24,
and the bodies make opposite promises (an invite works once and expires; a share link works until
its owner resets it), so they must not be made to mirror each other.

**`/share/email` has no rate limit.** Flagged in `fec14e7`'s own message and still true. Blast
radius is small -- invite-only accounts, and the address is logged while the link never is -- but a
per-user limiter along the lines of `AuthLoginLimiter` is the right next step before this sees real
use.

### Verification

1,361 tests, 0 failures, 0 checkstyle violations. Three mutations verified red: sending a freshly
minted link, dropping the trailing-slash strip, and returning the ciphertext undecrypted.

### Ref drift

`EmailService:56` -- the board's only line ref into any touched file -- is correct on `main` today
and becomes `:61` once #213 merges.

---

## Actuator outcome, 2026-08-24 -- the guarantee tested rather than the string

**Shipped:** [#214](https://github.com/themancalledzac/edens.zac.backend/pull/214).

The backend was already sound and had been verified by live probe: exposure was `include=health`
only, and `InternalSecretFilter` 403s everything but the three health URIs. The frontend has closed
its `/api/proxy/actuator/**` side. This is a third layer.

It is worth having because the first two are code and this is configuration. Working rule 1 says an
injected env var outranks a property, so `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=*` in a deployed
`.env` would register `/actuator/env` and `/actuator/configprops` with nothing in the repo to
notice. And nothing anywhere asserted actuator exposure at all.

`management.endpoints.web.exposure.exclude=env,configprops,beans,mappings,heapdump,threaddump,loggers,shutdown`
-- everything that dumps configuration, dumps process state, or mutates the running app.

### Where it went past the item, and why

The item asked for a test pinning the shipped value, read from `src/main/resources` per working rule
2. That test exists (`ActuatorExposureTest`) and it is not sufficient. It proves the property is
present, which is not the claim the hardening makes. The claim is that **Boot applies exclude after
include** -- an ordering this board quoted and nobody here had executed.

`ActuatorExposureEndToEndTest` boots the app with `include=*` on top of the shipped exclude. The
eight endpoints 404; `/actuator/health` still 200s. The ordering holds.

The mutation is what makes it worth keeping: empty the exclude, and `/actuator/env` answers **200**
on the app port. The assertion distinguishes the two worlds instead of passing in both -- which is
rule 15's complaint applied to config, and a string-equality test on a property file is the easiest
place to land the shape rule 15 warns about.

That generalises, and it is working rule 25.

### Verification

1,357 tests, 0 failures, 0 checkstyle violations. Two mutations verified red: deleting the shipped
exclude line, and running the end-to-end test with an empty exclude.


## MR 19 #16 outcome, 2026-08-25 -- the suggested clause was the bug

**Shipped:** [#216](https://github.com/themancalledzac/edens.zac.backend/pull/216).

### The N+1, as described

`findCurrentContentCollections` read every join row in the parent, then asked the database about
each one individually. `SELECT_CONTENT_COLLECTION` inner-joins `content_collection`, so every image
in the parent bought an empty result: removing one sub-collection from a 200-image collection issued
**201 queries, 200 of them answering nothing**. Admin write path, not public reads, which caps it.

Replaced by one query in `ContentRepository`. The matching moved into SQL and the service kept only
its logging.

### Where the item was wrong, and why it would not have been caught

The item proposed `cc.id IN (:ids) OR cc.referenced_collection_id IN (:ids)`. That clause alone
**drops the parent scope**, which the loop had for free by construction -- it iterated one parent's
join rows, so scoping was structural rather than expressed.

Typed verbatim, it matches blocks linked under a different parent. The damage is not a bad read:
`removeContentFromCollection` is parent-scoped and would delete nothing. But `onChildUnlinked` fires
off the same result list, so **role-grant propagation would run for a parent-child link that never
existed** -- invisible in the response, surfacing later as someone's access.

It would also have passed review and passed a mocked test. `CollectionServiceTest` stubs the
repository, so the service test proves the call happens once and can say nothing about which rows
come back. That is why the new coverage is an integration test against real Postgres, and why the
mutation matters: drop the `collection_content` join and
`doesNotReachIntoADifferentParentsLinks` goes red. Three mutations verified red in total -- the
parent scope, matching only on the block id, and adding a `visible = true` filter.

No `visible` filter, deliberately: the replaced loop had none, and an unlink has to reach a hidden
link. The mutation pins that too, which is the only reason it counts as a decision rather than an
omission.

### Estimate versus actual

"Test coupling is one mock line" -- it was two stubs
(`findContentByCollectionIdOrderByOrderIndex` and `findCollectionContentById`), both in
`removesCurrentFromEachRemoveIdParentChildren`. Small in absolute terms, but the failure mode is the
one rule 21 names: **the item counted the call it was thinking about and not the ones around it.**
Other stubs of those two methods elsewhere in the suite belong to unrelated paths and were untouched
-- which is exactly why the count has to be derived rather than recalled.

Net diff: +38 source in `ContentRepository`, -25 in `CollectionService`, one dead import, one dead
test fixture, +7 integration tests.

### What it made deletable, and what was deliberately left alone

`findCollectionContentById` now has exactly one caller left, `ContentModelConverter`. **MR 19 #14
removes that one**, at which point it and `findTextById` both drop to zero callers. Left in place
here on purpose: a repository deletion is Wave 1 work and does not belong riding along on a query
fix. The guardrail is written into #14 rather than left as a note.


## Wave 4 retro — measured in words, 2026-08-23

Prompted by a fair challenge: the MRs kept being called "debloat" while the diffs looked
net-positive. Both halves of that turned out to matter, and only one was a real problem.

The stats quoted in these PRs were commit-level, which mixes this tracker's running log in with the
code. 13b's headline `+161/-53` was 88 lines of doc and +20 of Java. **Quote Java-only stats for a
code MR.** Then, separately, the Java itself has to be measured in words, because javadoc's `/**`,
` * ` and `<p>` scaffolding counts as content and hides prose growth in the line count:

| MR | Java lines | words of prose | |
|---|---|---|---|
| 12a ([#177](https://github.com/themancalledzac/edens.zac.backend/pull/177)) | -25 | **-378** | |
| 12b ([#178](https://github.com/themancalledzac/edens.zac.backend/pull/178)) | -37 | **-301** | |
| 12c ([#180](https://github.com/themancalledzac/edens.zac.backend/pull/180)) | -1 | **-207** | |
| 13a ([#181](https://github.com/themancalledzac/edens.zac.backend/pull/181)) | +2 | **-75** | line count up, prose down |
| 13b ([#183](https://github.com/themancalledzac/edens.zac.backend/pull/183)) | +20 | **+178** | the only inflation |
| 13c ([#184](https://github.com/themancalledzac/edens.zac.backend/pull/184)) | -24 | **-192** | the correction |
| 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) | +4 | **-51** | lines up, prose down -- the 13a pattern again |
| **Wave 4** | **-61** | **-1,026** | 500 in-method comments removed; 67 remain, 66 by decision |

So the trend was sound and 13b was the outlier, now corrected. Two things worth carrying forward.
13a shows the divergence without the defect -- **+2 lines but -75 words** -- which is proof the line
count is simply the wrong instrument, not just that 13b was sloppy. And 12a/12b, the two biggest
sweeps, produced the two biggest prose reductions, so scale is not what causes inflation. Care is.
Working rule 10 has the check.

## MR 14 — Comment debloat: controllers, config, data layer — DONE ([PR #187](https://github.com/themancalledzac/edens.zac.backend/pull/187))

93 in-method comments re-derived on `51fede9` and dispositioned: **7 deleted, 19 promoted, 66 kept
inline, 1 quarantined.** Java-only **+4 lines / -51 words** -- the 13a pattern, line count up and
prose down. Seven files went to zero; 20 files became 13. Full write-up in the
[history file](2026-08-22-backend-cleanup-history.md#mr-14-outcome-2026-08-23).

The headline: **the wave rule did not fit this population**, and that is now working rule 12. Waves
1-3 hardened exactly the `controller/`, `config/` and `dao/` files MR 14 targets, so most of these
93 were written deliberately by this cleanup effort and guard a specific line rather than narrating
the code. Only 7 restated the code they sat on.

`SecurityConfig`'s 24 were measured as the guardrail asked, and kept: promoting them into one
`filterChain` docblock costs **+24 words (+9%)** even in a careful draft that states each rule once.
The entire overhead is anchor-naming -- a docblock has to write "on `/api/auth/me`,
`/api/auth/logout`, ..." for what position gives an inline comment free.

**Bug #16 is still real.** Verified against current `updateImages`: `ContentRepository.saveImage` is
still a single-row INSERT/UPDATE, the loop still calls it once per image, and the log line still
reads "Batch saved {}". The only `batchUpdate` in `ContentRepository` is in `saveContentPeople`, a
different table. So N image edits still issue N statements. `ContentService:227` stays quarantined
until the MR that adds a real batch save and fixes the log line with it.

Two doc claims shipped, both re-verified first. `CollectionVisibility`'s docblock said
`password_hash`; V18 renamed the column to `gallery_password`. `.claude/CLAUDE.md` claimed
`controller/prod/` is `@Profile("prod")` gated; it is not. On that second one the tracker was right
and a naive grep is not -- the only two `@Profile` hits under `controller/` are javadoc text saying
there is no such gating.


## S-10 outcome (2026-08-25) -- redemption-time identity, and the narrowing cost report

Shipped as [#221](https://github.com/themancalledzac/edens.zac.backend/pull/221). +111/-11 across
four files. Suite 1,377 -> 1,381, checkstyle and spotless clean.

### What shipped

One named predicate on `UserInviteService`, beside `mayAcceptInvite`:

```java
static boolean inviteAddressMatchesAccount(UserInviteEntity invite, AppUserEntity user) {
  return user.getEmail() != null && user.getEmail().equalsIgnoreCase(invite.getEmail());
}
```

tested in `accept` immediately after the status test. Three decisions inside those two lines:

- **After `redeem()`, not before.** The existing status rejection deliberately spends the token
  before refusing, so a link presented against an ineligible account is burnt rather than left live
  for a second attempt. A stale-address token deserves the same treatment for the same reason.
- **`equalsIgnoreCase`.** Every write path lowercases before storing (`createUser`, `upgradeUser`
  and `updateUser` all call `.toLowerCase()`), so a case difference can only come from a row
  predating that. It is not an identity change, and a case-sensitive comparison would refuse a real
  invitee -- working rule 18's failure-closed shape, which surfaces weeks later as "reset is broken".
- **Null guard on the account side only.** `user_invite.email` is `NOT NULL` in V32, so the invite
  side cannot be null. `users.email` has been nullable since V35 relaxed it for tag-only PERSON rows.
  `String.equalsIgnoreCase(null)` returns false rather than throwing, so only the receiver needs the
  guard.

### The narrowing cost report the guardrail asked for

The guardrail quarantined `mayAcceptInvite` and asked what dropping ACTIVE back out of it would
cost. Six costs. The third is the one that makes the report worth having.

1. **It removes the only password reset in the repo.** `grep -rni "password-reset\|forgot"` across
   `src/main` returns two javadoc mentions and no route. `POST /api/admin/users/{id}/invite` is the
   entire mechanism; there is no self-service flow to fall back to.
2. **It fails silently, at the invitee.** `AdminUserController.regenerateInvite` has no status gate,
   so it would keep returning `200` with an invite URL for an ACTIVE account. The admin sends the
   link believing it works; the user hits `410 Gone` days later. Making the narrowing fail loudly
   means also gating that endpoint -- work the narrowing does not include, and which is now filed
   separately as **S-21**.
3. **It silently changes a second method.** `invalidateInvitesForStatus` sweeps when
   `!mayAcceptInvite(newStatus)`. Narrow the predicate and `invalidateInvitesForStatus(id, ACTIVE)`
   starts sweeping, so **every** admin PATCH that sets status ACTIVE kills that account's outstanding
   invites -- including a reset link issued moments earlier, and including the ordinary "restore a
   disabled account" flow. This is a behavior change in a different file, reachable from a different
   endpoint, and **nothing in S-10's write-up points at it.** It is working rule 29's first case.
4. **Test cost.** `activeUserAcceptsForPasswordReset` and
   `invalidateInvitesForStatusLeavesEligibleAccountsAlone` both redden. The first exists specifically
   to catch this narrowing -- it is S-7's own guard against being reverted.
5. **Doc cost.** `SessionService.mayHoldSession`'s docblock states it is "deliberately narrower than
   `UserInviteService#mayAcceptInvite`, which also admits `INVITED`". Narrowed, the two sets become
   equal rather than nested and that paragraph stops being true.
6. **It does not close the class of bug.** `accept` would still redeem an INVITED account's invite
   against an address it was never issued to. The targeted sweep in `updateUser` covers that path
   today, but as a caller precondition rather than a guard at the operation -- working rule 17's
   shape, and the reason the fix belongs at redemption regardless of what the predicate says.

**Not verified, and it changes only how loudly the narrowing fails, not whether it fails:** whether
the frontend admin UI exposes a reset button and what it renders on a `410`. There is no frontend
checkout on this machine.

**Conclusion: narrowing is the wrong seam.** The eligibility predicate answers "is this account alive
enough to redeem an invite". S-10 is a question about *which* invite, which is identity, not
eligibility. Working rule 20's trap is adjacent and worth restating: rule 20 does not license
loosening status allowlists, and this report does not license tightening one either -- the allowlist
was never the defect.

### Why widening the `AdminUserController` sweep was also refused

The second half of the guardrail. Firing the sweep on every email change regardless of status looks
like the same fix. It closes this instance and leaves `accept` willing to redeem an invite against an
address it was never issued to, so it protects exactly the paths someone remembered to enumerate.
Working rule 16's principle: a guard at the read chokepoint covers entry points you failed to list;
a guard at one entry point covers that entry point.

### Comments corrected, and why that was not scope creep

Two comments asserted "an ACTIVE user has no pending onboarding invite to hijack" -- in
`AdminUserController.updateUser` and in
`AdminUserControllerTest.changingActiveUserEmailDoesNotTouchInvites`. S-7 falsified both when it
widened `mayAcceptInvite`, and the first **is the premise S-10 is built on**. Neither behavior
changed. Working rule 22 is the argument for fixing them in the same MR rather than filing them: a
stale comment about a *protection* does not get corrected by the next reader, because nobody
re-derives a guarantee they have been told already holds.

### Mutation evidence, stated precisely

TDD, RED first. On unfixed `main`, three of the four new tests fail:
`inviteIssuedToAnAddressTheAccountNoLongerHoldsIsRejected`, `aRejectedStaleInviteIsStillSpent`,
`anAccountWithNoEmailIsRejected`. The fourth, `anInviteAddressDifferingOnlyInCaseStillAccepts`,
**passes both before and after by design** -- it guards the direction the fix must not break, and it
is not a regression detector for the fix itself. Recorded here rather than left to be inferred from a
test count, because working rule 15's whole complaint is about tests that report coverage they do not
have.

## S-11 outcome (2026-08-25) -- the guard clause, and a fact the board already had

Shipped as [#222](https://github.com/themancalledzac/edens.zac.backend/pull/222). +97/-11 across
three files. Suite 1,377 -> 1,381.

One clause in `ProdSecretGuard` mirroring the `internal.api.secret` clause (null, blank, or the known
dev default), plus an `ACCESS_TOKEN_SECRET` block in `.env.example` naming what the value protects
and what rotating it costs.

**The compose default was kept deliberately.** Removing `:-dev-access-token-secret` leaves the
variable set-but-empty inside the container rather than absent, so `TokenCipher` would derive its key
from `sha256("")` -- more predictable, not safer. The startup guard is the seam; compose is not.

**The second consumer was already on the board.** S-11's severity paragraph traced `TokenCipher`
alone. `ClientGalleryAuthService` uses the same value as the HMAC key for `generateAccessToken`,
`generatePasswordAccessToken` and `passwordFingerprint` -- and the tracker's own "Unsettled" bullet on
rotation had listed all three uses since it was written. This was a section-integrity failure, not a
research gap, and it is half of working rule 29.

What it adds to the severity, in both directions so neither half gets re-derived:
`passwordFingerprint`'s docblock claims the fingerprint "is not derivable without the server secret",
and the `gallery_access_pw_<fingerprint>` cookie **name** carries that fingerprint -- so a known key
turns an observed cookie name into an offline dictionary attack on the gallery password. It is **not**
a forgery bypass: both validators recompute the expected HMAC from the gallery's *stored* password,
so minting a valid token still requires knowing that password.

**Mutation evidence.** Deleting the new clause reddens all four new tests, including
`Wiring.prodRefusesToStartOnTheDefaultDevAccessTokenSecret`, which boots a real prod context via
`ApplicationContextRunner` so the container rather than a reflective call runs the check -- the exact
gap S-4 was opened to close. The two pre-existing wiring tests now supply a real access-token secret,
so each still fails only for the reason it names. Source restored with `touch`, per working rule 15's
note on stale `.class` files.

## S-15 outcome (2026-08-26) -- the missing method, and why the existing sweep could not be reused

**Shipped** as [#224](https://github.com/themancalledzac/edens.zac.backend/pull/224), +112/-2 across
four files. Suite 1,385 -> 1,388.

### What shipped

`SessionService.revokeAllForUser(Long)`, a public pass-through over the repository method that
already existed, plus one call in `UserInviteService.accept` before the mint. `revokeAllForStatus`
now delegates to it so "revoke all" keeps one definition (working rule 14).

The doc's 2026-08-25 correction was right and was the whole reason this item was cheap: the item's
prescribed `revokeAllForUser` did not exist on the service. Had that correction not been made, the
session would have opened by typing a call to a method that does not compile.

### Three choices worth carrying forward

The revoke runs **before** `create`. Only sessions live at the moment of the call are affected, so
the fresh session survives; revoke after the mint and the user is logged out by their own password
reset. Both the mock test and the DB test assert the ordering rather than just the calls.

The revoke sits **below** the status and address guards. A token refused for either reason must not
end a live session -- otherwise a stale link becomes a way to log somebody out without redeeming
anything. That is `aRejectedAcceptRevokesNothing`.

The count folds into the existing `Invite accepted` log line rather than adding a second line.

### The guardrail's cost report: what routing through `revokeAllForStatus` would do

Asked for by the user, and answered by grepping the predicate rather than the method the item
discusses (working rule 29).

**As-is it is a silent no-op.** `revokeAllForStatus(id, ACTIVE)` returns 0 before touching the
repository, because `mayHoldSession(ACTIVE)` is true. That is the dangerous shape, not merely a
useless one: a mock-based test asserting `verify(sessionService).revokeAllForStatus(...)` would go
green and report coverage for a fix that does nothing. Working rule 15's exact failure.

Making it work means changing `mayHoldSession`, which has **two other consumers**:

- `SessionService.resolve` -- the read chokepoint on every authenticated request. Loosening the
  predicate so ACTIVE no longer "may hold" means no session resolves for anyone.
- `AdminUserController.updateUser`, which calls `revokeAllForStatus(id, request.status())`. If ACTIVE
  stopped being a no-op there, **every admin PATCH leaving a user ACTIVE becomes a session purge** --
  re-enabling a user would kill the session they just established. That is precisely the scenario
  `revokeAllForStatusLeavesAnActiveAccountsSessionsAlone` was written to catch, and it is the same
  second-caller failure working rule 29 was written from.

The seam is wrong independent of the cost. `revokeAllForStatus` answers "this status cannot hold a
session". S-15 needs "this event invalidated the credential". The account is ACTIVE before and
after, so no status-keyed sweep can express it.

### Scope check that came back clean

Working rule 16 says grep the operation being guarded. The operation is the password write:
`appUserRepository.updatePasswordHash` has **exactly one caller in `src/main`**, which is `accept`.
So the user's "accept only" scope was the whole set rather than a narrowing -- worth recording
because rule 16 has widened the scope on three previous items and this is the first time it
confirmed it.

### Mutation evidence

Both mutations reddened **both** tests, which is the point of having a mock test and a DB test
rather than one of each kind:

| Mutation | Result |
|---|---|
| move the revoke below `create` | mock `InOrder` fails; DB test finds the fresh session revoked |
| drop the revoke entirely | mock `never()` fails; DB test finds the prior session still resolving |

Source restored with `touch` afterwards per working rule 15's note on stale `.class` files.

## S-12 outcome (2026-08-26) -- a second path, and an item whose harm model was wrong

**Shipped** as [#225](https://github.com/themancalledzac/edens.zac.backend/pull/225), +186/-0 across
four files. Suite 1,385 -> 1,391.

### What shipped

`RoleRepository.dropMembershipsIfPerson(Long)` -- one `DELETE` carrying its own `EXISTS (SELECT 1
FROM users WHERE id = :userId AND status = 'PERSON')` test, mirroring `repointMemberships`'s inverted
form of the same idiom. Called from two places in `AdminUserController`.

The guard is in the statement rather than a caller's precondition (working rule 17), which is what
makes it safe to call unconditionally: it deletes nothing for a real account, so neither call site
needs a branch and no future caller has to restate the rule.

### The item named one path; there are two

Working rule 16 again, and the second time in one session it changed the scope. The operation is "a
PERSON row becomes an account", and `appUserRepository.updateStatus` has two admin callers:

- `upgradeUser` -- the path S-12 describes.
- `updateUser` -- takes a bare `UserStatus` and never checks the existing row is an account, so an
  admin PATCH reaches the identical state without going through `upgradeUser` at all.

Both run the sweep, and each has its own test, because a fix wired into only one of them passes the
other's test suite completely.

### Where the item's harm model was wrong

S-12 calls these grants **dormant**. They are not dormant at the authorization layer. `canView` joins
`role_member` to `role_collection` and tests **no status at all**, so it already answers `true` for
the PERSON. What makes the grant harmless today is only that a PERSON has no way to authenticate --
and the upgrade supplies exactly that, which is why the inheritance is instant rather than something
that has to be triggered later.

This surfaced the expensive way, which is the part worth recording: the first draft of the test
opened with `assertThat(roleRepository.canView(person, collection)).isFalse()` as a
scene-setting assertion and **failed on that line**. The corrected test asserts `true` before and
`false` after, which is a strictly stronger claim -- it shows the sweep removing access that already
resolved, not access that was hypothetical.

Working rule 21 says the premise is evidence and the fix is a hypothesis. This is a third category:
**the item's account of why the bad state is currently harmless is also a hypothesis**, and here it
was wrong in the safe direction. The harmlessness lived in the login path, not in the authorization
path the item pointed at.

### Mutation evidence

Three mutations, each caught by a different test, which is the evidence that the two call sites are
independently guarded:

| Mutation | Caught by |
|---|---|
| strip `status = 'PERSON'` from the DELETE | `dropMembershipsIfPersonLeavesAnAccountsMembershipsAlone` and `patchingARealAccountLeavesItsMembershipsAlone` |
| drop the sweep from `upgradeUser` | `upgradeDropsAGrantThatWasDormantWhileTheRowWasAPerson` |
| drop the sweep from `updateUser` | `patchingAPersonIntoAnAccountAlsoDropsTheDormantGrant` |

### Deliberately not done

**A migration purging every pre-guard `role_member` row on a PERSON.** It would close this at rest as
well as in flight. It is not needed for the security property -- no code path can create such a row
any more, and the sweep catches any that exist at the moment they would start mattering -- and it is
a destructive data change against rows nobody has inventoried. Left as a separate call rather than
ridden in on a security fix.

**The opposite direction.** See working rule 30: this sweep cannot cover `account -> PERSON`, and
that gap is S-13's, not a defect in this fix.

## Backfill note (2026-08-28) -- five outcomes this file never received

Working rule 11 splits a close-out across two files: the tracker gets one line plus any working rule
taught, and the outcome detail comes here. **Three consecutive doc close-outs sent nothing here at
all.** This file's last entry before this note was S-12, 2026-08-26. Since then #227, #228, #230,
#232 and #233 all shipped and all five write-ups went into the tracker instead -- which is the exact
bloat rule 11 was written to stop, and it went unnoticed because a tracker that grows still looks
like a tracker being maintained.

**The three entries below marked *backfilled* are reconstructed from what the tracker recorded, not
measured fresh.** They are accurate to the record and should not be read as independent verification.
The S-18 and S-17 entries are first-hand.

Working rule 38 exists so the next close-out cannot repeat this.

## S-13 outcome (2026-08-27) -- constrained at the input, not at the ordering *(backfilled)*

Shipped as [#227](https://github.com/themancalledzac/edens.zac.backend/pull/227).

`UserRequests` typed the field as the bare `UserStatus` enum while the javadoc one line above said
"INVITED / ACTIVE / DISABLED", with nothing enforcing it. Two requests then made
`PersonRepository.deletePersonById`'s `AND status = 'PERSON'` match a real account, which the
people-delete endpoint hard-deletes with memberships cascading. It also manufactured exactly the
`role_member`-on-a-PERSON state S-2 exists to prevent, on a path neither S-2 guard covered.

**Why the fix went to the request type rather than the sweep.** After #225 the obvious reading was
that `dropMembershipsIfPerson` in `updateUser` already handled it. It did not: the sweep runs
*before* the status write, so for an ACTIVE account being PATCHed to PERSON the row is still an
account when the sweep reads it, the sweep is a no-op, and the flip leaves the illegal rows. Moving
the sweep below the write would have fixed this direction and **silently broken S-12's**, because
`PERSON -> account` would then read an account and sweep nothing -- with every test #225 added
staying green, since they all seed a PERSON and assert on state after the call rather than on when
the call ran. Constraining the request enum closes both halves at the input end instead of fighting
the ordering. That asymmetry is working rule 30.

**Taught working rule 32.** S-13's first mutation reddened for the wrong reason -- the right colour
from the wrong cause. A mutation is evidence only once you read *why* it went red.

## S-21 outcome (2026-08-27) -- a schema constraint doing a status check's job *(backfilled)*

Shipped as [#228](https://github.com/themancalledzac/edens.zac.backend/pull/228). Filed while costing
S-10's guardrail, which asked what narrowing `mayAcceptInvite` would break.

`AdminUserController.regenerateInvite` looked the user up by id and minted an invite with **no status
check at all**. `accept` refuses anything outside `{INVITED, ACTIVE}`, so for a DISABLED account the
admin got `200` and a URL, the invitee got the email, clicked it, and received `410 Gone`. Nothing
anywhere said the account was ineligible.

**Traced rather than assumed:** for a PERSON row the failure was louder and differently wrong.
`users.email` is NULL for PERSON and `user_invite.email` is `NOT NULL` in V32, so the insert raised
`DataIntegrityViolationException` and `GlobalExceptionHandler` turned it into a `409` reading "Data
integrity violation: duplicate or invalid data". A schema constraint was doing a status check's job
and reporting the wrong reason for it.

**No test covered this before the fix.** `AdminUserControllerTest` had a happy path and a 404; every
other `regenerateInvite` assertion was a `verify(never())` on a different endpoint's path.

The gate keys on `UserInviteService.mayAcceptInvite` so eligibility keeps one definition (working
rule 14). Low severity because it grants nothing -- redemption was already refused; the cost was an
admin believing they had sent a working link.

## S-20 outcome (2026-08-28) -- the guardrail that said do not unify *(backfilled)*

Shipped as [#230](https://github.com/themancalledzac/edens.zac.backend/pull/230), +97/-30, suite
1,399 -> 1,403. Fourth consecutive item needing no adjustment, which retired working rule 27's streak
rather than merely inverting it.

`AuthController` and `WebAuthnService` both inlined `getStatus() != ACTIVE` while
`SessionService.mayHoldSession` existed as the named predicate. Adding a fifth `UserStatus` and
updating the predicate would have left both call sites admitting it.

**The guardrail was delivered as a report, and its answer was no.** Do **not** unify `mayHoldSession`
with `mayAcceptInvite`. The only non-breaking direction admits INVITED to sessions, and
`WebAuthnService.finishLogin` is a live hole under it, because a passkey outlives a status change.

**Closed one "tests that cannot fail" bullet and corrected its premise.** `AuthControllerTest` had
been described as already catching the passkey gap; it had itself omitted PERSON. Both tests were
defective, not one.

**Taught working rule 33.** A test deriving its cases from the thing under test cannot detect that
thing widening: mutating the predicate made both parameterized suites emit *fewer cases* and stay
green -- `AuthControllerTest` 13 -> 11, `WebAuthnServiceTest` 12 -> 10.

**The count correction that took three passes.** The recorded `UserStatus.ACTIVE` sweep was called
six, then corrected to seven, then corrected back to six on 2026-08-28. Seven comes only from an
*unescaped* `.` matching the `#` in `{@link UserStatus#ACTIVE}`, so three passes argued about a
number while running different commands. #230 deleted that javadoc line and the sweep now returns
four, all code. Working rule 31.

## S-18 outcome (2026-08-28) -- probed before fixed, and the test that could not see an omission

Shipped as [#232](https://github.com/themancalledzac/edens.zac.backend/pull/232). Suite 1,403.

### What shipped

`caches`, `conditions`, `flyway` and `scheduledtasks` onto the actuator exclude list. All four meet
#214's own stated criterion and none was on it.

| Endpoint | Why it meets the criterion |
|---|---|
| `caches` | carries DELETE operations, so it mutates the running app |
| `conditions` | the full auto-configuration report |
| `flyway` | migration history; `flyway-core` is a real dependency and `spring.flyway.enabled=true` |
| `scheduledtasks` | `@EnableScheduling` is on `Application` |

### Verified by probe, not by reading

The app was booted with `management.endpoints.web.exposure.include=*` and the exclude emptied before
anything was changed. **All four returned 200.** The premise was confirmed against a running context
rather than inferred from the property file, which also proved each of the four was genuinely
registrable rather than merely absent from a list.

### The test that reported coverage it did not have

`ActuatorExposureEndToEndTest` iterated `SHIPPED_EXCLUDE_LITERAL.split(",")`. A name missing from the
exclude value was therefore missing from the assertion too, so `caches` was reachable under
`include=*` for as long as nobody thought to add it. The loop now iterates
`ActuatorExposureTest.MUST_BE_EXCLUDED` -- the expectation, which the configuration cannot edit.
Sharing that one list rather than copying it holds the tree at two denylists, the expectation and the
shipped literal, instead of three.

This is working rule 33's species and it was found before rule 33 was written.

### Mutation evidence

Dropping `caches` from the properties file **and** the literal together -- the exact mutation the old
loop could not see, because a consistent edit keeps `excludeLiteralMatchesTheShippedFile` green --
reddens the probe with `/actuator/caches is reachable with include=*, expected: 404 NOT_FOUND but
was: 200 OK`. Going all-green from there now requires deleting the name from `MUST_BE_EXCLUDED` as
well, which is a visible decision.

### The include-only report, and why it was refused

The instruction was to report rather than switch. The case for include-only is real: an allowlist
names one endpoint instead of twelve and never has to chase whatever Boot ships next, and S-18 is an
instance of that upkeep being paid late.

It was refused because Boot resolves `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` from the environment
above `application.properties` (working rule 1). A stray `INCLUDE=*` in a deployed `.env` replaces
the include value outright; exclude applies after it and survives, include-only has nothing left. The
allowlist cannot defend the one scenario the layer exists for, because in that scenario the allowlist
is what was replaced. **And include-only would not have prevented S-18 anyway** -- `include=health`
already left those four off, so they were reachable only under the injected wildcard, meaning the
finding lived entirely inside the layer include-only proposed to delete.

Filed, not built: a `ProdSecretGuard`-shaped boot check reading the *resolved* exposure include and
refusing to start unless it is `health`. That defends the injected-env case without enumerating a
single endpoint name, and is the only thing that would make both the exclude list and
`MUST_BE_EXCLUDED` deletable. Carried forward as **working rule 34**.

## S-17 outcome (2026-08-28) -- not as specified, and two failures of the same kind

Shipped as [#233](https://github.com/themancalledzac/edens.zac.backend/pull/233). Suite 1,411.

### What shipped, and how it differs from the item

The item's stated fix was "extend the limiter past `/api/public/`". **That is not what shipped.** The
user directed a dedicated `ShareEmailLimiter`, leaving `RateLimitFilter`'s prefix untouched. The run
of items needing no adjustment ends at five.

`POST /api/read/user/share/email` had no limit at all, so any signed-in user could POST in a loop,
each call an SES send to an address they choose, from `no-reply@zacedens.com`, DKIM-signed by the
real domain, carrying a genuine clean-reputation link, with part of the subject line coming from the
sender's own display name. The board had recorded this as a token-guessing risk; it is an
authenticated open mail relay and the damage is SES reputation.

### Two choices worth carrying forward

**Keyed on the sender's user id and nothing else.** A `(sender, recipient)` key would bound repeat
mail to one victim while leaving a blast across many addresses unbounded, which is the shape that
costs the domain its reputation.

**A global daily cap alongside the per-sender one**, for the reason `ContactMessageLimiter` has one:
the damage is shared. An SES suspension takes the invite email and the gallery-password email down
with it, so a per-sender limit -- which bounds each account but scales with the number of accounts --
does not protect the resource at risk. Accounts are invite-only, so that scaling is slow rather than
free, but the cap costs nothing and is the only limit whose key a caller cannot pick.

The limit is checked ahead of the token lookup, so a limited caller cannot distinguish a missing
share link from a present one.

### Mutation evidence

| Mutation | Result |
|---|---|
| remove the controller's limiter check | both new controller tests redden |
| move the check *after* the token lookup | both redden |
| swap the global cap behind the per-sender bucket | the ordering test reddens |

### Where this MR failed twice, and what it taught

**A test of its own that could not fail.** The third mutation passed on the first attempt. The
ordering test spent the global cap and never let it refill, and once the cap is spent both orderings
refuse everything forever, so no assertion could separate them. Rewritten with a global period short
enough to refill inside the test, which makes the drained per-sender token observable. This is a
fourth species alongside rules 15, 32 and 33: **a test observing the system only in a state where
every variant behaves identically.**

**A wiring break every unit test was blind to.** The package-private `Duration` constructor makes two
constructors, so Spring found no default one and **the application context failed to start** --
every integration test in the tree errored while every unit test stayed green, because they all build
the limiter by hand. `@Autowired` on the property constructor, as `ClientGalleryAccessLimiter`
already does. A bean's own unit tests cannot see a wiring break; only the full build can.

Both are carried forward as **working rule 35**.

### Deliberately not done

**Widening `RateLimitFilter` past `/api/public/`.** Explicitly out of scope by instruction. The
per-path map the frontend board asked for before a second public endpoint lands is still unbuilt, and
this MR did not touch it.

## S-3 test outcome (2026-08-28) -- the surviving side was tested with one status

Shipped as [#235](https://github.com/themancalledzac/edens.zac.backend/pull/235). Suite 1,411 ->
1,414. Closes the third of the six "tests that cannot fail".

### What was wrong

`PersonRepositoryIntegrationTest` is S-3's entire deliverable -- the only test that sees
`deletePersonById`'s `AND status = 'PERSON'` against real SQL, because every other test naming the
method mocks `PersonRepository`. It seeded **ACTIVE and PERSON only**.

Rewrite the predicate as `AND status <> 'ACTIVE'` and both cases stay green: an ACTIVE row still
survives, a PERSON row is still deleted. Meanwhile every INVITED and DISABLED account becomes
deletable through the admin people-delete endpoint, cascading sessions, passkeys, invites, saves,
follows and share links -- the exact damage bug #1 was fixed to prevent. **The mutation S-3 stated
did redden the test; this one did not**, which is why the gap survived the item that created the
file.

### What shipped

The surviving side is parameterized over every non-PERSON `UserStatus` rather than over one of them.
Working rule 33's pin sits beside it -- `personIsTheOnlyStatusThisMethodMayDelete` -- because a
derived case list shrinks silently when a constant is deleted.

### Mutation evidence

| Mutation | Result |
|---|---|
| `= 'PERSON'` -> `<> 'ACTIVE'` (the invisible one) | 2 failures, INVITED and DISABLED, case count unchanged at 5 |
| guard made vacuously true | 3 failures, all `expected: 0 but was: 1` |
| fifth `UserStatus` added | cases 5 -> 6, the new status passes on its own, **and the pin reddens** |

The third is the one worth keeping: it shows rule 33's pairing end to end. The derived list absorbed
a new status silently and correctly, and the pin turned that silence into a decision.

### A trap for anyone running mutations in this repo

**S-3's stated mutation cannot be run as written.** Stripping `AND status = 'PERSON'` shortens the
SQL string, google-java-format reflows it, `spotless:check` fails, and **the build dies before a
single test runs**. That reads as "the mutation reddened the build" while proving nothing.

Use a mutation that preserves line shape -- `AND status IS NOT NULL` makes the guard vacuously true
without changing formatting. **And check the `Tests run:` count in the output: no such line at all
means the mutation never reached the tests.** This applies to every mutation on this board, not just
this item.

### Not done here

No doc update shipped with it. #235 merged with no tracker or history entry at all, four minutes
before the close-out that would have caught it. Recorded rather than hidden, because it is the same
omission working rule 38 had just been written for.

### Moved from the tracker 2026-08-28 (the split had lapsed at 27 entries)

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


*(Folded in 2026-08-29: the three entries below had been stranded after Appendix D by the
`32d2168` re-split; the two 2026-08-28 entries after them moved from the tracker per its new
session-log retention rule.)*

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
- 2026-08-26 — shipped **S-15** ([#224](https://github.com/themancalledzac/edens.zac.backend/pull/224),
  +112/-2) and **S-12** ([#225](https://github.com/themancalledzac/edens.zac.backend/pull/225),
  +186/-0); suite 1,385 -> 1,391. S-15 needed **no** adjustment, because the 2026-08-25 pass had
  already corrected its non-existent method -- working rule 27 paying out a second time. S-12 needed
  two: working rule 16 found a **second call site** (`updateUser`, which the item never mentions),
  and the item's "dormant" framing was **wrong** -- `canView` tests no status, so the grant already
  resolved; a scene-setting assertion failing is what exposed it. Delivered the guardrail's cost
  report rather than the change: routing S-15 through `revokeAllForStatus` is a silent no-op whose
  repair would turn every ACTIVE admin PATCH into a session purge. Added **working rule 30** (a
  state-keyed sweep guards one direction; placement picks it), which corrects **S-13** -- #225 edits
  S-13's own method and closes none of it. Board integrity: the security count read **8 when it was
  9** from the moment S-21 was filed; corrected, and now 7. Re-anchored S-20 and S-21 against the
  files #224/#225 touched; both intact, both still COLD. Next: **S-13**.
- 2026-08-27 — shipped **S-13** ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227),
  +117/-6) and **S-21** ([#228](https://github.com/themancalledzac/edens.zac.backend/pull/228),
  +77/-1); suite 1,391 -> 1,397. **Both shipped as specified, needing no adjustment** -- the second
  and third in a row, which inverts the streak working rule 27 was written about; what the three
  share is that each had been re-verified against the code within two days of implementation.
  S-13's consumer check (working rule 24, at the input end) came back clean against `edens.zac`
  `18eb038`: no shipped path can send `PERSON`, because the panel hides Update for PERSON rows and
  the detail page branches away before the editor mounts. Delivered the working-rule-30 guardrail as
  a **report, not a change** -- moving `dropMembershipsIfPerson` below the status write closes S-13's
  direction, reopens S-12's, and leaves every test #225 added green; two sweeps would need a
  data-loss decision from the user. S-21's own 2026-08-26 note is what picked its fix's placement
  (controller, not service). **Reconcile corrections, and one is a miss by the previous pass:**
  S-20's recorded grep asserts six hits and returns **seven** -- the extra is a javadoc `{@link}`
  added by #199 on 2026-08-24, so the count was wrong when written on 2026-08-26, though the premise
  and the six code sites are intact. MR 19 #17's `UserInviteService` refs had drifted from #224 and
  were **not caught by the #226 close-out**, which is drift outside its neighborhood and one of the
  two escalation conditions now met. MR 24's `AdminUserController` numbers re-measured (481 -> **520**
  main, 1,183 -> **1,294** test, +27% test in four days across four security MRs) and its positional
  refs **deleted rather than refreshed**, since refreshing them is what working rule 26 exists to
  forbid and this item did it to itself. MR 23's request-records item re-counted 11 -> **13 files**:
  #227 added a second constraint/validator pair to `controller/admin/`, so that package now holds
  bean-validation types by precedent rather than decision. Added **working rule 31** (a grep-based
  count must exclude comments; second javadoc miscount on this board) and **working rule 32** (a
  mutation is evidence only if you read *why* it reddened -- S-13's first mutation gave the right
  color for the wrong reason). Security board: 7 open -> **5, only 3 actionable**. Next: **S-20**.
  **Recommending a full-board review** -- see the note below.
- 2026-08-28 — shipped **S-20** ([#230](https://github.com/themancalledzac/edens.zac.backend/pull/230),
  +97/-30); suite 1,399 -> 1,403. Shipped as specified, **fourth consecutive item needing no
  adjustment**, so working rule 27's streak is retired rather than merely inverted. Delivered the
  guardrail as a report: **do not unify `mayHoldSession` with `mayAcceptInvite`** -- the only
  non-breaking direction admits INVITED to sessions, and `WebAuthnService.finishLogin` is a live
  hole under it because a passkey outlives a status change. Closed one "tests that cannot fail"
  bullet as a side effect and **corrected its premise**: `AuthControllerTest` was described as
  already catching the gap and had itself omitted PERSON, so both tests were defective, not one.
  Added **working rule 33** (a test deriving its cases from the thing under test cannot detect that
  thing widening -- mutating the predicate made both suites emit *fewer cases* and stay green,
  13 -> 11 and 12 -> 10). **Four recorded numbers corrected, three of them found unprompted**:
  S-20's own 2026-08-27 grep correction was wrong (escaped vs unescaped dot -- the original six was
  right, and #230 has since deleted the disputed javadoc line, leaving four); the recorded suite
  total was 1,397 when `main` measured 1,399; and the `Optional.get()` bullet's Atomic exclusions
  are **five, not eleven**, making its subset ~53 rather than 46 -- that last one **outside the
  neighborhood of anything recently merged**. Re-verified S-17 and S-18 premises against `main`;
  both exact, both COLD, both re-sized as test-dominated. Security board: 5 open -> **4, only 2
  actionable**. Next: **S-18, then S-17**. **Full-board review still recommended, slices 1 and 5,
  with slice 1 re-scoped to re-run recorded commands rather than only chase refs** -- see the note
  above.
- 2026-08-28 (close-out) — shipped **S-18** ([#232](https://github.com/themancalledzac/edens.zac.backend/pull/232)),
  **S-17** ([#233](https://github.com/themancalledzac/edens.zac.backend/pull/233)), the **S-3 test
  gap** ([#235](https://github.com/themancalledzac/edens.zac.backend/pull/235)) and two doc MRs
  ([#234](https://github.com/themancalledzac/edens.zac.backend/pull/234), this one); closed
  [#231](https://github.com/themancalledzac/edens.zac.backend/pull/231) unmerged as superseded rather
  than merging a close-out whose every live cell had gone stale. Suite 1,403 -> **1,414**. Security
  board: 4 open -> **2, and 0 actionable** -- both remaining are product calls, so **the actionable
  security board is empty for the first time since 2026-08-25** and the live source of work is now
  *Tests that cannot fail*, which got its own board row after three days as an unrowed subsection.
  S-18 shipped as specified; **S-17 did not** (a dedicated limiter, not a wider `RateLimitFilter`),
  ending the as-specified run at five. Standing instruction from the user mid-session: **never write
  inline comments**, which supersedes working rule 12 and re-scoped the 567-comment row from
  optional debloat to standing rule. Added **rules 34-39**. Three recorded numbers corrected, and
  **two process failures found in this session's own work**: the history file had received nothing
  since 2026-08-26 across five MRs (rule 38, five outcomes backfilled), and #232's rule-37 sweep was
  pushed after #232 had already merged, so it is stranded on a dead branch (rule 39, filed as R-1).
  Next: **R-1, then `ProdSecretGuardTest.Wiring`, then the `AdminUserControllerTest` comment, then
  the share-link `no-store` pin.** S-14 and S-16 need a user decision each -- ask both first.

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

Against `ai_docs/reviews/2026-07-25-open-pr-review.md`, backend items: 25 landed, 10 moot (superseded by the typeless phase-2 merge), **1 partial, 1 still open** (recounted 2026-09-01 at the tenth-run review; the bullet below had been headed "Still open" while two of its three clauses recorded closed work, so the headline was wrong in the safe direction).

**Closed since the 2026-08-24 recount, and both were already recorded as closed inside the "Still open" bullet:** C8, whose policy is recorded under the tracker's "Decisions needed"; and the `CollectionControllerDevTest` naming drift, which MR 4 shipped as [#164](https://github.com/themancalledzac/edens.zac.backend/pull/164) -- re-confirmed 2026-09-01, `find src/test -name "*DevTest*"` returns nothing.

- Still open: **C7, partial indexes on `is_blog`/`is_client`, explicitly optional.** Verified 2026-09-01: no `CREATE INDEX` in any migration mentions either column; they are introduced by `V50__collection_client_blog_flags.sql` as plain `BOOLEAN NOT NULL DEFAULT FALSE` with a CHECK constraint, and touched by V51/V52.
- Partial: `CollectionList.fromSibling` exists, but two positional construction sites remain, in `CollectionRepository` and `CollectionService` (line refs dropped per working rule 5). **Re-verified 2026-09-01 and both still resolve by name -- this is the oldest live claim on the board and it is still accurate.** Related: `fromSibling` passes `null` for `collectionDate` by construction. **The record's own docblock now says so explicitly** ("Siblings carry no collection date, so that component is null"), which it did not when this note was written, so the note is documenting documented behavior and can be trimmed to a pointer when someone is next here.
- Everything else verified landed (all-blogs on `is_blog`, no-flags INFO log, `@Valid` on `TagAdminController`, typeFilter deleted, column-list dedup, flag-triplication gone, the 1.6 test adds, the 1.7 comment fixes) or mooted by V51/V52.

# Security findings — closed (moved 2026-08-29)

Moved from the tracker 2026-08-29 by the full-board-review close-out. In-body cross-references
("above", "below", "in this same document") describe positions on the tracker as of the move.

The tracker's original section preamble, kept for provenance:

> Consolidated 2026-08-24 by the full-board review. Security work was scattered across three homes --
> a Wave 3 residual here, `CollectionAccessService` filed under a comments wave, and two new findings
> that had no home at all. They live here now. Every item below was traced in code on `4976220`, and
> the two marked PROVEN were demonstrated by mutating the source and watching the suite.

## Reopened 2026-08-25 by the split full-board review — the closed bodies

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


- [x] **S-12 (MEDIUM-HIGH, agent trace). Dormant `role_member` rows on a PERSON become live grants on
  upgrade.** **DONE** ([#225](https://github.com/themancalledzac/edens.zac.backend/pull/225),
  2026-08-26.) S-2 closed `addMember` and `repointMemberships`. `AdminUserController.upgradeUser` is
  the third path and the dangerous direction: it verifies the row is PERSON, sets an email, flips it
  to INVITED and mints an invite, with no `role_member` purge. The row id never changes, so any grant
  already pointing at that PERSON survives onto the live account. `RoleRepository` names this exact
  risk in `addMember`'s own docblock. **No migration ever purged pre-guard rows**, so the
  precondition is existing prod data rather than something an attacker has to arrange.

  **Shipped as `RoleRepository.dropMembershipsIfPerson`**, +186/-0, suite 1,385 -> 1,391. Full
  write-up in the [history file](2026-08-22-backend-cleanup-history.md#s-12-outcome-2026-08-26----a-second-path-and-an-item-whose-harm-model-was-wrong).
  Two corrections the item needed, both found by following the rules rather than the text:

  **Working rule 16 found a second path.** `updateUser` takes a bare `UserStatus` and never checks the
  existing row is an account, so an admin PATCH turns a PERSON into one without touching
  `upgradeUser`. Both call sites run the sweep, each with its own test -- a fix wired into one of them
  passes the other's suite completely.

  **"Dormant" is wrong.** `canView` joins `role_member` to `role_collection` and tests no status, so
  it already answers true for the PERSON. The harmlessness lives in the login path, not the
  authorization path the item points at, and the upgrade supplies exactly the login. Found because a
  scene-setting assertion that `canView` was false **failed**.

  **Not done, deliberately:** a migration purging pre-guard rows at rest. Not needed for the security
  property, and a destructive data change against rows nobody has inventoried -- a separate call
  rather than something ridden in on a security fix.


- [x] **S-13 (MEDIUM, agent trace). The admin update endpoint accepts `status: PERSON`.** **DONE**
  ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227), 2026-08-27.)
  `UserRequests` types the field as the bare `UserStatus` enum; the javadoc one line above says
  "INVITED / ACTIVE / DISABLED" and nothing enforces it. Two requests then make
  `PersonRepository.deletePersonById`'s `AND status = 'PERSON'` match a real account, which the
  people-delete endpoint hard-deletes with memberships cascading. It also manufactures exactly the
  `role_member`-on-a-PERSON state S-2 exists to prevent, on a path neither S-2 guard covers.

  **Both premises re-verified 2026-08-26 while shipping S-12, which edits this same method.**
  `PersonRepository.deletePersonById` is still `DELETE FROM users WHERE id = :id AND status =
  'PERSON'`, and `UpdateUserRequest.status` is still a bare `@NotNull UserStatus`. Intact.

  **S-12 does NOT close the second half of this item, and cannot be made to.** The obvious reading
  after #225 is that `dropMembershipsIfPerson` in `updateUser` already handles the
  `role_member`-on-a-PERSON state. It does not: the sweep runs **before** the status write, so for an
  ACTIVE account being PATCHed to PERSON the row is still an account when the sweep reads it, the
  sweep is a no-op, and the flip then leaves exactly the illegal rows. Moving the sweep after the
  write would fix this direction and **silently break S-12's**, because `PERSON -> account` would
  then read an account and sweep nothing. See working rule 30. The fix for this item is still
  constraining the request enum so `PERSON` never arrives -- which closes both halves at the input
  end rather than fighting the ordering.

  **Guardrail: leave the `dropMembershipsIfPerson` call site in `updateUser` where it is, and report
  what moving it below `appUserRepository.updateStatus` would do.** This is the tempting adjacent
  change and it is wrong in a way that hides: moving it closes this item's direction, reopens S-12's,
  and **every test #225 added stays green**, because they all seed a PERSON and assert on the state
  after the call rather than on when the call ran. If the analysis says two sweeps are wanted, that
  is a data-loss decision for the user (an `account -> PERSON` sweep deletes a real account's role
  grants), so report it rather than making it.

  **Also worth checking before assuming the scope:** whether any consumer actually sends a status at
  all on a PATCH that is not meant to change one. `UpdateUserRequest.status` is `@NotNull`, so every
  caller must send something -- constraining the type is not free if a frontend sends the row's
  current status back verbatim and that status is ever `PERSON`. Working rule 24 at the input end.

  **Outcome 2026-08-27.** Shipped as `@AccountStatus` + `AccountStatusValidator` on
  `UpdateUserRequest.status`, mirroring the `@GrantableLevel` / `GrantableLevelValidator` pair
  already in `controller/admin/` for the identical shape of problem (an enum field that must exclude
  one constant). +117/-6 across five files. **Shipped as specified, with no adjustment needed** --
  the second such item in a row after S-15, which is what broke the six-item streak working rule 27
  was written about.

  **The consumer check the item demanded came back clean, and it was worth running.** Checked
  `edens.zac` at `18eb038`: `UserManagementPanel` renders Merge/Upgrade instead of Update for a
  `PERSON` row and sets `onActivate={undefined}` so row-click navigation is off;
  `/admin/users/[id]/page.tsx` branches on `user.status === 'PERSON'` before `AdminUserSpaceEditor`
  mounts; both `STATUS_OPTIONS` lists are `['INVITED','ACTIVE','DISABLED']`. No shipped path sends
  `PERSON`, and no backend test does either -- `UserUpgradeIntegrationTest` PATCHes a PERSON *to*
  ACTIVE, which stays allowed and is now pinned by `updatePersonToActiveStillSucceeds`.

  **The guardrail held and the report is recorded above under working rule 30** -- the
  `dropMembershipsIfPerson` call site in `updateUser` is unchanged. Moving it below the status write
  closes this direction, reopens S-12's, and leaves every test #225 added green. Two sweeps would
  cover both and the second deletes a real account's role grants on the way to PERSON, which is a
  data-loss decision for the user. Not made.

  **Trap for the next mutation check, and it cost a wrong turn here.** The rejection test stubs
  `findById` with `lenient()` **on purpose**. Without that stub the un-annotated build returns
  `404` on an unstubbed lookup, which reddens the test for the wrong reason and proves nothing about
  the guard. With it, dropping `@AccountStatus` makes the PATCH return `200` and write `PERSON` --
  the actual defect. **A mutation that reddens a test is not automatically evidence; check that it
  reddens it via the behavior under test.** See also working rule 32.


- [x] **S-15 (MEDIUM, agent trace). Completing a password reset does not revoke the account's other
  sessions.** **DONE** ([#224](https://github.com/themancalledzac/edens.zac.backend/pull/224),
  2026-08-26.) `UserInviteService.accept` writes the new password hash and mints a new session without
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

  **Shipped as the corrected version specified**, +112/-2, suite 1,385 -> 1,388. Full write-up in the
  [history file](2026-08-22-backend-cleanup-history.md#s-15-outcome-2026-08-26----the-missing-method-and-why-the-existing-sweep-could-not-be-reused).
  Three things to carry: the revoke sits **below** the status and address guards, so a refused token
  cannot log somebody out (`aRejectedAcceptRevokesNothing`); working rule 16 came back **clean** for
  once -- `updatePasswordHash` has exactly one `src/main` caller, so "accept only" was the whole set
  rather than a narrowing; and the guardrail's cost report is in the history file. Its headline:
  routing through `revokeAllForStatus` is a **silent no-op**, and making it work means changing
  `mayHoldSession`, whose other two consumers are `resolve` (every authenticated request) and
  `updateUser` -- where an ACTIVE write must stay a no-op or every admin PATCH becomes a session
  purge.


- [x] **S-17 (DONE [#233](https://github.com/themancalledzac/edens.zac.backend/pull/233), 2026-08-28.)
  `share/email` with no rate limit is an authenticated open mail
  relay.** The board already recorded "no rate limit" as a known gap and framed it as a
  token-guessing risk. It is not: `RateLimitFilter` covers `/api/public/` only, so any signed-in user
  can POST unbounded to the endpoint, each call an SES send to an arbitrary address from
  `no-reply@zacedens.com`, DKIM-signed by the real domain, carrying a genuine clean-reputation link.
  Part of the subject line comes from the sender's own display name, which they set at invite
  acceptance. **The damage is SES reputation, and it is shared** -- a suspension takes the invite
  email and the gallery-password email down with it.

  **Re-verified 2026-08-28. Premise exact.** `RateLimitFilter:102` still reads
  `if (!request.getRequestURI().startsWith("/api/public/"))` and returns early for everything else;
  the endpoint is `POST /api/read/user/share/email`, outside that prefix, so it is unlimited today.
  **COLD**, and second in the run after S-18.

  **Size it as test-dominated**, same correction as S-18: the limiter wiring is small and the real
  deliverable is a test that reddens when the limit is removed.

  **Guardrail: do not widen `RateLimitFilter`'s prefix test.** The tempting one-line fix is to
  change `/api/public/` to `/api/` so the filter covers this endpoint too. That puts a per-IP
  limiter **and the 16KB body cap** in front of every authenticated endpoint in the app, including
  admin upload paths whose bodies are deliberately large -- a behavior change across dozens of
  routes smuggled in as a security fix for one. Add a dedicated limiter for this endpoint following
  the `AuthLoginLimiter` pattern instead, and leave the filter's prefix alone; the item's own note
  that "the four limiters already have disjoint key spaces" is what makes a fifth one safe. Report
  what widening the prefix would cost rather than doing it.


- [x] **S-18 (DONE [#232](https://github.com/themancalledzac/edens.zac.backend/pull/232), 2026-08-28.)
  #214's exclude list misses four endpoints that meet its own
  stated criterion.** The criterion is "dumps configuration, dumps process state, or mutates the
  running app". Available under `include=*` and not excluded: `caches` (has delete operations, so it
  mutates), `conditions` (full auto-config report), `flyway` (migration history) and `scheduledtasks`
  (`@EnableScheduling` is on). `InternalSecretFilter` still covers them in prod -- but #214 exists
  precisely as the layer for when it does not.

  **Re-verified 2026-08-28. Premise exact *at the time*; both halves of this sentence have since
  moved and are left as written with this correction rather than refreshed, per working rule 26.**
  The line is now `application.properties:71` (70 is the `include`), and the value is the
  twelve-name list #232 shipped. As of the re-verification it excluded exactly
  `env,configprops,beans,mappings,heapdump,threaddump,loggers,shutdown`; all four named endpoints
  are still absent, and `:69` still includes `health` alone. **COLD**, and picked as next.

  **Size it as test-dominated.** The config change is four names on one line. The deliverable is the
  test, because this item's own scope already carries the first bullet of "Tests that cannot fail":
  `ActuatorExposureEndToEndTest` iterates the denylist it is testing, so an omission like `caches`
  is structurally invisible and no change to `src/main` can redden it. Fixing the config without
  fixing that test ships a wider exclude list guarded by the same blind spot that let the four
  through. This is the same shape as **working rule 33** -- a test that reads its expectations from
  the thing under test -- so the fix is the same: assert the four newly-excluded names literally,
  and watch the case count when you change the parameterization.

  **Guardrail: do not switch the exposure model.** The tempting adjacent change is to drop the
  exclude list and rely on `include=health` alone, on the reasoning that an allowlist is stricter
  than a denylist and makes the item disappear. Leave `:69` as it is and only add to `:70`. #214
  shipped this belt-and-braces arrangement deliberately and its end-to-end test exists to prove
  exclude beats include under `include=*`; collapsing to one mechanism deletes the property that
  test was written to establish. If the analysis says include-only is right anyway, report what it
  would change and let the user decide.


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


- [x] **S-20 (MEDIUM, agent trace). "May hold a session" exists in three places and only one of them
  is `mayHoldSession`.** **DONE**
  ([#230](https://github.com/themancalledzac/edens.zac.backend/pull/230), 2026-08-28, +97/-30.)
  S-8's write-up claims the predicate serves both `resolve` and the sweep so
  it cannot drift. `AuthController` and `WebAuthnService` both inline `getStatus() != ACTIVE`. Adding
  a fifth `UserStatus` and updating `mayHoldSession` leaves both admitting it -- the exact drift
  S-9's refactor was done to prevent on the invite side.

  **Re-verified 2026-08-26** (SessionService was edited by #224, so this is in the neighborhood).
  Grepping `UserStatus.ACTIVE` across `src/main` returns exactly six hits: the two predicates, one
  seed in `AdminBootstrap`, one write in `UserInviteService.accept`, and the two inlined comparisons
  this item names -- `AuthController` (`!= ACTIVE`, in the login guard's three-clause `if`) and
  `WebAuthnService` (`!= ACTIVE`, guarding `finishLogin`). Premise intact, count exact, and #224 did
  not add a seventh. **COLD.**

  **The count above is wrong and was wrong when it was written. Corrected 2026-08-27.** That grep
  returns **seven**, and has since 2026-08-24. The seventh is
  `WebAuthnService:179` -- a **javadoc** line, `{@link UserStatus#ACTIVE}`, added by
  [#199](https://github.com/themancalledzac/edens.zac.backend/pull/199) two days *before* the
  re-verification that claimed six. So the 2026-08-26 pass either did not run the command it cited
  or reported a remembered number.

  **The premise and the work are unaffected: there are still exactly six *code* sites and still
  exactly two inlined comparisons.** What is wrong is the recorded check. Use
  `grep -rn 'UserStatus\.ACTIVE' src/main --include='*.java' | grep -v '\*'` -- seven raw, six code.
  This is the **second** time a javadoc line has broken a grep-based count on this board; the first
  was `isRealUser` at `UserShareControllerProd:34`, recorded in the history file. Two occurrences of
  the same trap is a rule, so it is now **working rule 31**. Still **COLD**.

  **Picked as next 2026-08-27**, because its fix is the same move S-21 just made one file over:
  route an inlined status test through the single named predicate that already states the rule, so
  the rule keeps one definition (working rule 14). `AuthController` and `WebAuthnService` call
  `SessionService.mayHoldSession(user.getStatus())` instead of `getStatus() != UserStatus.ACTIVE`.
  Two lines, plus whatever the two call sites need to see the static import.

  **Guardrail: leave `UserInviteService.mayAcceptInvite` alone, and report what merging the two
  predicates would do.** This is the tempting adjacent change and it is wrong. `mayHoldSession` is
  `== ACTIVE`; `mayAcceptInvite` is `INVITED || ACTIVE`. They look like near-duplicates one refactor
  away from each other, and they are **deliberately different**: an INVITED account may hold a live
  invite and may not hold a working session, which is exactly why #224's `updateUser` docblock says
  "the two sweeps key off different allowlists". Fold them together and an INVITED account gets a
  session, or a DISABLED-then-reinvited account stops being able to redeem -- depending on which
  definition wins. Do not unify them. If the analysis says a shared abstraction is right anyway,
  report it and let the user decide.

  **Second, smaller guardrail: do not restructure `AuthController`'s login guard.** The status test
  sits inside a three-clause `if` alongside the user-exists and password checks, and merging its
  branches is a behavior change hiding in a readability change. Substitute the predicate into the
  clause and leave the shape of the `if` alone.

  **Outcome 2026-08-28.** Shipped as specified. Both sites call
  `SessionService.mayHoldSession(...)`; the three-clause `if` keeps its shape; the now-unused
  `UserStatus` import came out of both files. `mayHoldSession`'s docblock now names all four call
  sites and states that one definition governs both ends of the lifecycle -- minting at
  `AuthController.login` and `WebAuthnService.finishLogin`, reading at `resolve` and
  `revokeAllForStatus`. **Fourth consecutive item to need no adjustment at implementation time**
  (S-15, S-13, S-21, S-20), and the fourth to have been re-verified against the code within two days
  of being implemented.

  **Estimate versus actual: "two lines, plus whatever the two call sites need to see the static
  import" was right about `src/main` and silent about the tests, which were four fifths of the
  diff.** Source was +18/-14 across three files. Tests were +82/-16. This is the *same* failure mode
  the doc already named once -- estimates that count source lines and forget the test file -- and it
  has now recurred on an item small enough that the miss looked harmless. Applied forward: **S-17
  and S-18 should both be re-sized as test-dominated**, because each is a few lines of config or
  wiring whose real deliverable is a regression test that can fail, and S-18's stated scope already
  admits this ("four names onto the exclude list, plus a test that is not self-referential").

  **The guardrail's report, delivered instead of the change.** Unifying the two predicates is a
  one-way street and the only non-breaking direction re-opens a closed hole. Narrowing
  `mayAcceptInvite` to `ACTIVE` breaks onboarding outright -- `accept` would refuse every
  first-time invite. So unification means widening `mayHoldSession` to admit `INVITED`, which lands
  differently at each of its four sites: `resolve` starts returning a principal for a demoted
  account (`resolveRejectsSessionWhoseAccountWasReturnedToInvited` goes red, and it documents that
  as intentional); `revokeAllForStatus` stops sweeping on `ACTIVE -> INVITED`, so those rows stay
  live *and* now resolve; `AuthController.login` is latent rather than live, because an `INVITED`
  account has no password hash and the `getPasswordHash() == null` clause catches it -- defended by
  ordering, not by the status test; and **`WebAuthnService.finishLogin` is a live hole**, because a
  passkey outlives a status change and nothing else guards that door. `INVITED` is precisely the
  status where the two questions must differ. **Do not unify.** What the two should share is a
  convention, not an implementation: never compare a `UserStatus` to a literal outside these two
  methods. S-20 makes that true for sessions; it was already true for invites.

  **Trap found by mutation, not by reading -- this is the item's most reusable output.** Both
  parameterized login tests were written to derive their cases from `mayHoldSession`, so a fifth
  `UserStatus` would be covered automatically. That derivation **cannot police the predicate it
  derives from**: mutating `mayHoldSession` to `!= DISABLED` made both tests emit *fewer cases* and
  stay green -- `AuthControllerTest` 13 -> 11, `WebAuthnServiceTest` 12 -> 10. Green, and testing
  less than before. The fix is one literal pin,
  `SessionServiceIntegrationTest.mayHoldSessionAdmitsActiveAndNothingElse`, which reddens on a
  widened predicate and on a fifth `UserStatus` -- the second is the intended cost, since session
  eligibility should be a decision rather than an inherited default. Generalized as **working rule
  33**.

  The derivation still earned its place: it exposed a real gap the board had only half-recorded.
  See the correction under "Tests that cannot fail" below.

  **Mutations verified red and read for why (working rules 15, 32):** widening `mayHoldSession` to
  `!= DISABLED` reddens the new pin plus the two pre-existing INVITED resolve/sweep tests; deleting
  the `AuthController` clause gives 3 failures, `Status expected:<401> but was:<204>`, one per
  ineligible status; deleting the `WebAuthnService` guard gives 3 failures, `Expecting code to raise
  a throwable`. One wrong turn worth recording: the first mutation run came back red from
  **spotless**, not from a test, because `mvn test` runs the format check first. Use
  `-Dspotless.check.skip=true` when mutating source, or you will read a formatting failure as
  evidence the guard is load-bearing -- working rule 32's exact failure mode, one layer out from the
  test.

  **Side effect worth knowing: this item deleted its own grep evidence.** `WebAuthnService:179`'s
  `{@link UserStatus#ACTIVE}` javadoc is gone, replaced by a link to `mayHoldSession`. The
  `UserStatus.ACTIVE` sweep over `src/main` now returns four, all code, and the escaped/unescaped
  discrepancy that made this item's count a three-pass argument no longer has anything to disagree
  about.


- [x] **S-21 (LOW, verified 2026-08-25). `regenerateInvite` mints a link for accounts that can never
  redeem it.** **DONE**
  ([#228](https://github.com/themancalledzac/edens.zac.backend/pull/228), 2026-08-27.) *(Filed while costing S-10's guardrail -- the endpoint had to be read to establish
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

  **Re-verified 2026-08-26** (`AdminUserController` and `UserInviteService` were both edited by #224
  and #225, so this is in the neighborhood). The endpoint still looks the user up by id and mints
  with no status test. One thing to know before fixing it that the item does not say: `upgradeUser`
  calls the **service** method `userInviteService.regenerateInvite` directly, not this endpoint, and
  it does so *after* flipping the row to INVITED. So a gate on the controller endpoint leaves
  `upgradeUser` working, and a gate pushed down into the service would also pass -- but only because
  of that ordering, which #225 did not change and a future edit could.

  **Outcome 2026-08-27.** Shipped as specified: `if (!UserInviteService.mayAcceptInvite(...))` ->
  `409` at the top of the endpoint, keyed on the same allowlist `accept` enforces at redemption so
  the eligibility rule keeps one definition (working rule 14). +21/-1 in `AdminUserController`,
  +56 in its test.

  **The 2026-08-26 note about `upgradeUser` decided the fix's placement**, which is the payoff for
  writing it down instead of leaving it re-derivable. The gate went on the **controller endpoint**,
  not pushed into the service, precisely because a service-level gate would pass only by accident of
  `upgradeUser`'s internal ordering.

  **The "no test covers this" note was exact and is now closed.** Three tests added where there had
  been a happy path and a 404: `regenerateForActiveUserReturns200` (pins the password-reset path the
  gate must not close), `regenerateForDisabledUserReturns409AndMintsNothing`, and
  `regenerateForPersonReturns409BeforeTheSchemaRejectsIt`. Mutation-verified: with the guard removed
  both 409 tests return `200`.

  **This is the third consecutive item to need no adjustment at implementation time** (S-15, S-13,
  S-21). Working rule 27's streak has inverted, and what all three share is that each had been
  re-verified against the code within two days of being implemented. That is the variable -- not
  item size, and not how carefully the fix was originally specified.


### Classification table — the DONE rows (2026-08-25)

| Item | State | Notes |
|---|---|---|
| S-10 | **DONE** ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221)) | -- shipped as specified 2026-08-25 |
| S-11 | **DONE** ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222)) | -- shipped as specified 2026-08-25 |
| S-12 | **DONE** ([#225](https://github.com/themancalledzac/edens.zac.backend/pull/225)) | -- shipped 2026-08-26; needed a second call site the item did not name, and its "dormant" framing was wrong |
| S-13 | **DONE** ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227)) | -- shipped 2026-08-27 as specified; the consumer check it demanded came back clean, and the working-rule-30 guardrail held |
| S-15 | **DONE** ([#224](https://github.com/themancalledzac/edens.zac.backend/pull/224)) | -- shipped 2026-08-26 exactly as the 2026-08-25 correction specified; the first item in this batch to need **no** adjustment at implementation time |
| S-17 | **DONE** ([#233](https://github.com/themancalledzac/edens.zac.backend/pull/233)) | -- shipped 2026-08-28 **not** as specified (a dedicated limiter, not a wider `RateLimitFilter`); taught working rule 35. Outcome: [history](2026-08-22-backend-cleanup-history.md) |
| S-18 | **DONE** ([#232](https://github.com/themancalledzac/edens.zac.backend/pull/232)) | -- shipped 2026-08-28 as specified; taught working rule 34. Outcome: [history](2026-08-22-backend-cleanup-history.md) |
| S-20 | **DONE** ([#230](https://github.com/themancalledzac/edens.zac.backend/pull/230)) | -- shipped 2026-08-28 as specified, fourth in a row; its guardrail report says do **not** unify the two predicates, and its test work produced working rule 33 |
| S-21 | **DONE** ([#228](https://github.com/themancalledzac/edens.zac.backend/pull/228)) | -- shipped 2026-08-27 as specified; the item's own 2026-08-26 `upgradeUser` note is what picked the gate's placement |

**Updated 2026-08-25 after the close-out:** S-10 and S-11 shipped, S-21 was filed and stamped COLD,
and S-15's row was corrected. That correction is the one worth noticing -- S-15 was stamped COLD on
the strength of "S-8 already built it", and the method it named does not exist. **A COLD stamp
asserts there is no unanswered question; it does not assert the prescribed fix compiles.** Working
rule 21 already says the fix is a hypothesis, and this table is where that distinction keeps getting
lost, because a single word in a status column reads as a warranty over the whole item.

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


### Holds from the 2026-08-29 adversarial re-review

The 2026-08-29 review attacked the merged set (S-10..S-21) as a group, per the board's restated
recommendation, and returned 0 HIGH / 0 MEDIUM. Four LOW findings were filed on the tracker
(S-22, S-23, S-24 and the passkey-revocation decision row). The targets that held, in full, so the
next pass does not spend the time again:

- **`mayHoldSession` vs `mayAcceptInvite` -- the "do not unify" reasoning is exactly right.**
  `WebAuthnService.finishLogin` reads status fresh through the predicate, so a passkey cannot mint
  a session after a demotion; the only non-breaking unification direction widens `mayHoldSession`
  to admit INVITED, under which `finishLogin` is the live hole (login is defended by the
  `passwordHash == null` ordering, resolve/sweep by other means, but the passkey door has only the
  status test). S-20's guardrail report confirmed against code.
- **Nothing lets a session or passkey outlive a status change today.** `resolve` re-reads status
  AND `isAdmin` every request (STATELESS context); deactivation runs `revokeAllForStatus` then a
  fresh-read backstop; no path hard-deletes an ACTIVE account (`deletePersonById` and merge are
  PERSON-only); an admin/role downgrade reflects on the next request. The one known latent --
  `resolve` slides the window before reading status -- is already on the tracker under "Unsettled".
- **The only rule-30 placement coupling is `dropMembershipsIfPerson`, correctly placed** -- it
  runs before the status write in both `updateUser` and `upgradeUser`. The session/invite sweeps
  are keyed on the `newStatus` *argument*, not a row read, so placement cannot couple them; the
  real analogue in that surface is the RoleRepository denylist, filed as S-22.
- **`ShareEmailLimiter` is keyed and placed correctly.** It keys on `principal.userId()`
  (session-derived, not spoofable, per-user not per-IP), is checked before the token lookup, and
  has a disjoint key space from the four other limiters; the null-sender branch is dead because
  the route requires a session. `RateLimitFilter` still covers `/api/public/`; the gallery unlock
  has its own per-IP+slug limiter.
- **The S-set mutation pins are resistant.** S-20: `mayHoldSessionAdmitsActiveAndNothingElse`
  iterates `UserStatus.values()`, reddening on both a widened predicate and a fifth status. S-3
  (#235): `PersonRepositoryIntegrationTest` parameterizes the survive-set over the enum plus a
  literal pin, reddening on the `<>'ACTIVE'` mutation and on a no-op DELETE. S-17: the 429 guard
  is pinned and rule-35's refill-observability fix is in place
  (`globalCapIsCheckedBeforeThePerSenderBucket`). S-18's exclude pin reddens on "drop a name" both
  ways; its only gap is the unlisted-endpoint blind spot, filed as S-23.
- **S-10, S-11, S-12, S-13, S-15, S-21 re-checked in the group**: the accept-flow order (guards ->
  password/status write -> `revokeAllForUser` -> mint) is correct, the S-13 `@AccountStatus` input
  guard closes both S-12/S-13 directions at the enum, and no cross-fix premise inversion (the
  S-7 -> S-10 shape) recurs among them. Hold.

# Board row narratives (moved 2026-08-29)

The Progress rows below were replaced with lean cells on 2026-08-29; the full prior texts,
verbatim, so nothing is lost. Each is one table row.

## Security board row — prior history (moved 2026-08-29)

| [Open security findings](2026-08-22-backend-cleanup-spike.md#open-security-findings) | **2 open, 0 HIGH, and both are blocked on the user** — so **nothing here is actionable**. S-14 and S-16 are product calls (named in the classification table), not research: neither can be settled by reading code. **The actionable security board is empty for the first time since 2026-08-25.** **Both shipped 2026-08-28**: S-18 ([#232](https://github.com/themancalledzac/edens.zac.backend/pull/232)) and S-17 ([#233](https://github.com/themancalledzac/edens.zac.backend/pull/233)), each mutation-verified, each test-dominated exactly as this row predicted — the estimate rule finally held two in a row. S-18 shipped as specified; **S-17 did not** — its stated fix was to widen `RateLimitFilter` past `/api/public/` and the user directed a dedicated limiter instead, so the run of "as specified" ends at five. **next: not from this board.** The next item comes from *Tests that cannot fail* (four still open) or the bug list. **S-20 shipped 2026-08-28** ([#230](https://github.com/themancalledzac/edens.zac.backend/pull/230), +97/-30), mutation-verified, as specified. Its 2026-08-27 count correction was itself wrong and was corrected 2026-08-28 — the recorded escaped-dot grep returns six and always did; seven comes only from an *unescaped* `.` matching the `#` in `{@link UserStatus#ACTIVE}`, so three passes argued about a number while running different commands (working rule 31). Moot now: #230 deleted that javadoc line and the sweep returns four, all code. *Prior history, kept:* (reopened 2026-08-25 with 11; S-19 settled the same day). The split full-board review attacked the closed set as a group and found what nine single-item reviews could not -- see S-10 through S-21 below. **Both HIGH findings are closed**: S-10 ([#221](https://github.com/themancalledzac/edens.zac.backend/pull/221)) and S-11 ([#222](https://github.com/themancalledzac/edens.zac.backend/pull/222)), both 2026-08-25. S-21 was filed while costing S-10's guardrail. **Shipped 2026-08-26**: S-15 ([#224](https://github.com/themancalledzac/edens.zac.backend/pull/224)) and S-12 ([#225](https://github.com/themancalledzac/edens.zac.backend/pull/225)). Sequencing S-15 first on working rule 27 was right -- the 2026-08-25 correction to it was what made it cheap, since its originally-stated fix named a method that does not compile. **Shipped 2026-08-27**: S-13 ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227)) and S-21 ([#228](https://github.com/themancalledzac/edens.zac.backend/pull/228)). The nine originally-closed items are still closed: S-1 ([#192](https://github.com/themancalledzac/edens.zac.backend/pull/192)), S-2 ([#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)), S-3 ([#195](https://github.com/themancalledzac/edens.zac.backend/pull/195)), S-4 ([#196](https://github.com/themancalledzac/edens.zac.backend/pull/196)), S-7 ([#199](https://github.com/themancalledzac/edens.zac.backend/pull/199)), S-9 ([#200](https://github.com/themancalledzac/edens.zac.backend/pull/200)), S-8 ([#204](https://github.com/themancalledzac/edens.zac.backend/pull/204)), S-5 ([#206](https://github.com/themancalledzac/edens.zac.backend/pull/206)) and S-6 ([#207](https://github.com/themancalledzac/edens.zac.backend/pull/207)). |

## Cross-repo row — prior history (moved 2026-08-29)

| [Cross-repo findings owed to the frontend](#cross-repo-findings-owed-to-the-frontend) | **0 open. This board is closed.** All four done 2026-08-24: `collectionDate` ([#157](https://github.com/themancalledzac/edens.zac.backend/pull/157)), `isPasswordProtected` ([#209](https://github.com/themancalledzac/edens.zac.backend/pull/209)), `share/email` ([#213](https://github.com/themancalledzac/edens.zac.backend/pull/213)) and actuator hardening ([#214](https://github.com/themancalledzac/edens.zac.backend/pull/214)). **Nothing is owed to another team.** `share/email` closed the last live 404 in shipped frontend UI and taught working rule 24. **next: nothing here** -- the next item comes from the security board (**S-20** as of 2026-08-27; this row has now named S-15, then S-13, both since shipped), not from this one. **The recurring fix for this row is to stop naming an item at all**: it is a closed section, so any pointer it carries is a copy of the security row's, one edit behind. It now says "see the security board row" and nothing more. *(Corrected 2026-08-25: this row pointed at "MR 19 #16 or MR 16 #4/#5" and MR 19 #16 shipped as [#216](https://github.com/themancalledzac/edens.zac.backend/pull/216) the day before. A next-pointer inside a closed section is exactly the kind that rots unwatched, because nobody re-reads a board row marked done.)* |

## Wave rows 3-5 — prior texts (replaced 2026-08-29)

| 3 — Security hardening | MR 10-11 | **complete** — [history](2026-08-22-backend-cleanup-history.md#wave-3--security-hardening) (#175, #176). **Superseded by the 2026-08-24 review**: see "Open security findings" below, which now holds six items including two HIGH ones. |
| 4 — Comments and docs | MR 12-14 | **mostly complete** — [history](2026-08-22-backend-cleanup-history.md#wave-4--mr-12-and-mr-13-complete) (#177, #178, #180, #181, #183, #184) and MR 14 ([#187](https://github.com/themancalledzac/edens.zac.backend/pull/187)) below. **Wave 4 removed 500 comments for -1,026 words across seven MRs.** MR 14 found the wave rule does not fit hardened files and produced working rule 12; its stale-docblock **items** (four, not one) are still open. |
| 5 — Consolidations | MR 15-19 | MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). #6 closed the `PersonRepository` carry and taught working rule 14; its own guard was later found to have a bypass (security finding S-2, closed [#193](https://github.com/themancalledzac/edens.zac.backend/pull/193)). The last MR 15 follow-up closed 2026-08-24 ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210)) -- **MR 15 is fully done**; the `getContext().getAuthentication()` grep returning four sites is its completion condition and is satisfied. MR 19 #16 shipped 2026-08-25 ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)) -- 201 queries to 1, and the board's suggested WHERE clause turned out to drop the parent scope. MR 19 #14 shipped 2026-08-25 ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) -- two queries to one, and **the first item in seven to need no adjustment at implementation time**, which is what broke the streak the full-board review's case rested on. **next: MR 16 #4/#5 (zero test coupling)** -- still outranked by the security board, though both HIGH findings closed 2026-08-25 (#221, #222) and what remains there is MEDIUM. |

# Full-board review reports (moved 2026-08-29)

Recommended 2026-08-24; run 2026-08-25 as two of three slices; restated 2026-08-27 and
2026-08-28; **executed in full 2026-08-29 as the 9-agent split across both repos** -- the
tracker's session log carries the result, so the standing recommendation below is discharged.

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


### Full-board review: recommendation restated 2026-08-28, and the case got stronger

**Still not run. Run slices 1 and 5. Skip the per-item re-estimate slice.** The 2026-08-27 note
below stands; what changed in one day is the evidence for condition 1, which was the weaker of the
two.

The S-20 close-out found **three more wrong recorded numbers without going looking for them**:

1. S-20's own 2026-08-27 count correction was wrong, and wrong in a way that accused an earlier pass
   of negligence it had not committed. Escaped versus unescaped dot. Three consecutive passes
   argued about that number while running different commands.
2. The suite total. The board recorded 1,397 after #228; `mvn clean install` on `main` at `dd0d7d0`
   reports **1,399**.
3. The `Optional.get()` inventory's Atomic exclusion count is **five, not eleven**, which makes the
   Optional subset ~53 rather than 46. That bullet has been re-derived four times and nobody
   re-checked the subtrahend. **This one is outside the neighborhood of anything that recently
   merged**, which is the specific trigger the escalation list names.

The pattern across all three is sharper than "drift": **every one of them is a number that a
previous pass wrote down as verified, and two of them are corrections that introduced a new error
while fixing an old one.** The doc's own line -- "single-item re-checks are re-reading the item, not
re-running it" -- is the diagnosis, and slice 1 should be scoped to act on it. **Re-run every
recorded command and re-measure every recorded count, rather than only chasing `file:line` refs.**
Refs are the cheap half of the problem and, on the evidence of this week, the less broken half.

Condition 2 is unchanged and now covers five merged fixes rather than four. S-20 adds two call sites
to a predicate that S-8 and S-15 already coupled to `AdminUserController`, and the S-13/S-12
placement coupling recorded under working rule 30 has an untested analogue in that widened surface.

**Honest note on payoff, so the cost is not oversold:** with S-20 closed the actionable security
surface is two MEDIUM items. This review's return is mostly the board's own reliability rather than
new security findings. That is still worth buying, because the board's entire value is being trusted
later, and it has now been wrong about a number on four consecutive passes.

### Full-board review recommended again, 2026-08-27

Not run. Two of the escalation conditions hold, and the skill's bar is two:

1. **Drift has outrun the scoped check.** MR 19 #17's `UserInviteService` refs drifted under #224 and
   the 2026-08-27 sweep found them -- but #224 merged *before* the #226 close-out, which was scoped
   to exactly those files and did not catch them. A ref that survives the sweep aimed at it is the
   definition of the cheap check failing.
2. **Security-relevant work has merged as a set with nothing reviewing it as a set.** S-15, S-12,
   S-13 and S-21 all shipped since the 2026-08-25 review, all four touching `AdminUserController`,
   `UserInviteService` or `RoleRepository`, and three of the four turn on the *interaction* between
   status writes, sweeps and guards -- exactly the class of defect the 2026-08-25 split review found
   by attacking the closed set as a group and that nine single-item reviews had missed. S-13 and S-12
   are already a documented instance of two shipped fixes whose placement is coupled (working rule
   30). Nothing has checked the other pairs.

Also worth noting though not itself a condition: **S-20's count was wrong for four days and survived
a re-verification that cited the command**. Single-item re-checks are re-reading the item, not
re-running it.

Against running it: the last one was only two days ago, the board's actionable security surface is
down to three MEDIUM items, and no estimate has blown out in three consecutive items. **The
recommendation is to run slices 1 and 5 only** -- the unscoped ref sweep, which is cheap and which
condition 1 says is now overdue, and the adversarial re-review of the four merged security fixes as
a group, which is what condition 2 asks for. Skip the per-item re-estimate slice; working rule 27's
streak has inverted and there is no evidence it is needed.

# Wave 4 and Wave 5 tracker detail (moved 2026-08-29)

Moved from the tracker 2026-08-29 by the full-board-review close-out. In-body cross-references
("above", "below", "in this same document") describe positions on the tracker as of the move.

## Wave 4 preamble and rule, as the tracker carried them

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

## MR 15 #2 and #6 — tracker bullets as they stood

- [x] #2. One SecurityConfig matcher instead of the copy-pasted `isRealUser` guards. **DONE** ([#189](https://github.com/themancalledzac/edens.zac.backend/pull/189)). **17 guards, not 18** -- the re-derivation counted a javadoc line in `UserShareControllerProd`. The matcher went OUTSIDE the enforce-authz toggle, next to `/api/auth/me`: the guards it replaced were unconditional, so that is the only behavior-preserving placement, and the guardrail's "costs a dev convenience" was false -- dev already required a session on these routes. A flyby now gets 403 rather than 401 there, by decision. Java-only main -42; 28 controller-level assertions became `config/UserRoutesAuthorizationWebMvcTest`. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-2-outcome-2026-08-23).

- [x] #6. `currentUserId` is duplicated. **DONE** ([#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)). Four copies became `config/CurrentUser.userId()`, joining `ClientIp` and `GalleryAccessCookies` as a static helper next to the security plumbing. The item's "move it onto `AuthPrincipal`" does not work -- that is a Spring-free record and this is a static context read. The null contract was left alone and costed instead: the four admin sites break local dev only, the two read-surface sites 500 a logged-out visitor, so it is two problems and not one. Java-only main -26 lines / +36 words. Two more copies of the same read were found and deliberately not folded in (`SyntheticCollectionResolver.currentPrincipal`, `CollectionService.viewerMaySeeHidden`) -- see rule 14. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-15-6-outcome-2026-08-24).

## MR 15 #6 follow-up — tracker detail

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

## MR 19 #14 outcome (2026-08-25)

Shipped as [#218](https://github.com/themancalledzac/edens.zac.backend/pull/218). The tracker
bullet in full -- the only merged code MR since #187 that had no history outcome entry until this
move (working rule 38's gap, closed 2026-08-29):

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

## MR 19 #16 — tracker bullet as it stood

- [x] #16. `findCurrentContentCollections` N+1. **DONE** ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)).
  201 queries -> 1. The diagnosis was exact; **the suggested fix was not, and would have shipped a
  silent bug**. `cc.id IN (:ids) OR cc.referenced_collection_id IN (:ids)` drops the parent scope
  the loop had for free by construction, so it matches blocks linked under a different parent --
  `removeContentFromCollection` is parent-scoped and would delete nothing, but `onChildUnlinked`
  would still fire role-grant propagation for a link that never existed. Test coupling was two
  stub lines, not one. [Full write-up](2026-08-22-backend-cleanup-history.md#mr-19-16-outcome-2026-08-25----the-suggested-clause-was-the-bug).

# Working rules — original narratives (moved 2026-08-29)

The tracker now carries distilled rules under the same numbers; these are the full originals,
moved verbatim. The intro below is the pre-2026-08-29 intro -- its rule-12 claim is superseded by
rule 37.

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

12. **SUPERSEDED 2026-08-28 by working rule 37 -- do not follow this rule.** *Promote a fact about the method; keep a warning about a line.* Rule 10 said measure before
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

30. **A sweep keyed on the row's current state guards one direction of a transition, and its
    placement is what picks the direction.** S-12's `dropMembershipsIfPerson` has to run *before* the
    status write: it deletes only while the row is still a PERSON, which is exactly the window that
    catches `PERSON -> account`. That placement makes it structurally incapable of catching
    `account -> PERSON`, because at sweep time the row is still an account and the statement matches
    nothing.

    This is not a defect to fix by moving the call. Move it after the write and the two directions
    simply swap -- S-13's hole closes and S-12's reopens, with every S-12 test still green, because
    those tests seed a PERSON and would keep passing right up until the flip stopped being swept.
    Two sweeps would be needed to cover both, and then the second one deletes a real account's roles
    on the way to PERSON, which is a data-loss decision rather than a guard.

    The general form: **a guard that reads mutable state is really a guard on a moment**, and the
    moment is chosen by where the call sits relative to the mutation. Before writing one, say out
    loud which transition it covers and which it therefore does not -- then check whether the
    direction it does not cover is somebody else's open item. Here it is S-13's, and S-13's own fix
    (refuse `PERSON` in the request at all) is better than either sweep, because it closes both
    halves at the input end instead of fighting the ordering.

    The corollary is about reading a board, not writing code: when an item ships, do not assume it
    subsumes a neighbouring item that names the same method. #225 edits the exact line S-13 is about
    and closes none of it.

31. **A grep-based count that includes comments will drift on the next docblock edit, and the item
    will assert a number its own stated command does not return.** S-20 recorded "grepping
    `UserStatus.ACTIVE` across `src/main` returns exactly six hits" on 2026-08-26. It returned seven
    then and returns seven now: the extra is `WebAuthnService:179`, a javadoc `{@link
    UserStatus#ACTIVE}` added by #199 on 2026-08-24, two days *before* the count was written down.
    The premise survived -- there really are six code sites -- but the verification everyone was
    meant to re-run says otherwise, which means the next session either "finds a new site" that
    isn't one, or stops trusting the item.

    This is the **second** time on this board. The first was the 2026-08-23 re-derivation raising
    the `isRealUser` guard count 17 -> 18 on the strength of `UserShareControllerProd:34`, also a
    javadoc line. Both times the count was defended as mechanical *because* it came from a grep.

    So: **when a count is the item's evidence, the recorded command must exclude comments**, and the
    item should say what the raw number is too, so a future re-run that gets the bigger number
    recognizes itself instead of filing a finding. For Java here, appending `| grep -v '\*'` is
    enough. State it as "seven raw, six code" rather than "six".

    **Corrected 2026-08-28, and the correction above was itself wrong.** The 2026-08-26 count of six
    was *right for the command the item recorded*. `grep -rn 'UserStatus\.ACTIVE'` -- escaped dot --
    returns six and always did. Seven comes only from the **unescaped** `UserStatus.ACTIVE`, where
    `.` is a regex wildcard matching the `#` in `{@link UserStatus#ACTIVE}`. So the 2026-08-27 pass
    ran a different command than the one it accused the earlier pass of not running, then filed the
    discrepancy as that pass's error. Nobody had failed to run anything.

    Worse, the prescribed remedy `| grep -v '\*'` returns six from a six-line input -- the right
    number for the wrong reason, working rule 32 happening inside working rule 31's own text.

    **The real rule is narrower and more useful than "exclude comments": a recorded grep must be
    recorded exactly as run, escaping included, because an unescaped `.` silently matches `#`, `$`
    and every other separator a Java reference can use.** Comments are one way to get a phantom hit;
    regex metacharacters are the way that bit this board, and the `isRealUser` precedent should be
    re-checked against the same possibility rather than assumed to be the same trap. Three passes
    argued about this count and all three were reasoning about different commands.

32. **A mutation that reddens the test is not evidence until you check *why* it reddened.** S-13's
    rejection test initially failed under mutation with `expected:<400> but was:<404>` -- the right
    color for the wrong reason. `findById` was unstubbed, so removing the constraint just let the
    request reach a controller that 404'd on a missing row; the test would have passed identically
    against a build that wrote `PERSON` to a row that *did* exist. Stubbing `findById` with
    `lenient()` -- deliberately stubbing something the passing path never reads -- moved the mutation
    result to `200`, which is the actual defect.

    The general form: **a mutation test proves the guard exists only if the mutant fails at the guard
    and then proceeds to do the wrong thing.** A mutant that dies earlier, on a fixture gap, proves
    the fixture is thin. Read the failure message, not the pass/fail bit. This is working rule 15's
    problem in the mirror -- rule 15 is a test that cannot fail, this is a test that fails for a
    reason unrelated to what it claims to test, and both report coverage that is not there.

    Related mechanical trap, cost a wrong turn the same session: restoring a mutated source file
    with `mv file.bak file` preserves the backup's **older mtime**, so `maven-compiler-plugin` skips
    recompiling and the next run silently tests the mutated bytecode. The symptom is a test failing
    after you restored the fix. Use `touch` on the restored file, or `git checkout --` **only if the
    change is committed** -- on an uncommitted edit that command deletes the work.

    Second mechanical trap, found 2026-08-28 by S-20: `mvn test` runs **spotless before the tests**,
    so a hand-edited mutation that breaks formatting reddens the build without a single test having
    run. The output is a diff, not an assertion failure, and it is easy to skim as success. Mutate
    with `-Dspotless.check.skip=true`.

33. **A test that derives its cases from the thing under test cannot detect that thing widening.**
    S-20 routed two inlined status checks through `SessionService.mayHoldSession`, then made both
    parameterized login tests build their case lists by asking `mayHoldSession` which statuses it
    refuses. That is genuinely drift-proof for the **call sites** -- add a fifth `UserStatus` and
    both login doors get covered automatically -- and completely blind to the **definition**.
    Mutating the predicate to `!= DISABLED` did not fail the tests; it made them emit fewer cases.
    `AuthControllerTest` went 13 -> 11 and `WebAuthnServiceTest` 12 -> 10, both green, both now
    testing less than before the mutation.

    This is a third species alongside working rules 15 and 32. Rule 15 is a test that cannot fail.
    Rule 32 is a test that fails for the wrong reason. This is a test that **quietly stops asking**
    -- the suite total drops and nothing turns red, so the only visible signal is a number nobody
    reads.

    **The fix is not to abandon derivation, which caught a real gap here** (the hardcoded list it
    replaced had silently omitted `PERSON` for months). It is to pair it with exactly one literal
    pin of the definition, placed where the definition lives -- for S-20,
    `SessionServiceIntegrationTest.mayHoldSessionAdmitsActiveAndNothingElse`. Derived sources for
    the call sites, one written-out allowlist for the rule. **Watch the case count when mutating a
    parameterized test**: a green run with fewer cases than the baseline is a failure wearing a pass.

    Asymmetry left open by S-20 and worth closing when someone is next in that file:
    `UserInviteService.mayAcceptInvite` has no equivalent pin. It is covered by named per-status
    cases in `UserInviteServiceAcceptTest`, so a fifth `UserStatus` reddens the session predicate's
    pin and nothing on the invite side.

34. **An allowlist is not defence in depth when the allowlist is the thing an attacker overwrites.**
    S-18 raised the obvious question: why keep a twelve-name actuator exclude list at all, when
    `management.endpoints.web.exposure.include=health` already names one endpoint and never has to
    chase whatever Spring Boot ships next? The include-only model was considered and **rejected**.

    Boot resolves `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` from the environment *above*
    `application.properties`, which is working rule 1. A stray `INCLUDE=*` in a deployed `.env`
    replaces the include value outright. The exclude list applies after include and survives that;
    include-only has nothing left to apply. The allowlist cannot defend the one scenario the layer
    exists for, because in that scenario the allowlist is what was replaced.

    The trade is also not symmetric. A missing name in the denylist exposes one endpoint and the fix
    is one word -- that is exactly what S-18 was. One injected env var under include-only exposes
    every endpoint at once. **And include-only would not have prevented S-18**: `include=health`
    already left those four off, so they were reachable only under the injected-wildcard scenario.
    The finding lived entirely inside the layer include-only proposed to delete.

    **The real follow-up, filed not built:** a `ProdSecretGuard`-shaped boot check that reads the
    *resolved* exposure include and refuses to start unless it is `health`. That defends the
    injected-env case without enumerating a single endpoint name, and it is the only thing that
    would make both the exclude list and `MUST_BE_EXCLUDED` deletable.

    Generally: before replacing a denylist with an allowlist, ask what overrides the allowlist.

35. **A green unit-test run is not evidence the change works. Mutate the test you just wrote, and
    run the full build before claiming a wiring change is done.** #233 produced two failures of this
    in one MR, from opposite directions.

    **The test that could not fail.** `ShareEmailLimiterTest` got a case asserting the global cap is
    checked before the per-sender bucket, so refusals do not drain a sender's budget. It passed. The
    mutation -- swap the two checks -- **also passed**. The test spent the global cap and never let
    it refill, and once the cap is spent both orderings refuse everything forever, so no assertion
    could separate them. The fix was a global period short enough to refill inside the test, which
    makes the drained token observable. This is a fourth species alongside rules 15, 32 and 33: not
    derived from the thing under test, not failing for the wrong reason, but **observing the system
    only in a state where every variant behaves identically**. Writing the test is not the check.
    Running the mutation is.

    **The wiring break every unit test was blind to.** `ShareEmailLimiter` has a package-private
    `Duration` constructor for timing tests, which makes two constructors. Spring then looked for a
    default one and **the application context failed to start** -- every integration test in the
    tree errored. Every unit test stayed green, because they all build the limiter by hand. A bean's
    own unit tests cannot see a wiring break by construction; only the full build can. `@Autowired`
    on the property constructor, exactly as `ClientGalleryAccessLimiter` already does. Mirror the
    sibling completely, not just its shape.

36. **The two security-count cells drift because they are edited one at a time.** The section row
    and the category row both state the open count. They have now disagreed three times.
    [#231](https://github.com/themancalledzac/edens.zac.backend/pull/231) is the cleanest case: it
    correctly moved the section row to "4 open, 2 actionable" and left the category row reading "5
    open, 3 actionable", so a doc MR whose entire purpose was correcting recorded numbers shipped a
    fresh disagreement between two cells forty lines apart.

    The check is one command and it is not optional: `grep -c '^- \[ \] \*\*S-'`. **Run it, put
    the number in both cells, and do not edit either cell without editing the other.** The board has
    twice written a rule about counting and twice miscounted afterwards, so the rule is now about
    the edit being atomic rather than about being careful.

37. **Never write inline comments. This supersedes working rule 12.** Standing instruction from the
    user, 2026-08-28, after finding them in [#233](https://github.com/themancalledzac/edens.zac.backend/pull/233).
    No `//` inside method bodies, constructors, test methods, or against fields -- not to explain a
    decision, justify a design, mark a section, or record why a test is shaped the way it is. There
    is no threshold of importance that earns one. Javadoc is the only place prose goes in a source
    file; anything that does not fit in a docblock belongs in the PR description or in this document.
    When editing any file for any reason, delete the inline comments already in it.

    **Working rule 12 said the opposite** -- that a comment anchored to a specific line
    (`RoleRepository`'s `\s` notes, `AdminBootstrap`'s "do not fix this into a single statement")
    should survive because a docblock cannot hold it. That reasoning is now void. Rule 12 is
    retained above only as history and must not be followed; the files it named still carry the
    comments it protected and are owed a sweep.

    The trap worth recording: the comments hardest not to write are the ones that feel
    load-bearing -- a mutation-verification note, a check-ordering rationale, a cache-TTL choice.
    That feeling is not an exemption, it is the signal. #233 shipped 31 of them, every one written
    deliberately.

38. **A close-out is two files. It is not done until the history file has the outcome.** Working
    rule 11 already said this and **three consecutive close-outs ignored it**: #229, #231 and the
    first draft of #234 all wrote their outcome detail into the tracker and sent nothing to
    [`2026-08-22-backend-cleanup-history.md`](2026-08-22-backend-cleanup-history.md). That file's
    last entry was S-12 on 2026-08-26 while five MRs shipped -- #227, #228, #230, #232, #233.

    Rule 11 failed as written because it describes where prose *belongs* and nothing checks that it
    arrived. A tracker that keeps growing still looks like a tracker being maintained, and the file
    that stopped changing is the one nobody opens. Backfilled 2026-08-28; the three older entries
    are marked *backfilled* because they are reconstructed from what the tracker recorded rather
    than measured fresh, and that distinction must survive.

    **The check is the diff, not the intention.** A close-out MR touching only one of the two files
    is wrong on its face. `git show --stat` must list both. If an MR genuinely has no outcome to
    record -- a pure correction, say -- write that in the commit message rather than leaving the
    single-file diff to be read as an oversight.

    This is the second rule on this board about a check that exists and does not get run (see rule
    36). Both have the same fix: make the omission visible in the diff rather than asking for care.

39. **A commit pushed to a branch after its PR merged goes nowhere, silently.** Working rule 28
    covers a *stacked* PR stranded when its base merges first. This is the simpler sibling and it bit
    on 2026-08-28: #232 merged at 21:12Z, the rule-37 comment sweep was pushed to
    `fix/s18-actuator-exclude` afterwards, and GitHub showed neither an error nor an updated PR --
    the branch just quietly diverged from what shipped. #233 was still open when its identical sweep
    landed, so that one merged. Two branches, the same edit, opposite outcomes, and nothing in either
    PR said so. Filed as R-1.

    **The check: after pushing to a branch whose PR you did not just open, confirm the PR is still
    OPEN.** One command -- `gh pr view <N> --json state`. And when a rule lands mid-session and you
    sweep several branches for it, sweep them in one pass and check each PR's state, because the
    branch you swept first is the one most likely to have merged while you worked on the others.

    The wider trap: a green `main` build proves nothing about work that never reached `main`. Both
    sweeps were verified by a full build on their own branch, and one of those branches was already
    dead.


## Tests that cannot fail — CLOSED 2026-08-30 (moved from the tracker)

All six closed. The last three went in one session: #239, #240, #241. Each was mutation-proved
against `main` first, so the queue's premise -- that these tests could not fail -- is now
evidenced rather than asserted.


Working rule 15 says a regression test that cannot fail is worse than none because it reports
coverage. The review checked the security tests against that standard and six failed it.

**Status 2026-08-29: three closed, three open, and this is still the board's first queue** (S-22
and S-23 joined the actionable set behind it on 2026-08-29). **Two of the three open items are
missing coverage** of a security behaviour that is live in `main`; the third is the
`AdminUserControllerTest` comment relocation. Each already carries the mutation that should redden
it, so none of them needs re-derivation before someone starts.

- [x] **Closed by [#232](https://github.com/themancalledzac/edens.zac.backend/pull/232), 2026-08-28.**
  `ActuatorExposureEndToEndTest` iterated the denylist itself, so an omission like `caches` was
  structurally invisible, and it supplied its own exclude via `@SpringBootTest(properties=...)` --
  **no change to `src/main` could redden it.** The loop now iterates
  `ActuatorExposureTest.MUST_BE_EXCLUDED`, the expectation list, which the config cannot edit.
  Sharing that one list rather than copying it keeps the tree at two denylists (the expectation and
  the shipped literal) instead of three. Mutation-verified: dropping `caches` from the properties
  file **and** the literal together -- the exact mutation the old loop could not see -- reddens it
  with `/actuator/caches is reachable with include=*, expected 404 but was 200`. This is working
  rule 33's species and it was found before rule 33 was written.
- [x] **CLOSED by [#241](https://github.com/themancalledzac/edens.zac.backend/pull/241), 2026-08-30.**
  **The board's own suggested pointer was wrong and was corrected while closing.** The item said to
  name `mayHoldSessionAdmitsActiveAndNothingElse`. That test does not redden on this mutation -- it
  pins `mayHoldSession` itself, which the mutation leaves alone. Verified by running both:
  keying the sweep off `mayAcceptInvite` reddens `revokeAllForStatusRevokesOnDemotionToInvited`
  (`AdminUserControllerTest` stays green at 54); widening `mayHoldSession` to `!= DISABLED` reddens
  the enum pin plus two others (`AdminUserControllerTest` again green at 54). The docblock names
  both, each against the mutation it actually catches -- naming only the enum pin would have
  replaced one false attribution with another. Original text:
- [x] *(superseded)* `AdminUserControllerTest.demotingUserToInvitedRevokesSessionsButKeepsInvites` cannot catch what
  its comment claims: `SessionService` is a mock and `mayHoldSession` is static. The mutation is
  caught by `SessionServiceIntegrationTest` instead. **False attribution, not a missing test** --
  worth fixing the comment so the next reader does not trust the wrong test.

  **Re-verified 2026-08-28** (S-20 changed `mayHoldSession`'s call sites, so this is in the
  neighborhood). Claim intact: the comment at `AdminUserControllerTest:1059` still reads "Mutation
  this catches: key the session sweep off `mayAcceptInvite` and this goes red", and the test still
  mocks `SessionService`. Still a one-line comment fix, still open. **The correct pointer is now
  more specific than it was**: #230 added
  `SessionServiceIntegrationTest.mayHoldSessionAdmitsActiveAndNothingElse`, which is the test that
  actually reddens on that mutation, so the pointer should name it rather than gesturing at the
  file. **Re-worded 2026-08-29 for working rule 37: the fix is delete-and-relocate, not an edited
  inline comment** -- delete the comment block and put the corrected pointer in the test's
  docblock.
- [x] `WebAuthnServiceTest` covers DISABLED only. Rewriting the guard as `== DISABLED` stays green
  while admitting INVITED and PERSON passkey logins, both reachable. `AuthControllerTest`
  parameterizes over both and does catch it; this one should too. **DONE**
  ([#230](https://github.com/themancalledzac/edens.zac.backend/pull/230), 2026-08-28) -- closed as a
  side effect of S-20, since both tests had to be touched to route the guards through the predicate.
  Both now derive their cases from `mayHoldSession` and run all three ineligible statuses.

  **The item's own premise was half wrong, in the direction that made it look smaller.**
  "`AuthControllerTest` parameterizes over both and does catch it" -- it parameterized over
  `{DISABLED, INVITED}` via a hardcoded `@EnumSource(names = ...)` and **omitted PERSON**. So both
  tests had the gap, not one, and the item's own comparison was pointing at a second instance of the
  defect as if it were the fix. A `PERSON` row has no password hash, so the login path was covered
  by an adjacent clause rather than by the status test -- which is exactly why nobody noticed. The
  passkey path had no such backstop.

  Carried forward as **working rule 33**: the replacement derives its cases from the predicate,
  which fixes the omission permanently but introduces a blind spot of its own, so the allowlist is
  now pinned separately.
- [x] **Closed by [#235](https://github.com/themancalledzac/edens.zac.backend/pull/235), 2026-08-28.**
  Outcome: [history](2026-08-22-backend-cleanup-history.md#s-3-test-outcome-2026-08-28----the-surviving-side-was-tested-with-one-status).
  `PersonRepositoryIntegrationTest` (S-3's whole deliverable) seeded both accounts ACTIVE, so
  mutating `status = 'PERSON'` to `status <> 'ACTIVE'` passes while making every INVITED and DISABLED
  account deletable through the people-delete endpoint. The mutation S-3 stated does redden it; this
  one does not.
- [x] **CLOSED by [#239](https://github.com/themancalledzac/edens.zac.backend/pull/239), 2026-08-30.**
  Premise held exactly. **Mutation-proved both ways**: with `@Component` deleted, `main`'s version
  reported `Tests run: 13, Failures: 0, BUILD SUCCESS` -- the gap was real, not theoretical -- while
  the replacement reddens 4 of 5. Moving the class out of `edens.zac.portfolio.backend` also reddens
  4. `@PostConstruct` still reddens the refusal cases, unchanged. **Trap recorded:** the move
  mutation cannot be run as stated -- the constructor is package-private, so relocating breaks the
  eight predicate cases at compile time and checkstyle fires first; the run dies with no `Tests run:`
  line and reads as a reddening while proving nothing (the S-3 species). Widen the constructor and
  skip checkstyle to get a real result. Original text:
  `ProdSecretGuardTest.java:117` still reads
  `new ApplicationContextRunner().withUserConfiguration(ProdSecretGuard.class)` -- the class is
  named directly, so no case depends on component scanning finding it.
  `ProdSecretGuardTest.Wiring` registers the guard class by hand, so moving it out of the
  component-scanned package keeps every case green while prod boots unguarded. The two mutations S-4
  stated do redden it. **Count corrected 2026-08-25: five wiring cases, not four** -- #222 added
  `prodRefusesToStartOnTheDefaultDevAccessTokenSecret`. The item is unchanged in substance and
  slightly worse in scale: the new clause is guarded by the same hand-registration, so
  `withUserConfiguration` still stands between this test and the thing it claims to prove.
- [x] **CLOSED by [#240](https://github.com/themancalledzac/edens.zac.backend/pull/240), 2026-08-30.**
  Both `/api/read/share` GETs added to the default-deny cases; `PUBLIC_ROUTES` untouched at nine
  (re-verified 2026-08-30). Mutation: allow-listing `/api/read/share/{token}` leaves `main`'s test
  **27 green** and reddens the new one once.
  **Premise corrected while closing:** the item said #213 put "a bearer token in that response body".
  It did not -- `ShareModels.ShareView` is `(ownerName, page)` and `buildView` fills both from the
  link. The credential is the **`Set-Cookie`**, built by `FlybyCookies.build(rawToken, ..)` on both
  routes, so a shared cache would replay another visitor's raw share token. Sharper, not smaller.
  **Noted, not fixed:** `/api/read/user/share` (GET) is also unlisted and unpinned. Original text:
- [x] *(superseded)* **Checked 2026-08-28: the behaviour is correct, only the pin is missing -- re-sized from a
  possible bug to a guard against a future edit.** `CacheControlInterceptor` stamps `no-store` on
  everything that is *not* allow-listed, and the share routes are not on that list, so the header
  is right today by default rather than by intent. `CacheControlInterceptorTest` has an
  allow-listed case and a password-protected case and **no share case**, so the regression this
  needs to catch is someone adding the share route to the allow-list -- which would be a quiet
  one-line change with a token in the response body. Smallest of the three open items.
  **Nothing pins that the share-link GET is `no-store`.** #213 put a bearer token in that
  response body; the cache-control default-deny list (`CacheControlInterceptor.PUBLIC_ROUTES`) enumerates **nine**
  sibling routes (re-measured 2026-08-29; the recorded six was stale) and not this one.
  Adding it to `PUBLIC_ROUTES` reddens nothing, and the read cache policy sets `s-maxage` for
  CloudFront -- so the failure mode is a shared cache serving one owner's share token to another
  visitor. Default-deny protects it today; nothing guards the edit.



## Rule 37 debt — R-1 CLOSED 2026-08-30 (moved from the tracker)

Closed by #238. The per-package rule-37 sweep it was carved out of is still open and is tracked
in the tracker's Progress category table, not here.


Moved out from under "Open security findings" 2026-08-29 -- comment debt is not a security
finding, and the Progress table already rows it separately.

### R-1: the #232 comment cleanup that never merged

- [x] **CLOSED by [#238](https://github.com/themancalledzac/edens.zac.backend/pull/238), 2026-08-30.** Cherry-picked `d42d24d` and `665bd7d`; `44a9d81` deliberately not picked. Nine lines, build green at 1,414. Both trimmed facts were re-checked as surviving elsewhere before the trim landed. **R-1 (2026-08-28). `main` violates working rule 37 in the two files #232 touched, and the fix
  is sitting on a merged branch.** After the rule-37 instruction landed, both open branches were
  swept. #233's sweep merged with it. **#232's did not: it had already merged, at 21:12Z, and the two
  cleanup commits were pushed to its branch afterwards.** They are still there --
  `origin/fix/s18-actuator-exclude` carries `d42d24d` (remove the inline comments) and `665bd7d`
  (cut the prose from the actuator property comments) -- and neither is reachable from `main`.
  **Corrected 2026-08-29:** `git log origin/main..origin/fix/s18-actuator-exclude --oneline`
  returns **three** commits, not two -- `44a9d81` (the S-18 fix itself) is also unreachable by SHA,
  because #232 squash-merged as `d6ff6a8`, so no branch commit is ever an ancestor of `main`. Its
  *content* is on `main`; only `d42d24d`/`665bd7d` are content-stranded. Record the command with
  the count (working rule 31).

  Verified on `main` 2026-08-28, not inferred: `ActuatorExposureTest.java:76-78` still carries the
  three-line `//` block inside `exposureExclude_namesEverySensitiveEndpoint`, and
  `application.properties` still carries the six-line prose block above
  `management.endpoints.web.exposure.include`. #233's equivalent trims **are** on `main`
  (`app.share.email-per-sender-per-hour` has its bare label), which is what makes the asymmetry a
  merge-ordering accident rather than a decision.

  **Fix: re-apply both -- about nine lines across the two files; do not merge the dead branch.**
  *(The old justification here -- "cherry-picking it would drag its stale copy of the tracker
  along" -- was false and is withdrawn 2026-08-29: no commit on that branch touches `ai_docs/`, and
  `git apply --check` passes clean for both diffs today, so cherry-picking `d42d24d` and `665bd7d`
  works too and touches only the two code files.)* Delete the three comment lines and trim the
  property block to
  `# Actuator Configuration (restrict exposed endpoints)`. Both facts are already in the class
  javadoc of `ActuatorExposureTest` and `ActuatorExposureEndToEndTest`, so nothing here is the only
  copy -- that was checked before the original trim and is why the trim was safe.

  **Guardrail: do not start the wider rule-37 sweep in this MR.** The 567-count row says per package,
  and this item is two files. Nine or ten lines, tests unchanged. **COLD.**



## MR 20 — the bare-array decision, CLOSED 2026-08-30 (moved from the tracker)

Answered by the user: bare arrays are blessed, `.claude/CLAUDE.md` amended in #243, no endpoint
changed. The inventory below is the record of what the rule now permits.


- [x] **CLOSED 2026-08-30 by decision: bare arrays are blessed, no endpoint changes.** `.claude/
  CLAUDE.md` was amended instead of wrapping the 17. The endpoint inventory below is kept as the
  record of what the rule now permits, not as outstanding work. Original text:
- [x] Decide first. **17 endpoints** (the prose said 15; the item's own list has always had 17, and 17 is what a re-derivation finds) return top-level JSON arrays against the stated "objects only" rule: `AdminController:84`; `AdminUserController:152, 366, 421, 434` (**re-derived 2026-08-29**: `listUsers`, `userRoles`, `userSavedImages`, `userFollows` -- the old 328/383/396 had drifted, and 383/396 are now the role-membership pair); `CollectionAdminController:43`; `ContentControllerProd:85, 96, 107, 118, 130` (correct as of 2026-08-29). **Six drifted, re-derived 2026-08-25 and named by symbol**: `AdminRoleController.listRoles` (`48`), `UserFollowsControllerProd.list` (`52`), `UserSavesControllerProd.list` (`50`) and `.listImages` (`56`), `UserSelectsControllerProd.list` (`55`), `UserRatingOverrideControllerProd.list` (`48`). **`UserSelectsControllerProd.list` is carried twice on this board**, here and under MR 22, and only MR 22's copy was corrected on 2026-08-24 -- deduplicate it rather than correcting it in two places. `CollectionAdminController:37` even documents the violation as policy. Either wrap them in one breaking-change MR, or amend `.claude/CLAUDE.md` to bless bare arrays. Today the codebase carries two contradictory conventions.

  **Frontend answer, 2026-08-24, re-measured 2026-08-29: it consumes bare arrays directly** at
  ~14 call sites in 6 files (`app/lib/api/{adminHome,roles,users,personal,selects,content}.ts`,
  typed as `T[]`; the old "20 call sites" predates the FE's `clientFetch` rewrite, #333/#334). So
  wrapping is breaking for 13 of the 17. Backend cost is 17 source sites against **92 array-shape
  assertions in 25 test methods across 8 files**, plus ~15 frontend test files. **Cross-repo
  visibility (2026-08-29): the FE board carried no counterpart row** -- this decision was invisible
  from the repo the breaking change lands on; the 2026-08-29 review filed the FE-side row, blocked
  on the same user decision.

  **The de-risking split the item does not offer:** four of the 17 have **no frontend consumer at
  all** -- `/api/read/content/people`, `/cameras`, `/lenses` and `/api/read/user/ratings`
  (**corrected 2026-08-29**: that is the route `UserRatingOverrideControllerProd` actually maps;
  the old text said `rating-overrides`. It also has no backend controller test). Those four can be wrapped today with zero
  coordination, which settles the convention question in code before negotiating the breaking 13.


**Refs re-derived 2026-08-30 during close-out.** Five drifted by -1 -- `AdminController` 85->84 and
`AdminUserController` 153->152, 367->366, 422->421, 435->434 -- because #243 edited docblocks near
the top of both files. A docblock-only diff moves line numbers exactly like a code change.
`CollectionAdminController:43` and `:37`, in the same inventory but in an untouched file, did not
drift. Command: `grep -nE 'ResponseEntity<List<' <file>` plus `grep -n listUsers`.


## Decisions answered 2026-08-30 (moved from the tracker)

All three answered by the user in one batch and shipped in
[#243](https://github.com/themancalledzac/edens.zac.backend/pull/243).

- [x] **Should `app.admin.enforce-authz=true` become unconditional? ANSWERED 2026-08-30: yes, and
  the toggle is gone.** The property was deleted rather than pinned to `true` -- a flag that can
  only hold one value is dead config. `SecurityConfig` now gates `/api/admin/**` on `hasRole(
  "ADMIN")` and `/api/edit/**` on `hasRole("USER")` in every profile, `EditAccessWebConfig` always
  registers its interceptor, and `ProdSecretGuard`'s third check went with it -- there is no longer
  an env var for it to guard against. **Local dev is no longer login-free on the write surface**;
  that was the accepted cost. Deleted with it: `AdminAuthorizationDisabledWebMvcTest` and
  `EditAuthorizationDisabledWebMvcTest`, which existed only to pin the disabled path, plus two
  `ProdSecretGuardTest` cases. The four admin `currentUserId` null sites (`AdminRoleController`
  68/123/151, `AdminUserController` 261/384) are closed by construction and needed no edit of their
  own. The two public-read null sites were not touched, per the original split. Original text:
  *(New row 2026-08-24. MR 15 #6
  costed the `currentUserId` null contract and found it is two problems, not one: the four
  `/api/admin/**` null sites exist only because the gate falls through to `permitAll` in dev. Making
  the flag unconditional closes all four properly. That is a dev-ergonomics decision, not a
  consolidation, which is why it is here and not in Wave 5.)* Trade-off is local admin convenience
  against an always-on admin gate. The two public-read null sites are correct as they stand and
  should not be touched either way.
- [x] **Should `parseImageDate` stay permissive? ANSWERED 2026-08-30: no -- strict and normalized,
  but falling through rather than throwing.** The month-13 framing was real but oversold: no
  Lightroom-exported JPEG produces it, and the genuine failure was that the parser took the first
  two numeric runs with no range check at all, so any string whose leading numbers were not a year
  and a month was accepted. It now range-checks (month 1-12, year 1826..next year) and treats an
  implausible value exactly like an unparseable one, so it falls through `createDate` ->
  `modifyDate` -> today. Nothing that parses correctly today changes and no upload starts failing.
  Seven cases added, five of which redden against the old implementation. Original text: *(New row
  2026-08-24. Noted in the history file
  during MR 13 and never given a row; it also replaces the struck EXIF/ISO half of consolidation
  #17.)* It returns **month 13** for a nonsense EXIF date and builds an S3 path out of it. Either
  reject the malformed date or clamp it -- both are behavior changes, so this needs a decision and
  its own small MR with a month-13 test.
- [x] **Bare-array responses: ANSWERED 2026-08-30 -- CLAUDE.md amended, the 17 endpoints stay as
  they are.** Wrapping was the breaking option and the frontend consumes bare arrays directly, so
  the rule moved to the code rather than the other way round. `.claude/CLAUDE.md` now says a list
  endpoint may return a top-level array, and prefers an object only when the response carries
  something besides the list. Closes MR 20 without a code change.

- 2026-08-29 — **the recommended full-board review ran**, as a 9-agent split across both repos:
  slices 1 and 5 plus open-items, structure and cross-repo slices; the per-item re-estimate slice
  deliberately skipped, per the board's own note that it should wait for a mis-specified item.
  Suite re-measured **1,414 exact** via `./mvnw clean install` (one-line fact for next time: it
  needs `JAVA_HOME=/opt/homebrew/opt/openjdk`). **8 wrong recorded numbers found and corrected,
  against ~45 that re-measured exact** — worst: the inline-comment debt is **~1,720** whole-line
  comments plus ~72 trailing under the rule-37 criterion, not "567 floor". **The security set held
  as a set: 0 HIGH, 0 MEDIUM** — four LOW findings filed as **S-22** (RoleRepository status
  denylists, unpinned), **S-23** (the rule-34 follow-up, finally a row), **S-24** (admin mail
  sends outside both limiters) and a **passkey-revocation** row under "Decisions needed". **Bugs
  #18/#19 filed from the FE board's archives** (E16's `updateLocation` slug conflict — a generic
  409 today, not the 500 the archive recorded; E13's location-tagged-GIF gap), and the MR 24
  executor-shutdown bug promoted as **#20**. **The 32d2168 misfiling reversed**: "Decisions
  needed", "Stale side branches" and Appendices C/D are back in this file and the Progress links
  resolve again; both file headers now say what each file holds. Restructure applied per the
  review's move-map: working rules distilled (narratives to history), closed security bodies to
  history, #218's write-up to history, the full-board-review reports to history — this review
  executes them; this file went 2,310 -> 1,172 lines (above the move-map's ~930 projection by the
  new S-items, bugs, decision rows and ledger this close-out added). Next: **R-1**, then the
  Tests-that-cannot-fail queue, with S-22/S-23 behind them; ask the user S-14, S-16, S-24 and the
  passkey call in one batch.

# 2026-08-31 close-out — S-14, S-16, S-22, S-23, S-24 and bug #21

Moved from the tracker 2026-08-31. Six items closed in one session, which emptied the security
board: **zero open security findings, and none blocked on the user.** In-body cross-references
("above", "below") describe positions on the tracker as of the move. Tracker bodies first, then the
outcomes.

## S-26 outcome (2026-08-31) -- the fix was one call, and three mutations were needed to prove it

Shipped as [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265). One line in
`AdminUserController.deregisterPasskey` -- `sessionService.revokeAllForUser(id)` below the delete
guard -- plus S-27's docblock narrowing in `ShareLinkService`, six tests and two docblocks. Suite
1,455 -> 1,461.

**The item's fix shape was right, first time in a while.** It said one call after a successful
delete; that is what shipped, at the position it named, with the account-wide blast radius it flagged
and for the reason it gave (`user_session` records no credential id). Nothing about the premise
needed correcting either -- `SessionService.resolve` really does test revoked, expired and
`mayHoldSession` and never reads `webauthn_credential`, so the account stayed ACTIVE and the session
kept resolving.

### What the guardrail was actually protecting against

"The fix is one call; the test is the work" turned out to be a claim about **how many ways this one
line can be written wrong**, not about effort. Three, and each needs its own test:

| Mutation | Killed by | Why it is a real mistake to make |
|---|---|---|
| delete the `revokeAllForUser` call | `deregisterPasskeyRevokesTheAccountsLiveSessions` (mock `verify`), `deregisteringAPasskeyStopsItsSessionResolving:87`, `deregisteringOnePasskeyEvictsSessionsMintedByTheOtherToo:107` | the finding itself |
| hoist the call above the delete guard | `deregisterPasskeyThatIs404RevokesNothing` (mock `never()`), `aFailedDeregistrationLeavesTheAccountsSessionResolving:143` | reads as harmless tidying; makes a guessed credential id a logout |
| drop `user_id = :userId` from the revoke SQL | `deregisteringAPasskeyLeavesAnotherAccountsSessionAlone:127` | one deregistration signs out the whole site |

Sources restored with `touch` after each, per working rule 15.

**The mock tests and the DB tests are not redundant, and working rule 15 says why.** A mock of
`SessionService` can only ever show that the controller *made the call*. What S-26 is about is
whether the call *evicts the session* -- and that lives in `resolve`, one layer below the mock. So
`PasskeyDeregistrationIntegrationTest` autowires the real controller against the Postgres container,
mints a session through `SessionService.create`, deregisters, and asserts `resolve` comes back empty
**while the account is still ACTIVE**. That last clause is the one that makes the assertion mean what
it says: ACTIVE passes `mayHoldSession` and the session was minted seconds earlier, so revocation is
the only remaining reason `resolve` can reject it. Same reasoning as S-15 (#224), which also shipped
a mock test and a DB test rather than one of each kind.

**The third mutation is the one a mock could never have caught**, and it is the reason the bystander
test exists at the endpoint level even though `UserSessionRepositoryIntegrationTest` already pins the
same SQL: the endpoint is where an admin's single deregistration would turn into a site-wide logout,
and that is where the regression should be read.

### Scope held

`/api/auth/webauthn/register/**` was not touched. It is still gated at `hasRole("USER")` with no
re-auth, which is step 4 of the item's exploit path -- but with the sessions revoked there is no
surviving session to register from, so the HIGH is closed on its own terms. The hardening remains a
separate, older question and still needs the password-login break-glass path traced before anyone
specifies it.

### S-27, riding along

`ShareLinkService.ownerAccountIsActive`'s docblock said a link "serves exactly while its owner's
account does" -- a biconditional #257 falsified by creating an ACTIVE account that cannot sign in
(last passkey deregistered, no password hash). Narrowed to what the code tests: the owner's account
*status* permits a session. The state it used to mis-describe is now named in the docblock rather
than left for the next reader to rediscover.
## MR 19 #21 outcome (2026-08-31) -- the N+1, and the reordering that would have ridden along

Shipped as [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266). One private method
in `CollectionService`, two tests, two updated tests. Suite 1,461 -> 1,463 (measured after
[#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) merged and this branch was
rebased onto it; against the pre-#265 baseline it was 1,455 -> 1,457).

**The item's fix shape needed no adjustment** -- the second consecutive one after MR 19 #14, which
broke the streak the full-board review's case rested on. Partition `orphanEntities` by type, call
`batchConvertImageEntitiesToModels` and `batchConvertGifEntitiesToModels`, re-merge into the
repository's ordering. Six queries where the default page of 50 cost up to 150.

### The re-merge warning was the load-bearing half of the guardrail

The obvious implementation is `imageModels` then `gifModels` concatenated, and it is wrong.
`findOrphanContentByLocationName` orders by `sort_date DESC` **across both kinds** -- the SQL
`COALESCE(ci.capture_date, cg.capture_date)` exists to do exactly that -- so concatenating groups all
images ahead of all gifs and silently reorders every mixed page.

**What makes it dangerous is that it passes the natural test.** A test asserting "both batch
converters were called and the per-entity one was not" is green under the concatenating version; it
is testing the N+1 fix, not the ordering. So the ordering needs its own test with a mixed page whose
SQL order interleaves the kinds -- gif, image, gif -- and `containsExactly` on the ids. Under the
concatenating mutation that one test reddens and nothing else does, which is the whole point of
having written it.

Implementation is a `Map<Long, ContentModel>` keyed by id, filled from both batches, then read back
in `orphanEntities` order. Ordering is preserved by the read, not by anything the batches promise.

### Mutation evidence

| Mutation | Killed by |
|---|---|
| concatenate the two batches instead of re-merging | `getLocationPage_mixedOrphans_keepTheRepositoryOrdering:987` **only** |
| drop the gif batch entirely | that test plus `getLocationPage_orphans_useTheBatchConverters:950` |

Sources restored with `touch`, per working rule 15.

### The partition is total, and that is a coupling worth naming

`findOrphanContentByLocationName` pins `content_type IN ('IMAGE', 'GIF')`, so images and gifs cover
the result set and no fallback branch is needed. That also makes the old code's
`.filter(Objects::nonNull)` dead -- only COLLECTION yields null from
`convertRegularContentEntityToModel`, and COLLECTION cannot appear. The method's docblock states the
dependency rather than leaving it implicit: widening that SQL means adding a branch here, or the new
type vanishes from the page.

### The item as filed, moved here from the tracker 2026-08-31

Kept because it is the diagnosis, and the diagnosis is the part a future reader needs. Filed by the
third run's cross-repo pair scan -- the one item that scan said was worth acting on.

`CollectionService.java:255-261` (as of #258) replaced a batch conversion with a per-entity one:

```java
List<ContentModel> images =
    orphanEntities.stream()
        .map(contentModelConverter::convertRegularContentEntityToModel)
        .filter(Objects::nonNull)
        .toList();
```

The old call was `batchConvertImageEntitiesToModels(orphanImageEntities)`, which batch-loads tags,
people and locations in three queries total. `convertRegularContentEntityToModel` routes an IMAGE to
`convertImageToModel`, which runs `tagRepository.findContentTags`,
`personRepository.findContentPeople` and `locationRepository.findLocationsByContentIds` per entity.
The first two are guarded by `entity.getTags()`/`getPeople()` being non-empty, **and that guard never
holds here** -- the entities come from `ContentRepository.findAllByIds`, which hydrates through JDBC
row mappers and never populates those sets. The location call is unguarded. Default `imageSize` is 50
(`CollectionControllerProd:133`), so the endpoint went from 3 queries to up to 150 per request.

Per FE-1 in the cross-repo section, the frontend discards the entire `images` array, so every one of
those queries was spent on data no client reads. Same shape as MR 19 #16, which went 201 queries to 1.

### Scope

The BE-2 decision was not resolved on the way past, as the item said it should not be. The frontend
still discards the entire `images` array (FE-1), so every one of those queries was spent on data no
client reads -- but the fix is correct whichever way BE-2 goes, so it did not wait.
## MR 25 FileEntry outcome (2026-08-31) -- the counts held, and an untested premise turned up

Shipped as [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267). The 3-arg
`DiskUploadRequest.FileEntry` constructor is gone and its 13 call sites now pass the canonical six
arguments. Suite 1,463 -> 1,465 (both new tests are the wire pin below,
not the refactor; measured after [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265)
and [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266) merged and this branch was
rebased onto both).

### Every number reproduced, and this time that is evidence

Working rule 23 says a sweep reporting zero corrections likelier asked the wrong question than found
the board accurate. This one reports zero corrections and is trustworthy anyway, for one reason:
**the method was written down and re-run**, not recalled. The paren-balanced scanner the third run
specified was rebuilt from that description, saved as `arity2.py`, run, and deleted.

| Claim | Re-derived |
|---|---|
| 13 three-arg sites | 13 -- `ImageUploadPipelineServiceTest` 10, `AdminControllerTest` 3 |
| 15 canonical six-arg sites | 15 -- same two files, 13 and 2 |
| 28 raw across all arities | 28 |
| zero `src/main` constructions at any arity | zero; the only `src/main` mention is a parameter type at `ImageUploadPipelineService:578` |

The refs were re-derived **after** MR 19 #21 was written but before it merged. That is safe here and
the reason is checkable rather than assumed: `git diff --name-only` on that branch lists
`CollectionService`, `CollectionServiceTest` and the two review docs, and none of the four files
holding `FileEntry` is among them.

### The item's one soft spot was a premise nothing tested

"Zero `src/main` construction at any arity -- the type only arrives via Jackson, which binds the
canonical 6-arg constructor for records, so this is test-only with no API-contract effect." The
premise is true. **But no test in the suite deserialized a `FileEntry` at all** -- `grep jpegPath`
outside constructor calls returns nothing -- so the whole safety argument for the delete rested on
knowing how Jackson treats records, with nothing to catch it if that were wrong or if someone later
added a `@JsonCreator`.

`DiskUploadRequestWireTest` now pins it from both directions: a pre-ingest body carrying only
`jpegPath`/`rawPath`/`people` binds with the three newer components null, and a full ingest body
populates all six. It passes on both sides of the delete, which is correct -- it is a pin on the
invariant that makes the delete safe, not a guard on the delete. This is working rule 33's "pair
every derivation with one literal pin", applied to a wire contract rather than a predicate.

### Scope

`DownloadResolution.extension` was not bundled, as the item's guardrail said. Three of the four
main-dead members remain, and `extension` is still the one to split off: 13 edits across 5 files, 2
of them in `src/main`, and 4 of its 6 accessor assertions are the only coverage of the collection-ZIP
original-to-web format fallback.

## S-22 outcome (2026-08-31) — shipped as a list, not the predicate the item specified

Shipped as [#247](https://github.com/themancalledzac/edens.zac.backend/pull/247). Both `RoleRepository` SQL sites now bind one `ROLE_MEMBERSHIP_STATUSES`
list; the admitted set is unchanged (everything except PERSON).

**The item's prescribed fix was not what shipped, and the correction generalizes.** It said "route
both SQL sites through a named `mayHoldRoleMembership` predicate". That predicate was written, and
removed on user review with the objection recorded verbatim: *"why would we need a boolean for
this? the user has a status, we shouldn't need this... this seems pointless and likely adding bloat
for no reason"*, plus a second objection to the name -- *"it's saying this user 'MAY' hold the role?
wtf, why MIGHT they? they do or they don't."*

Both were right. `SessionService.mayHoldSession` earns its shape because **four Java call sites** ask
it about one user during a request. This rule has **zero** -- it is only ever asked in SQL, over the
whole set -- so the boolean was a zero-caller wrapper around one `!=`. This became **working rule
40**.

**Mutation evidence:** narrowing the filter to `== UserStatus.ACTIVE` gives 1 failure and 2 errors
in `RoleRepositoryIntegrationTest` (25 cases). Run both before and after the predicate was removed.

**The narrowing report the item asked for**, verified against call sites rather than reasoned:
narrowing to ACTIVE would (1) break staged onboarding, since `createUser` creates accounts INVITED
and both `addMember` call sites pass a path variable straight through; (2) turn `UserMergeService`'s
merge into data loss, because `repointMemberships` moves rows only where the guard passes and then
unconditionally deletes what is left on the source; (3) revoke nothing, since nothing re-runs
`addMember` on a status change. Two costs, no benefit.

## S-23 outcome (2026-08-31) — the boot check, and the premise finally booted

Shipped as [#248](https://github.com/themancalledzac/edens.zac.backend/pull/248). `ProdActuatorExposureGuard` refuses a prod boot whose resolved
`management.endpoints.web.exposure.include` is anything but `health`. Reads
`WebEndpointProperties` rather than the raw property text, because that object is what actuator
consults -- so the check cannot drift from the exposure it guards. Prod-only, matching
`ProdSecretGuard`.

**The item's premise was reasoned from deps+config because the 2026-08-29 review could not boot the
app. It held**: the guard's ten tests all boot a real context through a component scan of
`Application`'s package.

**The exclude list was left exactly as it is** -- no names added, per the item's own fix shape. It
is now redundant rather than load-bearing; deleting it is still a separate call and still open as a
disposition nobody has made.

**Mutation evidence:** dropping `@PostConstruct` takes the class from 10/10 to **5 failures**,
exactly the refusal cases.

## S-16 outcome (2026-08-31) — one gate, and the item undercounted its own scope

Shipped as [#253](https://github.com/themancalledzac/edens.zac.backend/pull/253). `ShareLinkService.resolveByRawToken` now drops a link whose owner fails
`SessionService.mayHoldSession`. Suspend, not revoke: no row is deleted, so re-enabling restores
the same URL for everyone holding it. The test asserts the surviving row count, which is what makes
it a test of suspend rather than of revoke.

**Shipped smaller than written, deliberately.** The item specified a `users.status` predicate on
share resolve **and** on the scope query's join. Only the first was built. `resolveByRawToken` is
the only path from token to link -- callers are `FlybySessionFilter` and `ShareControllerProd` --
and the flyby cookie carries the **raw token**, not a shareId, so every request re-resolves and
re-reads status. Nothing reaches `findScopeCollectionIds` or `isCollectionInScope` without a
shareId resolve produced. This applies the user's own S-14 answer (one gate, as simple as possible)
consistently.

**The item undercounted its own scope, which is the stronger argument for the chokepoint.** It named
the scope query but not `ShareLinkRepository.isCollectionInScope`, the per-collection EXISTS check
on the authorization path, which would have needed the same join under the two-gate reading. Three
sites to keep in step, or one door.

**No new predicate**, per working rule 40 -- it reuses `mayHoldSession`, already pinned by
`mayHoldSessionAdmitsActiveAndNothingElse`, so a link serves exactly while its owner can sign in.

**Mutation evidence:** replacing the predicate with a vacuously-true one gives **4 failures of 16**.
Two earlier attempts at this mutation died on checkstyle before any test ran -- see working rule 41.

## S-14 and S-24 outcomes (2026-08-31) — answered, closed, no code beyond two docblocks

Both closed by [#250](https://github.com/themancalledzac/edens.zac.backend/pull/250).

**S-14 -- no second gate.** The user's answer, verbatim: *"any 'ADMIN' specific api request should
go through the SAME 'admin' gate. this should be as SIMPLE AS POSSIBLE. we don't want multiple
gates, but we want all 'admin' level endpoints to use the same gate."* That rejects the item's
proposed ownership/grant test, so the item closes as accepted behavior.

**What the answer does not settle, recorded rather than papered over.** `addCollection` is **not**
an admin endpoint -- it is on `UserShareControllerProd` at `/api/read/user/share`, reached by any
authenticated user for their own scope, and the admin sentinel inside `canView` is what makes it
answer yes for everything. The one-gate principle applies cleanly to `/api/admin/**`; it does not
decide what a user endpoint should do when an admin calls it. **If that is wanted, it is a routing
change (move it, or gate it), not the ownership test this item proposed, and it needs its own
item.** Forcing the answer into "allow, documented" would have recorded a decision the user did not
make.

**S-24 -- accept as admin-trusted.** No limiter. The answer's whole content was "document that", so
the documentation shipped with the answer rather than becoming a follow-up nobody files: docblocks
on `CollectionAdminController.updateGalleryAccess` and
`AdminUserController.sendInviteEmailAfterCommit`, each naming the `hasRole("ADMIN")` boundary and
the condition that reopens it (the path becoming reachable below admin).

## Bug #21 outcome (2026-08-31) — the sentinel, and a test that could not have failed

Shipped as [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249). Both dimension defaults in `ImageProcessingService.applyMetadataToEntity`
are now `null` instead of `0`. Backend sentinel only; the frontend fallbacks were left alone.

**The item's premise correction held and saved the wrong fix from being proposed again** -- the
header read already exists, and the defect is that it fails soft three ways.

**A trap worth more than the fix.** The first version of the regression test passed against **either**
sentinel. `imageMetadataExtractor` is a mock in `ImageProcessingServiceTest`, and a bare mock
returns `null` for an `Integer` on its own, so the captured entity had a null width no matter what
default the production code passed. The stub has to be made to return the default it is handed --
`thenAnswer(inv -> inv.getArgument(1))` -- before the test can fail. This is working rule 15's shape
hiding inside a mock's default return, and it would have shipped as coverage that was not there.
**Mutation evidence:** restoring the `0` default fails it with `expected: <null> but was: <0>`.

## Tracker bodies as they stood at the move

### Bug #21 (tracker body)

- [x] **Bug #21 (low) -- when the dimension fallback fails it writes `0`, and `0` is the one value
  the frontend cannot tell apart from "broken".** **IN FLIGHT 2026-08-30: [#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)** --
  both defaults are now `null`. Backend sentinel only; the frontend fallbacks are untouched. One
  thing found while writing the test and worth keeping: the extractor is a mock in
  `ImageProcessingServiceTest`, and a bare mock returns `null` for an `Integer` on its own, so the
  obvious version of the test passes against either sentinel and cannot fail. The stub has to
  return the default the production code passes it. *(Filed 2026-08-30 from the frontend board's C9.
  C9 asked a product question -- should a dimensionless cover fall back to a text-only header? --
  and the user's answer reframed it: "we should NEVER HAVE AN IMAGE WITHOUT height/width... we
  should NEVER be caught in this situation." That is a statement about this repo, so the item
  belongs here.)*
  **Premise correction made while filing, and it cuts the severity from medium to low.** The obvious
  reading -- "EXIF has no dimensions, so `0` gets written" -- is WRONG, and anyone re-deriving this
  item will reach for it. `ImageMetadataExtractor:97-100` and `:119-122` already check for the
  missing keys and call `ensureDimensions`/`ensureDimensionsFromPath`, which read width and height
  straight off the image header via `putDimensionsFromHeader:399-420`. **The "just read the real
  dimensions" fix already exists.** Do not propose it again.
  **What is actually wrong: that fallback fails soft, three ways, and each one lands on `0`.**
  `putDimensionsFromHeader` returns without setting the keys when `createImageInputStream` yields
  null (`:402-404`) or when no `ImageReader` handles the format (`:406-408`), and `ensureDimensions`
  swallows an `IOException` (`:369-371`). Each logs a warning and continues. Then
  `ImageProcessingService.applyMetadataToEntity:465-468` writes
  `parseIntegerOrDefault(metadata.get("imageWidth"), 0)` -- **default `0`, not null**. The realistic
  trigger is the no-reader branch: a format Java ImageIO has no reader for, which for a photography
  archive means RAW or HEIC without a plugin. Note the inconsistency in the same method -- `iso` on
  the very next line defaults to `null`; only the dimensions default to `0`.
  **Why `0` is worse than `null`.** Both frontend consumers handle the two differently and both get
  it wrong. `contentLayout.ts:650` guards `if (!coverBlock.imageWidth || !coverBlock.imageHeight)
  return null` -- `0` is falsy in JS, so a `0 x 0` cover renders **no collection header at all**.
  The sibling path at `parallaxCard.ts:135-136` falls back to a 1000px square via
  `raw.imageWidth ?? SQUARE_FALLBACK_SIDE` -- but `??` catches only null/undefined, so `0` passes
  straight through the fallback built for exactly this case. One sentinel, two consumers, two
  different wrong answers.
  **Fix shape:** default to `null` rather than `0` at `:465-468`, so the frontend's existing
  null handling works and `parallaxCard`'s `??` fallback fires as designed. Optionally make the
  soft-fail loud -- a warning nobody reads is how a `0 x 0` row reaches production unnoticed.
  The column already permits null (`ContentImageEntity:48` is a boxed `Integer`;
  `test-base-schema.sql:83` is `image_width INTEGER` with no `NOT NULL`), so no migration is needed
  for the null default. **Frontend C9 is parked pending this** -- it should not add a fallback for a
  value this repo should stop producing. **COLD.**

- [x] **Config rot C-1 (2026-08-30) -- CLOSED same session by
  [#245](https://github.com/themancalledzac/edens.zac.backend/pull/245).** #243 deleted
  `app.admin.enforce-authz` but only its assignment lines. Four sites went on describing it:
  `application.properties` (3 lines plus an empty banner with two adjacent `#---#` separators),
  `application-dev.properties` (2 lines), `docker-compose.yml` (a comment and a dead
  `ADMIN_ENFORCE_AUTHZ` env), and `.env.example` (3 lines plus the key). All false as of the same
  commit that made them false, and `.env.example` actively invited setting the toggle to `false`
  for a login-free admin surface, which now does nothing silently.
  **The lesson, and it generalises past this item: when deleting a config key, grep the
  surrounding prose, not the key.** Grepping the key after deleting it finds nothing and reads as
  a clean sweep; the prose survives precisely because it contains no assignment. It surfaced only
  from an unrelated angle -- diffing `application.properties` against the stale
  `fix/s18-actuator-exclude` branch, where the orphans appeared as unchanged context around a
  single `+` line. The four Java docblocks naming the toggle are kept deliberately: they say it
  *was removed and when*, which is true.

### S-14 (tracker body)

- [x] **S-14 (MEDIUM, agent trace). S-6 turned `addCollection` from a read decision into a durable
  third-party grant.** **ANSWERED 2026-08-30 -- no second gate. Closed as accepted behavior, with
  one thing it does not settle recorded below.** The user's answer, verbatim: *"any 'ADMIN'
  specific api request should go through the SAME 'admin' gate. this should be as SIMPLE AS
  POSSIBLE. we don't want multiple gates, but we want all 'admin' level endpoints to use the same
  gate."*

  **What that settles.** The default-safe option this item proposed -- an ownership/grant test
  distinct from `canView`, so the admin sentinel cannot durably grant a third party access -- is
  **rejected**. It is a second gate on top of the one that already answers for admins, which is
  exactly the shape the answer refuses. Do not open an MR adding one.

  **What it does not settle, and someone should notice this rather than re-deriving it.**
  `addCollection` is **not** an admin endpoint. It lives on `UserShareControllerProd`, mapped at
  `/api/read/user/share`, and is reached by any authenticated user for their own share scope; the
  admin sentinel in `canView` is what makes it answer yes for everything. So the one-gate principle
  applies cleanly to `/api/admin/**` and does not by itself decide what a *user* endpoint should do
  when an admin calls it. The behavior is accepted as it stands. If the intent is that this
  endpoint be admin-gated like the rest, that is a routing decision (move it, or gate it), not the
  ownership test this item proposed -- file it as its own item.

  Original finding follows.
 `UserShareControllerProd.addCollection` gates on `canView`, then writes a
  `share_link_collection` row -- which is the authorization set for an unauthenticated bearer-token
  holder. Before S-6, an admin holding no role grant got 403 there. The ADMIN sentinel now makes the
  gate always say yes, so one PUT can put any collection on the site, including another client's
  password-protected gallery, behind a URL that can be forwarded to anyone. **#207's reasoning ("an
  admin can already view everything") is correct for the read gates and does not transfer to a gate
  that grants access to someone else.** This is the first item to argue a previous fix was too
  broad rather than too narrow.

### S-16 (tracker body)

- [x] **S-16 (MEDIUM, agent trace). The revoke-on-status sweep covers sessions and invites and misses
  share links.** **IN FLIGHT 2026-08-31: [#253](https://github.com/themancalledzac/edens.zac.backend/pull/253).** Shipped as **one gate, not two.** The
  item specified a `users.status` predicate on share resolve *and* on the scope query's join; the
  scope-query half was not built, deliberately. `resolveByRawToken` is the only way a token becomes
  a link -- its two callers are `FlybySessionFilter:65` and `ShareControllerProd:59` -- and the
  flyby cookie carries the raw token, so every request comes back through it. Nothing reaches
  `findScopeCollectionIds` or `isCollectionInScope` without a shareId that resolve produced, so a
  status join there would be a second gate enforcing what the first already did. **That is the
  user's S-14 answer applied consistently** (one gate, as simple as possible), and it is why this
  item shipped smaller than written. **A third site the item did not name:**
  `ShareLinkRepository.isCollectionInScope` is the per-collection EXISTS check and would have needed
  the same join under the two-gate reading -- so the item undercounted its own scope, which is
  further reason the chokepoint was the right place. No new predicate was written: the check reuses
  `SessionService.mayHoldSession`, the rule the session path already enforces, so a link serves
  exactly while its owner's account can sign in. **UNBLOCKED 2026-08-30: the answer is SUSPEND, not
  revoke.** Disabling an account
  stops its share links from resolving; re-enabling restores them. Revoking is destructive and not
  undone by re-enabling, so it is refused. **Fix shape, now decided rather than open:** a
  `users.status` predicate on the share-resolve path (`ShareLinkService.resolveByRawToken`) and on
  the scope query's `share_link` -> `collection_people` join, so both read owner status. No rows are
  deleted on a status change. Note for whoever takes this: the status test wants a named predicate
  shared by both sites rather than two literals -- that is the drift S-20 closed for sessions and
  S-22 for role membership, and this would be the third site to hand-roll it. **This item is now
  actionable work, not a product call.**

  Original finding follows.
 `ShareLinkService.resolveByRawToken` reads no owner status, and the scope query
  joins `share_link` to `collection_people` with no `users.status` predicate. Disable a user for
  cause: S-1 refuses their login, S-8 kills their sessions, S-9 kills their invites, and their share
  link keeps serving every collection they are tagged in to anyone holding the URL. #213 sharpens
  this by making that link durable and re-readable rather than a one-shot value.

### S-22 (tracker body)

- [x] **S-22 (LOW, verified 2026-08-29). `RoleRepository`'s status guards are SQL denylists that
  fail open for a future `UserStatus`.** S-20's outcome set the convention: never compare a
  `UserStatus` to a literal outside the two named predicates (`mayHoldSession`, `mayAcceptInvite`).
  `RoleRepository.addMember` (`RoleRepository.java:135`, `status <> 'PERSON'`) and
  `repointMemberships` (`:522`, same test) break it -- and they are **denylists** where the two
  predicates are allowlists, so a fifth `UserStatus` is admitted to role membership by
  construction: the exact drift S-20 closed for sessions, one file over. Unpinned today:
  `RoleRepositoryIntegrationTest.addMemberRejectsTagOnlyPersonRow` (`:53`) tests only
  PERSON-rejected/ACTIVE-admitted, with no `UserStatus.values()` enum pin like
  `SessionServiceIntegrationTest.mayHoldSessionAdmitsActiveAndNothingElse`. **Not
  live-exploitable**: a DISABLED account in a role cannot authenticate at either chokepoint, so
  `canView`'s status-blind join is unreachable by that user -- the dormancy reasoning under
  "Verified sound" still holds, and this item does not tighten the membership rule, it pins it.
  Fix: add the enum pin (rule-33 shape) and route both SQL sites through a named
  `mayHoldRoleMembership` predicate so the rule keeps one definition (rule 14). **COLD.**
  **IN FLIGHT 2026-08-30: [#247](https://github.com/themancalledzac/edens.zac.backend/pull/247).** Shipped as one `ROLE_MEMBERSHIP_STATUSES` list bound by
  both SQL sites, **not** as a named boolean predicate. The predicate was written first and removed
  on review: `mayHoldSession` earns its shape from four Java call sites that ask it about one user
  per request, and this rule has none -- it is only ever asked in SQL, over the whole set, so the
  boolean was a zero-caller wrapper around one `!=`. The list is the single definition. The item's
  wording ("route both SQL sites through a named `mayHoldRoleMembership` predicate") should be read
  as naming the outcome, not the mechanism. The narrowing report the item did not ask for but the
  fix needs is in the PR: narrowing to ACTIVE breaks staged onboarding, turns a merge into a
  non-ACTIVE target into data loss, and revokes nothing.

### S-23 (tracker body)

- [x] **S-23 (LOW, filed 2026-08-29). The rule-34 follow-up, now actually filed: a boot check on
  the *resolved* actuator include.** Rule 34 recorded this follow-up as "filed not built" and it
  existed nowhere as a row until now. The gap it closes: `/actuator/metrics` (and `info`) meet
  S-18's own criterion under an injected `include=*` -- metrics dumps process/JVM/HTTP state and is
  enabled by default in Boot 3.x -- yet neither is in the shipped exclude
  (`application.properties:65`, twelve names -- **re-derived 2026-08-30**, was `:71`; #238 removed the
  six-line prose block above it, so `include` is now 64 and `exclude` 65. Stable under #245, which
  edits the same file lower down.) nor in `MUST_BE_EXCLUDED`, and **both S-18 tests are
  structurally blind to the omission**, because both derive from the same hand enumeration that
  omitted it (working rule 33 one level up). Reachable only under the injected-wildcard accident
  (rule 34), and in prod only to an internal-secret bearer -- `InternalSecretFilter` allows just
  the three health URIs otherwise. Fix shape: a `ProdSecretGuard`-shaped boot check asserting the
  resolved `management.endpoints.web.exposure.include` is `health` -- it closes metrics, info and
  every future Boot endpoint at once without hand-enumerating names, and it is the only thing that
  would make the exclude list and `MUST_BE_EXCLUDED` deletable. *(Premise reasoned from
  deps+config; the 2026-08-29 review could not boot the app.)* **COLD.**
  **IN FLIGHT 2026-08-30: [#248](https://github.com/themancalledzac/edens.zac.backend/pull/248).** `ProdActuatorExposureGuard`, prod-profile-gated like
  `ProdSecretGuard`, reading `WebEndpointProperties` rather than the raw property text so the check
  cannot drift from what actuator actually exposes. The premise held when the app was finally
  booted: the guard's own tests boot a real context. **The exclude list was left exactly as it is**
  -- no names added, and deleting it is still a separate call.

### S-24 (tracker body)

- [x] **S-24 (LOW, quick user call, filed 2026-08-29). Two admin mail-send paths are covered by
  neither limiter, and the gallery one amplifies.** **ANSWERED and CLOSED 2026-08-30: accept as
  admin-trusted, documented.** No limiter is added. The answer's whole content was "document that",
  so the documentation shipped with the answer rather than being left as a follow-up: a docblock on
  `CollectionAdminController.updateGalleryAccess` recording that the N-address send loop is
  deliberately uncapped, and one on `AdminUserController.sendInviteEmailAfterCommit` recording the
  same for its three callers. Both say the trust boundary out loud -- `hasRole("ADMIN")` -- and both
  name the condition that would reopen this: the path becoming reachable below admin.

  Original finding follows.
 `POST /api/admin/collections/{id}/gallery-access`
  (`CollectionAdminController.java:56`) loops `sendGalleryPasswordEmail` over a caller-supplied
  `request.emails()` list with no cap (`CollectionService.java:1675-1683` -- N SES sends per
  request), and the three admin invite endpoints (`AdminUserController.createUser` /
  `regenerateInvite` / `upgradeUser`, via `sendInviteEmailAfterCommit`) have no limiter at all.
  All are behind `hasRole("ADMIN")` -- highest trust, hence LOW -- but each is an authenticated SES
  send covered by neither `RateLimitFilter` (which covers `/api/public/` only) nor
  `ShareEmailLimiter` (keyed to the one share endpoint). The call: either a global daily cap on
  gallery-password sends (`ContactMessageLimiter` shape), or accept as admin-trusted and document
  that. One sentence from the user settles it.

# Full-board review — run 2026-09-01 (tenth run)

Eight read-only agents, one apply agent, one docs MR, zero code changes. The tracker carries the
summary, the filed items and the corrected rows; this is the agent-by-agent detail and the
"do not redo" list (working rule 11).

## Shape

Eight slices, one agent each, every one read-only and every one reporting to a file: **A** every
recorded number on the board re-measured; **B** every code reference in every open item re-derived;
**C** the near-term premises (MR 15-19, items #22-#29, MR 25); **D** the far set -- MR 21-24, Waves 6
and 7, Appendices B, C and D, branches and worktrees; **E** the merged security set attacked plus the
U- chain; **F** board self-consistency; **G** the backend/frontend pair; **H** re-priced estimates and
COLD/BLOCKED classification. The apply agent worked from the eight files rather than from a
retelling, which is what the second close-out's guardrail asked for and it mattered again: three
reports corrected a premise a paraphrase would have flattened.

**Zero code changes is the result to record.** Slices B, C, D, E and H each proposed at least one
code fix. All became board rows with their evidence; none was implemented.

## A — the numbers

**38 wrong or stale, 63 correct, 11 with no gate command at all.** The three worst:

1. **Lines 37, 49 and 50 record a gate that cannot match.** Detail in the tracker's review section.
   The proof is one line: the same file, same content, escaped form reads 5 and unescaped reads 0.
   `git log -S` pins the introduction to `5528660a`, the sixth close-out.
2. **The suite total.** 1,505 tests, 0 failures, 0 errors, 0 skipped on `main` at `43c6f2c6`,
   measured by running `mvn test` and summing the surefire reports, not by counting annotations. The
   board carried 1,502 and nothing had re-run it after #297. Rule 41 checked: all 262 report files
   were timestamped by that run. The mechanical count is 1,433 `@Test` plus 19 `@ParameterizedTest`;
   the gap to 1,505 is parameterized expansion.
3. **`RoleRepository` recorded at 10 inline comments sixteen lines below the cell recording it at 0.**
   #285 took it to 0 on 2026-08-31 and the Inline-comments row has said so since; the rule-37
   re-scoped row said 10 through three close-outs, inside a Progress cell asserting those three
   counts "are re-run every close-out".

Other corrections of substance: the four service-decomposition files (5,036, not 5,065 -- two of four
components moved); `@Value` field injection 9 -> 6; fully-qualified names 14 -> 11; `Optional.get()`
58 -> 59 raw and 47 -> 48 Optional; `uploadToS3` 6 callers -> 7; try-catch controller sites 2 -> 3
lines of `try {` though still 2 catching sites; `*Prod` test classes 10 -> 9; `MetadataValidator`'s
identical guards 6 -> 5 plus one numeric; `JobStatus` test references ~45 -> 39 across three files;
verb-route test references 61 -> 62; `CollectionServiceTest` 2,893 -> 2,850 in Appendix C while the
MR 25 row 420 lines away already said 2,850; and the tree-size baseline 32.6k/27.2k -> 35,697/28,071.

**The eleven figures with no command.** Two were given one, three were deleted or re-derived with a
stated method, and the rest are flagged in place. The three that mattered, because they sit in open
items and would be quoted by whoever schedules the work: MR 25's "745 lines of positional
construction" (re-measured with a stated method as **767**, and the replacement re-priced from ~120 to
~195 plus a ~200-line fixtures class, so the net is ~-370 rather than ~-600); MR 22's "14 sites" for
fully-qualified names (re-derived mechanically as 11, with the method stated); and MR 21's "59
Map-shaped lines in 17 test files", which no command on this board reproduces and which is now flagged
as unreproducible rather than quoted. MR 23's "23 files touched" was traced to a real command
(`grep -rl ControllerProd src/main src/test`) that counts a different unit than the row claims.

## B — the references

**113 checked, 74 exact (65%), 39 drifted or dead (35%).** That is a large improvement on the
38-of-130 the 2026-08-24 pass recorded, and the reason is visible in the data.

**Every item whose last re-derivation was done by name came back clean. Every item that wrote fresh
line numbers came back drifted.** `DownloadResolution.extension` 13 of 13, the four typeless migration
integration tests plus V54 6 of 6, MR 21's whole Map inventory 24 of 24, MR 16 #3 5 of 5 -- all
re-derived by name, none moved. MR 25's `CollectionRequests.Update` recorded nine per-site line
numbers at the eighth close-out and all nine moved within one run, because #296 cut 46 lines out of
`CollectionServiceTest` after they were written. **That row now carries a `git grep -n` command rather
than a list.**

Breakdown of the 39: 9 `CollectionServiceTest` `Update` sites; 4 in MR 18 #13; 4 in MR 18 #10; 4 in
MR 19 #17; 2 magic-2500; 2 `ImageUploadPipelineService` Atomic groups; 2 Appendix C S3-delete pairs;
3 counts that grew or shrank; 3 counts that are now wrong; 2 `*Prod` figures; 4 MR 24 sizes.

**One ref has now been corrected seven times**: `CollectionService.isGalleryAccessAuthorized`, written
as 542, 533, 534, 541, 539, 582 and now 575. The item that records it says "stop writing the number"
and then writes it. The number is deleted for good.

## C and D — the premises

**Nothing in the near-term set is dead, but one guardrail is, one premise's reasoning is wrong, and
one item's central finding was misdescribed.**

- **MR 18 #13's sort half is a category error.** `MetadataService.getAllTags` and `getAllLocations`
  order in SQL; `SyntheticCollectionResolver.toTagRecords` takes its list from a query ending
  `ORDER BY t.tag_name ASC`; and `toTagModel` / `toLocationModel`, the two methods the item named as
  unsorted producers, are single-entity mappers that map one row and cannot sort. **No endpoint
  returns an unordered tag or location list.** The one genuinely unordered site is
  `ContentService.buildUpdateResponse`, a `HashSet` in a mutation response. What is left is SQL
  `ORDER BY <name> ASC` versus Java `compareToIgnoreCase`, which agree under a locale collation and
  disagree under `C`. Nothing in the repo pins the collation.
- **`DownloadResolution.extension`'s guardrail is dead.** Each of the four ZIP `.extension()`
  assertions sits beside a `.contentType()` assertion proving the same branch, and in `ContentService`
  the two fields are assigned on consecutive lines inside the same branch in both methods, with
  `filename` derived from `extension`. The fallback is covered twice over. Unparked.
- **Item #22's reasoning is false.** Both PUT routes already null-guard every field; sixteen
  null-guards were read and listed. What is missing is the verb and a way to clear a nullable field.
- **`Synthetic.blogsOnly`, `AdminHomeService`'s cache row and Appendix D each track nothing.** The
  first has a premise already flagged FALSE and a fix the false premise was the only argument for; the
  second prescribes no action, so it is a checkbox that can never be ticked; the third is a design doc
  at 0% with no stubs. All three removed.
- **Appendix C's `Collectors.toMap` lead cannot happen.** `WHERE id IN (:ids)` returns one row per
  distinct id, so the map never sees a duplicate key. **Slices B and D disagreed here** -- D confirmed
  the `Collectors.toMap` line exists and called the lead "still true, exact", B traced the `IN` clause
  and showed the throw is unreachable. B shows its reasoning and wins; the lead is re-scoped to
  "duplicate ids silently last-write-win", which is a different finding.
- **The branch section had been maintained as a fixed list rather than re-run.** Four worktrees
  created after 2026-08-24 never reached it, four of its eight branches have no `origin` ref so its
  own measuring command errors on them, and two worktrees the board describes as "holding no work"
  hold 2 and 4 uncommitted modified files on branches with zero unique commits.

## E — security

Three findings, detailed in the tracker's ledger. **The reason they exist is worth recording**: every
one of the twenty-seven closed S- findings lives in auth, session, role-membership, share or actuator
code, and `/api/read/content/**` had never been attacked as an authorization surface. Two of its
routes have no authorization at all.

**The U- chain holds exactly as recorded.** `ProdActuatorExposureGuard` is `@Component @Profile("prod")`
with a `@PostConstruct verify()` that throws unless the resolved include is exactly `{health}` after
trim, lowercase and empty-filter, and it reads the bound `WebEndpointProperties`, so an env-var
override is caught too. U-7's and U-8's answers are both correct under `prod` and both depend on U-1.
**One thing to add when U-1 is next asked:** the guard covers only the *web* exposure, so if the
answer is `default`, the twelve-name exclude list is the only thing left -- and a name list is exactly
what S-18 proved cannot keep up.

**Two things looked at and judged not findings, recorded so nobody re-files them.** `mfaSatisfied` is
written at session creation, read into the principal and surfaced in `/api/auth/me`, and **no
authorization decision anywhere reads it** -- a break-glass password login has the same privileges as
a passkey login, `/api/admin/**` included. Not a defect today because nothing in the code claims
otherwise; it is display state, and step-up for admin writes is a decision somebody could make.
`viewerMaySeeHidden` uses a bare `p.userId() == null` where `AuthPrincipal`'s own docblock asks call
sites to use `isRealUser`. Semantically identical. Style drift, not a hole.

## G — the cross-repo pair

**The frontend clone exists** at `~/Code/edens.zac`, `origin/main` at `f4e8e25`, and the board said it
did not in two places. It was read exclusively through `git show origin/main:<path>` and a
`git archive` extract, so nothing here depends on another session's dirty branch.

**BE-2's briefing, and why option 2 won.** Option 1 was a product regression wearing an optimization's
clothes. `LocationPageResponse.images` is orphans only -- `findOrphanContentByLocationName` appends
`ORPHAN_COLLECTION_EXCLUSION` for every listed collection -- while `searchImages({ locationId })`
returns every image at the location. Moving the grid onto the location endpoint would have removed
photos already visible in a collection card above it, degraded the cover image worst at the
best-curated locations (`page.tsx:85` picks the first rating >= 4 from the same array), changed what
`count={images.length}` means, and made FE-1 and FE-2 mandatory prerequisites rather than optional
cleanups.

**The measured cost of option 2's win, stated with its caveat.** Roughly 7 of ~15 SQL queries per
location page load exist only to build the array, rising to 11 of ~19 if any content there is a GIF;
serialization is 50 31-field records at 600-1200 bytes each, so 30-60 KB. **But
`getCollectionsByLocation` is ISR-cached (`collections.ts:158`), so this is paid per revalidation, not
per visitor.** The performance argument is a modest win. The stronger argument is contract hygiene: an
unread public field is one nobody can safely change, and this one produced #258, then FE-1, then a
premise correction, then BE-2, all from a field with zero readers.

**The reverse scan, run against a live clone in both directions for the first time.** All 110 backend
routes matched against every `.ts`/`.tsx` under `app/`, plus `proxy.ts` and `tests/`, with ambiguous
matches hand-checked. Nine more dead endpoints found, `UserRatingOverrideControllerProd`'s two among
them -- both of that controller's routes are dead, so the whole class and its service path are
unreachable from the UI, and it already has three other rows against it. **One correction in the other
direction: `GET /api/read/collections/{slug}/download` is not dead.** `downloads.ts:28` builds it as a
navigation URL rather than a `fetch`, which is why a path-literal grep missed it.

## H — estimates and classification

**The classification section covered about 25 of 69 open checkboxes.** Everything in MR 21-24, most of
MR 26, Waves 6 and 7, the unsettled questions, the stale-docblock pair, the branch worklist and both
appendices sat outside it. The tracker's version now covers the whole board.

Six re-classifications landed: MR 25's `CollectionRequests.Update` from COLD to BLOCKED (ordering);
C7 out of "Decisions needed" into "Parked by decision"; FE-1 from BLOCKED (other repo) to closed;
FE-2 through FE-5's stated reason from "waits on someone filing them" to "waits on the frontend
acting"; U-2 and U-3 given buckets they had never had; and MR 16 #3 ticked closed as decided.

**Three estimate corrections worth carrying.** MR 19 #17's guardrail ("pick the members that share a
file") is unactionable -- the five members live in five separate files, one each. Its member (c) is
the expensive one at 11 call-site edits across 4 files and the board prices it at nothing. And its
member (b) carries an unnamed trap: `CollectionService.DEFAULT_PAGE_SIZE` is `default_content_per_page`
= 30, while `PaginationUtil.normalizeCollectionPageable` uses `default_collection_per_page` = 10, so
reaching for the obviously-named helper silently drops the main collection read endpoint from 30 items
a page to 10.

## Where two slices disagreed, and how it was resolved

1. **Appendix C's `Collectors.toMap` lead.** D: "still true, exact." B: the throw is unreachable
   because `WHERE id IN (:ids)` dedupes. **B applied** -- it shows the query and the reasoning; D
   confirmed only that the `Collectors.toMap` line exists.
2. **MR 18 #13.** H: delete the row, it does not deserve a third listing. C: keep it, blocked on one
   host probe. **C applied, with H's recommendation recorded in the row.** Both reached the same
   technical conclusion; deleting loses the record of why the original finding was wrong, and the
   collation question is cheap and real. **If the collation comes back as a locale collation, close
   the row that day.**
3. **MR 16 #3.** C: leave it as-is, no text change. H: tick it closed as decided. **H applied.** The
   verdict has been "not worth doing" across three re-derivations with every number exact; carrying it
   as an open checkbox that will never be ticked is the failure the `AdminHomeService` cache row was
   demoted for.
4. **The worktree count.** A and D counted 10 worktrees; B wrote "eleven, not six" by including the
   main checkout. **Ten worktrees plus the main checkout**, which is the eleven rows `git worktree
   list` prints. Stated both ways on the board so the next reader cannot get it wrong.

## Checked and clean — do not redo

Assembled from all eight slices. Everything here was re-derived at `43c6f2c6` and needs no further
checking until the named file is edited.

**References, re-derived by name and 100% clean.** `DownloadResolution.extension` -- all 13:
accessors `ContentServiceDownloadTest` 88, 102, 201, 217, 237, 239; constructions `ContentService`
781, 835, `ContentDownloadAuthTest:94`, `ContentDownloadControllerProdTest` 71, 75,
`DownloadUrlServiceTest` 100, 101; plus `DownloadUrlService` 83, 105, 108 and zero `.extension()` in
`src/main`. The four typeless migration ITs plus V54 -- 188, 282, 72, 164 (total 706) and 181. MR 21's
whole inventory -- 12 + 4 `AdminController` lines, 4 elsewhere, 20 lines across 19 endpoints, and all
eight raw-`Map` service methods. MR 16 #3's numbers -- 106 / 82 / 81 / 59 source lines,
`RateLimitFilter:131`, four copies of the Caffeine+Bucket4j core. MR 19 #17's `UserInviteService`,
`CollectionProcessingUtil` and `EmailService` refs -- 158, 257, 211, 566, 586, 588, 924, 195, 246,
301. MR 24's `AdminUserController` pair -- 614 source, 1,510 test, 12 injected fields. The
`@Autowired`-on-constructor five and the `ReflectionTestUtils.setField` five. Wave 4's two docblock
targets at `ContentModelConverter:37`, `ContentMutationUtil:30`, `CollectionService:114`,
`UserPageAssembler:26`. Every U-1 / U-7 / U-8 config ref -- `.env.example:3`, `docker-compose.yml:21`,
`application.properties:66` and `:67` with its twelve names. `new ContentModels.Image(` -- 14 test
sites across 11 files plus 2 in `src/main`. `CollectionRequests.Update`'s arity counts -- 22 at arity
17, 3 at arity 22, 8 files, 1 main site at `CollaboratorRequests.java:43`. Item #29's two guardrail
claims. MR 26's seven coverage gaps -- no test file exists for any of them, and there is still no
`src/test/.../controller/user/` directory.

**Premises, verified and not to be re-derived.** MR 16 #3's verdict, now three runs running with every
number exact. MR 18 #10's `setTags`/`setPeople`/`setLocations`-on-the-subclass finding, confirmed
against the source rather than inherited. MR 18 #13's dedupe half, closed on both grounds --
`Records.java` imports only `JsonProperty`, `types.FilmFormat` and `LocalDate`, and no file under
`model/` imports from `entity/` at all. Item #29. MR 21's whole row. Wave 4's PARENT-shaped pair and
the 2026-08-25 note that `TagViewResolver` does not carry the phrase. MR 26's five original coverage
rows and `UserRatingOverrideControllerProd`'s missing controller test. Appendix B's C7 (no migration
indexes `is_blog` or `is_client`) and its `CollectionList.fromSibling` partial, whose two positional
constructions are still in `CollectionRepository` and `CollectionService` exactly as the scorecard
says -- the oldest live claim on the board and still accurate.

**Security, attacked rather than confirmed.** S-16's reachability claim -- nothing moved near it;
`AuthPrincipal.flyby` still has exactly one `src/main` caller, `resolveByRawToken` exactly two,
`ShareLinkService.findById` exactly one. **Do not spend a fifth pass on it.** S-1's `mayHoldSession` as
a set -- all five call sites route through the predicate and there are zero inline `!= ACTIVE`
comparisons in `src/main`. Every `users.status` write path -- three `updateStatus` callers, each doing
the right thing, and no fourth writer. S-2's `addMember` guard and its sibling paths, including a
search for a third PERSON-promotion path that does not exist. The `app.admin.enforce-authz` toggle --
fully removed, only four docblock mentions remain, and `EditAccessWebConfig` no longer keys on it.
`CollaboratorAccessInterceptor` against all four `/api/edit/**` routes. Cacheability against
principal-varying bodies -- `CacheControlInterceptor.PUBLIC_ROUTES` holds nine exact patterns, both
slug routes are correctly absent, and the one that looked most dangerous reads no principal at all.
`InternalSecretFilter`. And the anonymous-surface screening of the flyby principal: every consumer of
`canView`, `isClient`, `hasAtLeast` and `effectiveLevel` either screens with `isRealUser`, is
chain-gated `hasRole("USER")`, or requires COLLABORATOR, so the share principal cannot reach a gallery
password, a download, a HIDDEN collection or the edit surface.

**Board hygiene, checked clean.** All 103 internal anchor links across both files resolve. No checkbox
is invisible to the `^- \[ \] ` gate -- there are no indented, table-cell or `*`-bulleted variants.
The history file holds zero open checkboxes. All 52 working rules are defined, numbered 1-52 with no
gaps or duplicates, and every rule number cited anywhere in the tracker exists. The tombstones are in
place. Rule 9's protected file still exists. The comment-count commands reproduce their recorded
figures exactly (203 main / 1,169 test leading, 68 trailing). Session-log retention was honoured by
the ninth close-out and both archive halves are linked. Rule 38 holds across the last eleven
tracker-touching commits, with one single-file commit (`3c034c94`) that describes a reconciliation
rather than a close-out and does not carry the sentence rule 38 asks for.

## Two small things worth fixing when someone is next in the file

- **`arity2.py` is not in the repo.** Four places quote `python3 arity2.py '<regex>' src/test/java` as
  the command that produced their counts, and one says "save it as `arity2.py` at the repo root".
  `ls arity2.py` returns not found and it is not tracked, so every session that needs an arity count
  writes it again first. The method is described well enough that the numbers are reproducible, but
  the recorded command is not runnable as written -- rule 31's spirit. Commit the scanner or stop
  quoting it.
- **Rule-37 per-file sweep items have been filed in three different sections**, wherever the session
  that shipped them happened to be: `AdminUserControllerTest`'s 73 under "Bugs filed after the waves
  closed", `CollectionRepositoryTest`'s 21 under MR 18, and the `CollectionServiceTest` sweep (#289)
  with no row at all -- it exists only inside the Inline-comments cell's prose. All three are closed,
  so nothing is lost, but the next concentration filed will land in a fourth place. Rule 47 says file
  a left-behind concentration as its own item; it does not say where.

# Full-board review — run 2026-08-31 (third run)

Five read-only agents, one apply agent, one docs MR, zero code changes. The tracker carries the
summary and the filed items; this is the detail that does not belong in a tracker (working rule 11).

## Shape

Slices, one agent each: the two unchecked MR 25 arity counts; premise re-verification across
MR 16-19; the merged security set (#247, #248, #250, #253, #257) attacked as a set; the
frontend/backend pair; and board hygiene, counts and internal consistency. The apply agent worked
from the five written report files, not from a retelling — which is what the second close-out's
guardrail asked for, and it mattered: two of the five reports corrected a premise the parent session
would have paraphrased away.

**Zero code changes is the result to record.** Three reports recommended code fixes. All three were
filed as board items with their evidence and none was implemented. The single docs MR was the whole
output.

## The count audit — clean, and that is new

| What | Recorded | Actual |
|---|---|---|
| Open bugs (`grep -c '^- \[ \] \*\*Bug #'`) | 1 | 1 |
| Open security (`grep -c '^- \[ \] \*\*S-'`, before filing) | 0 | 0 |
| History open checkboxes | 0 | 0 |
| Rule-37 leading `//`, total | 1,644 | 1,644 |
| — main / test | 262 / 1,382 | 262 / 1,382 |
| Trailing inline comments | 74 | 74 |
| `RoleRepository` / `AdminBootstrap` / `CollectionControllerProd` | 10 / 6 / 9 | 10 / 6 / 9 |
| Worktrees | 6 | 6 |

Also checked and clean: **every PR the board cites is merged** — all 63 numbers from #159 to #258
return `MERGED`; and **every internal anchor link between the two files resolves** — both files
parsed, every heading slug extracted, every `](...#anchor)` matched, zero broken.

Two of these numbers were corrected in the previous close-out and both held, which is the first time
a correction has survived a full audit.

## Security slice — checked and clean, do not redo

Filed: S-26 (HIGH), S-27 (LOW), S-28 (LOW). S-16's reachability claim held and is recorded under
"Verified sound" in the tracker. Everything below was attacked and found clean; do not spend a
session re-deriving any of it.

- **`/api/admin/**` authorization covers both new routes.** `SecurityConfig:75-76` is a path-pattern
  matcher over `/api/admin/**`, unconditional in every profile since #243. `AdminUserController` is
  `@RequestMapping("/api/admin/users")`, so both passkey routes are inside it by construction — no
  per-route enumeration to fall out of sync.
- **IDOR on `{credentialId}`.** `WebAuthnCredentialRepository.deleteByIdAndUserId:99-110` scopes the
  DELETE with `WHERE id = :id AND user_id = :userId`; another account's credential returns 0 rows and
  404s. Pinned by `WebAuthnCredentialRepositoryIntegrationTest.deleteByIdAndUserIdWillNotDeleteAnotherAccountsCredential`.
- **IDOR on `{id}`** — any admin can list and delete any account's passkeys. That is the S-24 answer
  (admins are trusted), not a finding.
- **Error-message leakage.** `AdminUserController:447` echoes only the caller's own path input.
- **`PasskeyDeregisterResult` and `PasskeyRow` leakage.** Both scoped to the `{id}` in the path;
  `PasskeyRow` carries id, label, transports, createdAt, lastUsedAt — no public key, no raw
  credential-id bytes. Pinned by `AdminUserControllerTest.listPasskeysReturnsMetadataWithoutKeyMaterial`.
- **Caching.** `CacheControlInterceptor` is default-deny; the passkey list is stamped `no-store`.
- **Deregistration actually stops a login.** `JdbcUserCredentialRepository.findByCredentialId` returns
  null after the delete, covered against real Postgres by
  `WebAuthnCredentialRepositoryIntegrationTest.aDeregisteredCredentialIsGoneFromTheLoginLookup`.
- **S-16 and S-22 do not touch.** `findScopeCollectionIds` and `isCollectionInScope`
  (`ShareLinkRepository:145-194`) resolve scope through `collection_people`, not `role_member`, so
  S-22's `ROLE_MEMBERSHIP_STATUSES` cannot widen a share's scope.
- **S-23** has no interaction with the passkey, session or share paths.
- **Deregistration vs S-1.** `WebAuthnService.finishLogin:218-221` still gates on `mayHoldSession`
  after a verified assertion, so a deleted credential and a non-ACTIVE status are independently fatal.

**Quarantined, not counted as findings.** `deregisterPasskey` is not `@Transactional`, so a concurrent
`register/finish` between the delete and the two response reads can make `remainingPasskeys` report a
credential the admin did not intend to count — report accuracy, probably not security, and the race
was not constructed. There is no audit trail for the deregistration beyond one SLF4J line; whether
this repo wants an audit table for admin credential changes is a product question with no recorded
answer.

## Cross-repo slice — the reverse-direction scan

Every endpoint path literal in `edens.zac`'s `app/lib/api/*.ts` was compared against every
`@RequestMapping` / `@*Mapping` pair under the backend's `controller/`. **No frontend call site
targets a backend route that no longer exists.** `/users/{id}/collections` appears in the frontend but
only inside a docblock at `app/lib/api/roles.ts:4` recording that the route was removed and replaced
by role-based grants; it is not a live call. The bare-array decision in #243 changed no endpoint, and
`parseImageDate` strictness in the same PR is upload-side only.

The backend routes with no frontend consumer are listed in the tracker's cross-repo section.

**Not re-investigated, per scope:** the `coverImage` stripping question and gallery password storage.
Nothing factually new was found about the frontend side of either.

## Working-tree note

The hygiene agent's audit ran while three branches were being cut in the same clone, and it saw a
commit appear and rewind. That commit was #260, which is real and open. Its report's item 15 —
contradictions that "come back if `dbb5271` re-lands" — describes rows #260 itself edits, and was
skipped for that reason.

## 2026-08-31 fifth-run close-out — #23, U-4, MR 25's overload and the AdminUserControllerTest sweep

Four MRs, four board items, all merged: [#269](https://github.com/themancalledzac/edens.zac.backend/pull/269),
[#270](https://github.com/themancalledzac/edens.zac.backend/pull/270),
[#271](https://github.com/themancalledzac/edens.zac.backend/pull/271),
[#272](https://github.com/themancalledzac/edens.zac.backend/pull/272).

### #23 — the stale `.env` template in `ai_ec2.md` ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269))

`+6 / -36`, docs only, exactly as specified. Both env blocks deleted and replaced with a pointer at
`.env.example`; `ai_deployment_strategy.md` untouched per the guardrail. The PR body says explicitly
that this does not settle U-1, so the item's guardrail survives into the record rather than only the
board.

### U-4 — the slide moved below the status check ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270))

The premise held exactly as the fourth run recorded it, and the one-block move was the whole source
change. **What the item did not price was that no existing test could catch the bug.**
`resolveRejectsSessionWhoseAccountWasDisabled` already asserted `expiresAt` is in the future, which
is true under the bug as well as after the fix, so the suite was green either way.

Rule 45 applied cleanly. Enumerating the ways the move can be written wrong gave four, and only one
of them had coverage before this MR:

| Wrong form | Caught by |
|---|---|
| slide left above `findById` or above `mayHoldSession` | **new** — `resolveDoesNotSlideTheWindowOfADisabledAccountsSession` |
| slide deleted outright | `resolveSlidesLastSeenWhenStale` (existing) |
| slide moved below the `return`, unreachable | `resolveSlidesLastSeenWhenStale` (existing) |
| cap dropped during the move | `resolveCapsSlideAtAbsoluteLifetimeCeiling` (existing) |

Mutation-proved rather than assumed (rules 15 and 32): with the slide put back above `findById` the
class runs 15 with **1 failure**, the new test, failing at the `last_seen_at` assertion — at the
guard, not on a fixture gap. Restored: 15/15.

`mayHoldSession` and the absolute-ceiling cap were not touched.

### MR 25's `resolveCollectionDownloadEntries` 2-arg overload ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271))

Every recorded count reproduced before the edit: 5 two-arg sites all in `ContentServiceDownloadTest`,
4 three-arg sites in that same file left alone, 1 `src/main` call at
`ContentDownloadControllerProd:140` using the 3-arg form. Selection was by arity, which is what the
guardrail asked for and what the same-file mix makes necessary.

One thing the item did not name: the 3-arg docblock said the null case is "identical to the 2-arg
overload". Deleting the overload turns that into a dangling reference, so it went too. **A
docblock that cross-references the thing being deleted is part of the deletion's cost** — cheap
here, but it is the kind of line a mechanical arity sweep walks straight past.

The other two MR 25 members were not bundled, per the guardrail.

### `AdminUserControllerTest`'s 73 inline comments ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272))

73 to 0, one file, `+118 / -73`. Delete-and-relocate, not a delete: the substantive ones went into
each test's docblock in the shape #265's own tests already used. What survived the move was the
S-13 `lenient()` explanation (the detail that makes the mutation land at the annotation rather than
404 on an unstubbed lookup), both halves of S-21, S-8 and S-9 with their "mutation this catches"
lines, the two scope guards with their S-7/S-10 reasoning, and the account-takeover guard.

One comment turned out to be a formatting artifact rather than prose: `// Constraining` alone on a
line, wrapping mid-sentence into `// the request enum must not close it.` It reads as a sentence now.

**The rule-42 checksum needed a distinction the rule had not drawn.** #271's PR body says "17 inline
comments deleted" and that is true as a count of comments; the rule-37 metric moved by **16**,
because one of the 17 was a trailing `code; //` and the metric counts only lines whose first
non-whitespace is `//`. Both numbers are right about different things. The reconciliation only
closes if you say which metric you are moving — see working rule 46.

### Two numbers corrected, and neither was caused by this run

**The board's recorded test-side inline-comment figure was 3 low.** It said 1,371 post-merge; the
board's own command at `a9d9e661` returns **1,374**, and it has returned 1,374 since `41d928b4`.
The recorded *delta* for #266 was right (-4); only the absolutes drifted, and they have carried the
offset for at least two runs. This is the failure mode where a number nobody re-runs reads as
authoritative because a past session wrote it down as measured.

**Working rule 36's own parenthetical was stale.** It said `grep -c '^- \[ \] \*\*S-'` "Returns 5 as
of 2026-08-29". It returns **1**, and has since #265. Both cells the rule governs were already
correct at 1 — it was the rule's stamp that rotted, which is the one place nothing was checking.

### MR 19 #15's gating question, settled by reading

The fourth run left MR 19 #15 out of its run because the item carried a question: does `findBySlug`'s
converter strip the gallery password? **It does — and the answer inverts the item.**

`CollectionProcessingUtil.convertToFullModel` -> `convertToModel` -> the shared base never sets
`galleryPassword` or `recipientEmails` on `CollectionModel`. It sets only `isPasswordProtected`, a
boolean derived from `galleryPassword != null`. The second entity fetch exists because
`CollectionService.getUpdateCollectionData` copies those two fields off the entity onto the model
itself, at `931-932`.

So the item's framing — "fetches the collection row twice", filed as a de-duplication — is wrong.
The two fetches return different data on purpose. Deleting the second one returns a null password
and empty recipients; "fixing" that by widening the converter leaks the gallery password onto every
read path sharing it, which is the risk the item flagged and could not price. **The real fix shape
is a two-column projection for this one caller**, and it is a different, smaller change than the
deletion the item describes.

Working rule 21 again: correct premise, prescribed fix that would have shipped a bug.

The item's second sub-claim is also settled. The "always-true null check" was recorded UNVERIFIED;
it is **VERIFIED true**. Both branches of `convertToFullModel` set content — line 352 in the empty
case, line 328 via `convertToModel` — so `collection.getContent()` at `CollectionService:914` is
never null on that path.

### U-1 was asked, and the answer was that it cannot be checked

The fourth run's close-out made U-1 the first thing the fifth run asks, because it gates U-7 and
U-8. It was asked before any code. **The user's answer was that they cannot check right now**, so
U-1 stays BLOCKED, U-7 and U-8 stay blocked behind it, and the twelve-name actuator exclude list at
`application.properties:67` was not touched. That is the correct outcome of the ask, not a failure
of it: the question is now on the record as put and unanswered rather than unasked.


# Ninth-run outcomes (2026-09-01)

Four MRs. Written up here; the tracker keeps one ticked line each with a pointer to these headings.

## #28 outcome 2026-09-01 — unified on 50 (#294)

**Answered in one word at the top of the session and merged the same day.** `+53/-60` across five
files. `page` and `size` moved out of the two controller signatures and into `ImageSearchFilter`,
the record both endpoints already bound as a `@ModelAttribute`, with `@Min(0)` on page,
`@Min(1) @Max(200)` on size and a compact constructor supplying `0` / `50` when the caller omits
them. That ordering matters: the constructor resolves the default before Hibernate Validator reads
the field, so the constraints see a real value whether or not the parameter was sent.

**Observable changes.** Admin's default stays 50; its `size=500` goes from 200-with-200-rows to a
400 and its `size=0` from 200-with-1-row to a 400, because `Math.min(Math.max(size, 1), 200)` is
gone. **Prod's `GET /api/read/content/images/search` moved its default page size 30 -> 50**, which
is a widening of the public read contract.

**The unverified premise, restated because it travels with the change.** MR 19 #19's frontend-safety
finding rests on a 2026-08-24 reading of `edens.zac` that cannot be checked from this repo; there is
still no frontend clone on this machine; and that reading covered *adding keys to a response*, not
*changing how many items a page returns*. The PR body says so plainly, the way #283 did.
**Corrected 2026-09-01 (tenth-run review): the clone does exist, at `~/Code/edens.zac` with
`origin/main` at `f4e8e25`. Both halves were checked live. MR 19 #19's widening is safe -- both
consumers read exactly the keys `PagedResponse` pins -- and the page-size change is real: two public
pages pass no `size` and now show 67% more photos. That debt is filed on the tracker.**

**Two deviations from the prescribed fix — working rule 21 in its usual shape.** The item specified
adding `@Validated` to `AdminController` and keeping #290's `MethodValidationPostProcessor` wiring.
Neither survived: `@Valid` on a `@ModelAttribute` is enforced by the `WebDataBinder`, not by the
`@Validated` AOP proxy, so no proxy is needed on either controller for these constraints, and the
three prod constraint tests now run on the plain `standaloneSetup` MockMvc. The premise (unify on 50)
was evidence; two-thirds of the prescribed mechanism was hypothesis and was wrong.

**Verification.** `mvn clean install` green at 1503. Stripping `@Min`/`@Max` from the record fails
all six constraint tests across both controllers at the guard, 200-instead-of-400; setting
`DEFAULT_SIZE` to 30 fails both default tests. Both mutants die for the right reason (rule 32).

**Cross-repo.** This owes `edens.zac` a row and is the second entry in that debt, alongside MR 19
#19's widened response. **Not filed there from here, and declared rather than noted.**

## CollectionRepository comment concentration (#295)

`+23/-37`, two files, no logic changed, suite 1502 at both ends. `CollectionRepositoryTest` 21 -> 0
and `CollectionRepository` 12 -> 0, both counts exactly as the board recorded them.

**The repository's twelve** were four three-line `====` section banners. They recorded the file's
four-part layout, which is worth keeping for a 900-line file, so it went into the class docblock as
one sentence -- stated once where a reader arrives, rather than four times in the body (rule 10).

**The test's twenty-one** split twelve bare Arrange/Act/Assert markers, which were straight deletes,
and nine carrying content, which went into docblocks on the thing each described: the reflection
swap onto `setNamedParameterJdbcTemplate`, the EXISTS-gate pin and the `hasValue`-not-`getValue`
note onto `sqlGatesOnExistsCollectionContentRowsAndPassesVisibilities`, the "not a bare `is_blog`"
note onto `sqlOmitsBlogPredicateWhenBlogsOnlyIsFalse`, and the UPDATE-path note onto
`updateSqlWritesCollectionEndDateColumnAndBindsParam`.

**Rule 46 did not bite, for the first time in five close-outs.** Both files held zero trailing
`code; //`, so the whole-line count and the comments-deleted count were the same number.

**What the reconciliation found instead — this is where rule 50 came from.** The rule-42 checksum
refused to balance: the test side moved -24 against 21 deleted. The three-line gap was not a missed
file but the *command*. `grep -rc` / `grep -rn` silently skip
`ImageMetadataExtractorKeywordFlagTest.java`, whose `XMP_HEADER` literal ends in a NUL byte that the
XMP packet format requires; BSD grep classifies the file as binary and emits nothing for its three
comments. Under `git grep` both deltas are exact: main 215 -> 203 (-12), test 1,192 -> 1,169 (-23,
being 21 here plus 2 from #297's rule-47 sweep). **The file is correct and the board's command was
not**, and it had been wrong since that test was written.

## CollectionServiceTest assert/verify twins (#296)

`-46`, one file, no main file touched, 1502 -> 1500 tests.

**All four refs landed exact** (`150`, `185`, `222`, `251`) -- the first ref set on this board in
several runs to need no correction, and the eighth close-out's decision to re-derive them *by name*
after #289 rewrote the file is why.

**What was deleted, and why each survivor covers it.**
`createCollection_verifiesEntityCreatedViaUtil` re-ran the happy-path test's stubbing with different
literals and added one assertion the first did not make literally,
`verify(collectionProcessingUtil).toEntity(request, anyInt())`. The happy-path test covers it
transitively: it stubs `toEntity` to return `savedEntity` and then verifies `save(savedEntity)`, so
a `createCollection` bypassing `toEntity` saves something else. Proven by mutation -- replacing the
call with `new CollectionEntity()` fails the survivor at its `save` stub.
`deleteCollection_happyPath_disassociatesAllRelationshipsThenDeletes` made the same four plain
verifies the inOrder test makes, on identical stubbing.

**The subset claim needed testing, and that is now working rule 51.** Plain `verify` fails on a
second matching call; an `inOrder` chain consuming one invocation per position has no obvious reason
to, so "strict subset" was not free and a deletion resting on the word alone would have dropped
cardinality coverage unchecked. Duplicating `collectionRepository.deleteById(id)` **does** fail the
surviving inOrder test. The claim held -- as a fact about Mockito, established by mutation, not as a
consequence of the word.

**Board numbers corrected on the way through.** The file is **2,850** lines (`wc -l`), not the 2,893
recorded at the eighth close-out: #293 added 3 after that measurement, then this MR removed 46. It
has grown 438 since the 2,412 baseline, not 481. And `new ContentModels.Image(` is **14** test call
sites, not 13, in the same 11 test files. **The PR's third claim was itself wrong** -- see the
tracker's `CollectionRequests.Update` row: it reported 26 sites against a test-only figure of 25 by
counting `src/main` too, and re-running the board's own arity scanner shows the row holds exactly.

## #27 outcome 2026-09-01 — the audit emptied the item (#297)

**The guardrail said "audit before wiring" and the audit changed the item before a line was
written.** #27 was filed as a repo-wide gap: `standaloneSetup` builds no `@Validated` proxy, so
`@Min`/`@Max`/`@Size` on `@RequestParam`s never fire, and "every other constraint-annotated
controller parameter in the repo has the same untested gap".

**There is no other one.** Across every file in `controller/`, exactly one method parameter carries
a constraint annotation: `ContentControllerProd.searchImages`'s `page` and `size` -- the one #290
fixed and #294 has since moved into `ImageSearchFilter`. `ContentControllerProd` is also the only
class in the repo carrying `@Validated`. The "wire the proxy" half was zero controllers.

**The conflation, now working rule 52.** The missing proxy affects constraints on *method
parameters* only. Constraints on a record component of a `@Valid @RequestBody` DTO --
`RoleRequests`, `UserRequests`, `InviteRequests`, `EditController.RatingPatch` -- are enforced by the
`WebDataBinder`, which `standaloneSetup` does build, and have been enforced in these tests all
along. The five `@RequestBody` parameters lacking `@Valid` all bind an untyped `Map`, `List` or
`String` with no constraints on them, so nothing is unreachable that way either.

**Coverage was the real remainder, and most of it existed.** `CreateUserRequest` and
`UpgradeUserRequest` email, `UpdateUserRequest` email and status, `InviteRequests` password and
displayName, and `RatingPatch` rating each already had a 400 test. Four did not, and each got one
test plus one mutation: `CreateRoleRequest.name` `@NotBlank` (mutant returns 201), the same field's
`@Size(max = 128)` (201), `SetRoleGrantRequest.level` `@NotNull` (204 -- distinct from the existing
unparseable-enum test, which 400s out of Jackson before validation runs), and
`UpdateUserRequest.description` `@Size(max = 500)` (200, writing 501 characters).

**Rule 32 fired on the fourth and it is worth recording.** On the first pass that mutant died 404 on
an unstubbed `findById` rather than at the guard, which proves a fixture gap and not the constraint.
It now stubs the row `lenient()` -- the same technique and the same reasoning as
`updateWithPersonStatusReturns400AndWritesNothing` three tests below it, whose docblock already
explained why -- and the mutant lands as a 200 that writes.

**No constraint annotation was changed and none turned out wrong**, which was the instruction.
**One finding handed forward as #29**: once #294 landed, `ContentControllerProd`'s `@Validated` has
nothing left to enforce.

# Session log archive — entries moved 2026-08-31

Oldest first. **The tracker keeps the current session's entries and moves the rest here on every
close-out** -- corrected 2026-08-31 (third run); the preamble had said "the two newest", which
matched neither the retention rule at the tracker's own session log nor what the moves actually did.
Extended by the 2026-08-31 third-run close-out.
Extended again by the 2026-08-31 fifth-run close-out.

- 2026-08-30 — **cross-repo filing from the frontend's close-out session. No backend code.**
  Filed **Bug #21** (the dimension fallback fails soft and writes `0`), promoted out of the frontend
  board's C9 after the user's answer reframed it from a rendering question into a data-integrity one
  about this repo. **The filing corrected its own premise before landing** — the obvious version of
  the bug ("EXIF missing, so `0` is written") is false, because `ensureDimensions` already reads the
  header; the real defect is that the header read fails soft three ways and each lands on `0`.
  Severity dropped medium → low on that correction, and the dead fix proposal is recorded in the
  item so it is not re-proposed.
  **Two consequences of #243 the frontend had to absorb, noted here so the trail is two-way.**
  Blessing bare arrays answers the frontend board's G5, which had been sitting BLOCKED-on-user for
  the same decision — it closes there with zero code. Second, and unprompted: making the
  `/api/admin/**` gate unconditional **invalidated a Critical Rule in the frontend's `CLAUDE.md`**,
  which still tells every agent "the local backend serves `/api/admin/**` with no cookie. Do not
  'fix' any of those as a security hole." Filed on the frontend board; flagged here because a
  backend security change silently falsifying a frontend standing instruction is a class of
  breakage neither board was watching for.
  **One gap observed in passing, not fixed:** #243 merged without a session-log entry on this
  board, so the log's newest entry is still 2026-08-29 while HEAD is #243. Left for the backend's
  own close-out rather than reconstructed from here.

- 2026-08-30 (second session) — **three MRs and the four-answer batch.** Shipped S-22
  ([#247](https://github.com/themancalledzac/edens.zac.backend/pull/247)), S-23 ([#248](https://github.com/themancalledzac/edens.zac.backend/pull/248)) and Bug #21 ([#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)), each mutation-proved before
  the PR, then recorded the four product answers the previous entry asked for.
  **The user's four answers:** S-14 *no second gate* (closed), S-16 *suspend, not revoke*
  (unblocked, now the next security item), S-24 *accept as admin-trusted* (closed, with the two
  docblocks that were the answer's whole content), passkey revocation *admin endpoint only* (filed
  as work, not built). **Nothing in the security section is blocked on the user any more.**
  **S-14's answer did not fit either option offered**, and this is the log line for it: the answer
  was a principle -- every admin endpoint through the same admin gate, as simple as possible -- and
  `addCollection` is not an admin endpoint. It sits on `UserShareControllerProd` at
  `/api/read/user/share`; the admin sentinel in `canView` is what makes it answer yes for
  everything. The principle rejects the ownership test the item proposed, so the item closes, but
  it does not decide what a user endpoint should do when an admin calls it. Recorded as closed with
  that gap named rather than forced into "allow, documented".
  **S-22 shipped in a different shape than the item specified, and the review caught it.** The item
  said "route both SQL sites through a named `mayHoldRoleMembership` predicate". That predicate was
  written, and removed on user review: `mayHoldSession` earns its shape from four Java call sites
  that ask it about one user per request, and this rule has **zero** -- it is only ever asked in
  SQL, over the whole set, so the boolean was a zero-caller wrapper around one `!=` added to match
  a convention rather than because anything called it. **The lesson generalizes past this item:** a
  predicate is worth naming when code calls it, not when a board says "named predicate". The list
  bound by both SQL sites is the same single definition with one less name in the API.
  **A verification failure worth recording, because it nearly shipped a false result.** The first
  mutation run on S-23's guard read "10/10 green" from
  `target/surefire-reports` after a build that had actually **failed to compile** -- removing
  `@PostConstruct` left an unused import, checkstyle failed the build, and the stale report from
  the previous run was still on disk. A stale surefire report reads exactly like a passing run.
  Deleting the report file before the mutation run is what turned it into the real answer (5
  failures). Any mutation proof that greps surefire output should delete the report first.
  Next: **S-16** (suspend on share resolve, now unblocked), then the passkey admin endpoint. Both
  are ordinary work with no open questions.

- 2026-08-30 — **the tests-that-cannot-fail queue closed, and the decisions batch settled.** Six PRs:
  R-1 ([#238](https://github.com/themancalledzac/edens.zac.backend/pull/238)),
  `ProdSecretGuardTest.Wiring` ([#239](https://github.com/themancalledzac/edens.zac.backend/pull/239)),
  the share-link `no-store` pin ([#240](https://github.com/themancalledzac/edens.zac.backend/pull/240)),
  the `AdminUserControllerTest` attribution fix ([#241](https://github.com/themancalledzac/edens.zac.backend/pull/241)),
  the decisions batch ([#243](https://github.com/themancalledzac/edens.zac.backend/pull/243)), and
  the config-rot follow-up ([#245](https://github.com/themancalledzac/edens.zac.backend/pull/245)).
  Also rescued [#237](https://github.com/themancalledzac/edens.zac.backend/pull/237): the 2026-08-29
  full-board review had itself been stranded by **working rule 39**, pushed to `docs/close-out-235`
  after #236 merged. The rule fired on the PR that filed it.
  **Every close was mutation-proved against `main` first**, and in two cases `main`'s version shipped
  green under the mutation — `@Component` deleted left `ProdSecretGuardTest` at 13/13, allow-listing
  the share route left `CacheControlInterceptorTest` at 27/27. **Two board premises were wrong and
  were corrected while closing:** the share-link credential is a `Set-Cookie`, not a response-body
  token; and the pointer the board told #241 to write names a test that does not redden on that
  mutation. Naming it would have replaced one false attribution with another.
  **#243 left config rot behind and this close-out found it** — four sites still describing a deleted
  property, filed as C-1 and fixed in #245. **Estimate-versus-actual:** every item matched its stated
  size except #243, which the board scoped as one decision and which touched 11 files; the
  three-decisions-in-one-PR shape was the user's call, and the size warning belongs on any future
  item that removes a config toggle rather than a code path.
  **Board-integrity finding, recorded not fixed:** the history file's header says "**Nothing here
  is open**", and `grep -c '^- \[ \] ' <history>` returns **7** (lines 85, 92, 99, 100, 104, 268,
  783). Three more were carried in by this close-out's own archive move and were neutralised to
  `[x]` before commit; the other seven predate it and belong to other sessions' write-ups, so they
  are reported rather than silently ticked. Someone should decide whether they are genuinely open
  work sitting in the archive (invisible to the board) or stale markup inside closed items.
  Next: **S-22 and S-23** (both COLD), then Bug #21. S-14, S-16, S-24 and passkey revocation need one
  batched user call.

- 2026-08-31 — **six MRs, and the security board went to zero.** Shipped S-22 ([#247](https://github.com/themancalledzac/edens.zac.backend/pull/247)),
  S-23 ([#248](https://github.com/themancalledzac/edens.zac.backend/pull/248)), bug #21 ([#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)), the four-answer batch ([#250](https://github.com/themancalledzac/edens.zac.backend/pull/250)), a
  docblock-trim follow-up ([#251](https://github.com/themancalledzac/edens.zac.backend/pull/251)) and S-16 ([#253](https://github.com/themancalledzac/edens.zac.backend/pull/253)). **"Open security findings"
  is empty for the first time since it was created 2026-08-24**, and none of the five closed on a
  deferral.
  **The four product calls were asked in the opening message and all four came back**, which is what
  made them into MRs instead of the next session's problem: S-14 *no second gate*, S-16 *suspend not
  revoke*, S-24 *accept as admin-trusted*, passkey revocation *admin endpoint only*. **S-14's answer
  did not fit either option offered** -- it was a principle about `/api/admin/**` and
  `addCollection` is a `/api/read/user/share` endpoint -- so it was recorded closed with the gap
  named rather than forced into a disposition the user did not choose.
  **Two items shipped smaller than their board text specified, and in both cases the board was
  wrong rather than the implementation lazy.** S-22's prescribed `mayHoldRoleMembership` predicate
  was written and then removed on user review for having zero runtime callers (**working rule 40**).
  S-16's prescribed second gate on the scope query was not built, because `resolveByRawToken` is the
  only door and the item had **also missed a third site** (`isCollectionInScope`) that the two-gate
  reading would have needed -- so the chokepoint was both simpler and more complete than what was
  written down.
  **Working rule 39 was broken by a session that had it in context.** Docblock trims were pushed to
  two branches whose PRs had squash-merged minutes earlier; the fix was a fresh branch and
  [#251](https://github.com/themancalledzac/edens.zac.backend/pull/251). Rule 39 now carries the two reasons it is easy to miss -- a squash-merged branch
  still reads as "ahead" of `main`, and a handed-over run is being merged as it lands.
  **Working rule 41 is new and cost the session twice**: a stale surefire report reads exactly like
  a passing mutation run, including its old `Time elapsed`.
  **Reconciliation found one number wrong and one command broken.** The rule-37 leading-`//` counts
  re-ran **unchanged** at 1,675 (290/1,385) -- six MRs, no new inline comments. The trailing figure
  did not: its recorded command returns **231**, not the recorded 72, because BSD `grep -E` ignores
  `\s` and nothing excluded `https://`. Corrected to **74** with a portable command now recorded
  beside it. `AdminUserControllerTest`'s 1,294 was **already stale when written** -- last changed in
  #241, before the 2026-08-30 close-out that recorded it, and outside the neighbourhood of anything
  that has merged since, which is the drift a scoped sweep cannot catch.
  **The previous close-out's board-integrity finding is resolved rather than carried a third time.**
  It reported 7 open checkboxes sitting in the history file, where "nothing here is open" is the
  stated invariant, and left them for someone to adjudicate. All 7 are now dispositioned: five were
  archived duplicates of items live on the tracker (the four MR-25 members and `cover_image_id`) and
  now say so; `PersonRepository.findAccountUserIdsByIds` closed with MR 15 #6 / #191; the Wave 3
  chunked-body residual closed as S-5 / #206. **This close-out's own archive move added four more**
  -- the same failure the previous one reported in itself -- caught by re-running its grep and
  neutralised before commit. `grep -c '^- \[ \] ' <history>` now returns **0**. Anyone doing an
  archive move should run that grep after, every time; copying a tracker body copies its checkbox.
  Next: **bug #20, then bug #17, then the passkey admin endpoint** -- see "Next run" below. Bug #19
  needs a one-word direction and the question is written into its item. **A full-board review is now
  due; it is recommended below and deliberately not run.**

- 2026-08-31 (second run) — **four MRs, three bugs closed, and the board's oldest carried bug went
  with them.** Shipped bug #20 ([#255](https://github.com/themancalledzac/edens.zac.backend/pull/255)),
  bug #17 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256)), passkey admin
  deregistration ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)) and bug #19
  ([#258](https://github.com/themancalledzac/edens.zac.backend/pull/258)), plus this close-out.
  **Bug #17 had been carried since 2026-08-24 with no checkbox anywhere; the bug category goes
  4 open -> 1**, and #18 is now the only open bug on the board.
  **The one question was asked in the opening message and came back**: bug #19 *surface, not
  refuse*, which is what made it a fourth MR instead of the next session's problem. Both items that
  left their fix open were decided with evidence rather than by size — #17 by finding the loop's
  *other* per-image writes, the passkey last-credential case by asking what refusing it would
  block.
  **Two items shipped larger than their board text implied, and in both cases the board stopped one
  layer too early.** Bug #19's "widen the orphan queries" hid a response-type change and a
  cross-repo break (**working rule 43**). Bug #20's "~10 lines" was right about the fix and had not
  priced the test it named.
  **Reconciliation moved a count for the first time.** The rule-37 leading-`//` figure went
  1,675 -> **1,644** (290/1,385 -> 262/1,382), and the delta reconciles line-for-line against this
  session's own deletions — **working rule 42**. Trailing held at 74, the rule-12 files held at
  10/6/9, and `grep -c '^- \[ \] ' <history>` is still **0**.
  **Five refs drifted and all five were inside the neighbourhood of what merged**, which is the
  cheap check working as designed: MR 17 #8 (382/395 -> **388/401**), MR 18 #9 (loop declarations
  -> **331**/**459**), MR 18 #10 (`updateGif` -> **550**), MR 19 #13 (-> **986**), MR 19 #15
  (-> **848**). MR 19 #17's `143-145` was re-verified by anchor text and had *not* moved.
  **Two MR 25 counts are now marked UNCHECKED rather than carried as verified** — the raw greps
  available do not discriminate constructor arity, and claiming them would have repeated the
  S-20 dispute (working rule 31).
  **Working rule 39 fired again and was caught**: #255 and #256 squash-merged within minutes, before
  item 3 started; checking PR state before branching item 4 kept it off a stale `main`.
  **One finding is deliberately unfiled**: #258's cross-repo break is not on the frontend board,
  because `edens.zac` had another session's dirty branch checked out. It is declared in the
  cross-repo section rather than silently dropped.
  Next: **the full-board review, which is now on its second restatement and is item 1** — three of
  its six triggers hold, including a new admin endpoint in the unreviewed security set. *(It ran the
  next session; outcome under "Full-board review — run 2026-08-31 (third run)" above.)*
- 2026-08-31 (third run) — **four MRs, the full-board review, and the first fully clean count audit
  the board has had.** Shipped the three answered decisions in one MR
  ([#260](https://github.com/themancalledzac/edens.zac.backend/pull/260)), MR 16 #4
  ([#261](https://github.com/themancalledzac/edens.zac.backend/pull/261)), MR 16 #5
  ([#262](https://github.com/themancalledzac/edens.zac.backend/pull/262)), plus this docs close-out.
  **The three one-word decisions were asked in the opening message and all three came back**, which
  is the only reason they became a same-session MR rather than the next session's problem — the same
  move that turned bug #19 into a fourth MR the run before. `cover_image_id` drop (V59), the
  DB-password default dropped (`${POSTGRES_PASSWORD}`), `role.kind` kept and documented (V60).
  **The full-board review ran as five read-only agents and one apply agent, and produced exactly one
  docs MR with zero code changes** — the guardrail the second close-out wrote for it, honoured. See
  the section above for what each slice returned.
  **Every one of the eleven recorded counts reproduced exactly and nothing moved.** Open bugs 1, open
  security 0 before filing, history open boxes 0, rule-37 leading `//` 1,644 (262 main / 1,382 test) at the time of the audit and **1,637 (262/1,375) after this run's own MRs merged**,
  trailing 74, `RoleRepository` 10, `AdminBootstrap` 6, `CollectionControllerProd` 9, worktrees 6.
  **This is the first close-out where the count audit found nothing at all** — including the two
  figures that were corrected in the previous close-out, which held.
  **The security section refilled after one session empty**: S-26 (HIGH), S-27 and S-28 (LOW). S-26
  is only HIGH because #257 removed the compensating control S-15 was measured against, which is a
  finding no single-item review could have produced. S-16's reachability claim held a third time and
  is now recorded under "Verified sound" so nobody re-derives it.
  **Both UNCHECKED MR 25 arity counts are CONFIRMED** — 21 and 13, by a paren-balanced scanner whose
  method is now written into the item. The raw greps that could not settle them (24 and 28) are fully
  accounted for by higher-arity calls. **The priority flag inverted**: `DownloadResolution.extension`
  reads as the cheapest of the four and is the most expensive (13 edits, 5 files, 2 in `src/main`),
  while `FileEntry` is effectively a single-file change.
  **The cross-repo GIF row's premise was wrong.** The frontend's location page discards the entire
  `images` array and gets its images from a second endpoint, so a location-tagged GIF cannot reach it
  at any prop type. Correcting that turned up a live backend N+1 that #258 introduced and nobody
  filed — up to 150 queries where 6 will do — which is now **MR 19 #21** and item 2 of the next run.
  **The board was misdescribing itself in five places.** The security section claimed to be empty in
  two places while holding four open checkboxes the rule-36 gate could not see; "0 open PRs" was
  wrong and #252 held the only copy of item #22; four live debts sat inside closed `[x]` lines with
  no checkbox; "9 open" config-rot findings existed nowhere in either file; and the open-board
  classification covered about a quarter of the open board. **Every one of them was a summary claim
  its own cited gate could not measure** — that is the pattern, and the fix in each case was the gate,
  not the wording. Two new gates came out of it (`\*\*U-` for the eight unsettled questions) and one
  was deleted for having no backing (the config-rot open/closed split).
  **Six MR 16-19 items were re-priced or corrected**: MR 16 #3 (four limiter copies, not three, after
  `ShareEmailLimiter` landed with S-17), MR 18 #12 (three computing sites and four delegating call
  sites, not "five places" — working rule 14), MR 18 #13 (9 construction sites, 4 Tag and 5 Location,
  not 10+4 and inverted), MR 19 #18 (re-priced down to ~-6 net; two of eleven SELECT lists carry an
  extra column and cannot share a constant), MR 19 #19 (re-priced up to ~25 lines; 7 test
  constructions across 3 files the board never priced), MR 19 #20 (`CollectionService` drifted +3,
  plus an unnamed third source site in `GeneralMetadataDTO`). MR 17 #7's prod ref drifted +1;
  everything else in MR 16-19 re-verified exact.
  Next: **S-26, the HIGH** — see the Next run section. It is the first time in three sessions the
  queue has not opened with a decision or a review.
- 2026-08-31 (third run, post-merge reconciliation) — **no new items, one MR, and three of this
  board's own freshly-written numbers were wrong.** All five of the run's PRs merged
  ([#260](https://github.com/themancalledzac/edens.zac.backend/pull/260),
  [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261),
  [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252),
  [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262),
  [#263](https://github.com/themancalledzac/edens.zac.backend/pull/263)), plus
  [#371](https://github.com/themancalledzac/edens.zac/pull/371) on the frontend. This entry is the
  reconciliation pass that followed, and everything it found was created by the close-out itself.
  **Item #22 was duplicated into two sections** — #263 folded it in while #252 still held its own
  copy, and both merged; the shorter copy is removed and a pointer left in its place.
  **The open-checkbox count was 94 and was actually 90**, because it was measured on the close-out's
  own pre-rebase branch; 89 after the dedupe, with the full +/- reconciliation now written into the
  classification block. **The rule-37 count moved 1,644 -> 1,637** and reconciles line-for-line
  against one file: `ReadCacheInvalidatorTest` 7 -> 0 in #262. `src/main` did not move; `SesConfig`
  held zero inline comments. **Three "still open" claims were stale within the hour** — #252 and
  #371 had both merged, and MR 16 #4/#5 were still listed COLD after shipping. **`0359-fe-ma1-collection-patch`
  reports 1 ahead but is safe to delete**: #252 was squash-merged, so the 0-ahead test does not
  apply to it. Working rule 42 now has a second half: *a count measured on a feature branch is not a
  count of `main`.* Next: **S-26 (HIGH)**.

- 2026-08-31 (fourth run, close-out) — **three MRs, and the two questions this close-out answered
  by looking were worth more than the ticks.** Shipped S-26 + S-27
  ([#265](https://github.com/themancalledzac/edens.zac.backend/pull/265)), MR 19 #21
  ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266)), MR 25's `FileEntry`
  ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267)), plus the close-out
  ([#268](https://github.com/themancalledzac/edens.zac.backend/pull/268)). All three items'
  prescribed fixes needed no adjustment — three in a row. What needed work each time was the test,
  and in two of three the premise was true but *untested*: S-26's "the fix is one call" hid three
  wrong forms of that line (rule 45), and MR 25's "no API-contract effect" rested on Jackson record
  binding nothing exercised. Every recorded MR 25 count reproduced; the close-out then nearly
  shipped a working rule built on a fabricated transcription error, caught before landing, and
  **rule 44** now says the opposite of what it first said. Step 3 answered U-7 and U-8 by reading
  `ProdActuatorExposureGuard` — both moot, but only under `prod`, which made **U-1 a hard dependency
  of both** and re-framed U-1 itself into item **#23**. The scoped drift sweep found 27 of 33 refs
  drifted, 0 gone, nine still resolving to plausible code. Rule-37 checksum 1,637 -> 1,633. Next:
  **#23 and U-4**, with **U-1 asked first**.

- 2026-08-31 (fifth run, close-out) — **four MRs, every one an item the fourth run had specified,
  and all four merged.** Shipped item **#23**
  ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)), **U-4**
  ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)), **MR 25's
  `resolveCollectionDownloadEntries` overload**
  ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)) and the
  **`AdminUserControllerTest` 73-comment sweep**
  ([#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)), plus this close-out.
  Board 87 -> **83**; **the first close-out in three that filed nothing new**, because the run was
  executing a spec rather than discovering one.
  **U-1 was asked first, as the fourth run instructed, and came back "cannot check right now."**
  That is the ask working: U-7 and U-8 stay blocked behind it and the actuator exclude list was not
  touched, with the question now on the record as put rather than unasked.
  **Every prescribed fix landed unadjusted -- four in a row now, seven across two runs.** What
  needed work was again the test. U-4's item said the reorder "costs nothing" and was right, and did
  not notice that **no existing test could catch the bug**: `resolveRejectsSessionWhoseAccountWasDisabled`
  asserts `expiresAt` is in the future, which holds under the bug too. Rule 45's enumeration gave
  four wrong forms of the move, three already covered and one not; the new test was mutation-proved
  at the guard (15 run, 1 failure), not assumed.
  **Step 3 paid again, and this time it inverted an item.** MR 19 #15's gating question --  does
  `findBySlug`'s converter strip the gallery password -- is **answered by reading**: the converter
  never populates `galleryPassword` or `recipientEmails`, only `isPasswordProtected`, and
  `CollectionService:931-932` grafts them on from the second fetch. So the "double fetch" is not a
  duplication at all, deleting it would return a null password, and widening the converter would
  leak that password onto every read path sharing it. The item is now a two-column projection and
  is plain COLD. Its "always-true null check" sub-claim went UNVERIFIED -> **VERIFIED true**.
  **Two recorded numbers were wrong and neither was this run's doing.** The test-side comment
  absolute was 1,371 where the board's own command returns **1,374** (every delta in the chain was
  right; only the absolute rotted), and **rule 36's own parenthetical still said "Returns 5"** when
  the gate has returned 1 since #265 -- both cells it governs were correct the whole time. Both are
  now **rule 46**, along with the trailing-comment metric mismatch (#271 deleted 17 comments and
  moved the rule-37 line count by 16) and the BSD `grep '^\s*//'` trap that bit this close-out's
  first measurement. Rule-37 checksum **1,633 -> 1,536**, the largest single-run drop on this board,
  reconciling exactly: main -2, test -98 = 73 + 16 + 9 across the three files edited.
  Scoped drift sweep: 13 refs re-derived in the neighbourhood of what merged, **7 drifted, 0 gone** --
  all six of `DownloadResolution.extension`'s accessor refs plus its `ContentService:851` -> `:835`
  construction site, and `SessionService:179` -> `:181`. `CollectionServiceTest:2139` re-checked and
  **exact**, which is rule 44's correction holding a second time. Next: **U-5, bug #18 and MR 19 #15**,
  with **U-6 asked first**.

**Format note (2026-09-01).** Entries moved before 2026-09-01 are bullets; the sixth, seventh
and eighth close-outs wrote their entries as `###` headings on the tracker and are archived in
that form. Both are session log entries and the order across both is oldest-first.

### 2026-08-31 -- sixth run. Eleven items, eight MRs, and the run shape changed mid-flight

**Eleven items, the most in one run on this board.** U-5 ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274)), bug #18 ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276)),
U-6 + S-28 ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278)), MR 18 #9 ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)), MR 19 #15 ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280)),
MR 19 #18 + #20 ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282)), MR 19 #19 ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283)), MR 18 #12 ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284)),
MR 17 #8 ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285)).

**Both remaining ledger gates hit zero.** Bug #18 was the last open bug; S-28 was the last open
security finding. `grep -c '^- \[ \] \*\*Bug #'` and `grep -c '^- \[ \] \*\*S-'` both return **0**.
Open checkboxes **83 -> 75**, and the arithmetic needs both halves stated because **this run's own
close-out tripped working rule 42 while citing it.** This run moved it **83 -> 74**: eleven ticked,
two filed (#12b and the `CollectionRepository` comment concentration). **[#277](https://github.com/themancalledzac/edens.zac.backend/pull/277) then merged
from outside the run and filed item #25**, taking `main` to **75**. The 74 was measured on this
close-out's branch before #277 landed, written down, and was wrong within the hour -- which is
exactly *"a count measured on a feature branch is not a count of `main`"*. Corrected here on the
rebase. **[#281](https://github.com/themancalledzac/edens.zac.backend/pull/281) does not move it**: its item #26 is filed already-ticked.

**The run was reshaped by the user, twice, mid-flight.** It started as four sequential items done
inline. After two, the user said the pace was the problem -- *"I need each `/next` session to have
like 10 or more MRs... we likely need to start looking at doing maybe 10 at a time, or... subagent
specific multi MR working, especially with these tiny tickets."* The remaining items were dispatched
as parallel subagents, one MR each. Then: *"if some of these are SMALL enough, we should also
consider grouping them into single MRs for ease of merging."* Applied immediately -- U-6 + S-28 became
one docblock MR, MR 19 #18 + #20 one consolidation MR. **Both instructions are now standing.**

**The parallel dispatch failed the first time, and the failure is worth recording because it is
invisible until it corrupts something.** Five subagents were dispatched without worktree isolation,
so all five shared the parent's single checkout at `~/Code/edens.zac.backend`. Each ran
`git checkout main`, yanking the tree out from under the others and the parent. Damage: HEAD landed
on an unrelated agent's branch, the parent's uncommitted bug #18 edits were silently reverted, and
one agent's in-progress edit sat dirty on another's branch. **The subtlest part**: a half-finished
`ContentModels.java` change rode across branch switches and made the tree uncompilable, which
initially read as a pre-existing break on `main`. It was actually an incomplete copy of
[#277](https://github.com/themancalledzac/edens.zac.backend/pull/277)'s work -- the record change without its `ContentModelConverter` caller. Nothing was
lost (two of five had committed; both kept), but only because the work was small. **The fix is
`isolation: "worktree"` per agent plus a prompt line telling each not to touch the shared clone.**
This is the exception CLAUDE.md's git rule names. All six re-dispatched agents ran clean.

**Three items corrected their own count or estimate on landing, which is working rule 5 again:**
- **MR 18 #9**: estimated ~110 net deleted, **actual -51**. The estimate assumed both callers collapse
  to nothing; they cannot, because the two completion logs differ and ingest's no-capture-date path
  logs WARN with its own job error instead of falling through the generic catch.
- **MR 18 #12**: the board said "five places", the 2026-08-31 correction said "three copies", and
  **there are five copies** -- two more in `CollectionService` the re-derivation missed. "Five" was
  accidentally right about the number and wrong about which things it counted. **Both prescribed
  directions were impossible** (a constructor cycle one way, a gratuitous injection the other); the
  shipped direction, `CollectionRepository.getNextOrderIndexForCollection`, was in neither option.
- **MR 19 #20**: three source sites became **five**. `Records.java` carries two independent ones.

**Working rule 21 held again, twice.** MR 19 #15's prescribed fix (delete the second fetch) would have
shipped a null gallery password; MR 18 #12's two prescribed directions do not compile. Correct
premises, wrong prescriptions, both caught by reading before typing.

**One coverage hole found and closed inside the same run.** #279's mutation testing showed nothing
verified consecutive `orderIndex` values on either upload path -- mutating `takeOrderIndex` to stop
advancing left all 32 tests green. That was routed to #284, which had not yet pushed and added
`processFilesFromDisk_multipleFiles_getConsecutiveOrderIndexes`. **Cross-agent routing of a finding
is new on this board and it worked**; the alternative was a filed item nobody picked up for a week.

**Rule 47 is new** and settles a contradiction the board carried for three runs: rule 37's "delete
the comments already in it" versus the tracker row's "do not sweep this in one MR". Four agents in
one run split on it, all defensibly. The boundary: your region plus what your change makes stale;
a pre-existing concentration is its own MR, and you file it rather than leave it silent.

**U-6 was asked first, as instructed, and the first answer had to be pushed back on.** The user
answered "if it is an ADMIN SPECIFIC ENDPOINT, which it is" -- but `/api/read/user/**` is
`hasRole("USER")` and the route is self-only. Re-asked with the evidence; the user chose keep-and-document.
**S-14 sat on this same misreading for days.** When an item's answer turns on a routing fact, check
the matcher before asking.

**Close-out ordering note.** This close-out branches off `main` and must merge **last**. Seven of the
nine PRs were open when it was first written; five have since merged, and [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284) and
[#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) were **rebased onto the merged `main` and re-verified** before this was finalised --
#284 had one real conflict (a duplicate static import in `CollectionRepositoryTest`, both sides
adding a different one), #285 rebased clean. **#284's rebase was the one that needed proving, not
just building**: [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279) rewrote the loops its new sequencing test guards, so the test was
re-mutated after the rebase -- stopping `takeOrderIndex` from advancing still reddens
`processFilesFromDisk_multipleFiles_getConsecutiveOrderIndexes` and nothing else. A clean auto-merge
would not have shown that. [#277](https://github.com/themancalledzac/edens.zac.backend/pull/277) also edited this tracker from outside the run and merged
first; the rebase was clean, so no hand resolution was needed. The inline-comment row now carries a
real `main` measurement with its scope stated.

**Next:** the board has **no open bugs and no open security findings**, so the next run picks from the
consolidation waves. COLD and specified: **MR 17 #7** (now unblocked by #283), **MR 18 #10**, **#11**
(the five BFS walks, "best value in MR 18"), **#13**'s sort-inconsistency split, **#12b**, MR 19 #17,
and the `CollectionRepository` comment concentration. **U-1 is still blocked and still gates U-7 and
U-8**; do not probe production. **MR 25's remaining two** are still parked behind their guardrails.

### 2026-08-31 -- seventh close-out. No code; the deferred count settled and four items re-derived

**Reconciliation only, and it was owed.** The sixth close-out deliberately declined to re-run the
inline-comment count because seven of its nine PRs were still open, citing working rule 42's second
half. All ten have since merged ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276), [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278)-[#286](https://github.com/themancalledzac/edens.zac.backend/pull/286), plus [#277](https://github.com/themancalledzac/edens.zac.backend/pull/277) and
[#281](https://github.com/themancalledzac/edens.zac.backend/pull/281) from outside the run), so this close-out pays that debt and re-derives the refs in the
neighbourhood of what landed.

**Every recorded command re-run, not re-read.** Open checkboxes **75** (matches the board's claim),
open bugs **0**, open security findings **0** -- both ledger gates confirmed still empty on `main`.
Inline comments **settle at 1,477 (215 main / 1,262 test)**, down from the sixth close-out's `1,487`
checkpoint. **The delta reconciles to one file**: measured commit by commit, #281 and #284 moved it
not at all, and #285 took `src/main` 225 -> 215 with `RoleRepository` going 10 -> 0.

**Four ref sets re-derived, three had drifted:**
- **MR 18 #11** -- `RoleGrantPropagationService` **all four exact** (127/168/188/207);
  `CollectionService.validateNoLinkCycle` **510** and `parentIdsOf` **536**, each **+2**.
- **MR 19 #17** -- `UserInviteService` 158/257 exact, `CollectionProcessingUtil` 566/924 exact,
  `EmailService` 195/246/301 exact; pagination inline **147-149** (**+2**);
  `uploadToS3`/`streamFileToS3` **715**/**742** (**-5 each**, shifted by #279's shared loop).
- **#12b** -- recorded as `~444`/`~1141`, which were approximated call sites, not declarations. Exact:
  `linkCollectionToParent` declared **416**, its copy of the rule at **446**, the child-collection copy
  at **1138**.
- **MR 17 #7** -- `AdminController.getAllImages` **256**, `ContentControllerProd.searchImages` **47**.

**Two corrections that matter more than the drift.**

**MR 17 #7 is unblocked and roughly half of it is already banked.** #283 removed the 286-291 re-wrap
block, so `getAllImages` now ends in one line and both endpoints return the identical type. What is
left is a COLD shared-filter-record refactor plus **a user decision nobody has asked**: unifying the
size handling turns admin `size=500` from a silent 200-row clamp into a 400, and moves two frontend
pages from 30 images to 50. Split, with the decision quarantined.

**The comment-sweep item filed last run was wrong about its own scope, and this close-out filed it.**
It named two files and quoted `~107` lines -- but 107 was #280's count across **four** files. Re-measured:
`CollectionServiceTest` **70**, `CollectionRepositoryTest` **21**, `CollectionRepository` **12**,
`CollectionService` **0** (already done by #280). So: 103 across three, not 107 across four, and not the
33 the named pair actually holds. **Re-scoped to `CollectionServiceTest` alone as the real item** -- at 70
it is the largest single-file concentration left, and [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272) is the worked precedent.

**A pattern worth naming: three consecutive close-outs have now caught a bad number, each in the
close-out's own work rather than in old board text** -- the fifth's `1,276` (three high), the sixth's
`74` checkboxes (stale within the hour), and this one's `~107`. Recorded numbers rot fastest right
where they were just written, because that is the text nobody has re-run yet.

**Next:** four-item run, all COLD -- MR 17 #7's de-dup half, MR 18 #11, #12b, and the
`CollectionServiceTest` sweep. **U-1 is still blocked** and still gates U-7 and U-8; it needs host
access, not a decision, and nobody should probe production for it.

## 2026-08-31 sixth-run close-out — eleven items, and three that corrected their own board entry

Eleven items across eight MRs. Tracker rows carry the one-line outcomes; the detail is here.

### U-5 — the trust-signal sentence ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274))

`ClientIp`'s docblock said "Only requests that flow through the known BFF proxy will carry
`X-Real-IP`, so its presence is the trust signal." That is the same reasoning the sentence above it
uses to *reject* `X-Forwarded-For`. S-19 settled this on 2026-08-25 by reading the live frontend:
`forwardHeaders` in `app/api/proxy/[...path]/route.ts` strips `x-real-ip` and re-derives it.

Replaced with the two things that actually do the work: the BFF strips any client-supplied copy
before re-injecting its own, and `InternalSecretFilter` (`@Order(-200)`, `@Profile("prod")`) rejects
direct hits under the prod profile. Docblock only, +5/-4. The guardrail said stop and report if the
method body needed editing; it did not. The `@link` to `InternalSecretFilter` needs no import, same package.

**The debt had sat untracked since 2026-08-25** inside S-19's closed `[x]` ledger line, where no gate
could see it. That is why U-5 through U-8 were promoted to their own checkboxes at the fifth close-out.

### Bug #18 — the slug check, and an item that did not price its own payoff ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276))

The create path checks the slug: `LocationRepository.findOrCreate` generates it, consults
`findBySlug`, and returns the existing row. `MetadataService.updateLocation` checked only
`findByLocationNameIgnoreCase`, then wrote `SlugUtil.generateSlug(locationName)`. `SlugUtil`
lowercases, strips outside the allowed class, collapses whitespace to hyphens — so "St. Moritz" and
"St Moritz" both give `st-moritz`, pass the name check, and hit `idx_location_slug`.

**The correction the item needed, and why "price it before writing" was the right instruction: the
caller-visible response does not change.** `GlobalExceptionHandler.handleDataIntegrity` discards the
exception message and returns a fixed body, so the DB violation and an explicit check produce the
identical opaque 409. What the fix buys is a deterministic pre-write check instead of reliance on a
constraint that exists only in the schema, a usable operator log, and agreement with the create path
about what makes a location unique. **A caller-visible message naming the slug needs a distinct
exception type with its own handler, and no amount of work in `GlobalExceptionHandler` gets there** —
which is the guardrail's point from the other direction.

`updateLocation` had **zero** tests. Three added, one per way the check can be written wrong
(rule 45). Mutation evidence, checking *why* each reddened (rule 32):

| Mutation | Result |
|---|---|
| `if (false && slugOwner.isPresent() && ...)` — kept the `findBySlug` call so the failure could not come from an unused-stub gap | 1 failure, `..._rejectsANameThatSlugifiesOntoAnotherLocation` at the `assertThatThrownBy`, "Expecting actual throwable to be an instance of" |
| `if (slugOwner.isPresent())` — self-exclusion dropped | 1 error, `..._allowsARenameThatKeepsItsOwnSlug`, throwing `Location slug already in use: st-moritz` on a legitimate rename |

Restored with `touch`, 10/10 green, full build 1,469 tests. **The first mutation was chosen
specifically to avoid a Mockito strict-stubs `UnnecessaryStubbingException`** — deleting the check
outright would have reddened the test on a fixture gap rather than the assertion, which rule 32 says
is not evidence.

### U-6 — a routing question whose first answer rested on a false premise ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278))

S-14 closed on the principle that every admin endpoint goes through the same admin gate, then found
`addCollection` did not fit, and never filed the follow-up. U-6 was that follow-up.

**Asked first, as the fifth close-out instructed. The user's first answer was "if it is an ADMIN
SPECIFIC ENDPOINT, which it is... it should be behind the admin gate."** The premise is false:

- `SecurityConfig` maps `/api/read/user/**` to `hasRole("USER")`, not ADMIN.
- The class docblock already says "the owner side of a share link: session-required, self-only. No
  route accepts a user id -- the principal is the only subject."
- `addCollection` writes to `shareLinkService.findForUser(principal.userId())` — the caller's own link.
- `candidateCollections` is built from `memberCollectionIdsForUser`, the role-grant list. The audience
  is non-admin clients.

Re-asked with that evidence rather than implementing the first answer. The user then chose keep-and-
document. **Admin-gating it would have 403'd every non-admin owner on their own share settings page.**
The admin sentinel only ever widens the admin's *own* share scope, which is why S-14 found no live
hole. **[#275](https://github.com/themancalledzac/edens.zac.backend/pull/275) is a dead reference** — opened by a subagent mid-dispatch, closed when its
branch was deleted; #278 carries the identical commit.

### S-28 — the recovery line, re-aimed ([#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), grouped with U-6)

Verified before writing, rather than trusting the item: `AdminBootstrap.init` looks up
`findByEmail(bootstrapEmail)` and, for an existing row, either promotes a non-admin or warns that
`ADMIN_BOOTSTRAP_PASSWORD` is still set — then returns. **It never resets the password.** So pointing
`ADMIN_BOOTSTRAP_EMAIL` at the locked-out admin does nothing; recovery needs a *fresh* address plus
the password, and a redeploy. The invite route is on `@RequestMapping("/api/admin/users")`, i.e.
behind the surface that is lost.

**The re-aiming the item predicted was real.** #265 rewrote exactly the docblock S-28 proposed to
amend. The current docblock was read first and the added paragraph is what was actually missing.
The WARN said only "until re-invited" — the one route that is unreachable in this case — and now
names the redeploy. No test asserts that string; checked before changing it.

### MR 18 #9 — the shared upload loop, at half the advertised saving ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279))

`runUploadLoop(request, job, CollectionResolver)`; disk passes a constant, ingest passes
`resolveDayBlog`. The disk loop's separate CREATE and UPDATE arms collapsed into the merged arm ingest
already had.

**-51 net (88 added, 139 removed), against an advertised ~110.** The gap is structural: the estimate
assumed the two callers collapse to nothing. They cannot. The completion logs differ (ingest appends a
day count), and ingest's no-capture-date path logs WARN with its own job error rather than falling
through the generic catch — routing it through the catch would have changed the log level and added a
stack trace. Both differences kept explicit rather than flattened.

Two deliberate changes named in the PR: the per-file failure log is now identical on both paths, and
`nextOrderIndex` is lazy on the disk path instead of eager before the loop.

**Its most valuable output was a mutation that survived.** Making `takeOrderIndex` stop advancing left
all 32 upload-pipeline tests green — nothing anywhere verified consecutive `orderIndex` values.
Reported rather than hidden, and closed the same day by #284.

### MR 19 #15 — the projection, and the fixture churn nobody predicted ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280))

Shipped as the re-shaped item specified. `CollectionRepository.findGalleryAccessBySlug` returns a
nested `GalleryAccessRow`, matching the pattern in `AdminHomeTileRepository` and `RoleRepository`.
The converter was **not** widened — populating those fields on `CollectionModel` would leak the
gallery password onto every read path sharing `convertToFullModel`. The dead
`if (collection.getContent() != null)` guard is gone.

**The unpriced cost was in the fixtures.** Ten stub `CollectionModel`s in `CollectionServiceTest` were
built with null content — a state the real converter cannot produce — and NPE'd once the dead guard
was removed. Each needed `.content(List.of())`. Twelve tests needed the new projection stubbed via a
`stubNoGalleryAccess()` helper. **The lesson generalises: deleting a defensive check that is dead in
production can still be load-bearing for fixtures that were never realistic.**

Six new tests, each mutation-proved individually.

### MR 19 #18 and #20 — grouped, and #20 miscounted again ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282))

Grouped at the user's instruction, two commits.

**#18**: three constants, nine call sites, the two serial-number variants left inline as the blocker
required (folding them in changes what `CAMERA_ROW_MAPPER_WITH_SERIAL` / `LENS_ROW_MAPPER_WITH_SERIAL`
receive). **-12 net, but the item's -6 was nearer the truth than that suggests**: the consolidation is
roughly a wash (-7 at call sites, +6 for constants) and most of the delta is six banner comments.
**The value is consistency with the siblings, which is what the item said.**

**#20**: **five source sites, not three.** The board named two; this run's dispatch added
`GeneralMetadataDTO:26`; the implementer found `Records.java` carries **two independent sites** — the
fully-qualified `FilmFormat` at `:23` and the record declaration at `:31`. Zero test sites, as
predicted, because `ContentControllerProdTest` asserts on component names that a type rename does not
change.

### MR 19 #19 — PagedResponse, and a premise that is still soft ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283))

+23/-47 across 7 files; `ImageSearchResponse` deleted. `AdminController.getAllImages` no longer builds
a `PageImpl` to feed `PagedResponse.from`.

**"Seven test constructions" is six constructions plus one variable declaration** (`ContentServiceTest:101`).
All six took `number = 0, last = true`, each derived independently — including the empty-page case,
where `totalPages` is 0 so there is no next page, matching what `PageImpl` produced before. No
assertion was rewritten, as the item predicted.

**The soft premise stays soft and the PR says so.** This widens `GET /api/read/content/images/search`
by the keys `number` and `last`. The frontend-safety claim — it reads only `result.content` and
ignores unknown keys — rests on a 2026-08-24 reading of `edens.zac` that was not re-verified and
cannot be verified from this repo. **Unblocks MR 17 #7.**

### MR 18 #12 — the item was wrong twice, in opposite directions ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284))

The board said "five places". The 2026-08-31 correction said "three copies plus four delegating call
sites" and invoked working rule 14 on the original for mixing units. **There are five real copies**:
the re-derivation missed `CollectionService.linkCollectionToParent` (~444) and the child-collection
add path (~1141). So "five" was accidentally right about the number and wrong about which things it
counted — **rule 14 landing on the correction as well as the original.**

**Both prescribed directions are impossible, and the second was only discovered by trying:**
- `ContentMutationUtil` -> `ContentService` is a hard constructor cycle; `ContentService` injects
  `ContentMutationUtil` at field line 60.
- The reverse would force a `ContentMutationUtil` injection into `TagService`, which injects only
  `TagRepository`, `CollectionRepository` and `CollectionService` — an injection existing solely to
  serve a 4-line helper, which the repo's own guidance rejects.

**Shipped direction was in neither option**: `CollectionRepository.getNextOrderIndexForCollection`,
beside the sole `MAX(order_index)` SQL. All three callers already inject `CollectionRepository`, so
**zero new injections**. Net +2 in `src/main` — eleven duplicated lines become one method carrying a
docblock and `@Transactional` the inline copies lacked.

Also closed #279's sequencing gap. The two `CollectionService` copies were cut deliberately — neither
path has a test pinning its order index — and filed as **#12b**. Consequence:
`getMaxOrderIndexForCollection` stays public.

### MR 17 #8 — delegation, with a shape worth a second look ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285))

`AdminUserController.addUserToRole` / `removeUserFromRole` now delegate to
`AdminRoleController.addMember` / `removeMember`. All four routes stay live, so neither frontend screen
changes — `RoleDetailView.tsx` drives the roles-side route, `UserRolesSection.tsx` the users-side.
Behaviour enumerated before and confirmed after: both pairs return 204, both map
`IllegalArgumentException` to 400.

`RoleRepository.addMember`'s docblock claimed "the two admin endpoints that reach here" and now names
`AdminRoleController.addMember` as the single caller. **Found by reading, not grep** — the phrase wraps
between "two" and "admin", which is rule 31 inside a docblock.

Mutation evidence is unusually direct: swapping the argument order in the roles-side methods reddened
both users-side tests, with the stack naming `AdminRoleController.addMember` as the call site — proof
the users-side request runs through the roles-side method rather than a stub. One new test locks the
400 PERSON-rejection to the users-side route; neither pair had a status test for it.

**The shape a reviewer should weigh**: `AdminUserController` now injects `AdminRoleController`, a
controller depending on a controller, which sits awkwardly beside working rule 19 ("controllers map
results to status codes; everything else is a service"). The item prescribed exactly this and it is
behaviour-preserving. Extracting a small shared service is the alternative if the shape is disliked.

### Process — what changed about how this board gets worked

**The user reshaped the run twice mid-flight**, and both instructions are now standing: ~10+ MRs per
`/next` session via parallel subagents, and grouping small MRs that share a review shape.

**The first parallel dispatch corrupted the working tree.** Five agents without worktree isolation all
ran `git checkout main` in the same clone. Full account in the tracker's sixth-run log entry. The
one-line version: **`isolation: "worktree"` is mandatory for parallel agents that open their own PRs**,
and a half-finished file can ride across branch switches and look exactly like a broken `main`.

**Cross-agent routing of a finding worked and is worth repeating.** #279's surviving mutation was
handed to #284 while it was still running and unpushed; it closed the gap inside the same run instead
of becoming an item nobody picked up.

## 2026-08-31 seventh close-out — the deferred count, settled

No code shipped. This close-out exists to pay a debt the sixth one deliberately took on and to
re-derive the refs in the neighbourhood of ten merged PRs.

### The inline-comment count, and why deferring it was right

The sixth close-out declined to re-run this number, because seven of its nine PRs were open and five
of them deleted comments. Working rule 42's second half — *a count measured on a feature branch is not
a count of `main`* — says such a number cannot be written down. It wrote down the reason instead.

All ten PRs have since merged, so the figure settles at **1,477 (215 main / 1,262 test)**, measured on
`main` with `grep -rn '^[[:space:]]*//' --include='*.java' src/main | wc -l` and the same for `src/test`.

**The remaining delta reconciles to a single file**, measured commit by commit rather than inferred:

| Commit | PR | main | test |
|---|---|---|---|
| `94fcbc75` | #283 | 225 | 1,262 |
| `337d6eee` | #281 | 225 | 1,262 |
| `b0c6967e` | #284 | 225 | 1,262 |
| `b57e9a23` | #285 | **215** | 1,262 |
| `5528660a` | #286 | 215 | 1,262 |

#281 and #284 moved it not at all. #285 took `src/main` 225 → 215, and `RoleRepository` went **10 → 0**
across that one commit.

**Rule 46 fired again, and this is a clean specimen of it.** #285's PR body reports removing *11 lines*
from `RoleRepository`; this checksum moved *10*. The trailing `code; //` count in that diff is **0**, so
the gap is not a trailing comment — it is a non-comment line caught in the same deletion. Both numbers
are correct about different metrics, which is exactly what rule 46 says to state rather than reconcile
away.

Sixth-run arc, both endpoints measured on `main`: **260 / 1,273 → 215 / 1,262**, so −45 main and −11
test. Not wholly attributable to the run: [#277](https://github.com/themancalledzac/edens.zac.backend/pull/277) and [#281](https://github.com/themancalledzac/edens.zac.backend/pull/281) landed
inside that window from outside it.

### Ref drift, scoped to what landed

Four ref sets re-derived; three had moved. `RoleGrantPropagationService`'s four walks were all exact,
which is unusual and worth recording so the next pass does not re-check them without cause.
`CollectionService` moved everything by +2. `ImageProcessingService`'s two S3 helpers moved −5 each,
shifted by [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)'s shared upload loop. Details in the tracker's seventh-run log entry.

### Two corrections worth more than the drift

**MR 17 #7 was blocked on MR 19 #19, and #19 shipped as [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283).** Half of #7 came with
it: the 286-291 re-wrap block is gone and `AdminController.getAllImages` now ends in one line, with both
endpoints returning the identical `ResponseEntity<PagedResponse<ContentModels.Image>>`. What remains is a
COLD shared-`@ModelAttribute` refactor plus a product decision that has never been asked — unifying the
size handling turns admin `size=500` from a silent 200-row clamp into a 400 and moves two frontend pages
from 30 images to 50. The tracker now splits them and quarantines the decision.

**The comment-sweep item filed by the sixth close-out was wrong about its own scope.** It named two files
and quoted `~107` lines; 107 was [#280](https://github.com/themancalledzac/edens.zac.backend/pull/280)'s count across **four**. Re-measured:
`CollectionServiceTest` **70**, `CollectionRepositoryTest` **21**, `CollectionRepository` **12**,
`CollectionService` **0** — already done by #280. The named pair holds 33. Re-scoped to
`CollectionServiceTest` alone, which at 70 is the largest single-file concentration left on the board and
has [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272) as its worked precedent.

### The pattern behind all three

Three consecutive close-outs have each caught a bad number, and in every case the bad number was **the
close-out's own**, not old board text: the fifth's `1,276` (three high), the sixth's `74` checkboxes
(stale within the hour), and this one's `~107`. Board text gets re-derived because sessions distrust it.
A number written down an hour ago reads as measured and nobody re-runs it. **The freshest number on the
board is the least verified one.**

## 2026-09-01 eighth-run close-out — four items, and the two-tier split repaired

Four items across four MRs, all merged. Tracker rows carry the one-line outcomes; the detail is here.

### MR 17 #7 — the filter record, and the constraints that were never enforced ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290))

`ImageSearchFilter` is a record of the ten filter parameters with a `toRequest(page, size)` factory.
Both endpoint methods went 13 parameters to 3, and the 12-argument `ImageSearchRequest` construction
is gone from both. `+246/-58` across 5 files.

**Paging deliberately stayed out of the record, and the reason is the non-obvious part.** Putting
`size` in the record with no default binds an omitted `size` as `0` on both sides, so both defaults
(50 admin, 30 prod) would have to be reapplied in the controllers anyway -- the same code, plus a
wrong-looking zero travelling through the record. Keeping `page` and `size` as plain `@RequestParam`s
also means prod's `@Min`/`@Max` keep firing through the mechanism they always used, instead of moving
to `@Valid` on a `@ModelAttribute` and changing which exception fires.

Both size behaviours were frozen as the guardrail required. **Nine new tests pin them; there were
zero before.** The proof is not that they pass: the controllers were reverted to `main` with the new
tests kept, and all 60 tests in the two classes passed against the old code too. That is what makes
them a freeze rather than a description of the new code.

**The trap, and it is the biggest finding of the run.** Prod's `@Min`/`@Max` on `size` were **never
enforced** in `ContentControllerProdTest`. `standaloneSetup` calls the raw controller instance, so
the `@Validated` AOP proxy Spring Boot builds at runtime does not exist and the constraints are
inert. The first constraint tests returned 200 instead of 400 and **failed identically against
unmodified `main`**, which is what proves the gap pre-existing rather than introduced. #290 fixes it
for this one controller by wiring a `MethodValidationPostProcessor` before building MockMvc. Every
other constraint-annotated controller parameter in the repo has the same gap, now filed as **#27**.

**The item's premise about the size unification is backwards, and settling it needs the user.** The
item says unifying moves two frontend pages "from 30 images to 50". Adopting prod's constraints moves
admin's default **down**, 50 -> 30. Either the board meant unifying on 50 rather than on prod's 30,
or the sentence is inverted; nothing in this repo settles it, and there is no frontend clone on this
machine. **The clone claim was false and is corrected 2026-09-01: `~/Code/edens.zac` is on this
machine.** Filed as **#28**. Cost of the unification, reported not built: ~15 backend lines (move
`page`/`size` into the record, `@Valid` at both call sites, add `@Validated` to `AdminController`
which lacks it, delete the clamp). Admin changes observably three ways -- `size=500` from
200-with-200-rows to 400, `size=0` from 200-with-1-row to 400, an omitted `size` from 50 rows to 30.
Prod does not change at all. Test churn is three of the nine new tests, plus moving the two admin
clamp tests into a validating-proxy nested class like prod's.

**Scope deliberately left out.** 89 pre-existing inline comments in the two test files were left
alone, because a dedicated comment-sweep agent was in flight on exactly that kind of change. **Rule
47**, matching the [#280](https://github.com/themancalledzac/edens.zac.backend/pull/280) precedent.

### MR 18 #11 — five walks, one visitor, and an estimate that forgot the new file ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288))

All five walks unified onto `CollectionGraphUtil.walk(root, neighborsFn, visitor)` -- package-private
static, private constructor, in `services`. `+80/-80` across 3 files.

- `propagateToVisibleSubtree` -- the visitor inserts the inherited grant
- `ancestorsOf` -- no visitor, reachable set minus root
- `subtreeOf` -- no visitor, reachable set including root
- `visiblyLinkedAncestorsOf` -- as `ancestorsOf`, visible-parent lookup
- `validateNoLinkCycle` -- the visitor throws on reaching the target

**Both risks flagged at dispatch were real and neither blocked.** Early termination needed nothing
special: the throw from the `Consumer` propagates straight out of the loop, so there is no stop signal
and no `Optional` return. The per-node work in `propagateToVisibleSubtree` is just the visitor.
**A visitor-shaped BFS helper absorbs both the early-exit and the per-node-work variants without
extra machinery** -- worth knowing the next time this shape comes up, though it is a fact about
writing that helper rather than about working this board, which is why it is not a working rule.

The helper returns a `LinkedHashSet` in root-first BFS order, so the two ancestor walks
`remove(collectionId)` to keep the root out. Same contents, same order.

**Placement.** `services` already holds SlugUtil, PaginationUtil, TokenUtil, ContentMutationUtil and
CollectionProcessingUtil, so a static util there is the existing convention rather than a new
abstraction. Putting it on `CollectionService` would make `RoleGrantPropagationService` depend on a
service it has no other reason to know; a Spring bean would add a constructor dependency to both for
a stateless function.

**The estimate correction, now working rule 48.** The board said "~95 lines". Actual is +80/-80, net
**zero**. The ~95 tracked raw deletions; the 47-line new file is the single copy plus docblocks. The
win is one loop instead of five, not fewer lines. **This failure mode travels**, and it applies to
every remaining "extract a shared helper" item on the board.

**Held -- do not re-investigate.** "Zero test churn" and "33 integration tests" are both exactly
right, the first estimate in a while to need no correction. No test file was touched. The 33 are
`CollectionLinkSecurityIntegrationTest` (14), `RoleGrantPropagationServiceIntegrationTest` (13) and
`RoleGrantVisibilityToggleIntegrationTest` (6); none names a private method. The cycle guard is
pinned by six of them, so no new test was needed.

**Left alone deliberately.** The two `parentIdsOf` are byte-identical -- both call
`findAllParentCollectionsByChildId`, map `getId`, `toList`. The dispatch warned they might differ and
they do not. Deduping them would force the helper to hold a `CollectionRepository`, converting a
stateless util into a dependency-holding one. That is a different change, not cowardice.

### #12b — the last two nextOrderIndex copies, and a coverage price that was wrong ([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291))

Both copies delegate to `CollectionRepository.getNextOrderIndexForCollection`. `+113/-8` across 3
files. All three refs were re-derived by name and **none had drifted** -- `linkCollectionToParent`
416, its copy 446, the child-collection copy 1138, exactly as the seventh close-out recorded.

**`getMaxOrderIndexForCollection` is now private.** These two were its last callers outside
`CollectionRepository`; the only remaining caller is `getNextOrderIndexForCollection` in the same
class, and no test mocked it. Its `@Transactional(readOnly = true)` was dropped -- the public wrapper
carries it, and Spring's proxy never applied it to a self-invocation anyway. **This closes the
consequence the item recorded**: "stays public because these two still call it".

**The "expensive to cover" premise did not hold, and the correction is now working rule 49.**
`CollectionLinkSecurityIntegrationTest` already drives both writers against real Postgres for the
S5/S6 checks, and already has a `linkViaStructureTab` helper sending exactly what the admin Structure
tab sends. The order-index tests are the same shape as tests already in that file.
`CollectionServiceTest` was never touched. **Before pricing an item as expensive to cover, check
whether an integration test already drives the path** -- this board has now mis-priced coverage that
way twice.

Tests went into a new `CollectionLinkOrderIndexIntegrationTest`, against real Postgres so the
`MAX(order_index)` SQL actually runs rather than a stub returning a number:

- `linkCollectionToParent_appendsEachChildAtTheNextIndex` -- three links land at 0, 1, 2
- `structureTab_withoutOrderIndex_appendsEachChildAtTheNextIndex` -- three children in one payload
  land at 0, 1, 2
- `structureTab_withExplicitOrderIndex_usesItInsteadOfAppending` -- pins the lazy branch

**Both delegations were mutation-proved**, each broken by passing `childCollectionId` instead of
`parentId`, and only the matching test failed. Watched failing, not assumed.

**Behaviour change worth flagging.** The child-collection path now queries only when the request omits
an `orderIndex`; it previously ran the `MAX` query unconditionally and discarded the result when the
caller supplied an index. Same value written, one fewer query per explicitly-positioned child. Matches
how [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284) rewrote the same ternary in
`ContentMutationUtil`.

**Its new test file added the 22nd `CollectionRequests.Update` arity-17 site**, which moved the MR 25
item's count from 21 -- the second time that item has grown because a test was written elsewhere.

### `CollectionServiceTest`'s 70 inline comments ([#289](https://github.com/themancalledzac/edens.zac.backend/pull/289))

70 whole-line comments to **0**, and 4 trailing `code; //` to **0** as well -- the trailing four sat
on `containsExactly` assertions in the ParentCollections tests, which the board's whole-line grep
never counted. So the file held **74** inline comments while the rule-37 checksum moves **70**.
**Rule 46 for the third consecutive run, and this time it was predicted rather than discovered
afterwards.** `+111/-75`, one file.

The 70 were 42 contiguous blocks: **58 lines (30 blocks) relocated** into the enclosing test's or
helper's Javadoc, and **12 lines (12 blocks) deleted outright** -- ten Arrange/Act/Assert markers, one
restating the `verify` beneath it, and one duplicating a claim its own docblock already made.

`+111/-75` against [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)'s `+118/-73`.
Lower because several relocated comments became one-line docblocks rather than multi-line ones.

Tests: 91 before, 91 after, 0 failures both ways. Full suite passes.

**Held -- do not re-investigate.** Every checkable claim in the 70 comments held against the code, the
same outcome the earlier `CollectionService` pass had. **Two files down, and no comment has yet been
found lying about its code.**

Flagged rather than filed as its own row: `updateWithSiblings` keeps two `/* collections */` and
`/* siblings */` positional argument labels on the 17-arg constructor call. They are block comments,
not the `//` form rule 37 counts, and removing them makes the call harder to read. They should
disappear when the `CollectionRequests.Update` item shortens that constructor, and are noted there as
a rider.

## Tracker detail moved 2026-09-01 — repairing the two-tier lapse

Working rule 11 says a closed item leaves one line on the tracker and its write-up goes here. Eleven
closed items were still carrying full write-ups on the tracker when the eighth close-out ran. Seven of
them ([#274](https://github.com/themancalledzac/edens.zac.backend/pull/274),
[#276](https://github.com/themancalledzac/edens.zac.backend/pull/276),
[#278](https://github.com/themancalledzac/edens.zac.backend/pull/278)-[#285](https://github.com/themancalledzac/edens.zac.backend/pull/285))
already had their detail in the sixth-run close-out section above, so the tracker text was a duplicate
and was replaced with a pointer. **The four below had no copy here at all and are moved verbatim**,
plus S-28's tracker body, which was the one piece of genuinely unique text in the set.

Two of the eleven were misplaced as well as long. **MR 17 #8's outcome paragraph sat under the
`## MR 18 — Services` heading**, attached to no item, from the sixth close-out until 2026-09-01. And
**S-28's ticked body sat under `### Open` in "Open security findings"** while its outcome paragraph sat
under `### Closed` attached to no bullet -- so a section whose gate correctly returned 0 still read as
holding an open finding. Both are now one ledger line each.

### MR 16 #4 — one AWS config class, and the property key that had to stay

**DONE 2026-08-31 (third run).** `S3Config` and `SesConfig` are one `AwsClientConfig` with a shared
`AwsCredentialsProvider` bean; 127 source lines became 89, and both catch-log-rethrow blocks are gone.
All four `@Bean` method names are unchanged, so every by-type injection is untouched.

**The zero-test-coupling claim was re-verified before starting, as the board asked, and it held -- but
it was incomplete in a way that mattered.** No test imports, constructs, `@Import`s or `@MockBean`s
either class; the only `src/test` mention was a comment in `application-test.properties`, updated in
the same commit. What the claim left out: **51 test classes load the full context and instantiate both
configs**, and they start only because `application-test.properties` supplies `aws.access.key.id`,
`aws.secret.access.key` and `aws.s3.region`. So the merge is free *only while those three property
keys are unchanged*. Renaming `aws.s3.region` to a neutral `aws.region` -- the tidy-looking move, since
SES borrowing an S3 key is the thing this item complained about -- fails all 51 at context load. **The
key was deliberately left as `aws.s3.region` and the class docblock says why**, so the next reader does
not "finish" the job. Test churn was zero Java lines, exactly as promised; 1450 tests, unchanged count.

*Item as filed:* zero test coupling -- nothing in `src/test` references `S3Config` or `SesConfig`, and
there is no `@Import`, so the rename to `AwsClientConfig` is free. Premise verified intact 2026-08-24.
`config/SesConfig.java` duplicates S3Config's credentials plumbing and borrows `aws.s3.region` for a
non-S3 client. Merge the SesV2Client bean into S3Config (rename it `AwsClientConfig`), share one
`AwsCredentialsProvider` bean across the four clients, and delete the catch-log-rethrow blocks. ~40
lines.

### MR 16 #5 — one CloudFront invalidation, and a trap worse than the item's wording

**DONE 2026-08-31 (third run).** `ReadCacheInvalidator` gained a public `invalidatePaths(List<String>)`,
and `ImageProcessingService.invalidateCloudFrontPaths` is deleted; both delete paths call the delegate.
`ImageProcessingService` drops `CloudFrontClient` and the `cloudfront.distribution-id` `@Value` and
gains `ReadCacheInvalidator`, so the constructor goes **arity 10 -> 9** as predicted.

**The `markChanged()` trap was confirmed by reading both methods, and it is worse than the board's
wording.** `markChanged()` publishes an event whose listener always invalidates the two constants in
`READ_SURFACE_PATHS` (`/api/read/collections*`, `/api/read/content*`). Those are API routes, so they do
not match media keys **at all** -- routing image deletes through it would mean deleted bytes keep being
served from the edge until their own TTL expires. It is not just "wildcards instead of specific keys
plus a deferral"; two of the three differences are outright wrong. `invalidatePaths` therefore runs
synchronously with no event and no `@TransactionalEventListener`, and `READ_SURFACE_PATHS` is untouched.

**The guard is a test, not a comment**: `invalidatePathsSendsSpecificKeys` captures the request builder
and asserts the exact prefixed media keys, so making that swap reddens the suite. It was
**mutation-proved before shipping** (working rules 15/32/41): dropping the `"/" + k` prefix reddened
exactly that one test and nothing else. Five tests added, none rewritten; 1450 -> 1455. The class
docblock's "two wildcards ... which is why this does not take per-slug paths" sentence was scoped to
`markChanged`, since the class now does take explicit paths.

*Item as filed:* **the item undersells itself** -- `cloudFrontClient` and `cloudFrontDistributionId`
are used only inside `invalidateCloudFrontPaths`, so delegating removes two constructor dependencies
(arity 10 -> 9). Test cost is ~4 lines and no mock or verify is rewritten. **Trap**: route through
`invalidatePaths(List<String>)` as written -- routing through `markChanged()` swaps specific keys for
two wildcards and defers to after-commit, which is a behavior change.
`ImageProcessingService.invalidateCloudFrontPaths` (**declaration at `869` as of 2026-08-31**, was
865-885, before that 838-863 -- +4 from #249's docblock; find by name) re-implements what
`services/ReadCacheInvalidator.java:~79-106` already owns. Give `ReadCacheInvalidator` an
`invalidatePaths(List<String>)` and delegate. ~25 lines.

### #24 — the locations component the resolver never read ([#277](https://github.com/themancalledzac/edens.zac.backend/pull/277))

**DONE 2026-08-31.** *(Filed 2026-08-31 from the frontend board's SD2 (`docs/spikes/2026-features.md`
in `edens.zac`), in the same pass that shipped it, per that board's rule that a cross-repo item filed
on one board only is invisible where it lands.)* `COLLECTION` content blocks carried no `locations`, so
the frontend's shipped `/collections` location filter matched nothing.

**The frontend board's spec was wrong about the size, and in the cheap direction.** It asked for a
locations batch-load mirroring the tags one at `SyntheticCollectionResolver:109`. That query already
runs -- `CollectionProcessingUtil.batchConvertToBasicModels:92-93` calls
`locationRepository.findLocationsByCollectionIds` once for the whole page and `buildBasicModel:156-161`
sets `CollectionModel.locations`. The resolver simply never read it. So the fix was one record
component on `ContentModels.Collection` plus a copy in `fromCollectionModel`: no new repository method,
no new query, no migration, no added N+1.

Hence no `withLocations` twin to `withTags`. Tags are fetched after conversion and need the post-hoc
setter; locations arrive on the model, so a `withLocations` nobody calls would be dead code.
`ContentModelConverter.buildCollectionRecord` deliberately keeps `List.of()` -- it runs once per
content row with no batched map, so filling it there would be the N+1 this avoided.

Additive public API change: the synthetic list views, the tag view (`TagViewResolver:91`) and the
`/user` page (`UserPageAssembler:161`) all gain a `locations` array of `{id, name, slug}` on each
`COLLECTION` block.

### #26 — a retention TTL, shipped off ([#281](https://github.com/themancalledzac/edens.zac.backend/pull/281))

**DONE 2026-08-31.** *(Filed 2026-08-31 from the frontend board's MA4 (`docs/spikes/2026-features.md`
in `edens.zac`), in the same pass that shipped it.)* Contact messages had no retention TTL, so PII
accumulated forever.

`messages` rows are a stranger's email address beside whatever they wrote, `V17` gives them a
`created_at` with a descending index, and nothing ever removed them. `MessageService` had `create` and
`delete(id)` and no bulk path.

**Shipped off, and the first opt-in only reports.** The deletion is irreversible -- the contact form is
the only writer and nothing archives what a purge removes -- and a local backend can point at the
production database, so "try it on localhost" is not a way to find out what it does. Hence two
properties rather than one:

- `app.messages.retention.days` (default `0`) -- the nightly job returns before touching the database.
- `app.messages.retention.dry-run` (default `true`) -- logs the count it would delete, deletes nothing.

Set `days`, read the count out of the logs, and only then set `dry-run=false`. Both defaults sit at the
safe end, so a deploy of this MR changes no behaviour at all.

`purgeOlderThan(LocalDateTime cutoff)` is package-private and takes its cutoff, the arrangement
`JobTrackingService.removeFinishedJobsStartedBefore` already uses, so the modes are testable without
aging a row or waiting for the cron. **Both guards are mutation-proved**: relaxing `retentionDays <= 0`
to `< 0` reddens the defaults test, and removing the dry-run branch reddens the dry-run test. That check
is here because of this board's own "Tests that cannot fail" section.

**No frontend half exists or is needed.** MA4's row reads BE+FE, but a retention TTL has no admin
surface -- it is configuration, not a control. The frontend-side MA4 slices are mark-as-read, delete
(already shipped), and search (shipped as
[#384](https://github.com/themancalledzac/edens.zac/pull/384)). **Mark-as-read was blocked here for
want of a read column and is not any more** -- `V61` added `read_at` in
[#300](https://github.com/themancalledzac/edens.zac.backend/pull/300), 2026-09-01, and the frontend
half is now owed to the frontend board as item #30's FE side.

### #30: the read marker and the filters that share its WHERE clause ([#300](https://github.com/themancalledzac/edens.zac.backend/pull/300))

*(Body moved out of the tracker 2026-09-01 by the tenth-run refile, under working rule 53. The row
itself stays open: the backend half is done, the frontend half is owed.)*

**A timestamp rather than a boolean.** One column answers both "is it read" and "when was it first
read", and mark-unread is the same UPDATE writing NULL. The write is `COALESCE(read_at, NOW())`, so
re-marking a read message keeps the original time instead of moving it. That is what lets a 0-row
result mean "no such id" rather than "nothing changed", and so lets the 404 stay correct.

**`?unread=` and `?q=` shipped together because they are the same WHERE clause.** Filing them apart
would have written that clause twice. `?q=` replaces the client-side filter shipped in
[edens.zac#384](https://github.com/themancalledzac/edens.zac/pull/384), which could only search the
rows already loaded.

**The list and its count share one WHERE fragment.** The admin list prints "N of M". Counting
unfiltered while paging filtered makes M a number about a different row set, which is a wrong total
rather than a stale one. LIKE wildcards in operator input are escaped, so searching `50%` matches
that text instead of every row.

**The new index is partial and carries the list's ORDER BY.** Unread is the selective side: it
shrinks as mail is triaged while read grows without bound. Reading the whole table already has
V17's `idx_messages_created_at`.


### S-28 (tracker body, moved 2026-09-01)

The finding as filed. Its outcome is in the sixth-run section above; its ledger line is on the tracker.
**S-28 (LOW) an admin deregistering their own last passkey can lock the admin surface out of
itself.** *(Filed 2026-08-31, third run.)* `AdminUserController.java:453-476` as of #265, was `443-465`. Removing the last
credential is allowed **by decision** (2026-08-30) and this does not re-litigate that; it records
the downstream consequence. A sole passkey-only admin who deletes their own last credential loses
the admin surface, and every recovery route is behind it: issuing a fresh invite is
`POST /api/admin/users/{id}/invite`, and `POST /api/auth/login` needs a password hash the account
does not have. **Recovery exists but is narrow.** `AdminBootstrap.init` (`AdminBootstrap.java:50-88`)
seeds a new ACTIVE admin **only when no `users` row holds the bootstrap email** — it returns early
for an existing row and, for an existing admin, logs a warning and ignores `ADMIN_BOOTSTRAP_PASSWORD`
entirely. So recovery means setting `ADMIN_BOOTSTRAP_EMAIL` to a **fresh** address plus
`ADMIN_BOOTSTRAP_PASSWORD` and redeploying; pointing it at the locked-out admin's own address does
nothing. LOW because a path exists and the trigger is an admin acting on themselves. Fix shape: a
line in the endpoint's docblock naming the redeploy recovery — the WARN at
**`AdminUserController.java:467-470` as of #265** (was `455-459`; **the stale range now lands on the
404 `ResourceNotFoundException` guard** -- real code, right method, wrong statement) is the only
signal today and it does not say what to do next.

**Severity premise falsified by [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265), and it moves the wrong way.**
S-28 is LOW partly because "a path exists": before #265, an admin who deleted their own last passkey
kept a working session for up to the 60-day sliding TTL and could register a replacement from inside
it. S-26's fix revokes that session, so **the lockout is now immediate** and the informal escape
hatch S-28's LOW was priced against is gone. The recovery path is still the redeploy, and it is
still narrow. **The fix shape also needs re-aiming**: #265 rewrote exactly the docblock this item
proposed to add a line to, and did not add the recovery line.

Extended again by the 2026-09-01 ninth-run close-out.
Extended again by the 2026-09-01 tenth-run close-out (the full-board review).

### 2026-09-01 -- eighth run. Four MRs, a fourth bad number, and the two-tier split repaired

**Four items, four MRs, all merged**: MR 17 #7 ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290)),
MR 18 #11 ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288)), #12b
([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291)) and the `CollectionServiceTest`
comment sweep ([#289](https://github.com/themancalledzac/edens.zac.backend/pull/289)), plus this
close-out. Exactly the four the seventh close-out specified, and every one of them landed.

**Both comment counts re-run, and the trailing one was wrong.** Leading form **1,477 -> 1,407**
(215 main / 1,192 test), trailing form **72 -> 68**. Both deltas reconcile line-for-line to one file
(**rule 42**): `CollectionServiceTest` 70 -> 0 whole-line and 4 -> 0 trailing. `src/main` did not move
at all. **The board's recorded trailing figure was `74`, stamped "re-run at the second close-out and
still 74"; at the pre-run commit it measures `72`.** It was 2 high and had rotted unnoticed for six
runs. That is **the fourth consecutive close-out to catch a bad number** -- 1,276, then a stale 74
checkbox total, then ~107, now this. The difference this time is that the number sat nowhere near
anything that merged, which is why nobody re-ran it.

**A fifth bad number, found while running the gates rather than reading the board.**
`grep -c '^- \[ \] \*\*U-'` returns **5**, not the **7** the Progress row and the section's own
rule-36 stamp both claimed. U-5 and U-6 shipped in the sixth run and were ticked in the section;
neither the row nor the stamp was edited with them. Rule 36's failure mode, in a section that is not
the one rule 36 was written about. Both fixed.

**Three ref sets drifted, and one of them got dangerous.** The `CollectionServiceTest` assert/verify
twins all moved (138/167/198/226 -> 150/185/222/251) because #289 rewrote the file, and the item's
reassurance that the stale numbers "land on blank lines, so this set fails visibly rather than
plausibly" is now **false and deleted**: all four land on real code, and `226` lands on a
`service.deleteCollection(collectionId);` call inside a delete test. Following it would look like
confirming the item while reading the wrong test. `CollectionRequests.Update` went 21 sites to
**22**, the new one added by #291's own new test file -- the second time that item's count has moved
because a test was written elsewhere. MR 18 #13's `CollectionService` Location pair is `265` and
**`267`**, the second drifted -2; **its other seven refs were not re-checked and the item now says
so**, because those files sit outside the neighbourhood of what merged.

**Two working rules hoisted out of the closed items, one lesson left where it was.** **Rule 48**:
a line estimate on an "extract a shared helper" item counts the deletions and forgets the file the
extracted code lands in -- MR 18 #11 was estimated at ~95 and shipped net zero, MR 18 #9 at ~110 and
shipped -51. **Rule 49**: check whether an integration test already drives the path before pricing an
item as expensive to cover -- #12b was parked on a coverage price that `CollectionLinkSecurityIntegrationTest`
had already paid, and this board has now mis-priced coverage that way twice. Not hoisted: MR 18 #11's
finding that a visitor-shaped BFS helper absorbs both early-exit and per-node-work variants without
extra machinery. It is true and useful, but it is a fact about writing that helper, not about working
this board; it stays in the write-up.

**The two-tier split had lapsed and this close-out repaired it.** Eleven closed items were still
carrying full write-ups on the tracker -- the sixth run's whole set, plus MR 16 #4 and #5, items #24
and #26, S-28 and bug #18. All eleven now have one-line outcomes with an archive link. Two of them
were worse than long: **MR 17 #8's outcome paragraph sat under the `## MR 18 — Services` heading**,
attached to no item, and **S-28's ticked body sat under `### Open` with its outcome orphaned under
`### Closed` beside no bullet.** Both are fixed. **Nothing still open was moved** -- every relocated
section was checked for a merged PR first.

**Two items filed, each with a row and a section in this edit.** **#27** (coverage gap): controller
parameter constraints are untested repo-wide, because `standaloneSetup` builds MockMvc without the
`@Validated` proxy, so `@Min`/`@Max` never fire. Found by #290 and confirmed pre-existing -- the first
constraint tests failed identically against unmodified `main`. **#28** (user decision): unify the
image-search page size on prod's 30 or admin's 50? MR 17 #7's own text says unifying moves two
frontend pages "from 30 to 50" and that is backwards.

**One dead lead dropped**, under working rule 5: the `CollectionServiceTest` "profiled in parts" lead
and its ranges 937-1385 / 1555-2017, against a file that is now 2,893 lines and was rewritten
wholesale by #289. It had been carried three times with an instruction to drop it.

Open checkboxes **75 -> 73**: three ticked (MR 17 #7, MR 18 #11, #12b), one removed with the dropped
lead, two filed (#27, #28). The comment item was **re-scoped rather than ticked** -- `CollectionServiceTest`
is done, `CollectionRepositoryTest` (21) and `CollectionRepository` (12) remain.

**Next:** **#28** (answered below, so it leads the run), **#27**, the `CollectionRepositoryTest` /
`CollectionRepository` comment concentration, and the assert/verify twins. MR 18 #13's
sort-inconsistency split and MR 19 #17 stay queued behind them.

**#28 was answered before this close-out was pushed**, which is the first time a blocked-on-user item
on this board has been asked and settled inside the same session that filed it. **The answer is
admin's 50.** It goes first in the next run and it is no longer BLOCKED. **#27 must re-derive its
refs after #28 lands** -- #28 adds `@Validated` to `AdminController` and edits both controller tests,
which is exactly the neighbourhood #27 audits.

### 2026-09-01 -- ninth run. Four MRs, an answered question shipped same-session, and seven bad numbers

**Four items, four MRs, all merged**: #28 ([#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)),
the `CollectionRepository` comment concentration ([#295](https://github.com/themancalledzac/edens.zac.backend/pull/295)),
the `CollectionServiceTest` assert/verify twins ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296))
and #27 ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)), plus this close-out.
Exactly the four the eighth close-out specified.

**#28 was asked in the opening message and shipped in the same session.** It was the run's only
blocked-on-user item, the answer was one word ("admin's 50"), and it never spent a session blocked.
That is the second consecutive run where batching the question into the opening message converted it
into one of that run's own MRs, which is enough to call the practice settled.

**#27's guardrail said "audit before wiring" and the audit emptied the item.** It was filed as a
repo-wide gap: "every other constraint-annotated controller parameter has the same untested gap".
**There is no other one.** Exactly one method parameter in `controller/` carries a constraint, and it
is the one #290 fixed and #294 has since moved into a record. Every other constraint in the package
is on a `@Valid @RequestBody` DTO, which `standaloneSetup` **does** validate -- the conflation is now
**working rule 52**. Zero proxies were wired; four genuinely uncovered constraints got a test and a
mutation each; no constraint annotation was changed and none turned out wrong. **The guardrail is
what made this cheap**, and it is the clearest case yet for writing one: without it the run would
have wired proxies into test classes with nothing to validate.

**Seven recorded numbers were wrong, the most in one close-out on this board.**
`AdminUserController` 601 -> **614** lines, its injected fields 10 -> **12**, its test 1,294 ->
**1,510** lines, `ContentModels.Image` 13 -> **14** test sites, the board's own open-checkbox count
73 -> **72**, the `U-` gate stamp 7 -> **5**, and the leading comment-count **command itself**.
**Three of those sit outside the neighbourhood of anything that has merged in a week** -- the
`AdminUserController` trio went stale at #285 and survived three close-outs -- which is the condition
the scoped sweep structurally cannot cover and the strongest of the four full-board-review triggers
now met.

**Rule 50, and it is the checksum eating itself.** The board's recorded leading-form command,
`grep -rn '^[[:space:]]*//' ...`, silently skips `ImageMetadataExtractorKeywordFlagTest.java`: its
`XMP_HEADER` literal ends in a format-required NUL byte, so BSD grep calls the file binary and emits
nothing for its 3 comments. At `b02520b1` the recorded command returns 1,189 and `git grep` returns
1,192 -- and the board had recorded **1,192**, so the number and the command it was stamped with have
never agreed. Both endpoints now use `git grep`. This is rule 31 inside rule 42's checksum for the
second time (rule 46's first half was `\s` under BSD grep), which is enough repetition to state the
general form: a checksum command is itself a recorded number and rots the same way.

**One correction this run owes itself.** #296's PR body filed the `CollectionRequests.Update` arity
count as drifted -- "26 sites, not 25". **Nothing had drifted.** The board's 25 is test-only; #296
counted `src/main` and `src/test` together. Re-run with the board's own paren-balanced scanner the
row holds **exactly**: 25 raw in test, 22 at arity 17. That is working rule 31 caught in the act by
the session that had just written a rule about it, and the row now says `-- 'src/test'` out loud so
the next pass cannot repeat it.

**What held, so nobody re-derives it.** All four of the eighth close-out's re-derived
`CollectionServiceTest` refs landed exact (`150`, `185`, `222`, `251`) -- the first ref set in several
runs needing no correction, which is what re-deriving *by name* buys. The trailing comment count held
at 68 across both endpoints. The `CollectionRequests.Update` arity count holds. Rule 36's two security
cells are consistent with each other and with the gate (0 open). `git grep` is tracked-files-only, so
the `.claude/worktrees/` contamination hazard is removed rather than re-checked.

**Filed:** **#29** (`ContentControllerProd`'s `@Validated` now has nothing to enforce -- the
"unreachable constraint" case #27's guardrail anticipated, arriving as a proxy with no constraint
rather than a constraint with no proxy). **Taught working rules 50, 51 and 52.**

**Next:** the **full-board review**, recommended and not run for the second time -- four of six
triggers hold. If it does not run: **#29**, **MR 18 #13's sort split**, **MR 19 #17**. The last two
are now carried forward a second time and are flagged as such.

# Tracker detail moved 2026-09-01: the tenth-run refile (working rules 11 and 53)

Everything below was moved out of the tracker in one pass on 2026-09-01, on branch
`docs/full-board-review-2026-09-01-tenth-run`. Two kinds of text: closed rows whose bodies had stayed
in the tracker after the MR shipped, and the "Prior text" chains that close-outs had been prepending
into table cells instead of moving out. Nothing here is open, and nothing was deleted -- each tracker
cell now carries the current value, its gate command, and a link to the heading below that holds the
rest. This refile is what working rule 53 exists to make routine.


## Four main-dead, test-live members: body moved 2026-09-01

- [x] **Four main-dead, test-live members owed to MR 25** -- **CLOSED as an umbrella row 2026-09-01
  (tenth run).** Two shipped ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267),
  [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)); the two that remain are
  tracked as their own rows under [Positional constructors that block the `TestFixtures`
  pass](2026-08-22-backend-cleanup-spike.md#positional-constructors-that-block-the-testfixtures-pass), where the counts and the
  guardrails live. **Do not re-file them here** -- this row was a second open checkbox for the same
  two pieces of work and inflated the board's open total by one. Body kept for the arity-scanner
  method, which is still the way to measure them (deleting them means editing test call
  sites, which is why MR 1a deferred them): `ContentService.resolveCollectionDownloadEntries` 2-arg
  overload (**DONE** -- [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31; all 5 counts reproduced on the day and the arity split held),
  `DownloadResolution.extension` (**0 main / 6 test, CONFIRMED 2026-08-31** -- but see the priority
  flag under MR 25: this is the most expensive of the four, not the cheapest),
  `CollectionRequests.Update`'s 17-arg constructor (**22 test sites, RE-DERIVED 2026-09-01**; was 21, and [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291) added the 22nd),
  `DiskUploadRequest.FileEntry`'s 3-arg constructor (**DONE** -- [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267), 2026-08-31; 13 sites, re-derived on the day and reproduced exactly). **Two of the four remain** -- `FileEntry` shipped as [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267) and `resolveCollectionDownloadEntries` as [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271) (2026-08-31).
  **All four counts now reproduce. The two UNCHECKED markers are cleared.** The raw greps that could
  not settle them are fully accounted for: `new CollectionRequests.Update(` returns **25** in test =
  22 compat-arity + 3 canonical 22-arg calls (re-derived 2026-09-01 on `main` at `43c6f2c6`; the
  recorded 24 = 21 + 3 was one behind). The two `CollectionProcessingUtilTest` canonical sites and
  the one in `CollectionServiceTest` are found by name, not by line -- **stop restamping them**
  (`CollectionProcessingUtilTest:290`, `:491`,
  `CollectionServiceTest:2139` -- **drifted from `:2056` by
  [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266), which added 83 net lines to
  this file (2056 + 83 = 2139), verified with `git show 3c034c94:<file> | sed -n '2056p'`. The
  original ref was correct.** This entry first went in claiming `:2056` had never been right; that
  was wrong and its reasoning is now **working rule 44**); the `FileEntry` equivalent returned
  **28** = 13 three-arg + 15 canonical six-arg, and that member shipped as
  [#267](https://github.com/themancalledzac/edens.zac.backend/pull/267). Both are main-dead: zero `src/main` callers of the 17-arg overload (the one main
  construction is `CollaboratorRequests.java:43`, arity 22) and zero `src/main` constructions of
  `FileEntry` at any arity — it only ever arrives via Jackson, which binds the canonical constructor,
  so deleting the 3-arg one has no wire effect.

  **The method, so the next session gets the same numbers** (single-line grep cannot see a
  google-java-format'ed `new Foo(\n a,\n b)`, which is why this stayed UNCHECKED for a week). Write a
  paren-balanced scanner: from each regex match, walk forward from the opening paren tracking nesting
  depth, skipping string and char literals, text blocks and both comment forms, and count commas at
  depth 1; arity is commas + 1, or 0 for empty parens. Save it as `arity2.py` at the repo root and run
  `python3 arity2.py '<regex>' <root>`:

  ```
  python3 arity2.py 'new\s+CollectionRequests\.Update\s*\(' src/test/java
  python3 arity2.py 'new\s+(DiskUploadRequest\.)?FileEntry\s*\(' src/test/java
  python3 arity2.py 'resolveCollectionDownloadEntries\s*\(' src/test/java
  python3 arity2.py '\.extension\s*\(' src/main/java src/test/java
  ```

  **Root every scan at `src/main/java` and `src/test/java` from the repo root** -- `.claude/worktrees/`
  holds stale copies of these test files and inflates every number. When the regex is a bare method
  name, declarations match alongside calls and must be subtracted by hand. Also listed under MR 25
  below, where the counts and two corrected premises now live.

  **`AuthPrincipal`'s 4-arg constructor was a fifth entry and has been removed from this list**: it
  is **not** main-dead. `SessionService` calls it -- which the old entry admitted two lines below a
  "zero `src/main` callers" heading. Disposition is now a decision, not a deferral: leave it. All **36**
  call sites (re-measured 2026-08-29: 35 test plus `SessionService.java`, **`:181` as of [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)**, was `:179` -- U-4's docblock addition shifted it; the old 30 was stale)
  are one-liners, and deleting a 3-line convenience constructor to append `, null` at 35 clean
  sites is not an improvement.

## V19's `admin_home_tile.cover_image_id`: research body moved 2026-09-01

- [x] **V19's `admin_home_tile.cover_image_id`** -- **ANSWERED and DROPPED 2026-08-31**, shipped as
  `V59__drop_admin_home_tile_cover_image_id.sql` and verified in the tree 2026-09-01
  (`ALTER TABLE admin_home_tile DROP COLUMN cover_image_id;`). **This box stayed open through five
  close-outs after the decision landed** and inflated the board's headline count by one; its own
  last sentence pointed at a row that was already ticked. Research body kept below for the premise
  correction it records. *(Prior state: research COLD, disposition still a decision.)*
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


## #25: the `people` component (moved 2026-09-01)

- [x] **#25 (same gap as #24) — `people` on `COLLECTION` content blocks was inert for exactly the
  reason `locations` was.** — **DONE**
  ([#293](https://github.com/themancalledzac/edens.zac.backend/pull/293), 2026-08-31). *(Found
  while shipping #24; filed rather than bundled, because the frontend board's rule is one MR per
  item and `people` is not part of SD2.)*

  `batchConvertToBasicModels` already batch-loaded `peopleByCollectionId` via
  `collectionPeopleRepository.findPeopleForCollections` and set it on the model, and the frontend
  already read `ref.people` — `contentFilter.ts:946` matches on `name`. The only missing link was
  the `people` component on `ContentModels.Collection`, so the loaded value had nowhere to ride out
  on and the filter matched against nothing.

  Followed #277 exactly: one record component, one copy in `fromCollectionModel`, no repository
  method, no query, no migration, no N+1. No `withPeople` twin for the reason there is no
  `withLocations` twin — people arrive on the model, so one nobody calls would be dead code.
  `buildCollectionRecord` keeps `List.of()`, and its docblock now states that for all three of
  tags, locations and people.

  Additive: every `COLLECTION` block on the synthetic list views, the tag view and the `/user` page
  gains a `people` array of `{id, name}`. `Records.Person` has no slug, so unlike locations there
  is no second field to keep in step.

  **The predicted cost of splitting it was exactly right** — the 20-component positional
  constructor and its four test call sites were edited a second time, and nothing else.


## FE-1: the location-page GIF chain (moved 2026-09-01)

- [x] **FE-1: the location page's `images` array can now carry GIFs, and the component types it
  `ContentImageModel[]`.** **CLOSED as won't-do 2026-09-01 (tenth run), by the BE-2 answer.** The
  decision is to drop the array, so the location page stays on `searchImages({ locationId })` and no
  GIF ever arrives through `LocationPageResponse.images` to widen the props for. **The "never reads
  the field" premise was re-verified live** against `edens.zac` `origin/main` at `f4e8e25` on
  2026-09-01: `parseCollectionArrayResponse` (`app/lib/api/collections.ts:56-69`) returns
  `data.content ?? data.collections ?? data.items` and discards `.images`, `.location`,
  `.totalCollections` and `.totalImages`. **The GIF goal that made this row worth having has a
  cheaper home**: teach `searchImages` to return GIFs, which is where every other image on that grid
  already comes from. That is a backend item, not this one. Two of the three recorded frontend refs
  are exact (`LocationPageClient.tsx:29`, `LocationPage.tsx:14`); the `count={images.length}` line is
  at **`LocationPage.tsx:41`**, not 42. Body kept for the chain it documents. *(Filed 2026-08-31 from [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258); working rule 43's evidence.)*
  `LocationPageResponse.images` widened from `List<ContentModels.Image>` to `List<ContentModel>`, so
  a location-tagged GIF now serializes into that array with `contentType: "GIF"`. Both frontend refs
  confirmed unmoved 2026-08-31: `app/components/LocationPage/LocationPageClient.tsx:29` and
  `app/components/LocationPage/LocationPage.tsx:14` both declare `images: ContentImageModel[]`.

  **PREMISE CORRECTED 2026-08-31 (third run), and the correction matters more than the row.** The
  old text said this was dormant because nothing is location-tagged as a GIF yet — a data claim the
  board could not check. The real reason is structural and checkable: **neither prop is fed by that
  endpoint.** `app/location/[slug]/page.tsx:80-83` fetches two things in parallel.
  `getCollectionsByLocation` hands the location endpoint's body to `parseCollectionArrayResponse`
  (`app/lib/api/collections.ts:55-68`), which returns `data.content ?? data.collections ?? data.items`
  — it takes `.collections` and **discards `.images`, `.location`, `.totalCollections` and
  `.totalImages`**. The `images` prop comes from a second call, `searchImages({ locationId })`, which
  hits `GET /api/read/content/images/search` and returns `ImageSearchResponse`, images only, untouched
  by #258. **So a location-tagged GIF cannot reach the frontend today at any prop type**, and
  widening the props alone changes nothing. Tagging one is reachable from the admin UI
  (`ContentGifModel.locations` exists and the metadata modal accepts a GIF), so the count is zero by
  habit, not by construction — but the discard is what makes this dormant.

  **What would break if a GIF did arrive**: an off-by-one header count, not a crash.
  `LocationPageClient.tsx:55-57` already narrows to `contentType === 'IMAGE'` before rendering, so
  the GIF is dropped from the grid — but the unfiltered array still feeds `count={images.length}`
  (`LocationPage.tsx:42`), `extractFilterOptions`, `computeFilterVisibility` and
  `computeFilterCounts`. "12 photos" over 11 tiles.

  **Fix shape**: widen both props to `ViewableContent[]` (`app/types/Content.ts:443` — exactly
  `ContentImageModel | ContentParallaxImageModel | ContentGifModel`), keep an `isImageContent`
  narrowing for the one `computeFilterVisibility` call (the only image-typed consumer left), and
  delete the `contentType === 'IMAGE'` filter. Leave `coverImage` as `ContentImageModel | null`. The
  precedent to copy is `CollectionPageClient.tsx:317-359`, which holds mixed content and derives an
  image-only list purely to compute filter dimensions. **But this fix only matters once BE-2 in the
  Decisions section is answered** — while the page's images come from `searchImages`, no GIF arrives.

## MR 19 #19's widened response: verified no action 2026-09-01

- [x] **MR 19 #19's widened response -- CLOSED as verified-no-action 2026-09-01, not filed.**
  [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) made `ImageSearchResponse` a
  `PagedResponse<ContentModels.Image>`, adding `totalElements`, `totalPages`, `number` and `last`.
  The 2026-08-24 frontend-safety claim had never been re-verified; it has now been, live against
  `edens.zac` `origin/main` at `f4e8e25`. **Both consumers are safe.** `searchImages`
  (`app/lib/api/content.ts:128-153`) reads `result.content` and nothing else. `getAllImages`
  (`content.ts:305-340`, the admin `/all-images` UI) reads exactly `content`, `totalElements`,
  `totalPages`, `number` and `last`, each behind a `typeof` guard -- it was already written against
  this shape. Nothing is owed to the other board.

## Closed decisions: bodies moved 2026-09-01

- [x] **Passkey revocation** — **SHIPPED 2026-08-31** ([#257](https://github.com/themancalledzac/edens.zac.backend/pull/257)).
  Admin endpoint only, as decided 2026-08-30: `GET` and `DELETE
  /api/admin/users/{id}/passkeys[/{credentialId}]`, both on the existing `/api/admin/**` gate. No
  user-facing list-and-remove. **Removing an account's last credential is allowed** — refusing it
  would block the one case the endpoint exists for — and the DELETE reports `{remainingPasskeys,
  passwordLoginAvailable}` so the admin sees when an account has been left unable to log in.
  Reasoning, the no-op `JdbcUserCredentialRepository.delete` finding, and the test that proves a
  deregistered credential cannot complete `finishLogin` are in
  [history](2026-08-22-backend-cleanup-history.md#2026-08-31-second-close-out--bugs-17-19-20-and-passkey-deregistration).
- [x] SpotBugs: decided — delete all four artifacts. Done in MR 2. If static analysis is wanted
  later, introduce it fresh at a current version with a filter written from scratch.
- [x] `admin_home_tile.cover_image_id` — **ANSWERED 2026-08-31 (third run): drop it.** Shipped as
  `V59__drop_admin_home_tile_cover_image_id.sql`. The premise was re-verified before the migration
  was written and holds exactly: V19 creates the column and seeds all ten tile rows with an explicit
  NULL, and `AdminHomeTileRepository:17` is the only Java that touches `admin_home_tile` at all. The
  other sixteen `cover_image_id` hits in `src/` are the unrelated column on the `collection` table
  and are untouched. No confirmation query was run against prod: the column cannot have received a
  value through the application, so the query only guards a manual DB edit, and the migration's
  header carries the one-line restore recipe if one turns up.
- [x] **Whether to ship a default DB password at all — ANSWERED 2026-08-31 (third run): drop the
  default.** `spring.datasource.password` is now `${POSTGRES_PASSWORD}` with no fallback, which
  fails the context at startup when the variable is unset. Option (b), chosen for consistency with
  `ACCESS_TOKEN_SECRET` and the AWS keys in the same file. Three things were checked before
  changing it, because "prod is unaffected" was the claim the decision rested on: `docker-compose.yml:19`
  sets `SPRING_DATASOURCE_PASSWORD` from the env, which shadows the property outright; the test
  classpath's own `src/test/resources/application.properties` shadows the main file entirely, so no
  test context ever resolves this placeholder; and CI's `POSTGRES_PASSWORD` (`.github/workflows/ci-cd.yml:55`)
  is a service-container variable that is never exported to the Maven step, so it was never load-bearing
  either. `.env.example` now marks the variable required. *(Original wording below, kept for the
  options it enumerates.)*
  *(Consolidated here 2026-08-29 — this
  decision previously had three homes: a "Carried forward" bullet, an open checkbox inside the
  history file's closed MR 9 section, and this section's promise of a row that did not exist. This
  row is now the only one.)* MR 9a fixed the separator and preserved the existing default, so
  `spring.datasource.password` falls back to `password` (`application.properties:13`). Options:
  (a) keep `${POSTGRES_PASSWORD:password}` as-is; (b) drop the default entirely with
  `${POSTGRES_PASSWORD}`, which fails the context at startup when unset, matching how
  `ACCESS_TOKEN_SECRET` and the AWS keys already behave in this file; (c) `${POSTGRES_PASSWORD:}`,
  which defers the failure to the first connection attempt. Prod is unaffected either way — compose
  shadows this property on every deploy — so the real question is what a local run should do when
  the variable is missing. *(The 2026-08-22 log line "Decided: keep the default DB password" was
  bug #9's scope call — keep the existing default while fixing the separator — not a disposition
  of this question; the history log entry is annotated to match.)*
- [x] `role.kind` — **ANSWERED 2026-08-31 (third run): keep it, documented as provenance.** Shipped as
  `V60__comment_role_kind.sql`, a `COMMENT ON COLUMN` recording that PERSONAL marks a role the V45
  backfill created and SHARED marks every role made since. The 2026-08-24 correction was re-verified
  against `V45__create_roles.sql` before writing it: line 37 inserts `'PERSONAL'` and lines 44 and 50
  join on `r.kind = 'PERSONAL'`, so both values are real in any database that ran V45 against a
  non-empty `user_collection`. No prod query was needed — the disposition is *keep*, and the grouping
  query only mattered for the drop case. The DB comment is the durable answer to "why is this column
  here", which is the question that made it look droppable twice.
  *(Original row below, kept for the corrected premise it records.)*
- [x] `role.kind`. **Premise FALSE, corrected 2026-08-24.** The item says it is "written as constant
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
- [x] **#28 — which direction does the image-search size unification go: prod's 30, or admin's 50?**
  **ANSWERED by the user 2026-09-01 ("admin's 50") and SHIPPED the same day**
  ([#294](https://github.com/themancalledzac/edens.zac.backend/pull/294), ninth run, `+53/-60`,
  five files). Asked at the top of the session exactly as the item specified, so its answer became
  one of the run's MRs rather than the next run's problem -- **the second time batching a one-word
  question into the opening message turned it into a same-session MR**, and the practice is now
  settled rather than experimental.

  **`page` and `size` moved into `ImageSearchFilter`** with `@Min(0)` / `@Min(1) @Max(200)` and a
  compact constructor supplying the defaults, so the paging contract has one definition instead of
  two. Admin's clamp is deleted: `size=500` and `size=0` now 400 rather than 200-with-clamped-rows.

  **The public read contract widened and it is UNVERIFIED against the frontend.**
  `GET /api/read/content/images/search` moved its default page size **30 -> 50**. MR 19 #19's
  frontend-safety finding rested on a 2026-08-24 reading of `edens.zac`, and that reading covered
  adding keys to a response rather than changing how many items a page returns. **Both halves were
  checked live on 2026-09-01 against `edens.zac` `origin/main` at `f4e8e25` -- the clone is on this
  machine and the board's claim that it is not was false.** MR 19 #19's widening is safe and that
  debt is closed. **This one is real**: two public pages pass no `size` and now show 67% more photos.
  Filed in the cross-repo section. Stated plainly in the PR body,
  the same way [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) did.
  **This owes `edens.zac` a row and it is the second entry in that debt**, alongside MR 19 #19's
  widened response. **Not filed there from here** -- declaring that plainly rather than leaving a
  "belongs on the other board" note, per the cross-repo rule.

  **Two deviations from the item's prescribed fix, both reported, both because the record turned out
  to be a better home for the constraints than the method signatures were.** No `@Validated` was
  added to `AdminController`: `@Valid` on a `@ModelAttribute` is enforced by the `WebDataBinder`, not
  the `@Validated` AOP proxy. And #290's `MethodValidationPostProcessor` wiring in
  `ContentControllerProdTest` went with it -- those three tests now run on the plain `standaloneSetup`
  MockMvc and still fail correctly without the constraints. **Working rule 21 again**: the premise was
  evidence, the prescribed fix was a hypothesis, and two-thirds of it did not survive contact.
  [Write-up](2026-08-22-backend-cleanup-history.md#28-outcome-2026-09-01--unified-on-50-294).
- [x] **Whether the location endpoint should keep serving an `images` array at all (BE-2).**
  **ANSWERED by the user 2026-09-01 (tenth run): drop the array.** The backend stops hydrating and
  serializing the orphan `images` array on `GET /api/read/collections/location/{slug}`, and the
  frontend stays on `searchImages({ locationId })`. **This closes FE-1 as won't-do rather than by
  doing frontend work** -- verified live against `edens.zac` `origin/main` at `f4e8e25`, the location
  page never reads the field: `parseCollectionArrayResponse` (`app/lib/api/collections.ts:56-69`)
  returns `data.content ?? data.collections ?? data.items` and discards the rest. Nothing on the
  frontend breaks.

  **Why not option 1.** Consuming `LocationPageResponse.images` would have made the page worse, not
  faster. That array is orphans only -- `findOrphanContentByLocationName` appends
  `ORPHAN_COLLECTION_EXCLUSION` for every listed collection -- while `searchImages({ locationId })`
  returns every image at the location. The grid would have shrunk to leftovers, the cover image
  (`app/location/[slug]/page.tsx:85` picks the first rating >= 4 from the same array) would have
  degraded worst at the best-curated locations, and `count={images.length}`
  (`LocationPage.tsx:41`) would have started meaning something else.

  **The one thing option 1 would have bought -- GIFs on location pages -- has a cheaper home:** add
  GIF support to `searchImages`, which is where every other image on that grid already comes from.
  One source for the grid, one place to change. That is a separate backend item and is not filed
  here.

  **MR 19 #21's N+1 fix is unaffected and stays banked.** It shipped as
  [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266) and only changes how
  already-fetched entities become models; under this decision the whole method is deleted along with
  the array. The board's claim that the fix was correct under both options was true.

  **The removal itself is filed as a COLD item under MR 19.**



## Next run (tenth close-out): the run's own framing, moved 2026-09-01

**Next run (set 2026-09-01, tenth close-out)** *(tracker text, superseded)*

**The full-board review ran, and it was the whole run.** Eight read-only slices, one apply agent, one
docs MR, zero code changes -- the guardrail that has held for every full-board review on this board
held again. Three security findings, thirty-eight bad numbers and seven premise corrections came out
of it, and every proposed code fix became a board row rather than a diff.

**The run after this one, in order. This supersedes the ninth close-out's three-item list.**


## Classification of the open board: arithmetic moved 2026-09-01

**Classification of the open board (stamped 2026-09-01, tenth close-out)** *(tracker text, superseded)*

**The open-checkbox count is deliberately not written here.** This close-out is a docs MR on a branch,
and the count changes with its own edits; **rule 42 says re-run it on `main` after the merge, never on
the branch, and this exact cell has been wrong twice for exactly that reason.** The parent session
runs `grep -c '^- \[ \] ' ai_docs/reviews/2026-08-22-backend-cleanup-spike.md` on `main` after the
merge and writes the number here. For reference, `main` at `43c6f2c6` held **69**.

**This review's arithmetic on the board's size, so the post-merge number can be checked rather than
trusted.** Ticked or removed: MR 16 #3 (decided), the V19 `cover_image_id` box (shipped five
close-outs ago), the four-main-dead-members umbrella (a duplicate of two rows below it), BE-2
(answered), FE-1 (won't-do), `Synthetic.blogsOnly` (deleted), `AdminHomeService`'s cache (demoted to a
note), Appendix D (deleted). Filed: S-29, S-30, S-31, the `RoleRepository.canView`/`isClient`
deletion, the #294 page-size debt, the location `images` removal, the `searchImages` GIF widening,
and MR 19 #19's debt closed rather than filed. The branch worklist went from three boxes to three
boxes with different contents.

**The classification below covers the whole open board for the first time.** Every prior version
covered about 25 items against a board of 69, so a reader taking the heading literally concluded the
board was a quarter its actual size. Waves 6 and 7, most of MR 26, the unsettled questions, the
stale-docblock pair, the branch worklist and the appendices were all outside it.

**COLD -- pick up with no unanswered question:** #29 (three lines, cheapest on the board); the two
MR 14 stale docblocks; MR 18 #10 (~180 lines, refs re-derived this review); MR 19 #17 members (a),
(b), (c) and (e); MR 25's `DownloadResolution.extension` (unparked), its `TestFixtures` /
`ContentModels.Image` pass, its four typeless-migration ITs and the `ImageUploadPipelineServiceTest`
verify ratio; **U-2**, which is answerable in-tree today and had no bucket until now; MR 21's whole
Map inventory; eight of MR 22's nine rows; MR 23's three package moves; six of MR 24's rows; MR 26's
eight coverage gaps; both new cross-repo backend items (dropping the location `images` array, adding
GIFs to `searchImages`); the `RoleRepository.canView`/`isClient` deletion; and Appendix C's leads,
which are research rather than MRs.

**BLOCKED (ordering):** **MR 25's `CollectionRequests.Update`**, on the `Update` half of the
`TestFixtures` pass -- **re-classified from COLD this review**, because calling it COLD makes it look
pickable when picking it alone is the wrong move. **MR 18 #13's sort split**, on reading the
production collation. **U-7 and U-8**, both on U-1.

**BLOCKED (user):** **U-1** -- host access, still off `Next:` until the user says they have it.
**U-3** -- the `ACCESS_TOKEN_SECRET` rotation story, a real judgement, and it had no bucket until
this review. **The `coverImage` stripping row** -- a judgement, not a word; the premise was
re-verified mechanically (`CollectionProcessingUtil:182` sets `coverImage` with no password gate, and
computes `isPasswordProtected` four lines below), so the strip could be implemented trivially and
whether it *should* be is the open question. **`V54FoldMigrationIntegrationTest`** -- join the
consolidation or stay deliberately exempt; its own docblock argues for exempt. **MR 22's try-catch
row** -- an untested behavior change on two admin endpoints with zero coverage, so somebody decides
whether the tests come first. **S-29's severity.**

**BLOCKED (other repo):** FE-2 through FE-5 and the #294 page-size debt. **They wait on the frontend
acting, not on someone filing them** -- [#371](https://github.com/themancalledzac/edens.zac/pull/371)
filed all five rows and merged 2026-08-31, verified live this review as C14, C15, C16, H7 and G6. The
board's stated reason had been stale for two runs.

**PARKED by decision:** gallery passwords (pending a design) and C7's partial indexes (an explicit
"not until scale demands it"). Both now sit under their own subheading in the Decisions section so
that section cannot read as a queue of live questions. **MR 16 #3 joined them as closed-by-decision**
this review.

**DONE since the last list was drafted.** The ninth run shipped #28
([#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)), the `CollectionRepository`
comment concentration ([#295](https://github.com/themancalledzac/edens.zac.backend/pull/295)), the
`CollectionServiceTest` twins ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296))
and #27 ([#297](https://github.com/themancalledzac/edens.zac.backend/pull/297)). The eighth run
shipped MR 17 #7 ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290)), MR 18 #11
([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288)), #12b
([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291)) and the `CollectionServiceTest`
comment sweep ([#289](https://github.com/themancalledzac/edens.zac.backend/pull/289)). Earlier runs'
outcomes are in the [history file](2026-08-22-backend-cleanup-history.md).


## Full-board review, tenth run: tracker prose moved 2026-09-01

**Full-board review — RUN 2026-09-01 (tenth run)** *(tracker text, superseded)*

**It ran, and it was the whole run.** Eight read-only agents, one apply agent, one docs MR, zero code
changes. Slices: recorded numbers; code references in every open item; near-term premises; the far
set (MR 21-24, Waves 6-7, the appendices, branches and worktrees); security; board self-consistency;
the cross-repo pair; and estimates with COLD/BLOCKED classification.

**What it found, one line each.**

- **Three summary cells recorded a gate command that can never match anything.** Lines 37, 49 and 50
  carried `grep -c '^- [ ] \*\*S-'` with the brackets unescaped. `[ ]` is a bracket expression
  matching one space, so the pattern needs `"- "` plus two spaces, and no checkbox line looks like
  that. **It returns 0 on any input.** It agreed with reality only because both sections happened to
  be empty, and it survived the seventh, eighth and ninth close-outs -- one of which wrote "the two
  security cells are consistent with each other and with the gate", which was true and worthless.
- **Rule 36's own stamp read 1 and the value was 0.** The rule that exists to stop a recorded number
  rotting **has now rotted twice**: it read 5 for two runs after #265 took the figure to 1, then read
  1 for four close-outs after #278 took it to 0. Both times the cells the rule governs were correct.
- **Thirty-eight recorded numbers were wrong or stale, against 63 correct**, and eleven more have no
  command behind them at all. Three of the eleven sit in open items and would be quoted by whoever
  schedules the work.
- **Three new security findings, all on the anonymous read surface.** S-29 (MED, possibly HIGH):
  `GET /api/read/content/images/search` returns every image in the database with no
  collection-visibility or gallery-password filter, handing anonymous callers the CloudFront URL for
  images that `ContentDownloadControllerProd` exists to gate. S-30 (LOW):
  `GET /api/read/content/people` lists every row in `users`. S-31 (LOW): a share opt-in is checked
  when added and never again.
- **S-25 never existed.** The ledger runs S-1..S-24, S-26, S-27, S-28; the real closed count is 27,
  and the board recorded it three ways (27, 28, twenty-five) in three cells.
- **113 code references checked, 74 exact and 39 drifted** -- and the split is not random. **Every
  item whose last re-derivation was done by name came back clean; every item that wrote fresh line
  numbers came back drifted.** `DownloadResolution` (13 of 13), the migration integration tests (6 of
  6), MR 21 (24 of 24) and MR 16 #3 (5 of 5) were all re-derived by name and none moved. MR 25's
  `CollectionRequests.Update` recorded nine per-site line numbers at the eighth close-out and all nine
  moved within one run.
- **The frontend clone exists.** `~/Code/edens.zac` is on this machine with `origin/main` at
  `f4e8e25`. The board said otherwise in two places, and several rows were marked UNVERIFIED on that
  basis. Every cross-repo claim is now checked live.
- **Seven premises did not survive checking.** MR 18 #13 named two SQL-ordered producers as unsorted
  and two single-entity mappers as producers; item #22's "a PUT will not do" is false because both
  PUT routes already null-guard every field; MR 22's `@Value` row pointed at two classes that no
  longer exist; its fully-qualified-names blocker was spent when #282 shipped; `DownloadResolution`'s
  coverage guardrail is discharged by adjacent `.contentType()` assertions; `Synthetic.blogsOnly` had
  nothing left; and Appendix C's `Collectors.toMap` duplicate-key throw cannot occur, because
  `WHERE id IN (:ids)` returns one row per distinct id.
- **The board contradicted itself in eight places**, all fixed: a cross-repo section saying both "all
  five are filed" and "STILL NOT FILED"; three Progress rows describing work that had shipped; an
  open checkbox for a migration that landed five close-outs ago; a second open checkbox for two items
  already tracked below it; a bullet giving three different sizes for one test file; an
  `Optional.get()` bullet carrying two mutually exclusive counts with the wrong one first; a
  classification heading over an empty set; and `RoleRepository` recorded at 10 comments sixteen lines
  below the cell recording it at 0.

**The structural lesson, and it is not the one the third run found.** That review's lesson was that a
summary must be measurable by the command it quotes. This one is narrower and worse: **a gate command
is itself a recorded value, and it rots without ever having been right.** Lines 37, 49 and 50 were
never correct -- they did not drift, they were wrong the day they were written, and nothing could
detect that because the value they produced happened to match the truth. **A gate whose output never
changes is not evidence that nothing changed.** The fix that generalises: when you write a gate,
prove it can return non-zero. One line does it --
`printf -- '- [ ] **S-99 x\n' | grep -c '^- \[ \] \*\*S-'` must return 1. Rule 50 stated the general
form ("a checksum command is itself a recorded number and rots the same way") one run before this
review found three commands that had never worked.

**The second lesson, from the ref audit.** Re-deriving by name produces a name; re-deriving by number
produces a number that is wrong within a run. Four ref sets re-derived by name came back 100% clean;
the one that recorded per-site line numbers came back 0% clean. **Stop writing per-site line numbers
for files that churn.** MR 25's `CollectionRequests.Update` row now carries a derivation command
instead of nine numbers.

Full agent-by-agent detail, the eight slices' "checked and clean — do not redo" lists merged into one,
and the two places where slices disagreed are in
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-09-01-tenth-run).



## Full-board review, third run: tracker summary moved 2026-09-01

**Full-board review — RUN 2026-08-31 (third run)** *(tracker text, superseded)*

**It ran.** Five read-only agents, one apply agent, one docs MR, zero code changes — the guardrail
the second close-out wrote for it held. Slices: the two unchecked MR 25 arity counts; premise
re-verification across MR 16-19; the merged security set including #257 attacked as a set; the
frontend/backend pair; and board hygiene, counts and internal consistency.

**What it found, in one line each.**

- **The counts are clean.** All eleven recorded counts reproduce exactly — open bugs, open security,
  history open boxes, the rule-37 leading-`//` figure and its main/test split, trailing inline
  comments, the three rule-12 protected files, the worktree count. **Nothing moved.** This is the
  first fully clean count audit the board has had.
- **The security set refilled it**: S-26 HIGH, S-27 and S-28 LOW, and S-16's reachability claim
  verified sound a third time.
- **Both UNCHECKED MR 25 counts are CONFIRMED**, with a reproducible method now written into the item.
- **The cross-repo GIF row's premise was wrong**, and correcting it turned up a live backend N+1
  (MR 19 #21) plus four more frontend divergences.
- **Six MR 16-19 items were re-priced or corrected** — three cost re-prices, two ref drifts, one
  premise correction — and every other ref in that range re-verified exact.
- **The board was lying about itself in five places**, all now fixed: an "empty" security section
  holding four open boxes, "0 open PRs" with #252 open, four live debts with no checkbox, "9 open"
  config-rot findings that exist nowhere, and a classification heading covering a quarter of the
  board.

**The structural lesson, and it is the one worth carrying.** Every claim that had drifted was a
summary claim the cited gate could not measure. `grep -c '^- \[ \] \*\*S-'` genuinely returned 0
while four open questions sat in that section, because they did not open with `**S-`. **A summary
must be measurable by the command it quotes, or it will go wrong the moment something is filed in a
shape the command cannot see.** Two new gates came out of this: `\*\*U-` for the unsettled questions,
and the removal of the config-rot cell's open/closed split, which had no gate at all.

Full agent-by-agent detail, the "checked and clean — do not redo" list from the security slice, and
the reverse-direction endpoint scan are in
[history](2026-08-22-backend-cleanup-history.md#full-board-review--run-2026-08-31-third-run).


## Session log leak detector: reading moved 2026-09-01

**Session log** *(tracker text, superseded)*

One line per session -- honoured in spirit, not in width; a review pass gets a paragraph. Three
entries in a row ending `Next: X` means X is being avoided -- say so and either make it real work
or drop it. (Checked 2026-08-24: not currently tripped. Two entries ended `Next: MR 15 #6` and it
then shipped. **Re-checked 2026-09-01 at the tenth-run review, and the reading needs two
corrections.** First, **MR 18 #13's split and MR 19 #17 were named in the sixth, eighth and ninth
runs' `Next:` lines -- three of the last four, but not three consecutive**, because the seventh
close-out shipped no code and its `Next:` was the eighth run's list. The board's "carried forward
unchanged for the second time" counted only the consecutive pair and missed the sixth-run
appearance. Both are addressed now: MR 19 #17 is scheduled and MR 18 #13 is genuinely blocked on the
production collation. Second, **the detector has a blind spot: an item that stops appearing under
`Next:` looks resolved to it.** MR 18 #10 was named in the sixth run's `Next:`, vanished from every
one since, and has sat on the COLD list through four close-outs without being worked or
re-justified. It is named in the tenth close-out's carried-forward paragraph for that reason.
**Checked again 2026-09-01: TRIPPED, on U-1.** The sixth, seventh and eighth entries
all end saying U-1 is still blocked. It is not being avoided -- it needs host access nobody has had
on three consecutive runs -- but the detector is right that repeating it in `Next:` achieves
nothing. **Disposition: U-1 stops appearing under `Next:`.** It is a question for the user, it lives
in "Unsettled security questions" with U-7 and U-8 behind it, and it goes back into a run's `Next:`
only when the user says they have host access.) **Retention rule (stated 2026-08-29 --
the omission that caused the last lapse): the current session's entries stay here; every close-out
moves the rest to the history file's log archive in the same pass. A close-out MR that grows this
log without moving the older entries is the lapse signal.** The archive has two halves and both are
in the history file: the [pre-split log](2026-08-22-backend-cleanup-history.md#session-log) (entries
from 2026-08-22) and the [newer archive](2026-08-22-backend-cleanup-history.md#session-log-archive--entries-moved-2026-08-31)
(2026-08-30 onward). **Link both** -- the tracker pointed only at the older half for a session, and
the newest entries sat thousands of lines further down under a heading nothing linked to.
*(This paragraph is the section's standing note. It sat at the end of the sixth-run entry, between
two log entries, until 2026-09-01; it is now under the heading it governs.)*

## Session log, tenth-run close-out: full text moved 2026-09-02

### 2026-09-02 -- tenth-run close-out. Post-merge restamp, and a gate that went blind in a day

**Close-out only, no code.** [#299](https://github.com/themancalledzac/edens.zac.backend/pull/299)
merged as `19dacf24` after rebasing onto
[#300](https://github.com/themancalledzac/edens.zac.backend/pull/300).

**Rule 42 discharged on every branch-measured figure, and all of them held**: open checkboxes
**69**, open `S-` **3**, `U-` **5**, `Bug #` **0**, tracker **1,880**, history **9,736**, all re-run
on `main` at `19dacf24`. The review MR's real delta is **-208 tracker / +2,116 history** against its
rebase parent `71464517`, not the -197 / +2,088 it recorded against `43c6f2c6`.

**The `#NN` gate the tenth review wrote lasted less than a day.** It read
`grep -c '^- \[ \] \*\*#2'`, hardcoding the first digit, so #30 -- filed by #300 the same
afternoon -- was invisible from the moment it merged. Widened to `\*\*#[0-9]`, which returns
**3**. This is rule 36's failure inside the gate written to fix rule 36's failure, and it gives the
general form: **a gate must match the series it counts, not the members that existed when it was
written.**

**Two recorded facts went stale within a day of being measured.** "Zero open PRs" is now #301,
open and CONFLICTING -- and it conflicts *because* #299 rewrote the tracker under it. The refile
invalidated an in-flight branch, which is a cost of the two-tier split nobody had written down.
#301 also edits six `Collection*` files, so the next run's three items were chosen to avoid them.

**MR 18 #13 is chained to U-1, not independently blocked.** Checked rather than assumed: no
`COLLATE` in any migration, no `LC_COLLATE` or `POSTGRES_INITDB_ARGS` anywhere, so the ordering
takes the database default and the question needs host access.

**One claim of the review's own corrected, and one of mine.** Slice H's "members (a) and (d) have
zero `src/test` references" is right about (d) and loose about (a), whose `validate`/`redeem` carry
16. I first read `findLiveInvite` as a vanished symbol; it is the name to create, and the row says
so. Recorded because a close-out that only corrects the previous session is doing half the job.

**Next:** **#29** plus the two MR 14 stale docblocks as one docs MR; **MR 25's
`DownloadResolution.extension`**; **MR 19 #17 members (a) and (d)**. Three questions go in the
opening message: the production collation, S-29's severity, and the #294 page-size call.

## Session log, tenth-run entry: full text moved 2026-09-01

**2026-09-01 -- tenth run. The full-board review, three security findings, and two gate commands that never worked** *(tracker text, superseded)*

**The full-board review ran and it was the whole run.** Eight read-only slices, one apply agent, one
docs MR, **zero code changes** -- the guardrail every full-board review on this board has held to.
Three slices proposed code fixes; all three became board rows with their evidence and none was
implemented.

**The headline is not a stale number. It is three gate commands that were never right.** Lines 37, 49
and 50 recorded `grep -c '^- [ ] \*\*S-'` with the brackets unescaped. `[ ]` in a basic regular
expression is a bracket expression matching one space, so the recorded pattern needs `"- "` followed
by two spaces, and **it returns 0 against any input**. It was introduced by the sixth close-out and
survived the seventh, eighth and ninth -- the ninth's own log entry says "rule 36's two security cells
are consistent with each other and with the gate (0 open)", which was true, because the gate cannot
disagree with anything. All three now carry the escaped form. **Rule 36's own stamp read `1` while the
value was `0`**, its second rot: it read 5 for two runs after #265, then 1 for four close-outs after
#278. The rule that exists to stop a recorded number rotting has now rotted twice while the cells it
governs were fine both times.

**Three security findings, all on the anonymous public read surface, which no prior pass had
attacked.** Every one of the twenty-seven closed S- findings lives in auth, session, role-membership,
share or actuator code. **S-29 (MED, possibly HIGH):** `GET /api/read/content/images/search` joins
`content` to `content_image` and nothing else -- no `collection_content` join, no
`collection.visibility` predicate, no `gallery_password` predicate -- so an anonymous caller gets
every image in the database with its unsigned CloudFront URL, including images whose only home is a
password-protected client gallery. It walks around the same gate `ContentDownloadControllerProd`
exists to enforce. **S-30 (LOW):** `GET /api/read/content/people` is
`SELECT id, name, created_at FROM users ORDER BY name ASC` with no predicate, so since V35 merged
people and accounts it returns the whole account roster by name and id -- and those ids are S-29's
`personIds` filter. **S-31 (LOW):** a share opt-in is gated on `canView` when it is added and never
re-checked, so a revoked role grant leaves the collection's tile on the link. **S-25 never existed**;
the ledger is 27 lines running S-1..S-24, S-26, S-27, S-28, which is the gap behind the "28 findings"
three cells disagreed about.

**Thirty-eight recorded numbers were wrong or stale against 63 correct, and eleven more have no
command behind them.** The largest single correction was the whole suite total (1,505, not 1,502,
measured by running it). The most-repeated failure was unchanged from the ninth close-out: a
`file:line` or a `wc -l` sitting outside the neighbourhood of anything that merged, which the scoped
sweep structurally cannot reach.

**113 code references checked, 74 exact and 39 drifted, and the split is not random.** Every item
whose last re-derivation was done **by name** came back clean -- `DownloadResolution` 13 of 13, the
four typeless migration ITs plus V54 6 of 6, MR 21's whole inventory 24 of 24, MR 16 #3 5 of 5. The
one item that recorded per-site line numbers came back 0 of 9: MR 25's `CollectionRequests.Update`
had all nine `CollectionServiceTest` refs move -43 to -46 within one run, because
[#296](https://github.com/themancalledzac/edens.zac.backend/pull/296) -- the ninth run's own MR --
cut 46 lines out of that file after they were written, and the close-out then stamped the row "it
HOLDS EXACTLY" about the counts without re-running the refs. **That row now carries a derivation
command instead of nine numbers.**

**Seven premises did not survive checking.** MR 18 #13 named `MetadataService` and
`SyntheticCollectionResolver` as unsorted producers; both order in SQL, and the two methods it named
are single-entity mappers that cannot sort. Item #22 said a `PUT` "will not do"; both PUT routes
already null-guard every field, so `{id, title}` updates title alone. MR 22's `@Value` row pointed at
`S3Config` and `SesConfig`, which MR 16 #4 deleted. Its fully-qualified-names blocker was spent when
#282 shipped. `DownloadResolution`'s coverage guardrail is discharged by a `.contentType()` assertion
sitting on the adjacent line of every test it names. `Synthetic.blogsOnly` had nothing left and the
row is deleted. And Appendix C's `Collectors.toMap` duplicate-key throw **cannot occur** -- `WHERE id
IN (:ids)` returns one row per distinct id, so the map never sees a duplicate key; the real finding is
a silent last-write-wins.

**The frontend clone is on this machine and the board said it was not, in two places.** `~/Code/edens.zac`,
`origin/main` at `f4e8e25`. Every cross-repo row was re-verified live for the first time.
**BE-2 was answered: drop the array.** The location endpoint stops serving the orphan `images` array
and the frontend stays on `searchImages({ locationId })` -- **which closes FE-1 by closing it, not by
doing frontend work**, because that page never read the field. The removal is filed as a COLD backend
item with its measured cost: roughly 7 of ~15 SQL queries per location page load and 30-60 KB of
JSON, paid per ISR revalidation rather than per visitor. The GIF visibility that option 1 would have
bought is cheaper as GIF support in `searchImages`, filed alongside it. **One cross-repo debt closed
and one filed:** MR 19 #19's widened response needs nothing (both consumers read exactly the keys
`PagedResponse` pins), and #294's 30 -> 50 page-size default is real -- two public pages pass no
`size` and silently show 67% more photos.

**The structural lesson, and it is worse than the third run's.** That review taught that a summary
must be measurable by the command it quotes. This one: **a gate command is itself a recorded value,
and it can rot without ever having been right.** Lines 37, 49 and 50 did not drift. They were wrong
the day they were written, and nothing could detect that because their output happened to match the
truth. **A gate whose output never changes is not evidence that nothing changed.** When you write a
gate, prove it can return non-zero:
`printf -- '- [ ] **S-99 x\n' | grep -c '^- \[ \] \*\*S-'` must return 1. Rule 50 stated the general
form one run before this review found three commands that had never worked.

**Filed:** S-29, S-30, S-31; the `RoleRepository.canView`/`isClient` deletion, which had been written
down since 2026-08-25 and lived only inside a ticked bullet where no gate could see it; the #294
page-size debt; the location `images` removal; and the `searchImages` GIF widening. **Closed:** MR 16
#3 as decided, BE-2 as answered, FE-1 as won't-do, MR 19 #19's debt as verified-no-action, the V19
`cover_image_id` box that had been open five close-outs after it shipped, and the four-main-dead-members
umbrella that was a second checkbox for two rows below it. **Deleted:** `Synthetic.blogsOnly`,
Appendix D, `AdminHomeService`'s cache row (demoted to a note -- a checkbox with no action can never
be ticked), and two paragraphs of superseded `Optional.get()` arithmetic.

**Next:** **#29** and the two MR 14 stale docblocks as one docs MR; **MR 19 #17 members (a) and (d)**;
**MR 25's `DownloadResolution.extension`**, now unparked. **Three questions at the top of the session,
all cheap:** the production Postgres collation (it decides whether MR 18 #13 closes or is a ~10-line
MR), how severe S-29 actually is, and whether the frontend wants 50 images on `/location/[slug]` and
`/tag/[slug]`.


## U-1: the profile question chain, moved 2026-09-01

- [ ] **U-1 -- whether prod actually runs the `prod` profile.** The deployment docs contradict each other:
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

  **Narrowed 2026-08-31 (fourth run), and the framing was wrong.** The contradiction is not two docs
  disagreeing about a deployment. It is **two competing copies of the same `.env` template**, and
  the count is **2-0 for `prod`** once you include the operational one -- **corrected 2026-09-01: the
  two `ai_ec2.md` blocks that said `default` were deleted by
  [#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), which this same bullet
  records twenty-seven lines below.** `ai_ec2.md` now carries one `SPRING_PROFILES_ACTIVE` mention
  (`:198`) and it names no value. The table's other two rows verify exactly:

  | Source | Says | What it is |
  |---|---|---|
  | `.env.example:3` | `prod` | the template the deployed `.env` is built from -- the file `docker-compose.yml` actually reads. Carries the comment "Use `dev` for local development, `prod` on EC2/production" |
  | `ai_docs/ai_deployment_strategy.md:289` | `prod` | prose |

  `docker-compose.yml:21` is `SPRING_PROFILES_ACTIVE: "${SPRING_PROFILES_ACTIVE:?must be set: dev,
  prod, or default}"`, so the value is required and cannot silently default; whatever the host's
  `.env` says is what runs.

  **This splits the item in two, and one half is now COLD work rather than a question.**
  `ai_ec2.md` carrying a stale duplicate of the env template is a doc bug fixable today, on the
  project's own rule that a doc describing something that is no longer true gets its wrong version
  deleted rather than annotated. The right fix is for `ai_ec2.md` to stop carrying a second copy at
  all and point at `.env.example`. **Filed as #23.**

  What stays blocked is only the proof: reading `SPRING_PROFILES_ACTIVE` on the live host, or hitting
  the origin without `X-Internal-Secret` and confirming a 403. **BLOCKED on the user** -- it needs
  host access, and the confirming probe is a request against production that should be run or
  authorized by its owner, not fired off by a close-out.

  **ASKED 2026-08-31 (fifth run), before any code, and the answer was that it cannot be checked right
  now.** So it stays BLOCKED, and **U-7 and U-8 stay blocked behind it** -- the twelve-name actuator
  exclude list at `application.properties:67` was not touched. Its docs half shipped the same day as
  [#269](https://github.com/themancalledzac/edens.zac.backend/pull/269), which deletes the stale
  `.env` copy and explicitly does not claim to settle this. **The question is now on the record as
  put and unanswered rather than unasked**, which is the difference between a blocked item and a
  neglected one. Ask it again at the top of the next run; it is still one command and it still gates
  three items.

## U-4, U-5, U-6 and the canView confirmation: bodies moved 2026-09-01

- [x] **U-4 -- `SessionService.resolve` slid the session window before reading status** — **DONE** ([#270](https://github.com/themancalledzac/edens.zac.backend/pull/270), 2026-08-31). The slide block moved below `mayHoldSession`; `mayHoldSession` and the absolute-ceiling cap untouched. **The item was right that the move costs nothing and did not price that no existing test could catch the bug** — `resolveRejectsSessionWhoseAccountWasDisabled` asserted `expiresAt` is in the future, which is true under the bug too. One new test, mutation-proved (15 run, 1 failure, at the guard). [Write-up](2026-08-22-backend-cleanup-history.md#u-4--the-slide-moved-below-the-status-check-270).
- [x] **`RoleRepository.canView` and `isClient` have zero `src/main` callers -- confirmed 2026-08-25**, after S-6 routed
  everything through `effectiveLevel`. They are the bug S-6 fixed, still sitting in the DAO under the
  right names and still green in tests. Wave 1 deletion candidates, and the names are the hazard.
  **The confirmation was done; the deletion it recommends had no checkbox anywhere on the board until
  2026-09-01. It is now the row below.**

## U-7 and U-8: the actuator derivation, moved 2026-09-01

- [ ] **U-7 -- whether to delete the now-redundant actuator exclude list.**
  *(Promoted 2026-08-31 out of S-23's closed ledger line.)* S-23 shipped the resolved-include boot
  check, which refuses a prod boot on any include wider than `health`. The name-based exclude list it
  replaced was left untouched and is now redundant. Deleting it is a disposition nobody has made.

  **ANSWERED 2026-08-31 (fourth run) by reading `ProdActuatorExposureGuard`, and the answer is
  conditional in a way neither U-7 nor U-8 recorded.** The guard requires the resolved include to
  equal exactly `{health}` and throws from `@PostConstruct` otherwise, so under the `prod` profile
  nothing but `health` can be exposed and every one of the twelve names in the exclude list is
  unreachable by construction. Redundant: **yes**.

  **But the guard is `@Profile("prod")`, and whether prod runs the prod profile is U-1.** On a host
  named `default` the guard does not exist, the include is still `health` from
  `application.properties:66` -- but nothing enforces it, and the exclude at `:67` is then the only
  thing standing between a widened include and an exposed endpoint. So the exclude list is redundant
  *exactly where the guard runs*, which is the thing U-1 cannot prove.

  **U-1 is therefore a hard dependency of U-7 and U-8, and the board had all three filed as
  independent questions.** Do not delete the exclude list until U-1 is settled: deleting it is safe
  under `prod` and removes the last line of defence under `default`. That is the whole disposition,
  and it is now a decision with a stated precondition rather than an open question.
- [ ] **U-8 -- whether S-18's criterion-incompleteness is now moot.**
  *(Promoted 2026-08-31 out of S-18's closed ledger line.)* S-18's exclude list still does not name
  `metrics` or `info`, both of which meet its own stated criterion under `include=*`, and both of its
  tests derive from the same enumeration so neither can see the gap. S-23 was named as the fix shape
  and has shipped. Whether that closes this residual or only hides it behind a boot check has not
  been recorded. Answer it, then close U-7 and U-8 together.

  **ANSWERED 2026-08-31 (fourth run), same reading as U-7 and with the same precondition.** It is
  moot **under `prod`**: `ProdActuatorExposureGuard` refuses any resolved include other than exactly
  `{health}`, so `metrics` and `info` cannot be exposed no matter what the exclude list omits, and
  the criterion-incompleteness has nothing left to bite. The distinction the item asked for --
  "closes the residual" versus "hides it behind a boot check" -- resolves to **closes it**, because
  the guard is fail-closed at startup rather than a filter something can slip past.

  Under `default` it is not moot at all, and `metrics` and `info` remain exactly as unnamed as S-18
  left them. **So U-8 has the same dependency on U-1 as U-7**, for the same reason, and the two do
  close together as the board predicted -- just not before U-1 does.


## `0257-backend-security-bugs`: verification moved 2026-09-01

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

## `fix/s18-actuator-exclude`: original text moved 2026-09-01

- [x] **SETTLED 2026-08-30 -- safe to delete; it now holds nothing unique.** R-1 landed its two
  stranded commits on `main` via [#238](https://github.com/themancalledzac/edens.zac.backend/pull/238),
  and `44a9d81`'s content was already there via #232's squash. Verified by content, not by commit
  count: `git diff origin/main origin/fix/s18-actuator-exclude -- <the two files it touched>` shows
  the actuator block byte-identical, and `ActuatorExposureTest.java` does not appear in the diff at
  all. **Trap worth keeping:** `git log origin/main..origin/fix/s18-actuator-exclude --oneline`
  still returns **3**, and always will -- a squash merge never makes a branch commit an ancestor of
  `main`, so that command can never reach 0 and is the wrong test for "is this branch safe to
  delete". Diff the content of the files it touched instead. Original text:
  *(superseded, demoted from an open checkbox 2026-08-31 -- it was a `- [ ] ` for an item settled
  directly above it, and it counted toward the file's open-box total.)* **`fix/s18-actuator-exclude`
  (added 2026-08-28; corrected 2026-08-29).**
  `git log origin/main..origin/fix/s18-actuator-exclude --oneline` returns **three** commits, not
  the two first recorded: `44a9d81` (the S-18 fix itself — its *content* is on `main` via squash
  `d6ff6a8`, so the SHA is unreachable by construction), plus `d42d24d`/`665bd7d`, the rule-37
  sweep pushed after #232 merged — those two are content-stranded. `git apply --check` passes
  clean for both diffs today. See R-1; do not merge the branch.


## Appendix C: resolved leads, moved 2026-09-01

- [x] Possibly-dead endpoints -- **all three confirmed ALIVE 2026-08-24 by grepping the frontend.
  Do not delete any of them.** `GET /api/admin/collections/metadata` has 5 call sites
  (`app/lib/api/collections.ts` into the explore page, collections panel, collection-edit hook and
  two admin pages). Both role-membership pairs are live, driving two different screens (see MR 17
  #8). Ids-only `GET /api/read/user/saves` is called from `app/lib/api/personal.ts` into
  `CollectionPageWrapper`, and the frontend types it as a bare `number[]`.
- [x] `role.kind` -- **premise disproved 2026-08-24**, V45 also writes `'PERSONAL'`. Full correction under "Decisions needed"; it may be carrying provenance.
- [x] `PersonRepository.findAccountUserIdsByIds` -- **resolved in MR 15 #6.** It had zero callers in main and test, so the only-accounts-get-grants rule was documented and unenforced. The method was deleted and the rule enforced at `RoleRepository.addMember` instead. Low severity: admin-only endpoints, and a PERSON row cannot log in; the risk was a dormant grant surviving an upgrade to an account.
- [x] `collection.rows_wide` -- **premise FALSE, confirmed 2026-08-24. The frontend DOES read it**: `CollectionPageWrapper` uses `collection.rowsWide ?? LAYOUT.defaultChunkSize` as the row-packer chunk size, so dropping the column changes public rendering. Struck as a lead.

## Bug #18: tracker body moved 2026-09-01

- [x] **Bug #18 (low-medium) — `updateLocation` misses the slug-uniqueness check the create path
  has, so a slug collision dies at the DB with a misleading generic 409.** *(Filed 2026-08-29 from
  the frontend board's E16 archive (`docs/spikes/2026-summer-refactor/group-e-consolidations.md` in
  `edens.zac`), which found it in passing -- "That is a backend 500, and it belongs on the backend's
  board, not this one" -- and it was never filed here until now.)* Verified in source:
  `MetadataService.updateLocation` checks `findByLocationNameIgnoreCase` only, then writes
  `SlugUtil.generateSlug(locationName)`; V8 has `CREATE UNIQUE INDEX idx_location_slug`; the
  find-or-create path in `LocationRepository` consults `findBySlug` first and the admin update path
  does not. Two distinct names that slugify identically ("St. Moritz" / "St Moritz") pass the name
  check and hit the unique index inside `LocationRepository.save`. **One premise correction to the
  archive's account: this is not a 500 today.** `LocationRepository` is a JdbcTemplate DAO, so the
  violation surfaces as `DataIntegrityViolationException`, which `GlobalExceptionHandler` maps to a
  generic **409** ("Data integrity violation: duplicate or invalid data") that never mentions the
  slug. Fix shape: mirror the create path's slug check before the save and return the same conflict
  shape the name check uses. **COLD.** **DONE** ([#276](https://github.com/themancalledzac/edens.zac.backend/pull/276), 2026-08-31, sixth run). Shipped as specified. **The item did not price its own payoff**: the caller-visible 409 is byte-identical before and after, because `GlobalExceptionHandler.handleDataIntegrity` discards the message. **This closed the last open bug on the board.** [Write-up](2026-08-22-backend-cleanup-history.md#bug-18--the-slug-check-and-an-item-that-did-not-price-its-own-payoff-276).

## MR 17 #7: tracker body moved 2026-09-01

- [x] #7. Admin image list duplicates the prod image search — same 12 `@RequestParam`s, same service call, different response wrapper (`AdminController.getAllImages` (**`258-294` as of #218**) vs `ContentControllerProd.searchImages` (**declaration at `46` as of 2026-08-31, drifted +1 from `45`**; find by name)). Bind the filter once with a shared `@ModelAttribute` record, reuse prod's constraints, return one response type. **"Reuse prod's constraints" is an unpriced behavior change**: admin clamps with `Math.min(Math.max(size, 1), 200)` while prod validates with `@Min/@Max`, so admin `size=500` goes from silently returning 200 rows to a 400; defaults also differ (50 vs 30), and two frontend pages that pass no `size` would jump from 30 images to 50. **Do MR 19 #19 first** -- it is the same decision from the other direction, and #7 then shrinks to sharing the filter record. Realistic ~70 with test -- **re-verified 2026-08-31**, including the size handling: admin is `defaultValue = "50"` then `Math.min(Math.max(size, 1), 200)` (`AdminController:271`), prod is `defaultValue = "30"` with `@Min(1) @Max(200)` (`ContentControllerProd:61`), so `size=500` returns 200 rows on admin and 400 on prod. The **do MR 19 #19 first** ordering also re-confirmed: admin already returns `ResponseEntity<PagedResponse<ContentModels.Image>>` and re-wraps at 286-291, and that whole block disappears once #19 lands.

  **UNBLOCKED 2026-08-31 (seventh close-out). MR 19 #19 landed as [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) and the
  half of this item that depended on it is now done in `main`**, verified rather than assumed:
  `AdminController.getAllImages` (**declaration `256`**, was `258`) ends in a single
  `return ResponseEntity.ok(contentService.searchImages(request));` -- the 286-291 re-wrap block is gone.
  `ContentControllerProd.searchImages` is at **`47`** (drifted +1 from `46`). Both now return the
  identical `ResponseEntity<PagedResponse<ContentModels.Image>>`. **The "same 12 `@RequestParam`s" claim
  re-measured exact: 12 on each.**

  **What is left splits into a COLD half and a decision, and they should not ride together:**

  - **COLD:** bind the filter once with a shared `@ModelAttribute` record and have both endpoints take it.
    Pure de-duplication, no behaviour change, and it is the whole of what this item is now worth.
  - **A user decision, still unpriced and still not asked:** whether to unify the size handling.
    Re-verified exact at this close-out -- admin is `defaultValue = "50"` then
    `Math.min(Math.max(size, 1), 200)` (**`AdminController:270-271`**), prod is `defaultValue = "30"` with
    `@Min(1) @Max(200)` (**`ContentControllerProd:61`**). Unifying means admin `size=500` stops silently
    returning 200 rows and starts returning a 400, and the two frontend pages that pass no `size` jump
    from 30 images to 50. **That is a product call, not a refactor.**

  **So the guardrail on this item is: do the shared filter record and leave both size behaviours exactly
  as they are.** If the unification looks tempting mid-change, report what it would cost and stop.
  Realistic size drops from the recorded ~70-with-test to roughly **~40**, because the response-wrapper
  work is already banked in #283.

  **DONE** ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290), 2026-09-01, eighth
  run, `+246/-58` across 5 files). Shipped `ImageSearchFilter`, a record of the ten filter parameters
  with a `toRequest(page, size)` factory; both endpoint methods went 13 parameters to 3 and the
  12-argument `ImageSearchRequest` construction is gone from both. **Paging deliberately stayed out of
  the record**, and the guardrail held -- both size behaviours are untouched. Nine new tests freeze
  them, and there were zero before.
  **It found a hole in what the whole suite proves, and that is the run's biggest finding**: prod's
  `@Min`/`@Max` on `size` were never enforced in `ContentControllerProdTest`, because `standaloneSetup`
  builds MockMvc against the raw controller and the `@Validated` proxy does not exist. #290 fixes it
  for that one controller; **every other constraint-annotated controller parameter in the repo has the
  same gap, now filed as [#27](2026-08-22-backend-cleanup-spike.md#mr-26--coverage-gaps).**
  **This item's own premise about the size unification is backwards and needs the user**, now filed as
  [#28](2026-08-22-backend-cleanup-spike.md#decisions-needed-from-the-user): adopting prod's constraints moves admin's default **down**,
  50 -> 30, not "from 30 to 50" as the paragraphs above say twice.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-17-7--the-filter-record-and-the-constraints-that-were-never-enforced-290).

## MR 17 #8: tracker body moved 2026-09-01

- [x] #8. Role membership is writable from two endpoint pairs backed by the same repository calls (`PUT`/`DELETE /api/admin/users/{id}/roles/{roleId}` in `AdminUserController` -- `addUserToRole` / `removeUserFromRole` -- **declarations at `388` and `401` as of #257** (were 382/395; #257 added the passkey endpoints and a constructor parameter above them, +6. Before that the old 383/396 pointed at each method's first body line, not its declaration). Find by name -- vs `PUT`/`DELETE /api/admin/roles/{roleId}/members/{userId}` in `AdminRoleController:149-166` -- `addMember` / `removeMember`). Keep the roles-side pair. **Blocker resolved 2026-08-24: the frontend uses BOTH**, driving two different screens (`RoleDetailView.tsx` calls the roles-side route, `UserRolesSection.tsx` the users-side). So this is a coordinated cross-repo change with deploy ordering, not a backend delete -- cheapest path is making the users-side method delegate to the roles-side one, leaving components untouched. **PR #191 lowered its priority**: both pairs now route through the guarded `RoleRepository.addMember`, so this is tidiness, not security. Scope must also include that method's docblock, which says "the two admin endpoints that reach here". **All four refs re-verified exact 2026-08-31 (third run)**: `addUserToRole` 388, `removeUserFromRole` 401, `AdminRoleController.addMember` 150, `.removeMember` 163 -- both inside the recorded `149-166`. **The docblock is at `RoleRepository.java:138-149` and `grep "two admin endpoints" ` returns nothing** because the phrase is line-wrapped between "two" and "admin". Find it by reading the docblock, not by grep.

  **DONE** ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285), 2026-08-31, sixth run). Users-side pair delegates to the roles-side pair; all four routes stay live. **Shape worth a reviewer's eye**: `AdminUserController` now injects `AdminRoleController`. [Write-up](2026-08-22-backend-cleanup-history.md#mr-17-8--delegation-with-a-shape-worth-a-second-look-285). *(This outcome paragraph sat under the `## MR 18 — Services` heading, not with its own item, from the sixth close-out until 2026-09-01.)*

## MR 18 #9: tracker body moved 2026-09-01

- [x] #9. The from-disk and ingest background loops are ~70 lines of copy-paste (`processFilesFromDiskLoop` vs `ingestFilesGroupedByDayLoop` -- **find them by name; the recorded `331` and `459` drifted to `442` and `478`, so a closed row was quoting its refs as current** (ranges were 316-420 and 444-555) -- the largest drift on the board, ~38 lines each), including a CREATE/UPDATE switch the ingest loop already merged. One shared loop with a `(fileEntry, prepared) -> collectionId` resolver. **Three copies, not two** -- the CREATE/UPDATE arms inside `processFilesFromDiskLoop` are a third. Net deletion ~110, better than the stated ~85, and all source: **zero forced test churn** -- re-confirmed 2026-08-31, neither loop is named in any test file. **Both declarations re-verified exact 2026-08-31 (331 and 459); no drift since #255.** Confirming context: the ingest loop's own docblock at 449 says "Same shape as {@link #processFilesFromDiskLoop}", so the duplication is documented in the source.

  **DONE** ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279), 2026-08-31, sixth run). One `runUploadLoop(request, job, CollectionResolver)`. **Estimated ~110 net deleted, actual -51** -- taught **working rule 48**. Found the `takeOrderIndex` coverage hole, closed by #284. [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-9--the-shared-upload-loop-at-half-the-advertised-saving-279).

## MR 18 #11: tracker body moved 2026-09-01

- [x] #11. Four near-identical BFS walks: `RoleGrantPropagationService.java:168-223` (three) plus `CollectionService` `validateNoLinkCycle`/`parentIdsOf` (**re-derived 2026-08-31, seventh close-out: `validateNoLinkCycle` at `510`, `parentIdsOf` at `536`, the walk at `517-531`** -- each +2 from the `508`/`534` recorded at the sixth close-out, shifted by [#280](https://github.com/themancalledzac/edens.zac.backend/pull/280) and [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284); find them by name). One `walk(root, neighborsFn)` helper. **Five walks, not four** -- `propagateToVisibleSubtree` is a fifth the line range missed, and it sits at **127**, above the recorded `168-223` range. **Re-derived 2026-08-31 (third run) by reading the bodies, not the names** -- every one is `Set<Long> visited` + `Deque<Long> pending = new ArrayDeque<>(...)` + `while` + `if (!visited.add(current))`. Declarations: `propagateToVisibleSubtree` **127**, `ancestorsOf` **168**, `subtreeOf` **188**, `visiblyLinkedAncestorsOf` **207**, `CollectionService.validateNoLinkCycle` **508** (walk at **513-530**) with `parentIdsOf` at **534** (all +40 from #266; were 468 / 473-489 / 494, and **the stale 468 and 494 both land on blank lines**, so those two fail visibly). `rematerializeSubtreeFromAncestors` (155) is **not** a walk -- it calls `ancestorsOf`. ~95 lines, zero test churn, pinned by 33 integration tests, none of which names any of the five private methods. **Best value in MR 18.**

  **DONE** ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288), 2026-09-01, eighth
  run, `+80/-80` across 3 files). All five walks unified onto
  `CollectionGraphUtil.walk(root, neighborsFn, visitor)` -- package-private static, private
  constructor, in `services` beside SlugUtil, PaginationUtil and the rest.
  **The "~95 lines" estimate was wrong and the correction generalises: actual is +80/-80, net ZERO.**
  The ~95 counted raw deletions and forgot the 47-line new file the extracted code lands in. The win
  is one loop instead of five, not fewer lines. This is now **working rule 48**, and it applies to
  every remaining "extract a shared helper" item on this board.
  **"Zero test churn" and "33 integration tests" were both exactly right** -- the first estimate on
  this board in a while to need no correction at all. Do not re-derive either.
  The two `parentIdsOf` copies are byte-identical and were left alone deliberately: deduping them
  would force the helper to hold a `CollectionRepository`, turning a stateless util into a
  dependency-holding one.
  [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-11--five-walks-one-visitor-and-an-estimate-that-forgot-the-new-file-288).

## MR 18 #12: tracker body moved 2026-09-01

- [x] #12. `nextOrderIndex` logic. **"Five places" is PREMISE-CORRECTED 2026-08-31 (third run) -- it mixed two different units, which is working rule 14's exact failure.** There are **three places that compute the index**, all the same two lines over `collectionRepository.getMaxOrderIndexForCollection`: `ContentService.nextOrderIndex` (declaration **467**, body 468-469), `ContentMutationUtil.nextOrderIndex` (declaration **170**, body 171-172, private), and `TagService:124-125` inline and not extracted. The other "places" are **call sites that already delegate**, not copies: `ImageUploadPipelineService` calls `contentService.nextOrderIndex` at 357, 527, 546 and 760. So it is **three copies and four delegating call sites**. There is exactly one `MAX(order_index)` SQL in the repo, at `CollectionRepository:867`. `TagService` also differs -- it seeds a counter it then post-increments (`.orderIndex(orderIndex++)` at 131), so a shared helper replaces its first line only, not its loop. **Yield is ~4 source lines across three files**, not what "five places" suggests. The delegate instruction and its stub count are exact: `ImageUploadPipelineServiceTest` has **15** `when(contentService.nextOrderIndex(...))` stubs (144, 199, 351, 387, 416, 443, 498, 533, 576, 659, 683, 726, 777, 822, 950). **Do it the delegate way or not at all.**

  **DONE** ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284), 2026-08-31, sixth run). **There are five real copies**, so "five places" was accidentally right about the number and wrong about which things it counted -- working rule 14 twice on one item. Shipped onto `CollectionRepository.getNextOrderIndexForCollection`, a direction neither prescribed option named. The two `CollectionService` copies were cut deliberately and became **#12b**, closed 2026-09-01. [Write-up](2026-08-22-backend-cleanup-history.md#mr-18-12--the-item-was-wrong-twice-in-opposite-directions-284).

## CollectionRepositoryTest's 21 comments: tracker body moved 2026-09-01

- [x] **`CollectionRepositoryTest`'s 21 inline comments and `CollectionRepository`'s 12.** **DONE**
  ([#295](https://github.com/themancalledzac/edens.zac.backend/pull/295), 2026-09-01, ninth run,
  `+23/-37`, two files, no logic changed, suite 1502 at both ends). 21 -> 0 and 12 -> 0.
  Twelve of the repository's were four three-line `====` banners, folded into one class-docblock
  sentence naming the file's four-part layout; twelve of the test's were bare Arrange/Act/Assert
  markers and were straight deletes; the other nine carried content and went into docblocks on the
  thing each described. **Rule 46 did not bite** -- both files held zero trailing `code; //`, so the
  whole-line count and the comments-deleted count were the same number, and this is the first
  close-out in five where a comment MR's two metrics agreed. **Taught working rule 50.**
  [Write-up](2026-08-22-backend-cleanup-history.md#collectionrepository-comment-concentration-295).

  **The `/* collections */` rider below is NOT closed by this** -- it lives in `CollectionServiceTest`,
  which this item no longer covers, and it still waits on the `CollectionRequests.Update` item.
  Confirmed still present 2026-09-01 while shipping [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296).

  **Rider, not its own row.** `CollectionServiceTest.updateWithSiblings` keeps two `/* collections */`
  and `/* siblings */` positional argument labels on the 17-arg `CollectionRequests.Update` call. They
  are block comments, not the `//` form rule 37 counts, and removing them makes the call harder to
  read. They should disappear when the
  [`CollectionRequests.Update` item](2026-08-22-backend-cleanup-spike.md#positional-constructors-that-block-the-testfixtures-pass)
  shortens that constructor.

## #12b: tracker body moved 2026-09-01

- [x] **#12b. The two `nextOrderIndex` copies [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284) deliberately left in `CollectionService`.**
  *(Filed 2026-08-31, sixth run, out of MR 18 #12's implementation.)* #284 collapsed three copies onto
  `CollectionRepository.getNextOrderIndexForCollection`. Two more compute the same rule inline and were
  cut from that PR with reasons: `CollectionService.linkCollectionToParent` and the child-collection add path -- **exact as of the
  seventh close-out, re-derived from the two `getMaxOrderIndexForCollection` call sites rather than from
  the method declarations**: `linkCollectionToParent` is declared at **`416`** and its copy of the rule
  sits at **`446`**; the child-collection copy sits at **`1138`**. *(The sixth run recorded these as
  `~444` and `~1141`, which were the call sites approximated, not the declarations -- `444` was within two
  lines of the real call site and `~1141` within three.)* **Find both by name**, this file moves
  constantly. Neither
  path has a test pinning its order index, the second sits inside a large update flow that is
  expensive to cover, and writing that coverage in a contended 1,400-line file would have blown the
  MR's scope. **The blocker is coverage, not design**: the destination method already exists and
  `CollectionService` already injects `CollectionRepository`, so the change itself is two one-line
  delegations. Cost is the two tests, not the two lines. **Consequence already on `main`**:
  `getMaxOrderIndexForCollection` stays public rather than becoming private, because these two still
  call it. **COLD.**

  **DONE** ([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291), 2026-09-01, eighth
  run, `+113/-8` across 3 files). Both copies delegate to
  `CollectionRepository.getNextOrderIndexForCollection`. All three refs were re-derived by name and
  **none had drifted** -- `linkCollectionToParent` 416, its copy 446, the child-collection copy 1138,
  exactly as the seventh close-out recorded.
  **The consequence this item recorded is now closed**: `getMaxOrderIndexForCollection` is
  **private**. These two were its last callers outside `CollectionRepository`, no test mocked it, and
  its `@Transactional(readOnly = true)` was dropped -- the public wrapper carries it and Spring's
  proxy never applied it to a self-invocation anyway.
  **The "expensive to cover" warning did not hold, and the correction generalises.**
  `CollectionLinkSecurityIntegrationTest` already drives both writers against real Postgres and
  already has a `linkViaStructureTab` helper sending exactly what the admin Structure tab sends. The
  three new order-index tests are the same shape as tests already in that file, in a new
  `CollectionLinkOrderIndexIntegrationTest`; `CollectionServiceTest` was never touched. This is now
  **working rule 49** -- the board has mis-priced coverage cost this way twice.
  Both delegations were **mutation-proved**, each broken by passing `childCollectionId` instead of
  `parentId`, and only the matching test failed. Watched failing, not assumed.
  **Behaviour change worth flagging**: the child-collection path now runs the `MAX(order_index)` query
  only when the request omits an `orderIndex`. It previously ran it unconditionally and discarded the
  result. Same value written, one fewer query per explicitly-positioned child -- the same shape #284
  gave the ternary in `ContentMutationUtil`.
  [Write-up](2026-08-22-backend-cleanup-history.md#12b--the-last-two-nextorderindex-copies-and-a-coverage-price-that-was-wrong-291).

## MR 19 #15: tracker body moved 2026-09-01

- [x] #15. `getUpdateCollectionData` fetches the collection row twice (`CollectionService.getUpdateCollectionData`, **declaration at `888` as of #266 -- was `848`, and #266's `batchConvertOrphans` shifted everything past old-271 by +40; find it by name**). The double fetch is confirmed: **`891-895` as of #266** calls the service's own `findBySlug` (**declared at `348`**, returns `CollectionModel`), and **`908-912`** calls `collectionRepository.findBySlug` (returns `CollectionEntity`). *(All four were `851-855` / `308` / `868-873` before #266. **The stale numbers all resolved to real repository calls in two different paginated finders**, so following them would have looked like confirming the double fetch while reading unrelated methods -- re-derive by name.)* Both throw the same `ResourceNotFoundException` with the same message.

  **ANSWERED 2026-08-31 (fifth run) by reading the converter, and the answer inverts the item.** The question was whether `findBySlug`'s converter populates password and recipient emails or deliberately strips them. **It never populates them.** `CollectionProcessingUtil.convertToFullModel` -> `convertToModel` -> the shared base sets only `model.setIsPasswordProtected(entity.getGalleryPassword() != null)` (**`CollectionProcessingUtil:186`**); there is no `setGalleryPassword` or `setRecipientEmails` on a `CollectionModel` anywhere in `src/main`. The second entity fetch exists because **`CollectionService:931-932`** copies those two fields off the entity onto the model itself.

  **So this is not a double fetch and it is not a de-duplication.** The two fetches return different data on purpose. Deleting the second returns a null gallery password and empty recipients; widening the converter to avoid that leaks the gallery password onto every read path sharing it, which is exactly the risk the item flagged and could not price. **The fix shape is a two-column projection for this one caller** -- a smaller, different change than the deletion the item describes, and it should be re-titled before it is worked. Working rule 21 again: correct premise, prescribed fix that would have shipped a bug. **The item is COLD now**, and cheaper than its old framing suggested.

  **The "always-true null check" sub-claim is now VERIFIED true** (was UNVERIFIED). It is `if (collection.getContent() != null)` at **`CollectionService:914`**, and both branches of `convertToFullModel` set content -- `Collections.emptyList()` at `CollectionProcessingUtil:352` in the empty case, and via `convertToModel` at `:328` otherwise -- so it is never null on this path. It is dead weight, and removing it is part of this item, not a separate one.

  **DONE** ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280), 2026-08-31, sixth run). Shipped as the re-shaped item specified: `CollectionRepository.findGalleryAccessBySlug` returning a nested `GalleryAccessRow`. The converter was **not** widened. **Fixture churn the item did not predict**: ten stub `CollectionModel`s in `CollectionServiceTest` had null content and NPE'd once the dead guard went. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-15--the-projection-and-the-fixture-churn-nobody-predicted-280).

## MR 19 #18: tracker body moved 2026-09-01

- [x] #18. `EquipmentRepository` repeats each SELECT column list **3-4 times per list across 3 lists** while sibling repositories hoist constants (`AppUserRepository`, `ShareLinkRepository`, `WebAuthnCredentialRepository`, `CollectionRepository` all do it right -- re-confirmed 2026-08-31). Hoist per-entity constants. **RE-PRICED DOWN 2026-08-31 (third run), because two of the eleven lists cannot share a constant.** There are 11 lists, of which **9 are hoistable into 3 constants** and 2 are one-off variants: `104` carries an extra `body_serial_number` and `198` an extra `lens_serial_number`. The other nine are identical within their group -- cameras `113`/`121`/`183`, lenses `207`/`214`/`258`, film types `270`/`278`/`325`. (The two `SELECT COUNT(*) > 0` at 221 and 285 are not column lists.) Nine one-line replacements plus three constant declarations is roughly **-6 net, not ~-15**. **The value is consistency with the siblings, not line count.** *Blocker*: do not force the two variants into the constant by concatenation -- that changes what the row mapper receives on the two serial-number lookups.

  **DONE** ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282), 2026-08-31, sixth run, grouped with #20). Three constants, nine call sites; the two serial-number variants left inline as the blocker required. **-12 net, and the item's "-6" was nearer the truth than that suggests** -- most of the delta is six banner comments. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).

## MR 19 #19: tracker body moved 2026-09-01

- [x] #19. `model/ImageSearchResponse.java` is a strict subset of `model/PagedResponse.java`. Replace it with `PagedResponse<ContentModels.Image>`. **Unblocked 2026-08-24**: the frontend reads only `result.content`, never `totalElements`/`totalPages`, and ignores unknown keys, so growing the contract is safe. (That is a 2026-08-24 finding in the other repo and was **not** re-verified on 2026-08-31.) `AdminController` already re-wraps into `PagedResponse`, so those lines vanish. **Do this before MR 17 #7.**

  **RE-PRICED UP 2026-08-31 (third run): the board priced the source and not the tests.** Four source sites -- `ContentService.searchImages` declaration `393` and its return at `404`; `ContentControllerProd.searchImages` return type at `46` and its local at `75`; `AdminController` import `13` and lines 286-291; then delete `model/ImageSearchResponse.java`. **Seven test constructions the board does not mention, across three files**: `ContentControllerProdTest` at `259`, `280`, `312`, `334`, `356`; `AdminControllerTest:751` as of #267 (was 750); `ContentServiceTest:101`. Each becomes `new PagedResponse<>(images, n, m, number, last)` and the implementer has to **pick correct `number` and `last` per test rather than copy them**. This is exactly the shape the board has been burned by twice -- a response-type change that reads as local and lands in tests. The good news: **no assertion needs rewriting.** `ContentControllerProdTest` asserts on `$.content`/`$.totalElements`/`$.totalPages`, `AdminControllerTest:757-759` on `$.content`, `ContentServiceTest:102-104` on `.content()`/`.totalElements()`/`.totalPages()` -- all keys and accessors `PagedResponse` also carries. Only the 7 constructor calls need two more arguments each. **Realistic ~25 lines, not "4 more lines vanish as a bonus".**

  **DONE** ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283), 2026-08-31, sixth run). +23/-47 across 7 files. **The soft premise is still soft and the PR says so**: this widens `GET /api/read/content/images/search` by the keys `number` and `last`, and the frontend-safety claim rests on a 2026-08-24 reading of `edens.zac` that cannot be verified from this repo. **This unblocked MR 17 #7**, which shipped 2026-09-01 as [#290](https://github.com/themancalledzac/edens.zac.backend/pull/290). [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-19--pagedresponse-and-a-premise-that-is-still-soft-283).

## MR 19 #20: tracker body moved 2026-09-01

- [x] #20. `Records.FilmFormat` (DTO) shadows the `FilmFormat` enum, forcing a fully-qualified name at `Records.java:23` and duplicating the mapping at `ContentControllerProd:147-149` and `CollectionService`. Rename the record `FilmFormatOption`, import the enum, one static factory. **Refs re-derived 2026-08-31 (third run): `Records.java:23` and `ContentControllerProd:147-149` exact; `CollectionService` DRIFTED +3, from `930-932` to `**973-975 as of #266** (was 933-935)`** (`List<Records.FilmFormat> filmFormats =` at 933, `Arrays.stream(FilmFormat.values())` at 934, `.map(ff -> new Records.FilmFormat(...))` at 935). **One source site the board never named: `model/GeneralMetadataDTO.java:26` declares `List<Records.FilmFormat> filmFormats)`** and the rename touches it -- three source sites, not two. **Test impact is nil**: `ContentControllerProdTest` asserts on `$.filmFormats[0].name` and `.displayName` (394-398, 412), which are component names and do not change under a type rename, and no test constructs `Records.FilmFormat`.

  **DONE** ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282), 2026-08-31, sixth run, grouped with #18). **Five source sites, not three** -- `Records.java` carries two independent ones. Renamed to `FilmFormatOption` with a `FilmFormatOption.of(FilmFormat)` factory; zero test sites, as predicted. [Write-up](2026-08-22-backend-cleanup-history.md#mr-19-18-and-20--grouped-and-20-miscounted-again-282).

## CollectionServiceTest twins: tracker body moved 2026-09-01

- [x] `services/CollectionServiceTest.java` assert/verify twins. **DONE**
  ([#296](https://github.com/themancalledzac/edens.zac.backend/pull/296), 2026-09-01, ninth run,
  `-46`, one file, no main file touched, 1502 -> 1500 tests; **the suite is 1,505 on `main` at `43c6f2c6` after #297 added five, measured by running it, not by counting `@Test` annotations**). **All four refs the eighth close-out
  re-derived landed exact** (`150`, `185`, `222`, `251`) -- the first ref set on this board in
  several runs to need no correction, which is what a re-derivation by name buys. Only the redundant
  twin of each pair was deleted: `createCollection_verifiesEntityCreatedViaUtil`, whose one unique
  assertion the happy-path test already covers transitively, and
  `deleteCollection_happyPath_disassociatesAllRelationshipsThenDeletes`.

  **The "strict subset" claim needed checking and the item was right.** Plain `verify` fails on a
  second matching call; `inOrder.verify` consuming one invocation at a position does not obviously
  do so, so the two are not the same assertion about cardinality and "strict subset" was not free.
  Duplicating `collectionRepository.deleteById(id)` in the service **does** fail the surviving inOrder
  test, which settles it. Generalizable, and now **working rule 51**.

  **File size re-measured on `main`: `wc -l` returns 2,850**, not the 2,893 the eighth close-out
  recorded -- #293 added 3 after that measurement, then #296 removed 46. It has grown **438** since
  the 2,412 baseline, not 481.
  [Write-up](2026-08-22-backend-cleanup-history.md#collectionservicetest-assertverify-twins-296).

## #27: tracker body moved 2026-09-01

- [x] **#27 (coverage gap) — constraint annotations on controller parameters are untested
  repo-wide.** **CLOSED 2026-09-01 (ninth run) by [#297](https://github.com/themancalledzac/edens.zac.backend/pull/297),
  and the audit its guardrail demanded changed the item before a line was written.**

  **The premise is wrong as filed.** The item says "every other constraint-annotated controller
  parameter in the repo has the same untested gap". **There is no other one.** Across every file in
  `controller/`, exactly one method parameter carries a constraint annotation:
  `ContentControllerProd.searchImages`'s `page` and `size` -- the one #290 fixed and
  [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294) has since moved into
  `ImageSearchFilter`. `ContentControllerProd` is also the only controller carrying `@Validated` at
  all. So the "wire the proxy" half of this item was **zero controllers** and no proxy was wired.

  **The distinction the item missed, and it generalizes -- now working rule 52.** The missing
  `@Validated` proxy under `standaloneSetup` only affects constraints on *method parameters*. Every
  other constraint in the package sits on a record component of a `@Valid @RequestBody` DTO
  (`RoleRequests`, `UserRequests`, `InviteRequests`, `EditController.RatingPatch`), and those are
  enforced by the `WebDataBinder`, which `standaloneSetup` **does** build. They were never in the gap.
  The five `@RequestBody` parameters lacking `@Valid` all bind an untyped `Map`, `List` or `String`
  with no constraints on them, so nothing is unreachable that way either.

  **What was actually left was coverage, and most of it already existed.** `CreateUserRequest` and
  `UpgradeUserRequest` email, `UpdateUserRequest` email and status, `InviteRequests` password and
  displayName, and `RatingPatch` rating each already had a 400 test. **Four did not**, and #297 adds
  one each with a mutation apiece: `CreateRoleRequest.name` `@NotBlank`, the same field's
  `@Size(max = 128)`, `SetRoleGrantRequest.level` `@NotNull`, and `UpdateUserRequest.description`
  `@Size(max = 500)`. **No constraint annotation was changed and none turned out wrong.**

  **Rule 32 fired on the fourth one**, which is why it is worth recording: with no stub it died 404
  on an unstubbed `findById` rather than at the guard, proving a fixture gap and not the constraint.
  It now stubs the row `lenient()` -- the same technique, for the same reason, as
  `updateWithPersonStatusReturns400AndWritesNothing` three tests below it -- and the mutant lands as
  a 200 that writes 501 characters.
  [Write-up](2026-08-22-backend-cleanup-history.md#27-outcome-2026-09-01--the-audit-emptied-the-item-297).

## Progress row narratives: Wave 5 chain, moved 2026-09-01

| 5 — Consolidations | MR 15-19 | **Three more shipped 2026-09-01 (eighth run)**: MR 17 #7 ([#290](https://github.com/themancalledzac/edens.zac.backend/pull/290)) -- the last item this wave had blocked on ordering -- MR 18 #11 ([#288](https://github.com/themancalledzac/edens.zac.backend/pull/288)) and #12b ([#291](https://github.com/themancalledzac/edens.zac.backend/pull/291)). **MR 17 is complete.** What is left in the wave is **MR 18 #10**, **MR 18 #13's sort split** and **MR 19 #17**. **MR 16 #3 was ticked closed as decided 2026-09-01 (tenth-run review)** -- every number in it has reproduced exactly across three re-derivations and the answer has been "not worth doing" every time. **MR 18 #13's sort split is re-scoped and now BLOCKED**: two of the three producers it named as unsorted are ordered in SQL, and what is left is a Java-versus-SQL collation question nobody can answer without reading the production database's collation. Prior row text: **Six members shipped 2026-08-31 (sixth run)**: MR 17 #8 ([#285](https://github.com/themancalledzac/edens.zac.backend/pull/285)), MR 18 #9 ([#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)), MR 18 #12 ([#284](https://github.com/themancalledzac/edens.zac.backend/pull/284)), MR 19 #15 ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280)), MR 19 #18 and #20 together ([#282](https://github.com/themancalledzac/edens.zac.backend/pull/282)), MR 19 #19 ([#283](https://github.com/themancalledzac/edens.zac.backend/pull/283)). **MR 19 #19 unblocks MR 17 #7**, which was the board's only ordering blocker inside this wave. **Three of the six corrected their own item's count or estimate on landing** -- see the sixth-run log entry. Prior row text: MR 15 #1, #2, #6 **done** ([#165](https://github.com/themancalledzac/edens.zac.backend/pull/165), [#189](https://github.com/themancalledzac/edens.zac.backend/pull/189), [#191](https://github.com/themancalledzac/edens.zac.backend/pull/191)) and the follow-up closed ([#210](https://github.com/themancalledzac/edens.zac.backend/pull/210)) — **MR 15 is fully done**. MR 19 #16 shipped ([#216](https://github.com/themancalledzac/edens.zac.backend/pull/216)); MR 19 #14 shipped ([#218](https://github.com/themancalledzac/edens.zac.backend/pull/218)) — the first item in seven to need no adjustment at implementation time, which broke the streak the full-board review's case rested on. MR 16 #4 and #5 both shipped ([#261](https://github.com/themancalledzac/edens.zac.backend/pull/261), [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262), 2026-08-31), and **MR 19 #21 shipped the same day** ([#266](https://github.com/themancalledzac/edens.zac.backend/pull/266)) -- the second consecutive item whose prescribed fix needed no adjustment. Prior row text: [history](2026-08-22-backend-cleanup-history.md#board-row-narratives-moved-2026-08-29). |

## Open security findings row: prior states, moved 2026-09-01

| [Open security findings](2026-08-22-backend-cleanup-spike.md#open-security-findings) | **3 open — the section refilled 2026-09-01 (tenth run, full-board review): S-29 (MED), S-30 (LOW), S-31 (LOW).** All three sit on the anonymous public read surface, which the twenty-seven closed findings never attacked. Edit gate (rule 36): `grep -c '^- \[ \] \*\*S-'` = **3**, measured on the review branch `docs/full-board-review-2026-09-01-tenth-run`; **re-run it on `main` after the merge (rule 42)**. **The recorded command was broken from the sixth close-out until this review**: it read `'^- [ ] \*\*S-'`, whose unescaped `[ ]` is a bracket expression matching one space, so it returned 0 against any input and agreed with reality only while the section happened to be empty. Prior state: The section refilled 2026-08-31 (third run) with three, and [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) closed two of them the same day: **S-26, the HIGH** (a deregistered passkey's sessions kept resolving, and their holder could register a replacement from inside one) and **S-27** (a docblock #257 falsified), which rode with it as filed. **S-28 (LOW) is what is left** -- one docblock line naming the redeploy recovery for an admin who deregisters their own last passkey. All three came out of the merged set (#247, #248, #250, #253, #257) attacked as a set; that pass also confirmed **S-16's reachability claim holds**, which is recorded under "Verified sound" so nobody re-derives it. **27 closed**, one ledger line each below; the newest outcomes are in [history](2026-08-22-backend-cleanup-history.md#s-26-outcome-2026-08-31----the-fix-was-one-call-and-three-mutations-were-needed-to-prove-it). **The ledger has 27 lines and the highest number issued is S-28: S-25 was never assigned and appears nowhere in either file.** The board recorded that count three different ways (27, 28, twenty-five) until 2026-09-01; all three cells now say 27. This cell's own second gate stamp read **1** for four close-outs after [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278) took the figure to 0, contradicting the head of the same cell; the stale sentence is deleted. **This gate counts numbered findings only**; the unsettled questions have their own section and their own row, because four of them used to live inside this section where no gate could see them. |

## Cross-repo row: prior states, moved 2026-09-01

| [Cross-repo findings owed to the frontend](2026-08-22-backend-cleanup-spike.md#cross-repo-findings-owed-to-the-frontend) | **5 open as of 2026-09-01 (tenth run): FE-2 through FE-5 plus the newly filed [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294) page-size debt.** **FE-1 is CLOSED as won't-do**: BE-2 was answered "drop the array", so the location page stays on `searchImages` and there is no GIF to widen the props for. **The count moved out of the heading** so that correcting it cannot break this link. **Every FE row was re-verified live against `edens.zac` `origin/main` at `f4e8e25` on 2026-09-01** -- the clone exists on this machine and the board's two claims that it does not are deleted. Prior text: **5 open — re-derived 2026-08-31 (third run) by a full pair scan of both repos.** The GIF row's premise was **wrong and is corrected in place**: the frontend's `/location/[slug]` page never reads `LocationPageResponse.images`, so a location-tagged GIF cannot reach it today at any prop type. Four more were found, all dormant or dev-only. **All five are now filed in `edens.zac`** ([#371](https://github.com/themancalledzac/edens.zac/pull/371), **merged 2026-08-31**, docs-only), which closes the gap the second run declared and could not close: four became new rows (C14, C15, C16, H7) and the fifth was already shipped there as G6 (#351), so it went under that board's "do not re-investigate" list rather than becoming a duplicate. #371 has merged, so these are filed on both boards; the rows stay open here until the frontend acts on them. The same scan found two backend items: an N+1 regression (now **MR 19 #21**) and a serialization question (now a Decisions row). Read the section. |

## Decisions row: prior states, moved 2026-09-01

| [Decisions needed from the user](2026-08-22-backend-cleanup-spike.md#decisions-needed-from-the-user) | **2 open as of 2026-09-01 (tenth run), and NEITHER is waiting on you.** **BE-2 was answered: drop the array.** The location endpoint stops hydrating and serializing the orphan `images` array, and the frontend stays on `searchImages({ locationId })`. That closes FE-1 as won't-do and turns the removal into a COLD backend item. The two that remain are parked by decision and are now filed under their own subheading so this count cannot read as a queue of live questions: gallery passwords (pending a design) and C7's partial indexes (an explicit "not until scale demands it"). Prior text: **3 open as of 2026-09-01 (ninth close-out), and ONE is waiting on you.** **#28 was asked at the top of the ninth session, answered in one word ("admin's 50") and shipped the same day** as [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294) -- the second consecutive time a one-word question batched into the opening message became one of that run's own MRs. The live question is whether the location endpoint should keep serving an `images` array. The other two wait on nobody: gallery passwords are parked by decision, and C7 is an explicit "not until scale demands it". Prior text: **4 open as of 2026-09-01 (eighth close-out), and TWO are waiting on you.** The new one is **#28 -- unify the image-search page size on prod's 30 or admin's 50?**, filed out of [#290](https://github.com/themancalledzac/edens.zac.backend/pull/290) and answerable in one word. The other live question is whether the location endpoint should keep serving an `images` array. The remaining two wait on nobody: gallery passwords are parked by decision, and C7 is an explicit "not until scale demands it". Prior text: **3 open, and only ONE is waiting on you.** The three one-word calls were asked in the opening message of the 2026-08-31 third run and all three came back: `cover_image_id` **drop** (V59), the DB-password default **drop the default** (`${POSTGRES_PASSWORD}`), `role.kind` **keep, documented as provenance** (V60). All three shipped together in one MR. What remains: gallery passwords (**parked by decision** pending a design) and the partial-index item C7 (an explicit "not until scale demands it"), neither of which waits on anyone -- plus **one new row added by this run's cross-repo scan**, whether the location endpoint should keep serving an `images` array at all, given the frontend discards it. That one is a real open question. **Batching the three into the opening message is what turned them into a same-session MR** — asked at the end, they would have been the next session's problem (working rule 41's neighbour). Edit gate (rule 36): the count is over the section's own `- [ ] ` lines; re-run it and update this row together. |

## Stale side branches row: prior state, moved 2026-09-01

| [Stale side branches](2026-08-22-backend-cleanup-spike.md#stale-side-branches) | **Branch and worktree list re-run 2026-09-01 (tenth run).** **Ten worktrees, not six** -- five under `edens.zac.backend.worktrees/` and five under `.claude/worktrees/`; `git worktree list` returns eleven rows, the eleventh being the main checkout. Four were created after 2026-08-24 for work that has since merged and none reached this board. **Zero open PRs in the repo**, and **four of the branches this section tracks have no `origin` ref at all**, so the measuring command the section recorded fails on half its rows. Prior text: **New 2026-08-24; branch list re-run 2026-08-31 (third run).** 6 worktrees, unchanged. **"0 open PRs" was wrong and is corrected**: `0359-fe-ma1-collection-patch` carried [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252), which **merged 2026-08-31** after this was written; it had held the only copy of item #22, now folded into the tracker directly. **That branch is therefore safe to delete, making four deletable, not three** -- though it still reports 1 ahead, because #252 was squash-merged (see the section). Three others are genuinely 0 ahead and safe to delete; two hold unique work. `fix/s18-actuator-exclude` holds nothing unique (settled 2026-08-30, see the section). |

## Unsettled security questions row: prior state, moved 2026-09-01

| [Unsettled security questions](2026-08-22-backend-cleanup-spike.md#unsettled-security-questions) | **5 open, re-run 2026-09-01 (eighth close-out): `grep -c '^- \[ \] \*\*U-'` returns 5, not the 7 this row and the section's own gate both claimed.** U-5 and U-6 shipped 2026-08-31 (sixth run) as [#274](https://github.com/themancalledzac/edens.zac.backend/pull/274) and [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278) and were ticked in the section, but neither the row nor the gate stamp was edited with them -- **working rule 36's failure mode in a section that is not the one rule 36 was written about.** Open: U-1, U-2, U-3, U-7, U-8. **The section also holds one non-`U-` open box as of 2026-09-01** -- the `RoleRepository.canView`/`isClient` deletion, which is work rather than a question and opens `**Delete` so it cannot move this gate. It had been written down since 2026-08-25 inside a ticked bullet where no gate could see it. Prior text: **7 open — U-4 shipped 2026-08-31 as [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)** (it was never a question; the fourth run re-classified it as a specified one-block fix filed in the wrong section, and this run shipped it). **Row created 2026-08-31 (third run).** Four of these lived inside "Open security findings" as plain checkboxes, so `grep -c '^- \[ \] \*\*S-'` reported the section empty while it held them; four more existed only as prose inside closed `[x]` ledger lines, with no checkbox at all. They are now one numbered list with its own gate. Edit gate (rule 36): `grep -c '^- \[ \] \*\*U-'` = **5**, re-run 2026-09-01 (ninth close-out) — run it and update this row together. **The stamp read 7 for two close-outs after the row's own lead text was corrected to 5**, which is rule 36's failure mode inside the very cell that carries rule 36's instruction: the lead was edited, the gate was not. |

## Bugs category row: prior state, moved 2026-09-01

| Bugs (fix, not delete) | **21** (5 high) — **21 shipped, 0 open.** Bug #18, the last one, shipped 2026-08-31 (sixth run) as [#276](https://github.com/themancalledzac/edens.zac.backend/pull/276). Checkbox check: `grep -c '^- \[ \] \*\*Bug #'` = **0**, re-run 2026-09-01 on `main` at `43c6f2c6`. **The recorded command had its `[ ]` unescaped from the sixth close-out until 2026-09-01, which made it return 0 against any input.** **The bug ledger is closed.** Previously: Bugs #17, #19 and #20 all shipped 2026-08-31 ([#256](https://github.com/themancalledzac/edens.zac.backend/pull/256), [#258](https://github.com/themancalledzac/edens.zac.backend/pull/258), [#255](https://github.com/themancalledzac/edens.zac.backend/pull/255)); #21 earlier the same day ([#249](https://github.com/themancalledzac/edens.zac.backend/pull/249)). Checkbox check: `grep -c '^- \[ \] \*\*Bug #'` = **1**, re-run 2026-08-31 (third close-out, unchanged). **Bug #18 is the only open bug on the board.** Items **#22 through #29** are filed in the same number series but are feature dependencies, doc bugs and coverage items rather than code bugs, so they open with `**#NN` and do not move this gate. **They now have a gate of their own, which they did not until 2026-09-01: `grep -c '^- \[ \] \*\*#2'` = **2** (#22 and #29), re-run 2026-09-01 on `main` at `43c6f2c6`.** The series was invented after rule 36 and had gone eight items with no command behind it. #23 came out of the fourth run's attempt to settle U-1 by looking, and **shipped 2026-08-31** ([#269](https://github.com/themancalledzac/edens.zac.backend/pull/269)): `ai_ec2.md` had carried a stale second copy of the `.env` template disagreeing with `.env.example` about the Spring profile, and both blocks are gone. **Open in the series: #22 and #29.** *(This cell said "#22 is the only one of the two still open" until 2026-09-01; the series was eight items by then, and #29 had been filed by the close-out that left the sentence alone.)* | — |

## Security findings category row: prior state, moved 2026-09-01

| Security findings | **3 open: S-29 (MED), S-30 (LOW), S-31 (LOW), all filed 2026-09-01 by the tenth-run review.** Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **3**, measured on the review branch; **re-run on `main` after the merge (rule 42)** and edit this cell and the section-table row together (working rule 36). **27 closed** -- the ledger runs S-1..S-24, S-26, S-27, S-28, and **S-25 was never assigned**, which is why "28 closed" was recorded here and "twenty-five" in the section lead. **The recorded command was broken from the sixth close-out until 2026-09-01**: `[ ]` unescaped is a bracket expression matching one space and returns 0 on any input. Previously: **1 open** (S-28 LOW). The three filed 2026-08-31 by the full-board review are down to one: S-26 (HIGH) and S-27 (LOW) shipped together as [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) the same day. 27 closed; the seven newest 2026-08-31. Checkbox check: `grep -c '^- \[ \] \*\*S-'` = **1**, re-run 2026-08-31 after #265 (working rule 36: run it and edit this cell and the section-table row together). Numbered findings only — the eight unsettled questions have their own gate. | — |

## Inline-comment count: measurement history

| Inline comments | **RE-RUN 2026-09-01 (ninth close-out) on `main` at `3a53c0cb`, all four ninth-run PRs merged, and the command itself was found wrong -- see working rule 50. Figures, `git grep`: **1,372** (203 main / 1,169 test). Trailing form: **68**, unmoved.** **Both deltas reconcile line-for-line (rule 42):** main `215 -> 203` is -12, all `CollectionRepository`; test `1,192 -> 1,169` is -23 = 21 (`CollectionRepositoryTest`) + 2 (`AdminRoleControllerTest`), the first from [#295](https://github.com/themancalledzac/edens.zac.backend/pull/295) and the second a rule-47 sweep riding with [#297](https://github.com/themancalledzac/edens.zac.backend/pull/297). [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294) and [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296) moved neither count. **The trailing figure held at 68 across both endpoints**, which is the first close-out in five where nothing about the comment metrics was wrong at the start -- except the command. **`grep -rn` skips a binary-classified test file and returns 1,189 where `git grep` returns 1,192 at the same commit; the board had recorded the `git grep` number beside the `grep -rn` command, so the two have never agreed.** Full explanation and the NUL byte that causes it: **working rule 50**. The command block below is updated to the `git grep` form. Worktree contamination check: `git grep` is tracked-files-only and cannot see `.claude/worktrees/` at all, which removes that hazard rather than re-checking it. Prior text follows. **RE-RUN 2026-09-01 (eighth close-out) on `main` at `8aa0a1ec`, both endpoints measured, every PR of the eighth run merged: leading form `1,407` (215 main / 1,192 test), trailing form `68`.** Both commands are quoted verbatim in the code block below this table, escaping included, exactly as run (**rule 31**) -- they carry pipes and cannot live inside a table cell. **Both deltas reconcile line-for-line to one file (rule 42):** leading `1,477 -> 1,407` is -70 and trailing `72 -> 68` is -4, and both are `CollectionServiceTest`, which went 70 -> 0 whole-line and 4 -> 0 trailing in [#289](https://github.com/themancalledzac/edens.zac.backend/pull/289). Nothing else moved. `src/main` did not move at all -- 215 at both ends -- even though [#290](https://github.com/themancalledzac/edens.zac.backend/pull/290) edited two controllers and [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291) edited `CollectionRepository` and `CollectionService`; those PRs added no whole-line comments. **The recorded trailing `74` was wrong.** Measured at the pre-run commit `19e1cabf` the board's own trailing command returns **72**, so the recorded `74` was 2 high and had rotted unnoticed since the second close-out, where it was stamped "re-run 2026-08-31 (second close-out) and still 74". That is **rule 46's second half for the FOURTH consecutive close-out**, and it is worth saying so plainly: the fifth caught `1,276`, the sixth caught a stale `74` checkbox total, the seventh caught `~107`, and this one caught the trailing `74`. This one is the purest specimen yet -- it sat outside the neighbourhood of everything that merged, which is the failure mode. A count nobody re-runs just sits there being wrong. Worktree contamination check, which the row below requires: the scoped commands return **0** hits under `.claude/worktrees/`. Prior text follows. **SETTLED at the seventh close-out (2026-08-31), with every PR of the sixth run merged: `1,477` (215 main / 1,262 test).** Command, as run: `grep -rn '^[[:space:]]*//' --include='*.java' src/main | wc -l` and the same for `src/test` (bracket class, per **rule 46**). **The sixth close-out's `1,487` (225 / 1,262) was a checkpoint taken before [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284) and [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) merged, and it said so.** The remaining delta reconciles to a single file: measured at each commit, #281 and #284 moved it **not at all** (225 / 1,262 throughout), and #285 took `src/main` **225 -> 215** with `RoleRepository` going **10 -> 0**. **Rule 46 again, and worth stating**: #285's PR body says it removed *11 lines* from that file while this checksum moved *10*, and the trailing `code; //` count in that diff is **0** -- so the difference is a non-comment line, not a trailing comment. Both numbers are right about different metrics. **Sixth-run arc, both endpoints measured on `main`:** 260 / 1,273 before the run, 215 / 1,262 after -- **-45 main, -11 test** -- though [#277](https://github.com/themancalledzac/edens.zac.backend/pull/277) and [#281](https://github.com/themancalledzac/edens.zac.backend/pull/281) landed inside that window from outside the run, so the arc is not wholly attributable to it. Prior checkpoint text follows. **Read the scope of that figure before quoting it.** It includes [#276](https://github.com/themancalledzac/edens.zac.backend/pull/276), [#278](https://github.com/themancalledzac/edens.zac.backend/pull/278), [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279), [#280](https://github.com/themancalledzac/edens.zac.backend/pull/280), [#282](https://github.com/themancalledzac/edens.zac.backend/pull/282), [#283](https://github.com/themancalledzac/edens.zac.backend/pull/283) **and [#277](https://github.com/themancalledzac/edens.zac.backend/pull/277), which is not part of this run** -- and it does **not** include [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284), [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) or this close-out. **It is therefore a checkpoint, not this run's final figure**, and the seventh close-out must re-run it once the last two land; a full attribution is not possible here because #277 landed in the middle of the run from outside it. **The verified sub-delta, measured on `main` both sides:** [#276](https://github.com/themancalledzac/edens.zac.backend/pull/276) took `src/main` **260 -> 253** (the seven `// ===` section markers in `MetadataService`) and `src/test` **1,273 -> 1,268** (one whole-line comment plus a four-line block relocated into docblocks in `MetadataServiceTest`), and both reconcile line-for-line against that diff (**rule 42**). From 253/1,268 the five later merges plus #277 give **-28 main / -6 test**. **A recorded absolute has rotted again**: the fifth close-out recorded `1,276` on the test side, but the board's own command at that commit returns **1,273** -- three high, which is **rule 46's second half** appearing for the second consecutive run. Use **260/1,273** as the pre-sixth-run baseline, not 260/1,276. **Four of this run's PRs deleted comments and one declined a sweep** ([#280](https://github.com/themancalledzac/edens.zac.backend/pull/280) left ~107 lines and filed them as their own item; see **rule 47**). Prior text follows. **Re-measured 2026-08-29.** Old criterion (whole-line `//` at indent >= 4, `src/main`): **73**. Rule-37 criterion (any line whose first non-whitespace is `//`, `src/main` + `src/test`): **1,675** (290 main / 1,385 test), plus **72** trailing `code; //` lines. **Re-run 2026-08-31 (fifth run, post-merge): 1,536 (260 main / 1,276 test)** -- down 97, the largest single-run drop on this board, and **it reconciles exactly** (**rule 42**): main -2 (U-4's two-line slide comment, [#270](https://github.com/themancalledzac/edens.zac.backend/pull/270)); test -98 = 73 (`AdminUserControllerTest`, [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272)) + 16 (`ContentServiceDownloadTest`, [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271)) + 9 (`SessionServiceIntegrationTest`, #270). **Fourth consecutive run that removed some and added none.** **Two corrections to the figures below, and neither was caused by this run.** First: the prior run's recorded **1,371** was wrong; the board's own command at `a9d9e661` returns **1,374**, and has since `41d928b4`, so every absolute in the chain below is 3 low while every recorded delta is right -- see **rule 46**. Second: #271's PR body says 17 comments deleted where this metric moved 16, because one was a trailing `code; //`; both are correct about different things. Prior run, as recorded and 3 low: **1,633 (262 main / 1,371 test)** -- down 4 from 1,637, and the delta reconciles line-for-line against a single file (**working rule 42**): `CollectionServiceTest` lost 4 `// Arrange` / `// Act` / `// Assert` markers in the two tests [#266](https://github.com/themancalledzac/edens.zac.backend/pull/266) rewrote (`git show` on that file: 4 removed, **0 added**). `src/main` did not move. **Third consecutive run where a session removed some and added none.** Prior run: **1,637 (262 main / 1,375 test)** -- down 7 from 1,644 (262/1,382), and that delta also reconciled against a single file: `ReadCacheInvalidatorTest` went **7 -> 0** in [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262), which removed its inline comments and moved the three carrying real reasoning into docblocks. `src/main` did not move at all -- `SesConfig.java` was deleted by [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261) and held zero inline comments. **This is the second consecutive run where a session removed some and none were added.** Prior figure: **1,644 (262 main / 1,382 test)** -- down 31 from 1,675 (290/1,385). The earlier re-run that day was UNCHANGED, which was the first confirmation that a session added none; this one is the first that a session *removed* some. **The delta reconciles exactly against the four MRs' own deletions** (-28 main: 11 in `ImageUploadPipelineService`, 10 in `ContentService`, 7 in `AdminUserController`; -3 test: 1 in `AdminUserControllerTest`, 2 in `WebAuthnCredentialRepositoryIntegrationTest`), which is **working rule 42**. **The trailing figure was wrong and its recorded command did not reproduce it.** As recorded, `grep -vE '^\s*[^:]+:[0-9]+:\s*//'` returns **231**, not 72: BSD `grep -E` does not honour `\s`, so the exclusion under-matches, and nothing excluded URLs -- every `https://` in a javadoc counts as a `//`. **Corrected count: 74**, by the trailing command below, which is portable and URL-safe. *(That `74`, stamped "re-run 2026-08-31 (second close-out) and still 74", is the number the eighth close-out found to be 2 high. It was **72** at `19e1cabf` and is **68** now.)*

## MR 16 #3: one keyed rate limiter, body moved 2026-09-01

- [x] #3. One keyed rate limiter. **CLOSED AS DECIDED 2026-09-01 (tenth-run review): not worth doing.** Every number in this item reproduced exactly at `43c6f2c6` for the third consecutive run -- four source line counts, four structural refs, three test-site pairs, `RateLimitFilter:131` as the only `estimateAbilityToConsume` call -- and the answer has been "no" every time. Carrying it as an open checkbox that will never be ticked is the same failure as `AdminHomeService`'s cache row, and it was closed the way `AuthPrincipal`'s constructor was: as a decision, not a deferral. **Stop re-deriving it.** Body kept for the four structural reasons the merge does not work. Prior text: **Re-derived 2026-08-31 (third run): FOUR copies, not three.** `config/ShareEmailLimiter.java` (106 lines) landed 2026-08-28 with S-17 ([#233](https://github.com/themancalledzac/edens.zac.backend/pull/233)) carrying the same Caffeine + Bucket4j core -- `Caffeine.newBuilder()` at 67, a private `newBucket(int, Duration)` at 97, **plus a global daily bucket at 71**, which makes it structurally the closest thing on the board to the shared `KeyedRateLimiter(capacity, window, idleTtl)` this item imagines *and* the second class carrying the global bucket that signature has no slot for. Its test adds 3 constructor sites and 27 `.allow(` calls, taking the test bill from ~84 to **~114**. **Every 2026-08-24 number below re-verified exact 2026-08-31**: 82 and 81 source lines, 7+24 and 7+32 test sites, one `estimateAbilityToConsume` site (`RateLimitFilter.java:131`), `AuthLoginLimiter` a 59-line Caffeine counter with no `Bucket`. **The verdict gets stronger, not weaker: still not worth doing.** Prior text: **Re-derived 2026-08-24: three copies, not two** -- `RateLimitFilter.newBucket` is a third byte-identical Caffeine+Bucket4j core. **Two halves of the original wording were wrong and are corrected here.** "The same class twice" is false: `ContactMessageLimiter` carries a global daily bucket that a `KeyedRateLimiter(capacity, window, idleTtl)` signature has no slot for, and its own docblock calls that bucket the only limit an attacker cannot pick the key for. "Their TTL policies have already drifted" is also false: `ClientGalleryAccessLimiter`'s `window + 15min` idle TTL is a documented deliberate choice (an attacker must not reset it by pausing), and calling it drift invites someone to "fix" it to 2h and weaken it. **Cost is test-dominated: ~-55 source against ~84 test sites** (7 constructor sites + 24 calls in `ContactMessageLimiterTest`, 7 + 32 in `ClientGalleryAccessLimiterTest`, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`). Keep `AuthLoginLimiter` separate -- it is a `Cache<String,Integer>` counter, not Bucket4j. Low priority.

  **Cost re-measured 2026-08-24 while doing S-5, which was told to leave these cores alone. Every number above held, and one new blocker turned up.** The test-site counts are exact, not approximate: `ContactMessageLimiterTest` has 7 constructor sites and 24 `tryConsume` calls, `ClientGalleryAccessLimiterTest` has 7 and 32 `.allow(` calls -- 70 in the two dedicated tests, plus `CollectionControllerProdTest` and `MessagesControllerPublicTest`. Source is 82 + 81 lines across the two classes, and the shared part of them is small: the bucket shape (`Bandwidth.builder().capacity(n).refillIntervally(n, window)` wrapped in `Bucket.builder().addLimit(...)`) and the `Caffeine.newBuilder().maximumSize(10_000)` cache. Everything around it differs.

  **The new blocker is `Retry-After`.** `RateLimitFilter` does not just ask its bucket a yes/no question -- it calls `bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill()` to build the header (the only such call in the codebase). A `boolean allow(String key)` signature, which is the shape the other two callers want, cannot serve it. A merged class has to expose the `Bucket` or a nanos-to-refill accessor, and that is a wider API than the item's framing implies.

  **Four more things that do not merge**, all found by reading the three call sites rather than the class list. (1) Three different key functions: `email.trim().toLowerCase(Locale.ROOT)`, `ip.trim() + "|" + GalleryAccessCookies.normalizeSlug(slug)`, and `ClientIp.resolve(request)` -- so the shared class takes a pre-computed key and each caller keeps its own normalization, which is most of what looked like the duplication. (2) Three different blank-key policies: `ContactMessageLimiter` skips the per-email bucket but has already spent a global token, `ClientGalleryAccessLimiter` returns true, `RateLimitFilter` has no blank case. (3) The idle TTL cannot have a default -- `ClientGalleryAccessLimiter`'s `window + 15min` is deliberate and the other two are a fixed 2h, so it must be an explicit constructor parameter, which is the parameter most likely to be got wrong later. (4) `ClientGalleryAccessLimiter`'s package-private `Duration` constructor exists so refill-timing tests can use sub-second windows instead of sleeping for minutes; it has to survive the merge intact.

  **Verdict unchanged, with more confidence behind it: not worth doing.** The merge saves roughly 50 source lines, needs a wider API than a boolean, and rewrites ~70 test call sites -- and the four items above are each a way to quietly weaken a live limiter while the suite stays green. S-5 no longer collides with it; that file is settled.

## Rule-37 per-file sweep: closed-file chain, moved 2026-09-01

| ^ **re-scoped 2026-08-28** | Working rule 37 turns this from a debloat nice-to-have into a standing rule: **every** inline comment in `src/main` and `src/test` is now a violation, not just the ones a rule flagged. **Do not sweep this in one MR** — take it per package, and take the files working rule 12 protected first -- **`AdminBootstrap` (6, and it is in `services/`, not `config/`) and `CollectionControllerProd` (9); both re-run 2026-09-01 on `main` at `43c6f2c6` and unchanged.** **`RoleRepository` is DONE and this cell said otherwise for four close-outs**: it held 10 until [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285) took it to **0** as a side effect of MR 17 #8 on 2026-08-31, and the Inline-comments row sixteen lines above has recorded that all along. Two cells, one maintained. **`SecurityConfig` is off this list**: #243 swept it from ~27 to **4** as a side effect of removing the authz toggle, so it is nearly done and no longer the priority the row assumed. **One recorded exemption**: the second `coverImage` banner in `CollectionControllerProdTest` stays until its "Carried forward" decision lands. **`AdminUserControllerTest` is DONE** -- it held 73, the single largest concentration found on this board, and [#272](https://github.com/themancalledzac/edens.zac.backend/pull/272) took it to **0** on 2026-08-31 (re-run at the close-out). [#265](https://github.com/themancalledzac/edens.zac.backend/pull/265) had edited that file and deliberately left them, which was the right call and is why it became its own MR. **Its neighbours were explicitly not swept and still hold their own counts** -- `AdminControllerTest` and the other admin tests are each their own MR, unmeasured as of this close-out. (Bug #17's `ContentService` comment is no longer exempt — its board row is the evidence now.) | — |

## MR 18 #10: ref drift chain, moved 2026-09-01

- [ ] #10. `updateGif` reimplements the tag/people/location merge blocks that `ContentMutationUtil` already owns as `updateImage*Optimized` (`ContentService.updateGif`, **declaration at `550` as of #256** (was 546-635; +4 from #256's docblock, net of its comment deletions), vs the three `updateImage*Optimized` helpers in `ContentMutationUtil`, **`177-237` as re-derived 2026-09-01 on `main` at `43c6f2c6`**: Tags **177**, People **199**, Locations **221** -- **all three drifted -6 when [#284](https://github.com/themancalledzac/edens.zac.backend/pull/284) removed the `nextOrderIndex` copies from that file, and the "all four refs re-verified exact 2026-08-31" stamp below was taken against a pre-#284 tree on the day #284 landed**). **"The helpers only use the content id" is FALSE** -- all three call `setTags`/`setPeople`/`setLocations`, which are declared on subclasses, not `ContentEntity`. The fix needs a return-the-set signature, not a retype, and it converts `ContentServiceTest.updateGif_persistsPeopleAndLocations` (**`ContentServiceTest.java:144`**) into a weaker test. Realistic ~180, not ~40, and the cost is dominated by that test rewrite. **`updateGif`'s declaration at `550` is exact and unchanged; the method spans 550-639.** **One thing that makes the fix cheaper than described**: `updateGif` never calls `setTags`/`setPeople`/`setLocations` on the gif entity at all -- it computes the merged set and persists ids through `tagRepository.saveContentTags`, `contentRepository.saveContentPeople` and `locationRepository.saveContentLocations`. So a return-the-set helper serves the gif path directly and only the image call sites gain a `setX` line. The asymmetry is one line per block, not a redesign. **This item has been COLD and unworked for four close-outs and has not appeared under any run's `Next:` since the sixth**, which makes it invisible to the session log's leak detector while never being re-justified. Either work it or take it off the COLD list with a reason.

## MR 19 #17: ref drift chain, moved 2026-09-01

- [ ] #17. Smaller items: `UserInviteService.validate`/`redeem` duplicate token resolution (**`validate` 158-175 and `redeem` 257-274 as of 2026-08-27**; was 140-152 / 220-237, and before that 85-130 -- the file has gone 130 -> 238 -> 275 lines under S-7/S-9/S-15, **so stop quoting ranges for this one and find the two methods by name** -- into `findLiveInvite`); pagination normalization re-inlined in `CollectionService.getCollectionWithPagination` (**declaration `125`; find `int normalizedPage` by name and stop recording a number for this one** -- it has now been written as `127-130`, `142-144`, `143-145`, `145-147`, `147-149` and is back at **`145-147`** on `main` at `43c6f2c6`, moved by [#288](https://github.com/themancalledzac/edens.zac.backend/pull/288) and [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291). It is the single most-drifted ref on the board. It is three lines not four. **The `143-145` reading was anchor-text-verified hours before #266 invalidated it by adding two imports** -- anchor text was checked and the number was not re-derived from it, which is the whole failure mode; call `PaginationUtil`); `toEntity`'s `defaultPageSize` parameter and `applyPaginationDefaults` are redundant with each other (`CollectionProcessingUtil.toEntity` **`566-589`** and `applyPaginationDefaults` **`924-932`** as of 2026-08-25, were `569-596, 939-947` -- **neither file was touched by #213/#214/#216, so this drift predates them**); `uploadToS3`/`streamFileToS3` duplicate key and URL construction (`ImageProcessingService` -- declarations at **`715`** and **`742`** as of the seventh close-out -- **-5 each from the `720`/`747` recorded earlier on 2026-08-31**, and the shift is [#279](https://github.com/themancalledzac/edens.zac.backend/pull/279)'s shared upload loop; were 716/743, before that 697-745); EmailService HTML skeleton **three times, not twice** -- `buildHtml`, `buildInviteHtml` and `buildShareLinkHtml`, the third added by [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) under an explicit guardrail not to fold it in there (optional, **~50-70 lines now, not ~35**). #213's own write-up sent this consolidation to MR 24; that was wrong, it lives here and has always lived here.

## `@Value` field injection row: superseded count, moved 2026-09-01

- [ ] `@Value` field injection: **6 sites, re-derived mechanically 2026-09-01; the recorded 9 is dead.** `AwsClientConfig:29`, `:32`, `:35` (feeding `@Bean` methods), `CollectionControllerProd:56`, `ShareControllerProd:45`, `DownloadUrlService:54`. Every other `@Value` in `src/main` is already a constructor parameter. **The "six in `S3Config` and `SesConfig`" half of the old count no longer exists and the "they fold into MR 16 #4" instruction is spent**: MR 16 #4 shipped as [#261](https://github.com/themancalledzac/edens.zac.backend/pull/261) / [#262](https://github.com/themancalledzac/edens.zac.backend/pull/262), merging those two classes into `AwsClientConfig`, which field-injects three, and `SesConfig`'s went to `EmailService`'s constructor (`:52`). Move to constructor parameters, following the `WebAuthnController` pattern. Test coupling is exactly **five** `ReflectionTestUtils.setField` calls (re-measured 2026-08-29; `CollectionControllerProdTest` has two). Also `@Autowired` on constructors at **five** classes now: `AuthLoginLimiter`, `ClientGalleryAccessLimiter`, `ShareEmailLimiter` (added by #233, same two-ctor shape), `WebAuthnChallengeStore`, `WebAuthnService`. **The real size is 1 deletion and 4 javadoc notes**: only `AuthLoginLimiter` has a single constructor; the other four genuinely have two, where the second is the package-private test constructor, so `@Autowired` is load-bearing. Fifteen minutes.

## `AdminUserController` row: prior figures, moved 2026-09-01

- [ ] `AdminUserController` is a service wearing a controller's clothes: **RE-MEASURED 2026-09-01 (ninth close-out) and three of its four numbers had rotted** -- **three** repositories (`AppUserRepository`, `RoleRepository`, `WebAuthnCredentialRepository`), **seven** services, **one sibling controller** (`AdminRoleController`, injected by [#285](https://github.com/themancalledzac/edens.zac.backend/pull/285)) and a `frontendBaseUrl` -- **twelve injected fields, not the ten this row claimed**. **614** lines (`wc -l`), not 601; the 601 was correct before #285 and has been stale since, sitting outside the neighbourhood of everything that merged for three close-outs. Its test is **1,510** lines (`wc -l`, 1,485 before this run's [#297](https://github.com/themancalledzac/edens.zac.backend/pull/297) added 25), not the recorded **1,294** -- the hidden half is now larger than the row's whole estimate. Prior figures: **601** lines (469 -> 474 -> 481 -> 520 -> 523 -> 601, the last +78 across #257 and #265; **only 12 of those 78 are #265's**, the rest predate this run), entity building, multi-step `@Transactional` orchestration, afterCommit hooks. Extract an `AdminUserService`. **Largest real cost in Wave 7**: ~200 source lines move, and the test file is the larger half. *(This paragraph carried "`AdminUserControllerTest` is **1,462** lines" alongside the **1,510** three sentences above it, so the row stated three different sizes for one file. The 1,462 sentence and its growth history are deleted 2026-09-01. The argument is that this class is edited constantly, so the extraction cost rises; that survives without six historical line counts. **Do not re-record the history -- re-measure with `wc -l` when you scope the extraction.**)* **The 1,294 recorded here was already wrong when it was written**: the file last changed in #241 on 2026-08-30, before that day's close-out, and the close-out did not re-measure it. Nothing this session touched the file -- this is a number that rotted on its own, outside the neighbourhood of anything that merged, which is the case a scoped drift sweep cannot catch and is the hidden half.

## `ContentModels.Image` positional row: prior counts, moved 2026-09-01

- [ ] `new ContentModels.Image(` with 31 positional components appears in **11** test files (**14** test call sites, RE-RUN 2026-09-01 (ninth close-out) as `git grep -o 'new ContentModels.Image(' -- 'src/test' | wc -l`; was 13, and the file count of 11 holds. There are also **2** sites in `src/main` -- `AdminHomeService` and `ContentModelConverter` -- which are outside this item's scope and are not counted in the 14; quote the test-only figure or say which you mean, per **rule 31**.) *(prior text: 13 call sites)*, **7** of which have their own private helper *(unreproducible: a grep for a private `Image`-returning helper across the 11 files finds 8, and the row never says what counts as one. Give the figure a command or drop it)*. Same for `CollectionRequests.Update` -- **corrected 2026-08-29: the canonical record has 22 components** (`parents` is the 22nd; the compat docblock's "all five set to null" checks out, 22 - 5 = 17), and the deletion target is the **17**-arg compat constructor at its **22** test call sites -- **26 `Update` constructions in all: 22 compat plus 3 canonical in `src/test`, plus one canonical in `src/main` (`CollaboratorRequests.java:43`)**, re-derived 2026-09-01 on `main` at `43c6f2c6`. **This entry said 21 and asserted 21 was right against the "Positional constructors" row below, which says 22. The row below is the maintained copy and it is correct; quote it rather than this line.** [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291) added the 22nd site on 2026-09-01 and that row was corrected while this one was not, so the reconciliation sentence pointed the next reader at the wrong entry for a run. One `TestFixtures` class with builders. **Re-measured 2026-09-01 by walking each construction to its balanced closing paren: 767 lines of positional construction across 17 test files** -- `ContentModels.Image` 364 lines over 14 sites in 11 files, `CollectionRequests.Update` 403 lines over 25 sites in 8 files. **The recorded 745 had no stated method, so it cannot be said whether it was ever right; 767 is the figure with a method behind it.** The "~120 replacement" is optimistic: 39 builder call sites at a realistic 4-6 lines each is ~195, plus a `TestFixtures` class covering a 31-component record and a 22-component record at ~200. **Net ~-370, not ~-600.** It is still the largest single deletion available, **but price it by rule 48**: the win is one place that knows the 31-component shape instead of 39 sites, not the line delta. The -600 claim was doing load-bearing work it cannot support.

## `CollectionRequests.Update` row: prior text, moved 2026-09-01

- [ ] `model/CollectionRequests.java` -- 17-arg `Update` constructor, **22** test call sites. **RE-RUN 2026-09-01 (ninth close-out) with the same paren-balanced arity scanner and it HOLDS EXACTLY**: 25 raw in test = 22 at arity 17, 3 at arity 22, plus 1 arity-22 site in `src/main` (`CollaboratorRequests`). **A correction to this run's own reporting, and it is rule 31 caught in the act.** [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296)'s body filed this as drifted -- "26 sites across 9 files, not 25" -- because it counted `src/main` **and** `src/test` against a figure that is test-only. Nothing had drifted; the two passes ran different commands. **The row's number was right and the drift finding was wrong**; the fix is that this row now says `-- 'src/test'` out loud. Prior text: **RE-DERIVED 2026-09-01 (eighth close-out) with the paren-balanced arity scanner, not a grep**: 25 raw in test = **22 at arity 17** + 3 canonical at arity 22; 1 raw in main at arity 22 (`CollaboratorRequests.java:43`), so still zero `src/main` callers of the 17-arg form. **Eight files.** `CollectionServiceTest` still carries **8 of the 22** -- a numerator that has now held across four re-derivations.

## `DownloadResolution.extension` row: prior text, moved 2026-09-01

- [ ] `model/DownloadResolution.java` -- the `extension` component. **PRIORITY FLAG, added 2026-08-31 (third run): this is the most expensive of the four, not the cheapest, and its "0 main / 6 test" headline reads like a free delete.** Deleting the accessor means deleting the record component, which takes the canonical constructor from 4 args to 3, so every construction site changes too. **13 edits across 5 files, 2 of those edits in `src/main`** (both in `ContentService`; it is 2 edits, **1 file** -- the old wording read as 2 files. **Re-derived and reproduced exactly 2026-08-31, fourth run**, with `'\.extension\s*\('` escaped, and the unescaped control returning the same set so the over-match warning does not bite here) -- 6 accessor sites and 7 construction sites. **All refs RE-DERIVED 2026-08-31 (fifth run) after [#271](https://github.com/themancalledzac/edens.zac.backend/pull/271) rewrote both files this item lives in; the counts held and 7 of the 13 refs moved.** Accessors, `ContentServiceDownloadTest`: **88, 102, 201, 217, 237, 239** (were 96, 110, 210, 225, 240, 242 -- all six drifted, #272's sibling sweep and #271's arity edits both shifted this file). Construction: `ContentService:781` (**unchanged**) and `ContentService:835` (**was `:851`, -16 -- #271 deleted the 15-line 2-arg overload plus one docblock line above it**); `ContentDownloadAuthTest:94`, `ContentDownloadControllerProdTest:71` and `:75`, `DownloadUrlServiceTest:100` and `:101` (**all five unchanged** -- #271 did not touch those files). **It is the only one of the four that touches `src/main` at all. If MR 25 needs splitting, split this off.** The component does genuinely carry no main-side behavior: `DownloadUrlService` consumes `List<DownloadResolution>` at 83, 105, 108 and never reads `extension`, and there are zero `.extension()` calls anywhere in `src/main`. Prior text: **5** construction sites in test (not 4), **7 in total** as re-derived 2026-08-25 -- the two in `src/main` are both in `ContentService`

## The coverImage stripping row: background, moved 2026-09-01

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
  should assert, not a comment fix. **Recorded exemption (2026-08-29, working rule 37): this second
  banner is excluded from the rule-37 comment sweep until this decision lands** -- a sweep deleting
  it would erase the record of the open question without anyone seeing this row.

  **The frontend already strips (added 2026-08-29 by the cross-repo review).** FE #327 (2026-08-25)
  shipped a client-side strip on the public card path keyed on `isPasswordProtected === true`,
  deliberately diverging from #209's render-hint intent (cover retained for a locked tile; that tile
  UI is unbuilt). So "list-endpoint stripping is genuinely wanted" would now *duplicate* an
  enforcement the frontend already has -- and #209's cost report already shows a query-level filter
  would empty `all-client-galleries`. Without this line a future session could re-derive "the
  frontend needs the strip", which is the exact C6-shaped failure.

  **What to decide, and it is not obvious.** Either the section is a stale record of a fix that was
  reverted or never landed, in which case delete the banner and rename the test to what it actually
  asserts (controller pass-through); or list-endpoint stripping is genuinely wanted, in which case
  the test is a specification with no implementation and the work is real. Do not resolve this by
  reading the comment. #209's cost report has what implementing it would break.

## #22: PATCH route ref detail, moved 2026-09-01

- [ ] **#22 (feature dependency, not a bug) — `PATCH /api/edit/collections/{id}` does not exist,
  and the frontend's largest open item is blocked on it.** *(Filed 2026-08-31 from the frontend
  board's MA1 (`docs/spikes/2026-features.md` in `edens.zac`), whose row said "Task 1 (backend
  `PATCH /collections/{id}`) was assigned to a sibling agent — verify it exists before starting".
  It was verified and it does not. **Folded into the tracker 2026-08-31 (third run) from
  [#252](https://github.com/themancalledzac/edens.zac.backend/pull/252)**, which had been sitting
  open and unmerged since the first run of that day, so this item existed only inside a PR and was
  invisible to anyone reading the board.)*

  Verified against `origin/main` rather than a checkout — this repo's `.claude/worktrees/` copies
  make an unscoped `grep -rn` return convincing false positives for exactly this query:

  ```bash
  git grep -n "PatchMapping(" origin/main -- 'src/main/java/**/controller/**'
  ```

  **PREMISE CORRECTED 2026-09-01 (tenth-run review). The route is genuinely missing and the
  behavior the frontend needs already exists.**

  **What still holds.** Five `@PatchMapping`s exist and none is a whole-collection field patch:
  `/content/images` (`AdminController:234`, was 233), `/content/gifs/{id}` (`AdminController:308`,
  **was 341**), `/{id}` (`AdminUserController:329`, was 313), `/collections/{collectionId}/rating`
  (`EditController:52`) and `/collections/{collectionId}/images` (`EditController:94`). The last two
  are sub-resource patches. Three of the five refs had drifted; all five re-derived 2026-09-01 on
  `main` at `43c6f2c6`. A live scan of `edens.zac` `origin/main` the same day confirms no bare
  `PATCH /api/edit/collections/{collectionId}` exists.

  **What is false.** The item said a `PUT`-style whole-object update "will not do, because two
  fields edited in parallel must not clobber each other". **Both existing PUT routes already behave
  as partial updates.** `PUT /api/edit/collections/{id}` (`EditController:70`, narrow
  `CollaboratorUpdate`) and `PUT /api/admin/collections/{id}` (`AdminController:112`, full
  `CollectionRequests.Update`) share one write, and every field on that write is null-guarded:
  `CollectionProcessingUtil` 612, 620, 623, 640, 658, 663, 667, 670, 673, 677, 680, 683, 687 and
  `CollectionService` 621, 625, 629. A body of `{id, title}` updates title alone.

  **What is actually missing is two small things.** (a) The `PATCH` verb itself, if the frontend
  needs the verb rather than the behavior -- an alias over the existing handler. (b) Clearing a
  nullable field: null already means "unchanged", which is why `clearCollectionDate` and
  `clearCollectionEndDate` exist as explicit booleans and `coverImageId` treats `0` as clear. A
  per-field commit model that has to clear `description` needs that decision made.

  **Sequencing changed with the premise.** This is no longer MR 1 of MA1 gating eleven frontend
  tasks by construction. **Ask the frontend board first whether pointing `buildFieldPatch` at the
  existing null-guarded `PUT` unblocks MA1**, and whether it needs any admin-only field (slug,
  visibility, isClient, isBlog, people, collections, siblings, parents -- the collaborator tier
  exposes none of them). If the answer to the first is yes, this closes here as a frontend
  documentation fix. **COLD, and it should not be picked up as backend work before that question is
  asked.** It remains the highest-consequence open item on either board and has been available and
  unpicked since 2026-08-31.

## S-29: tracker body as first filed, 2026-09-01

- [ ] **S-29** (MED, possibly HIGH) **`GET /api/read/content/images/search` returns every image in the
  database, with no collection-visibility and no gallery-password filter.** Anonymous: no cookie, no
  header, no session. `SecurityConfig:79-80` matches `/api/read/content/**` against no rule, so it
  falls to `anyRequest().permitAll()`; `ContentControllerProd.searchImages` (`:44`) passes straight
  through `ContentService.searchImages` (`:393`) to `ContentRepository.searchImages` (`:767`), whose
  `SELECT_CONTENT_IMAGE` (`:158`) is `FROM content c JOIN content_image ci ON c.id = ci.id` plus three
  LEFT JOINs. **There is no join to `collection_content` or `collection` and no predicate on
  `collection.visibility` or `collection.gallery_password`.** `appendSearchConditions` (`:812-861`)
  adds only the caller's own filters; `countSearchImages` (`:784`) has the same shape. What comes back
  is `ContentModels.Image` carrying `imageUrl` and `imageUrlRaw` (`model/ContentModels.java:31-32`),
  both unsigned CloudFront URLs (`ImageProcessingService:736` and `:760`) that anyone can fetch.
  **This walks around three gates**: `enforceVisibility` (`CollectionService:1523`), the
  content-stripping at `CollectionControllerProd:80-84`, and `isDownloadAuthorized`
  (`ContentDownloadControllerProd:196-204`). The third is the sharpest contradiction -- that
  controller exists to presign private S3 objects behind a CLIENT-or-cookie check, and this endpoint
  hands out the CloudFront URL for the same image to an anonymous caller. Enumeration is easy: `size`
  is capped at `@Max(200)` with paging, so the table walks in pages of 200, and `personIds` is a
  filter, so with S-30 an attacker picks any user id off the public people list and pulls every photo
  that person is tagged in. **Severity, stated honestly:** MED as filed. HIGH if any client gallery
  holds images not also published elsewhere, which is a data question this repo cannot answer. Closer
  to LOW if every image is public anyway. **Ask before pricing.**
  **Mutation the test must survive:** seed one image whose only `collection_content` row points at a
  CLIENT_GALLERY with a non-null `gallery_password`, assert an anonymous search response does not
  contain that id, then delete the new visibility predicate from `appendSearchConditions` (or invert
  its `NOT EXISTS`) and watch it redden. A test that only counts rows, or that seeds a LISTED image
  and asserts it is present, stays green under that mutation and does not count.

## S-31: tracker body as first filed, 2026-09-01

- [ ] **S-31** (LOW) **a share opt-in is checked when it is added and never again.**
  `UserShareControllerProd.addCollection` (`:167-178`) gates the opt-in on
  `collectionAccessService.canView(principal, collectionId)`. That is the only check; the row lands in
  `share_link_collection` and nothing re-tests it. `ShareLinkRepository.isCollectionInScope`
  (`:170-194`) and `findScopeCollectionIds` (`:145-162`) both resolve scope as `collection_people`
  UNION `share_link_collection`, with no join back to `role_member` / `role_collection`. So: Alice
  holds a role grant on collection X, opts X into her share link, an admin removes her from the role,
  and her link keeps serving X -- `UserPageAssembler.assembleForShare` (`:85-87`) still renders X's
  tile and `CollectionAccessService.effectiveLevel` still resolves GENERAL for the flyby on X. The
  removal side already anticipates revocation: `removeCollection`'s docblock says the delete is
  "deliberately NOT gated on the owner's current grant". **Why LOW.** Every consumer of the flyby's
  GENERAL was traced and it is inert: `CollaboratorAccessInterceptor` needs COLLABORATOR,
  `viewerMaySeeHidden` (`CollectionService:1537`) rejects a null `userId`, and `CollectionService:582`,
  `UserSelectsService:87`, `UserRatingOverrideService:41-42`, `ContentDownloadControllerProd:196-200`
  and `isGalleryAccessAuthorized` (`CollectionService:575-584`) all screen with `isRealUser` first.
  The exposure is the tile metadata on the share page, not content access.
  **This may close on S-14's reasoning rather than be patched.** S-14 answered the neighbouring
  question -- an admin widening their own share scope -- with "answered, not patched: no second gate".
  If the same disposition applies here, record it as answered. It has not been asked, which is the
  only reason it is a row.
  **Mutation if patched:** grant, opt in, revoke the grant, assert the collection is absent from
  `findScopeCollectionIds` and false from `isCollectionInScope`, then drop the new `role_member` join
  from the `share_link_collection` arm of both queries.

## `CollectionRequests.Update`: per-site refs, moved 2026-09-01

  **STOP RECORDING PER-SITE LINE NUMBERS FOR THIS FILE (rule 5, applied 2026-09-01).** All nine refs this row carried drifted -43 to -46 within one run, because [#296](https://github.com/themancalledzac/edens.zac.backend/pull/296) removed 46 lines from the file *after* the eighth close-out wrote them -- and the ninth close-out then stamped this row "it HOLDS EXACTLY" about the counts without re-deriving the refs. `:294` now lands on a blank line. **This is the third consecutive run in which this row's counts held and its line numbers did not, and it is the single worst-drifting ref set on the board.** Derive them by name instead:

  ```
  git grep -n 'new CollectionRequests.Update(' -- src/test/java/edens/zac/portfolio/backend/services/CollectionServiceTest.java
  ```

  For the record only, at `43c6f2c6`: arity-17 at `:248, :299, :329, :382, :436, :2155, :2546, :2658` and the arity-22 site at `:2251`. **Do not restamp these next run -- re-run the command.**

  **The +1 is `CollectionLinkOrderIndexIntegrationTest.java:47`, a new file added by [#291](https://github.com/themancalledzac/edens.zac.backend/pull/291) this run.** That is the second time this item's own count has moved because a new test was written somewhere else. **Its size is a moving target that grows with test coverage**, so re-run the scanner rather than quoting the number, and record the method beside whatever you measure:

  ```
  python3 arity2.py 'new\s+CollectionRequests\.Update\s*\(' src/test/java
  ```

  **`arity2.py` is not in this repo** (`ls arity2.py` returns not found, and it is not tracked), so
  every session that needs an arity count writes it again first. The method is described above and the
  numbers are reproducible, but the recorded command is not runnable as written -- rule 31's spirit.
  **Either commit the scanner or stop quoting it as a command.**

  **CLASSIFIED 2026-09-01: BLOCKED (ordering) on the `TestFixtures` pass, not COLD.** The board listed it as COLD-with-a-guardrail, which makes it look pickable when picking it alone is the wrong move: deleting the compat constructor pushes all 22 sites to the 22-arg form with five explicit nulls each, and the builder pass then rewrites the same 22 again. A dependency on another board item is the ordering bucket. **And the dependency is narrower than "the `TestFixtures` pass"**: the two MR 25 targets barely overlap -- `ContentModels.Image` has 14 arity-31 sites across 11 files, and `CollectionServiceTest` is the only file in both lists, at unrelated call sites (Image at 784, 826, 1004). **If `TestFixtures` is ever split, this rides with the `Update` half only.** It also owns the two
  `/* collections */` and `/* siblings */` positional argument labels in
  `CollectionServiceTest.updateWithSiblings`, which shortening the constructor removes.

## `FileEntry` 3-arg constructor: tracker body moved 2026-09-01

- [x] `model/DiskUploadRequest.java` -- 3-arg `FileEntry` constructor. **DONE** ([#267](https://github.com/themancalledzac/edens.zac.backend/pull/267), 2026-08-31). **Every number re-derived on the day and every one reproduced**: 13 three-arg (10 `ImageUploadPipelineServiceTest`, 3 `AdminControllerTest`) and 15 canonical (13 + 2 in the same two files), 28 total, zero in `src/main` at any arity. The arity-scanner method the board wrote down works and is worth keeping for the remaining three. **One thing the item asserted was untested rather than false**: "no API-contract effect" rests on Jackson binding a record through its canonical constructor, and **no test anywhere deserialized a `FileEntry`**, so the delete rested on an assumption. `DiskUploadRequestWireTest` now pins it from both directions. Write-up in [history](2026-08-22-backend-cleanup-history.md#mr-25-fileentry-outcome-2026-08-31----the-counts-held-and-an-untested-premise-turned-up).

## `resolveCollectionDownloadEntries` overload: tracker body moved 2026-09-01

- [x] `services/ContentService.java` — `resolveCollectionDownloadEntries` 2-arg overload. **DONE** ([#271](https://github.com/themancalledzac/edens.zac.backend/pull/271), 2026-08-31). All 5 counts reproduced before the edit; the 5 two-arg sites now pass `null` explicitly and the 4 three-arg sites in the same file were left alone. Selected by arity, per the guardrail. **One cost the item did not name**: the 3-arg docblock cross-referenced "the 2-arg overload", so deleting the overload made that a dangling reference and it went too. [Write-up](2026-08-22-backend-cleanup-history.md#mr-25s-resolvecollectiondownloadentries-2-arg-overload-271).

## Positional constructors: sizing note, long form moved 2026-09-01

**Sizing note added 2026-08-24 from #209, and it applies to every item that adds a field to a

## #29: tracker body, long form moved 2026-09-01

- [ ] **#29 (dead annotation) — `ContentControllerProd`'s `@Validated` now has nothing to enforce.**
  *(Filed 2026-09-01, ninth run, out of #27's audit and [#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)'s
  landing. Numbered in the #22-#28 series so it is greppable; it opens `**#29` and so moves neither
  ledger gate.)*

  `@Validated` on a controller class exists to build the AOP proxy that enforces constraints on
  **method parameters**. #294 moved `searchImages`'s `page` and `size` into `ImageSearchFilter`,
  where `@Valid` on the `@ModelAttribute` enforces them through the `WebDataBinder` instead. The
  class now carries **zero** constraint-annotated method parameters -- verified by the #27 audit,
  which found it was the only such class in the repo before #294 and none after -- so the annotation
  builds a proxy for nothing.

  **This is the "a controller whose constraints turn out to be unreachable is a finding, not a test
  to write" case #27's guardrail anticipated, arriving from the other direction**: not a constraint
  that could never fire, but a proxy with no constraint left to fire.

  Scope: delete the annotation and its import, or keep it and say in the class docblock why. **COLD**,
  and roughly two lines. **Guardrail:** `ContentControllerProd` is the only `@Validated` in the repo,
  so there is no pattern to preserve and nothing else to sweep -- do not go looking for siblings.
  **RE-VERIFIED EXACT 2026-09-01 (tenth-run review), by two slices independently.** `@Validated` at
  `ContentControllerProd.java:29`, its import at `:18`. `git grep -rn '@Validated' -- src/main src/test`
  returns exactly two hits repo-wide: that annotation and a docblock mention at
  `GlobalExceptionHandler.java:142`. **Across the whole `controller/` package, zero method parameters
  carry a constraint annotation** -- every survivor sits on a record component of a `@Valid @RequestBody`
  or `@ModelAttribute` DTO. `ContentControllerProd` has seven endpoint methods and only `searchImages`
  (`:44`) takes a parameter at all: `@Valid @ModelAttribute ImageSearchFilter filter` (`:45`), whose
  `@Min`/`@Max` live on `ImageSearchFilter:26-27` and go through the `WebDataBinder`, not the proxy.

  **One rider, and it is exactly the docblock-upkeep rule.** `GlobalExceptionHandler.java:142` reads
  "Handle constraint violations from `@Validated` on path/query parameters." Deleting the annotation
  makes that sentence describe nothing, so it is a third line in the diff. **Do not delete the handler
  on that basis** -- it stays reachable, `GlobalExceptionHandlerTest:74` throws
  `ConstraintViolationException` directly, and whether anything else can throw it was not checked.
  **Three lines, zero test churn, and the cheapest open item on the board.**

## Appendix C job-status lead: long form moved 2026-09-01

- [ ] **The image-upload job-status endpoint may be entirely dead.** *(New lead 2026-08-24, found
  while answering the `JobStatus` question.)* `POST /content/images/{id}/from-disk` returns 202 with
  a `jobId` "for polling", and `GET /api/admin/content/images/jobs/{jobId}` serves the status -- but
  the frontend never calls either. Zero hits for `jobId`, `jobs/` or `from-disk` across its `app/`
  tree. If the disk-import flow is admin-CLI-only, the whole `JobTrackingService` surface plus its
  ~45 test references may be dead weight. Confirm how disk import is actually triggered before
  acting; this is the kind of "nobody calls it" claim that is wrong when a human uses curl.
  **The frontend half is now confirmed against a live clone (2026-09-01): `jobs/{jobId}` and
  `from-disk` have no caller anywhere in `edens.zac` `origin/main`.** What remains unanswered is the
  same thing it always was -- how disk import is actually triggered. **The "~45 test references"
  figure is 39 across three files** (`ImageUploadPipelineServiceTest` 28, `AdminControllerTest` 6,
  `JobTrackingServiceTest` 5); the recorded file list missed the third.

## Stale side branches: tenth-run re-run, long form moved 2026-09-01

**Re-run 2026-09-01 (tenth run). This section had been maintained as a fixed list of six rather than
re-run, and three things about it were wrong.**

**One: there are ten worktrees, not six.** `git worktree list` returns eleven rows -- the main
checkout plus five under `edens.zac.backend.worktrees/` (`0217-user-upgrade-be`,
`0257-backend-security-bugs`, `0392-sd7-people`, `collection-debloat`, `log-review-followups`) and
five under `.claude/worktrees/` (`agent-a6284b71d0e38254c`, `agent-af43dc86deca4305f`,
`auth-password-reset`, `one-way-siblings`, `pr281`). **Four were created after 2026-08-24, all four
for work that has since merged, and none reached this board** -- which is what this section exists to
catch.

**Two: the recorded measuring command fails on half its rows.** `git ls-remote --heads origin` lists
no `claude/auth-password-reset`, `claude/one-way-collection-associations`, `0217-user-upgrade-be` or
`chore/log-review-followups`; those exist only as local branches. So
`git rev-list --left-right --count origin/main...origin/<branch>` **errors out** on four of the eight
branches tracked here, and any re-run that reported numbers for them was reading something else.
**Measure against local refs: `git rev-list --left-right --count main...<branch>`.**

**Three: zero open PRs.** `gh pr list --state open` returns nothing. Every PR named in this section is
merged, #252 included.

Re-measured at `43c6f2c6` (behind / ahead): `feat/collection-debloat` **221 / 0**,
`claude/auth-password-reset` **142 / 0**, `claude/one-way-collection-associations` **142 / 0**,
`0359-fe-ma1-collection-patch` **49 / 1**, `0257-backend-security-bugs` **133 / 1**,
`0217-user-upgrade-be` **288 / 1**, `chore/log-review-followups` **251 / 1**,
`fix/s18-actuator-exclude` **65 / 3**. **Every "0 ahead" verdict holds.** Only the behind-counts
moved, and those are not worth re-recording -- the "147-184 commits behind" figures below are dropped
rather than restamped for that reason.


## Drop the orphan images array: long form moved 2026-09-01

- [ ] **Drop the orphan `images` array from `GET /api/read/collections/location/{slug}`.**
  *(Filed 2026-09-01, tenth run, out of the answered BE-2 decision. The decision is recorded under
  [Decisions needed from the user](2026-08-22-backend-cleanup-spike.md#decisions-needed-from-the-user); this row is the work.)*

  **Scope.** Delete `images` from `LocationPageResponse`, and with it `batchConvertOrphans`,
  `ContentRepository.findOrphanContentByLocationName` (`:440`),
  `countOrphanContentByLocationName` (`:461`) and their tests. **Keep `totalImages`** -- it is one
  cheap COUNT and the only piece of the orphan half with a plausible use. `imagePage` and `imageSize`
  become dead request parameters and go too.

  **Measured cost of leaving it, on `main` at `43c6f2c6`.** One location page load issues roughly 15
  SQL queries and **seven of them exist only to build this array**: the orphan id + `sort_date` query,
  the `findAllByIds` content-type lookup, the per-type SELECT, the count, and the tags / people /
  locations batch lookups for the 50 images. If any content at the location is a GIF, the partition in
  `batchConvertOrphans` runs the second batch converter too and it becomes **11 of ~19**. Serialization
  is 50 `ContentModels.Image` records at roughly 600-1200 bytes of JSON each -- **30-60 KB per
  response**, generated and transferred and dropped on the floor.

  **The honest caveat on the size of the win.** `getCollectionsByLocation` is fetched with
  `next: { revalidate: TIMING.revalidateCache }` (`app/lib/api/collections.ts:158`), so Next.js
  ISR-caches it. **This cost is paid per cache revalidation, not per visitor.** The performance
  argument is a modest win. **The stronger argument is contract hygiene**: a public response field
  that no client reads is a field nobody can safely change later, and this one has already caused two
  rounds of cross-repo confusion -- #258 widened it, the widening triggered FE-1, FE-1 triggered a
  premise correction, and that triggered BE-2.

  **It is a breaking wire change for any client outside this repo. There are none** -- verified by a
  live scan of `edens.zac` `origin/main` on 2026-09-01. **COLD.**

## #294 page-size debt: long form moved 2026-09-01

- [ ] **[#294](https://github.com/themancalledzac/edens.zac.backend/pull/294)'s page-size default,
  30 -> 50 -- REAL, and owed to `edens.zac`.** *(Filed here 2026-09-01, tenth run.)*
  `GET /api/read/content/images/search` now defaults `size` to 50 (`ImageSearchFilter.DEFAULT_SIZE`,
  `@Min(1) @Max(200)`). **Two public pages pass no `size` and now silently show 67% more photos**:
  `app/location/[slug]/page.tsx:82` (`searchImages({ locationId })`) and `app/tag/[slug]/page.tsx:48`
  (`searchImages({ tagIds })`). Nothing crashes -- both render whatever array arrives -- but it is a
  visible product change on public routes, shipped without the frontend being told.
  `app/search/SearchResults.tsx:12` passes `size: SEARCH_RESULT_LIMIT` and is unaffected.
  **What the frontend has to decide:** whether 50 is the wanted grid size on those two pages. If yes,
  nothing changes but the debt should still be recorded. If no, pass an explicit `size: 30` at both
  call sites and stop depending on a backend default. Two riders worth carrying: `SEARCH_RESULT_LIMIT`
  is exactly **200** (`app/components/SearchPage/searchFilters.ts:13`), sitting on the new inclusive
  `@Max(200)`, so one bump to 201 turns `/search` into a 400; and #294 deleted admin's
  `Math.min(Math.max(size, 1), 200)` clamp, so any admin caller passing `size > 200` now gets a 400
  instead of 200 rows (no current caller does).

## S-30: tracker body as first filed, 2026-09-01

- [ ] **S-30** (LOW) **`GET /api/read/content/people` lists every row in `users`, not every tagged
  person.** `MetadataService.getAllPeople` (`:112`) calls
  `PersonRepository.findAllByOrderByPersonNameAsc` (`:49-52`), which is literally
  `SELECT id, name, created_at FROM users ORDER BY name ASC` -- no `status` predicate and no
  "is tagged in anything" predicate. Since V35 merged people and accounts into one table, this
  returns the id and display name of every account on the system: admins, collaborators, clients,
  INVITED accounts that never onboarded, DISABLED accounts, alongside the tag-only PERSON rows the
  route was built for. The route is `ContentControllerProd.getAllPeople:67`, anonymous under
  `permitAll`, and it is in `CacheControlInterceptor.PUBLIC_ROUTES` (`config/CacheControlInterceptor.java:65`),
  so it is shared-cacheable too. Email is not exposed; the account roster by name is, plus each
  `users.id` -- the same id that is the `{id}` path variable on `/api/admin/users/**` and the
  `personIds` filter on S-29. **It is also a functional bug**: an account never tagged in a photo
  appears in the tag-filter dropdown and returns zero results.
  **Mutation:** seed a DISABLED account with no `collection_people` and no `content_image_people` row,
  assert it is absent from the response, then drop the new `WHERE` clause and watch it redden. A test
  that only asserts a tagged PERSON is present stays green and does not count.

## What the closed security set is worth carrying forward: long form moved 2026-09-01

**What the closed set is worth carrying forward** *(tracker text, superseded)*

*(Retitled 2026-09-01. This heading read "Classification of the still-open items" and its lead
classified S-28, which shipped in the sixth run -- so it described an empty set for four close-outs
while the `### Open` heading directly above it said so. The three findings filed 2026-09-01 are
classified in their own rows above.)*

*(The claim "the section is EMPTY" stood here and in the Progress row for one session while the
section held four open checkboxes. They were the "Unsettled" questions, which do not open with
`**S-` and so were invisible to the rule-36 gate that both claims quoted. They now have their own
section and their own gate. **The lesson is the gate, not the wording**: a summary claim about a
section must be measurable by the command it cites, or it will drift the moment something is filed
in a shape the command cannot see.)*

The table is kept
rather than deleted because the section has refilled three times now, and its shape is the thing that
made the two blocked items answerable: each row named the question and who answers it, in the form
the user could act on. Both were answered within a day of being written that way.

The last five rows, and what each closed as, are the ledger lines above. The DONE rows of the
2026-08-25 table moved to history 2026-08-29 with the bodies; the 2026-08-31 rows moved with theirs.

**One thing worth carrying forward past the section being empty.** S-14's answer did not fit either
option the question offered -- it was a principle ("all admin endpoints through the same gate")
about a different endpoint class than the one the item was about. It was recorded as closed **with
the gap named**, rather than forced into "allow, documented". A question that comes back with an
answer to a slightly different question is still an answer; write down which question it answered.

*(Tests that cannot fail closed 2026-08-30 -- all six. Full write-ups, mutation results and the two
premise corrections:
[history](2026-08-22-backend-cleanup-history.md#tests-that-cannot-fail--closed-2026-08-30-moved-from-the-tracker).)*


## S-16's reachability claim: the four checks, moved 2026-09-01

- **S-16's reachability claim HOLDS -- re-tested 2026-08-31 with #257's endpoints in the tree. Do not
  re-derive this.** #253 shipped one gate instead of the two the item specified, resting on the claim
  that `resolveByRawToken` is the only way a token becomes a link. Four checks, all clean.
  **Producers of `AuthPrincipal.shareId`**: exactly one, `FlybySessionFilter.java:64-74`;
  `AuthPrincipal.flyby` has no other `src/main` caller. **Callers of `resolveByRawToken`**: two,
  unchanged — `FlybySessionFilter:65` and `ShareControllerProd:59`; #257 added none. **Consumers of a
  shareId**: `CollectionAccessService:93-94`, `ShareControllerProd:81`, `UserPageAssembler:86`, all
  taking a shareId the filter or the exchange route already produced through the gate. **The bypass
  looked for and not found**: `ShareLinkService.findById` does turn an id into a link without checking
  owner status, but its only caller is `ShareControllerProd.currentView`, which reads
  `principal.shareId()` — and no route accepts a share-link id as input. The principal cannot be
  replayed either: `SecurityConfig:57-58` is `SessionCreationPolicy.STATELESS` and `FlybySessionFilter`
  writes through `SecurityContextHolder.setContext`, not a `SecurityContextRepository`, so every
  request carrying `ezac_flyby` re-enters `resolveByRawToken` and re-reads `users.status`. And no
  shared cache is a back door: `CacheControlInterceptor.PUBLIC_ROUTES` is default-deny and excludes
  both `/api/read/share/**` routes. **#257 does not touch the share path at all** — no new
  `share_link` reader, no new principal producer, no route taking a share-link id.


## Next run: carried-forward paragraph, moved 2026-09-01

**Carried forward, and the leak detector's reading corrected.** MR 18 #13's split and MR 19 #17 were
named in the `Next:` of the sixth, eighth and ninth runs -- three of the last four, but not three
consecutive, because the seventh close-out shipped no code and its `Next:` was the eighth run's list.
The board's "carried forward unchanged for the second time" counted only the consecutive pair. **MR 19
#17 is scheduled above and MR 18 #13 is now genuinely blocked, so neither is being avoided.**

## Cross-repo section: filing history, moved 2026-09-01

The 2026-08-24 batch closed and lives in
[history](2026-08-22-backend-cleanup-history.md#cross-repo-findings-owed-to-the-frontend). This
section was re-opened by #258 and re-derived 2026-08-31 (third run) by a full pair scan: every
endpoint path literal in `edens.zac`'s `app/lib/api/*.ts` compared against every
`@RequestMapping`/`@*Mapping` pair under `controller/`. **No frontend call site targets a backend
route that no longer exists**, so nothing here is a live 404. The five below are type drift and one
dev-workflow change.

**All five are filed in `edens.zac` as of 2026-08-31 (third run):**
[#371](https://github.com/themancalledzac/edens.zac/pull/371), docs-only, **merged 2026-08-31**. Four are new rows on
that board (C14, C15, C16, H7); the fifth had already shipped there as G6 (#351), so it was recorded
under that board's "do not re-investigate" list instead of being filed twice. **This closes the gap
the second run declared and could not close** -- the row then read "deliberately NOT filed ... so it
is invisible in `edens.zac` until someone files it", because that repo had another session's dirty
branch checked out. It was dirty again this run; the filing was done in a temporary worktree off
`origin/main` without touching that session's tree. **#371 has since merged**, so these rows are
filed on both boards and the gap is closed; they stay open here until the frontend acts on them.


## Cross-repo section: the stale STILL-NOT-FILED paragraph, moved 2026-09-01

*(A paragraph headed "STILL NOT FILED ON THE FRONTEND BOARD" sat here from the second run until
2026-09-01, contradicting the paragraph seventy lines above it and reading as the section's
conclusion because it was last. It was stale. **All five rows exist on `docs/spikes/2026-summer-refactor.md`
in `edens.zac` `origin/main`, verified line by line 2026-09-01: C14 (FE-2), C15 (FE-1), C16 (FE-3),
H7 (FE-4) and G6 / PR #351 (FE-5).** The paragraph was telling the next session to redo work already
done, in a repo the board also believed was not on this machine.)*


## Open security findings: section preamble, long form moved 2026-09-01

Consolidated 2026-08-24 by the full-board review; re-attacked as a merged set 2026-08-25, again
2026-08-29 (adversarial -- 0 HIGH, 0 MEDIUM) and again 2026-08-31 (third run, the full-board
review's security slice). **The 2026-08-31 pass refilled the section: 1 HIGH, 0 MEDIUM, 2 LOW.**
It held S-22 (#247), S-23 (#248), S-14/S-24 (#250), S-16 (#253) and the new passkey deregistration
endpoints (#257) together as one set, which is how S-26 was found — it is only a HIGH because #257
removed the compensating control S-15 was measured against. The section was empty for exactly one
session. **Twenty-seven closed** (S-1 through S-28; **S-25 was never assigned** and appears nowhere in either board file, which is the gap behind the "28 findings" this board has quoted): one ledger line each below; bodies, outcomes and
the 2026-08-25 "reopened" context are in the history file
([Security findings -- closed](2026-08-22-backend-cleanup-history.md#security-findings--closed-moved-2026-08-29)).
Per-path limiter mapping context -- which limiter covers which route -- sits in history's
[S-17 outcome](2026-08-22-backend-cleanup-history.md#s-17-outcome-2026-08-28----not-as-specified-and-two-failures-of-the-same-kind).

**The unsettled questions no longer live in this section.** They moved 2026-08-31 to their own
[Unsettled security questions](2026-08-22-backend-cleanup-spike.md#unsettled-security-questions) section with their own gate, because
four open checkboxes sat here while this section's row and classification both said "empty" — the
rule-36 gate greps `^- \[ \] \*\*S-` and none of them opened that way.


## Appendix C duplicate image ids: derivation, moved 2026-09-01

- [ ] `updateImages` and duplicate image ids in one request. **PREMISE CORRECTED 2026-09-01 (tenth-run
  review): the recorded finding cannot happen, and the real one is different.** The lead said two
  updates for the same image id "fail before any work happens" because `Collectors.toMap` throws on a
  duplicate key. It cannot throw. `ContentService:135-139` builds `imageIds` from the request with no
  dedupe, so a duplicate id does reach the list -- but `:148` passes it to
  `contentRepository.findImagesByIds`, whose SQL is `... WHERE c.id IN (:ids)`
  (`ContentRepository:290`), and an `IN` list returns **one row per distinct id** however many times
  the id appears. So `imageList` at `:149` carries no duplicates and the `Collectors.toMap` at `:151`
  has no duplicate key to reject.

  **What actually happens is that both updates apply, in request order, last write winning, silently.**
  That may still be worth filing; it is a different finding, and the lead as written would send
  someone hunting an exception that cannot occur. **Re-scope it or strike it -- do not schedule it as
  recorded.** *(One slice reported this lead as "still true, exact" on the strength of the
  `Collectors.toMap` line existing; the correction above traces the `IN` clause and wins on evidence.)*
  The earlier correction still stands: `GlobalExceptionHandler` maps `IllegalStateException` to
  **400**, not 500 (working rule 3). It belongs with bug #17 (same method) whenever that MR happens.

## Appendix C CollectionServiceTest lead: drop note, moved 2026-09-01

*(DROPPED 2026-09-01, eighth close-out, under working rule 5. The lead said `CollectionServiceTest`
"was profiled in parts, not read line-by-line" and carried the original ranges 937-1385 and
1555-2017. The file was **2,893** lines when this lead was dropped and is **2,850** on `main` at `43c6f2c6`; it was rewritten wholesale by
[#289](https://github.com/themancalledzac/edens.zac.backend/pull/289); the ranges name nothing, there
is no symbol to recover them by, and the lead had already been carried three times with an
instruction to drop it. Anything still wanted from that file is covered by the assert/verify twins
item under MR 25.)*


## Appendix D: deletion note, long form moved 2026-09-01

*(**Appendix D deleted 2026-09-01, tenth-run review.** It held one row -- `ml_image_tagging`, a design
doc at `ai_docs/ml_image_tagging_design.md` with 0% implemented and no stubs in `src/` -- unchanged
since it was filed and explicitly zero-cost. Re-verified: `grep -rn "ml_image_tagging\|mlImageTagging" src/`
returns nothing. It tracked no work, no decision and no decay, while occupying a top-level appendix
heading and an open checkbox on a board whose open count is a tracked metric. Recorded here in one
sentence instead: the largest unstarted feature is still `ml_image_tagging`, and it costs nothing
until someone starts it.)*


## `Optional.get()` row: superseded arithmetic note, moved 2026-09-01

  *(Two paragraphs of superseded arithmetic were deleted from this bullet 2026-09-01. The board had
  carried two mutually exclusive counts of the Atomic exclusion -- 11 from the fourth run and "five,
  not eleven" from 2026-08-28 -- and **the more prominent one was the wrong one**, so a reader going
  top to bottom took away "~53, materially larger than recorded" when the truth is 48 and smaller.
  The 2026-08-28 count was wrong because its method counted files importing
  `java.util.concurrent.atomic`, and `ImageUploadPipelineService` reads six such fields through
  record accessors and imports nothing. The pattern those paragraphs taught -- a total can hold while
  its components move -- is working rule 5 and is stated three other places on this board.)*

## `JobStatus.status` row: long form moved 2026-09-01

- [ ] `JobStatus.status` is a stringly-typed field with its states in a trailing comment (`JobTrackingService`). **Split the item**: making it an enum is COLD and non-breaking (Jackson serializes an enum to the same string), but costs **39 test references across three files, re-counted 2026-09-01** -- `ImageUploadPipelineServiceTest` 28, `AdminControllerTest` 6 and **`JobTrackingServiceTest` 5, which the recorded file list misses**; the recorded "~45 across two files" was wrong on both halves. The trailing `// PENDING, PROCESSING, COMPLETED, FAILED` comment at `JobTrackingService:29` is a rule-37 violation on its own and is a one-line fix that does not need the enum decision. Adding `COMPLETED_WITH_ERRORS` instead of flipping a 500-file job to FAILED over one error is
  **UNBLOCKED as of 2026-08-24** -- the check was run and there is no frontend job-status poller at
  all. `jobId`, `JobStatus`, `job.status`, `jobStatus`, `/jobs/` and `from-disk` return zero hits
  across the whole frontend `app/` tree, and no code compares against `'COMPLETED'`/`'FAILED'`/
  `'PROCESSING'`/`'PENDING'`. The backend returns a `jobId` for polling that nobody polls. So the new
  enum value breaks no consumer -- **and the more interesting finding is that the whole job-status
  endpoint may be dead**, which belongs in Appendix C rather than being fixed here.

## Gallery-access save-failure row: long form moved 2026-09-01

- [ ] Route the gallery-access save failure through an exception instead of a `saved()` boolean with a hand-built 400 (`CollectionAdminController`). **This is an undeclared wire change**: today a failure returns 400 with a `GalleryAccessResponse` body; through an exception it returns 400 with `GlobalExceptionHandler.ErrorResponse`. 30 test references across 4 files. **Checked 2026-08-24 and the answer is yes, so this stays
  BLOCKED and is now precisely specified.** `saveGalleryAccess` in the frontend's
  `app/lib/api/collections.ts` reads `result.saved` and `result.reason` straight off the 400 body and
  rethrows as `ApiError(result.reason ?? <fallback>)`. Routing through `GlobalExceptionHandler` would
  return `ErrorResponse` instead, so `saved` and `reason` both come back undefined and the admin UI
  silently degrades to the generic fallback message. **The blocker is a frontend change, and it is
  small**: have the frontend read the `ErrorResponse` shape first, then land the backend change.

## Request record homes: long form moved 2026-09-01

- [ ] Request records have two homes: `RoleRequests`/`UserRequests` (`controller/admin/`) and
  `InviteRequests` (`controller/auth/`) versus `MessageRequests`/`CollectionRequests`/`ContentRequests`/`CollaboratorRequests`
  (`model/`). Move the three strays into `model/`. **The strongest argument for this is not in the
  item**: `UserMergeService` imports `UserRequests` from a controller package, so a service currently
  reaches up into the controller layer. Also unlisted and the same smell: `GrantableLevel` and
  `GrantableLevelValidator` are non-controller types sitting in `controller/admin/`. Eleven files,
  zero net lines.

  **Re-counted 2026-08-27: thirteen files, and the validator smell doubled.** S-13
  ([#227](https://github.com/themancalledzac/edens.zac.backend/pull/227)) added `AccountStatus` and
  `AccountStatusValidator` to `controller/admin/` -- deliberately, because mirroring the
  `GrantableLevel` pair was the right call for that fix and this item is not blocking. But it means
  the package now holds **two** constraint/validator pairs, so what was an aside is a small pattern:
  `controller/admin/` is where bean-validation types live in this codebase, by precedent rather than
  by decision. **Decide that explicitly when this MR runs** -- either a `validation/` package for all
  four, or say in the doc that constraints live beside the requests they constrain. Leaving it
  undecided is how a third pair gets added the same way.

## `AdminUserController` row: positional-ref note moved 2026-09-01

  *Positional refs replaced with names 2026-08-24, per working rule 5 -- this item's range list had drifted twice in two days.* **They were re-added as fresh line numbers anyway, and drifted a third time on 2026-08-27** when #227/#228 landed; that is working rule 26 happening inside the very item that recorded the lesson. **The numbers are now gone for good. Find these by name.** The `@Transactional` orchestration blocks are `createUser`, `regenerateInvite`, `upgradeUser`, `updateUser` and `merge`; the afterCommit hook itself is `sendInviteEmailAfterCommit`, called from the first three.

  **The item is growing faster than it is being done, and the rate is increasing** -- 469 -> 520 main and 1,015 -> 1,294 test across **four** security MRs, all of which edited the exact class this proposes to split. The test file has grown **279 lines, 27%, in four days**. Every one of those MRs was small and correct; the point is that the extraction's cost is set by how often this class is touched, and it is touched constantly. **This is now the strongest do-it-sooner argument on the board.**

## `UserShareControllerProd` row: ref chain moved 2026-09-01

- [ ] Same shape, smaller: `UserShareControllerProd` computes grant and candidate sets inline with a repository. Move it into `ShareLinkService`. **Re-derived 2026-08-24 and de-positionalized**: the old range `124-152` overran the end of a 145-line file. The work is two private methods -- `buildSettings` and `candidateCollections`, the latter
  holding the `memberCollectionIdsForUser` call. **Find them by name.** The 2026-08-24 pass
  "de-positionalized" this by writing fresher numbers (`:116-128`, `:135-144`, `:137`), and
  [#213](https://github.com/themancalledzac/edens.zac.backend/pull/213) invalidated all three the
  same day (working rule 26). **The stamps are gone for good (2026-08-29)** -- and the "227 lines today" clause is deleted
  2026-09-01, because the file is **231** and the clause was the same mistake this row documents
  twice. Find the two methods by name; both resolve, `buildSettings` called from two places and
  `candidateCollections` holding the `memberCollectionIdsForUser` call.

## `Synthetic.blogsOnly` deletion note moved 2026-09-01

*(`Synthetic.blogsOnly` -- **row DELETED 2026-09-01, tenth-run review.** Its premise was flagged FALSE
on 2026-08-25: the catalog has three entries (false, true, false) and the read site is reached by both
a true and a false spec, so the field is not constant there. Both its refs have since drifted -- the
catalog is `44-49`, the read site `99`, the record declaration `155` -- and the false premise was the
only argument for the prescribed fold. Nothing survived: false premise, drifted refs, and a fix with
no reason behind it. The catalog claim itself still holds: `spec.blogsOnly()` is passed through to
`CollectionRepository.findNonEmptyOrderedByVisibilityIn`, which branches on it at `:484`.)*

## Decisions section preamble: long form moved 2026-09-01

Returned to the tracker 2026-08-29: the #236 re-split (`32d2168`) had moved this section into the
history file, breaking the Progress links and the history file's "nothing here is open" rule.

*(BE-2 -- whether the location endpoint should keep serving an `images` array -- was added 2026-08-31
by the third run's cross-repo scan and **answered 2026-09-01: drop the array**. It is the last ticked
bullet before the parked subsection.)*

**Two rows are open and neither is a question for the user.** Both are under
[Parked by decision](2026-08-22-backend-cleanup-spike.md#parked-by-decision--waiting-on-nobody) at the end of this section, so this
section's open count cannot be read as a queue of things waiting on you.

*(Three decisions -- `enforce-authz`, `parseImageDate`, bare-array responses -- were answered and
shipped 2026-08-30 in [#243](https://github.com/themancalledzac/edens.zac.backend/pull/243).
Answers and reasoning:
[history](2026-08-22-backend-cleanup-history.md#decisions-answered-2026-08-30-moved-from-the-tracker).)*


## MR 11 untested-fix row: long form moved 2026-09-01

- [ ] **MR 11's headline security fix is untested.** Moving **five** throw sites to bare
  `RuntimeException` -- 3 in `JdbcUserCredentialRepository` and 2 in `WebAuthnService` -- has zero
  coverage. **Re-derived 2026-09-01: five, not the eight this row claimed, and
  `JdbcUserCredentialRepository` lives in `config/`, not `dao/`.** There is no
  `JdbcUserCredentialRepositoryTest`, and `WebAuthnServiceTest` never touches those messages.

  **One clause the row's wording invites a reader to get wrong.** The protection is the exception
  **type**, not the message text: the ids are still in the messages and still reach the log. A bare
  `RuntimeException` falls to `GlobalExceptionHandler`'s `@ExceptionHandler(Exception.class)`
  (`:196`), which returns a generic 500 with "An unexpected error occurred". **Test the status and
  the body, not the message.** The regression to catch is anyone re-typing these as
  `IllegalArgumentException` or `IllegalStateException`, both of which map to 4xx with the message on
  the wire (rule 3).

---

## #31 -- parents on public reads and the `is_film` backfill (#301)

**`parents` was admin-only.** The inverse join was walked only on the manage path, so the
  frontend's Related section could show curated siblings and nothing else.
  `findAllParentCollectionsByChildId` now takes `listedOnly`, mirroring `findSiblings`. Public
  reads apply **both** gates: `c.visibility = 'LISTED'`, because a HIDDEN or UNLISTED parent is a
  dead link and a disclosure at once, and `cc.visible = true`, because a membership the owner hid
  should not resurface as a parent link. Admin and the three internal callers -- cycle detection,
  the delete-time parent recount, and role-grant propagation, all of which need every parent
  regardless of visibility -- pass `false` and are unchanged.

  **V62 restates two rules the ingest path already enforces**: a film stock implies film, and a
  flagged film body implies film (`resolveFilmCameraDefaults`). It does not infer film from a
  slug -- `-film` in a name is a naming habit, not data. `IS DISTINCT FROM TRUE` rather than
  `= FALSE`, because the column is nullable and "unset" means NULL as often as FALSE.

  **Scope limit, recorded so it is not mistaken for done.** The counts that motivated the item
  (`chamonix-film` 0/5, `vienna-film` 0/5, `gorge-50km-film` 0/7 against `dolomites-film`'s 33/33)
  are repaired only if those images carry a flagged body or a film stock. V23 flags exactly two
  bodies, which is the likely reason dolomites reads 33/33 and the rest read zero. Not verifiable
  this pass -- the local backend was down and those counts are a 2026-08-30 measurement, not a
  current fact. **Re-measure against a live backend after this deploys**; if a third body is
  involved, flagging it is a data call for the owner, not a migration that can guess.

## #29 and the ConstraintViolation handler (2026-09-02)

`#29` closed as scoped: `@Validated` deleted from `ContentControllerProd` along with its import, and
`GlobalExceptionHandler:142`'s docblock rewritten because it named the annotation as the handler's
source. All three refs reproduced exactly.

**The `ConstraintViolationException` handler was deliberately kept, and it is not dead code.** The
board's guardrail said only that `GlobalExceptionHandlerTest:74` throws the exception directly. That
understates the cost. Two live sources remain after `@Validated` is gone:

1. **Hibernate entity validation on flush.** Thirteen entity classes under `entity/` carry Jakarta
   constraint annotations, `spring-boot-starter-validation` is on the compile classpath, and nothing
   sets `jakarta.persistence.validation.mode=none`. Hibernate's `BeanValidationEventListener` is
   therefore active and throws `jakarta.validation.ConstraintViolationException` on flush.
2. **The two tests.** `GlobalExceptionHandlerTest:74` throws it directly and `:184` asserts the 400.

So deleting the handler would send entity-validation failures to the catch-all `handleGeneric` and
mis-report a client error as a **500**, and would redden two tests. The annotation was the dead
thing; the handler was never coupled to it. The rewritten docblock now says which source is live, so
the next reader does not have to re-derive this.

## The seven `PARENT` docblock uses, classified (2026-09-02)

The MR 14 row asked for the list of seven to be worked and marked rather than swept. Done:

**Rewritten -- dead vocabulary, the enum is gone:**

- `CollectionService:114` -- "into a PARENT-shaped model populated with children" -> "into a model
  whose blocks are its child collections".
- `CollectionService:563` -- "a PARENT password" -> "a parent's password".
- `UserPageAssembler:26` -- "a self-only, PARENT-shaped aggregation" -> "a self-only aggregation".
- `UserPageAssembler:38` -- "(PARENT model of `ContentModels.Collection` blocks" -> "(a model whose
  blocks are `ContentModels.Collection`".

**Kept -- the deliberate warning and its setup, per the row's guardrail:**

- `CollectionService:1553` -- "ANY collection (not just a legacy PARENT)". Says "legacy" itself; it
  is the sentence that sets up the warning.
- `CollectionService:1557` and `:1558` -- "keying on `type == PARENT` here would strip every child
  out of a non-PARENT wrapper". This is the warning the closed `filterNonListedChildCollections` row
  decided to keep.

The rule the split follows: a `PARENT` that names the shape of a model is dead vocabulary and gets
rewritten; a `PARENT` that warns against keying on the deleted enum needs the dead name to make
sense and stays.

## The production collation, answered (2026-09-02)

MR 18 #13's blocker. **Production Postgres sorts as `C`.** The Java-versus-SQL disagreement is real,
so the sort split is live work rather than a close-as-no-op.

**The diagnostic the board recommended is broken.** `SELECT datcollate FROM pg_database` returns
`en_US.utf8` on production, which reads as "locale collation, nothing to do". It is wrong, and it is
wrong in the direction that closes the item.

Production is `postgres:16-alpine` (`scripts/ec2-postgres/docker-compose.yml`: no
`POSTGRES_INITDB_ARGS`, no `LANG`/`LC_ALL` override, no `--locale`). The image sets
`LANG=en_US.utf8`, so `initdb` records that string in the catalog -- but musl libc implements no
locale collation, so `strcoll` falls through to `strcmp`. The recorded name and the actual behavior
disagree and no catalog query exposes the gap.

Measured by starting both images and sorting the same mixed-case list
(`'apple','Banana','cherry','Almond','bravo'`, `ORDER BY n ASC`):

| Image | `datcollate` | Actual ordering |
|---|---|---|
| `postgres:16` (Debian, glibc) | `en_US.utf8` | `Almond, apple, Banana, bravo, cherry` |
| `postgres:16-alpine` (**production**) | `en_US.utf8` | `Almond, Banana, apple, bravo, cherry` |
| `postgres:16-alpine`, explicit `COLLATE "C"` | -- | `Almond, Banana, apple, bravo, cherry` |

Alpine's default ordering is byte-for-byte identical to explicit `COLLATE "C"`, and both put every
uppercase name before every lowercase one. Java `compareToIgnoreCase` interleaves them, matching the
Debian row. That is the disagreement MR 18 #13 is about.

**Generalizable, and it is the same failure class as this board's unescaped-`[ ]` grep gates:** to
learn what collation a database actually uses, sort a mixed-case list. Never read `datcollate` -- it
returns a plausible answer for every input.
## MR 25's `DownloadResolution.extension` (2026-09-02)

Closed. The record went from four components to three; the 13 refs the tenth review recorded
reproduced **exactly** -- 6 accessor assertions and 7 construction sites across 5 files, the only
near-term board item with zero ref drift, and it stayed that way.

**The local `extension` variable in `ContentService` is untouched and still load-bearing.** It feeds
`sanitizeFilename` and decides the download filename's extension in both `resolveImageDownload` and
`resolveCollectionDownloadEntries`. Only the record component went. The board's old "written, never
read" phrasing invited exactly the mistake of deleting the logic with the component.

**Coverage was mutation-proved rather than argued.** Two of the six accessor assertions belonged to
tests that already asserted on `.filename()`, so those lines were simply dropped. The other four were
swapped in place to `.filename()`, which discriminates identically because `sanitizeFilename` appends
the extension it is given. For the per-image ZIP fallback test the swap was kept strictly in place --
no restructuring.

The proof: move `extension = ".jpg"` outside the `origUrl != null` branch in
`resolveCollectionDownloadEntries`, which makes the format fallback per-request instead of per-image.
`original_someMissingOriginal_fallsBackToWebPerImage` fails on the swapped `.filename()` assertion.
Restored, the suite is green and `mvn clean install` exits 0.

**Rule this closes:** the guardrail that parked this item for four close-outs -- "4 of its 6 accessor
assertions are the only coverage of the collection-ZIP format fallback" -- was true about *which*
assertions carried the coverage and wrong that the coverage was tied to the *component*. A filename
that ends in the extension carries the same discrimination. Before parking a deletion on a coverage
guardrail, check whether an adjacent field already witnesses the same behavior.

### The positional-constructor preamble, collapsed 2026-09-02

The "do them in the SAME pass as the `TestFixtures` builders" paragraph is retired: all three members
it argued did **not** need to ride with the fixtures have now shipped standalone, which is the
paragraph's own claim confirmed -- `FileEntry` (#267), `resolveCollectionDownloadEntries` (#271) and
`DownloadResolution.extension` (2026-09-02). Only `CollectionRequests.Update` remains, and it is the
one member the paragraph said must ride with the builders, because its 17-arg sites are precisely the
sites a builder collapses and doing them separately rewrites the same 22 sites twice. That reasoning
now lives in the `CollectionRequests.Update` row itself, so the preamble was one line on the board.
