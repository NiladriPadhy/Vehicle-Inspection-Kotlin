# Firestore Contract

Document shapes and access rules. Full tree is in `data-model.md`. Firestore SDK types are
confined to `:core:data`; mappers convert to/from domain models.

## Paths

```text
inspectors/{inspectorId}
inspectors/{inspectorId}/inspections/{inspectionId}
inspectors/{inspectorId}/inspections/{inspectionId}/images/{imageId}
inspectors/{inspectorId}/inspections/{inspectionId}/images/{imageId}/findings/{findingId}
inspectors/{inspectorId}/inspections/{inspectionId}/images/{imageId}/annotations/{annotationId}
inspectors/{inspectorId}/inspections/{inspectionId}/auditLog/{entryId}
```

Document ids equal local Room ids (idempotent upserts). Timestamps are epoch millis (Long).

## Inspection document (fields)

`context, status, currentStep, createdAt, updatedAt, completedAt, gps{lat,lng}?,
deviceInfo, scores{exterior,interior,safety,cosmetic,confidence}, overallCondition,
finalRecommendation, summary, vehicle{...}, report{remoteJsonUrl,generatedAt,status}`

## Image document (fields)

`section, position, captureState, skipReason?, remoteUrl, thumbnailUrl, width, height,
sizeBytes, capturedAt, orientation, quality`

## Finding / Annotation documents

As defined in `data-model.md` / `ai-gemini-contract.md`; findings only written after AI
validation passes.

## Security rules (intent)

```text
match /inspectors/{uid}/{document=**} {
  allow read, write: if request.auth != null
                     && request.auth.uid == uid
                     && appCheckVerified();
}
```

- Ownership scoped strictly to the authenticated inspector.
- App Check enforced for all access.
- Enum/string field shape validated server-side where practical; the client remains the
  authoritative validator for AI-derived content prior to write.
