package com.vyapaarmitra.api.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vyapaarmitra.api.auth.AuthUser;
import com.vyapaarmitra.api.business.BranchAccessService;
import com.vyapaarmitra.api.business.BranchRepository;
import com.vyapaarmitra.api.common.AppTime;
import com.vyapaarmitra.api.config.AppProperties;
import com.vyapaarmitra.api.customer.Customer;
import com.vyapaarmitra.api.customer.CustomerService;
import com.vyapaarmitra.api.share.ShareService;
import com.vyapaarmitra.api.template.TemplateDtos.RenderResponse;
import com.vyapaarmitra.api.user.Role;
import java.math.BigDecimal;
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
    @Mock private BranchAccessService branchAccessService;
    @Mock private CustomerService customerService;
    @Mock private ShareService shareService;
    @Mock private AppProperties appProperties;
    @Mock private AppTime appTime;

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

        RenderResponse res = service.render(authUser, template.getId(), customerId);

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

        RenderResponse res = service.render(authUser, template.getId(), customerId);

        assertThat(res.text()).isEqualTo("Namaste Ramesh");
        assertThat(res.text()).doesNotContain("/s/");
        verify(shareService, never()).createLedgerShare(any(), any());
    }
}
