# Gemini Vision AI Contract

Gemini Vision is accessed only through `AiVisionPort` (data layer). The model is prompted
to return **structured JSON**. Every response MUST be validated before use (Principle VIII).

## 1. Image damage detection

**Request context**: image bytes + a structured prompt including `section`
(EXTERIOR/INTERIOR), `position` (e.g. `FRONT`), and the allowed `DamageType` taxonomy for
that section. The prompt instructs the model to return ONLY JSON matching the schema.

**Expected response JSON**:

```json
{
  "findings": [
    {
      "damageType": "SCRATCH",
      "confidence": 0.82,
      "severity": "MEDIUM",
      "boundingBox": { "x": 0.12, "y": 0.34, "w": 0.20, "h": 0.10 },
      "repairRecommendation": "Polish or repaint affected panel section",
      "reviewRequired": false
    }
  ]
}
```

**Validation rules (all must pass, else `AiInvalidResponse` → reject + retry)**:
- Top-level object with a `findings` array (may be empty).
- `damageType` ∈ known `DamageType` enum (unknown → mapped to `OTHER` only if configured,
  otherwise rejected).
- `confidence` is a number in `[0.0, 1.0]`.
- `severity` ∈ {LOW, MEDIUM, HIGH, CRITICAL}.
- `boundingBox.x,y,w,h` numbers in `[0.0, 1.0]`; `w > 0`, `h > 0`; `x+w ≤ 1`, `y+h ≤ 1`.
- `repairRecommendation` non-empty string.
- `reviewRequired` boolean; app additionally sets `true` when `confidence < threshold`.
- Response parses as strict JSON; extra prose outside JSON is rejected.

## 2. Annotation re-verification

**Request context**: image bytes + the annotated region geometry + the inspector's
claimed damage type/severity.

**Expected response JSON**:

```json
{
  "confirmed": true,
  "correctedDamageType": "DEEP_SCRATCH",
  "correctedSeverity": "HIGH",
  "nearbyFindings": [ { "damageType": "PAINT_CHIP", "confidence": 0.7, "severity": "LOW",
                        "boundingBox": {"x":0.5,"y":0.5,"w":0.05,"h":0.05},
                        "repairRecommendation": "...", "reviewRequired": true } ],
  "mergedWith": ["findingId-1"],
  "inconsistency": false
}
```

**Validation**: `confirmed` boolean; corrected fields (if present) valid enums;
`nearbyFindings[]` each pass the finding validation above; `mergedWith` array of ids;
`inconsistency` boolean. Invalid → reject + retry; inspector's manual annotation is
preserved regardless.

## 3. Final verification (whole inspection)

**Request context**: the set of images (or references) + counts per position.

**Expected response JSON**:

```json
{
  "scores": { "exterior": 78, "interior": 85, "safety": 90, "cosmetic": 72, "confidence": 88 },
  "overallCondition": "GOOD",
  "summary": "Minor cosmetic wear; no structural or safety concerns detected.",
  "integrity": {
    "missingImages": ["REAR_WINDSHIELD"],
    "duplicateImages": [],
    "lowQualityImages": ["ROOF"],
    "suspiciousImages": [],
    "potentialFraud": false
  }
}
```

**Validation**: each score integer in `[0, 100]`; `overallCondition` non-empty; `summary`
non-empty; integrity arrays are string arrays; `potentialFraud` boolean. Invalid → reject +
retry; scores are not persisted until validation passes.

## Failure & offline handling

- Network/timeout → `AiUnavailable` (retryable, queued via `AiAnalysisWorker`).
- Validation failure → `AiInvalidResponse` (retryable); never stored or shown as
  authoritative; capture/inspection can proceed with findings marked pending.
- Low-confidence findings are surfaced with `reviewRequired = true`.
