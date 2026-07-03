# Firebase Storage Contract

## Path hierarchy

```text
inspections/{inspectionId}/exterior/{position}.jpg
inspections/{inspectionId}/interior/{position}.jpg
inspections/{inspectionId}/documents/{documentType}.jpg   # Old: RC, POLLUTION_CERTIFICATE, INSURANCE
inspections/{inspectionId}/thumbnails/{imageId}.jpg
inspections/{inspectionId}/report.json
```

- Deterministic, id-keyed paths → idempotent + resumable uploads (no duplicates after an
  app kill mid-sync).
- Images are compressed before upload (target long-edge + JPEG quality) while preserving
  analysis fidelity.
- Content types: `image/jpeg` for images/thumbnails, `application/json` for the report.

## Upload semantics

- Uploaded by `ImageUploadWorker` / `ReportUploadWorker` (WorkManager), ordered per
  inspection (images → metadata → AI results → report).
- On success, the returned download URL is written back to the Room row (`remoteUrl` /
  `remoteJsonUrl`) and mirrored to Firestore.
- Exponential backoff on failure; per-item `syncState` updated.

## Security rules (intent)

```text
match /inspections/{inspectionId}/{allPaths=**} {
  allow read, write: if request.auth != null
                     && ownsInspection(request.auth.uid, inspectionId)
                     && appCheckVerified();
}
```

Only the owning inspector may read/write a given inspection's objects; App Check enforced.
