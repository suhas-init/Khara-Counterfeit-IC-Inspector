# Khara — Offline Counterfeit IC Inspector

Khara turns an ordinary smartphone into a field-grade counterfeit IC inspector. It runs fully offline — no cloud, no network, no data leaving the device — and gives a QA engineer a transparent pass/flag verdict in about ten seconds.

Built for incoming-inspection QA engineers at EMS subcontractors and small-scale electronics manufacturers who get 30 seconds per chip, have no X-ray station at the bench, and work in facilities that legally prohibit internet-connected devices near sensitive material.

---

## The Problem

Counterfeit electronic components cost the global electronics sector over $100 billion every year. Automotive absorbs $40 billion, defence $32 billion, healthcare $25 billion. A 2012 US Senate Armed Services Committee investigation found 1 million+ suspect parts across 1,800 cases in the Pentagon supply chain. India imports nearly 90% of its electronic components for defence, medical, and space.

Counterfeits come in documented forms:
- **Blacktopping** — original markings sanded off, recoated in black epoxy, fake part number reprinted
- **Remarking** — cheaper part relabelled as a more expensive one
- **Resurfacing** — recycled e-waste chips sanded until they look factory-new
- **Cloning** — complete copies with forged traceability paperwork
- **Diverted out-of-spec parts** — chips that failed factory QC, routed to grey-market brokers

No existing tool sits in the gap between "eyeball it under a loupe" and "send it to a $40K lab for two weeks." Cloud tools are disqualified by policy in the facilities that need this most.

---

## How Khara Works

The QA engineer points the camera at a chip, takes three guided macro photos, and gets a transparent trust score with a per-signal breakdown. Every scan is logged with a SHA-256 hash-chained audit entry and can be exported as an on-device PDF report.

### Eight Independent Signals

| Signal | Technique | What It Catches |
|---|---|---|
| MobileCLIP 512-dim embedding | Cosine similarity against reference | Primary visual identity |
| Perceptual Hash (pHash) | 64-bit DCT fingerprint, Hamming distance | Structural identity gate |
| Texture Forensics | Laplacian variance + LBP entropy + Sobel scratch ratio | Blacktopping, sanding, resurfacing |
| OCR Part Match | Tesseract + Levenshtein fuzzy matching | Wrong part number, remarking |
| Color Verification | LAB Delta E distance | Surface color changes |
| Logo Template Match | Normalized cross-correlation | Logo tampering or removal |
| Pin Count | Canny edge + contour analysis | Package integrity |
| Identity Gate | pHash must agree with embedding | Prevents false passes |

### Fusion Logic

Rule-based, not a learned classifier. A high trust score requires the visual embedding AND the perceptual hash to agree with the registered reference. Any strong texture anomaly forces a FLAG regardless of other signals. The engineer always sees the full per-signal breakdown — never a black-box verdict.

---

## Technical Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Camera | CameraX with guided macro capture |
| ML Runtime | TensorFlow Lite with QNN Delegate (NPU) |
| Vision | OpenCV 4.9 (texture, color, contours, perspective) |
| OCR | Tesseract4Android |
| Embedding | MobileCLIP (512-dim) + MobileNet-V2 INT8 |
| Detection | YOLO11n (TFLite) |
| Audit | SHA-256 hash-chained log |
| Report | android.graphics.pdf.PdfDocument |

### Hardware Acceleration

Neural network inference routes to the Qualcomm Hexagon NPU via the QNN Delegate with HTP_BURST performance mode. Classical computer vision runs on CPU. GPU delegate is available as a fallback.

---

## Privacy

- No cloud calls
- No external APIs
- No user accounts
- No telemetry
- All inference on-device
- All scan data stays on the phone until the engineer explicitly exports it

---

## Team

- Suhas B Hiremath — Team Lead
- B S Sachin — Team Member
- Gourav raj - Team Member
Track: Open Innovation
