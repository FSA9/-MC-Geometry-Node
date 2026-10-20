package com.mine.geometry_node.client.ui.document.edit.commands.document;

import com.mine.geometry_node.client.ui.document.edit.ICommand;
import com.mine.geometry_node.client.ui.document.edit.GraphController;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.node.document.NodeGraph;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 修改整张图的类型、描述和标签，并将修改纳入编辑器撤销栈。
 */
public final class CmdSetGraphMetadata implements ICommand {
    private final GraphController mController;
    private final String mOldGraphTypeId;
    private final String mOldComment;
    private final List<String> mOldTags;
    private final QuestDefinition mOldQuestDefinition;
    private final String mNewComment;
    private final String mNewGraphTypeId;
    private final List<String> mNewTags;
    private final QuestDefinition mNewQuestDefinition;

    public CmdSetGraphMetadata(GraphController controller, String newGraphTypeId, String newComment,
                               List<String> newTags, QuestDefinition newQuestDefinition) {
        mController = controller;
        NodeGraph graph = controller != null ? controller.getContext().getGraph() : null;
        mOldComment = normalizeComment(graph != null ? graph.comment : null);
        mOldGraphTypeId = graph != null ? graph.getGraphTypeId() : "";
        mOldTags = normalizeTags(graph != null ? graph.tags : null);
        mOldQuestDefinition = graph != null && graph.quest != null ? graph.quest : QuestDefinition.EMPTY;
        mNewComment = normalizeComment(newComment);
        mNewGraphTypeId = newGraphTypeId != null ? newGraphTypeId.trim() : "";
        mNewTags = normalizeTags(newTags);
        mNewQuestDefinition = newQuestDefinition != null ? newQuestDefinition : QuestDefinition.EMPTY;
    }

    @Override
    public boolean canExecute() {
        return mController != null
                && (!Objects.equals(mOldGraphTypeId, mNewGraphTypeId)
                || !Objects.equals(mOldComment, mNewComment)
                || !Objects.equals(mOldTags, mNewTags)
                || !Objects.equals(mOldQuestDefinition, mNewQuestDefinition));
    }

    @Override
    public void execute() {
        mController.setGraphMetadata(mNewGraphTypeId, mNewComment, mNewTags, mNewQuestDefinition);
    }

    private static String normalizeComment(String comment) {
        return comment != null ? comment.trim() : "";
    }

    private static List<String> normalizeTags(List<String> tags) {
        return new ArrayList<>(TagSet.normalize(tags, TagPolicy.GRAPH_EDIT_LEGACY));
    }
}
