package org.apereo.cas.oidc.claims;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.cas.authentication.principal.Principal;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.oidc.claims.mapping.OidcAttributeToScopeClaimMapper;
import org.apereo.cas.services.AbstractRegisteredServiceAttributeReleasePolicy;
import org.apereo.cas.services.RegisteredService;
import org.apereo.cas.util.spring.ApplicationContextProvider;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This is {@link BaseOidcScopeAttributeReleasePolicy}.
 *
 * @author Misagh Moayyed
 * @since 5.1.0
 */
@Slf4j
public abstract class BaseOidcScopeAttributeReleasePolicy extends AbstractRegisteredServiceAttributeReleasePolicy {
    private static final long serialVersionUID = -7302163334687300920L;


    private List<String> allowedAttributes;

    @JsonIgnore
    private String scopeName;

    public BaseOidcScopeAttributeReleasePolicy(final String scopeName) {
        this.scopeName = scopeName;
    }

    public String getScopeName() {
        return scopeName;
    }

    public void setAllowedAttributes(final List<String> allowed) {
        this.allowedAttributes = allowed;
    }

    public List<String> getAllowedAttributes() {
        return this.allowedAttributes;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        final BaseOidcScopeAttributeReleasePolicy rhs = (BaseOidcScopeAttributeReleasePolicy) obj;
        return new EqualsBuilder()
            .appendSuper(super.equals(obj))
            .append(getAllowedAttributes(), rhs.getAllowedAttributes())
            .append(getScopeName(), rhs.getScopeName())
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(13, 133)
            .appendSuper(super.hashCode())
            .append(getAllowedAttributes())
            .append(getScopeName())
            .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .appendSuper(super.toString())
            .append("allowedAttributes", getAllowedAttributes())
            .append("scopeName", scopeName)
            .toString();
    }

    @Override
    public Map<String, Object> getAttributesInternal(final Principal principal,
                                                     final Map<String, Object> attributes,
                                                     final RegisteredService service) {
        final ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
        if (applicationContext == null) {
            LOGGER.warn("Could not locate the application context to process attributes");
            return new HashMap<>();
        }

        final Map<String, Object> resolvedAttributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        resolvedAttributes.putAll(attributes);
        final Map<String, Object> attributesToRelease = new HashMap<>(resolvedAttributes.size());
        LOGGER.debug("Attempting to map and filter claims based on resolved attributes [{}]", resolvedAttributes);

        final CasConfigurationProperties properties = applicationContext.getBean(CasConfigurationProperties.class);
        final List<String> supportedClaims = properties.getAuthn().getOidc().getClaims();
        final Set<String> allowedClaims = new HashSet<>(getAllowedAttributes());
        allowedClaims.retainAll(supportedClaims);

        LOGGER.debug("[{}] is designed to allow claims [{}] for scope [{}]. After cross-checking with "
                + "supported claims [{}], the final collection of allowed attributes is [{}]",
            getClass().getSimpleName(), getAllowedAttributes(),
            getScopeName(), supportedClaims, allowedClaims);

        allowedClaims
            .stream()
            .map(claim -> mapClaimToAttribute(claim, resolvedAttributes))
            .filter(p -> p.getValue() != null)
            .forEach(p -> attributesToRelease.put(p.getKey(), p.getValue()));
        return attributesToRelease;
    }

    private Pair<String, Object> mapClaimToAttribute(final String claim, final Map<String, Object> resolvedAttributes) {
        final ApplicationContext applicationContext = ApplicationContextProvider.getApplicationContext();
        final OidcAttributeToScopeClaimMapper attributeToScopeClaimMapper =
            applicationContext.getBean("oidcAttributeToScopeClaimMapper", OidcAttributeToScopeClaimMapper.class);

        LOGGER.debug("Attempting to process claim [{}]", claim);

        if (attributeToScopeClaimMapper.containsMappedAttribute(claim)) {
            final String mappedAttr = attributeToScopeClaimMapper.getMappedAttribute(claim);
            final Object value = resolvedAttributes.get(mappedAttr);
            LOGGER.debug("Found mapped attribute [{}] with value [{}] for claim [{}]", mappedAttr, value, claim);
            return Pair.of(claim, value);
        }

        final Object value = resolvedAttributes.get(claim);
        LOGGER.debug("No mapped attribute is defined for claim [{}]; Used [{}] to locate value [{}]", claim, claim, value);
        return Pair.of(claim, value);
    }
}
