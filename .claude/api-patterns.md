# API Patterns

## Endpoint Structure
```
/api/read/...    - Public GET endpoints (prod, @Profile("prod"))
/api/admin/...   - Admin/write endpoints (dev, @Profile("dev"))
```

## Error Handling

All error handling is centralized in `GlobalExceptionHandler` (`config/GlobalExceptionHandler.java`).
Controllers do NOT use try-catch blocks -- they throw exceptions and let the handler respond.

### Error Response Format
```json
{
  "timestamp": "2026-02-21T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Collection not found: my-slug"
}
```

### Exception Mapping
| Exception | HTTP Status | When |
|-----------|-------------|------|
| `IllegalArgumentException` (msg contains "not found") | 404 NOT_FOUND | Resource not found |
| `IllegalArgumentException` (other) | 400 BAD_REQUEST | Invalid input |
| `IllegalStateException` | 400 BAD_REQUEST | Invalid state |
| `MethodArgumentNotValidException` | 400 BAD_REQUEST | `@Valid` failures |
| `ConstraintViolationException` | 400 BAD_REQUEST | `@Validated` failures |
| `MethodArgumentTypeMismatchException` | 400 BAD_REQUEST | Wrong param type |
| `DataIntegrityViolationException` | 409 CONFLICT | Duplicate/FK violation |
| `Exception` (catch-all) | 500 INTERNAL_SERVER_ERROR | Unexpected errors |

## Controller Pattern
```java
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/read/collections")
public class CollectionControllerProd {

    private final CollectionService collectionService;

    @GetMapping("/{slug}")
    public ResponseEntity<CollectionModel> getCollectionBySlug(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        CollectionModel collection =
            collectionService.getCollectionWithPagination(slug, page, size);
        return ResponseEntity.ok(collection);
    }
}
```

Key points:
- Use `ResponseEntity<T>` with typed return (not `ResponseEntity<?>`)
- No try-catch -- GlobalExceptionHandler handles all exceptions
- Throw `IllegalArgumentException` for not-found / bad input

## Response Patterns

### Success
```java
return ResponseEntity.ok(model);           // 200 with body
return ResponseEntity.ok(page);            // 200 with Page<T>
return ResponseEntity.status(HttpStatus.CREATED).body(model);  // 201
return ResponseEntity.noContent().build(); // 204 for deletes
```

### Triggering Errors (in services or controllers)
```java
// 404 -- message must contain "not found"
throw new IllegalArgumentException("Collection not found: " + slug);

// 400 -- any other IllegalArgumentException
throw new IllegalArgumentException("Invalid collection type: " + type);

// 400 -- invalid state
throw new IllegalStateException("Cannot delete: collection has content");
```

## Pagination
```java
@GetMapping
public ResponseEntity<Page<CollectionModel>> getAllCollections(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
    Pageable pageable = PaginationUtil.normalizeCollectionPageable(page, size);
    Page<CollectionModel> collections = collectionService.getAllCollections(pageable);
    return ResponseEntity.ok(collections);
}
```

## Request Bodies
```java
@PostMapping
public ResponseEntity<CollectionUpdateResponseDTO> create(
        @RequestBody @Valid CollectionRequests.Create request) {
    var response = collectionService.createCollection(request);
    return ResponseEntity.ok(response);
}

@PostMapping("/{slug}/access")
public ResponseEntity<Map<String, Boolean>> validateAccess(
        @PathVariable String slug,
        @RequestBody Map<String, String> passwordRequest) {
    String password = passwordRequest.get("password");
    // ...
}
```

Key points:
- Always use `@Valid` with `@RequestBody` for request DTOs
- Use typed `ResponseEntity<T>` -- never `ResponseEntity<?>`

## Dev vs Prod Controllers
- `*ControllerDev` (`/api/admin/...`): Write/admin endpoints, `@Profile("dev")`
- `*ControllerProd` (`/api/read/...`): Read-only public endpoints, `@Profile("prod")`
- Never expose dev/admin endpoints in production

<!-- Phase 3a DONE: Interface/Impl split removed -- controllers inject concrete service classes directly -->
