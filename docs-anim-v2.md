# Scanimal animation system v2 — the in-depth design

Goal: a buddy that reads as a real little creature living on the screen — walks around
believably, idles, gets bored, perks up when it finds something, scans, plays, sleeps, talks
with its mouth moving, shows how it feels on its face, in the right facing, at the right life
stage. This doc defines the whole system so the art and the engine agree.

## 1. Behavior (what decides what it's doing)

A small state machine in the app picks a **state** every moment from the game context. The art just
has to provide frames for each state; the app drives when.

| state | trigger | facing |
|---|---|---|
| walk | strolling to a target | side |
| idle | standing, recently active | front (faces you) |
| bored | idle with nothing happening for ~30s | front |
| interested | a new signal/scan/device just found | front, then toward it |
| scan | actively sniffing Wi-Fi/BT (radar/scan) | side or 3/4 |
| play | a toy is out and it's near it | side |
| eat | feeding | front |
| sleep | asleep / night | side (curled) |
| talk | a speech bubble is showing | current facing + mouth flap |
| react (happy/sad/angry/love) | an event | front |

The app already knows all these triggers (walking flag, found-signal events, toy, asleep, speech,
anim). v2 just maps them to states + facings and plays the matching frames.

## 2. Facing / angles

Four logical directions: **left, right, front (toward viewer), back (away)**. Left is the
mirror of right, drawn in code — so the ART only needs **3 views: side (right), front, back**.
- walking horizontally → side (mirrored for left)
- idle / talk / emote → front
- walking up-screen / leaving → back (optional; can fall back to side early on)

## 3. Faces, expressions, mouth

- **Expressions** are baked into the body frames per state (a bored slump, a happy bounce, etc.) —
  simplest and most consistent.
- **Talking** needs a mouth that moves. A short **talk mouth-flap** (2–3 frames: closed → open →
  mid) plays only while a speech bubble is up, looped fast, in whatever facing the buddy is in.
  Easiest to author as a **front-facing talk row** (mouth open/closed), shown front-on while talking.

## 4. Pack format (modular, like v1 but richer)

Still 6-column grids of square cells, feet on the bottom edge, transparent, no text/margins — but now
**named by pack + facing** so we can add detail without one impossible mega-sheet. Per life stage:

    app/src/main/assets/sprites/anim/<species>_<stage>_<pack>.webp

- **move** pack (rows): idle_front, walk_side, idle_side, walk_front, walk_back, turn
- **act** pack (rows): bored, interested, scan, play, eat, sleep
- **face** pack (rows): talk (mouth flap, front), happy, sad, angry, surprised, sleepy

So **3 sheets per life stage × 5 stages = 15 sheets per species.** The app reads the fixed row order
per pack (no per-species config), slices each row at cell = sheetHeight/rows, and plays the state's row
in the right facing. Missing packs/rows fall back (act→move idle, face→body frames) so partial delivery
always works.

## 5. Life-cycle accuracy

Each stage is a distinct creature age in all three packs (baby small & clumsy … elder slow & worn).
The baby's **move** pack still carries its grow-up order in the walk rows (crawl → stand → steps).

## 6. What the app does (engine work, phased)

- **Phase A (shipped v1.7.x):** calmer/slower playback, smaller sprite, slow stroll with loiter.
- **Phase B:** state machine (bored/interested/scan/play/sleep) mapping to rows; multiple frame rates.
- **Phase C:** facing selection (side vs front vs back) from movement + state.
- **Phase D:** talk mouth-flap synced to speech bubbles.

Each phase ships on its own and degrades gracefully when the matching art isn't present yet.

## 7. Generation prompts (per pack — attach the life-cycle photo)

Same rules as before (6×5-style grid, square cells, feet on bottom edge, transparent, consistent
scale, no text/margins), but **state the facing and the rows per pack**. Example for the **move**
pack, adult:

> Using this photo, make a game animation sheet for the ADULT creature in it. Strict grid of equal
> square cells, transparent, feet on the bottom edge, same size every cell, clean style matching the
> photo. Rows (one action each, left→right, loopable, no jitter):
> Row1 idle facing the viewer (4) · Row2 walk in side view facing right (8) · Row3 idle in side view (4)
> · Row4 walk facing the viewer, toward camera (6) · Row5 walk away from camera, back view (6) ·
> Row6 turn from side to front (5).

(Full per-pack, per-stage row lists live in section 4; the art session fills them in the same way.)

## 8. MASTER PROMPT — generate everything (attach the life-cycle photo)

Using this one photo of the creature's life stages, generate game animation SPRITE SHEETS for this
exact creature. It shows the five ages (baby → child → teen → adult → elder); keep it the SAME
character and use each age for its own stage. Match colors, markings and proportions from the photo —
do not invent a different creature.

Rules for every sheet: one transparent-background image, a strict grid of equal SQUARE cells, evenly
spaced, no margins, no text, no grid lines, no drop shadow. In every cell the creature is the SAME
size, centered horizontally with feet on the bottom edge. Clean cel-shaded semi-realistic style
matching the photo. Each ROW = one action, animated left→right, a smooth loopable cycle, body centered,
feet on the baseline, no jitter.
Facings: "side" = 3/4 side view facing RIGHT (don't draw left — mirrored in code). "front" = toward the
viewer. "back" = away.

For EACH life stage (baby, child, teen, adult, elder) make THREE sheets:

MOVE — <species>_<stage>_move.webp (6 rows):
  1 idle front (4) · 2 walk side-right (8) · 3 idle side (4) · 4 walk front toward viewer (6) ·
  5 walk back/away (6) · 6 turn side→front (5)
ACT — <species>_<stage>_act.webp (6 rows):
  1 bored front (5) · 2 interested/perk-up front (5) · 3 scan, peering at a handheld scanner, side (6) ·
  4 play side (6) · 5 eat front (5) · 6 sleep curled side (5)
FACE — <species>_<stage>_face.webp (6 rows, all front):
  1 talk mouth-flap closed→open→mid (3) · 2 happy (4) · 3 sad (4) · 4 angry (4) · 5 surprised (4) ·
  6 sleepy (4)

Baby exception: the baby MOVE walk rows show it learning to move (crawl → stand → wobbly steps).
Output 15 files to app/src/main/assets/sprites/anim/ (e.g. chameleon_adult_move.webp), transparent WebP,
square cells. One image per request is fine — produce them one sheet at a time, character identical
across all sheets. START with the adult's three sheets.
