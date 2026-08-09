package com.mine.geometry_node.client.ui.common;

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
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvgIconView extends View {
    public enum Icon {
        GRAPH_EDITOR("graph_editor.svg"),
        ASSET_LIBRARY("asset_library.svg"),
        TERMINAL("console.svg"),
        PERFORMANCE("analysis.svg"),
        CLEAR("clear.svg"),
        RESET("refresh.svg"),
        CLOSE("close.svg");

        private final String mFileName;

        Icon(String fileName) {
            mFileName = fileName;
        }

        String resourcePath() {
            return "/assets/geometry_node/icons/ui/" + mFileName;
        }
    }

    private static final int DEFAULT_COLOR = 0xFFB8C0CC;
    private static final int MAX_CACHED_IMAGES = 64;
    private static final Map<Icon, SvgDocument> DOCUMENTS = new EnumMap<>(Icon.class);
    private static final Map<RenderKey, Image> IMAGES = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<RenderKey, Image> eldest) {
            if (size() <= MAX_CACHED_IMAGES) {
                return false;
            }
            eldest.getValue().close();
            return true;
        }
    };

    private final Paint mPaint = new Paint();
    private Icon mIcon;
    private int mColor;

    public SvgIconView(Context context, Icon icon, int color) {
        super(context);
        mIcon = icon;
        mColor = resolveColor(color);
        mPaint.setAntiAlias(true);
        setWillNotDraw(false);
    }

    public void setIcon(Icon icon) {
        if (mIcon == icon) {
            return;
        }
        mIcon = icon;
        invalidate();
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

        Image image = getImage(mIcon, size, mColor);
        float left = (getWidth() - size) * 0.5f;
        float top = (getHeight() - size) * 0.5f;
        canvas.drawImage(image, left, top, mPaint);
    }

    private static int resolveColor(int color) {
        return color == 0 ? DEFAULT_COLOR : color;
    }

    private static synchronized Image getImage(Icon icon, int size, int color) {
        RenderKey key = new RenderKey(icon, size, color);
        Image cached = IMAGES.get(key);
        if (cached != null && !cached.isClosed()) {
            return cached;
        }

        SvgDocument document = DOCUMENTS.computeIfAbsent(icon, SvgIconView::loadDocument);
        Image image = rasterize(document, size, color);
        IMAGES.put(key, image);
        return image;
    }

    private static SvgDocument loadDocument(Icon icon) {
        try (InputStream input = SvgIconView.class.getResourceAsStream(icon.resourcePath())) {
            if (input == null) {
                throw new IllegalStateException("Missing SVG icon resource: " + icon.resourcePath());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Element root = factory.newDocumentBuilder().parse(input).getDocumentElement();
            float[] viewBox = numbers(root.getAttribute("viewBox"), 4);
            List<SvgLayer> layers = new ArrayList<>();
            collectLayers(root, SvgStyle.DEFAULT, layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("SVG icon has no drawable paths: " + icon.resourcePath());
            }
            return new SvgDocument(viewBox[0], viewBox[1], viewBox[2], viewBox[3], List.copyOf(layers));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load SVG icon " + icon.resourcePath(), exception);
        }
    }

    private static void collectLayers(Element element, SvgStyle inherited, List<SvgLayer> layers) {
        SvgStyle style = inherited.merge(element);
        Shape shape = switch (element.getTagName()) {
            case "path" -> parsePath(element.getAttribute("d"), style.evenOdd());
            case "rect" -> parseRect(element);
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

        int[] pixels = bufferedImage.getRGB(0, 0, size, size, null, 0, size);
        try (Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Format.RGBA_8888)) {
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            bitmap.setImmutable();
            return Image.createTextureFromBitmap(bitmap);
        }
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

    private record RenderKey(Icon icon, int size, int color) {
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
        private static final Pattern TOKEN_PATTERN = Pattern.compile(
                "[CcHhLlMmQqSsTtVvZz]|[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");
        private static final Pattern NUMBER_PATTERN = Pattern.compile(
                "[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?");

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
            while (matcher.find()) {
                mTokens.add(matcher.group());
            }
            mPath = new Path2D.Float(evenOdd ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
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
