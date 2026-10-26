# PLUS 04: drawing tools, and Save/Share

Part of the PLUS plans; start at `plus-00-overview.md`. Size: small to medium. Needs plan 01, and plan 02 for the pop-up.
Files: `MainActivity.kt` (`buildMainRow`, `selectTool`, `toolFor`, `toolsOf`, `refreshButtons`, the panel's Host), `Buttons.kt` (`ToolButton` badge), `ParentPanel.kt` (`showDrawing`).

## The split

| Tool | Free | PLUS |
|---|---|---|
| Crayon: 12 colors, 3 sizes | ✓ | |
| Eraser, undo, clean page (hold) | ✓ | |
| White and black paper, 30 sheets kept, autosave | ✓ | |
| Tap-to-fill on coloring pages | ✓ | |
| Magic crayon: rainbow, glitter, glow, mirror (Free Draw and Color) | | ✓ |
| Stamps, all 8 (Free Draw and Color) | | ✓ |
| Pattern crayons, all 8 | | ✓ |
| Stickers, all 12 | | ✓ |
| Save to phone and Share (grown-ups' Drawings page) | | ✓ |
| Baby mode, bedtime timer, pinning, sounds, voice | ✓ | |

The brief's "additional drawing tools" means glow, mirror and the pattern crayons, which already exist. Tools added later (more stickers, a sticker pack per group, new patterns) also go in PLUS as the "future premium content" (ideas in plan 07).

## Steps

1. **Badges:** in `buildMainRow`, a locked tool button gets the padlock badge (`ToolButton.locked = true`, drawn in `Buttons.kt` alongside `chosen`). `refreshButtons()` updates it whenever PLUS changes.
2. **Taps:** at the top of `selectTool(tool)`, add `if (!Plus.toolOpen(tool)) { showLocked(tool); return }`. The current tool and color stay as they were. The tray (`openTray`) can't open on a locked tool, because the first tap never selects it.
3. **The saved tool:** `toolFor()` also checks `Plus.toolOpen`, so a saved magic crayon, stamp, pattern or sticker comes back as the crayon.
4. **A refund while on the easel:** in `Plus.onChange`, if `drawing.tool` is now locked, switch to `Tool.BRUSH`.
5. **What's on the paper stays.** Stamps, stickers, glitter and patterns drawn earlier stay in the drawing; only adding new ones needs PLUS.
6. **Drawings page:** in `ParentPanel.showDrawing(id)`, if `!Plus.canExport()`, Save and Share show the "PLUS" tag and lead to the sum, then the PLUS page (plan 05). Delete stays free.
7. **Color mode:** `toolsOf(Mode.COLOR)` still includes `MAGIC` and `STAMP`. They show as locked, the same as in Free Draw.

## A question for you

With every fun tool behind PLUS, the free easel is the crayon and the eraser. That's what the brief says. Letting one thing through as a taste (say the rainbow crayon, or the star stamp) shows kids and parents what PLUS feels like, and makes the free version feel less bare. The default here is none, as written. Adding a taste is a one-line change in `Plus.toolOpen`.

## Checks

- Free tier: crayon, colors, sizes, eraser, undo and clean page all work as today. Each locked button shows the pop-up and doesn't change the tool.
- A drawing that already has stickers on it still shows them on the free tier.
- The size button still works; it's free and doesn't depend on the tool.
- With PLUS: every tool, every tray, and tap-again cycling all work as today.
