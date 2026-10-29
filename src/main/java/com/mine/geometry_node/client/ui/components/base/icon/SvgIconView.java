package com.mine.geometry_node.client.ui.components.base.icon;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.Bitmap;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.Paint;
import icyllis.modernui.view.View;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.time.Duration;
import com.mine.geometry_node.core.cache.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvgIconView extends View {
    public enum Icon {
        SCENE_EYE("scene/eye.svg"),
        SCENE_CURSOR("scene/cursor.svg"),
        SNAP("scene/snap.svg"),
        GRID("scene/grid.svg"),
        GRAPH_EDITOR("editors/graph_editor.svg"),
        GAME("editors/game.svg"),
        PROPERTIES("panels/sliders.svg"),
        TRANSFERS("panels/transfer.svg"),
        ASSET_LIBRARY("editors/asset_library.svg"),
        DATA_LIBRARY("editors/data_library.svg"),
        TERMINAL("editors/console.svg"),
        PERFORMANCE("editors/analysis.svg"),
        FOLDER("navigation/folder.svg"),
        CLOUD("navigation/cloud.svg"),
        STAR("navigation/star.svg"),
        FILE("assets/file.svg"),
        PACK("assets/pack.svg"),
        SCHEMATIC("assets/schematic.svg"),
        MODEL("assets/model.svg"),
        IMAGE("assets/image.svg"),
        ARROW_UP("navigation/arrow-up.svg"),
        ARROW_DOWN("navigation/arrow-down.svg"),
        CHEVRON_UP("navigation/chevron-up.svg"),
        CHEVRON_DOWN("navigation/chevron-down.svg"),
        CHEVRON_LEFT("navigation/chevron-left.svg"),
        CHEVRON_RIGHT("navigation/chevron-right.svg"),
        GRID_VIEW("navigation/grid-view.svg"),
        LIST_VIEW("navigation/list-view.svg"),
        SPLIT_HORIZONTAL("panels/split-horizontal.svg"),
        SPLIT_VERTICAL("panels/split-vertical.svg"),
        WINDOW("panels/window.svg"),
        COPY("actions/copy.svg"),
        SEARCH("actions/search.svg"),
        PLUS("actions/plus.svg"),
        MORE_HORIZONTAL("actions/more_h.svg"),
        MORE_VERTICAL("actions/more_v.svg"),
        UPLOAD("actions/upload.svg"),
        DOWNLOAD("actions/download.svg"),
        CHECK("actions/check.svg"),
        DRAG_HANDLE("actions/drag-handle.svg"),
        CLEAR("actions/clear.svg"),
        TRASH("actions/clear.svg"),
        RESET("actions/refresh.svg"),
        SHUFFLE("actions/shuffle.svg"),
        CLOSE("actions/close.svg"),
        EXPAND("navigation/chevrons-up-down-64px.svg"),
        COLLAPSE("navigation/chevrons-down-up-64px.svg"),
        SIDEBAR_COLLAPSE("navigation/sidebar-collapse.svg"),
        SQUARE_CHECK("actions/square-check-64px.svg"),
        SQUARE_PLUS("actions/square-plus-64px.svg"),
        SQUARE_MINUS("actions/square-minus-64px.svg"),
        DATABASE("database/library.svg"),
        BLUEPRINT("editors/graph_editor.svg", "blueprint"),
        QUEST("graph_types/quest.svg", "quest"),
        BEHAVIOR_TREE("graph_types/behavior_tree.svg", "behavior_tree");

        private final String mFileName;
        private final String mGraphTypeId;

        Icon(String fileName) {
            this(fileName, null);
        }

        Icon(String fileName, String graphTypeId) {
            mFileName = fileName;
            mGraphTypeId = graphTypeId;
        }

        public String resourcePath() {
            return "/assets/geometry_node/icons/ui/" + mFileName;
        }

        public static Icon forGraphType(String graphTypeId) {
            if (graphTypeId == null || graphTypeId.isBlank()) return null;
            for (Icon icon : values()) {
                if (graphTypeId.equals(icon.mGraphTypeId)) return icon;
            }
            return null;
        }

        public static Icon forExpandedState(boolean expanded) {
            return expanded ? COLLAPSE : EXPAND;
        }
    }

    private static final int DEFAULT_COLOR = UiThemes.EDITOR.text().secondary();
    private static final int MAX_CACHED_IMAGES = 64;
    private static final BoundedCache<String, SvgDocument> DOCUMENTS =
            new BoundedCache<>(128, Long.MAX_VALUE, Duration.ZERO, ignored -> 1, ignored -> {});
    private static final BoundedCache<RenderKey, Image> IMAGES =
            new BoundedCache<>(MAX_CACHED_IMAGES, Long.MAX_VALUE, Duration.ZERO, ignored -> 1, ignored -> {});

    static {
        registerCache("svg_documents", CacheDescriptor.Storage.MEMORY, DOCUMENTS);
        registerCache("svg_images", CacheDescriptor.Storage.GPU, IMAGES);
    }

    private static void registerCache(String name, CacheDescriptor.Storage storage, BoundedCache<?, ?> cache) {
        CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:" + name,
                "geometry_node.cache." + name, CacheDescriptor.Domain.CLIENT, storage,
                CacheDescriptor.Lifetime.APPLICATION), new ManagedCache() {
            public java.util.concurrent.CompletionStage<CacheStats> stats() {
                return CacheExecutors.submit(() -> CacheStats.memory(cache.size(), -1));
            }
            public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                // Drop cache ownership only. MUI Image owns a native Cleaner, while Arc3D's
                // BigRecord.DrawImageRect retains its own ref-counted native image. Calling
                // Image.close() here would invalidate a wrapper concurrently used by onDraw.
                return CacheExecutors.run(cache::clear);
            }
            public boolean supports(CacheClearRequest request) {
                return request.namespace() == null && (request.reason() == CacheClearRequest.Reason.USER
                        || request.reason() == CacheClearRequest.Reason.RESOURCE_RELOAD);
            }
        });
    }

    private final Paint mPaint = new Paint();
    private String mResourcePath;
    private int mColor;

    public SvgIconView(Context context, Icon icon, int color) {
        this(context, icon.resourcePath(), color);
    }

    /** Classpath SVG resource, including addon namespaces, without a built-in enum entry. */
    public SvgIconView(Context context, String resourcePath, int color) {
        super(context);
        mResourcePath = java.util.Objects.requireNonNull(resourcePath);
        mColor = resolveColor(color);
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    public void setIcon(Icon icon) {
        if (mResourcePath.equals(icon.resourcePath())) {
            return;
        }
        mResourcePath = icon.resourcePath();
        invalidate();
    }

    /** The resolved tint currently used by this renderer. */
    public int getIconColor() {
        return mColor;
    }

    public void setIconColor(int color) {
        int resolved = resolveColor(color);
        if (mColor == resolved) {
            return;
        }
        mColor = resolved;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        if (size <= 0) {
            return;
        }

        float left = (getWidth() - size) * 0.5f;
        float top = (getHeight() - size) * 0.5f;
        drawIcon(canvas, left, top, size);
    }

    /** Draws this resource inside a custom control without defining a second icon renderer. */
    public void drawIcon(Canvas canvas, float left, float top, int size) {
        if (size <= 0) return;
        Image image = getImage(mResourcePath, size, mColor);
        if (image == null) return;
        try {
            canvas.drawImage(image, left, top, mPaint);
        } finally {
            // Keep the wrapper/cleaner alive until the canvas has acquired its native reference.
            Reference.reachabilityFence(image);
        }
    }

    private static int resolveColor(int color) {
        return color == 0 ? DEFAULT_COLOR : color;
    }

    private static synchronized Image getImage(String resourcePath, int size, int color) {
        RenderKey key = new RenderKey(resourcePath, size, color);
        long imageGeneration = IMAGES.generation();
        Image cached = IMAGES.get(key);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }

        long documentGeneration = DOCUMENTS.generation();
        SvgDocument document = DOCUMENTS.get(resourcePath);
        if (document == null) {
            document = loadDocument(resourcePath);
            DOCUMENTS.putIfCurrent(resourcePath, document, documentGeneration);
        }
        Image image = rasterize(document, size, color);
        // MUI may return null during device loss/shutdown. Do not cache failure; retry next draw.
        if (image != null) IMAGES.putIfCurrent(key, image, imageGeneration);
        return image;
    }

    private static SvgDocument loadDocument(String resourcePath) {
        try (InputStream input = SvgIconView.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing SVG icon resource: " + resourcePath);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            float[] viewBox = numbers(root.getAttribute("viewBox"), 4);
            if (viewBox[2] <= 0 || viewBox[3] <= 0) {
                throw new IllegalArgumentException("SVG viewBox must have positive dimensions: " + resourcePath);
            }
            List<SvgLayer> layers = new ArrayList<>();
            collectLayers(root, SvgStyle.DEFAULT, layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("SVG icon has no drawable paths: " + resourcePath);
            }
            return new SvgDocument(viewBox[0], viewBox[1], viewBox[2], viewBox[3], List.copyOf(layers));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load SVG icon " + resourcePath, exception);
        }
    }

    private static void collectLayers(Element element, SvgStyle inherited, List<SvgLayer> layers) {
        SvgStyle style = inherited.merge(element);
        Shape shape = switch (element.getTagName()) {
            case "path" -> parsePath(element.getAttribute("d"), style.evenOdd());
            case "rect" -> parseRect(element);
            case "circle" -> {
                float radius = number(element.getAttribute("r"), 0.0f);
                float cx = number(element.getAttribute("cx"), 0.0f);
                float cy = number(element.getAttribute("cy"), 0.0f);
                yield new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2);
            }
            case "ellipse" -> {
                float rx = number(element.getAttribute("rx"), 0.0f);
                float ry = number(element.getAttribute("ry"), 0.0f);
                float cx = number(element.getAttribute("cx"), 0.0f);
                float cy = number(element.getAttribute("cy"), 0.0f);
                yield new Ellipse2D.Float(cx - rx, cy - ry, rx * 2, ry * 2);
            }
            default -> null;
        };

        if (shape != null && (style.hasFill() || style.hasStroke())) {
            layers.add(new SvgLayer(shape, style.hasFill(), style.hasStroke(), style.strokeWidth(),
                    style.strokeCap(), style.strokeJoin()));
        }

        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element childElement) {
                collectLayers(childElement, style, layers);
            }
        }
    }

    private static Shape parseRect(Element element) {
        float x = number(element.getAttribute("x"), 0.0f);
        float y = number(element.getAttribute("y"), 0.0f);
        float width = number(element.getAttribute("width"), 0.0f);
        float height = number(element.getAttribute("height"), 0.0f);
        float rx = number(element.getAttribute("rx"), 0.0f);
        float ry = number(element.getAttribute("ry"), rx);
        if (rx > 0.0f || ry > 0.0f) {
            return new RoundRectangle2D.Float(x, y, width, height, rx * 2.0f, ry * 2.0f);
        }
        return new Rectangle2D.Float(x, y, width, height);
    }

    private static Image rasterize(SvgDocument document, int size, int color) {
        BufferedImage bufferedImage = rasterizeDocument(document, size, color);
        int[] pixels = bufferedImage.getRGB(0, 0, size, size, null, 0, size);
        try (Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Format.RGBA_8888)) {
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            bitmap.setImmutable();
            return Image.createTextureFromBitmap(bitmap);
        }
    }

    private static BufferedImage rasterizeDocument(SvgDocument document, int size, int color) {
        BufferedImage bufferedImage = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = bufferedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            float scale = Math.min(size / document.width(), size / document.height());
            float offsetX = (size - document.width() * scale) * 0.5f - document.minX() * scale;
            float offsetY = (size - document.height() * scale) * 0.5f - document.minY() * scale;
            graphics.translate(offsetX, offsetY);
            graphics.scale(scale, scale);
            graphics.setColor(new Color(color, true));

            for (SvgLayer layer : document.layers()) {
                if (layer.fill()) {
                    graphics.fill(layer.shape());
                }
                if (layer.stroke()) {
                    graphics.setStroke(new BasicStroke(layer.strokeWidth(), layer.strokeCap(), layer.strokeJoin()));
                    graphics.draw(layer.shape());
                }
            }
        } finally {
            graphics.dispose();
        }

        return bufferedImage;
    }

    private static Path2D.Float parsePath(String data, boolean evenOdd) {
        return new SvgPathParser(data, evenOdd).parse();
    }

    private static float number(String value, float defaultValue) {
        return value == null || value.isBlank() ? defaultValue : Float.parseFloat(value);
    }

    private static float[] numbers(String value, int expectedCount) {
        Matcher matcher = SvgPathParser.NUMBER_PATTERN.matcher(value);
        float[] result = new float[expectedCount];
        int count = 0;
        while (matcher.find() && count < expectedCount) {
            result[count++] = Float.parseFloat(matcher.group());
        }
        if (count != expectedCount) {
            throw new IllegalArgumentException("Expected " + expectedCount + " numbers in: " + value);
        }
        return result;
    }

    private record RenderKey(String resourcePath, int size, int color) {
    }

    private record SvgDocument(float minX, float minY, float width, float height, List<SvgLayer> layers) {
    }

    private record SvgLayer(Shape shape, boolean fill, boolean stroke, float strokeWidth,
                            int strokeCap, int strokeJoin) {
    }

    private record SvgStyle(String fill, String stroke, float strokeWidth, String lineCap,
                            String lineJoin, String fillRule) {
        private static final SvgStyle DEFAULT = new SvgStyle("black", "none", 1.0f, "butt", "miter", "nonzero");

        SvgStyle merge(Element element) {
            return new SvgStyle(
                    attribute(element, "fill", fill),
                    attribute(element, "stroke", stroke),
                    number(attribute(element, "stroke-width", null), strokeWidth),
                    attribute(element, "stroke-linecap", lineCap),
                    attribute(element, "stroke-linejoin", lineJoin),
                    attribute(element, "fill-rule", fillRule));
        }

        boolean hasFill() {
            return !"none".equalsIgnoreCase(fill);
        }

        boolean hasStroke() {
            return !"none".equalsIgnoreCase(stroke);
        }

        boolean evenOdd() {
            return "evenodd".equalsIgnoreCase(fillRule);
        }

        int strokeCap() {
            return switch (lineCap.toLowerCase()) {
                case "round" -> BasicStroke.CAP_ROUND;
                case "square" -> BasicStroke.CAP_SQUARE;
                default -> BasicStroke.CAP_BUTT;
            };
        }

        int strokeJoin() {
            return switch (lineJoin.toLowerCase()) {
                case "round" -> BasicStroke.JOIN_ROUND;
                case "bevel" -> BasicStroke.JOIN_BEVEL;
                default -> BasicStroke.JOIN_MITER;
            };
        }

        private static String attribute(Element element, String name, String inherited) {
            String value = element.getAttribute(name);
            return value.isBlank() ? inherited : value;
        }
    }

    private static final class SvgPathParser {
        private static final String NUMBER_REGEX = "[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?";
        private static final Pattern NUMBER_PATTERN = Pattern.compile(NUMBER_REGEX);
        private static final Pattern TOKEN_PATTERN = Pattern.compile("[CcHhLlMmQqSsTtVvZz]|" + NUMBER_REGEX);

        private final List<String> mTokens = new ArrayList<>();
        private final Path2D.Float mPath;
        private int mIndex;
        private char mCommand;
        private char mPreviousCommand;
        private float mX;
        private float mY;
        private float mStartX;
        private float mStartY;
        private float mControlX;
        private float mControlY;

        SvgPathParser(String data, boolean evenOdd) {
            Matcher matcher = TOKEN_PATTERN.matcher(data);
            int end = 0;
            while (matcher.find()) {
                requireSeparator(data.substring(end, matcher.start()));
                mTokens.add(matcher.group());
                end = matcher.end();
            }
            requireSeparator(data.substring(end));
            mPath = new Path2D.Float(evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
        }

        private static void requireSeparator(String text) {
            if (!text.matches("[\\s,]*")) {
                throw new IllegalArgumentException("Unsupported SVG path syntax: " + text);
            }
        }

        Path2D.Float parse() {
            while (mIndex < mTokens.size()) {
                String token = mTokens.get(mIndex);
                if (isCommand(token)) {
                    mCommand = token.charAt(0);
                    mIndex++;
                } else if (mCommand == 0) {
                    throw new IllegalArgumentException("SVG path data starts without a command");
                }
                executeCommand();
            }
            return mPath;
        }

        private void executeCommand() {
            boolean relative = Character.isLowerCase(mCommand);
            char normalized = Character.toUpperCase(mCommand);
            switch (normalized) {
                case 'M' -> {
                    float x = coordinateX(nextNumber(), relative);
                    float y = coordinateY(nextNumber(), relative);
                    mPath.moveTo(x, y);
                    mX = mStartX = x;
                    mY = mStartY = y;
                    resetControl();
                    mPreviousCommand = 'M';
                    mCommand = relative ? 'l' : 'L';
                }
                case 'L' -> {
                    lineTo(coordinateX(nextNumber(), relative), coordinateY(nextNumber(), relative));
                    mPreviousCommand = 'L';
                }
                case 'H' -> {
                    lineTo(coordinateX(nextNumber(), relative), mY);
                    mPreviousCommand = 'H';
                }
                case 'V' -> {
                    lineTo(mX, coordinateY(nextNumber(), relative));
                    mPreviousCommand = 'V';
                }
                case 'C' -> {
                    float x1 = coordinateX(nextNumber(), relative);
                    float y1 = coordinateY(nextNumber(), relative);
                    float x2 = coordinateX(nextNumber(), relative);
                    float y2 = coordinateY(nextNumber(), relative);
                    float x = coordinateX(nextNumber(), relative);
                    float y = coordinateY(nextNumber(), relative);
                    mPath.curveTo(x1, y1, x2, y2, x, y);
                    mControlX = x2;
                    mControlY = y2;
                    mX = x;
                    mY = y;
                    mPreviousCommand = 'C';
                }
                case 'S' -> {
                    float x1 = mPreviousCommand == 'C' || mPreviousCommand == 'S' ? mX * 2.0f - mControlX : mX;
                    float y1 = mPreviousCommand == 'C' || mPreviousCommand == 'S' ? mY * 2.0f - mControlY : mY;
                    float x2 = coordinateX(nextNumber(), relative);
                    float y2 = coordinateY(nextNumber(), relative);
                    float x = coordinateX(nextNumber(), relative);
                    float y = coordinateY(nextNumber(), relative);
                    mPath.curveTo(x1, y1, x2, y2, x, y);
                    mControlX = x2;
                    mControlY = y2;
                    mX = x;
                    mY = y;
                    mPreviousCommand = 'S';
                }
                case 'Q' -> {
                    float x1 = coordinateX(nextNumber(), relative);
                    float y1 = coordinateY(nextNumber(), relative);
                    float x = coordinateX(nextNumber(), relative);
                    float y = coordinateY(nextNumber(), relative);
                    mPath.quadTo(x1, y1, x, y);
                    mControlX = x1;
                    mControlY = y1;
                    mX = x;
                    mY = y;
                    mPreviousCommand = 'Q';
                }
                case 'T' -> {
                    float x1 = mPreviousCommand == 'Q' || mPreviousCommand == 'T' ? mX * 2.0f - mControlX : mX;
                    float y1 = mPreviousCommand == 'Q' || mPreviousCommand == 'T' ? mY * 2.0f - mControlY : mY;
                    float x = coordinateX(nextNumber(), relative);
                    float y = coordinateY(nextNumber(), relative);
                    mPath.quadTo(x1, y1, x, y);
                    mControlX = x1;
                    mControlY = y1;
                    mX = x;
                    mY = y;
                    mPreviousCommand = 'T';
                }
                case 'Z' -> {
                    mPath.closePath();
                    mX = mStartX;
                    mY = mStartY;
                    resetControl();
                    mPreviousCommand = 'Z';
                    mCommand = 0;
                }
                default -> throw new IllegalArgumentException("Unsupported SVG path command: " + mCommand);
            }
        }

        private void lineTo(float x, float y) {
            mPath.lineTo(x, y);
            mX = x;
            mY = y;
            resetControl();
        }

        private void resetControl() {
            mControlX = mX;
            mControlY = mY;
        }

        private float coordinateX(float value, boolean relative) {
            return relative ? mX + value : value;
        }

        private float coordinateY(float value, boolean relative) {
            return relative ? mY + value : value;
        }

        private float nextNumber() {
            if (mIndex >= mTokens.size() || isCommand(mTokens.get(mIndex))) {
                throw new IllegalArgumentException("Incomplete SVG path command: " + mCommand);
            }
            return Float.parseFloat(mTokens.get(mIndex++));
        }

        private static boolean isCommand(String token) {
            return token.length() == 1 && Character.isLetter(token.charAt(0));
        }
    }
}
