import type { ConnectorRow } from "../types";

interface ConnectorsTableProps {
  rows: ConnectorRow[];
  // See App.tsx's selectInRhapsody — double-clicking an existing connector's row selects/highlights
  // its underlying IRPLink live in Rhapsody. A pending connector (row.guid === null, no Link exists
  // yet) isn't double-clickable — nothing to select.
  onSelectInRhapsody: (guid: string | null | undefined) => void;
}

/** The Connectors tab's own view — a plain table of every connector (existing and pending),
 * columns: View, fromPort.Owner, fromPort.Name, toPort.Owner, toPort.Name. Owner/Name are the raw,
 * unresolved End1Path/End2Path segments the backend already split out (see
 * ModelStore#getConnectorTable) — shown as-is, not prettified. Requested live: "im frontend
 * brauchen wir... eine tabelle aller connector[en]." */
export function ConnectorsTable({ rows, onSelectInRhapsody }: ConnectorsTableProps) {
  return (
    <div className="connectors-table-wrap">
      <table className="connectors-table">
        <thead>
          <tr>
            <th>View</th>
            <th>fromPort.Owner</th>
            <th>fromPort.Name</th>
            <th>toPort.Owner</th>
            <th>toPort.Name</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={5} className="connectors-table-empty">
                No connectors.
              </td>
            </tr>
          ) : (
            rows.map((row, i) => (
              <tr
                key={i}
                className={row.guid ? "connectors-table-row-selectable" : ""}
                title={row.guid ? "Double-click to select in Rhapsody" : undefined}
                onDoubleClick={row.guid ? () => onSelectInRhapsody(row.guid) : undefined}
              >
                <td>{row.view ?? ""}</td>
                <td>{row.fromOwner}</td>
                <td>{row.fromName}</td>
                <td>{row.toOwner}</td>
                <td>{row.toName}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
