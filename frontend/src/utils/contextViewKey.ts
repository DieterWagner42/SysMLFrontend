/** The ArchNode.sizes/positions key for the system-of-interest's own box in a specific Context View
 * tab — see types.ts's own comment on ArchNode.sizes for why this is a dynamic, per-Context-View
 * slot rather than a single shared "Context" one. Originally size-only (hence the file name) —
 * position needed the exact same treatment right after, requested live: "die Größe geht jetzt, aber
 * die Position noch nicht" — reused as-is rather than duplicated, since both are just "which
 * Context View am I looking at" and share the same key format. `contextViewTab` is the same
 * `string | null` App.tsx already tracks (a Context View's guid, or null for the built-in
 * unfiltered "All" tab) — kept as one function so the key format only needs to be written (and
 * changed, if it ever needs to) once, shared between seeding (reading a saved value) and persisting
 * (writing a new one), for both position and size. */
export function contextViewKey(contextViewTab: string | null): string {
  return `Context:${contextViewTab ?? "All"}`;
}
