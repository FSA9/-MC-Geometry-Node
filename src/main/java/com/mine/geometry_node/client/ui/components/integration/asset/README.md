# Asset picker

`AssetPickerDialog` reuses `AssetFileBrowserPanel` and the owning `MainUiServices` modal stack.

```java
var options = new AssetPickerOptions(
        AssetPickerOptions.Sources.REMOTE,
        AssetPickerOptions.Target.FILE,
        entry -> acceptsFile(entry),
        new AssetLocation.Remote(initialDirectory, false));
AssetPickerDialog.show(anchor, title, options, entry -> useSelection(entry));
```

The result is an `AssetEntry`: remote `path()` is relative to the server asset root;
local `localFile()` identifies the selected file. Cancellation does not invoke the callback.
Successful selection closes the modal before invoking the callback, once.

Sources are `LOCAL`, `REMOTE`, or `BOTH`. Targets are `FILE`, `FOLDER`, or
`FILE_OR_FOLDER` (used by the legacy path adapter). The file predicate controls file
visibility and confirmation; directories remain navigable. Folder targets hide files.
Confirmation rechecks source, target and predicate, including browser pick actions.
Favorites is a collection, so it cannot be selected as a current folder.
Selecting the current remote folder requires a successful listing for that location;
pending and failed requests cannot confirm an unverified path.

`initialDirectory` is an existing `AssetLocation.Local` or `AssetLocation.Remote`;
null uses local drafts when local is allowed, otherwise the remote root. Local
locations must name a concrete directory; remote locations never create directories
implicitly. With both sources, delayed remote capability discovery restores an initial
remote location only if the user has not navigated elsewhere. Remote-only pickers
always stay remote, including when remote browsing is unavailable.

The constructor and `show` accept an optional final `UiTheme` parameter; defaults use
`UiThemes.EDITOR`. The theme controls modal chrome, scrim, status and actions. The
reused browser/sidebar keep their existing editor styling. `show` binds the anchor's
retained window lifetime and uses it for nested modal ownership and return focus.

`FilePickerDialog` and `FolderPickerDialog` retain their existing factories and callback
formats. File paths retain legacy inference/canonicalization. Folder pickers retain
explicit folder creation through the browser's existing public actions. Domain rules
and graph binding belong to callers, outside this component.

Focused tests: `AssetPickerDialogTest` and `FilePickerNavigationTest`.
