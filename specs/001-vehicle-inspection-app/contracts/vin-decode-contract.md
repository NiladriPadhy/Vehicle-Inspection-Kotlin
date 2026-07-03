# VIN Decode & OCR Contract

VIN handling has two stages: capture (manual or OCR) and decode (via `VinDecodeSource`).

## OCR (ML Kit Text Recognition)

- Input: a CameraX frame / captured image of the VIN label.
- Output: extracted text → candidate VIN via regex `^[A-HJ-NPR-Z0-9]{17}$` (excludes
  I, O, Q) + check-digit validation.
- On low-confidence or invalid pattern → prompt inspector to confirm/correct before decode.

## Decode (`VinDecodeSource`)

Abstracted behind `VehicleRepository.decodeVin`. Provider-agnostic.

**Input**: 17-char VIN string.

**Output (`VehicleDecodeDto`, all fields optional except vin)**:

```json
{
  "vin": "1HGCM82633A004352",
  "year": 2020,
  "manufacturer": "Honda",
  "make": "Honda",
  "model": "Accord",
  "variant": "EX-L",
  "trim": "EX-L",
  "bodyStyle": "Sedan",
  "fuelType": "Petrol",
  "transmission": "Automatic",
  "color": null,
  "registrationNumber": null
}
```

**Outcomes**:
- Success with full/partial data → map to `Vehicle`; missing fields remain editable.
- Not found / error → `VinLookupFailed` → UI routes to manual attribute selection
  (manufacturer, model, variant, year, color required).

**Validation**: `vin` must be a valid 17-char VIN; `year` (if present) within a plausible
range; string fields trimmed. Provider selection is deferred to implementation and MUST be
swappable without touching domain/presentation.
