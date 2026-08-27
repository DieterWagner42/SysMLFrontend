# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project goal

A **PowerPoint-like visual frontend for authoring SysML models**, which abstracts away
tool-specific idiosyncrasies of the underlying modeling tool (IBM Rational Rhapsody) as much as
possible. The editor should feel like a slide/diagram editor, not like a traditional SysML tool's
property-heavy UI.

The frontend captures three aspects of a system model, one per tab (like slides):

1. **Architecture** — the hierarchical decomposition of the system. There is no user-facing
   "Package" concept — just a **model title** at the root, and every element below it is
   automatically leveled by nesting depth: **System of Systems (optional) → System → Subsystem →
   Equipment (leaf)**. The level is never freely chosen except the SoS-vs-System choice at the very
   top; see "Automatic hierarchy" below.
2. **Context** — the external systems the system-of-interest interacts with (Actors).
3. **Capabilities** — the capabilities / use cases the system provides (UseCases).

Interaction model: **drag & drop** from a palette to place architecture elements / external
systems / capabilities, plus per-node/per-port **context menus** for specialization (rename,
add-child, interface typing). Edits are pushed **on the fly** — but "on the fly into Rhapsody" is
opt-in, not assumed:

- **Rhapsody mode**: if `config.ini`'s `[Rhapsody] installDir` is set *and* a Rhapsody instance can
  actually be reached, every change syncs live into that running Rhapsody project.
- **Local mode** (the fallback, and what runs with no configuration at all): everything is kept in
  an in-memory tree and auto-persisted to a local XML file after every change — this is local
  mode's equivalent of "on the fly". No Rhapsody involved, nothing to install.

Either way there's no separate "offline draft, sync later" step — whichever store is active is
always the live one. Model snapshots can also be explicitly saved/loaded to an arbitrary XML path
via "Save XML"/"Load XML" in the header, independent of which mode is active (e.g. to back up a
Rhapsody-mode session, or hand a local-mode session to someone else).

## Repository layout

```
backend/    Java service — either bridges the web frontend to a running Rhapsody instance,
            or serves a local in-memory/XML-backed model when Rhapsody isn't available
frontend/   React + TypeScript + React Flow web UI
rhapsody.jar  vendored copy of IBM Rhapsody's Java Automation API (also under backend/lib/)
```

A sibling project, `D:\KI\plugin\SPREAD`, independently implements the same
"launch/attach to Rhapsody and expose it over HTTP" idea for a different purpose (LAN remote
control of element selection). `backend/` **deliberately duplicates** SPREAD's two-JVM bootstrap
pattern rather than depending on it — SPREAD was left untouched by design, so don't try to unify
them without checking with the user first.

## Backend (`backend/`)

Package `com.sysmlfrontend.backend`. No Maven/Gradle — this dev machine has neither installed, so
it compiles the same way as SPREAD: manually with `javac`, using the JDK at
`C:\Program Files\Java\jdk-26.0.1\bin` if `java`/`javac` aren't already on PATH.

### Architecture: `ModelStore` abstraction, two implementations

Everything domain-facing (`WebServer`, the XML snapshot logic) is written against a `ModelStore`
interface (`server/ModelStore.java`), not against Rhapsody directly:

- **`LocalXmlModelStore`** — pure Java, in-memory tree + UUID guids, **zero rhapsody.jar
  dependency**. Auto-persists to `[Local] statePath` (default `local-model.xml`) after every
  mutation and auto-loads it back on startup. This is what makes the app fully usable with no
  Rhapsody installed anywhere.
- **`RhapsodyModelStore`** (was `RhapsodyGateway`) — live sync into a running Rhapsody instance via
  `com.telelogic.rhapsody.core`. Only constructed in the JVM that has `rhapsody.jar` on its
  classpath.

Both are used through the same `WebServer`, which has **no rhapsody.jar dependency at all**. XML
export/import (`ModelXml.java`) is also store-agnostic — it walks `ModelStore`'s Map/List shapes,
not Rhapsody objects, so it works identically for both stores and isn't duplicated per store.

### Startup: Rhapsody is opt-in, with graceful fallback at two points

- **`BootstrapApp`** (entry point, **zero rhapsody.jar dependency**) reads `config.ini`. If
  `[Rhapsody] installDir` is unset/empty, or `rhapsody.jar` isn't found under it, it runs
  `LocalXmlModelStore` + `WebServer` **directly in this JVM** — no second process, no native
  library path, nothing Rhapsody-related touched at all. Only when a Rhapsody install is actually
  configured does it relaunch into `ModelServer`.
- **`ModelServer`** (second JVM, only launched when Rhapsody was configured) tries to
  attach/launch Rhapsody. If that fails for any reason (nothing running, launch times out, etc.),
  it **falls back to `LocalXmlModelStore`** instead of crashing — so "found a Rhapsody server" gates
  live sync at two independent points: is one configured, and does connecting to it actually work.
- **`ServerRunner`** (shared, rhapsody.jar-free) holds the actual start/console-stop/shutdown loop
  used by both `BootstrapApp` (local mode) and `ModelServer` (Rhapsody mode or its fallback), so
  that logic isn't duplicated.
- Two-JVM reasoning (only relevant once Rhapsody is actually wanted): `rhapsody.jar`'s native
  bridge needs its DLL on `java.library.path`, and that can't be changed after JVM startup on
  modern Java — hence relaunching into `ModelServer` with `-Djava.library.path=...` and
  `rhapsody.jar` prepended to the classpath, rather than just adding the jar to the running JVM.

### Compiling

```bash
# Bootstrap-only tier (no rhapsody.jar needed — this alone runs the app in local mode):
javac --release 25 -d out src/com/sysmlfrontend/backend/BootstrapApp.java src/com/sysmlfrontend/backend/AppConfig.java src/com/sysmlfrontend/backend/ServerRunner.java src/com/sysmlfrontend/backend/server/Json.java src/com/sysmlfrontend/backend/server/ModelStore.java src/com/sysmlfrontend/backend/server/ModelXml.java src/com/sysmlfrontend/backend/server/HierarchyLevels.java src/com/sysmlfrontend/backend/server/UseCaseDocFormatter.java src/com/sysmlfrontend/backend/server/LocalXmlModelStore.java src/com/sysmlfrontend/backend/server/WebServer.java src/com/sysmlfrontend/backend/server/RhapsodyConnector.java src/com/sysmlfrontend/backend/server/FileDialogHelper.java

# Full tier (adds ModelServer + RhapsodyModelStore + the vendored ECAD services — see bug #5 above
# for why RhapsodyModelStore depends on them — needs rhapsody.jar):
javac --release 25 -cp lib/rhapsody.jar -d out src/com/sysmlfrontend/backend/BootstrapApp.java src/com/sysmlfrontend/backend/AppConfig.java src/com/sysmlfrontend/backend/ServerRunner.java src/com/sysmlfrontend/backend/ModelServer.java src/com/sysmlfrontend/backend/server/Json.java src/com/sysmlfrontend/backend/server/ModelStore.java src/com/sysmlfrontend/backend/server/ModelXml.java src/com/sysmlfrontend/backend/server/HierarchyLevels.java src/com/sysmlfrontend/backend/server/UseCaseDocFormatter.java src/com/sysmlfrontend/backend/server/LocalXmlModelStore.java src/com/sysmlfrontend/backend/server/RhapsodyModelStore.java src/com/sysmlfrontend/backend/server/WebServer.java src/com/sysmlfrontend/backend/server/RhapsodyConnector.java src/com/sysmlfrontend/backend/server/FileDialogHelper.java src/com/ibm/rhapsody/samples/plugin/model/ECADContext.java src/com/ibm/rhapsody/samples/plugin/services/ModelElementService.java src/com/ibm/rhapsody/samples/plugin/services/StereotypeService.java src/com/ibm/rhapsody/samples/plugin/services/DiagramService.java src/com/ibm/rhapsody/samples/plugin/util/ErrorHandler.java src/com/ibm/rhapsody/samples/plugin/util/FileValidator.java
```

Both tiers compile cleanly. `--release 25` targets class file version 69 (Java 25) rather than
whatever this dev machine's own JDK happens to be (JDK 26.0.1, class file version 70) — found live:
a target deployment machine with only OpenJDK 25.0.4 installed failed with
`UnsupportedClassVersionError` against classes built without this flag. `--release` also compiles
against Java 25's own API surface (not 26's), so this only needs revisiting if the code ever
actually requires a newer-than-25 API. `backend/lib/rhapsody.jar` is kept in sync with the real
installed jar (`C:\Program Files\IBM\Rhapsody\10.0.3\Share\JavaAPI\rhapsody.jar`) for accurate
compilation — at runtime `BootstrapApp`/`ModelServer` always load the jar from wherever
`config.ini`'s `installDir` actually points, not this copy.

### Running

```bash
backend\start.bat [path\to\config.ini]
```

Defaults to `config.ini` in `backend/`. **This machine now has Rhapsody 10.0.3 installed**
(`C:\Program Files\IBM\Rhapsody\10.0.3`) and `config.ini`'s `installDir` is set to it, so a normal
run attempts Rhapsody mode by default, falling back to local mode if that install ever becomes
unreachable. Comment `installDir` back out to force local mode unconditionally.

### Real bugs found and fixed by testing against the live Rhapsody 10.0.3 install

Nothing below was discoverable without an actual Rhapsody instance — worth re-checking if the
install path, version, or edition ever changes:

1. **Native library path was wrong.** `rhapsody.dll` (what `RhapsodyAppServer` actually
   `loadLibrary`s) does **not** live in the install root on this version — it ships next to
   `rhapsody.jar` itself, at `<installDir>\Share\JavaAPI\rhapsody.dll`. `BootstrapApp` now puts
   *both* that directory and the install root on `java.library.path` (path-separator-joined) so it
   works regardless of which layout a given install uses.
2. **`rhapsody.exe` needs `-lang=cpp` (or similar) to launch unattended.** Without it, Rhapsody
   shows an interactive perspective picker (C/C++/Java/Ada/SysEng/...) on first launch and never
   registers as a COM automation server — a fully automated launch just times out. Found the
   correct flags (`-dev_ed -lang=cpp`) by inspecting the target/arguments of the "Rhapsody 10.0.3
   Developer C++" Start Menu shortcut. Configurable via `[Rhapsody] launchArgs` in `config.ini`
   (default `-dev_ed -lang=cpp`) — change `-lang=` if a different edition/perspective is wanted.
3. **The Rhapsody `Project` root object doesn't implement `addClass`/`addActor`/`addUseCase`**
   via automation, even though `IRPProject extends IRPPackage` which declares them — calling them
   on the root throws `"Method addClass not implemented for Project."` at runtime (a COM dispatch
   restriction the Java interface doesn't reflect). Fixed in `RhapsodyModelStore.containerFor()`:
   all root-level creation is redirected through one hidden, find-or-created package named
   `SysMLFrontendData`. Its contents are flattened into the root's children/actors/useCases on
   read (same mechanism already used for legacy foreign packages), so it never appears as a node —
   "no Package concept" holds from the user's perspective even though Rhapsody itself required one
   under the hood.
4. **Rhapsody GUIDs can contain spaces** (e.g. `"GUID 4303db76-6f99-..."`, `"OLDID 1424988 98"`).
   Used unencoded as a URL path segment this breaks the request. Fixed on the frontend
   (`api/client.ts`): every guid interpolated into a URL path now goes through
   `encodeURIComponent(...)`. (The backend needed no change — `HttpExchange.getRequestURI().getPath()`
   already auto-decodes percent-escapes.)
5. **A plain (non-`Block`-stereotyped) `Class` can't host a `Port` at all in a project where the
   real SysML profile is loaded** — `addNewAggr(portMetaType, name)` on one always failed
   `"Can't add aggregate of type Port. Cannot modify read only element (or element with read only
   owner) $OMROOT\Profiles\SysML\SysMLProfile_rpy\SysML.sbs."`, no matter the container or
   stereotype-application method. Four hypotheses were tried and live-disproven, in order, before
   this was found — worth remembering so they aren't re-tried:
   - Giving the port an interfaceBlock contract unconditionally (not just when the user supplied an
     explicit `type`) — no effect, since the failure happens *inside* `addNewAggr` itself, before a
     contract could ever be set.
   - `ModelServer`'s connector unconditionally calling `IRPApplication.addProfileToModel("SysML")`
     on every connect — turned out to be a red herring for this specific error (this project's
     template already had the profile baked in, so the call was never the actual cause), though
     disabling it remains good practice regardless: the port/proxyPort/interfaceBlock/view
     mechanism here never needed the real profile applied by *this app* — it's stamped entirely via
     `addStereotype`/`addNewAggr("Tag",...)`. `[Rhapsody] sysmlProfile` now defaults to empty
     (never called) instead of `"SysML"`.
   - Redirecting the port's actual `addNewAggr` call onto a fresh, ad-hoc interfaceBlock instead of
     the original owner classifier (on the theory that the *owner* was somehow tainted) — no
     effect, identical error even on a completely untouched class.
   - Applying the `"proxyPort"` stereotype via `addSpecificStereotype` on an *existing* stereotype
     (found via `findNestedElementRecursive`) instead of the ad-hoc `addStereotype(name, metaType)`
     — no effect either, since (again) the failure is in `addNewAggr` itself, which runs first.
   - **Actual root cause**, confirmed live: every architecture element must carry the SysML
     `"Block"` stereotype (in addition to this app's own level stereotype — System/Subsystem/...) —
     a plain `Class`, even one this app itself created and fully controls, isn't a valid Port host
     once the real profile is loaded. Fixed in `createArchitectureElement`'s `ensureBlockStereotype`:
     applies `stereotypeService.getBlockStereotype()` (an *existing*, pre-loaded stereotype — see
     below — not an ad-hoc `addStereotype("Block", ...)`, which would hit the exact same read-only
     conflict `"proxyPort"` did) to every element, both on fresh creation and when re-matching an
     existing one by `sourceGuid` (so re-exporting a model created before this fix still heals it).
   - Port creation itself (`createPort`) now delegates to **`ModelElementService#addOrGetPort`**,
     vendored verbatim from `D:\KI\plugin\ECAD` into `backend/src/com/ibm/rhapsody/samples/plugin/
     {model,services,util}/` (see below) — after several rounds of this app's own hand-rolled
     reimplementations of the "right" sequence kept failing identically, calling ECAD's actual,
     unmodified, independently-proven-working method removed all risk of a subtle reimplementation
     bug. `RhapsodyModelStore` constructs a `StereotypeService` once (`loadStandardStereotypes()`,
     in its own constructor) and a `ModelElementService`, both passed to `addOrGetPort` alongside a
     find-or-created `"ib" + portName` interfaceBlock (ECAD's own naming convention). **Important**:
     `addOrGetPort(parentClass, interfaceBlock, ...)` creates the port as a normal, natively-owned
     member of `parentClass` — the interfaceBlock is only ever used as the port's `contract`, never
     as its physical container. An earlier attempt misread this and tried redirecting the port's
     actual containment onto the interfaceBlock (with ownership tracked via a Tag, mirroring how
     Capabilities/Functions/UseCases work) — creation still succeeded, but nothing was ever
     findable back through `getPorts()` again, since ports were never redirected like that in the
     first place. Reverted; `portsOf`/`getPorts` read a classifier's ports natively
     (`classifier.getPorts()`), same as originally. `IRPActor` (Context tab Actors) can't be passed
     to ECAD's `IRPClass`-typed `addOrGetPort` directly (`IRPActor` extends `IRPClassifier`, not
     `IRPClass`) — handled by an inline equivalent of the same three-call sequence, generalized to
     `IRPClassifier`.
   - Verified live end-to-end against a project with the real SysML profile loaded: full
     create → export → re-read cycle, top-level ports AND nested/decomposed ones, all with correct
     name/direction/type/view.

### Ports: ProxyPort, 4 views, nested decomposition — verified pattern (not guessed)

The earlier `"FlowPort"`/`IRPSysMLPort.setType()` approach was wrong on two counts: `"FlowPort"`
isn't a real metaclass at all (absent from `metaclasses.txt`), and that's why port direction never
used to take effect. The actual pattern was cross-checked against `D:\KI\plugin\ECAD`'s
`ICDExporter`/`ModelElementService`/`StereotypeService` — a working, independently-developed SysML
reader/writer for the same class of model — and then verified end-to-end against a live Rhapsody
10.0.3 instance (create → read → nested create → export → import, all confirmed correct). As of
bug #5 above, top-level port creation goes further than cross-checking: `ModelElementService` and
`StereotypeService` (plus their `ECADContext`/`DiagramService`/util dependencies) are vendored
verbatim into `backend/src/com/ibm/rhapsody/samples/plugin/{model,services,util}/` and called
directly from `RhapsodyModelStore` (`modelElementService.addOrGetPort(...)`), rather than
re-derived — several rounds of this app's own reimplementation of "the same sequence" kept failing
identically in a project with the real SysML profile loaded, and calling ECAD's actual code turned
out to be the only way to rule out a subtle divergence. Included in both backend `javac` compile
commands below.

- A port is `classifier.addNewAggr(portMetaType, name)` with `portMetaType = "Port"` (a real
  metaclass) → a plain `IRPPort`, **not** `IRPSysMLPort`.
- Tagged as a ProxyPort via `addStereotype("proxyPort", portMetaType)` (lowercase, matching ECAD's
  convention; ad-hoc-created if a project doesn't already define it — no profile required).
- Typed via `IRPPort.setContract(interfaceBlock)`, where `interfaceBlock` is an `IRPClass`
  stereotyped `"interfaceBlock"`, find-or-created by name (`findOrCreateInterfaceBlock()`).
- **Direction** (In/Out/InOut) has no native `IRPPort` property — stored as a Tag named
  `"Direction"` (`element.getTag`/`addNewAggr("Tag",...)`/`IRPTag.setValue`, same pattern ECAD uses
  for its own custom attributes).
- **View** (Operational/Functional/Logical/Physical — this app's own classification, not part of
  the ECAD-verified pattern) is a same-named Stereotype on the port, same mechanism as the
  hierarchy levels.
- **Nested ports (interface decomposition) are NOT children of the port element.** They are ports
  owned by the port's own `interfaceBlock` contract (`contract.getPorts()`) — exactly mirroring
  ECAD's `ICDExporter` recursion (`buildPortSection` → `contract` → `getProxyPorts(contract)` →
  ...). Creating a "nested" port passes an existing port's GUID as the owner; `resolvePortContainer()`
  redirects that to the port's contract, auto-creating one if the port doesn't have one yet.
  Live-verified 4 levels deep (Operational → Functional → Logical → Physical), matching the
  intended progression, though the backend doesn't enforce any particular view order.
- **interfaceBlocks must be filtered out of the visible architecture tree.** They're auxiliary
  port-typing classes living in the same hidden default package as top-level architecture elements
  (see `containerFor()` below), so without an explicit filter (`collectArchitectureChildren` skips
  any `IRPClass` stereotyped `"interfaceBlock"`) they'd double up in the tree/export — found live:
  a decomposed port's interfaceBlocks appeared both correctly nested under their owning port *and*
  as spurious top-level siblings before this filter was added.

`LocalXmlModelStore` mirrors the same *shape* (each port has `view` + nested `children` = its
decomposition) without Rhapsody's interfaceBlock indirection — a nested port there is just added
directly to the parent port's own `children` list.

### Further Rhapsody-mode modeling requirements (domain-expert guidance, not guessed)

Four more requirements for how `RhapsodyModelStore` should shape the resulting Rhapsody model,
beyond what was needed to just get creation working (bug #5 above) — all Rhapsody-only, no
`LocalXmlModelStore`/frontend impact:

- **Every architecture element's hierarchy-level stereotype (`SystemOfSystem`/`System`/`Subsystem`/
  `Equipment`) is applied via `applyStereotypeSafely`, not the plain `addStereotype(name,
  metaType)`** — same reasoning as `ensureBlockStereotype` (bug #5): reuses an existing,
  possibly-profile-owned stereotype of that exact name instead of trying to ad-hoc create one,
  which is what a same-named read-only profile stereotype would otherwise conflict with.
- **Every one of the four architecture views gets its own package**: `viewPackage("Operational" |
  "Functional" | "Logical" | "Physical")`, find-or-created directly under the project root — a
  real, immediately visible top-level package in Rhapsody's own Model Browser. **Not** nested
  inside the hidden default package (`containerFor`'s `SysMLFrontendData`) the way
  `findOrCreateInterfaceBlock`'s own fallback (no explicit view) still is — found live: nesting
  view packages inside the hidden one made them invisible/hard to find in practice ("es gibt
  keinen Ordner Operational"), even though they existed. The `addClass`/`addActor`/`addUseCase`
  restriction that makes the hidden-package workaround necessary for *those* (see `containerFor`'s
  javadoc — "Method X not implemented for Project") does **not** apply to `addNestedPackage`, which
  `IRPProject` inherits from `IRPPackage` same as any other package — confirmed live, a package
  added directly to the project works fine. Still reached by `collectArchitectureChildren`'s
  existing recursion into every sub-package regardless of nesting depth, so nothing else needed to
  change to keep this app's own "no Package concept" flattening intact. Two kinds of content are
  routed into these:
  - A port's interfaceBlock (`findOrCreateInterfaceBlock(name, view)`) — goes into the package
    matching that specific port's own `view`. Still shared/reused project-wide by name regardless
    of which view first created it (`findClassifierByName` searches the whole project), matching
    the pre-existing "shared reusable type" semantics for a port's `type` field.
  - A root-level `FunctionalNode`/`LogicalNode`/`PhysicalNode` (`containerForKind`) — goes into
    `"Functional"`/`"Logical"`/`"Physical"` respectively instead of the generic hidden package.
    System Structure elements (`System`/`Subsystem`/`Equipment`/`SystemOfSystem`) go into
    `"Operational"` — Structure and Operational share the same tree/instances (see `positions`'s
    per-view design above), and Operational is the one of the two that's an actual SysML
    architecture view, so that's the package this tree's Blocks belong under. Only the single
    topmost element of the whole tree is ever routed this way — `containerForKind` is only called
    for root-level creation (`parent instanceof IRPPackage`); everything nested below that stays a
    nested classifier of its own parent (`((IRPClass) parent).addClass(name)`, see
    `createArchitectureElement`), which already transitively keeps it "inside" whichever package
    the top-level ancestor lives in, without needing its own separate routing.
  - This is create-time routing only — a root element created *before* this feature existed stays
    wherever it already was (e.g. `SysMLFrontendData`, confirmed live for elements from earlier in
    this project's own history), it isn't retroactively moved into its view package by a later
    read or re-export. Deliberate: this project already tried and abandoned a generic Rhapsody
    reparent/move operation as too risky (see the "Known gaps" bullet on that below) — retroactively
    relocating existing elements would need exactly that. Move any old ones by hand in the Rhapsody
    GUI (an ordinary, safe drag-and-drop there) if wanted.
  - `createActor`/`createUseCase` route the same way, not just `createArchitectureElement`: both
    (Context/Capabilities tabs — no per-element nesting, always effectively root-level) previously
    went to the generic hidden default package via `containerFor`, same bug as Blocks/
    interfaceBlocks before this section's routing existed. `createUseCase` goes into
    `viewPackage("Operational")` (a Capability/UseCase is an Operational-architecture concept,
    same reasoning as System Structure Blocks above). `createActor` goes into `kontextPackage()` —
    a package named `"Kontext"` nested *under* `"Operational"` (not a standalone top-level one,
    tried first and corrected). Verified live: a fresh Actor's own containment path is
    `flexis / Operational / Kontext / <name>`.
- **Every port must have an interfaceBlock contract, immediately at creation** — not just when the
  user supplied an explicit `type` (bug #5 already fixed that for top-level ports via
  `ModelElementService#addOrGetPort`, which sets one unconditionally) — a *nested/decomposed* port
  additionally needs one right away too, not only lazily once *its own* first child gets added
  (`resolvePortContainer`'s pre-existing fallback) — a leaf nested port with no children of its own
  would otherwise never get one at all. Fixed in `createPort`'s nested-port branch: immediately
  calls `findOrCreateInterfaceBlock("ib" + name, view)` + `setContract`, same `"ib" + portName`
  naming ECAD itself uses.
- **Every System Structure architecture element needs an Internal Block Diagram showing its
  children as aggregated parts, plus one Block Definition Diagram per tree showing every
  Block in it** — Rhapsody's own nested-classifier containment (`parentClass.addClass(name)`, used
  for the System/Subsystem/Equipment hierarchy) is a *namespace* concept, not a SysML "part"/
  aggregation relationship, and doesn't by itself produce any diagram. Implemented in
  `addAggregationPart(parent, child)`, called from `createArchitectureElement` right after `child`
  is created:
  - **Composition + IBD**: `parent.addRelationTo(child, "", "Composition", "", "", "Association",
    "", "")` establishes the Composition association directly between the two already-live class
    references (Rhapsody then auto-creates a default `"its" + childName` instance for it);
    `DiagramService#createIBD` find-or-creates `parent`'s own Internal Block Diagram;
    `ModelElementService#getInstance` finds that auto-created instance; `DiagramService#
    addPartToIBD`/`isPartInIBD` place it on the diagram (skipped if already present), positioned by
    an incrementing X offset (`(parent.getNestedClassifiers().getCount() - 1) * 150`) so siblings
    don't overlap. ECAD's own `XMLImporter` "child assembly" pattern creates its analogous
    composition differently — via `IRPPackage#addGlobalObject("its"+name, name, packageName)`
    first, then relating to `relation.getOtherClass()` — but that only works there because ECAD's
    assemblies are flat siblings within one package, so `addGlobalObject`'s own by-name class
    lookup (scoped to that package) finds them; this app's children are nested classifiers of their
    own parent, not siblings in a package, so that lookup can't find `child` there — reproduced
    live, `addGlobalObject` produced a broken reference that failed on the very next call touching
    it ("Rhapsody object deleted"). Relating directly to `child` (already a live reference, no
    re-lookup needed) avoids that lookup entirely and works.
  - **BDD**: one Block Definition Diagram (Rhapsody: `IRPObjectModelDiagram`, stereotyped
    `"Block Definition Diagram"` — confirmed live as an existing real stereotype in this profiled
    project, applied via `applyStereotypeSafely` same as `Block`/`proxyPort`) **per tree**, owned by
    (`addNewAggr("ObjectModelDiagram", ...)` on) the topmost System/SystemOfSystem ancestor
    (`topLevelAncestor()`, walking up via `getOwner()` until the owner is no longer an `IRPClass`)
    — not one per parent, and not package-owned, both tried and corrected first. Contains that root
    plus every descendant added to the tree so far (`addBlockToBDD`, idempotent, grid-positioned by
    how many nodes are already on the diagram), growing incrementally as more children are created
    anywhere under it, live-verified 3 levels deep. No explicit edge for the Composition
    association: `addNewEdgeForElement(...)` here reproducibly threw `"Rhapsody operation failed"`
    (isolated live via a standalone diagnostic reproducing every step of this method individually —
    every step up to and including adding both nodes to the BDD succeeded, only the edge call
    itself failed; `addNewEdgeForElement` is evidently meant for `IRPLink` connectors between
    instances/ports — `DiagramService#createConnector`'s own usage, an IBD/instance-level concept —
    not a class-level `IRPRelation`/association on a BDD). Not needed anyway: Rhapsody's own diagram
    rendering shows an existing association automatically once both ends are visible on the same
    diagram.
  - Verified live end-to-end: parent + child creation via the API produces no error and reads back
    correctly; the resulting Composition/IBD/BDD structure itself isn't inspectable through this
    app's own JSON tree (it has no diagram concept), only by opening the project in Rhapsody itself
    — confirmed there via a standalone diagnostic script attaching independently to the same live
    Rhapsody instance (`RhapsodyAppServer.getActiveRhapsodyApplication()`), not through the app.
  - **Caution found live, unrelated to this feature's own logic**: repeatedly calling `/api/
    loadModel` against an *already-open* project (many times, over one long debugging session) at
    one point left several packages/elements — `SysMLFrontendData` and its contents, Actors —
    absent from the live in-memory session, even though their on-disk `.sbsx` subsystem files were
    confirmed still fully intact and untouched. Not data loss: closing and reopening the project
    once cleanly in the Rhapsody GUI restored everything. Cause not fully root-caused; if it
    recurs, check each package's own `.sbsx` file timestamp/size before assuming anything is
    actually gone.
  - **A second, more severe incident of the same class**: mid-session, Rhapsody itself crashed
    outright (not just a stuck dialog) while the user was working live in the GUI and the assistant
    reconnected via `/api/loadModel` around the same time — the backend's own console log showed
    `"Opened Rhapsody project: flexis"` printed **twice** in quick succession right before every
    subsequent call (including plain `GET /api/architecture`) started failing with
    `RhapsodyRuntimeException: Der Vorgang wurde erfolgreich beendet.` ("the operation completed
    successfully" — a paradoxical error string, consistent with a wedged COM connection rather than
    a real failure). Recovery: kill all `java` processes (`Get-Process java | Stop-Process -Force`;
    the backend does not recover from this on its own, no amount of waiting helped), have the user
    restart Rhapsody and confirm the project reopens intact, then restart the backend and do
    **exactly one** clean reconnect — coordinated explicitly with the user beforehand ("please
    don't also click Load Model right now") rather than assumed. No data was lost (Rhapsody
    persists incrementally to disk independent of the crash), but this confirms the underlying
    caution generalizes beyond "many repeated calls over a long session" to "two reconnects
    happening at the same time from different actors" — treat every `/api/loadModel` call as
    something to serialize with whatever else might be touching the same live session, not just
    something to rate-limit.
- **A Physical port's `type` (one of config.ini's `[Physical] interfaceTypes` — e.g. `"electrical"`,
  `"mechanical"`) is applied directly as a stereotype on the port itself** (`applyStereotypeSafely
  (el, type, portMetaType)`), not used to name/share an interfaceBlock the way every other view's
  `type` is — that's what lets a profile hang its type-specific tags off the port. The port's
  interfaceBlock stays the generic `"ib" + portName` one already set unconditionally at creation
  (see the bullet above) and is deliberately **not** renamed/shared by type for Physical ports,
  unlike every other view. Read back symmetrically in `typeOf()`: for a Physical port, `"type"` is
  whichever stereotype on the port is neither `"proxyPort"` nor one of `PORT_VIEWS` (this class
  doesn't have access to the configurable physical-types list to match against directly, but
  nothing else currently stereotypes a port) — for every other view, unchanged, read from the
  contract's own name. Verified live: a Physical port created with `type="electrical"` reads back
  `type:"electrical"` correctly, while its actual Rhapsody contract is the generic `ibPowerConn`
  and its stereotypes are `proxyPort` + `electrical` + `Physical`, confirmed via direct inspection.
- **A Function is a native Operation on the FunctionalNode's own class**
  (`IRPClassifier#addOperation(name)`/`getOperations()`), not a separate class of its own the way
  an earlier version of this modeled it (a plain `IRPClass` stereotyped `"function"`, living in the
  `"Functional"` package, ownership tracked via an owner-GUID Tag — the same "Rhapsody has no
  native ownership for this" workaround Capability-to-element links use, see `LINKED_OWNERS_TAG`
  below). An Operation genuinely *is* natively owned by its classifier, so none of that indirection is
  needed: `createFunction` now requires `parentGuid` to resolve to the FunctionalNode class itself
  (an `IRPClass`), and `getFunctionsOf` reads `ownerClass.getOperations()` directly — no
  owner-tag/whole-project search at all. `FUNCTION_STEREOTYPE`/its `collectArchitectureChildren`
  filter are kept only for legacy compatibility with any Function-as-class elements a project might
  already have from before this change; nothing new is ever created that way again. Verified live:
  a Function created via the API returns an actual `IRPOperation` instance owned by the target
  FunctionalNode.
  - **The `sourceGuid` re-import path needed the same migration handling separately** — found live,
    right after the above: a project already exported to before this change kept an orphaned
    `EnterVoiceData` *class* sitting in `SysMLFrontendData` ("es gibt immer noch eine Klasse"). The
    `existing instanceof IRPOperation` check only handled a match that was *already* an Operation;
    a still-class-shaped match (from before this change existed) fell through untouched, silently
    leaving it behind as a duplicate once a fresh Operation got created alongside it. Fixed: when
    `existing` is found but isn't an `IRPOperation`, `createFunction` now deletes it
    (`deleteFromProject()`) before creating the real Operation, which is then stamped with the same
    `sourceGuid` so any *future* re-import of the same function correctly matches the Operation
    instead. Verified live: deleted the stale class, recreated the same function through the normal
    API, confirmed only `OPERATION on Planning: EnterVoiceData` remains — no class.

### Capability → UseCase redesign (two-level, replacing the old flat model)

Capabilities were originally a flat list of UseCases, each attached directly to one architecture
element (mirroring how a port is owned by its Block). Reworked into a two-level model, mirroring
FunctionalNode→Function: a **Capability** is now its own top-level grouping (shown as its own box
in the Capabilities tab, like a FunctionalNode) that owns a list of **UseCases**; an architecture
element only ever **links** to an existing Capability (a reference, not ownership — the same
Capability can be linked from multiple elements). `ModelStore`'s interface was split accordingly:
`getCapabilities()`/`createCapability(name)` (top-level), `getUseCasesOf(capabilityGuid)`/
`createUseCase(capabilityGuid, name)` (UseCases owned by a Capability), and
`getCapabilitiesOf(ownerGuid)`/`linkCapability(ownerGuid, capabilityGuid)`/`unlinkCapability(...)`
(the reference from an element to a Capability). Both stores implement this identically in shape:

- **`LocalXmlModelStore`**: a new `CapabilityEntry` (guid, name, x, y, `linkedOwners` — a `List
  <String>` of linked element guids) alongside the existing `UseCaseEntry`, now re-pointed at its
  owning Capability (`capabilityGuid`) instead of an architecture element (`ownerGuid`). UseCases
  no longer carry their own canvas position (dropped `x`/`y`, matching `FunctionEntry` — they're
  rendered inline inside their Capability's box, same as Functions inside a FunctionalNode).
- **`RhapsodyModelStore`**: a **Capability is an `IRPPackage`**, find-or-created under
  `capabilitiesPackage()` (`"Capabilities"`, nested under `viewPackage("Operational")` — same
  placement rationale as `kontextPackage`). A **UseCase is native package containment**
  (`IRPPackage#addUseCase`/`getUseCases()`) inside that specific Capability's own package — no
  owner-tag indirection needed, unlike the old flat model. The **link** from an element to a
  Capability has no native Rhapsody relationship either, so it's a comma-separated list of element
  GUIDs in a single Tag (`LINKED_OWNERS_TAG`) stamped on the Capability package itself — same
  workaround class as `SOURCE_GUID_TAG`/`POS_*_TAG`, parsed/joined by `linkedOwners()`.
  `findBySourceGuidInPackage` also had to start checking a package's own tag directly (not just its
  contents) — a Capability is itself a package, so unlike Blocks/Actors/UseCases (found via their
  parent's `getClasses()`/`getActors()`/`getUseCases()` collections) there's no separate collection
  to search for "packages that are themselves the match".
- **`WebServer`**: `/api/capabilities` (GET list / POST create), `/api/capabilities/{guid}` (DELETE
  — cascades its UseCases), `/api/capabilities/{guid}/useCases` (GET/POST), `/api/useCases/{guid}`
  (DELETE), and on the element side `/api/elements/{guid}/capabilities` (GET linked / POST
  `{"capabilityGuid"}` to link) plus `/api/elements/{guid}/capabilities/{capabilityGuid}` (DELETE
  to unlink).
- **`ModelXml`**: capabilities are now written once under `<capabilities>` as `<capability>` (with
  its own `<useCase>` children), not per-element; an architecture element instead carries a
  `<capabilityLink guid="..." name="..."/>` reference. Since a link's guid must resolve to an
  already-created Capability, `importInto` now processes the whole `<capabilities>` section
  *before* `<architecture>` (previously architecture, context, then capabilities, in that order).
- **Frontend**: new `CapabilityNode.tsx` (mirrors `FunctionalNode`'s box-with-a-list shape) +
  `UseCasesSection.tsx` (mirrors `FunctionsSection`) for the Capabilities tab. The existing
  `CapabilitiesSection.tsx` (on an architecture element's own node) was reworked from "type a name
  to create a UseCase" into a picker (`<select>`) among already-existing Capabilities — it now only
  links/unlinks, it never creates a Capability itself (that only happens in the Capabilities tab).
  `App.tsx`'s `capabilities` state (top-level list, each with its `useCases`) is now fetched
  unconditionally on mount (like `architecture`), not gated on the active tab, since the
  Architecture tab's own link-picker needs the full list even before the Capabilities tab has ever
  been opened.

Live-verified end-to-end against the profiled `flexis` project: create Capability → create UseCase
inside it → link to an architecture element → confirm the link is embedded on that element's node
from `GET /api/architecture` → unlink → export XML (confirmed exact `<capabilities><capability>
<useCase/></capability></capabilities>` + `<capabilityLink>` shape) → delete Capability (confirmed
its UseCase was cascade-deleted too) → confirmed the rest of the real tree untouched throughout.
Also reproduced and fixed live through the actual browser UI (drag "Capability" onto the
Capabilities-tab canvas, "+ Use Case" inside it), not just the HTTP API directly.

**Re-importing an exported XML back into the *same* already-populated Rhapsody project used to
fail outright** — found live (not specific to Capabilities) importing a real user-saved
`flexis1.xml` back into the `flexis` project it came from: `"Can't add aggregate of type Package.
Cannot add Package due to a clash with an existing element."` for a Capability, then (after fixing
that) `"Can't add aggregate of type Class. Cannot add Class due to a clash with an existing
element."` for an architecture element, then `"Can't add aggregate of type Port. ... There is a
(name) clash with an existing Proxy Port ..."` for a nested port — three instances of the same root
cause, found and fixed one at a time as each was uncovered by the next live import attempt.
Root cause: `stampSourceGuid`/`SOURCE_GUID_TAG` is only ever stamped when an element is *itself*
created via XML import (`sourceGuid != null`); a normal interactively-created element (via the web
UI, no import involved) never gets tagged, so re-importing an XML exported from that same live
project can't match it by identity (`findBySourceGuid` searches Tag values, not native Rhapsody
GUIDs, even though the exported `guid` attribute — `el.getGUID()` — happens to equal the element's
native GUID) and falls through to creating a duplicate, which Rhapsody rejects outright for
Package/Class/Port. **Fixed** with a find-or-create-BY-NAME fallback, scoped to the one specific
container/parent a fresh element would land in (never a project-wide search — same "two originals
can collide on the sanitized/matched name" ambiguity as `findClassifierByName`'s own documented
first-match-wins limitation, accepted for the same reason):
`findOrCreateCapabilityPackage`/`findOrCreateUseCase` (added alongside the DisplayName fix above),
and newly added `findClassByNameDirect`/`findNestedClassByNameDirect` (root-level vs. nested branch
of `createArchitectureElement`) and `findPortByNameDirect` (the nested-port branch and the Actor
branch of `createPort` — the `IRPClass`-owner branch already gets this for free from ECAD's own
`ModelElementService#addOrGetPort`, which does its own find-or-get internally). Deliberately only
applied where a match is found: `addAggregationPart` (Composition + IBD + BDD placement) is skipped
entirely when an existing Class was re-matched instead of freshly created, since re-running it on
an already-related pair risks a duplicate Composition association and the IBD/BDD placement would
already be there from the original creation anyway. Verified live: `flexis1.xml` (a real export
containing a Capability, linked UseCase, and the full architecture/context tree) imported
successfully back into the same still-open `flexis` project with `elementsCreated`/`actorsCreated`/
`capabilitiesCreated`/`useCasesCreated` all > 0 and no error, where it previously failed at the
first of the three error types above. **Not extended to Actors or Functions** — not yet confirmed
whether `createActor`'s plain `kontextPackage().addActor(name)` or `createFunction`'s
`addOperation(name)` clash the same way on a duplicate name; fix the same way (find-or-create by
name, scoped to the specific owning package/classifier) if either turns out to.

**Package names are far more restricted than Class/Actor/UseCase names** — found live: creating a
Capability named `"defend static area"` threw `"Name 'defend static area' is illegal for element
of type Package"` (spaces rejected outright; likely other punctuation too). Since a Capability is
backed by an `IRPPackage`, `createCapability`/`renameElement` now route the user's original text
through `el.setDisplayName(name)` + `el.setIsShowDisplayName(1)` (Rhapsody's own native
"label differs from identifier" mechanism) while the actual Package `Name` is a sanitized form —
`sanitizePackageName()` replaces every character in `" .,/"` with `"_"`. Read back symmetrically:
`elementRef()` prefers `getDisplayName()` over `getName()` whenever one is set (every other kind
here never sets a DisplayName, so this is a no-op fallback to `Name` for them). `renameElement` is
generic across every element kind, so it specifically excludes `IRPProject` from the
Package-shaped-name branch (`IRPProject extends IRPPackage`, so a plain `instanceof IRPPackage`
check would incorrectly also sanitize the model root's own title). Verified live: created a
Capability named `"defend static area"`, reads back correctly; renamed it to `"Defend, Static/Area."`
(exercising space/comma/slash/period together), reads back correctly; renamed the model root itself
(a no-op rename to its own current name) to confirm the `IRPProject` exclusion still takes the
plain `setName` path.

### Domain-to-Rhapsody mapping (`RhapsodyModelStore`)

| Frontend concept | Rhapsody API |
|---|---|
| Model root | `IRPProject` itself, `"kind":"Model"` in the JSON tree — never rendered as a canvas node (see frontend section) |
| Architecture element | `IRPClass` ("Block"), created via `addClass` on a container (see `containerFor()` below for the root-creation workaround) — no direct `IRPPackage` involved from the user's perspective |
| Context (external system) | `IRPActor`, created via `addActor` (also through `containerFor()` when the parent is root) |
| Capability | `IRPPackage` under `capabilitiesPackage()`, created via `addNestedPackage` — owns a list of UseCases |
| UseCase (owned by a Capability) | `IRPUseCase`, created via `addUseCase` directly on that Capability's own package |
| Capability↔element link | No native relationship — a comma-separated GUID list in one Tag (`LINKED_OWNERS_TAG`) on the Capability package |
| Interface / port | See "Ports" above — `IRPPort` via `addNewAggr("Port", name)`, typed via `setContract`, direction via Tag, view + proxyPort via Stereotype |
| Element delete | Generic `IRPModelElement.deleteFromProject()`; the model root itself is guarded against deletion |
| Element lookup by id | `IRPProject.findElementByGUID`, searched across all open projects (1-based `IRPCollection`, same gotcha as SPREAD) |

#### Automatic hierarchy (`HierarchyLevels`, shared by both stores)

There is no free choice of level and no Package. `HierarchyLevels.childLevel(isRoot, parentLevel,
requestedKind)` (in `server/HierarchyLevels.java`, used by both `RhapsodyModelStore` and
`LocalXmlModelStore`) computes the level for every new element from its parent:

- Parent is the model root → **System** by default, or **SystemOfSystem** if the client explicitly
  requested it (the *only* point where the level is a real choice — SoS is optional).
  `RhapsodyModelStore` detects "parent is root" via `parent instanceof IRPPackage`;
  `LocalXmlModelStore` via `parent == root`.
- Parent is SystemOfSystem → child is **System** (automatic).
- Parent is System → child is **Subsystem** (automatic).
- Parent is Subsystem → child is **Equipment** (automatic, and a leaf — attempting to create a
  child of an Equipment throws a clear error).

In `RhapsodyModelStore`, the level is tagged as a same-named Stereotype
(`created.addStereotype(kind, levelMetaType)`, metaclass configurable via `[Rhapsody]
levelMetaType`, default `"Class"`) so it survives a round trip through Rhapsody. Reading it back
(`levelOf()`) uses `getStereotypes()` (not the deprecated singular `getStereotype()`) and takes the
first applied stereotype; an element with no matching stereotype (created outside this app, or a
pre-existing model) reads back as `"Block"`. Legacy `IRPPackage`s already in a model are still read
(flattened transparently into their parent's children, never surfaced as a node) but never created
by this app — see bug #3 above for why a hidden package still exists under the hood regardless.

**Every fresh model gets one default top-level aspect node per Functional/Logical/Physical view** —
`"System_F"`/`"System_L"`/`"System_P"` — created by `LocalXmlModelStore#reset` ("New Model") right
after clearing the tree, via the same `createArchitectureElement` any other creation goes through
(so they get a real guid, are exported/re-imported like any other element, etc.), just so those
three views aren't empty until the user manually adds their own first node. System Structure/
Operational deliberately have **no** such default — SoS-vs-System is a real, user-facing choice
there (see above), not a fixed starting point to default to. `RhapsodyModelStore#reset` still
throws unconditionally (no "blank new Rhapsody project" operation — see its own javadoc), so this
only ever originates in the local store; a model later promoted via "Export to Rhapsody" carries
these three over automatically, the same as any other local element at that point — no separate
Rhapsody-side logic needed. Verified via a standalone diagnostic constructing a
`LocalXmlModelStore` against a throwaway state path (never touching the running backend or its live
Rhapsody connection) and calling `reset("TestModel")`, confirming exactly these three children with
the right kind/name.

### Reparenting an existing architecture element (`moveElement`)

The earlier "no reparent/move operation" limitation (below) is resolved: `IRPModelElement` has a
real, documented `setOwner(IRPModelElement owner)` API for changing an element's containment —
found by reading `IRPModelElement.java` directly out of the jar, not the riskier `clone()`+delete
route the original gap assumed was the only option. `ModelStore#moveElement(guid, newParentGuid)`
moves an EXISTING element (keeping its own guid/kind/children/ports — a true move, not a copy) to
become a child of a different parent, or the model root.

- **Compatibility rule** (`HierarchyLevels#kindFamily`/`requireCompatibleMove`, shared by both
  stores): a move must stay within the same one of the four separate root-level trees — the three
  aspect kinds (FunctionalNode/LogicalNode/PhysicalNode) are each their own family, and every
  System-of-Systems-chain kind (SoS/System/Subsystem/Equipment, plus legacy "Block") shares one
  "Structure" family. At the root, only the kinds a fresh root-level creation could itself produce
  are allowed (SoS/System for Structure, any aspect kind for its own family) — an existing
  Subsystem/Equipment can never land directly at the root. Equipment is never a valid new parent
  (leaf). This never recomputes a level (unlike `childLevel` at creation time) — an existing
  element's kind is already fixed and a move doesn't change it, only its containment parent.
- **Cycle prevention** is each store's own responsibility (`LocalXmlModelStore#containsDescendant`,
  `RhapsodyModelStore#isSameOrDescendant`) — `HierarchyLevels` has no access to either store's
  actual tree to walk.
- **`RhapsodyModelStore`**: `cls.setOwner(...)` — the new owner is either `containerForKind(...)`
  (moving to root, same routing root-level creation itself uses) or the new parent `IRPClass`
  directly (nested, matching `addClass`'s own containment). Moving under an existing element ALSO
  calls `addAggregationPart` for the new parent/child pair — found live: without this, an element
  moved under a Functional/Logical/Physical aspect node (or any System-tree parent) had no
  Composition association/IBD part/BDD node at all, since it had originally been created at the
  root (root-level creation never calls `addAggregationPart` either — only the nested branch of
  `createArchitectureElement` does). `addAggregationPart` itself gained an idempotency guard
  (`hasCompositionTo`, via `IRPClassifier#getRelations()`/`IRPRelation#getOtherClass()`) so calling
  it again for an already-composed pair — e.g. re-moving the same element to the same parent, or
  backfilling BDD/IBD for elements moved before this extension existed — doesn't pile up duplicate
  Composition associations; the IBD/BDD placement were already idempotent. The element's PREVIOUS
  parent's own Composition/IBD placement is deliberately left untouched (stale but harmless —
  cleaning that up isn't attempted). Verified live end-to-end against the profiled `flexis`
  project: moved `LNInHouseConsole`→`System_L`, `test`→`System_P` (both via the API directly and
  through the actual frontend UI), confirmed via an independent standalone diagnostic
  (`RhapsodyAppServer.getActiveRhapsodyApplication()`, read-only) that `System_F`/`System_L`/
  `System_P` each ended up with a real BDD (all children as nodes), a Composition relation to each
  child, and an IBD — by re-running the same-parent move once this extension existed, to backfill
  elements moved just before it.
- **`LocalXmlModelStore`**: plain list surgery (detach from old parent's `children`, attach to new
  parent's) — no Composition/IBD/BDD concept to maintain there at all.
- **Frontend** (`MoveElementPicker.tsx`): a new "+ Existing Element" context-menu item (alongside
  "+ Child Element") opens a picker listing every element compatible with the clicked target
  (`utils/hierarchy.ts`'s `kindFamily` mirrors the backend rule client-side, computed from the
  already-fetched `architecture` tree — no extra endpoint needed) and moves the picked one there.
  **Found and fixed live, via `claude-in-chrome`**: the picker's first version used a native
  `<select size={8}>` listbox — with exactly one candidate (a common case, e.g. only one other
  FunctionalNode existing yet), the browser auto-highlights that sole option for display without
  firing a `change` event on click (nothing "changed" from the browser's point of view), so React's
  own `selected` state never updated and the "Move here" button stayed silently disabled — reported
  as "I can select it but nothing happens when I click Move here." Fixed by replacing the `<select>`
  with a plain `<div role="listbox">` of clickable rows (own `onClick` per row, no native
  select/option involved), sidestepping that whole class of browser quirk. Reproduced the exact bug
  and verified the fix live in the browser against the real `flexis` project before and after.

### BDD/IBD node positions mirror the frontend's own canvas layout

`addAggregationPart`'s BDD node placement (`addBlockToBDD`) and IBD part placement previously used
a fixed auto-incrementing grid, ignoring wherever the user had actually dragged that element on the
frontend canvas. Requested explicitly ("die selben Koordinaten vom Frontend auch in den bdd/ibd
verwenden") — now `scaledFrontendPosition(cls)` reads back `cls`'s own saved canvas position via the
same `POS_X_TAG_PREFIX`/`POS_Y_TAG_PREFIX` tags `readPositions` already reads, multiplied by a
configurable `[Rhapsody] diagramPositionScale` (config.ini, default `1.0`) — both `addBlockToBDD`
and the IBD placement call site fall back to their original grid position when the element was
never manually positioned in a view that mirrors onto the diagram (see `isDiagramPositionView`
below; nothing meaningful to mirror then). The scale factor exists because the user anticipated the
two coordinate systems might not agree on scale ("vielleicht müssen wir einen Skalierungsfaktor
verwenden") — left tunable rather than guessed, since judging the "right" factor requires actually
looking at the resulting Rhapsody diagram. Verified live end-to-end: created an isolated `PosTest`
FunctionalNode, set its Functional-view position to `(777, -444)` via the same `/api/positions`
endpoint the frontend itself uses, moved it under `System_F`, then confirmed via an independent
read-only diagnostic (enumerating `IRPGraphNode#getAllGraphicalProperties()` — the `"Position"` key,
not `getX()`/`getY()`, which don't exist on `IRPGraphNode`) that BOTH the resulting BDD node and IBD
part landed at exactly `Position=777,-444` with the default `1.0` scale, then cleaned up
(`deleteElement`) and confirmed the real tree unaffected.

### Dragging a node in the frontend also moves it live in Rhapsody's own BDD/IBD

The above only positioned a node at CREATE/MOVE time — a plain canvas drag afterward (`setPosition`)
only ever stamped the `POS_*_TAG` tracking tags, never touched the already-drawn BDD node/IBD part
Rhapsody had drawn earlier — reported live as "moving an element in the frontend isn't transferred
to Rhapsody on the fly." Fixed: `setPosition` now also calls `updateDiagramPositions(cls, x, y)`
whenever the drag's own view is one `isDiagramPositionView(kind, view)` says mirrors onto that
element's diagrams — which moves the existing BDD node (`findExistingBDD` + `findGraphNode`, both
read-only finds, never create-if-missing — a drag shouldn't spontaneously create a BDD) and the
existing IBD part (`DiagramService#getIBD` on the element's own PARENT, since the part represents
the "its"+name instance living on the PARENT's IBD) via `moveGraphNode`, which sets both the
`"Position"` graphical property (top-left corner) and the redundant `"Polygon"` one (all four
corners) to stay consistent — found via `IRPGraphNode#getAllGraphicalProperties()` since neither
`getX()`/`getY()` nor a dedicated move method exist on `IRPGraphNode`.

`isDiagramPositionView` initially only counted "Operational" for System-of-Systems-chain kinds (same
reasoning as view-package routing — Operational is the one of Structure/Operational that's an
actual SysML view) — corrected after live feedback ("in der operational view ändert sich etwas! ...
im System Structure diagramme habe ich die Elemente verschoben, aber ich sehe nichts in Rhapsody")
to accept EITHER "Structure" or "Operational" for that family, since both views render the exact
same tree and a user dragging in either naturally expects it to reflect on the Rhapsody side — each
view still keeps its own independent position for the frontend's own layout, this only changes
which of the two additionally pushes onto the single shared Rhapsody diagram (whichever was dragged
most recently wins there). `scaledFrontendPosition` was updated symmetrically to prefer
"Operational" but fall back to "Structure" when picking what to mirror at create/move time.

**Verified live, then found a real but separate issue while verifying**: checked that a small drag
of `Missile` in the Operational view produced an EXACT match between the frontend's stored position
and Rhapsody's actual `"Position"` property (`392,275` vs. frontend's `392.5, 275.1`) — confirming
the sync math itself is correct. The "big jump" the user actually saw was a different problem: they
had manually hand-positioned every OTHER node in Rhapsody to visually resemble the frontend layout,
but hand-eyeballed positions don't numerically equal the frontend's raw coordinates (different
zoom/origin) — so pushing one node's *exact* frontend coordinate made it land far from its
manually-approximated neighbors. Fixed with a one-time bulk resync (a Python script driving the
existing `/api/positions` endpoint for every element with a saved position in its
`isDiagramPositionView`-relevant view — no new backend code needed, this only re-exercises the
already-verified per-element path) that brought every node onto a consistent, frontend-derived
baseline at once; incremental drags stay consistent with each other from then on since they all
push the same coordinate space.

### Backend surfaces Rhapsody save failures to the frontend in real time (`isSaveHealthy`)

`RhapsodyModelStore#save()` already wrapped `activeProject().save()` defensively (a save hiccup
shouldn't fail an otherwise-successful API call — see its own javadoc) but only ever logged a
console warning on failure, easy to miss. Found live: a whole reparenting session's worth of
`moveElement` calls appear to have had their `save()` calls silently fail sometime before Rhapsody
crashed on its own shortly after (unrelated crash) — reopening the project afterward reverted to
the last state that WAS actually written to disk, silently losing everything only saved in memory,
discovered only much later when the tree unexpectedly looked flat again. Fixed: a `saveHealthy`
flag (`RhapsodyModelStore`) is now set false on any `save()` failure (true again once a save
succeeds), exposed via `GET /api/status`'s new `"saveHealthy"` field (`ModelStore#isSaveHealthy`,
default `true` — always true for `LocalXmlModelStore`, whose own auto-persist failures already
print a comparable console warning but have no equivalent live indicator need, being a plain file
write rather than a remote automation call that can silently wedge). The frontend polls
`/api/status` every 20s (previously only on mount and after specific actions) and shows a
persistent warning banner — distinct from the transient error banner — whenever `saveHealthy` is
false, telling the user to check Rhapsody before closing/reopening the project.

### A "duplicate" element can be a real orphaned object, not a stale connection — check GUID stability

Found live: `LNInHouseConsole` and `test` each briefly appeared TWICE in `GET /api/architecture` —
once correctly nested under `System_L`/`System_P`, once as an unexpected top-level sibling — with
genuinely DIFFERENT GUIDs each (confirmed by reading the raw JSON directly, not a display/traversal
bug). Initially suspected a stale/desynced backend connection (plausible given the day's other COM
instability), but the decisive test was checking whether the SAME two GUIDs still both existed
after a FULL Rhapsody restart and reopening the identical project file — they did, proving these
were real, disk-persisted objects, not an artifact of the current process's connection state. Likely
origin (not fully confirmed): an orphaned duplicate class left over from an early `flexis1.xml`
import attempt that failed partway through, from BEFORE the "Class name clash" fix (see the
`findOrCreateCapabilityPackage`/`findClassByNameDirect` section above) existed — silently sitting as
a direct sibling inside the "Physical"/"Logical" package (not nested under `System_P`/`System_L`,
so easy to miss unless that specific package is expanded directly rather than just browsing the
`System_P`/`System_L` subtree) until the user found and moved it by hand in Rhapsody's own GUI.
Lesson for next time this class of "did I lose/duplicate data" question comes up: compare GUIDs (not
just names) across a full Rhapsody restart — if they survive unchanged, the data is real, not a
connection artifact, however implausible the discrepancy with what's visible in the GUI looks.

**Caveat found immediately after, so this "lesson" isn't universal**: the model ROOT's own GUID
(`Flexis`/the project itself) has now been observed changing across TWO separate full-Rhapsody-
restart-and-reopen cycles in this same session (`ed86b572...` → `cc506f03...` → `57b27989...`),
even though the SAME `flexis.rpyx` file was reopened each time and the user confirmed nothing looked
wrong. So "GUID changed" does NOT reliably mean "real duplicate/lost data" — apparently at least
some GUIDs (the project root, seen so far) can legitimately be reminted on a full close+reopen of
Rhapsody itself, while element-level duplication (the actual `LNInHouseConsole`/`test` case above)
is still real. Treat a changed root GUID as expected/harmless; treat a changed or duplicated
NON-root element GUID as worth investigating the way this section did.

### Ports exist in the model but aren't drawn on the diagram — `showAllPorts()` was never called

Found live: "die Proxyports werden nicht mit all ihren nested ports angezeugt" turned out to be a
DIAGRAM RENDERING gap, not a model-data one — the underlying Port/interfaceBlock/nested-port object
graph was already exhaustively verified correct many times over (see the "Ports" section above), but
nothing in this app's own code ever called `IRPGraphNode#showAllPorts()`, so a class's ports existed
in the model yet were simply never drawn as graphical elements on its IBD. ECAD's own vendored
`DiagramService` already has exactly this logic in `populateIBD` (`private`, only reachable before
now via the bulk, `ECADContext`-driven `populateAllIBDs`) — calls `showAllPorts()` on every
`"DiagramFrame"` node (the diagram's own owning class, revealing ITS top-level ports) and every
already-present `"Port"`-typed node (revealing THAT port's own nested children) already on the
diagram. Widened to `public` (the only change to the vendored file — no logic touched) so
`RhapsodyModelStore` can call it per-diagram directly, via a new `refreshPortVisibility(ibd)`:

- Called from `addAggregationPart` right after `addPartToIBD`, and from `createPort`'s top-level
  (`owner instanceof IRPClass`) branch via a read-only `DiagramService#getIBD` find (never creates
  one as a side effect of adding a port — matches the same "genuinely optional find" pattern
  `updateDiagramPositions` already established).
- Calls `populateIBD` **twice**, not once: a single pass only reveals ONE new frontier — calling
  `showAllPorts()` on the `"DiagramFrame"` node synchronously creates brand-new `"Port"`-typed graph
  nodes for the class's own top-level ports, but `populateIBD`'s own node iteration is a snapshot
  taken BEFORE that call runs, so those newly-created Port nodes' own nested children aren't seen
  until a SECOND pass finds them already present. Not a generic fixed-point loop (deeper decomposition
  than 2 levels revealed per call site isn't handled) — each call site only ever adds one new
  frontier of ports at a time in practice (a fresh element only ever gets its own direct ports at
  create time; deeper nesting is added incrementally over separate `createPort` calls, each of which
  gets its own two-pass refresh).
- Verified live against the real `flexis` project's own `ibdFlexis` (no test data needed — this
  only changes what's DRAWN, not the underlying model, so read-only-safe against real data): before
  the fix, `ibdFlexis` had no `"Port"`-typed graphical nodes at all for `Flexis`'s own ports;
  triggered the refresh path via a harmless no-op re-`moveElement` (same parent) on `Missile`
  (which still routes through `addAggregationPart`); confirmed via an independent read-only
  diagnostic that `ibdFlexis` now shows `HEU`/`User` (top-level) AND their nested `JMessages`/
  `Voice`/`Display`/`Input` (one level of decomposition deep) all as visible `"Port"` graph nodes.

### Every element gets its own BDD/IBD immediately — not only once it has a first child

`addAggregationPart` (the only thing that creates/populates a BDD or IBD) only ever runs for a NEW
CHILD, and only touches the PARENT's IBD and the tree's shared BDD — it never creates the
newly-created CHILD's own (future) IBD. This meant a childless root element — `System_F`/`System_L`/
`System_P`, created as bare aspect-tree roots with nothing under them yet — had neither a BDD nor an
IBD of its own at all, reported live ("system_f, system_l und system_p müssen auch ein ibd und bdd
haben!"). Not actually root-specific: ANY newly-created element, anywhere in the tree, has the same
gap until it happens to get its own first child. Fixed generally, not just for roots:
`createArchitectureElement` now calls a new `ensureOwnDiagrams(created)` unconditionally (both the
root and nested branches, and even the sourceGuid-rematch path — idempotent, so safe on a re-matched
existing element too) — `topLevelAncestor(cls)` finds which tree-wide BDD `cls` belongs to (NOT
`createOrGetBDD(cls)` directly, since the BDD is one-per-TREE not one-per-element — see
`createOrGetBDD`'s own javadoc), adds both the tree root and `cls` itself as BDD nodes, and calls
`DiagramService#createIBD(cls, ...)` to give `cls` its own IBD ready for whatever children it gets
later. Verified live against the real, already-existing `System_F`/`System_L`/`System_P` (all three
childless at the time): re-issued their creation (`POST /api/architecture/elements` with the same
name/kind/parent — the existing find-by-name match path, so this only backfills, creates nothing
new — confirmed by the returned guids being identical to the pre-existing ones) and confirmed via an
independent read-only diagnostic that all three now have their own `bddSystem_F`/`ibdSystem_F`,
`bddSystem_L`/`ibdSystem_L`, `bddSystem_P`/`ibdSystem_P` respectively.

### `"its"+name` instance lookup must capitalize the class name's first letter

Follow-up, reported live right after the above: `System_P`'s BDD correctly showed `test` as a Block
node, but its IBD was missing `test`'s corresponding PART — while `System_F`/`System_L`'s IBDs
already had theirs (`itsPlanning`, `itsPerformMission`, `itsLNInHouseConsole`) correctly. Root
cause, found via a targeted diagnostic dumping `IRPClass#getNestedElements()`: Rhapsody's own
auto-generated Composition association-end instance name capitalizes the class name's first letter
regardless of the class's own casing — a class named `"test"` (lowercase) got an instance actually
named `"itsTest"`, not the naively-expected `"itstest"`. Every OTHER name in this project happened
to already start uppercase (`Planning`, `PerformMission`, `LNInHouseConsole`, ...), so
`modelElementService.getInstance(parent, "its" + child.getName())` coincidentally worked everywhere
except this one lowercase-named element — silently returning `null` for it instead of throwing,
so `addPartToIBD` (in `addAggregationPart`) and the live-drag position update (in
`updateDiagramPositions`) both just silently no-op'd rather than erroring. Fixed with a shared
`itsInstanceName(String className)` helper (`"its" + uppercase-first-letter + rest`), replacing
BOTH raw `"its" + name` call sites. The fact that the BDD had `test` correctly while the IBD didn't
is what pointed at a NAME-STRING lookup specifically being the defect — `addBlockToBDD` matches by
the model object's own GUID, never by a reconstructed name string, so it was never exposed to this
class of bug at all. Verified live: re-triggered the same `test`→`System_P` move again (idempotent)
and confirmed via an independent diagnostic that `ibdSystem_P` now shows `itsTest` (plus its ports
`J20`/`Mech1`, from the earlier `showAllPorts()` fix working together with this one).

### Interfaces (ports) are kept in sync as a single "Unikat" via their shared interfaceBlock

Requested live: reusing an interface (port name) should carry over all its settings, and editing
one should ideally update every other use of it — "wir verlinken... die interfaces so dass wir
immer nur ein unicat haben." Unlike a Capability, a Port can never literally be the same Rhapsody
object owned by two different classifiers — Rhapsody's object model requires a port to be a native
member of exactly one owner. So "Unikat" here means keep-in-sync-via-shared-contract, not true
object sharing: a port's `type` was ALREADY a shared, find-or-created-by-name interfaceBlock
(`findOrCreateInterfaceBlock`) — reusing the same port NAME with no explicit `type` already resolves
to the exact same interfaceBlock automatically (`"ib" + name`, unconditionally set at creation) —
but `direction`/`view` were only ever stored on each individual port, never synced across ports
sharing that same interfaceBlock. Fixed with two new tags stamped on the interfaceBlock itself
(`INTERFACE_DIRECTION_TAG`/`INTERFACE_VIEW_TAG`, the canonical "source of truth" for that
interface), wired into `applyPortSpec` (shared by both `createPort` and `updatePort`, so this
applies uniformly whether creating or retyping) via `syncInterfaceIdentity`:

- **Pull**: if a call doesn't explicitly supply `direction`/`view`, the interfaceBlock's own
  currently-stored value (if any) is applied to the port being created/updated — reusing an
  existing interface's name without re-specifying its settings adopts them automatically.
- **Push + propagate**: whichever `direction`/`view` end up effective are stamped back onto the
  interfaceBlock, then `propagateToSiblingPorts` applies them to every OTHER port anywhere in the
  project (any nesting depth, Actors, and nested/decomposed ports — `collectPortsByContract`
  mirrors `findBySourceGuidInPorts`' own traversal shape, including its cyclical-contract guard)
  whose own resolved contract is that same interfaceBlock.

No frontend changes needed — the existing free-text name field with autocomplete
(`knownInterfaces`) already surfaces "you've used this name before" suggestions; this backend
change is what makes reusing a name (or editing any one of its uses) actually keep every use in
sync, which the frontend has no way to do on its own (it only knows about ports already fetched
into the current session, not the whole project). Scoped to `RhapsodyModelStore` only —
`LocalXmlModelStore` has no shared-interfaceBlock concept at all (each port is a fully independent
leaf in its own tree), so equivalent local-mode support would need a separate, more invasive
redesign (a real top-level Interface registry, closer to how the Capability redesign works) if
ever wanted. Verified live with three isolated test elements (never touching real project data):
created two ports both named `"SharedIface"` (confirmed both resolved to `type: "ibSharedIface"`
automatically); changed one's direction to `"In"` and confirmed the other followed; changed one's
view to `"Functional"` and confirmed the other followed; created a THIRD same-named port with
`direction`/`view` left empty and confirmed it came back already `"In"`/`"Functional"` — pulled
from the interfaceBlock's own canonical values with no explicit input. Cleaned up the three test
elements afterward; the shared `"ibSharedIface"` interfaceBlock itself was deliberately left
behind rather than risk an extra live Rhapsody mutation for a purely cosmetic cleanup — harmless
(already filtered out of this app's own displayed tree, like every interfaceBlock) but findable
under the "Operational" package's own Model Browser view if ever worth deleting by hand.

#### Interfaces are scoped per view, with one explicit cross-view exception

Follow-up requested live after real data showed the gap: reusing `test`'s `"J20"` port kept a
different `type` (`"Connector"`) than `System_P`'s own `"J20"` (`type: "None"`) — never synced —
and separately: "wichtig ist die interfaces sind pro view/ebene operational, functional, logical
un physical" / "jede view hat ihre eigenen Interfaces! diese sollten auch nur innerhalb der view
wiederverwendet werden können! ausnahme sind externe interfaces von System_F, die überall
wiederverwendet werden dürfen" — confirmed to mean specifically **ports on the tree-root elements
themselves** (Flexis/System_F/System_L/System_P — not Actors, not any nested element). Two
independent gaps, fixed separately:

- **`findOrCreateInterfaceBlock` was a project-wide name search**, ignoring `view` for the lookup
  entirely (only used it to decide where a *newly-created* one gets placed) — an Operational
  `"optical"` and a Physical `"optical"` would have silently merged into one interfaceBlock (wrong:
  two conceptually different interfaces at different abstraction levels) had that scenario come up.
  Fixed: now takes a 3rd `boolean external` parameter. When `false` (the normal case), the search is
  scoped to `viewPackage(view)` only (`findClassByNameDirect`, no project-wide walk). When `true`
  (see next bullet), it widens to every view's own package via `findInterfaceBlockAcrossAllViews`
  before falling back to view-scoped creation.
  - `isRootLevelClass(IRPClass cls)` (`cls`'s own `getOwner()` is an `IRPPackage`, i.e. it's a tree
    root, not nested under another Block) and `isExternalPort(IRPModelElement portEl)` (same check,
    but starting from an existing port's own owner classifier, explicitly excluding a
    nested/decomposed port's owner — its own interfaceBlock contract, which would otherwise also
    look "root-level" by the same test) decide, per call site, whether `external` is `true`.
  - **The reusability itself is marked with a real, visible Rhapsody stereotype**
    (`EXTERNAL_INTERFACE_STEREOTYPE = "externalInterface"`, applied to the interfaceBlock alongside
    `"interfaceBlock"`), not left as a purely in-memory heuristic recomputed from ownership on every
    call — requested live: "am besten wir setzen für die externen Schnittstellen einen Stereotyp in
    Rhapsody". Visible directly in Rhapsody's Model Browser/stereotype label.
- **Physical view's `type` isn't interfaceBlock-based at all** (see the "Ports" section above — it's
  a stereotype stamped directly on the port), so it sits entirely outside the sync mechanism above.
  Fixed with a separate, name-keyed sync path: `syncPhysicalType(el, type)`, called from
  `applyPortSpec` whenever `view == "Physical"`. Mirrors `syncInterfaceIdentity`'s
  pull-effective-value/propagate-to-siblings shape, but matches candidates by
  `portEl.getName().equals(...)` AND `hasStereotype(portEl, "Physical")`
  (`collectPhysicalPortsByName`/`*InClassifier`/`*InPorts`, same traversal shape as
  `collectPortsByContract*`) instead of by shared contract GUID — there is no shared object to
  propagate through for a per-port stereotype. `setPhysicalTypeStereotype(el, type)` removes any
  prior non-`proxyPort`/non-`PORT_VIEWS` stereotype before applying the new one (previously,
  `applyStereotypeSafely` alone could stack an old and new type stereotype on the same port, and
  `typeOf`'s "whichever one comes first" read-back could then silently keep reporting the stale
  value).
  - **Real bug found and fixed live during verification**: `syncInterfaceIdentity`'s existing
    push+propagate step (direction/view) ran unconditionally, including for an `external`
    interfaceBlock — but an external interfaceBlock is deliberately shared *across different
    views on purpose*, so forcing direction/view to match is wrong by construction. Reproduced
    live: creating a Logical-view port on `System_L` reusing a Functional-view root port's
    interface name (`"TestScopeIF"`) force-flipped the *original* Functional port's own view to
    `"Logical"` (and its direction along with it) via this exact propagation path. First-pass fix
    made `syncInterfaceIdentity` skip its entire pull/push/propagate sequence whenever
    `hasStereotype(ibEl, EXTERNAL_INTERFACE_STEREOTYPE)` — **too broad, corrected in the very next
    round** (see below): that disabled DIRECTION sync too, even for the common case of an external
    interface reused entirely within one single view.
  - Verified live end-to-end, on real project data (`D:\soucres\Flexis\flexis.rpyx`):
    - `test`'s `"J20"` (`type: "Connector"`) and `System_P`'s own `"J20"` (`type: null`) — updating
      either one now updates the other; confirmed in both directions, then restored to the
      pre-existing `"Connector"` value on both.
    - A `"TestScopeIF"`-typed port created on `System_F` (root, Functional, `Out`) and a second one
      on `System_L` (root, Logical, `In`) both resolved to the same shared interfaceBlock (the
      cross-view "external" exception) while correctly keeping their own independent view — the
      exact scenario the propagation bug above broke, now confirmed fixed.
    - A third `"TestScopeIF"`-typed port created on `Planning` (a non-root Subsystem, Operational)
      resolved to its own separate, Operational-package-scoped interfaceBlock — confirmed
      unaffected by, and not merged with, the root-level external pair above, and unaffected when
      its own direction was changed afterward.
    - All test ports deleted afterward; `J20` restored to its original synced value; `saveHealthy`
      confirmed `true` throughout.

#### Follow-up: direction must still sync for an external interface, and Physical type needs a PULL step too

Reported live right after the above, against real (not test) data: `System_P` (root, Physical) has
an external interface `"optical"` (`type: "electromagnetic-optical"`); relinking it from `test`
(a non-root child of `System_P`, same Physical view) by creating a same-named `"optical"` port
there produced "eine Teilkopie mit einem neuen Type" (a partial copy with a different/empty type)
instead of adopting the original's type, and changing the original's direction (`InOut`→`In`)
never showed up on `test`'s copy at all. Two separate, previously-undiscovered gaps:

- **The first-pass "skip everything for an external ib" fix (previous section) was too blunt.**
  The whole point of marking an ib `external` is that it's *reusable cross-view* — but `System_P`
  (Physical) and `test` (also Physical) aren't actually a cross-view case at all; `test` merely
  happens to be non-root, so its default per-port contract lookup still resolves to the SAME
  `"iboptical"`/named interfaceBlock as the root one (view-scoped search finds it regardless of the
  `external` flag, since external only WIDENS the search, it doesn't change WHERE a match is
  placed). Skipping the sync unconditionally for any external ib broke this same-view reuse too,
  even though nothing about it crosses a view boundary. Fixed: `syncInterfaceIdentity` now only
  excludes **view** from pull/push/propagation when `external` — `effectiveView` is computed as
  `null` outright for an external ib, so it's never pulled onto a port, never stamped back onto the
  ib's own tag, and never propagated to siblings, exactly preserving the earlier fix's intent
  (different views of the same external interface legitimately keep their own view). **Direction**,
  however, is no longer gated on `external` at all — `effectiveDirection`/its propagation run the
  same way regardless, since a port's direction is a genuine "Unikat" setting the user wants shared
  across every reuse of an interface, cross-view or not.
- **`syncPhysicalType` never had a PULL step** — unlike `syncInterfaceIdentity` (which already
  pulled direction/view from the ib's own tag when a call didn't specify one), a Physical port's
  `type` has no shared contract object to pull from, and the original implementation only ever
  PUSHED `changedPort`'s own (already-applied) type outward to siblings — a freshly-created port
  reusing an existing interface's name with no explicit `type` given had nothing to push, so its
  type stayed empty. Fixed: `syncPhysicalType` now collects same-named Physical siblings FIRST, and
  if `changedPort` itself has no effective type, scans those siblings for the first one that already
  has a type and adopts it (`setPhysicalTypeStereotype`) before proceeding to the (unchanged)
  push/propagate step.
- Verified live end-to-end against the real, already-existing `"optical"` interface (not test data
  this time — `System_P`'s and `test`'s actual ports):
  - Updated `test`'s `"optical"` (`type: null`) with no `type` in the request body → correctly
    pulled `"electromagnetic-optical"` from `System_P`'s own `"optical"`.
  - Changed `System_P`'s `"optical"` direction `InOut`→`In` → confirmed `test`'s copy followed to
    `"In"` too (previously stayed `"InOut"`, unsynced).
  - Re-verified the cross-view case doesn't regress: recreated `System_F`/`System_L`
    `"TestScopeIFA"` pair (Functional/`Out` and Logical/`In`), changed each one's direction in turn,
    confirmed BOTH follow each other (direction now syncs even cross-view, as intended) while each
    kept its own `view` (`Functional`/`Logical`) untouched throughout. All test ports cleaned up
    afterward; `saveHealthy` confirmed `true` throughout.

#### Kind-group separation must also apply to Physical, and physical type needs a PULL step

Follow-up requested live after real data showed the gap: reusing `test`'s `"J20"` port kept a
different `type` (`"Connector"`) than `System_P`'s own `"J20"` (`type: "None"`) — never synced —
and separately: "wichtig ist die interfaces sind pro view/ebene operational, functional, logical
un physical" / "jede view hat ihre eigenen Interfaces! diese sollten auch nur innerhalb der view
wiederverwendet werden können! ausnahme sind externe interfaces von System_F, die überall
wiederverwendet werden dürfen" — confirmed to mean specifically **ports on the tree-root elements
themselves** (Flexis/System_F/System_L/System_P — not Actors, not any nested element). See the
git history/session log for the full multi-round derivation; the durable end state is captured in
the "Interfaces are individually reusable..." section below, which supersedes the intermediate
attempts.

### Nested interfaces within an external port's decomposition are THEMSELVES external

Follow-up requested live, after real data showed direction/view still weren't linking correctly:
"ich habe das nested interface voice von system_f nach planning importiert! warum werden die
Änderungen nicht weitergeleitet?" — root-caused to a mix-up between two DIFFERENT elements both
named `"Planning"` (`Flexis → Planning`, a Subsystem, vs `System_F → Planning`, a separate
FunctionalNode with no `HEU` port of its own) — but investigating it surfaced a genuine, deeper
design gap the user then articulated precisely: **"HEU.JMessages ist eine externe Schnittstelle
und HEU.Voice ist die 2. externe Schnittstelle! HEU ist der Container!"** and **"Top-level
Interface ist eine Collection von nested Interfaces, die auf Subsystemen einzeln verwendet
werden!"** — a top-level external port (`HEU`) is really just a CONTAINER; its own nested
decomposition (`JMessages`, `Voice`) are THEMSELVES individually-reusable external interfaces, not
inert detail scoped only to wherever they happen to be nested. Confirmed scope: reuse should carry
over BOTH the shared contract (enabling the existing direction "Unikat" sync) AND the interface's
own already-established view (forced onto the new user, since a non-root reuse has no view of its
own to protect) — reachable from ANY element, root or not (`"auf Subsystemen einzeln verwendet"`),
unlike the ordinary root-to-root `external` case (`TestScopeIF` on `System_F`/`System_L`), where
each root element's OWN occurrence deliberately keeps its own view (see the section above).

- **`isWithinExternalTree(owner)`** (`createPort`'s nested-port branch, and `resolvePortContainer`'s
  lazy-contract fallback): when creating a port NESTED under `owner`, its own default `"ib"+name`
  contract is now created with `external = isWithinExternalTree(owner)` instead of a hardcoded
  `false` — true when `owner` is itself a root element's own top-level port (`isExternalPort`), OR
  when `owner`'s OWN resolved contract already carries `EXTERNAL_INTERFACE_STEREOTYPE` (a deeper
  decomposition level under an already-external ancestor — recursive). This is what makes `Voice`
  (nested under `HEU`, itself external since `HEU` sits on root elements) get marked
  `EXTERNAL_INTERFACE_STEREOTYPE` on ITS OWN `"ibVoice"` contract, immediately at creation.
- **`isPortWithinExternalTree(portEl)`** (`applyPortSpec`'s type-resolution branch, replacing the
  narrower `isExternalPort`): the "am I myself already external" check for an EXISTING port being
  re-typed — `isExternalPort` alone only recognizes a tree root's own DIRECT top-level ports, missing
  a NESTED port (like `Voice`) whose own native owner is an interfaceBlock, not a root Class. Checks
  whether that owning interfaceBlock itself carries `EXTERNAL_INTERFACE_STEREOTYPE`.
- **`findOrCreateInterfaceBlock`'s new non-external fallback**: when a NON-external call (`external
  = false`) finds nothing in its own narrow, view-scoped search, it now ALSO tries
  `findExternalInterfaceBlockAcrossAllViews(name, view)` — a kind-group-wide search (same
  Physical-vs-logical separation as the root-to-root case) that ONLY matches an interfaceBlock
  ALREADY carrying `EXTERNAL_INTERFACE_STEREOTYPE` (deliberately picky — a coincidentally-named,
  never-external interfaceBlock must NOT be found this way, or every non-root element would risk
  accidentally merging into unrelated same-named interfaces, undoing the per-view scoping this file
  otherwise enforces). This lets ANY element (root or not) LINK to an already-established external
  interface by name; it still can't MINT a brand-new external identity on its own.
- **`syncInterfaceIdentity`'s `rootOwned`/`adoptEstablishedView` split**: `rootOwned =
  isExternalPort(changedPort)` (is the port ITSELF a root element's own top-level port).
  `adoptEstablishedView = external && !rootOwned` — a NON-root port that ended up on an external ib
  (via the fallback above) has no view of its own to protect and ADOPTS the ib's canonical
  `INTERFACE_VIEW_TAG` (falling back to whatever it was given only the very first time, bootstrapping
  the tag) — overriding whatever its own current tab/context implied, and re-stamping its own view
  stereotype if different. A ROOT-owned use of an external ib (`rootOwned=true`, e.g. `TestScopeIF`
  on `System_F`/`System_L`) is unaffected — view stays completely untouched, exactly as before.
  DIRECTION continues to sync/propagate in all cases, regardless of `external`/`rootOwned` (unchanged
  from the previous round).
- **Real bug found and fixed live during verification, twice**:
  - Deleting a port via `DELETE /api/ports/{guid}` does NOT delete its now-unused interfaceBlock
    contract — an earlier broken test port's orphaned `"ibVoice"` (created in the wrong, Functional,
    view package before this fix existed) was left behind, and its NARROW view-scoped search kept
    finding that stale duplicate before ever reaching the new external fallback, masking the fix
    entirely. Diagnosed and cleaned up via a standalone read-only-then-targeted-delete diagnostic
    (`RhapsodyAppServer.getActiveRhapsodyApplication()`, same pattern used throughout this file) —
    not something the backend needs to handle automatically (an orphaned interfaceBlock is otherwise
    harmless and already filtered out of the visible tree, matching the precedent set for
    `"ibSharedIface"` earlier), but worth remembering as a possible confounder if a similar rename/
    retype test ever seems to silently "not take effect" after a prior failed attempt.
  - Tested against genuinely stale BYTECODE, not stale MODEL DATA: a later source edit
    (`isPortWithinExternalTree` + `applyPortSpec`'s updated call site) was made AFTER the backend had
    already been recompiled-and-restarted for the EARLIER edits in this same round, so the running
    process never picked it up — confirmed via `Get-Process java | Select StartTime` predating the
    `.class` file's own last-modified time. Recompiling and restarting again (this time verifiably
    AFTER every edit) resolved it; worth checking process-start-vs-compile timestamps first next time
    a fix "verified working in isolation" appears to silently not take effect end-to-end.
- Verified live end-to-end against the real, already-existing `flexis` project:
  - Retroactively touched `HEU` (root, already-existing) and its nested `Voice` once each (an
    ordinary `PATCH /api/ports/{guid}` retype, not a new endpoint) to backfill
    `EXTERNAL_INTERFACE_STEREOTYPE` onto their pre-existing, pre-this-session contracts (`"ibHEU"`,
    `"ibVoice"`) — confirmed via a read-only diagnostic that both ended up correctly marked.
  - Created a fresh `"Voice"` port directly on `System_F → Planning` (the FunctionalNode, Functional
    view, non-root, `type` left blank) — confirmed it resolved to the SAME `"ibVoice"` contract as
    `HEU`'s nested `Voice` (`type: "ibVoice"` in the response) AND its own view came back
    `"Operational"` (forced/adopted), not `"Functional"` as requested — exactly the desired outcome.
  - Changed `HEU`'s nested `Voice` direction `In`→`Out` → confirmed ALL FIVE ports sharing
    `"ibVoice"` (Flexis's `HEU.Voice`, the Subsystem `Planning`'s `HEU.Voice`, `System_F`'s
    `HEU.Voice`, `PerformMission`'s own top-level `Voice`, and the newly-created `System_F →
    Planning`'s `Voice`) updated to `"Out"` together.
  - Re-verified no regression: recreated the `TestScopeIF`-style root-to-root pair
    (`System_F`/Functional/`Out` and `System_L`/Logical/`In`, both root elements) — direction still
    syncs between them, but each kept its OWN view untouched throughout, confirming `rootOwned` still
    correctly exempts genuine root-to-root external reuse from the new view-forcing behavior.
    Re-verified `System_P`/`test`'s `"optical"` (Physical) direction sync also still works unchanged.
  - All test/diagnostic artifacts cleaned up afterward (test ports deleted, `optical`/`Voice`
    directions restored to their pre-test values); `saveHealthy` confirmed `true` throughout.

### `openProject` avoided when the project is already open (`ModelServer#findAlreadyOpenProject`)

Found live, twice in one session: calling `IRPApplication#openProject(path)` when that exact
project is already open in the live Rhapsody session is a recurring source of instability — once it
left the in-memory session missing several packages/elements (see the "Caution found live" note
above), and once (right after a session that also included a full Rhapsody crash — unrelated to
this app, the user was working live in the GUI at the time) it wedged the COM connection entirely,
every subsequent call including a plain `GET /api/architecture` failing with
`RhapsodyRuntimeException: Der Vorgang wurde erfolgreich beendet.` ("the operation completed
successfully" — a paradoxical error string consistent with a wedged connection, not a real
failure) until the backend was killed and restarted. `ModelServer`'s connector now calls
`findAlreadyOpenProject(cachedApp, path)` first — matches an already-open `IRPProject` by name
(the path's own filename minus extension; `IRPProject` has no exposed accessor to compare full
paths against) via `IRPApplication#getProjects()` — and only falls through to `openProject` if
nothing matched, entirely avoiding the risky call in the common case where `/api/loadModel` is
called again against a project that's already open (e.g. the frontend reconnecting, or two people
working against the same backend around the same time).

**Known gaps:**

- `findClassifierByName`/`findOrCreateInterfaceBlock` (used to resolve a port's `type`) does a
  full recursive tree walk by name; it will misbehave if two classes share a name in different
  parts of the tree (first match wins).
- Root-level creation (Block/Actor/UseCase/interfaceBlock all going through `containerFor()`) and
  the port model (Port/proxyPort/interfaceBlock/Tag-direction/view-stereotype) are both verified
  live against Rhapsody 10.0.3. `portMetaType`/`levelMetaType`/`viewMetaType` remain configurable
  in case a different install/profile needs different metaclass names — check `metaclasses.txt` in
  the Rhapsody installation's `Doc` directory if creation ever fails or produces the wrong type.

### HTTP API (`WebServer`)

Single router under `/api/` (see the routing table in `WebServer.java`'s class javadoc for the
full list). `GET /api/status` includes `"mode":"rhapsody"|"local"` so the frontend can tell which
store is active. `GET /api/architecture` returns the whole hierarchy tree in one call;
`POST/PATCH/DELETE` under `/api/architecture/elements`, `/api/context/actors`,
`/api/elements/{guid}/ports`, `/api/ports/{guid}` cover create/rename/delete for each aspect —
**guids in these paths must be URL-encoded** by the caller (see bug #4 above). Capabilities are
two-level (see the redesign section above): `/api/capabilities` (list/create top-level),
`/api/capabilities/{guid}` (delete), `/api/capabilities/{guid}/useCases` (list/create UseCases
owned by that Capability), `/api/useCases/{guid}` (delete), and `/api/elements/{guid}/capabilities`
(list linked / POST `{"capabilityGuid"}` to link) plus `/api/elements/{guid}/capabilities/
{capabilityGuid}` (unlink). `POST /api/export` / `POST /api/import` (both `{"path": "..."}`) handle
the XML snapshot feature, working through `ModelXml` against whichever `ModelStore` is active.
Responses are JSON via the hand-rolled `Json` class (supports nesting, unlike SPREAD's
intentionally-flat `JsonUtil` — this API needs nested trees). CORS is wide open since the frontend
dev server runs on a different port than the backend.

### XML snapshot format (`ModelXml`)

Store-agnostic — works purely against the `ModelStore` interface (Map/List shapes), so it's shared
by both stores rather than duplicated, and is also what `LocalXmlModelStore` uses internally for
its own auto-persist/auto-load. Built with the JDK's own `javax.xml.parsers`/`javax.xml.transform`
(DOM + Transformer), no external XML library. Verified end-to-end: DOM round-trip (escaping,
nesting, ports) standalone, and the full create/export/import cycle against both a running local
backend and a live Rhapsody instance.

```xml
<sysmlModel>
  <architecture>
    <element kind="System" name="Vehicle">
      <position view="Structure" x="120" y="40"/>
      <position view="Operational" x="300" y="40"/>
      <port name="PowerIn" direction="In" type="ElectricalPower" view="Operational">
        <port name="PowerFunc" direction="In" type="PowerFuncInterface" view="Functional"/>
      </port>
      <capabilityLink guid="..." name="Defend Static Areas"/>
      <element kind="Subsystem" name="Engine"/>
    </element>
  </architecture>
  <context><actor name="GroundControl"><port .../></actor></context>
  <capabilities>
    <capability guid="..." name="Defend Static Areas" x="80" y="40">
      <useCase guid="..." name="Detect Threat"/>
    </capability>
  </capabilities>
</sysmlModel>
```

A Capability is written once under `<capabilities>` (with its own `<useCase>` children, see the
redesign section above), not per-element; an architecture element instead carries a
`<capabilityLink guid="..." name="..."/>` reference — `name` there is for readability only, `guid`
is what's actually used to re-link on import. Since a link's guid must resolve to an
already-created Capability, `<capabilities>` is imported before `<architecture>`.

An architecture element's canvas position is stored as one `<position view="..." x="..." y="..."/>`
child per Architecture-tab view it's been manually dragged in — not a single flat `x`/`y` — because
System Structure and Operational both render the exact same tree/guids, so a drag in one view must
not move the node in the other (`ModelStore#setPosition` takes a `view` argument for exactly this
reason; see `HierarchyLevels`/the "Automatic hierarchy" section above for the parallel
Functional/Logical/Physical trees, which only ever populate their own one view's `<position>`).
Actors/Capabilities have no view concept (the Context/Capabilities tabs have no views), so they
keep a single flat `x`/`y` attribute pair directly on `<actor>`/`<capability>` instead — a UseCase
(owned by a Capability, shown inline in its box) has no canvas position of its own at all, the same
as a Function inside a FunctionalNode. A pre-multi-view
export (flat `x`/`y` directly on `<element>`) still imports fine — it's applied to every view so an
old model doesn't visually jump on first load after upgrading, and a later drag in any one view
then naturally splits off just that view's position going forward.

Every element carries the source store's own `guid` (see `ModelStore`'s sourceGuid javadoc) so
re-importing the same XML updates matching elements in place instead of duplicating them; a target
store that can't adopt an external guid as its own (Rhapsody) instead tracks it via a Tag
(`SOURCE_GUID_TAG`) and mints its own native GUID regardless. Nesting still encodes parent/child
structure independent of guids; the level (`kind`) on import is only honored at the root
(SoS-vs-System) and is otherwise recomputed automatically from depth, same as any other creation. A
legacy `<package>` element (only ever produced by export, for foreign packages already in a
Rhapsody model) has its children flattened into its parent on import, since neither store creates
packages of its own outside the Capability/view-package mechanisms described above.

## Frontend (`frontend/`)

React 19 + TypeScript + Vite + React Flow (`reactflow` package). Scaffolded with
`npm create vite@latest -- --template react-ts`.

**Node.js is installed at `C:\Program Files\nodejs` but not on this shell's PATH** — prefix
commands with that directory (e.g. `set PATH=C:\Program Files\nodejs;%PATH%` or call
`npm.cmd`/`node.exe` by full path) or add it to PATH yourself.

```bash
cd frontend
npm install
npm run dev      # Vite dev server, http://localhost:5173
npm run build     # tsc -b && vite build
```

`VITE_API_BASE_URL` (`frontend/.env`, default `http://localhost:4567/api`) points the frontend at
the backend.

### Structure

- `src/types.ts` — domain types mirroring the backend's JSON shapes. `ArchLevel` is the four
  automatic hierarchy levels; `ArchKind` widens that with `"Block"` (read-only legacy fallback,
  Rhapsody mode only). The model root's own `kind` is `"Model"` and is a separate case entirely —
  see layout note below.
- `src/api/client.ts` — thin fetch wrapper, one function per backend endpoint. Every guid used in a
  URL path goes through `encodeURIComponent` (Rhapsody guids can contain spaces — see backend bug
  #4). `createArchitectureElement`'s `kind` param is optional — omit it to let the backend compute
  the level automatically; pass `"SystemOfSystem"` only when creating at the root.
- `src/App.tsx` — tab switching (Architektur/Kontext/Fähigkeiten), fetches per tab,
  builds React Flow `nodes`/`edges` from the fetched data. `architecture` (the model root) is
  fetched unconditionally on mount, not gated on the active tab, since its `guid` is the fallback
  parent for creation from every tab. Header shows the model title (click to rename) plus path
  inputs/buttons for Load Model and Save/Load XML. `layoutArchitectureTree` never renders a node
  for the root itself — `root.children` become the depth-0 nodes, each its own independent
  tidy-tree layout (hand-rolled, no dagre/elk dependency).
- `src/components/nodes/ArchitectureNode.tsx` — every node it renders is one of the four hierarchy
  levels (or legacy `"Block"`) and always shows `PortsSection`, since the root (the one kind that
  wouldn't own ports) is never passed to it.
- `src/components/nodes/PortsSection.tsx` — top-level port list for a Block/Actor + inline "add
  port" form (name/view/direction/type). Passes its `onAddPort` callback down to `PortRow`
  *unbound* (not pre-closed over the owning Block/Actor's guid) so it can be reused for nested
  creation.
- `src/components/PortRow.tsx` — one ProxyPort with a retyping popover (view/direction/type/delete)
  — the "Schnittstellen-Typisierung" interaction — plus a "+ Nested Port (Dekomposition)" mini-form
  and recursive rendering of `port.children` (indented, connected via `.port-row-wrapper`'s left
  border) for arbitrary decomposition depth. Nested creation calls the same `onAddPort` but with
  `port.guid` as the owner instead of the Block/Actor's guid — the backend redirects that to the
  port's interfaceBlock contract (see backend's "Ports" section) transparently.
- `src/components/ContextMenu.tsx` — generic right-click menu. Architecture nodes get a single
  "+ Child Element" entry (omitted for Equipment, the leaf level) since the level is automatic —
  there's no per-level quick-add anymore.
- `src/components/Palette.tsx` — HTML5-drag palette sidebar, content swapped per active tab. The
  Architecture tab's palette (`ARCH_PALETTE` in `App.tsx`) has exactly two items: "System of
  Systems (optional)" (always targets the root regardless of selection) and "Element" (generic,
  level computed automatically from the drop target).

### Fixed bugs from this session

1. **Silent no-op drop when no model was available.** Dropping a palette item while `architecture`
   was `null` used to do nothing at all — no error, no visual change. Root-caused via
   `claude-in-chrome` (temporarily monkey-patching `window.prompt`/`window.fetch` from
   `javascript_tool`, since these dialogs block the automation session and can't be clicked through
   directly). Fixed: `onDrop` now throws a specific, user-facing error through the existing
   `withErrorHandling`/error-banner mechanism, and `architecture` is fetched on mount regardless of
   active tab (previously only fetched when the Architecture tab was active, so opening the app on
   Kontext/Fähigkeiten first left it `null` forever).
2. **Unencoded GUIDs in URLs** — see backend bug #4 above; fixed in `api/client.ts`.
3. **Interface name/type suggestions (`knownInterfaces`, the `<datalist>` on "+ Interface"/"+ Nested
   Port" forms) offered every interface anywhere in the model, regardless of view.** Reported live:
   "beim anlegen eines neuen interfaces werden bei der Nameneingabe immer noch alle interfaces
   angeboten. diese liste muss view spezifisch sein" — the backend's own interface reuse had
   already been fixed to be per-view-scoped (see the backend's "Interfaces are scoped per view"
   section), but the frontend's suggestion list was still built by walking the ENTIRE tree with no
   view awareness at all, so e.g. adding a Physical-view port on `test` would still suggest
   `"Voice"`/`"plan"` (Functional-only interfaces from unrelated elements). Fixed: `KnownInterface`
   (`types.ts`) gained `view`/`external` fields; `App.tsx`'s `knownInterfaces` `useMemo` now records
   both while walking (a port counts as `external` only when it's a TOP-LEVEL port of one of the
   four tree roots — `architecture.children`, i.e. Flexis/System_F/System_L/System_P themselves,
   mirroring the backend's `isRootLevelClass`/`isExternalPort` — never a nested/decomposed port and
   never an Actor's). A new `forView(knownInterfaces, view)` helper (`utils/knownInterfaces.ts`)
   filters to `k.view === view || k.external`; `PortsSection`/`PortRow` each compute their own
   `scopedKnownInterfaces` (from `lockedView`, or the local view `<select>`'s current value when
   there's no lock) and use it for both the name/type `<datalist>`s and `matchKnownInterface`'s
   autofill — the unscoped list is still what flows down to child `PortRow`s themselves (each one
   computes its own scoping for its own nested-port form; a `PortRow` rendering an EXISTING port
   needs no scoping at all). Verified live in the browser against the real `flexis` project: adding
   an interface on `test` (Physical) suggested only `HEU`/`User` (external, from root `Flexis`) plus
   `J20`/`Mech1`/`optical` (Physical, `System_P`'s own external ports) — none of the Functional-only
   `Voice`/`plan`/`planDetail`/`Plan` leaked in; adding one on `Planning` (Operational, non-root)
   correctly included the Operational subtree names plus `System_P`'s external `J20`/`optical`, with
   no Physical-only non-external names leaking in either.
   - **Unrelated corruption found and fixed while debugging this**: `App.tsx` had two stray
     embedded NUL (`\x00`) bytes baked into its `knownInterfaces` function body (mid-template-
     literal, between `${p.name}` and `${p.direction...}`), left over from some earlier edit in this
     session — invisible in a normal read, but it made the file fail UTF-8 decoding for tooling
     (`grep`/diff treated it as a binary file) and caused this fix's own first `Edit` attempt to fail
     to match. Removed via direct byte-level surgery reading/rewriting the file in Python instead of
     the normal edit path, since the corrupted bytes couldn't be reproduced faithfully as a tool
     argument. Confirmed zero NUL bytes remain afterward, and `npm run build` (`tsc -b && vite
     build`) succeeds cleanly.
   - **Follow-up correction, reported live right after**: "beim neu anlegen von schnittstellen in
     system_l, system_f und flexis sehe ich externe interfaces von system_p. dass ist falsch. auch
     wenn bereits ein interface benutzt in dem element ist, darf es nicht mehr angeboten werden." —
     two more, separate gaps:
     - **Physical and non-Physical "external" interfaces were being offered across each other.**
       First reflex was to make a Physical root port never `external` at all — WRONG, corrected by
       the very next message: "System_p enthält physicalische interfaces (stecker/flachse/optic/...)
       alle anderen flexis, system_f und system_u enthalten logische schittstellen" / "System_P sind
       auch externe Schnittstellen, aber nur physikalische! Wir müssen immer zwischen externen und
       internen Schnittstellen unterscheiden." System_P's own top-level ports ARE external (still get
       `EXTERNAL_INTERFACE_STEREOTYPE` on the backend) — the actual fix needed is that "external"
       reuse must stay confined to a KIND-GROUP: `{"Operational","Functional","Logical"}` (the
       "logical" group) and `{"Physical"}` never mix. Backend: `findOrCreateInterfaceBlock`'s
       widening search (`findInterfaceBlockAcrossAllViews`, now takes the calling `view` too) only
       searches within the SAME kind-group as the port being resolved, instead of either "all four
       views" (the original bug) or "never Physical" (the over-corrected first attempt). Frontend:
       `KnownInterface.external` computation reverted to plain root-ownership (no Physical
       exclusion); `forView` (`utils/knownInterfaces.ts`) gained a `sameKindGroup` check so an
       `external` entry only bypasses the view filter when its own view is in the same kind-group as
       the view being edited.
     - **Already-owned interface names were still offered as "reuse" suggestions on the SAME
       element.** New `excludeOwnNames(knownInterfaces, existingNames)` (`utils/knownInterfaces.ts`)
       filters out any suggestion whose name matches one of `existingNames` — `PortsSection` passes
       its own `ports.map(p => p.name)` (the owner's current top-level ports), `PortRow` passes
       `port.children.map(c => c.name)` (that specific port's own existing nested/decomposed
       children) for its nested-port form. Composed with `forView`
       (`excludeOwnNames(forView(knownInterfaces, view), existingNames)`) in both components.
     - Verified live in the browser against the real `flexis` project: adding an interface on
       `Flexis` (Operational) now suggests only `JMessages`/`Voice`/`Display`/`Input` (same-view,
       Flexis's own existing ports' nested children) — `HEU`/`User` correctly EXCLUDED (already
       Flexis's own top-level ports) and `J20`/`optical`/`Mech1` correctly EXCLUDED (Physical
       kind-group, no longer cross-offered). Adding one on `test` (Physical, a non-root descendant of
       `System_P`) now suggests `Mech1`/`heat` (Physical kind-group, reused from elsewhere in the
       Physical subtree) while `J20`/`optical` are correctly EXCLUDED (already `test`'s own ports) —
       confirming same-kind cross-element reuse still works alongside the new owned-name exclusion.
   - **Second follow-up, reported live right after**: "bei der auswahlt von existieren interfaces
     müssen alle (inklusive nestes schnittstelle) benututzten schnittstellen unterdrückt werden! in
     system_f werden die nested schnittstellen von HEU und User noch angeboten!" — `excludeOwnNames`
     was only ever given each TOP-LEVEL port name (`ports.map(p => p.name)` /
     `port.children.map(c => c.name)`), so a nested/decomposed descendant's own name (e.g.
     `"JMessages"`/`"Voice"`, nested under `System_F`'s own `"HEU"` port — which shares its
     interfaceBlock contract with `Flexis`'s `"HEU"`, so that decomposition is already effectively
     part of `System_F`'s own port structure too) was never excluded, even though offering it back
     risks the same accidental-duplicate problem the first pass of this fix already solved for
     top-level names. Fixed: new `allPortNames(ports)` (`utils/knownInterfaces.ts`) walks `ports`
     AND every nested `children` array recursively, collecting every name at any depth; both
     `PortsSection` (`allPortNames(ports)`) and `PortRow` (`allPortNames(port.children)`) now pass
     this instead of a flat top-level-only list into `excludeOwnNames`. Verified live in the browser:
     adding an interface on `System_F` (Functional) now correctly excludes `HEU` (own top-level
     port) AND its nested `JMessages`/`Voice` (previously still offered), while still correctly
     suggesting `User` (Flexis's external top-level port, not yet used on `System_F`) and
     `plan`/`planDetail`/`Plan` (same-view Functional reuse from `Planning`/`Missile`) — no
     regression to either of the two fixes from the round right before this one.

### Nested-interface suggestion regression + `Container.Name` disambiguation

Follow-up to the "nested interfaces within an external port's decomposition are themselves
external" backend feature (see backend/CLAUDE.md's own section by that name). Two more rounds,
found live against a real `HEU`/`HEU1` scenario:

- **Regression**: "jetz kann ich weder HEU.Voice noch HEU1.Voice unter den existierenden
  inferfaces auswählen." — `App.tsx`'s `knownInterfaces` walk hardcoded `external = false` for
  EVERY nested port (`walkPorts(p.children, false, p.name)`), a leftover from BEFORE the backend
  taught nested ports under an external ancestor to be external too. Since `Voice` (nested under
  the external `HEU`) is itself `view: "Operational"` and (per the frontend) `external: false`,
  `forView`'s filter (`k.view === view || (k.external && sameKindGroup(...))`) excluded it from
  EVERY view except Operational itself — so it silently vanished from Functional/Logical
  suggestion lists entirely, exactly the reported symptom. Fixed: `walkPorts` now threads
  `parentExternal` through the recursion and a nested port's own `external` is `parentExternal`
  (inherited), not hardcoded `false` — mirroring the backend's `isWithinExternalTree` exactly.
- **UX redesign**: "es ist besser den Kontainername als prefix mit punkt vor die nested
  interfaces zu stellen z.B. HEU.Voice und HEU1.Voice" — supersedes the earlier `<option label>`
  approach (previous section), which turned out to be doubly broken: (a) it relied on the
  `external`-propagation that had the regression above, and (b) even once populated,
  `<option value="Voice" label="...">` doesn't reliably render two DISTINCT suggestions when
  multiple options share the same `value` — browsers dedupe datalist suggestions by value,
  label or not. Fixed by making the qualifier part of the VALUE itself: `KnownInterface`'s dedup
  key in `App.tsx` now includes `parentName` (previously `HEU.Voice` and `HEU1.Voice` collapsed
  into ONE entry despite different parents, since they otherwise share direction/type/view/name —
  correct per "Voice ist Voice", but it meant `parentName` diversity could never even be observed
  for labeling). New `qualifiedValue(k)` (`utils/knownInterfaces.ts`) returns `"Parent.Name"` for a
  nested interface or bare `name` for a top-level one — used as the actual `<option value>` in both
  `PortsSection`/`PortRow`'s datalists. New `resolveQualifiedInput(knownInterfaces, typed)` resolves
  a picked/typed qualified string back to `{name: bareName, matched: thatSpecificEntry}` — wired into
  `handleNameChange`/`handleNestedNameChange` so the qualifier is stripped back to the real port name
  (`"Voice"`) the instant it's recognized, before anything is ever submitted; the underlying shared
  contract/reuse identity (confirmed by the user: "Voice ist Voice also auch das selbe ibVoice") is
  completely unaffected — this only changes what's typed/picked in the form.
- Verified live end-to-end against real data (`System_F`'s `HEU`/`HEU1`, both with their own nested
  `Voice` sharing the same `ibVoice` contract): the "Port name" datalist on `System_F → Planning`
  (Functional, non-root) now correctly lists `HEU.Voice`, `HEU1.Voice`, `HEU.JMessages`,
  `User.Display`, `User.Input`, `plan.planDetail` alongside the plain top-level names (`HEU`, `User`,
  `plan`, `Plan`, `HEU1`) — every one individually selectable. Typing `"HEU1.Voice"` resolved the
  input back to bare `"Voice"` with `direction: InOut`/`type: ibVoice` correctly auto-filled from
  that specific entry.

#### Reversal: `HEU.Voice`/`HEU1.Voice` need genuinely SEPARATE contracts after all

Immediately after the above, once `HEU.Voice` was actually added to `Planning`: "nachdem ich
HEU.Voice zu planing hinzugfüght habe will ich auch HEU1.Voice hinzufügen. aber HEU1.Voice wird
nicht mehr angeboten!" — traced to `excludeOwnNames`, which (correctly, at the time) suppressed
ANY suggestion whose bare name matched something the owner already had — but `HEU1.Voice`'s bare
name (`"Voice"`) now collided with the just-added port, hiding it entirely. Asked whether this was
expected (since `HEU.Voice`/`HEU1.Voice` share one contract, adding both to the same element is
inherently a no-op — Rhapsody also wouldn't allow two ports literally named `"Voice"` on one
owner) — the user reversed the earlier "Voice ist Voice" premise: **"Guter Punkt! dann brauchen
wir 2 ibVoice! es können ja unterschiedliche Informationen kommen!"** — `HEU.Voice` and
`HEU1.Voice` must be genuinely independent contracts after all, each carrying its own
direction/settings, precisely so both can coexist (under different port names) on the same
element.

- **Backend** (`createPort`'s nested-port branch): a nested-under-external port's own default
  contract name is now qualified by its immediate owner's name — `"ib" + owner.getName() + "_" +
  name` (e.g. `"ibHEU_Voice"`, `"ibHEU1_Voice"`) — instead of the flat `"ib" + name` that let
  `HEU.Voice` and `HEU1.Voice` collide onto one shared `"ibVoice"` class. Gated on
  `isWithinExternalTree(owner)` exactly as before; a non-external nested port keeps the flat
  convention unchanged (lower collision stakes, out of scope for this fix).
- **Frontend** (`excludeOwnNames`): narrowed back to only suppress a bare, TOP-LEVEL suggestion
  (`k.parentName == null`) whose name is already used — a QUALIFIED suggestion (`k.parentName !=
  null`, e.g. `"HEU1.Voice"`) now stays offered even once the bare name is already used elsewhere
  on the owner, since picking one always implies a genuinely different identity that the user will
  give a new, distinct name to (Rhapsody enforces per-owner name uniqueness natively, so it can
  never silently collide).
- **Data migration**: `HEU1`'s own already-existing `Voice` (created before this fix, sharing
  `HEU`'s `"ibVoice"`) was split off live via an ordinary `PATCH /api/ports/{guid}` with an
  explicit `type: "ibHEU1_Voice"` — no new endpoint needed, since `applyPortSpec`'s existing
  type-resolution path already creates-or-finds whatever contract name it's given. No general
  "detect and split every collision" migration was attempted — this was a targeted one-off fix for
  the specific test data in play.
- Verified live: changed `HEU`'s `Voice` direction `In`→`Out` and confirmed `HEU1`'s `Voice`
  stayed `InOut`, unaffected (previously, before the split, this exact change had propagated to
  ALL five ports sharing the old flat `"ibVoice"`) — restored `HEU`'s direction back to `In`
  afterward. Re-opened `Planning`'s add-interface form (which already has its own `"Voice"` port
  from `HEU.Voice`) and confirmed `"HEU1.Voice"` still appears and remains selectable, correctly
  resolving to `type: "ibHEU1_Voice"` (distinct from the existing port's `ibVoice`).

#### Auto-suffix on name collision — both interfaces must coexist, not silently overwrite

The manual-rename expectation above turned out wrong in practice: "wenn ich in Planning das 2
HEU1.Voice hinzufüge wird HEU.Voice mit HEU1.Voice überschrieben. Es ist immer nur 1 Voice
interface möglich. Aber es müssen beide Voice Interfaces an Planning zuordenbar möglich sein." —
submitting a qualified pick whose resolved bare name already exists on the owner doesn't get
rejected or warned about; `createPort`'s `addOrGetPort`/`findPortByNameDirect` find-or-get-by-name
logic just finds the EXISTING same-named port and silently retypes it, overwriting the first
interface instead of adding a second one.

Fixed in `handleNameChange`/`handleNestedNameChange` (`PortsSection.tsx`/`PortRow.tsx`): when a
QUALIFIED pick (`matched.parentName != null`) resolves to a bare name that's already among the
owner's own port names (`allPortNames(ports)` / `allPortNames(port.children)`), the resolved name
falls back to the SAME `"Parent.Name"` qualified form it was picked as (`qualifiedValue(matched)`,
e.g. `"HEU1.Voice"` — requested live: "bitte nutze den selben prefix wie bei der Auswahl... also
statt Voice_HEU1 HEU1.Voice") instead of colliding with the existing one. Keeps both traceable to
which external interface each came from, while still carrying that source's own distinct contract
via `matched.type` (`ibHEU1_Voice`, unaffected — only the PORT's own name changes). A non-colliding
qualified pick (the owner doesn't have that bare name yet) is untouched — stays the clean bare
name, no suffix.

**Real constraint found live while wiring the literal `"."` into an actual port name**: every port
automatically gets a default contract CLASS named `"ib" + portName`, and Rhapsody rejects `"."` in
Class names — `"Can't add aggregate of type Class. Name 'ibHEU1.Voice' is illegal..."`. Asked the
user for a fallback (`"_"` instead of `"."`, or the same DisplayName-sanitization trick already
used for Capability package names); chose the DisplayName trick. Implemented in `createPort`:
`sanitizedName = sanitizePackageName(name)` computed once up front and used EVERYWHERE the name
would otherwise flow into a Rhapsody `Name` property (the port's own creation call in all three
owner branches, `findPortByNameDirect`'s search, and the derived `"ib"+name`/`"ib"+owner+"_"+name`
contract names) — `setDisplayName(created, name)` applied afterward whenever `sanitizedName !=
name`. Notably could NOT flow a custom name through ECAD's vendored `addOrGetPort` for the
Class-owner (top-level port) branch — it derives the PORT's own name FROM the contract's name
(`interfaceBlock.getName().substring(2)`, stripping `"ib"`), so the sanitized form has to be what's
actually stored there too, with `setDisplayName` layered on as an independent step afterward rather
than passed into `addOrGetPort` itself. `portNode()` (the port read-back path) now prefers
`DisplayName` over `Name`, mirroring `elementRef`'s existing Capability-package pattern exactly.

Verified live: created a port named `"HEU1.Voice"` (literal dot) directly on `Planning` — no error
this time, response correctly showed `"name": "HEU1.Voice"` — confirmed `Planning` now shows BOTH
`"Voice"` (`ibVoice`, from `HEU`) AND `"HEU1.Voice"` (`ibHEU1_Voice`, from `HEU1`) as two genuinely
separate, coexisting ports, each with its own distinct underlying (sanitized) Name and a
`DisplayName` that lets the literal dot show through;
`saveHealthy` confirmed `true` throughout.

#### The qualified prefix is now ALWAYS kept, not just on collision

"ich möchte dass immer der Prefix HEU oder HEU1 bei den nested ports eingefügt wird. das hilft
alles besser zu identifizieren" — widens the collision-only fallback above into the default
behavior: `handleNameChange`/`handleNestedNameChange` now use `qualifiedValue(matched)` as the
resulting port name whenever the pick came from a nested interface (`matched.parentName != null`)
at all, regardless of whether the bare name would have collided with anything on this owner. A
non-nested (top-level, `parentName == null`) pick, or free-typed text that never matched a
suggestion, is unaffected — stays the bare name as before. Verified live: picking `"plan.
planDetail"` (a nested, non-colliding suggestion) on `Planning` now correctly keeps `"plan.
planDetail"` in the name field, instead of resolving down to the bare `"planDetail"` as it did
before this change.

#### `excludeOwnNames` needed to compare qualified identities too, not bare names

Immediate follow-up, once the "always keep the qualified prefix" change above landed: "jetzt werden
die bereits ausgewählten interfaces nicht mehr aus der liste von existierenden schnittstellen
ausgeblendet!" — `excludeOwnNames` was still comparing a suggestion's bare `k.name` against the
owner's own port names, and unconditionally exempting every nested (`parentName != null`) entry
from exclusion entirely (a leftover from the round where `HEU.Voice`/`HEU1.Voice` needed to BOTH
stay offered even once one was used). Now that picking a nested suggestion ALWAYS produces its
qualified name as the actual port name, that comparison was stale: a nested suggestion that's
ALREADY been added under its own qualified name (e.g. `"HEU.Voice"` already a port on this owner)
kept reappearing in the list forever, since only its bare `"Voice"` was ever checked — never its
real, qualified identity. Fixed: `excludeOwnNames` now compares each suggestion's OWN
`qualifiedValue(k)` (the exact name it would actually produce if picked) against `existingNames`
directly — naturally handles both cases at once: a nested suggestion is excluded once ITS OWN
qualified identity is already used, while a DIFFERENT qualified identity sharing the same bare name
(`"HEU1.Voice"` vs an already-used `"HEU.Voice"`) still stays offered, exactly as before.

Verified live: `Planning` already has both `"Voice"` (from `HEU`) and `"HEU1.Voice"` (from `HEU1`)
— reopened its add-interface form and confirmed `"HEU"`/`"HEU.Voice"` and `"HEU1"`/`"HEU1.Voice"`
resolved correctly: `HEU.Voice` and `HEU1.Voice` are now correctly EXCLUDED (both already used),
while `HEU.JMessages`, `User.Display`, `User.Input`, `plan.planDetail`, and the plain top-level
names (`HEU`, `User`, `plan`, `Plan`, `HEU1`) remain correctly offered.

### Known simplification to revisit

Every "name a new element" / "confirm delete" interaction uses `window.prompt` / `window.confirm`.
Deliberate MVP shortcut — works, but blocking browser dialogs aren't "PowerPoint-like" UX and will
need replacing with proper inline modals/forms in a later pass. `claude-in-chrome` browser
automation cannot safely click through these dialogs, so future automated UI testing of
create/rename/delete flows needs this fixed first.

There is also no drag-and-drop **onto an existing node** (e.g. dropping "Element" directly onto a
Block to add a port to it, or to reparent something) — top-level ports are added via the
"+ Interface" button inside each node, and nested/decomposed ports via "+ Nested Port" inside
`PortRow`'s popover instead. Reparenting isn't implemented at all (see the backend's "no
reparent/move operation" gap above). Dropping "Element" creates it as a child of whatever node is
currently *selected* (click-selected, not drag-target-detected).

## rhapsody.jar / Rhapsody installation reference

- Real install on this machine: `C:\Program Files\IBM\Rhapsody\10.0.3`. Model files are **`.rpyx`**
  (the current XML-based project format), not the classic `.rpy`.
- `rhapsody.jar`: `<installDir>\Share\JavaAPI\rhapsody.jar`, package `com.telelogic.rhapsody.core`.
  Ships both compiled `.class` files and the corresponding `.java` sources, so the API contract can
  be read directly out of the jar (extract with a zip tool — there is no `jar`/`javap` on this
  machine's PATH; `python -m zipfile` works). It's a **COM automation bridge**, Windows-only, and
  only works against a running Rhapsody instance — not a REST/network API.
- `rhapsody.dll` (the actual native bridge): `<installDir>\Share\JavaAPI\rhapsody.dll` — **not** in
  the install root, see backend bug #1 above.
- Launching `rhapsody.exe` unattended needs `-lang=cpp` (or the right perspective for your
  edition) — see backend bug #2 above. Start Menu shortcuts under "IBM Engineering Systems Design
  Rhapsody 10.0.3" show the right arguments per edition if this ever needs re-deriving (inspect via
  `(New-Object -ComObject WScript.Shell).CreateShortcut(path).Arguments` in PowerShell).
- **`D:\KI\plugin\ECAD`** — a separate, independently-developed Rhapsody plugin (an ICD/ECAD
  document generator) that reads and writes the exact same kind of model this app targets
  (ProxyPorts, interfaceBlocks, nested port decomposition). When extending port-related or other
  Rhapsody-automation logic, **check ECAD's source first** (`src/com/ibm/rhapsody/samples/plugin/`,
  especially `services/ModelElementService.java`, `services/StereotypeService.java`,
  `io/ICDExporter.java`) — it's working, tested code for the same object model, not a guess. This
  is how the `"FlowPort"` bug above got found and fixed: ECAD's code uses `"Port"` +
  `addSpecificStereotype`/`setContract`, never `"FlowPort"`/`IRPSysMLPort` at all.
- When extending `RhapsodyModelStore`, prefer reading the relevant `.java` source straight out of
  the jar over guessing at method signatures (e.g. `IRPModelElement.java` is ~1300 lines) — and
  where possible, verify against the live install (or check ECAD) rather than assuming; several of
  the bugs above
  were exactly this kind of assumption turning out wrong in practice.
