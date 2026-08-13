import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { PendingConnector } from "../types";

interface PendingConnectorsPanelProps {
  onClose: () => void;
}

/** Rhapsody mode only — lists every connector (IRPLink) the backend's own delegation rules say
 * should exist but doesn't yet (see ModelStore#getPendingConnectors), with a switch to force-create
 * each one (or all at once). Requested live after finding that connector auto-creation only ever
 * fires for a brand-new TOP-LEVEL port — a port added directly under an already-existing "external"/
 * "internal" container (the normal way to add a second/third reuse of an interface) has no trigger
 * of its own: "am besten bauen wir einen schalter auf der GUI ein mit dem ich das erzeugen der links
 * forcieren kann. am besten bauen wir noch eine view in dem ich alle zu erzeugenden links sehe dort
 * sollte auch der schalter liegen." Always empty in local mode (no connector/diagram concept there).
 * Re-fetches after every create so the list (and the underlying model) never drift apart. */
export function PendingConnectorsPanel({ onClose }: PendingConnectorsPanelProps) {
  const [items, setItems] = useState<PendingConnector[] | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    try {
      setError(null);
      setItems(await api.getPendingConnectors());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  useEffect(() => {
    load();
  }, []);

  function keyOf(c: PendingConnector): string {
    return c.warning ? `warning:${c.warning}` : `${c.fromPortGuid}${c.toPortGuid}`;
  }

  async function createOne(c: PendingConnector) {
    setBusy(keyOf(c));
    try {
      await api.createPendingConnector(c);
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  async function createAll() {
    if (!items) return;
    setBusy("__all__");
    try {
      // Warning entries carry no GUIDs — see PendingConnector's own javadoc — never something to
      // create, only to show.
      for (const c of items) {
        if (!c.warning) await api.createPendingConnector(c);
      }
      await load();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
        <h3>Pending Connectors</h3>
        <p className="modal-field-hint">
          Connectors that should exist in Rhapsody (based on shared interfaces) but haven't been
          created yet — this can happen when a port is added directly under an existing
          "external"/"internal" container. Click one, or "Create all", to force creation.
        </p>
        {error && <div className="error-banner">{error}</div>}
        {items === null ? (
          <p>Loading…</p>
        ) : items.length === 0 ? (
          <p>No pending connectors — everything that should be connected already is.</p>
        ) : (
          <div className="modal-list">
            {items.map((c) => (
              <div
                className={c.warning ? "modal-list-row pending-connector-row pending-connector-warning" : "modal-list-row pending-connector-row"}
                key={keyOf(c)}
              >
                <span>{c.warning ?? c.description}</span>
                {!c.warning && (
                  <button disabled={busy !== null} onClick={() => createOne(c)}>
                    {busy === keyOf(c) ? "Creating…" : "Create"}
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
        <div className="modal-actions">
          <button onClick={onClose} disabled={busy !== null}>Close</button>
          {items !== null && items.some((c) => !c.warning) && (
            <button onClick={createAll} disabled={busy !== null}>
              {busy === "__all__" ? "Creating…" : "Create all"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
