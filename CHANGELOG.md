# Changelog for EverCrops: Farmer's Delight (NeoForge 1.21.1)

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-06-23

### Changed

- Now requires EverCrops 4.0.0 or newer.
- Rebuilt on EverCrops 4.0.0's new shared add-on framework. Everything works exactly as before — no gameplay changes.

## [1.1.4] - 2026-06-20

### Changed

- Now requires EverCrops 3.5.3 or newer.

### Fixed

- Fixed the same right-click harvesting issue (e.g. Harvest With Ease) for Farmer's Delight crops — tomatoes, rice, budding tomato seedlings, and mushroom colonies. After being away, harvesting and replanting one of these no longer lets it instantly grow back from leftover catch-up growth.

## [1.1.3] - 2026-06-06

### Fixed

- Fixed the add-on loading in the wrong order, which could make the game crash when other Farmer's Delight add-ons (such as Ender's Delight) were also installed. It now loads after Farmer's Delight and EverCrops, the way it always should have.

## [1.1.2] - 2026-06-03

### Changed

- Now requires EverCrops 3.5.1 or newer.

### Fixed

- Rice, tomatoes, budding tomato seedlings, and mushroom colonies could lose a little of their catch-up growth in the very instant they caught up after you'd been away. They now keep all of it.
- Made sure your Farmer's Delight crops keep being tracked alongside EverCrops 3.5.1's new automatic cleanup, so they're never accidentally forgotten.

## [1.1.1] - 2026-5-27

### Fixed

- Had the mixin path pointing to an old package. Fixed.

## [1.1.0] - 2026-5-26

### Added

- **Organic Compost** now finishes composting while your base is unloaded. Place it, go
  explore for a while, and come back to Rich Soil instead of a block that hasn't moved
  since you left. The more compost activators and water nearby, the faster it ripens.
- **Mushrooms on Rich Soil** now convert into Mushroom Colonies the moment you return
  to the area, instead of waiting for the next random tick after you arrive.
- **Mushroom Colonies** now grow while the chunk is unloaded. A colony that was stuck at
  one mushroom will have grown into a fuller cluster by the time you check on it.

### Fixed

- Fixed a game freeze (server deadlock) that occurred when ropes were placed above tomato
  plants and the tomato climbed onto them. The game would lock up a short time after
  the climb started and had to be force-killed. Caused by Farmer's Delight 1.3.0's new
  rope-climbing tomato block inheriting code from the base tomato block without carrying
  over all of the same block properties.
- Fixed tomato catch-up silently not working at all since 1.0.0. Tomatoes were never
  registered when placed, so every tick was a no-op. Tomato catch-up now works correctly.
- Tomatoes climbing ropes now also catch up while the area is unloaded. Each rope segment
  the tomato has grown to will advance through its age stages just like a ground tomato does.

## [1.0.0] - 2026-5-3

### Added

- Catch-up growth for **tomatoes** (`TomatoBlock`) — AGE (VINE_AGE) 0–3, requires light level ≥ 9. Ground-planted tomatoes only; rope-logged variants are excluded as their multi-block vine mechanics are handled entirely by vanilla.
- Catch-up growth for **budding tomato seedlings** (`BuddingTomatoBlock`) — AGE 0–3 via `BuddingBushBlock`, requires light level ≥ 9. Catch-up advances the seedling to max age only; the transition to a full `TomatoBlock` via `growPastMaxAge()` is left to vanilla's own `randomTick` to keep multi-block placement logic in a clean context.
- Catch-up growth for **rice** (`RiceBlock`) — AGE 0–3, requires light level ≥ 9. Catch-up advances the base rice block; placement of `RicePaniclesBlock` above at max age is left to vanilla.
- Note: **cabbage**, **onion**, and **rice panicles** (`CabbageBlock`, `OnionBlock`, `RicePaniclesBlock`) extend `CropBlock` without overriding `randomTick` and are already covered by EverCrops's `CropBlockMixin` — no additional mixins are needed for these crops.
- Uses EverCrops's shared `CropRegistry` and `CropState` persistence (`evercrops.dat` per dimension). All EverCrops debug commands (`/evercrops inspect`, `/evercrops tick`, `/evercrops simulate`) work with Farmer's Delight crops automatically.
