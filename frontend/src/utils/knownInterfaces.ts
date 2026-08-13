import type { KnownInterface, PortDirection, PortSpec, PortView } from "../types";

/** Every name used anywhere in `ports`, INCLUDING nested/decomposed descendants at any depth — not
 * just the top-level names. Requested live: "bei der auswahl von existierenden interfaces müssen
 * alle (inklusive nested schnittstellen) benutzten schnittstellen unterdrückt werden! in system_f
 * werden die nested schnittstellen von HEU und User noch angeboten!" — System_F's own "HEU" port
 * shares its interfaceBlock contract with Flexis's "HEU" (see backend's "Unikat" interface reuse),
 * so its nested decomposition (JMessages/Voice) is already effectively part of System_F's own port
 * structure too, even though System_F itself has no TOP-LEVEL port literally named "JMessages" —
 * offering those names back as "new interface" suggestions on System_F risked the exact same
 * accidental-duplicate problem excludeOwnNames already prevents for top-level names alone. */
export function allPortNames(ports: PortSpec[]): string[] {
  const names: string[] = [];
  function walk(list: PortSpec[]) {
    for (const p of list) {
      names.push(p.name);
      walk(p.children);
    }
  }
  walk(ports);
  return names;
}

// The two kind-groups external reuse is allowed to widen within — mirrors the backend's own
// RhapsodyModelStore LOGICAL_PORT_VIEWS/PHYSICAL_PORT_VIEWS split. A Physical connector is a
// fundamentally different kind of interface than an Operational/Functional/Logical one and the two
// must never merge, even though a root element's top-level port is "external" (reusable within its
// own kind-group) regardless of which of the two groups it belongs to — found live: "System_P sind
// auch externe Schnittstellen, aber nur physikalische! Wir müssen immer zwischen externen und
// internen Schnittstellen unterscheiden."
const PHYSICAL_PORT_VIEWS: ReadonlySet<PortView> = new Set(["Physical"]);

function sameKindGroup(a: PortView, b: PortView): boolean {
  return PHYSICAL_PORT_VIEWS.has(a) === PHYSICAL_PORT_VIEWS.has(b);
}

/** Scopes a knownInterfaces list to the view actually being edited — mirrors the backend's own
 * per-view interfaceBlock scoping (backend/CLAUDE.md's "Interfaces are scoped per view" section):
 * a suggestion is offered when its own view matches, OR it's `external` (a port on a root-level
 * tree element — Flexis/System_F/System_L/System_P — which the backend allows reusing from any
 * OTHER view in the SAME kind-group, see sameKindGroup) AND that root element's own view is in the
 * same kind-group as the view being edited. `view` is the view currently being edited (the locked
 * Architecture-tab view, or whatever the view <select> is currently set to when there's no locked
 * view); `undefined`/`null` leaves the list unscoped (no view context to filter by at all). */
export function forView(knownInterfaces: KnownInterface[], view: PortView | null | undefined): KnownInterface[] {
  if (!view) return knownInterfaces;
  return knownInterfaces.filter((k) => k.view === view || (k.external && !!k.view && sameKindGroup(k.view, view)));
}

/** Drops any suggestion whose OWN qualifiedValue (the exact name it would actually create if
 * picked — see qualifiedValue/handleNameChange, which now ALWAYS uses this qualified form for a
 * nested pick, not just on collision) is already used by the element being edited. History: started
 * as bare-name-only exclusion, widened to cover nested names too (allPortNames), narrowed to
 * top-level-only so `HEU.Voice`/`HEU1.Voice` could both stay offered even once one was used, then
 * — once picking a nested suggestion started ALWAYS producing its qualified name as the actual port
 * name — that top-level-only narrowing became stale: "jetzt werden die bereits ausgewählten
 * interfaces nicht mehr aus der liste von existierenden schnittstellen ausgeblendet!" (a nested
 * suggestion that's ALREADY been added, e.g. "HEU.Voice" is already a port on this owner, kept
 * reappearing forever, since only its bare "Voice" was ever checked against — never its actual
 * qualified identity). Comparing `qualifiedValue(k)` against `existingNames` directly fixes both:
 * a nested suggestion is excluded once ITS OWN qualified identity is used, while a DIFFERENT
 * qualified identity sharing the same bare name (`"HEU1.Voice"` vs an already-used `"HEU.Voice"`)
 * still stays offered. `existingNames` should be built with `allPortNames`. */
export function excludeOwnNames(knownInterfaces: KnownInterface[], existingNames: Iterable<string>): KnownInterface[] {
  const owned = new Set(existingNames);
  if (owned.size === 0) return knownInterfaces;
  return knownInterfaces.filter((k) => !owned.has(qualifiedValue(k)));
}

/** If every entry in knownInterfaces with this exact name (case-sensitive) shares the same
 * direction+type, returns that pair so the caller can auto-fill a new port's form fields —
 * matching the intent of "reuse an existing interface" rather than just suggesting a name.
 * Returns null for no match, or an ambiguous one (the same name used with different direction/
 * type combinations elsewhere in the model), in which case the user's own picks are left alone. */
export function matchKnownInterface(
  knownInterfaces: KnownInterface[],
  name: string,
): { direction: PortDirection | null; type: string | null } | null {
  const trimmed = name.trim();
  if (!trimmed) return null;
  const matches = knownInterfaces.filter((k) => k.name === trimmed);
  if (matches.length === 0) return null;
  const first = matches[0];
  const allSame = matches.every((m) => m.direction === first.direction && m.type === first.type);
  return allSame ? { direction: first.direction, type: first.type } : null;
}

/** Distinct, non-empty "type" values across every known interface — offered as a free-text
 * <datalist> suggestion list independent of which name is being typed, since a type (e.g.
 * "ElectricalPower") is often reused across many differently-named ports. */
export function knownTypes(knownInterfaces: KnownInterface[]): string[] {
  const seen = new Set<string>();
  for (const k of knownInterfaces) {
    if (k.type && k.type.trim()) seen.add(k.type);
  }
  return Array.from(seen);
}

/** The value actually shown/selectable in a <datalist> suggestion for k — `"ParentName.Name"` for a
 * nested interface, bare `name` for a top-level one. Requested live, replacing an earlier
 * `<option label>`-based attempt: "es ist besser den Kontainername als prefix mit punkt vor die
 * nested interfaces zu stellen z.B. HEU.Voice und HEU1.Voice" — a `label` attribute is shown
 * alongside a suggestion without becoming the picked text, but two options sharing the same
 * underlying `value` ("Voice") are indistinguishable to a browser's own datalist matching, so the
 * qualifier now has to be part of the value itself; resolveQualifiedInput strips it back down to
 * the real port name before anything is submitted — the underlying reuse identity (and the shared
 * backend contract, "Voice ist Voice also auch das selbe ibVoice") is unaffected, this only changes
 * what's typed/picked in the form. */
export function qualifiedValue(k: KnownInterface): string {
  return k.parentName ? `${k.parentName}.${k.name}` : k.name;
}

/** Resolves free-typed/picked text against knownInterfaces' own qualifiedValue forms — an exact
 * match (the user picked, or typed, e.g. "HEU.Voice") returns the underlying bare port name
 * ("Voice") plus that specific entry for direction/type autofill. No match (typed text that isn't
 * a qualified value — including a bare name typed without ever opening the suggestion list) returns
 * the text unchanged with `matched: null`, so the caller can fall back to matchKnownInterface's own
 * by-bare-name lookup. */
export function resolveQualifiedInput(
  knownInterfaces: KnownInterface[],
  typed: string,
): { name: string; matched: KnownInterface | null } {
  const hit = knownInterfaces.find((k) => qualifiedValue(k) === typed);
  return hit ? { name: hit.name, matched: hit } : { name: typed, matched: null };
}
