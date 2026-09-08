package com.tino.backend.messaging.adapter.out.rendering;

import com.tino.backend.messaging.application.model.MessageCardModel;
import com.tino.backend.messaging.application.model.RenderedMedia;
import com.tino.backend.messaging.application.port.out.WhatsAppCardRenderer;
import com.tino.backend.messaging.application.service.MoneyFormatter;
import com.tino.backend.messaging.domain.model.DebtEntryDisplayType;
import com.tino.backend.messaging.domain.model.DebtEntryView;
import com.tino.backend.messaging.domain.model.MoneyView;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/** Deterministic server-side PNG renderer; it never calls a browser or remote font. */
@Component
public final class Java2dWhatsAppCardRenderer implements WhatsAppCardRenderer {
    private static final int WIDTH = 1080;
    private static final int PADDING = 64;
    private static final Color SURFACE = new Color(247, 250, 248);
    private static final Color PRIMARY = new Color(0, 112, 70);
    private static final Color TEXT = new Color(27, 40, 35);
    private static final Color SECONDARY = new Color(93, 112, 104);
    private static final Color BORDER = new Color(220, 231, 225);
    private static final Color ORANGE = new Color(245, 130, 0);
    private static final Color BLUE = new Color(45, 104, 224);
    private static final Color RED = new Color(224, 61, 68);
    private static final Color PURPLE = new Color(119, 76, 190);
    private static final DateTimeFormatter GROUP_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.forLanguageTag("pt-BR"));

    @Override
    public RenderedMedia render(MessageCardModel model) {
        try {
            var statement = model.statement();
            var many = statement.entries().size() > 8;
            var visible = many
                    ? statement.entries().subList(Math.max(0, statement.entries().size() - 5), statement.entries().size())
                    : statement.entries();
            var groups = groupByDate(visible);
            var height = 350 + visible.size() * 96 + groups.size() * 34 + (many ? 132 : 0);
            var image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            configure(graphics);
            graphics.setColor(SURFACE);
            graphics.fillRect(0, 0, WIDTH, height);
            drawHeader(graphics, model.messageType(), statement.businessName(), height);
            drawSummary(graphics, model.messageType(), statement.customerDisplayName(), statement.openBalance(), height);
            drawEntries(graphics, groups, many, statement.entries().size(), height);
            graphics.dispose();

            var bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", bytes)) {
                throw new IllegalStateException("PNG writer unavailable");
            }
            var content = bytes.toByteArray();
            return new RenderedMedia("image/png", content,
                    model.messageType().templateId() + "-v1.png", sha256(content));
        } catch (RuntimeException | java.io.IOException exception) {
            throw new IllegalStateException("WhatsApp card rendering failed", exception);
        }
    }

    private static void configure(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    }

    private static void drawHeader(Graphics2D g, WhatsAppMessageType type, String businessName, int height) {
        var accent = accent(type);
        g.setColor(PRIMARY);
        g.fillRect(0, 0, WIDTH, 176);
        g.setColor(accent);
        g.fillRect(0, 168, WIDTH, 8);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 50));
        g.drawString("TINO", PADDING, 82);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("• Caderneta", PADDING + 190, 82);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(type.label(), PADDING, 136);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        g.drawString(ellipsize(g, businessName, WIDTH - 2 * PADDING, 24), PADDING, height - height + 162);
    }

    private static void drawSummary(Graphics2D g, WhatsAppMessageType type, String customer, MoneyView balance, int height) {
        var top = 224;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(PADDING, top, WIDTH - 2.0 * PADDING, 182, 28, 28));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(2));
        g.draw(new RoundRectangle2D.Double(PADDING, top, WIDTH - 2.0 * PADDING, 182, 28, 28));
        g.setColor(SECONDARY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("Cliente", PADDING + 32, top + 50);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 34));
        g.drawString(ellipsize(g, customer, 530, 34), PADDING + 32, top + 94);
        g.setColor(SECONDARY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
        g.drawString("Saldo em aberto", WIDTH - PADDING - 390, top + 50);
        g.setColor(accent(type));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
        g.drawString(ellipsize(g, MoneyFormatter.format(balance), 350, 42), WIDTH - PADDING - 390, top + 106);
    }

    private static void drawEntries(Graphics2D g, List<EntryGroup> groups, boolean many, int totalEntries, int height) {
        var top = 446;
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        g.drawString(many ? "Últimos lançamentos" : "Lançamentos", PADDING, top);
        g.setColor(SECONDARY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
        if (many) {
            g.drawString("Mostrando 5 de " + totalEntries + " lançamentos", PADDING, top + 38);
            top += 58;
        } else {
            top += 24;
        }
        for (var group : groups) {
            g.setColor(PRIMARY);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 23));
            g.drawString(group.date().format(GROUP_DATE).toUpperCase(Locale.forLanguageTag("pt-BR")), PADDING, top);
            top += 34;
            for (var entry : group.entries()) {
                drawEntry(g, entry, top);
                top += 96;
            }
        }
        if (many) {
            g.setColor(PRIMARY);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            g.drawString("Ver extrato completo no TINO", PADDING, height - 42);
        }
    }

    private static void drawEntry(Graphics2D g, DebtEntryView entry, int top) {
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(PADDING, top, WIDTH - 2.0 * PADDING, 76, 18, 18));
        g.setColor(BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(PADDING, top, WIDTH - 2.0 * PADDING, 76, 18, 18));
        g.setColor(entry.type() == DebtEntryDisplayType.PAYMENT ? PRIMARY : SECONDARY);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 25));
        g.drawString(ellipsize(g, entry.description(), 670, 25), PADDING + 22, top + 31);
        g.setColor(entry.type() == DebtEntryDisplayType.PAYMENT ? PRIMARY : TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        g.drawString(ellipsize(g, MoneyFormatter.format(entry.amount()), 280, 26), WIDTH - PADDING - 300, top + 31);
        g.setColor(SECONDARY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
        g.drawString(entry.type().name().toLowerCase(Locale.ROOT), PADDING + 22, top + 58);
    }

    private static List<EntryGroup> groupByDate(List<DebtEntryView> entries) {
        var grouped = new LinkedHashMap<LocalDate, List<DebtEntryView>>();
        for (var entry : entries) {
            grouped.computeIfAbsent(entry.occurredAt(), ignored -> new java.util.ArrayList<>()).add(entry);
        }
        return grouped.entrySet().stream().map(entry -> new EntryGroup(entry.getKey(), List.copyOf(entry.getValue()))).toList();
    }

    private record EntryGroup(LocalDate date, List<DebtEntryView> entries) {}

    private static Color accent(WhatsAppMessageType type) {
        return switch (type) {
            case DEBT_STATEMENT -> PRIMARY;
            case DEBT_CREATED -> ORANGE;
            case PAYMENT_CONFIRMED -> PRIMARY;
            case PARTIAL_PAYMENT_CONFIRMED -> BLUE;
            case PAYMENT_REMINDER -> RED;
            case PROMISE_TO_PAY, PAYMENT_AGREEMENT -> PURPLE;
        };
    }

    private static String ellipsize(Graphics2D g, String value, int maxWidth, int fontSize) {
        var metrics = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, fontSize));
        if (metrics.stringWidth(value) <= maxWidth) {
            return value;
        }
        var suffix = "…";
        var result = value;
        while (!result.isEmpty() && metrics.stringWidth(result + suffix) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + suffix;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
