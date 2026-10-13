# Scanimal animation sheets — the modular standard

One **sprite sheet per life stage** per species. Drop the sheets in and the app animates that
species across its whole life cycle. Adding a new species = generate the same 5 sheets from that
species' life-cycle reference image. Nothing else to configure.

## Files

    app/src/main/assets/sprites/anim/<species>_<stage>.webp

- species: `blob dino dog cat chameleon plant fish bird human alien robot`
- stage: `baby child teen adult elder`  → 5 sheets per species
- transparent WebP, one grid image per stage

## The grid (fixed — do not change)

Every sheet is a **6 columns × 5 rows** grid of equal square cells.

- **Row = an action** (top to bottom, in the exact order below).
- **Columns = that action's frames**, left → right. Actions that use fewer than 6 frames leave the
  trailing cells in that row fully transparent (the app knows each action's frame count, so it never
  samples the empty cells).
- Every cell is the same size and the creature is drawn at the **same scale** in all 30 cells, **centered
  horizontally with its feet on the bottom edge of the cell** — so it never jumps, grows, or drifts
  between frames or actions.
- Pure transparent background. No text, no labels, no visible grid lines or borders, no drop shadow
  (the app draws the shadow). Even spacing; the whole image is exactly 6×5 cells with no margin.
- Suggested size: cells 320–512 px square → a 1920×1600 to 3072×2560 sheet. Higher is better if the
  generator supports it; the app derives the cell size from the image, so any clean 6×5 grid works.

### Row order per stage (action = row, with frame count)

| row | BABY | CHILD | TEEN | ADULT | ELDER |
|----|------|-------|------|-------|-------|
| 0 | cry (4) | idle (4) | idle (4) | idle (4) | idle (4) |
| 1 | crawl (6) | walk (6) | walk (6) | walk (6) | walk (6, slow) |
| 2 | sit-up (4) | jump (5) | turn (5) | jump (5) | sit (4) |
| 3 | stand (4) | sit (4) | wall-climb (6) | tongue-snap (6) | stretch (6) |
| 4 | first-steps / walk (6) | play (6) | show-off (6) | color-shift (6) | yawn (5) |

The **baby** rows are a growing-up progression: the app plays them in order as the baby ages through
its first day — cry → crawl → sit up → stand → first steps — then it graduates to the child stage.
Later stages all share walk + idle + sit/jump/turn, plus two age-appropriate signature moves.

## In the app

- The app loads `anim/<species>_<stage>.webp`, splits it into a 6×5 grid (cell = imageHeight / 5),
  and for the current action plays that row's frames at ~10 fps.
- Fallback chain if a sheet is missing: per-action loose frames (`<species>_<stage>_<action>_<n>.webp`)
  → the single `<species>_<stage>.webp` still + procedural motion → built-in vector art (Blip).
- Facing left mirrors the art horizontally in code, so only draw facing **right**.

## The generation prompt (reusable — swap the reference image + species name)

Attach the species' **life-cycle reference image** (the one that shows the 5 ages of the creature:
baby, child, teen, adult, elder). Then run this prompt once per life stage, changing `<STAGE>` and the
row list to match the table above. Keep the character identity, colors and markings locked to the
reference so every stage reads as the same individual growing up.

> Generate a **2D game animation sprite sheet** for the **<STAGE> <SPECIES>** shown in the attached
> life-cycle reference (use the <STAGE> version of this exact character — same colors, markings and
> proportions). Output a single transparent-background image laid out as a **strict 6-column by 5-row
> grid of equal square cells**, evenly spaced, no margins, no text, no grid lines, no drop shadow.
> In every cell the creature is the **same size**, **centered horizontally with its feet resting on the
> bottom edge of the cell**, drawn in a consistent 3/4 side view facing **right**, clean cel-shaded
> semi-realistic style matching the reference.
> Each ROW is one action animated left→right; fill only the stated number of frames, leave the rest of
> the row transparent:
> - Row 1 — <ACTION 1> (<N> frames)
> - Row 2 — <ACTION 2> (<N> frames)
> - Row 3 — <ACTION 3> (<N> frames)
> - Row 4 — <ACTION 4> (<N> frames)
> - Row 5 — <ACTION 5> (<N> frames)
> The frames in a row must be a smooth, loopable cycle of that one action with the body staying
> centered and the feet on the baseline so it never jitters. Transparent PNG/WebP, square cells.

### Chameleon, filled in (the 5 runs to do now)

- **baby**: Row1 cry (4) · Row2 crawl (6) · Row3 sit up (4) · Row4 wobble to standing (4) · Row5 first wobbly steps / walk (6)
- **child**: Row1 idle breathe+blink (4) · Row2 walk (6) · Row3 hop/jump (5) · Row4 sit (4) · Row5 playful tail-curl / pounce (6)
- **teen**: Row1 idle (4) · Row2 walk (6) · Row3 turn around (5) · Row4 climb a branch (6) · Row5 show-off color flash + eye swivel (6)
- **adult**: Row1 idle (4) · Row2 walk (6) · Row3 jump (5) · Row4 tongue-snap catch (6) · Row5 slow color-shift (6)
- **elder**: Row1 idle, slightly hunched (4) · Row2 slow walk with a little cane (6) · Row3 sit/rest (4) · Row4 stretch (6) · Row5 big yawn (5)

Deliver as `chameleon_baby.webp` … `chameleon_elder.webp` in `app/src/main/assets/sprites/anim/`.
