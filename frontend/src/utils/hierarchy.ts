import type { ArchKind } from "../types";

export type ArchFamily = "Functional" | "Logical" | "Physical" | "Structure";

/** Mirrors backend/HierarchyLevels#kindFamily — which of the four separate root-level trees a
 * kind belongs to. The three aspect kinds are each their own family; every System-of-Systems-chain
 * kind (SoS/System/Subsystem/Equipment, plus the legacy "Block" fallback) shares one "Structure"
 * family. Used by MoveElementPicker to restrict candidates to the same tree as the move target,
 * matching what the backend itself will actually accept (see ModelStore#moveElement). */
export function kindFamily(kind: ArchKind): ArchFamily {
  if (kind === "FunctionalNode") return "Functional";
  if (kind === "LogicalNode") return "Logical";
  if (kind === "PhysicalNode") return "Physical";
  return "Structure";
}
