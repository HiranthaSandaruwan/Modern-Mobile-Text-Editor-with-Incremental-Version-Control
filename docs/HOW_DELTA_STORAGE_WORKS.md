# How the delta-based version control works (for the report & viva)

## The problem
Storing a full copy of the file for every snapshot wastes storage.
The assignment requires: **do not duplicate the base file across versions —
store incremental changes (deltas) instead.**

## Our storage design

Only version 1 stores the full file. Every later version stores just a
*unified-diff patch* — the same text format `git diff` produces.

```
Room database (SQLite)
┌────────────────────────────────────────────────────────┐
│ tracked_files                                          │
│  id │ name        │ isReadOnly │ encoding              │
│  1  │ notes.md    │ false      │ UTF-8                 │
├────────────────────────────────────────────────────────┤
│ file_versions                                          │
│  # │ version │ baseContent      │ patchText            │
│  1 │   v1    │ FULL TEXT ✔      │ (null)               │
│  2 │   v2    │ (null)           │ patch: v1 → v2       │
│  3 │   v3    │ (null)           │ patch: v2 → v3       │
└────────────────────────────────────────────────────────┘
```

Example of a stored patch (this is the ONLY thing saved for v2):

```
--- previous
+++ current
@@ -1,3 +1,4 @@
 # Shopping list
 - milk
-- bread
+- brown bread
+- eggs
```

## Rebuilding a version (rollback)

To restore version 3:

```
text = v1.baseContent          // full text of the base version
text = applyPatch(text, v2.patchText)   // now text == version 2
text = applyPatch(text, v3.patchText)   // now text == version 3
```

Code: `VersionControlManager.rebuildText()` — a simple loop.

## Where java-diff-utils is used

| Task | Library call | Our function |
|---|---|---|
| compute delta between two texts | `DiffUtils.diff(oldLines, newLines)` | `createPatchText` |
| serialize delta as text | `UnifiedDiffUtils.generateUnifiedDiff(...)` | `createPatchText` |
| parse stored patch | `UnifiedDiffUtils.parseUnifiedDiff(lines)` | `applyPatchText` |
| apply patch to a text | `patch.applyTo(lines)` | `applyPatchText` |
| line-by-line diff screen | `DiffUtils.diff(...)` + walking `patch.deltas` | `buildDiffLines` |

The assignment explicitly allows using this open-source library instead of
writing a diff algorithm from scratch.

## Why the texts are split into lines

Diffs are calculated **per line** (like git). `text.split("\n")` turns the
document into a list of lines; the library compares the two lists and reports
which lines were inserted, deleted or changed.
