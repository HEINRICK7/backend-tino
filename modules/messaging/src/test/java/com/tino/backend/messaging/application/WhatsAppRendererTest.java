package com.tino.backend.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.tino.backend.messaging.adapter.out.rendering.Java2dWhatsAppCardRenderer;
import com.tino.backend.messaging.application.model.MessageCardModel;
import com.tino.backend.messaging.application.model.WhatsAppStatementSnapshot;
import com.tino.backend.messaging.application.service.RenderWhatsAppMessage;
import com.tino.backend.messaging.application.service.WhatsAppTextComposer;
import com.tino.backend.messaging.domain.model.DebtDisplayStatus;
import com.tino.backend.messaging.domain.model.DebtEntryDisplayType;
import com.tino.backend.messaging.domain.model.DebtEntryView;
import com.tino.backend.messaging.domain.model.DebtStatementView;
import com.tino.backend.messaging.domain.model.MoneyView;
import com.tino.backend.messaging.domain.model.WhatsAppMessageType;
import com.tino.backend.shared.kernel.BusinessId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;

class WhatsAppRendererTest {
    private static final UUID ACCOUNT = UUID.fromString("018f2e48-4f7e-7c34-9c37-c7d5cf3dfd4a");
    private static final UUID CUSTOMER = UUID.fromString("018f2e48-4f7e-7c34-9c37-c7d5cf3dfd4b");
    private static final BusinessId BUSINESS = new BusinessId(
            UUID.fromString("018f2e48-4f7e-7c34-9c37-c7d5cf3dfd4c"));
    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Test
    void composesShortHumanTextWithExactMoneyAndDate() {
        var snapshot = snapshot(6, new BigDecimal("693.50"));
        var text = new WhatsAppTextComposer().compose(WhatsAppMessageType.DEBT_STATEMENT, snapshot);

        assertThat(text).contains("Olá, Gerlane de Araújo.")
                .contains("*R$ 693,50*")
                .contains("6 lançamentos")
                .contains("08/09/2026")
                .doesNotContain("score", "risco");
    }

    @Test
    void rendersDeterministicPngAndSummarizesMoreThanEightEntries() throws Exception {
        var snapshot = snapshot(18, new BigDecimal("1240.50"));
        var renderer = new RenderWhatsAppMessage(new WhatsAppTextComposer(), new Java2dWhatsAppCardRenderer());
        var first = renderer.execute(WhatsAppMessageType.DEBT_STATEMENT, snapshot);
        var second = renderer.execute(WhatsAppMessageType.DEBT_STATEMENT, snapshot);

        assertThat(first.renderedHash()).isEqualTo(second.renderedHash());
        assertThat(first.media().sha256()).isEqualTo(second.media().sha256());
        assertThat(first.media().content()).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        var image = ImageIO.read(new ByteArrayInputStream(first.media().content()));
        assertThat(image.getWidth()).isEqualTo(1080);
        assertThat(image.getHeight()).isLessThan(1500);
    }

    @Test
    void supportsLongCustomerNamesAndZeroEntries() {
        var statement = new DebtStatementView("Mercadinho TINO", "Cliente com um nome muito longo ".repeat(6),
                MoneyView.brl(BigDecimal.ZERO), DebtDisplayStatus.SETTLED, java.util.List.of(), NOW);
        var snapshot = new WhatsAppStatementSnapshot(BUSINESS, ACCOUNT, CUSTOMER, "+5586999999999", 0, statement);
        var message = new RenderWhatsAppMessage(new WhatsAppTextComposer(), new Java2dWhatsAppCardRenderer())
                .execute(WhatsAppMessageType.PAYMENT_CONFIRMED, snapshot);

        assertThat(message.text()).contains("R$ 0,00");
        assertThat(message.media().content()).isNotEmpty();
    }

    private static WhatsAppStatementSnapshot snapshot(int count, BigDecimal balance) {
        var entries = new ArrayList<DebtEntryView>();
        for (var index = 0; index < count; index++) {
            entries.add(new DebtEntryView(LocalDate.of(2026, 9, Math.min(28, index + 1)),
                    index % 3 == 0 ? DebtEntryDisplayType.PAYMENT : DebtEntryDisplayType.PURCHASE,
                    "Compra fiada " + index, MoneyView.brl(new BigDecimal("35.00"))));
        }
        var statement = new DebtStatementView("Mercadinho TINO", "Gerlane de Araújo",
                MoneyView.brl(balance), balance.signum() == 0 ? DebtDisplayStatus.SETTLED : DebtDisplayStatus.OPEN,
                entries, NOW);
        return new WhatsAppStatementSnapshot(BUSINESS, ACCOUNT, CUSTOMER, "+5586999999999", count, statement);
    }
}
