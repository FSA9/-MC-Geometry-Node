package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Graph value persistence registry. Codec lookup and persistable port-type
 * capabilities are populated by the same registrations.
 */
public final class GraphValueCodecRegistry {

    private static final Map<Class<?>, GraphValueCodec<?>> CLASS_TO_SERIALIZER = new ConcurrentHashMap<>();
    private static final Map<String, GraphValueCodec<?>> ID_TO_SERIALIZER = new ConcurrentHashMap<>();
    private static final Map<PortType, PersistentTypeRegistration> PORT_TYPE_REGISTRATIONS =
            new EnumMap<>(PortType.class);

    // 包装盒的标准字段名
    private static final String TYPE_KEY = "_gn_type";
    private static final String DATA_KEY = "data";

    private GraphValueCodecRegistry() {
    }

    public static synchronized <T> void register(GraphValueCodec<T> serializer) {
        register(serializer, new PortType[0]);
    }

    public static synchronized <T> void register(GraphValueCodec<T> serializer, PortType... portTypes) {
        Objects.requireNonNull(serializer, "serializer");
        Class<T> targetClass = Objects.requireNonNull(serializer.getTargetClass(), "serializer target class");
        String typeId = Objects.requireNonNull(serializer.getTypeId(), "serializer type id");
        if (typeId.isBlank()) {
            throw new IllegalArgumentException("Graph value codec type id cannot be blank");
        }
        if ("_gn_list".equals(typeId) || "_gn_dict".equals(typeId)) {
            throw new IllegalArgumentException("Graph value codec type id is reserved: " + typeId);
        }
        if (CLASS_TO_SERIALIZER.containsKey(targetClass)) {
            throw new IllegalStateException("Graph value codec already registered for class: "
                    + targetClass.getName());
        }
        if (ID_TO_SERIALIZER.containsKey(typeId)) {
            throw new IllegalStateException("Graph value codec type id already registered: " + typeId);
        }
        validatePersistentTypes(portTypes);
        CLASS_TO_SERIALIZER.put(targetClass, serializer);
        ID_TO_SERIALIZER.put(typeId, serializer);
        registerPersistentTypes(targetClass, serializer, portTypes);
    }

    private static synchronized void registerNative(Class<?> valueClass, PortType... portTypes) {
        validatePersistentTypes(portTypes);
        registerPersistentTypes(valueClass, null, portTypes);
    }

    private static void validatePersistentTypes(PortType... portTypes) {
        if (portTypes == null) return;
        for (PortType portType : portTypes) {
            Objects.requireNonNull(portType, "persistent port type");
            if (PORT_TYPE_REGISTRATIONS.containsKey(portType)) {
                throw new IllegalStateException("Persistent graph value type already registered: " + portType);
            }
        }
    }

    private static void registerPersistentTypes(Class<?> valueClass, @Nullable GraphValueCodec<?> codec,
                                                PortType... portTypes) {
        Objects.requireNonNull(valueClass, "persistent value class");
        if (portTypes == null) return;
        for (PortType portType : portTypes) {
            PersistentTypeRegistration registration = new PersistentTypeRegistration(valueClass, codec);
            PORT_TYPE_REGISTRATIONS.put(portType, registration);
        }
    }

    static {
        registerNative(Number.class, PortType.INTEGER, PortType.LONG, PortType.FLOAT);
        registerNative(Boolean.class, PortType.BOOLEAN);
        registerNative(String.class, PortType.STRING, PortType.PATH);
        registerNative(List.class, PortType.LIST);
        registerNative(Map.class, PortType.DICT, PortType.SHOP);

        // UUID
        register(new GraphValueCodec<UUID>() {
            @Override public String getTypeId() { return "uuid"; }
            @Override public Class<UUID> getTargetClass() { return UUID.class; }
            @Override public Tag serialize(UUID value) { return StringTag.valueOf(value.toString()); }
            @Override public UUID deserialize(Tag tag) { return UUID.fromString(tag.asString().orElse("")); }
        }, PortType.ENTITY);

        // BlockPos
        register(new GraphValueCodec<BlockPos>() {
            @Override public String getTypeId() { return "block_pos"; }
            @Override public Class<BlockPos> getTargetClass() { return BlockPos.class; }
            @Override public Tag serialize(BlockPos value) { return LongTag.valueOf(value.asLong()); }
            @Override public BlockPos deserialize(Tag tag) { return BlockPos.of(((LongTag) tag).longValue()); }
        });

        // Vec3
        register(new GraphValueCodec<Vec3>() {
            @Override public String getTypeId() { return "vec3"; }
            @Override public Class<Vec3> getTargetClass() { return Vec3.class; }
            @Override public Tag serialize(Vec3 value) {
                ListTag list = new ListTag();
                list.add(DoubleTag.valueOf(value.x));
                list.add(DoubleTag.valueOf(value.y));
                list.add(DoubleTag.valueOf(value.z));
                return list;
            }
            @Override public Vec3 deserialize(Tag tag) {
                ListTag list = (ListTag) tag;
                return new Vec3(list.getDoubleOr(0, 0.0), list.getDoubleOr(1, 0.0), list.getDoubleOr(2, 0.0));
            }
        }, PortType.XYZ);

        // SlotRef
        register(new GraphValueCodec<SlotRef>() {
            @Override public String getTypeId() { return "slot_ref"; }
            @Override public Class<SlotRef> getTargetClass() { return SlotRef.class; }
            @Override public Tag serialize(SlotRef value) { return StringTag.valueOf(value.serialize()); }
            @Override public SlotRef deserialize(Tag tag) { return SlotRef.parse(tag.asString().orElse("")); }
        }, PortType.SLOT);

        // BlockState
        register(new GraphValueCodec<BlockState>() {
            @Override public String getTypeId() { return "block_state"; }
            @Override public Class<BlockState> getTargetClass() { return BlockState.class; }
            @Override public Tag serialize(BlockState value) { return NbtUtils.writeBlockState(value); }
            @Override public BlockState deserialize(Tag tag) {
                return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, (CompoundTag) tag);
            }
        }, PortType.BLOCK_STATE);

        // ItemStack
        register(new GraphValueCodec<ItemStack>() {
            @Override public String getTypeId() { return "item_stack"; }
            @Override public Class<ItemStack> getTargetClass() { return ItemStack.class; }
            @Override public Tag serialize(ItemStack value, HolderLookup.Provider provider) {
                return ItemStack.OPTIONAL_CODEC
                        .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value)
                        .getOrThrow(IllegalArgumentException::new);
            }
            @Override public ItemStack deserialize(Tag tag, HolderLookup.Provider provider) {
                return ItemStack.OPTIONAL_CODEC
                        .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                        .getOrThrow(IllegalArgumentException::new);
            }
        }, PortType.ITEM_STACK);

        register(new GraphValueCodec<EntityTemplateValue>() {
            @Override public String getTypeId() { return "entity_template"; }
            @Override public Class<EntityTemplateValue> getTargetClass() { return EntityTemplateValue.class; }
            @Override public Tag serialize(EntityTemplateValue value) {
                CompoundTag tag = new CompoundTag();
                tag.putString("entity_type", value.entityTypeId());
                tag.put("entity_data", value.data());
                return tag;
            }
            @Override public EntityTemplateValue deserialize(Tag tag) {
                CompoundTag compound = requireCompound(tag, "entity_template");
                if (!compound.contains("entity_type")
                        || !(compound.get("entity_data") instanceof CompoundTag entityData)) {
                    throw new IllegalArgumentException("Invalid entity_template graph value");
                }
                return new EntityTemplateValue(
                        compound.getStringOr("entity_type", ""),
                        entityData
                );
            }
        }, PortType.ENTITY_TEMPLATE);

        register(new GraphValueCodec<Item>() {
            @Override public String getTypeId() { return "item"; }
            @Override public Class<Item> getTargetClass() { return Item.class; }
            @Override public Tag serialize(Item value) {
                return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(value).toString());
            }
            @Override public Item deserialize(Tag tag) {
                String id = requireString(tag, "item");
                return BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown item: " + id));
            }
        }, PortType.ITEM);

        register(new GraphValueCodec<ColorValue>() {
            @Override public String getTypeId() { return "color"; }
            @Override public Class<ColorValue> getTargetClass() { return ColorValue.class; }
            @Override public Tag serialize(ColorValue value) {
                ListTag channels = new ListTag();
                channels.add(FloatTag.valueOf(value.r()));
                channels.add(FloatTag.valueOf(value.g()));
                channels.add(FloatTag.valueOf(value.b()));
                channels.add(FloatTag.valueOf(value.a()));
                return channels;
            }
            @Override public ColorValue deserialize(Tag tag) {
                ListTag channels = requireList(tag, "color");
                if (channels.size() != 4) {
                    throw new IllegalArgumentException("Color value must contain four channels");
                }
                return new ColorValue(
                        channels.getFloatOr(0, 0.0F),
                        channels.getFloatOr(1, 0.0F),
                        channels.getFloatOr(2, 0.0F),
                        channels.getFloatOr(3, 1.0F));
            }
        }, PortType.COLOR);

        register(new GraphValueCodec<RichTextValue>() {
            @Override public String getTypeId() { return "rich_text"; }
            @Override public Class<RichTextValue> getTargetClass() { return RichTextValue.class; }
            @Override public Tag serialize(RichTextValue value, HolderLookup.Provider provider) {
                CompoundTag result = new CompoundTag();
                result.putString("type", value.type());
                result.putInt("version", value.version());
                result.putString("plain", value.plain());
                ListTag segments = new ListTag();
                for (RichTextValue.Segment segment : value.segments()) {
                    CompoundTag encoded = new CompoundTag();
                    encoded.putString("kind", segment.kind());
                    encoded.putString("text", segment.text());
                    encoded.putString("source", segment.source());
                    encoded.putString("display", segment.display());
                    encoded.put("style", toTagStrict(segment.style(), provider));
                    segments.add(encoded);
                }
                result.put("segments", segments);
                return result;
            }
            @Override public RichTextValue deserialize(Tag tag, HolderLookup.Provider provider) {
                CompoundTag encoded = requireCompound(tag, "rich_text");
                List<RichTextValue.Segment> segments = new ArrayList<>();
                for (Tag rawSegment : encoded.getListOrEmpty("segments")) {
                    CompoundTag segment = requireCompound(rawSegment, "rich_text segment");
                    Object rawStyle = fromTag(segment.get("style"), provider);
                    Map<String, Object> style = stringKeyMap(rawStyle, "rich_text segment style");
                    segments.add(new RichTextValue.Segment(
                            segment.getStringOr("kind", RichTextValue.KIND_TEXT),
                            segment.getStringOr("text", ""),
                            segment.getStringOr("source", ""),
                            segment.getStringOr("display", "inline"),
                            style));
                }
                return new RichTextValue(
                        encoded.getStringOr("type", RichTextValue.TYPE),
                        encoded.getIntOr("version", RichTextValue.VERSION),
                        encoded.getStringOr("plain", ""),
                        segments);
            }
        }, PortType.RICH_TEXT);

        register(new GraphValueCodec<GeometryValue>() {
            @Override public String getTypeId() { return "geometry"; }
            @Override public Class<GeometryValue> getTargetClass() { return GeometryValue.class; }
            @Override public Tag serialize(GeometryValue value) {
                ListTag primitives = new ListTag();
                for (GeometryValue.Primitive primitive : value.primitives()) {
                    CompoundTag encoded = new CompoundTag();
                    encoded.putString("type", primitive.type().id());
                    putVector(encoded, "center", primitive.center());
                    putVector(encoded, "size", primitive.size());
                    encoded.putInt("vertices_x", primitive.verticesX());
                    encoded.putInt("vertices_y", primitive.verticesY());
                    encoded.putInt("vertices_z", primitive.verticesZ());
                    encoded.putInt("radial_vertices", primitive.radialVertices());
                    encoded.putInt("side_segments", primitive.sideSegments());
                    encoded.putInt("fill_segments", primitive.fillSegments());
                    encoded.putString("fill_type", primitive.fillType().id());
                    primitives.add(encoded);
                }
                return primitives;
            }
            @Override public GeometryValue deserialize(Tag tag) {
                ListTag encoded = requireList(tag, "geometry");
                List<GeometryValue.Primitive> primitives = new ArrayList<>(encoded.size());
                for (Tag rawPrimitive : encoded) {
                    CompoundTag primitive = requireCompound(rawPrimitive, "geometry primitive");
                    GeometryValue.PrimitiveType type = GeometryValue.PrimitiveType.fromId(
                            primitive.getStringOr("type", ""));
                    Vec3 center = readVector(primitive, "center");
                    Vec3 size = readVector(primitive, "size");
                    primitives.add(switch (type) {
                        case CUBE -> GeometryValue.Primitive.cube(
                                center, size,
                                primitive.getIntOr("vertices_x", 1),
                                primitive.getIntOr("vertices_y", 1),
                                primitive.getIntOr("vertices_z", 1));
                        case CYLINDER -> GeometryValue.Primitive.cylinder(
                                center,
                                primitive.getIntOr("radial_vertices", 3),
                                primitive.getIntOr("side_segments", 1),
                                primitive.getIntOr("fill_segments", 1),
                                (float) (size.x * 0.5D),
                                (float) size.y,
                                GeometryValue.CylinderFillType.fromId(
                                        primitive.getStringOr("fill_type", "")));
                        case UV_SPHERE -> GeometryValue.Primitive.uvSphere(
                                center,
                                primitive.getIntOr("radial_vertices", 3),
                                primitive.getIntOr("side_segments", 1),
                                (float) (size.x * 0.5D));
                    });
                }
                return GeometryValue.of(primitives.toArray(GeometryValue.Primitive[]::new));
            }
        }, PortType.GEOMETRY);
    }

    public static Set<PortType> supportedPortTypes() {
        return Set.copyOf(PORT_TYPE_REGISTRATIONS.keySet());
    }

    public static boolean supportsPortType(@Nullable PortType type) {
        return type != null && PORT_TYPE_REGISTRATIONS.containsKey(type);
    }

    @Nullable
    public static Tag toTag(Object value, HolderLookup.Provider provider) {
        if (value == null) return null;

        if (value instanceof Entity entity) {
            value = entity.getUUID();
        }

        // --- 快车道 ---
        if (value instanceof Integer i) return IntTag.valueOf(i);
        if (value instanceof Long l) return LongTag.valueOf(l);
        if (value instanceof Short s) return ShortTag.valueOf(s);
        if (value instanceof Double d) return DoubleTag.valueOf(d); // 补充 Double
        if (value instanceof Float f) return FloatTag.valueOf(f);
        if (value instanceof String s) return StringTag.valueOf(s);
        if (value instanceof Boolean b) return ByteTag.valueOf(b);
        if (value instanceof Number number) {
            return toTag(GraphNumberNormalizer.normalize(number), provider);
        }

        // Item implementations may use concrete subclasses. This is an explicit adapter to the
        // ITEM contract, not an order-dependent polymorphic codec lookup.
        if (value instanceof Item item) {
            @SuppressWarnings("unchecked")
            GraphValueCodec<Item> itemCodec = (GraphValueCodec<Item>) CLASS_TO_SERIALIZER.get(Item.class);
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, itemCodec.getTypeId());
            wrapper.put(DATA_KEY, itemCodec.serialize(item, provider));
            return wrapper;
        }

        // List
        if (value instanceof List<?> list) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_list"); // 专用 ID
            ListTag nbtList = new ListTag();

            for (Object item : list) {
                Tag elementTag = toTag(item, provider);
                if (elementTag != null) {
                    // 强制包一层 Compound，打破 ListTag 必须同类型的诅咒
                    CompoundTag elementWrapper = new CompoundTag();
                    elementWrapper.put("v", elementTag);
                    nbtList.add(elementWrapper);
                }
            }
            wrapper.put(DATA_KEY, nbtList);
            return wrapper;
        }

        // Dict
        if (value instanceof Map<?, ?> map) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_dict"); // 专用 ID
            CompoundTag dataTag = new CompoundTag();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    Tag elementTag = toTag(entry.getValue(), provider); // 递归序列化 Value
                    if (elementTag != null) {
                        dataTag.put(key, elementTag);
                    }
                }
            }
            wrapper.put(DATA_KEY, dataTag);
            return wrapper;
        }

        // Registered graph values use exact runtime classes. Polymorphic codecs must
        // provide an explicit adapter instead of depending on map iteration order.
        GraphValueCodec<Object> serializer = (GraphValueCodec<Object>) CLASS_TO_SERIALIZER.get(value.getClass());
        if (serializer != null) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, serializer.getTypeId());
            wrapper.put(DATA_KEY, serializer.serialize(value, provider));
            return wrapper;
        }

        System.err.println("[GeometryNode] Unsupported variable type for saving: " + value.getClass().getName());
        return null;
    }

    /**
     * Encodes a complete value or fails. Unlike the permissive legacy entry point,
     * this method never drops unsupported list elements, map keys, or map values.
     */
    public static Tag toTagStrict(Object value, HolderLookup.Provider provider) {
        if (value == null) {
            throw new IllegalArgumentException("Graph values cannot contain null");
        }
        if (value instanceof List<?> list) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_list");
            ListTag encoded = new ListTag();
            for (Object item : list) {
                CompoundTag element = new CompoundTag();
                element.put("v", toTagStrict(item, provider));
                encoded.add(element);
            }
            wrapper.put(DATA_KEY, encoded);
            return wrapper;
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_dict");
            CompoundTag encoded = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Graph map keys must be strings");
                }
                encoded.put(key, toTagStrict(entry.getValue(), provider));
            }
            wrapper.put(DATA_KEY, encoded);
            return wrapper;
        }
        Tag encoded = toTag(value, provider);
        if (encoded == null) {
            throw new IllegalArgumentException("Unsupported graph value type: "
                    + value.getClass().getName());
        }
        return encoded;
    }

    @Nullable
    public static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        if (tag == null) return null;

        // --- 快车道 ---
        if (tag instanceof IntTag i) return i.intValue();
        if (tag instanceof LongTag l) return l.longValue();
        if (tag instanceof ShortTag s) return s.intValue();
        if (tag instanceof DoubleTag d) return d.doubleValue();
        if (tag instanceof FloatTag f) return f.floatValue();
        if (tag instanceof StringTag s) return s.value();
        if (tag instanceof ByteTag b) return b.byteValue() != 0;

        // --- 拆包与慢车道 ---
        if (tag instanceof CompoundTag compound && compound.contains(TYPE_KEY)) {
            String typeId = compound.getStringOr(TYPE_KEY, "");

            // List
            if ("_gn_list".equals(typeId) && compound.contains(DATA_KEY)) {
                ListTag nbtList = compound.getListOrEmpty(DATA_KEY);
                List<Object> resultList = new ArrayList<>();
                for (int i = 0; i < nbtList.size(); i++) {
                    CompoundTag elementWrapper = nbtList.getCompoundOrEmpty(i);
                    resultList.add(fromTag(elementWrapper.get("v"), provider));
                }
                return resultList;
            }
            // Dict
            if ("_gn_dict".equals(typeId) && compound.contains(DATA_KEY)) {
                CompoundTag dataTag = compound.getCompoundOrEmpty(DATA_KEY);
                Map<String, Object> resultMap = new HashMap<>();
                for (String key : dataTag.keySet()) {
                    resultMap.put(key, fromTag(dataTag.get(key), provider)); // 递归反序列化 Value
                }
                return resultMap;
            }

            GraphValueCodec<?> serializer = ID_TO_SERIALIZER.get(typeId);
            if (serializer != null && compound.contains(DATA_KEY)) {
                return serializer.deserialize(compound.get(DATA_KEY), provider);
            } else {
                System.err.println("[GeometryNode] Missing serializer for type: " + typeId);
            }
        }

        return null;
    }

    public static boolean isSupported(Object value) {
        if (value == null) return false;
        if (value instanceof Entity) return supportsPortType(PortType.ENTITY);

        if (value instanceof List<?> list) {
            return supportsPortType(PortType.LIST)
                    && list.stream().allMatch(GraphValueCodecRegistry::isSupported);
        }
        if (value instanceof Map<?, ?> map) {
            return supportsPortType(PortType.DICT) && map.entrySet().stream().allMatch(entry ->
                    entry.getKey() instanceof String && isSupported(entry.getValue()));
        }

        if (CLASS_TO_SERIALIZER.containsKey(value.getClass())) return true;
        return PORT_TYPE_REGISTRATIONS.values().stream()
                .anyMatch(registration -> registration.valueClass().isInstance(value));
    }

    private static String requireString(Tag tag, String valueType) {
        return tag.asString().orElseThrow(() ->
                new IllegalArgumentException("Invalid " + valueType + " graph value"));
    }

    private static CompoundTag requireCompound(Tag tag, String valueType) {
        if (tag instanceof CompoundTag compound) return compound;
        throw new IllegalArgumentException("Invalid " + valueType + " graph value");
    }

    private static ListTag requireList(Tag tag, String valueType) {
        if (tag instanceof ListTag list) return list;
        throw new IllegalArgumentException("Invalid " + valueType + " graph value");
    }

    private static Map<String, Object> stringKeyMap(Object value, String valueType) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("Invalid " + valueType);
        }
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Invalid " + valueType + " key");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private record PersistentTypeRegistration(Class<?> valueClass, @Nullable GraphValueCodec<?> codec) {}

    private static void putVector(CompoundTag target, String key, Vec3 value) {
        ListTag vector = new ListTag();
        vector.add(DoubleTag.valueOf(value.x));
        vector.add(DoubleTag.valueOf(value.y));
        vector.add(DoubleTag.valueOf(value.z));
        target.put(key, vector);
    }

    private static Vec3 readVector(CompoundTag source, String key) {
        ListTag vector = source.getListOrEmpty(key);
        if (vector.size() != 3) {
            throw new IllegalArgumentException("Geometry vector must contain exactly three numbers: " + key);
        }
        return new Vec3(
                vector.getDoubleOr(0, 0.0D),
                vector.getDoubleOr(1, 0.0D),
                vector.getDoubleOr(2, 0.0D));
    }
}
