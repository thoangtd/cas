package org.apereo.cas.support.spnego;

import lombok.extern.slf4j.Slf4j;

import java.security.Principal;

/**
 * @author Marc-Antoine Garrigue
 * @author Arnaud Lesueur
 * @since 3.1
 */
@Slf4j
public class MockPrincipal implements Principal {

    private final String principal;

    public MockPrincipal(final String principal) {
        super();
        this.principal = principal;
    }

    @Override
    public String getName() {
        return this.principal;
    }

}
