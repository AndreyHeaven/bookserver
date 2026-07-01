# Format conversion architecture

The conversion feature is an **architectural slot**: everything (REST, DB job
lifecycle, async execution, registry) is wired end-to-end, but no real converter
ships. The only production bean is `NotImplementedFormatConverter`, which always
fails with the static message `Conversion not implemented yet`.

## Components

- `FormatConverter` — extension point. `supportedPairs()` advertises the directed
  `(source -> target)` pairs a converter handles; `convert(source, targetFormat)`
  performs the work.
- `FormatPair` — normalized (lowercase) source/target pair, used as the registry key.
- `SupportedFormat` — formats the architecture is aware of: `fb2, epub, mobi, pdf, azw3`.
- `FormatConverterRegistry` — collects all `FormatConverter` beans at startup and
  resolves one by pair. First bean claiming a pair wins (`putIfAbsent`).
- `ConversionService` — validates the pair (fail fast with 422 when unsupported),
  persists a `ConversionJob(PENDING)`, and dispatches to the async worker.
- `ConversionWorker` — `@Async`; moves the job `RUNNING -> SUCCEEDED/FAILED`,
  invoking the resolved converter. Any exception is recorded on the job message.
- `ConversionsController` — `POST /api/conversions`, `GET /api/conversions`,
  `GET /api/conversions/{id}`.

## Adding a real converter

1. Create a Spring bean implementing `FormatConverter`:

   ```java
   @Component
   public class CalibreFormatConverter implements FormatConverter {
       @Override public Set<FormatPair> supportedPairs() {
           return Set.of(new FormatPair("fb2", "epub"), new FormatPair("epub", "mobi"));
       }
       @Override public Path convert(Path source, String targetFormat) throws Exception {
           // shell out to ebook-convert, return the produced file path
       }
   }
   ```

2. That is all — no existing class changes (Open/Closed). Because the registry uses
   `putIfAbsent`, a real converter registered for a pair takes precedence over
   `NotImplementedFormatConverter` only if it is discovered first. To guarantee
   precedence, disable the not-implemented bean (see below) or annotate the real
   one with `@Order`/`@Primary` semantics via bean ordering.

3. Persist the produced file through `BookFileStorage`, create a new `BookFile`,
   and set it on `ConversionJob.outputBookFile` when wiring the success path.

## Extension points

- **Async pool** — the worker uses Spring's default `@Async` executor. Configure a
  dedicated `ThreadPoolTaskExecutor` (core/max pool size, queue capacity) to bound
  concurrent conversions.
- **Max concurrent conversions** — enforce via the executor queue/pool size or a
  semaphore inside the worker.

## Disabling `NotImplementedFormatConverter`

- Remove its `@Component`, or
- Guard it with a profile (e.g. `@Profile("!real-converters")`) and activate the
  opposite profile in production once a real converter exists.
