package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.editor.asset.AssetPathUtils;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;

import java.io.File;
import java.util.List;

public sealed interface AssetLocation permits AssetLocation.Local, AssetLocation.Remote {
    AssetSourceKind sourceKind();

    record Local(File directory, boolean favorites, List<String> favoritePaths) implements AssetLocation {
        public Local {
            favoritePaths = favoritePaths == null ? List.of() : List.copyOf(favoritePaths);
        }

        @Override
        public AssetSourceKind sourceKind() {
            return AssetSourceKind.LOCAL;
        }
    }

    record Remote(String directory, boolean createIfMissing) implements AssetLocation {
        public Remote {
            directory = AssetPathUtils.normalizeRemoteDirectory(directory);
        }

        @Override
        public AssetSourceKind sourceKind() {
            return AssetSourceKind.REMOTE;
        }
    }
}
