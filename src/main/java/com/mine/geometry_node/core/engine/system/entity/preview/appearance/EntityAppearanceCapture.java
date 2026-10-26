package com.mine.geometry_node.core.engine.system.entity.preview.appearance;

import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewLimits;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewNbt;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;

import com.mine.geometry_node.mixin.entitypreview.EntityAppearanceInvoker;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.DelegatingOps;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueOutput;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.*;

/** Filters before invoking field codecs, never materializing the original saved entity NBT. */
public final class EntityAppearanceCapture {
    private EntityAppearanceCapture() {}

    /** Shared preview policy for both live replies and persistent directory observations. */
    public static String unsupportedReason(Entity entity) {
        if (entity instanceof net.minecraft.world.entity.player.Player) return "unsupported_player";
        if (entity instanceof Interaction) return "no_appearance";
        if (!entity.getType().canSerialize() || !entity.getType().canSummon()) return "unsupported_type";
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        // Unknown mod serializers cannot safely be invoked using the vanilla appearance schema.
        return id == null || !id.getNamespace().equals("minecraft")
                || !entity.getClass().getName().startsWith("net.minecraft.world.entity.")
                || id.getPath().equals("mannequin") ? "unsupported_type" : "";
    }

    public static EntityTemplateValue template(Entity entity) {
        if (!unsupportedReason(entity).isEmpty()) return EntityTemplateValue.EMPTY;
        return new EntityTemplateValue(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), capture(entity));
    }

    public static CompoundTag capture(Entity entity) {
        var output = new AppearanceOutput(entity.registryAccess());
        output.storeNullable("CustomName", ComponentSerialization.CODEC, entity.getCustomName());
        output.putBoolean("CustomNameVisible", entity.isCustomNameVisible());
        output.putBoolean("Invisible", entity.isInvisible());
        output.putBoolean("Glowing", entity.hasGlowingTag());
        output.putBoolean("HasVisualFire", entity.displayFireAnimation());
        output.putInt("TicksFrozen", entity.getTicksFrozen());
        if (entity instanceof TamableAnimal animal)
            output.putBoolean("Tame", animal.isTame());
        // Never enter Entity.saveWithoutId: even a discard sink would run attachment serializers.
        ((EntityAppearanceInvoker) entity).geometryNode$saveAppearanceFields(output);
        return EntityAppearanceSanitizer.sanitize(output.result);
    }

    private static ItemStack item(ItemStack source) {
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(source.getItem(), Math.clamp(source.getCount(), 1, 99));
        // Read a fixed component catalog. Never copy the original patch or encode its custom data.
        for (String name : EntityAppearanceSanitizer.ITEM_COMPONENTS) {
            var id = net.minecraft.resources.Identifier.parse(name);
            DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(id).orElse(null);
            if (type != null) copyComponent(source, result, type);
        }
        result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, source.hasFoil());
        return result;
    }
    private static <T> void copyComponent(ItemStack source, ItemStack result, DataComponentType<T> type) {
        T value = source.get(type);
        if (value != null) result.set(type, value);
        else result.remove(type);
    }

    private static void checkText(Component root) {
        record Node(Component component, int depth) {}
        Deque<Node> pending = new ArrayDeque<>();
        pending.push(new Node(root, 0));
        int nodes = 0;
        while (!pending.isEmpty()) {
            Node node = pending.pop();
            if (++nodes > 256 || node.depth >= 12)
                throw new IllegalArgumentException("Entity appearance text complexity exceeded");
            List<Component> siblings = node.component.getSiblings();
            if (siblings.size() > 256) throw new IllegalArgumentException("Entity appearance text too long");
            for (Component sibling : siblings) pending.push(new Node(sibling, node.depth + 1));
            if (node.component.getContents() instanceof TranslatableContents translated) {
                if (translated.getArgs().length > 256) throw new IllegalArgumentException("Entity appearance text too long");
                for (Object argument : translated.getArgs()) if (argument instanceof Component component)
                    pending.push(new Node(component, node.depth + 1));
            }
        }
    }

    static Component safeText(Component source) {
        checkText(source);
        return copyText(source);
    }
    private static Component copyText(Component source) {
        MutableComponent result;
        if (source.getContents() instanceof PlainTextContents literal) {
            if (literal.text().length() > EntityPreviewLimits.MAX_STRING_LENGTH)
                throw new IllegalArgumentException("Appearance text too long");
            result = Component.literal(literal.text());
        } else if (source.getContents() instanceof TranslatableContents translated) {
            Object[] arguments = translated.getArgs().clone();
            for (int i = 0; i < arguments.length; i++) {
                Object argument = arguments[i];
                if (argument instanceof Component component) arguments[i] = copyText(component);
                else if (argument instanceof String string && string.length() <= EntityPreviewLimits.MAX_STRING_LENGTH
                        || argument instanceof Number || argument instanceof Boolean) { /* Static argument. */ }
                else throw new IllegalArgumentException("Unsupported appearance text argument");
            }
            result = Component.translatableWithFallback(translated.getKey(), translated.getFallback(), arguments);
        } else throw new IllegalArgumentException("Unsupported dynamic appearance text");
        Style style = source.getStyle();
        result.setStyle(Style.EMPTY.withColor(style.getColor()).withBold(style.isBold()).withItalic(style.isItalic())
                .withUnderlined(style.isUnderlined()).withStrikethrough(style.isStrikethrough()).withObfuscated(style.isObfuscated()));
        for (Component sibling : source.getSiblings()) result.append(copyText(sibling));
        return result;
    }

    static final class AppearanceOutput implements ValueOutput {
        final CompoundTag result = new CompoundTag();
        private final DynamicOps<Tag> ops;
        AppearanceOutput(HolderLookup.Provider registries) {
            ops = registries.createSerializationContext(new BoundedOps());
        }
        @Override public <T> void store(String name, Codec<T> codec, T value) {
            if (!EntityAppearanceSanitizer.allows(name)) return;
            Tag encoded;
            if (value instanceof Component component) {
                encoded = ComponentSerialization.CODEC.encodeStart(ops, safeText(component)).getOrThrow();
            } else if (value instanceof ItemStack stack) {
                ItemStack safe = item(stack);
                if (safe.isEmpty()) return;
                encoded = ItemStack.CODEC.encodeStart(ops, safe).getOrThrow();
            } else if (value instanceof EntityEquipment equipment) {
                EntityEquipment safe = new EntityEquipment();
                for (EquipmentSlot slot : EquipmentSlot.values()) safe.set(slot, item(equipment.get(slot)));
                encoded = EntityEquipment.CODEC.encodeStart(ops, safe).getOrThrow();
            } else encoded = codec.encodeStart(ops, value).getOrThrow();
            put(name, encoded);
        }
        private void put(String name, Tag value) {
            if (EntityAppearanceSanitizer.allows(name)) {
                EntityPreviewNbt.validate(value);
                result.put(name, value);
                EntityPreviewNbt.validate(result);
            }
        }
        @Override public <T> void storeNullable(String name, Codec<T> codec, T value) { if (value != null) store(name, codec, value); }
        @Override public <T> void store(MapCodec<T> codec, T value) { /* Unnamed maps have no positive field schema. */ }
        @Override public void putBoolean(String name, boolean value) { put(name, ByteTag.valueOf(value)); }
        @Override public void putByte(String name, byte value) { put(name, ByteTag.valueOf(value)); }
        @Override public void putShort(String name, short value) { put(name, ShortTag.valueOf(value)); }
        @Override public void putInt(String name, int value) { put(name, IntTag.valueOf(value)); }
        @Override public void putLong(String name, long value) { put(name, LongTag.valueOf(value)); }
        @Override public void putFloat(String name, float value) { put(name, FloatTag.valueOf(value)); }
        @Override public void putDouble(String name, double value) { put(name, DoubleTag.valueOf(value)); }
        @Override public void putString(String name, String value) {
            if (EntityAppearanceSanitizer.allows(name)) {
                if (value.length() > EntityPreviewLimits.MAX_STRING_LENGTH) throw new IllegalArgumentException("Appearance string too long");
                put(name, StringTag.valueOf(value));
            }
        }
        @Override public void putIntArray(String name, int[] value) { /* No allowlisted array fields. */ }
        @Override public ValueOutput child(String name) { return DISCARD; }
        @Override public ValueOutputList childrenList(String name) { return DISCARD_LIST; }
        @Override public <T> TypedOutputList<T> list(String name, Codec<T> codec) { return discardTypedList(); }
        @Override public void discard(String name) { result.remove(name); }
        @Override public boolean isEmpty() { return result.isEmpty(); }
    }

    private static <T> ValueOutput.TypedOutputList<T> discardTypedList() {
        return new ValueOutput.TypedOutputList<>() {
            public void add(T value) {}
            public boolean isEmpty() { return true; }
        };
    }
    private static final ValueOutput.ValueOutputList DISCARD_LIST = new ValueOutput.ValueOutputList() {
        public ValueOutput addChild() { return DISCARD; }
        public void discardLast() {}
        public boolean isEmpty() { return true; }
    };
    private static final ValueOutput DISCARD = new ValueOutput() {
        public <T> void store(String name, Codec<T> codec, T value) {}
        public <T> void storeNullable(String name, Codec<T> codec, T value) {}
        public <T> void store(MapCodec<T> codec, T value) {}
        public void putBoolean(String n, boolean v) {}
        public void putByte(String n, byte v) {}
        public void putShort(String n, short v) {}
        public void putInt(String n, int v) {}
        public void putLong(String n, long v) {}
        public void putFloat(String n, float v) {}
        public void putDouble(String n, double v) {}
        public void putString(String n, String v) {}
        public void putIntArray(String n, int[] v) {}
        public ValueOutput child(String n) { return this; }
        public ValueOutputList childrenList(String n) { return DISCARD_LIST; }
        public <T> TypedOutputList<T> list(String n, Codec<T> c) { return discardTypedList(); }
        public void discard(String n) {}
        public boolean isEmpty() { return true; }
    };

    /** Budget is applied during codec creation, before a large NBT tree can be built. */
    private static final class BoundedOps extends DelegatingOps<Tag> {
        private int operations, bytes;
        BoundedOps() { super(NbtOps.INSTANCE); }
        private void charge(int amount) {
            bytes += amount;
            if (++operations > 8192 || bytes > EntityPreviewLimits.MAX_NBT_HEAP_BYTES)
                throw new IllegalArgumentException("Entity appearance encoding complexity exceeded");
        }
        @Override public Tag createString(String value) {
            if (value.length() > EntityPreviewLimits.MAX_STRING_LENGTH) throw new IllegalArgumentException("Appearance string too long");
            charge(32 + value.length() * 3);
            return super.createString(value);
        }
        @Override public Tag createNumeric(Number value) { charge(16); return super.createNumeric(value); }
        @Override public Tag createByte(byte v) { charge(16); return super.createByte(v); }
        @Override public Tag createShort(short v) { charge(16); return super.createShort(v); }
        @Override public Tag createInt(int v) { charge(16); return super.createInt(v); }
        @Override public Tag createLong(long v) { charge(16); return super.createLong(v); }
        @Override public Tag createFloat(float v) { charge(16); return super.createFloat(v); }
        @Override public Tag createDouble(double v) { charge(16); return super.createDouble(v); }
        @Override public Tag createBoolean(boolean v) { return createByte((byte)(v ? 1 : 0)); }
        private <T> List<T> bounded(Stream<T> stream) {
            List<T> result = stream.limit(EntityPreviewLimits.MAX_COLLECTION_LENGTH + 1).toList();
            if (result.size() > EntityPreviewLimits.MAX_COLLECTION_LENGTH) throw new IllegalArgumentException("Appearance collection too long");
            charge(32 + 8 * result.size());
            return result;
        }
        @Override public Tag createList(Stream<Tag> input) { return super.createList(bounded(input).stream()); }
        @Override public Tag createMap(Stream<Pair<Tag, Tag>> input) { return super.createMap(bounded(input).stream()); }
        @Override public Tag createMap(Map<Tag, Tag> input) { return createMap(input.entrySet().stream().map(e -> Pair.of(e.getKey(), e.getValue()))); }
        @Override public Tag createByteList(ByteBuffer input) {
            if (input.remaining() > 256) throw new IllegalArgumentException("Appearance array too long");
            charge(32 + input.remaining()); return super.createByteList(input);
        }
        @Override public Tag createIntList(IntStream input) { return super.createIntList(bounded(input.boxed()).stream().mapToInt(Integer::intValue)); }
        @Override public Tag createLongList(LongStream input) { return super.createLongList(bounded(input.boxed()).stream().mapToLong(Long::longValue)); }
        @Override public ListBuilder<Tag> listBuilder() { return new ListBuilder.Builder<>(this); }
        @Override public RecordBuilder<Tag> mapBuilder() { return new RecordBuilder.MapBuilder<>(this); }
    }
}
