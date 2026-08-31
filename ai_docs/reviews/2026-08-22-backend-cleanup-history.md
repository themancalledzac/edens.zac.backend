# Backend cleanup — completed work

Closed-out detail split out of [`2026-08-22-backend-cleanup-spike.md`](2026-08-22-backend-cleanup-spike.md)
on 2026-08-23 and grown by every close-out since. **Nothing here is open** -- true again as of
2026-08-29: the `32d2168` re-split had misfiled the open "Decisions needed" and "Stale side
branches" sections here for a day, and both are back in the tracker, along with Appendices C and D.
This file holds: Waves 1-3 and every closed MR and security outcome, the closed security findings'
tracker bodies (moved 2026-08-29), the closed cross-repo board, Appendices A and B, the full-board
review reports, the working rules' original narratives, and the session-log archive. The tracker
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

Against `ai_docs/reviews/2026-07-25-open-pr-review.md`, backend items: 25 landed, 10 moot (superseded by the typeless phase-2 merge), **1 partial, 2 still open** (recounted 2026-08-24 against the itemization below, which never supported "2 partial, 3 open").

- Still open: C7 partial indexes on `is_blog`/`is_client` (explicitly optional). **C8 is now closed** -- the policy is recorded under "Decisions needed". **`CollectionControllerDevTest` naming drift is closed too**: MR 4 shipped as [#164](https://github.com/themancalledzac/edens.zac.backend/pull/164) and no `*DevTest*` file exists anywhere in the tree.
- Partial: `CollectionList.fromSibling` exists, but two positional construction sites remain (`CollectionRepository`, `CollectionService` -- verified still true, line refs dropped per working rule 5). Related, found 2026-08-24: `fromSibling` passes `null` for `collectionDate` by construction, which is the one place that field is still null on the wire.
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


# Session log archive — entries moved 2026-08-31

Oldest first. **The tracker keeps the current session's entries and moves the rest here on every
close-out** -- corrected 2026-08-31 (third run); the preamble had said "the two newest", which
matched neither the retention rule at the tracker's own session log nor what the moves actually did.
Extended by the 2026-08-31 third-run close-out.
Extended again by the 2026-08-31 fifth-run close-out.

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
