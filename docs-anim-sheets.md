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

## The generation prompt (universal — just attach the photo)

**Attach the creature's life-cycle photo** (the one showing its 5 ages: baby, child, teen, adult, elder).
The prompt reads the creature from the photo, so it's the **same prompt for every species** — you only
change the one **STAGE** word and paste that stage's 5 rows. Run it once per stage (5 runs per species).

> Using this photo of the creature's life stages, make a **2D game animation sprite sheet** for the
> **ADULT** version of the creature shown in the photo. Match its exact colors, markings and
> proportions — do not invent a different creature. Output a single transparent-background image as a
> **strict 6-column by 5-row grid of equal square cells**, evenly spaced, no margins, no text, no grid
> lines, no drop shadow. In every cell the creature is the **same size**, **centered horizontally with
> its feet on the bottom edge of the cell**, in a consistent 3/4 side view **facing right**, clean
> cel-shaded semi-realistic style matching the photo. Each ROW is one action animated left→right; fill
> only the stated number of frames and leave the rest of that row transparent:
> - Row 1 — idle: gentle breathe + blink (4 frames)
> - Row 2 — walk cycle (6 frames)
> - Row 3 — jump (5 frames)
> - Row 4 — a signature "grab/attack" move that fits this creature (6 frames)
> - Row 5 — a second signature move that fits this creature (6 frames)
> Each row must be a smooth, loopable cycle of that one action, body centered and feet on the baseline so
> it never jitters. Transparent WebP/PNG, square cells.

Swap the **STAGE** word and the 5 rows for the stage you're making (rows per stage, in order):

- **BABY** — Row1 cry (4) · Row2 crawl (6) · Row3 sit up (4) · Row4 wobble up to standing (4) · Row5 first wobbly steps / walk (6)
- **CHILD** — Row1 idle breathe+blink (4) · Row2 walk (6) · Row3 hop/jump (5) · Row4 sit (4) · Row5 playful move / pounce (6)
- **TEEN** — Row1 idle (4) · Row2 walk (6) · Row3 turn around (5) · Row4 climb (6) · Row5 show-off move (6)
- **ADULT** — Row1 idle (4) · Row2 walk (6) · Row3 jump (5) · Row4 signature grab/attack (6) · Row5 second signature move (6)
- **ELDER** — Row1 idle, slightly hunched (4) · Row2 slow walk (6) · Row3 sit / rest (4) · Row4 stretch (6) · Row5 big yawn (5)

Keep Row 1 = idle and Row 2 = walk exactly as stated for every stage (the app relies on those two rows).
Rows 3–5 just need to read as that creature; the exact move is up to the art.

Save the five outputs named `<species>_baby.webp` … `<species>_elder.webp` in
`app/src/main/assets/sprites/anim/` (e.g. `chameleon_adult.webp`). That's it — the app does the rest.
