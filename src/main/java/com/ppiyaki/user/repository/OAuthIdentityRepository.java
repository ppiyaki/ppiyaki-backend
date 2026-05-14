package com.ppiyaki.user.repository;

import com.ppiyaki.user.domain.OAuthIdentity;
import com.ppiyaki.user.domain.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, Long> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
