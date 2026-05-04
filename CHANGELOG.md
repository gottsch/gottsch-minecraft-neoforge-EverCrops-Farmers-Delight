# Changelog for EverCrops: Farmer's Delight (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-5-3

### Added

- Catch-up growth for **tomatoes** (`TomatoBlock`) — AGE (VINE_AGE) 0–3, requires light level ≥ 9. Ground-planted tomatoes only; rope-logged variants are excluded as their multi-block vine mechanics are handled entirely by vanilla.
- Catch-up growth for **budding tomato seedlings** (`BuddingTomatoBlock`) — AGE 0–3 via `BuddingBushBlock`, requires light level ≥ 9. Catch-up advances the seedling to max age only; the transition to a full `TomatoBlock` via `growPastMaxAge()` is left to vanilla's own `randomTick` to keep multi-block placement logic in a clean context.
- Catch-up growth for **rice** (`RiceBlock`) — AGE 0–3, requires light level ≥ 9. Catch-up advances the base rice block; placement of `RicePaniclesBlock` above at max age is left to vanilla.
- Note: **cabbage**, **onion**, and **rice panicles** (`CabbageBlock`, `OnionBlock`, `RicePaniclesBlock`) extend `CropBlock` without overriding `randomTick` and are already covered by EverCrops's `CropBlockMixin` — no additional mixins are needed for these crops.
- Uses EverCrops's shared `CropRegistry` and `CropState` persistence (`evercrops.dat` per dimension). All EverCrops debug commands (`/evercrops inspect`, `/evercrops tick`, `/evercrops simulate`) work with Farmer's Delight crops automatically.
