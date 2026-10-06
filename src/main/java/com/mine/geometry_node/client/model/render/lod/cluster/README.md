# Archived CPU Cluster Experiment

This directory preserves the former CPU cluster hierarchy experiment for future
reference. Its Java sources and matching tests under `render/lod/cluster` are
excluded from the active Gradle source sets. The active model LOD implementation
lives in the sibling `render/lod/model` package.

The archived implementation is not connected to importing, rendering, commands,
the HUD, or performance reports. It must not be enabled merely by restoring an
import: its memory growth, representative-triangle proxies, hierarchy selection,
shadow projection, and resident fallback behavior require a fresh review.

`integration/*.java.disabled` contains snapshots of the previous renderer and
asset integration. They are historical text, not compiled Java. The snapshots
retain their original package names and references so the prior wiring remains
readable. They may differ from the current renderer and asset classes.

Ordinary source-index cluster bounds used for frustum culling are independent of
this experiment and remain active. Lighting BVHs continue using full source
geometry.
