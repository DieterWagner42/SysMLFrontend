import { useState } from "react";
import { PortRow } from "../PortRow";
import type { KnownInterface, PortDirection, PortSpec, PortView } from "../../types";
import { allPortNames, excludeOwnNames, forView, knownTypes, matchKnownInterface, qualifiedValue, resolveQualifiedInput } from "../../utils/knownInterfaces";

interface PortsSectionProps {
  ownerGuid: string;
  ports: PortSpec[];
  onAddPort: (ownerGuid: string, name: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortChange: (portGuid: string, direction: PortDirection, type: string, view: PortView) => void;
  onPortDelete: (portGuid: string) => void;
  onEditDocumentation: (guid: string, name: string) => void;
  // When set, the view the currently-selected architecture view implies (Operational/Functional/
  // Logical/Physical) — the "View" picker is hidden and every new port here is created with this
  // view directly, instead of asking the user to redundantly pick what's already implied by
  // "you're adding an interface while looking at the X view". Undefined for the Context tab's
  // Actors, which have no such view context, so they keep the full manual picker.
  lockedView?: PortView;
  // Whether ownerGuid is itself one of the four tree roots (Flexis/System_F/System_L/System_P) —
  // see utils/knownInterfaces.ts' forView `allowTopLevelExternal` param. Defaults to true (every
  // existing caller except a non-root ArchitectureNode keeps today's behavior — Actors/
  // SystemOfInterest included, since they're never routed through the internal/external delegation
  // collectors on the backend and picking a bare external name there still makes sense).
  isRootOwner?: boolean;
  // Every distinct interface (name/direction/type) seen anywhere else in the model — offered as
  // <datalist> suggestions on the name/type fields below so e.g. an Operational "HEU" port already
  // used on the System can be reused on a Subsystem or FunctionalNode instead of retyping it. See
  // App.tsx's knownInterfaces and utils/knownInterfaces.ts.
  knownInterfaces: KnownInterface[];
  // See PortRow's own javadoc — whether a port's own decomposition starts expanded or collapsed.
  // Defaults to true (unchanged Architecture-tab behavior); Context tab nodes pass false.
  defaultExpanded?: boolean;
  // The configurable [Physical] interfaceTypes list (mechanic/electric/radiofrequency/... — see
  // config.ini, editable via the gear-icon ConfigPanel) — offered as the "Type" field's own picker
  // instead of the free-text interfaceBlock-name datalist whenever the effective view is Physical.
  // A Physical port's type is a physical REALIZATION PROPERTY of that specific interface, not a
  // shared identity ("der Punkt ist wir müssen physicalische properties zu jedem interface
  // zuweisen... HEU.Link16 kann dann RF sein") — see RhapsodyModelStore#setPhysicalTypeStereotype.
  physicalInterfaceTypes: string[];
}

const VIEWS: PortView[] = ["Operational", "Functional", "Logical", "Physical"];

/** Port/interface list for a Block or Actor node, with an inline "add port" form. Shared between
 * ArchitectureNode (Block) and ActorNode since both are classifiers that can own typed ProxyPorts.
 * onAddPort is passed down unbound (not pre-closed over ownerGuid) so PortRow can reuse it to add
 * nested/decomposed ports under an existing port instead of a new top-level one. */
export function PortsSection({ ownerGuid, ports, onAddPort, onPortChange, onPortDelete, onEditDocumentation, lockedView, isRootOwner = true, knownInterfaces, defaultExpanded = true, physicalInterfaceTypes }: PortsSectionProps) {
  const [adding, setAdding] = useState(false);
  const [name, setName] = useState("");
  const [type, setType] = useState("");
  const [view, setView] = useState<PortView>("Operational");
  const effectiveView = lockedView ?? view;
  const isPhysical = effectiveView === "Physical";
  const namesListId = `known-iface-names-${ownerGuid}`;
  const typesListId = `known-iface-types-${ownerGuid}`;
  // Suggestions offered while adding THIS interface, scoped to the view it's actually being added
  // in (lockedView, or the view <select>'s current pick when there's no locked view) — see
  // utils/knownInterfaces.ts' forView — and excluding every name this owner already uses, INCLUDING
  // nested/decomposed descendants (allPortNames/excludeOwnNames) so an already-used interface isn't
  // suggested back as if it were a fresh reuse candidate. Recomputed on every render so it stays in
  // sync with `view` when the user changes the <select> before typing a name. isRootOwner gates bare
  // top-level external names (e.g. "HEU" itself) out of this list for a non-root child — only its
  // nested interfaces (e.g. "HEU.Voice") stay offered; see forView's own javadoc.
  const scopedKnownInterfaces = excludeOwnNames(forView(knownInterfaces, effectiveView, isRootOwner), allPortNames(ports));

  function submit() {
    if (!name.trim()) return;
    // No direction — a top-level interface (the only kind this form ever creates, see its own doc
    // comment) is purely a grouping container, never itself directional; see applyPortSpec's own
    // matching backend check. "InOut" here is a harmless, unused placeholder the backend ignores.
    onAddPort(ownerGuid, name.trim(), "InOut", type.trim(), effectiveView);
    setName("");
    setType("");
    setAdding(false);
  }

  // Selecting (or typing exactly) an existing interface's name auto-fills direction/type from it —
  // still creates an independent new port, just pre-filled from the matching known one instead of
  // starting blank (works the same whether the Type field below is the free-text/datalist form or
  // the Physical-only picker — either way `type` just needs to end up holding one of the values
  // that field's own options accept). A qualified pick (e.g. "HEU.Voice",
  // see qualifiedValue) ALWAYS keeps that "Parent.Name" form as the actual port name — requested
  // live: "ich möchte dass immer der Prefix HEU oder HEU1 bei den nested ports eingefügt wird. das
  // hilft alles besser zu identifizieren" — not just when it happens to collide with an existing
  // name on this owner (the earlier, narrower behavior): every reused nested interface stays
  // traceable to its source container by name alone, everywhere it's reused, not only when that
  // was the only way to avoid an accidental overwrite (see the Rhapsody-name-uniqueness reasoning
  // this was originally introduced for). A plain, non-nested pick (`matched.parentName == null`,
  // or free-typed text that never matched a suggestion at all) is untouched — stays the bare name.
  function handleNameChange(value: string) {
    const { name: resolved, matched } = resolveQualifiedInput(scopedKnownInterfaces, value);
    const finalName = matched?.parentName ? qualifiedValue(matched) : resolved;
    setName(finalName);
    const match = matched ?? matchKnownInterface(scopedKnownInterfaces, resolved);
    if (match?.type) setType(match.type);
  }

  return (
    <div className="ports-section nodrag" onClick={(e) => e.stopPropagation()}>
      {ports.map((p) => (
        <PortRow
          key={p.guid}
          port={p}
          onAddPort={onAddPort}
          onChange={onPortChange}
          onDelete={onPortDelete}
          onEditDocumentation={onEditDocumentation}
          lockedView={lockedView}
          knownInterfaces={knownInterfaces}
          physicalInterfaceTypes={physicalInterfaceTypes}
          defaultExpanded={defaultExpanded}
        />
      ))}
      {adding ? (
        <div className="port-add-form">
          <input
            autoFocus
            placeholder="Port name"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && submit()}
            list={namesListId}
          />
          <datalist id={namesListId}>
            {Array.from(new Set(scopedKnownInterfaces.map((k) => qualifiedValue(k)))).map((v) => (
              <option key={v} value={v} />
            ))}
          </datalist>
          {!lockedView && (
            <select value={view} onChange={(e) => setView(e.target.value as PortView)}>
              {VIEWS.map((v) => (
                <option key={v} value={v}>{v}</option>
              ))}
            </select>
          )}
          {isPhysical ? (
            <select value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">Type...</option>
              {physicalInterfaceTypes.map((t) => (
                <option key={t} value={t}>{t}</option>
              ))}
            </select>
          ) : (
            <>
              <input
                placeholder="Type (optional)"
                value={type}
                onChange={(e) => setType(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && submit()}
                list={typesListId}
              />
              <datalist id={typesListId}>
                {knownTypes(scopedKnownInterfaces).map((t) => (
                  <option key={t} value={t} />
                ))}
              </datalist>
            </>
          )}
          <button onClick={submit}>Add</button>
          <button onClick={() => setAdding(false)}>Cancel</button>
        </div>
      ) : (
        <button className="add-port-btn" onClick={() => setAdding(true)}>
          + Interface
        </button>
      )}
    </div>
  );
}
