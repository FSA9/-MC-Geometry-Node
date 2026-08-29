# GeometryNode

[中文](docs/README_CN.md) | [English](README.md)

GeometryNode is an in-game visual programming and content-authoring mod for Minecraft, inspired by Unreal Engine Blueprints and Blender Geometry Nodes. It brings gameplay logic, entity behavior, quests, dialogue, assets, and supporting development tools into one node-based editor.

Creators can build and iterate on gameplay systems without writing a dedicated mod for every interaction. Graph assets can be edited, saved, loaded, validated, and executed directly in the Minecraft runtime.

## Features

- **Visual graph editor** — Create and organize graphs with node search, typed ports, connections, groups, properties, reusable assets, undo/redo, and in-editor previews.
- **Blueprint runtime** — Drive gameplay through events, execution flow, variables, conditions, delays, functions, and nodes for players, entities, blocks, items, inventories, worlds, and visual effects.
- **Behavior trees and entity AI** — Build independent behavior-tree assets with selectors, sequences, decorators, conditions, actions, same-asset node groups, blackboards, runtime debugging, and controlled coordination with Minecraft's native `Goal` and `Brain` AI.
- **Quest system** — Define quest metadata, objectives, conditions, counters, rewards, and status transitions, with persistent player progress and an in-game quest interface.
- **Dialogue and shops** — Create branching conversations with rich text and selectable choices, manage server-side dialogue sessions, and connect dialogue flow to shop transactions and graph execution.
- **AI-assisted editing and MCP** — Expose authenticated, loopback-only MCP tools that let compatible AI clients inspect editor and graph state, query node contracts, and propose reversible graph patches. Changes are dry-run validated and presented for approval before being committed as one undoable edit.
- **Integrated terminal** — Use multiple command or shell tabs inside the editor, including an interactive PowerShell PTY on Windows and graph-aware commands shared with the MCP tooling.
- **Asset workflow** — Browse local and remote assets, transfer files between client and server, preview images and schematics, and manage graph assets from the editor.
- **Geometry and model pipeline** — Build procedural geometry for world operations and load custom model assets through the project's model import, validation, GPU resource, and rendering pipeline.
- **Extension API** — Register custom nodes, graph value codecs, markers, dialogue presentations, and editor integrations through a plugin-oriented API.

## Current Target

- Minecraft `26.1.2`
- NeoForge `26.1.2.75` or newer
- ModernUI `3.13.0.5` or newer
- Architectury API `20.0.7` or newer
- Java `25`

## Development Status

GeometryNode is under active development. Features are being integrated and hardened, and node APIs, graph formats, runtime behavior, and compatibility requirements may still change between versions.

## Interface Preview

![GeometryNode interface preview](docs/img.png)
