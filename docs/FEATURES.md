# Feature Analysis: Khara

Khara is designed to excel in the **Creative Phone Use** and **Office Kit** categories of the iQOO Hackathon 2026.

## 1. Creative Phone Use (Sensors as Forensic Tools)

### Camera (Micro-Scale Forensics)
Instead of simple photography, Khara uses the camera for high-dimensional signal extraction. The guided 3-angle capture (Top, Raking, Side) simulates a lab-grade goniophotometer setup. This allows the system to detect "blacktopping"—a common counterfeit technique where old markings are sanded off and a new layer of epoxy is applied.

### Haptics (Immediate Triage)
In noisy industrial environments, visual alerts can be missed. Khara uses distinct vibration pulses (Pass vs. Flagged) to provide immediate, non-visual confirmation. This turns the phone into a tactile triage device.

### Accelerometer (Angle Guidance)
The guided UI ensures that the raking light capture is done at the correct tilt. This ensures the consistency of the visual embedding comparison.

## 2. Office Kit Ready (Enterprise Workflow)

### Secure Audit Entry
Khara solves the "trust gap" in field triage. By generating a hash-chained audit entry locally, it provides a verifiable record that can be synced to enterprise dashboards (Office Kit) without ever uploading the raw forensic images.

### One-Tap Sync
The "Sync Audit Log" feature demonstrates the "Office Kit" story: secure, friction-free data movement from the field (phone) to the desk (dashboard) via JSON-LD.

### Offline Resilience
Industrial sites and secure defense facilities often prohibit cloud connectivity. Khara’s 100% offline nature makes it the only tool capable of operating in these "air-gapped" environments.

## Feature Rubric Mapping
| Feature | Rubric Category | Impact |
| :--- | :--- | :--- |
| **MobileNet Embedding** | Creative Phone Use | Turns standard camera into a texture spectrometer. |
| **3-Pulse Flag Haptic** | Creative Phone Use | Eyes-up industrial safety. |
| **Hash-Chained Audit** | Office Kit | Verifiable forensic documentation. |
| **JSON-LD Sync** | Office Kit | Enterprise system interoperability. |
