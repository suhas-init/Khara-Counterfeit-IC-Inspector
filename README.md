# Khara: Offline Counterfeit Component Inspector

**Khara** is a phone-native forensic tool designed for immediate triage of counterfeit Integrated Circuits (ICs) in high-risk environments. It operates entirely on-device, requiring no internet connectivity to perform deep visual analysis and maintain secure audit logs.

## The Problem: The $75B Silent Risk
Counterfeit electronic components cause an estimated **$75 billion annual loss** to the global economy. Beyond financial damage, "ghost" components have been linked to critical failures in defense systems (e.g., the THAAD incident) and medical equipment. Traditional forensic labs are expensive and slow; field engineers need a "first-response" triage tool in their pockets.

## Our Solution: Phone-Native Forensic Triage
Khara transforms a standard smartphone into a forensic workstation. By combining macro-photography with on-device computer vision, it detects "blacktopping" (remarking) and texture anomalies that are invisible to the naked eye.

### Technical Architecture
- **Vision Core:** MobileNet-V2 (INT8 Quantized) for high-dimensional feature extraction.
- **Comparison Engine:** Real-time Cosine Similarity against a local ledger of "known-good" reference embeddings.
- **Voice Trigger (Future):** OpenAI Whisper Tiny for hands-free operation in industrial cleanrooms.
- **Intelligence (Future):** Google Gemma 3 for local reasoning on detected anomalies.

## Key Features
- **3-Angle Guided Capture:** Forces the user to capture top-down, raking light (for texture), and marking views.
- **Real-Time Similarity Scoring:** Compares current component signature against reference vectors.
- **Hash-Chained Audit Log:** Generates a SHA-256 tamper-evident record for every scan, compatible with enterprise Office Kit dashboards.
- **Haptic Triage:** Distinct vibration patterns for PASS/FLAGGED results allow for "eyes-up" operation.

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Camera:** CameraX API
- **AI/ML:** TensorFlow Lite
- **Security:** Java MessageDigest (SHA-256)

## Setup & Run
1. **Clone the repository.**
2. **Download the TFLite model:**
   - Download [MobileNet-V2 1.0 224](https://tfhub.dev/google/lite-model/imagenet/mobilenet_v2_100_224/classification/5/metadata/1).
   - Place `mobilenet_v2_1.0_224.tflite` in `app/src/main/assets/`.
3. **Build in Android Studio:** Ensure Min SDK is 26 and target is 34.
4. **Permissions:** Grant Camera permission on first launch.

## Future Scope (iQOO 30-Hour Build)
- **Reference Expansion:** Integration of a local SQLite database for thousands of chip families.
- **Macro Mode:** Optimization for the iQOO high-resolution macro sensors.
- **Gemma Integration:** Local LLM to explain *why* a chip was flagged (e.g., "Font kerning mismatch").
- **Sync Protocol:** Seamless one-tap export to Office Kit via secure JSON-LD.

## Team Members
- [Your Name / Placeholder]
- [Teammate 1 / Placeholder]
