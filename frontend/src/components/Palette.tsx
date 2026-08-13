export interface PaletteItem {
  type: string;
  label: string;
  hint?: string;
}

interface PaletteProps {
  items: PaletteItem[];
}

/** Drag source sidebar. Draggable items carry their "type" via the HTML5 DataTransfer
 * API (application/sysmlfrontend-item), read by the canvas's onDrop handler. */
export function Palette({ items }: PaletteProps) {
  return (
    <aside className="palette">
      <h3>Elements</h3>
      {items.map((item) => (
        <div
          key={item.type}
          className="palette-item"
          draggable
          onDragStart={(e) => {
            e.dataTransfer.setData("application/sysmlfrontend-item", item.type);
            e.dataTransfer.effectAllowed = "move";
          }}
          title={item.hint}
        >
          {item.label}
        </div>
      ))}
      <p className="palette-hint">Drag onto the canvas to create an element.</p>
    </aside>
  );
}
