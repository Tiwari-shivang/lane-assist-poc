
# European Road Markings — Engineering Cheat Sheet (UNECE + CEN)  

**Purpose.** A precise, implementation‑oriented reference you can plug into your RAG + OpenCV pipeline for detecting and validating European road and junction markings.  
**Scope.** Baseline geometry/meaning from the **UNECE Vienna Convention (European Agreement & 1973 Protocol on Road Markings)**; in‑service performance from **CEN EN standards**. National manuals may refine details — treat those as overlays.

> **Units.** All dimensions in **metres (m)** unless noted. Use a single `pixels_per_meter` (PPM) for non‑georeferenced rasters, or derive PPM from metadata.  
> **Legend.** “Stroke” = painted segment of a broken line. “Gap” = space between strokes. “Driver’s side” = the side of the pair closest to the lane user.

---

## 0) Color, function & classes (baseline)
- **Permitted colors:** **White** (default for most markings), **Yellow** (often for parking/standing restrictions, edge/kerb controls; zig‑zags), with national allowances for **Blue** to denote permitted/restricted parking zones.  
- **Classes:** **Longitudinal** (centre, lane, edge, separation, warnings), **Transverse** (stop, give‑way, crossings), **Other** (arrows, chevrons/hatched areas, words & numerals, studs).  
- **Crossing rules:** **Solid** line → do **not** cross/straddle. **Broken** line → crossing permitted subject to rules. **Solid + broken pair** → **obey the line nearest to you**.

---

## 1) Longitudinal markings (centre, lane, edge)

### 1.1 Normal broken (guidance) line
- **Width:** ≥ **0.10** m  
- **Stroke length:** ≥ **1.0** m  
- **Gap:** **2–4 ×** stroke (but **≤ 12 m**)  
- **Use:** Lane division, centre line on two‑way roads where sight distance is adequate.

### 1.2 Broken **warning** line
- **Pattern:** **Stroke longer than gap** (≈ **2–4 ×** gap) for a denser “warning” appearance.  
- **Use:** Approaches to hazards (crests, bends, junctions), where extra caution/visibility is needed.

### 1.3 Solid line (single)
- **Width:** ≥ **0.10** m (≥ **0.15** m for motorway edge lines)  
- **Minimum continuous length:** ≥ **20** m (when used as a prohibition)  
- **Rule:** Drivers **must not cross or straddle**.

### 1.4 Double line (solid+broken / solid+solid)
- **Solid + broken (adjacent):** **Obey the nearer line** (i.e., if solid on your side → no crossing).  
- **Double solid:** No crossing from either side; used for strong separation.

### 1.5 Separation of main vs accel/decel lanes
- **Broken line width:** **≥ 2 ×** the width of a normal broken line (i.e., visually heavier).

### 1.6 Edge/border line (carriageway edge)
- **Width:** ≥ **0.10** m (≥ **0.15** m on motorways).  
- **Use:** Delimits the usable carriageway; may be continuous.

### 1.7 Reversible lane borders
- **Delimitation:** **Double broken warning** lines (denser pattern on each side).

### 1.8 Guide lines & arrows near intersections
- **Guide line pattern:** Recommend **0.50 m** stroke & **0.50 m** gap to assist lane choice.  
- **Arrow markings:** May supplement guide lines to indicate permitted movements.

---

## 2) Transverse markings (junction control & crossings)

### 2.1 Stop line
- **Form:** Wide transverse bar across approach lane(s).  
- **Typical width (paint thickness across travel):** **0.25–0.40** m.  
- **Supplementary:** Word marking **“STOP”** may be added ahead of the line.

### 2.2 Give‑way (yield) line
- **Bar type:** Transverse broken/short bars, **width:** **0.20–0.60** m; **bar length:** **≥ 2 ×** width.  
- **Triangle (shark teeth) alternative:** Row of isosceles triangles with **base 0.40–0.60 m** and **height 0.60–0.70 m**, triangle tips pointing **toward the driver who must yield**.

### 2.3 Pedestrian crossing (zebra)
- **Stripe width vs gap:** **Stripe ≈ Gap**, and **(Stripe + Gap) = 0.80–1.40 m**.  
- **Minimum crossing width:** **≥ 2.5 m** where **V85 ≤ 60 km/h**; **≥ 4.0 m** where **V85 > 60 km/h** (these should be signalised).  
- **Orientation:** Stripes orthogonal to the traffic flow.  
- **No studs** in the crossing field (national exceptions may exist for tactile paving at edges).

### 2.4 Cyclist crossing / cycle priority crossings
- **Marking:** **Two broken lines** bounding the cycle crossing.  
- **Element form:** Prefer **0.40–0.60 m** squares/parallelograms; **gap = side length**.  
- **Widths:** **≥ 1.8 m** (one‑way), **≥ 3.0 m** (two‑way).  
- **Separation from zebra:** Where combined, keep visual distinction (pattern/texture).

---

## 3) Other markings (direction, management, standing/parking)

### 3.1 Lane‑selection arrows
- **Arrow length:** **≥ 2.0 m** (longer on higher‑speed approaches).  
- **Use:** Repeated in advance of the decision point; may be accompanied by word markings.

### 3.2 Oblique hatching / chevrons (divergence & convergence areas)
- **Areas bounded by:**  
  - **Continuous** line → **do not enter**.  
  - **Broken** line → **may enter with care** (e.g., turning lanes).  
- **Internal pattern:** Oblique stripes or chevrons pointing along flow deflection.  
- **Purpose:** Keep traffic out of separation noses and taper zones.

### 3.3 Word & numeral markings
- **Character height (elongated in travel direction):**  
  - **≥ 1.6 m** where approach speed **≤ 60 km/h**  
  - **≥ 2.5 m** where **> 60 km/h** (examples up to **4 m** used).  
- **Examples:** “BUS”, “STOP”, speed numerals (30/50), directional names.

### 3.4 Parking/standing control on kerbs/edges
- **Yellow continuous/broken lines:** Prohibition/restriction of standing/parking.  
- **Yellow zig‑zags:** Places where **parking is prohibited** (often by crossings/approaches).  
- **Blue marking (where used):** Denotes **permitted/restricted parking** zones (national).

### 3.5 Marking obstructions/islands
- **Coloring:** Alternate **black/white** or **black/yellow** striping on the object/island faces visible to traffic.

---

## 4) In‑service performance (CEN EN series — what “good” looks like on road)

> These classes ensure the installed markings remain conspicuous and safe. They do not prescribe shapes/locations — they specify **performance**.

- **EN 1436 – Road marking performance for road users**
  - **Retro‑reflection (dry, RL)**: classes (e.g., **R2 ≥ 100**, R3 ≥ 150, R4 ≥ 200, R5 ≥ 300 mcd·lx⁻¹·m⁻²).  
  - **Visibility under diffuse light (Qd)**: classes Q1/Q2/Q3/Q4 (e.g., **Q2 ≥ 100**, **Q3 ≥ 130**).  
  - **Wet/wet‑night (RW/RLwet)**: classes W1… (country‑specific adoptions).  
  - **Skid resistance (SRT)**: classes **S1 ≥ 45** up to **S5 ≥ 65**.  
  - **Chromaticity & luminance factor (B)**: white/yellow color boxes and minimum luminance.  
- **EN 1871 – Materials (paints, thermoplastics, cold plastics)**: composition/physical tests; premix bead options.  
- **EN 1423 / EN 1424 – Drop‑on & premix glass beads**: gradation, refractive index, durability.  
- **EN 1790 – Preformed markings** (tapes/preformed thermoplastics): product characteristics & conformity.  
- **EN 1824 – Road trials**: how to set up and measure performance on real roads.

> For POC CV/RAG, use EN 1436 **only if** you infer “is this bright enough?” — usually out of scope unless you have photometric metadata. Most CV checks use geometry/patterns.

---

## 5) RAG schema (what to store per marking)

Use a compact schema so OpenCV can be parameterised and detections validated.

```yaml
- id: lane_guidance_broken
  kind: longitudinal
  color: white
  semantics: crossing_permitted_with_rules
  geometry:
    width_m: [0.10, null]
    stroke_m: [1.0, null]      # minimum
    gap_to_stroke: [2.0, 4.0]  # ratio
    max_gap_m: 12.0
  cv_cues:
    pattern: "periodic strokes along flow"
    ops: ["tophat", "binary", "anisotropic_dilate_along_flow"]
  qc:
    min_length_m: 20
    overlap_with_road: 0.6

- id: lane_warning_broken
  geometry:
    stroke_to_gap: [2.0, 4.0]  # stroke longer than gap
    width_m: [0.10, null]

- id: solid_line
  geometry:
    width_m: [0.10, null]
    min_continuous_m: 20
  semantics: no_crossing

- id: edge_line_motorway
  geometry:
    width_m: [0.15, null]

- id: stop_line
  kind: transverse
  geometry:
    bar_width_m: [0.25, 0.40]
  semantics: mandatory_stop_at_line

- id: give_way_triangles
  geometry:
    triangle_base_m: [0.40, 0.60]
    triangle_height_m: [0.60, 0.70]
  semantics: yield

- id: zebra_crossing
  geometry:
    stripe_plus_gap_m: [0.80, 1.40]
    stripe_to_gap_ratio: [0.5, 2.0]  # stripe ≈ gap
    min_crossing_width_m: { v_le_60_kph: 2.5, v_gt_60_kph: 4.0 }
  orientation: orthogonal_to_flow

- id: cycle_crossing
  geometry:
    element_size_m: [0.40, 0.60]
    gap_equals_element: true
    width_m: { one_way_min: 1.8, two_way_min: 3.0 }
```

> Extend with **arrows, chevrons, word/numeral heights**, parking/standing kerb lines, and reversible lane borders as needed.

---

## 6) CV implementation hints (tying geometry to pixels)

- **Markings mask first:** grayscale **Top‑hat (17–31 px)** + Otsu, AND with road mask. For RGB aerials, OR in **HSV white/yellow** thresholds.  
- **Directional growth:** Estimate flow with **HoughLines** and **dilate with a rotated long‑thin kernel** (length ≈ 6–10% of max dimension; thickness ≈ 1.5–2.5% or from lane width rule × PPM).  
- **Periodic checks (zebra, broken lines):** sample intensity profiles **perpendicular** (zebra) or **along flow** (broken); verify **stroke/gap** counts & ranges from rules.  
- **Polygonization:** `minAreaRect/boxPoints` for leg‑like bands; `convexHull + approxPolyDP` for irregular junction cores.  
- **Filters:** road overlap ratio ≥ **0.6–0.8**; minimum area fraction **0.001–0.003**; remove skinny outliers unless rule allows.  
- **Labels:** attach rule `id` to each accepted polygon; optionally run OCR for word/numeral markings on RGB inputs.

---

## 7) Notes & caveats
- This sheet reflects **UNECE Protocol** conventions widely adopted across Europe + **CEN EN** performance classes. Individual countries may add lane colors, cycle priority patterns, advance boxes, etc. Treat national manuals as **additional sources** in your RAG.  
- Geometric ranges are intended for **machine validation**. For legal compliance and construction, consult the official texts.

---

### Change log
- v1.0 — Initial extraction for POC (monolithic Spring + OpenCV + RAG).
