# Durable lint and background formatting — SNI-78

Thiago approved on 2026-11-05: caller-generated IDs, Redis persistence, seven days
of retention for terminal operations, and the same recoverable lifecycle for
background formatting. Compared with synchronous retry, this costs Redis writes,
space and state-machine logic; it permits retrieving a completed result after a
lost response instead of guessing whether remote computation stopped. Compared
with provider-generated IDs plus an idempotency key, one caller ID avoids a
second identity/mapping across these internal services.

The trade-off framework follows *Fundamentals of Software Architecture*, 2nd ed.,
chapter 27 (laws/trade-offs) and chapter 21 (decisions), as identified in the
[publisher's index](https://www.oreilly.com/library/view/fundamentals-of-software/9781098175504/).
The protocol here is the project's application of that framework, not a recipe
attributed to the book. Existing synchronous lint-on-save and public interactive
NDJSON contracts remain synchronous/unchanged.

## Internal HTTP contract

- `PUT /operations/{uuid}` accepts `kind` (`LINT`/`FORMAT`), `version`, `code`,
  explicit `rules`, expected `processorRevision`, and `submittedAt` (UTC instant).
  The caller chooses the UUID and persists it before sending. A retry must carry
  exactly the same command, including its original timestamp. Same ID/command
  returns the existing operation; changed input returns 409. A cancelled fence
  returns CANCELLED and never starts work.
- `GET /operations/{uuid}` returns command, state, boot owner, result/reason.
  A missing ID returns 404. **404 alone does not prove that a delayed PUT cannot
  arrive.** Revision is frozen in the record because the generic operations path
  can represent different capabilities; it is checked before admission and work.
- `POST /operations/{uuid}/cancel` atomically creates a cancelled tombstone if
  the ID is absent. A delayed PUT cannot execute through that tombstone. Pending
  work cancels without starting; running work becomes CANCELLING until the engine
  actually returns. Disconnecting HTTP is not cancellation or proof of stopped CPU.
- `Execution-Active-Limit` is returned with operation records. The PUT can include
  the same header as a precondition; mismatched capacity is rejected before work.
- States: PENDING, RUNNING, CANCELLING, COMPLETED, FAILED, CANCELLED. COMPLETED
  carries a typed outcome (`LINTED`, `FORMATTED`, `INVALID_CODE`, `INVALID_RULES`,
  input/output/diagnostic limit failures). Noncompliant lint is a completed
  analysis with diagnostics. FAILED is an operational failure, not compliance.
- A new absent operation must be submitted within the configured admission window
  (default ±2 minutes, allowing bounded clock skew). Existing operations are still
  retrievable outside that window. This prevents an old request starting after its
  seven-day terminal tombstone expires. Configure clocks appropriately.

## Storage, CPU and recovery

Redis Lua scripts atomically insert/CAS records and maintain an active index.
PENDING/RUNNING/CANCELLING never receive a TTL. Terminal records expire seven days
from final persistence; reads and duplicate starts do not extend that TTL. This
is a recovery window, separate from the durable application audit in PostgreSQL.
No unlimited recovery guarantee is made after expiry.

Active admission and serialized record size are bounded. Redis must retain its
persistent AOF/noeviction configuration. Exhausted/unavailable storage produces
an error; it does not announce a result as durable. A result whose final write
fails is held locally and retried without recalculating it. A local in-flight
record also prevents Redis loss from making live CPU look absent. Redis AOF's
configured every-second durability can lose the newest writes on a crash;
recovery can recompute pure work, and does not promise exactly-once CPU execution.

A shared process capacity is used by interactive execution and the durable
worker. There is at most one background computation, with at least one manual
slot reserved. Cancellation/deadline checks run at lazy collection boundaries;
the slot is released only after the computation has actually returned. No timeout
or Redis lease expiration frees a slot while the calculation is still running.

**Supported topology:** one provider process per namespace, with stop-first
replacement. This is the existing Redis Swarm overlay. Enabling requires
`operations.deployment-mode=single-stop-first`; this declaration must match the
deployment. A new boot can recover a predecessor's RUNNING pure computation
because the predecessor process has exited. A changed processor revision fails
the operation explicitly instead of attributing a new engine to old input.
Multiple live provider replicas sharing a namespace are unsupported; do not scale
this feature horizontally without a separate ownership/recovery design.

## Activation

The feature defaults off. Include the Redis overlay, secret/configtree and private
Redis network for PrintScript. Set `PRINTSCRIPT_OPERATIONS_ENABLED=true` and
`PRINTSCRIPT_OPERATIONS_DEPLOYMENT_MODE=single-stop-first`. Swarm uses an
ambient-specific `OPERATIONS_NAMESPACE`. Never mount a repository or build code
inside the VM. Deploy published images in the already agreed way.

Enable the provider and verify its operations contract before enabling the new
Snippets workers. Drain legacy synchronous permits first: their remote CPU cannot
be proven stopped by creating a new operation tombstone. They remain UNKNOWN
until independently verified. No Azure resources or CD workflow is modified by
these application changes; actual activation remains an operational step.

## Verification

Run `./gradlew build` with JDK 21 and Docker. `DurableOperationsTest` uses real
Redis and the published engine for lint/format output, replay after coordinator
restart, processor revision change, seven-day TTL, active records without expiry,
cancel-before-start and clock bounds. Controlled engine/storage faults verify
that cancellation retains capacity until return, manual admission remains possible,
and a lost final Redis write neither hides live CPU nor reruns the local calculation.
