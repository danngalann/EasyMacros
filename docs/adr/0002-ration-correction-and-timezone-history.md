# ADR 0002: Ration correction and timezone history

## Status

Accepted

## Context

Rations hold immutable nutrient snapshots, while users still need a correction path and
may change their profile timezone after logging history. These choices change how past
dashboard dates are interpreted.

## Decision

Correct a ration by soft-deleting it and logging a replacement; do not edit its nutrient
snapshot in place. A timezone change updates the user's IANA timezone and atomically
reclassifies all active ration local dates from their stored instants in the new zone.
The consumption instant and nutrient snapshot remain unchanged.

## Consequences

Historical day totals can move between local dates after a timezone change. The UI must
warn about that effect. Deletion removes a ration from active dashboard/history reads,
and a replacement log gets its own immutable snapshot.
