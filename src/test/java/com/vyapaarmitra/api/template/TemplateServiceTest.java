package com.vyapaarmitra.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.business.Business;
import com.vyapaarmitra.api.business.BusinessRepository;
import com.vyapaarmitra.api.common.ApiException;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.ledger.EntryType;
import com.vyapaarmitra.api.ledger.LedgerEntryRepository;
import com.vyapaarmitra.api.share.ShareService;
import com.vyapaarmitra.api.template.TemplateDtos.RenderResponse;
import com.vyapaarmitra.api.user.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The khata-link auto-append on {@link TemplateService#render}. */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private MessageTemplateRepository templateRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private BranchAccessService branchAccessService;
    @Mock private CustomerService customerService;
    @Mock private ShareService shareService;
    @Mock private AppProperties appProperties;
    @Mock private AppTime appTime;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks private TemplateService service;

    private final AuthUser authUser = new AuthUser(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

    private MessageTemplate enabledTemplate() {
        MessageTemplate t = new MessageTemplate();
        t.setId(UUID.randomUUID());
        t.setBusinessId(authUser.businessId());
        t.setBody("Namaste {{customer_name}}");
        t.setEnabled(true);
        return t;
    }

    private Customer customer(String phone) {
        Customer c = new Customer();
        c.setBusinessId(authUser.businessId());
        c.setBranchId(UUID.randomUUID());
        c.setName("Ramesh");
        c.setPhone(phone);
        c.setCurrentBalance(BigDecimal.ZERO);
        return c;
    }

    @Test
    void appendsKhataLinkWhenPartyHasPhone() {
        MessageTemplate template = enabledTemplate();
        UUID customerId = UUID.randomUUID();
        Customer customer = customer("+91 98765 43210");
        customer.setId(customerId);
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer);
        when(shareService.createLedgerShare(authUser, customerId)).thenReturn("Tok123");
        when(appProperties.webUrl()).thenReturn("https://app.vyapaarmitra.in/");

        RenderResponse res = service.render(authUser, template.getId(), customerId, null, null);

        assertThat(res.text()).startsWith("Namaste Ramesh");
        // Single slash even though webUrl carried a trailing one.
        assertThat(res.text()).contains("https://app.vyapaarmitra.in/s/Tok123");
    }

    @Test
    void skipsKhataLinkWhenPartyHasNoPhone() {
        MessageTemplate template = enabledTemplate();
        Customer customer = customer(null);
        UUID customerId = UUID.randomUUID();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer);

        RenderResponse res = service.render(authUser, template.getId(), customerId, null, null);

        assertThat(res.text()).isEqualTo("Namaste Ramesh");
        assertThat(res.text()).doesNotContain("/s/");
        verify(shareService, never()).createLedgerShare(any(), any());
    }

    @Test
    void renderWithWindowFillsWindowTokens() {
        MessageTemplate template = enabledTemplate();
        template.setBody("{{window_start}} se {{window_end}}: kharide {{window_credit}}, "
            + "chukaye {{window_payment}}, baaki {{window_due}}");
        UUID customerId = UUID.randomUUID();
        Customer customer = customer(null);
        customer.setId(customerId);
        customer.setCurrentBalance(new BigDecimal("1500"));
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        // Half-open window [start, end+1) — boundary days inclusive.
        Instant from = Instant.parse("2026-08-31T18:30:00Z");
        Instant to = Instant.parse("2026-09-30T18:30:00Z");
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer);
        when(appTime.startOfDay(start)).thenReturn(from);
        when(appTime.startOfDay(end.plusDays(1))).thenReturn(to);
        when(ledgerEntryRepository.sumByCustomerAndTypeBetween(
            customerId, EntryType.CREDIT, from, to)).thenReturn(new BigDecimal("4200"));
        when(ledgerEntryRepository.sumByCustomerAndTypeBetween(
            customerId, EntryType.PAYMENT, from, to)).thenReturn(new BigDecimal("1000"));
        // A ₹500 payment recorded after the window: balance at window close is higher.
        when(ledgerEntryRepository.signedSumAfter(customerId, to))
            .thenReturn(new BigDecimal("-500"));

        RenderResponse res = service.render(authUser, template.getId(), customerId, start, end);

        assertThat(res.text())
            .contains("01-09-2026", "30-09-2026", "₹4200", "₹1000", "₹2000");
    }

    @Test
    void renderFillsBusinessNameWithShopName() {
        MessageTemplate template = enabledTemplate();
        template.setBody("{{business_name}}: aapka {{amount_due}} baaki hai");
        UUID customerId = UUID.randomUUID();
        Customer customer = customer(null);
        customer.setId(customerId);
        Business shop = new Business();
        shop.setName("Demo3 Store");
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer);
        when(businessRepository.findById(customer.getBusinessId())).thenReturn(Optional.of(shop));

        RenderResponse res = service.render(authUser, template.getId(), customerId, null, null);

        assertThat(res.text()).startsWith("Demo3 Store: ");
    }

    @Test
    void renderRejectsStartAfterEndDate() {
        MessageTemplate template = enabledTemplate();
        UUID customerId = UUID.randomUUID();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer(null));

        assertThatThrownBy(() -> service.render(authUser, template.getId(), customerId,
                LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1)))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_DATE_RANGE"));
    }

    @Test
    void renderRejectsHalfSpecifiedWindow() {
        MessageTemplate template = enabledTemplate();
        UUID customerId = UUID.randomUUID();
        when(templateRepository.findById(template.getId())).thenReturn(Optional.of(template));
        when(customerService.loadAccessible(authUser, customerId)).thenReturn(customer(null));

        assertThatThrownBy(() -> service.render(authUser, template.getId(), customerId,
                LocalDate.of(2026, 9, 1), null))
            .isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("INVALID_DATE_RANGE"));
    }
}
