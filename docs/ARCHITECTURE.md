# Technical Architecture: Khara

Khara is built on a "Privacy-First Forensic" architecture. All data remains in the application's secure sandbox, and all inference is performed on the local NPU/GPU.

## Data Flow Diagram
```text
[ Lens / CameraX ] 
       |
       v
[ Image Pre-processing ] -- (Resize 224x224, Normalize INT8)
       |
       v
[ TFLite Interpreter ] -- (MobileNet-V2 INT8)
       |
       v
[ Penultimate Layer ] -- (1280-dim Vector Extraction)
       |
       v
[ Cosine Similarity ] <-> [ Local Reference JSON ]
       |
       v
[ Verdict Engine ] ------> [ Haptic Feedback ]
       |
       v
[ Secure Ledger ] -------> [ SHA-256 Hash Chain ]
```

## On-Device Models

### 1. MobileNet-V2 (Primary Vision)
- **Role:** Feature Extraction.
- **Parameters:** ~3.4 Million.
- **Quantization:** INT8 (via TFLite).
- **Reasoning:** Chosen for its optimal balance of latency and feature-space resolution. By using the penultimate layer embeddings instead of final classification, we can detect subtle deviations in surface texture and marking kerning without needing a specific "Counterfeit" class during training.

### 2. Whisper Tiny (Future Scope)
- **Role:** Voice-to-Text session logging.
- **Parameters:** ~39 Million.
- **Reasoning:** Enables hands-free operation in industrial environments where field engineers are often wearing gloves or holding equipment.

### 3. Gemma 3 (Future Scope)
- **Role:** Reasoning & Triage Explanation.
- **Reasoning:** Converts raw similarity scores into human-readable triage advice. "Flagged due to surface reflectivity mismatch" is more actionable for a non-expert than "Score: 0.42".

## Security & Integrity
Every scan result is passed through a **Hash Chain** protocol. The Scan ID, Timestamp, and Overall Score are hashed using **SHA-256**. This ensures that the local audit trail is tamper-evident, a critical requirement for forensic reports used in legal or insurance claims.
