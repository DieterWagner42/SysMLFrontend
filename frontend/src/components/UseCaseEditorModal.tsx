import { useState, useEffect, useCallback } from "react";
import type { UseCaseDetail, ElementRef } from "../types";
import { NewActorPicker } from "./NewActorPicker";

interface UseCaseEditorModalProps {
  useCase: UseCaseDetail | null;
  actors: ElementRef[];
  contextViews: ElementRef[];
  onCreateActor: (name: string, contextViewGuid: string | null) => Promise<ElementRef>;
  onSave: (detail: Partial<UseCaseDetail>) => void;
  onClose: () => void;
}

/**
 * Modal editor for a UseCase — full rich narrative (Goal, Actors, Preconditions,
 * Basic Path with B1 trigger, Alternative Paths with step-ref picker, Extension
 * Paths, UC-level Post Condition).
 */
export function UseCaseEditorModal({
  useCase,
  actors,
  contextViews,
  onCreateActor,
  onSave,
  onClose,
}: UseCaseEditorModalProps) {
  if (!useCase) return null;

  // Working copy — all edits happen here, committed on Save
  const [detail, setDetail] = useState<UseCaseDetail>(useCase);
  const [expandedGroups, setExpandedGroups] = useState<Record<string, boolean>>({});
  // Whether the full Step Refs picker is open for a given alternative (by index) —
  // collapsed to a read-only summary of the selected refs by default.
  const [stepRefEditing, setStepRefEditing] = useState<Record<number, boolean>>({});
  // Modal-in-modal for creating a brand-new Actor (with an optional Context) from here.
  const [newActorPickerOpen, setNewActorPickerOpen] = useState(false);

  // Helper to update a single field
  const updateField = useCallback(<K extends keyof UseCaseDetail>(field: K, value: UseCaseDetail[K]) => {
    setDetail((prev) => ({ ...prev, [field]: value }));
  }, []);

  // --- Basic Path rendering with B1 trigger ----------------------------------

  const renderBasicPath = () => (
    <div className="uc-section">
      <h4><span className="uc-icon uc-icon-basic">▶</span>Basic Path</h4>
      <div className="uc-list uc-timeline">
        {detail.basicPath.map((step, idx) => (
          <div key={idx} className="uc-step">
            <span className="step-index">B{idx + 1}</span>
            <input
              type="text"
              value={step}
              onChange={(e) => {
                const arr = [...detail.basicPath];
                arr[idx] = e.target.value;
                updateField("basicPath", arr);
              }}
              placeholder={idx === 0 ? "Trigger: Actor does something..." : "Next step..."}
              className={idx === 0 ? "basic-trigger" : ""}
            />
            {idx === 0 && (
              <span className="trigger-tag">Trigger</span>
            )}
          </div>
        ))}
        <button
          type="button"
          className="add-step-btn"
          onClick={() => updateField("basicPath", [...detail.basicPath, ""])}
        >
          + Add Step
        </button>
      </div>
    </div>
  );

  // --- Alternative Paths rendering ------------------------------------------

  // All step refs a "Step Refs" picker can offer: basic path steps, plus every
  // alternative's and extension's own sub-steps (Ax.y / Ex.y).
  const basicStepRefs = detail.basicPath.map((_, i) => `B${i + 1}`);
  const alternativeStepRefs = detail.alternatives.flatMap((alt, aIdx) =>
    alt.subSteps.map((_, sIdx) => `A${aIdx + 1}.${sIdx + 1}`)
  );
  const extensionStepRefs = detail.extensions.flatMap((ext, eIdx) =>
    ext.subSteps.map((_, sIdx) => `E${eIdx + 1}.${sIdx + 1}`)
  );

  const toggleStepRef = (aIdx: number, ref: string) => {
    const arr = [...detail.alternatives];
    const current = arr[aIdx].stepRefs;
    let next: string[];
    if (ref === "always") {
      next = current.includes("always") ? [] : ["always"];
    } else {
      const withoutAlways = current.filter((r) => r !== "always");
      next = withoutAlways.includes(ref)
        ? withoutAlways.filter((r) => r !== ref)
        : [...withoutAlways, ref];
    }
    arr[aIdx] = { ...arr[aIdx], stepRefs: next };
    updateField("alternatives", arr);
  };

  const renderStepRefGroup = (aIdx: number, alt: UseCaseDetail["alternatives"][number], label: string, refs: string[]) => {
    if (refs.length === 0) return null;
    return (
      <div className="step-ref-group">
        <span className="step-ref-group-label">{label}</span>
        {refs.map((ref) => (
          <button
            key={ref}
            type="button"
            className={`step-ref-chip ${alt.stepRefs.includes(ref) ? "active" : ""}`}
            onClick={() => toggleStepRef(aIdx, ref)}
          >
            {ref}
          </button>
        ))}
      </div>
    );
  };

  // Step Refs field: a read-only summary of the selected refs by default, with an
  // "Edit" button that reveals the full picker only when the user actually needs it.
  const renderStepRefsField = (aIdx: number, alt: UseCaseDetail["alternatives"][number]) => {
    const editing = !!stepRefEditing[aIdx];
    const noStepsAvailable =
      basicStepRefs.length === 0 && alternativeStepRefs.length === 0 && extensionStepRefs.length === 0;
    return (
      <div className="field-row">
        <label>Step Refs (which step does this branch from?)</label>
        {!editing ? (
          <div className="step-ref-summary">
            <div className="step-ref-summary-tags">
              {alt.stepRefs.length === 0 ? (
                <span className="step-ref-empty">No steps selected</span>
              ) : (
                alt.stepRefs.map((ref) => (
                  <span key={ref} className="step-ref-chip active step-ref-chip-static">
                    {ref}
                  </span>
                ))
              )}
            </div>
            <button
              type="button"
              className="step-ref-edit-btn"
              onClick={() => setStepRefEditing((prev) => ({ ...prev, [aIdx]: true }))}
            >
              Edit
            </button>
          </div>
        ) : (
          <div className="step-ref-picker">
            <div className="step-ref-group">
              <button
                type="button"
                className={`step-ref-chip step-ref-chip-always ${alt.stepRefs.includes("always") ? "active" : ""}`}
                onClick={() => toggleStepRef(aIdx, "always")}
              >
                always
              </button>
            </div>
            {renderStepRefGroup(aIdx, alt, "Basic", basicStepRefs)}
            {renderStepRefGroup(aIdx, alt, "Alternative", alternativeStepRefs)}
            {renderStepRefGroup(aIdx, alt, "Extension", extensionStepRefs)}
            {noStepsAvailable && (
              <span className="step-ref-empty">No other steps defined yet — add Basic Path steps first.</span>
            )}
            <button
              type="button"
              className="step-ref-done-btn"
              onClick={() => setStepRefEditing((prev) => ({ ...prev, [aIdx]: false }))}
            >
              Done
            </button>
          </div>
        )}
      </div>
    );
  };

  const renderAlternatives = () => (
    <div className="uc-section">
      <h4><span className="uc-icon uc-icon-alt">⑂</span>Alternative Paths</h4>
      <div className="uc-groups">
        {detail.alternatives.map((alt, aIdx) => (
          <div key={aIdx} className="uc-group alternative">
            <div className="group-header">
              <span className="group-badge trigger">A{aIdx + 1}</span>
              <input
                type="text"
                className="group-title-input"
                value={alt.title}
                onChange={(e) => {
                  const arr = [...detail.alternatives];
                  arr[aIdx] = { ...arr[aIdx], title: e.target.value };
                  updateField("alternatives", arr);
                }}
                placeholder="Alternative title..."
              />
              <button
                type="button"
                className="expand-toggle"
                onClick={() => {
                  setExpandedGroups((prev) => ({ ...prev, [`alt-${aIdx}`]: !prev[`alt-${aIdx}`] }));
                }}
              >
                {expandedGroups[`alt-${aIdx}`] ? "▼" : "▶"}
              </button>
              <button
                type="button"
                className="delete-group-btn"
                onClick={() => {
                  const arr = [...detail.alternatives];
                  arr.splice(aIdx, 1);
                  updateField("alternatives", arr);
                }}
              >
                ×
              </button>
            </div>

            {expandedGroups[`alt-${aIdx}`] && (
              <div className="group-body">
                {renderStepRefsField(aIdx, alt)}
                <div className="field-row">
                  <label>What Happens</label>
                  <textarea
                    value={alt.whatHappens}
                    onChange={(e) => {
                      const arr = [...detail.alternatives];
                      arr[aIdx] = { ...arr[aIdx], whatHappens: e.target.value };
                      updateField("alternatives", arr);
                    }}
                    rows={2}
                    placeholder="Description of the alternative..."
                  />
                </div>
                <div className="field-row">
                  <label>Sub-Steps (A{aIdx + 1}.1, A{aIdx + 1}.2, ...)</label>
                  <div className="uc-list">
                    {alt.subSteps.map((sub, sIdx) => (
                      <div key={sIdx} className="uc-step">
                        <span className="step-index">A{aIdx + 1}.{sIdx + 1}</span>
                        <input
                          type="text"
                          value={sub}
                          onChange={(e) => {
                            const arr = [...detail.alternatives];
                            arr[aIdx] = {
                              ...arr[aIdx],
                              subSteps: arr[aIdx].subSteps.map((v, i) => (i === sIdx ? e.target.value : v)),
                            };
                            updateField("alternatives", arr);
                          }}
                          placeholder="Sub-step..."
                        />
                      </div>
                    ))}
                    <button
                      type="button"
                      className="add-step-btn"
                      onClick={() => {
                        const arr = [...detail.alternatives];
                        arr[aIdx] = { ...arr[aIdx], subSteps: [...arr[aIdx].subSteps, ""] };
                        updateField("alternatives", arr);
                      }}
                    >
                      + Add Sub-Step
                    </button>
                  </div>
                </div>
                <div className="field-row">
                  <label>Post Condition (this alternative)</label>
                  <input
                    type="text"
                    value={alt.postCondition}
                    onChange={(e) => {
                      const arr = [...detail.alternatives];
                      arr[aIdx] = { ...arr[aIdx], postCondition: e.target.value };
                      updateField("alternatives", arr);
                    }}
                    placeholder="State after alternative completes... (default: n/a)"
                  />
                </div>
              </div>
            )}
          </div>
        ))}
        <button
          type="button"
          className="add-group-btn"
          onClick={() =>
            updateField("alternatives", [
              ...detail.alternatives,
              { title: "", stepRefs: ["always"], whatHappens: "", subSteps: [], postCondition: "n/a" },
            ])
          }
        >
          + Add Alternative Path
        </button>
      </div>
    </div>
  );

  // --- Extension Paths rendering --------------------------------------------

  const renderExtensions = () => (
    <div className="uc-section">
      <h4><span className="uc-icon uc-icon-ext">⚠</span>Extension Paths</h4>
      <div className="uc-groups">
        {detail.extensions.map((ext, eIdx) => (
          <div key={eIdx} className="uc-group extension">
            <div className="group-header">
              <span className="group-badge trigger">E{eIdx + 1}</span>
              <input
                type="text"
                className="group-title-input"
                value={ext.title}
                onChange={(e) => {
                  const arr = [...detail.extensions];
                  arr[eIdx] = { ...arr[eIdx], title: e.target.value };
                  updateField("extensions", arr);
                }}
                placeholder="Extension title..."
              />
              <button
                type="button"
                className="expand-toggle"
                onClick={() => {
                  setExpandedGroups((prev) => ({ ...prev, [`ext-${eIdx}`]: !prev[`ext-${eIdx}`] }));
                }}
              >
                {expandedGroups[`ext-${eIdx}`] ? "▼" : "▶"}
              </button>
              <button
                type="button"
                className="delete-group-btn"
                onClick={() => {
                  const arr = [...detail.extensions];
                  arr.splice(eIdx, 1);
                  updateField("extensions", arr);
                }}
              >
                ×
              </button>
            </div>

            {expandedGroups[`ext-${eIdx}`] && (
              <div className="group-body">
                <div className="field-row">
                  <label>Trigger Condition</label>
                  <input
                    type="text"
                    value={ext.triggerText}
                    onChange={(e) => {
                      const arr = [...detail.extensions];
                      arr[eIdx] = { ...arr[eIdx], triggerText: e.target.value };
                      updateField("extensions", arr);
                    }}
                    placeholder="e.g. Actor does something unexpected..."
                  />
                </div>
                <div className="field-row">
                  <label>Sub-Steps (E{eIdx + 1}.1, E{eIdx + 1}.2, ...)</label>
                  <div className="uc-list">
                    {ext.subSteps.map((sub, sIdx) => (
                      <div key={sIdx} className="uc-step">
                        <span className="step-index">E{eIdx + 1}.{sIdx + 1}</span>
                        <input
                          type="text"
                          value={sub}
                          onChange={(e) => {
                            const arr = [...detail.extensions];
                            arr[eIdx] = {
                              ...arr[eIdx],
                              subSteps: arr[eIdx].subSteps.map((v, i) => (i === sIdx ? e.target.value : v)),
                            };
                            updateField("extensions", arr);
                          }}
                          placeholder="Sub-step..."
                        />
                      </div>
                    ))}
                    <button
                      type="button"
                      className="add-step-btn"
                      onClick={() => {
                        const arr = [...detail.extensions];
                        arr[eIdx] = { ...arr[eIdx], subSteps: [...arr[eIdx].subSteps, ""] };
                        updateField("extensions", arr);
                      }}
                    >
                      + Add Sub-Step
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        ))}
        <button
          type="button"
          className="add-group-btn"
          onClick={() =>
            updateField("extensions", [...detail.extensions, { title: "", triggerText: "", subSteps: [] }])
          }
        >
          + Add Extension Path
        </button>
      </div>
    </div>
  );

  // --- Handle Escape to close -----------------------------------------------

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") onClose();
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [onClose]);

  // --- Render ---------------------------------------------------------------

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal use-case-editor" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <div className="modal-header-text">
            <span className="modal-eyebrow">Use Case</span>
            <h2>{detail.name}</h2>
          </div>
          <button className="modal-close" onClick={onClose} title="Close">
            ×
          </button>
        </div>
        <div className="modal-body">
          {/* Goal */}
          <div className="uc-section">
            <h4><span className="uc-icon uc-icon-goal">◎</span>Goal</h4>
            <textarea
              value={detail.goal}
              onChange={(e) => updateField("goal", e.target.value)}
              rows={3}
              placeholder="What does this use case achieve?"
            />
          </div>

          {/* Actors (guids, displayed as names with autocomplete) */}
          <div className="uc-section">
            <h4><span className="uc-icon uc-icon-actors">◈</span>Actors</h4>
            <div className="actor-selector">
              {detail.actors.map((actorGuid, idx) => {
                return (
                  <div key={idx} className="actor-row">
                    <select
                      value={actorGuid}
                      onChange={(e) => {
                        const arr = [...detail.actors];
                        arr[idx] = e.target.value;
                        updateField("actors", arr);
                      }}
                    >
                      <option value="">— Select Actor —</option>
                      {actors.map((a) => (
                        <option key={a.guid} value={a.guid}>
                          {a.name}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      className="delete-actor-btn"
                      onClick={() => {
                        const arr = [...detail.actors];
                        arr.splice(idx, 1);
                        updateField("actors", arr);
                      }}
                    >
                      ×
                    </button>
                  </div>
                );
              })}
              <button
                type="button"
                className="add-actor-btn"
                onClick={() => updateField("actors", [...detail.actors, ""])}
              >
                + Add Actor
              </button>
              <button
                type="button"
                className="add-actor-btn"
                onClick={() => setNewActorPickerOpen(true)}
              >
                + New Actor
              </button>
            </div>
          </div>

          {/* Preconditions */}
          <div className="uc-section">
            <h4><span className="uc-icon uc-icon-pre">✓</span>Preconditions</h4>
            <div className="uc-list uc-checklist">
              {detail.preconditions.map((pc, idx) => (
                <div key={idx} className="uc-step">
                  <span className="step-index">P{idx + 1}</span>
                  <input
                    type="text"
                    value={pc}
                    onChange={(e) => {
                      const arr = [...detail.preconditions];
                      arr[idx] = e.target.value;
                      updateField("preconditions", arr);
                    }}
                    placeholder="Precondition..."
                  />
                  <button
                    type="button"
                    className="delete-step-btn"
                    onClick={() => {
                      const arr = [...detail.preconditions];
                      arr.splice(idx, 1);
                      updateField("preconditions", arr);
                    }}
                  >
                    ×
                  </button>
                </div>
              ))}
              <button
                type="button"
                className="add-step-btn"
                onClick={() => updateField("preconditions", [...detail.preconditions, ""])}
              >
                + Add Precondition
              </button>
            </div>
          </div>

          {/* Basic Path */}
          {renderBasicPath()}

          {/* Alternative Paths */}
          {renderAlternatives()}

          {/* Extension Paths */}
          {renderExtensions()}

          {/* Post Condition (UC level) */}
          <div className="uc-section">
            <h4><span className="uc-icon uc-icon-post">◆</span>Post Condition</h4>
            <textarea
              value={detail.postCondition}
              onChange={(e) => updateField("postCondition", e.target.value)}
              rows={2}
              placeholder="Final state after use case completes... (default: n/a)"
            />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn-cancel" onClick={onClose}>
            Cancel
          </button>
          <button className="btn-save" onClick={() => onSave(detail)}>
            Save
          </button>
        </div>
      </div>
      {newActorPickerOpen && (
        <NewActorPicker
          contextViews={contextViews}
          onCreate={(name, contextViewGuid) => {
            onCreateActor(name, contextViewGuid).then((created) => {
              updateField("actors", [...detail.actors, created.guid]);
              setNewActorPickerOpen(false);
            });
          }}
          onClose={() => setNewActorPickerOpen(false)}
        />
      )}
    </div>
  );
}