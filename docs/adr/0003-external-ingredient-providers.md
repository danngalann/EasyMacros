# ADR 0003: External ingredient providers

- Status: Accepted
- Date: 2026-08-24

## Decision

EasyMacros uses the Open Food Facts v2 product endpoint through a backend-owned Node
native `fetch` adapter. The adapter requests a minimal field set, sends a contact-bearing
EasyMacros User-Agent, validates EAN-13/UPC-A locally, and maps only as-sold `_100g`
values. It owns timeout, one bounded retry, request coalescing, positive/negative cache,
and a process-wide request budget. Android never calls Open Food Facts.

Multimodal ingestion uses OpenRouter's OpenAI-compatible chat-completions endpoint and
a configurable model. The initial default is `google/gemini-2.5-flash`. Requests disable
model creativity and require a strict JSON Schema response; EasyMacros validates the
result again locally. This follows Grocy's established OpenRouter/provider and typed
multimodal-output pattern while deliberately not copying Grocy's debug logging of model
messages.

Both providers create expiring, user-owned candidates. OpenRouter work is represented
by a durable leased job with bounded attempts and private temporary image storage.
Neither provider can create an ingredient: only confirmation of the client-submitted,
edited candidate values performs that transaction.

## Consequences

Open Food Facts v2 is deprecated but currently provides the stable flat `_100g`
contract needed by EasyMacros; a v3 migration requires a new adapter comparison and ADR
update. The official JavaScript SDK remains alpha and lacks the required narrow v2 query
and resilience controls, so it is not used. Provider operation requires deployment
configuration, shared-IP budgeting, visible attribution, and a single API replica while
temporary image paths are local.
