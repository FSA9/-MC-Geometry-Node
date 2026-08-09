package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.List;
import java.util.Objects;

public record AssetTransferFailure(
        AssetTransferErrorCode code,
        String messageKey,
        List<String> messageArguments,
        String detail
) {
    public AssetTransferFailure {
        code = Objects.requireNonNull(code, "code");
        messageKey = Objects.requireNonNullElse(messageKey, "geometry_node.asset_transfer.error.unknown");
        messageArguments = messageArguments == null ? List.of() : List.copyOf(messageArguments);
        detail = Objects.requireNonNullElse(detail, "");
    }

    public boolean isRetryable() {
        return code.isRetryable();
    }
}
