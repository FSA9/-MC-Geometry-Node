package com.mine.geometry_node.client.ui.components.integration.equipment;

import com.mine.geometry_node.client.entitypreview.EntityPreviewClient;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.EntityTemplatePreviewView;
import com.mine.geometry_node.client.ui.components.integration.valuepreview.ValuePreviewStyle;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.core.engine.system.entitypreview.appearance.EntityAppearanceCapture;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import icyllis.modernui.core.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Prepares outfit snapshots on the Minecraft render thread and publishes them on the UI queue.
 * Even a live reference is captured and rendered as a detached template, never dressed in place. */
final class EquipmentEntityPreviewView extends EntityTemplatePreviewView {
    private record Source(EntityTemplateValue template, UUID entityId, String dimension,
                          Map<String, ItemStack> equipment) {}
    private volatile Source source = new Source(EntityTemplateValue.EMPTY, null, "", Map.of());
    // Render-thread cache; Source identity fences concurrent UI edits and source switches.
    private Source appliedSource;
    private EntityTemplateValue appliedBase;
    private Object appliedLevel;
    private Object appliedConnection;
    private Entity capturedLive;
    private EntityTemplateValue capturedAppearance;
    private boolean previewAttached;
    private long attachmentRevision;
    private record Publication(Source source, long attachment, EntityTemplateValue template) {}
    private Publication pendingPublication;
    private boolean publicationPosted;
    private final Runnable applyPublication = this::applyPendingPreview;
    private final EquipmentPreviewPreparation preparation = new EquipmentPreviewPreparation(System::nanoTime,
            failure -> System.getLogger(EquipmentEntityPreviewView.class.getName()).log(System.Logger.Level.WARNING,
                    "Equipment preview preparation failed; using the base preview", failure));

    EquipmentEntityPreviewView(Context context, UiTheme theme) {
        super(context, RotationMode.HORIZONTAL, ValuePreviewStyle.forTheme(theme));
        setEditable(true);
    }

    synchronized void setTemplateSource(EntityTemplateValue template) {
        source = new Source(template == null ? EntityTemplateValue.EMPTY : template, null, "", source.equipment());
        super.setDisplayTemplate(source.template());
    }

    synchronized void setReferenceSource(UUID entityId, String dimension) {
        source = new Source(EntityTemplateValue.EMPTY, entityId, dimension == null ? "" : dimension, source.equipment());
        super.setDisplayReference(entityId, dimension);
    }

    synchronized void setEquipment(Map<String, ItemStack> equipment) {
        Map<String, ItemStack> detached = new LinkedHashMap<>();
        equipment.forEach((key, stack) -> detached.put(key, stack.copy()));
        source = new Source(source.template(), source.entityId(), source.dimension(), Map.copyOf(detached));
        requestNativePreviewRender();
        invalidate();
    }

    @Override protected synchronized void onAttachedToWindow() {
        super.onAttachedToWindow();
        attachmentRevision++;
        previewAttached = true;
    }

    @Override protected synchronized void onDetachedFromWindow() {
        previewAttached = false;
        attachmentRevision++;
        pendingPublication = null;
        publicationPosted = false;
        removeCallbacks(applyPublication);
        appliedSource = null;
        appliedBase = null;
        appliedLevel = null;
        appliedConnection = null;
        capturedLive = null;
        capturedAppearance = null;
        preparation.reset();
        super.onDetachedFromWindow();
    }

    @Override protected synchronized void renderNativePreviewContent(GuiGraphicsExtractor graphics, float deltaTick,
            float guiScale, float alpha, float left, float top, float width, float height, float viewportScale) {
        if (!previewAttached) return;
        Minecraft minecraft = Minecraft.getInstance();
        Source input = source;
        if (minecraft != null && minecraft.level != null) {
            preparation.run(input, () -> source == input, () -> preparePreview(minecraft, input), () -> {
                appliedSource = null;
                appliedBase = null;
                appliedLevel = null;
                appliedConnection = null;
                capturedLive = null;
                capturedAppearance = null;
                publishPreview(input, null);
            });
        }
        super.renderNativePreviewContent(graphics, deltaTick, guiScale, alpha, left, top, width, height, viewportScale);
    }

    private void preparePreview(Minecraft minecraft, Source input) {
        EntityTemplateValue appearance = input.template();
        if (input.entityId() != null) {
            boolean sameDimension = input.dimension().isEmpty()
                    || minecraft.level.dimension().identifier().toString().equals(input.dimension());
            Entity live = sameDimension ? minecraft.level.getEntity(input.entityId()) : null;
            if (live != null && !live.isRemoved()) {
                if (live != capturedLive || appliedSource != input || appliedLevel != minecraft.level) {
                    // Existing appearance capture excludes identity/world relationships before encoding.
                    capturedAppearance = new EntityTemplateValue(BuiltInRegistries.ENTITY_TYPE.getKey(live.getType()).toString(),
                            EntityAppearanceCapture.capture(live));
                    capturedLive = live;
                }
                appearance = capturedAppearance;
            } else {
                capturedLive = null;
                capturedAppearance = null;
                EntityPreviewClient.Result result = EntityPreviewClient.INSTANCE.resolve(input.entityId(), input.dimension());
                appearance = result.state() == EntityPreviewClient.State.READY ? result.template() : null;
                if (appearance == null) {
                    synchronized (this) {
                        if (source == input) {
                            // Let the shared reference mode show its bounded loading/error state.
                            publishPreview(input, null);
                            appliedSource = null;
                            appliedBase = null;
                        }
                    }
                }
            }
        }
        if (appearance != null && (input != appliedSource || !appearance.equals(appliedBase)
                || appliedLevel != minecraft.level || appliedConnection != minecraft.getConnection())) {
            EntityTemplateValue dressed = EquipmentPreviewTemplates.apply(appearance, input.equipment(), minecraft.level.registryAccess());
            synchronized (this) {
                if (source == input) {
                    publishPreview(input, dressed);
                    appliedSource = input;
                    appliedBase = appearance;
                    appliedLevel = minecraft.level;
                    appliedConnection = minecraft.getConnection();
                }
            }
        }
    }

    /** Render work publishes detached values only. MUI setters invalidate the attached view tree
     * and must run on its UI queue, including loading/error fallback and source-mode switches. */
    private synchronized void publishPreview(Source expected, EntityTemplateValue template) {
        if (!previewAttached || source != expected) return;
        pendingPublication = new Publication(expected, attachmentRevision, template);
        // One pending UI task per view even when rendering outpaces UI event processing.
        if (publicationPosted) return;
        publicationPosted = true;
        post(applyPublication);
    }

    private synchronized void applyPendingPreview() {
        Publication publication = pendingPublication;
        pendingPublication = null;
        publicationPosted = false;
        if (publication == null || !previewAttached || source != publication.source()
                || attachmentRevision != publication.attachment()) return;
        if (publication.template() != null) super.setDisplayTemplate(publication.template());
        else if (publication.source().entityId() != null)
            super.setDisplayReference(publication.source().entityId(), publication.source().dimension());
        else super.setDisplayTemplate(publication.source().template());
    }
}
