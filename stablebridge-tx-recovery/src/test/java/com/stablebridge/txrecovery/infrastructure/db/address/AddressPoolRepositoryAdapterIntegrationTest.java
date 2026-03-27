package com.stablebridge.txrecovery.infrastructure.db.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.stablebridge.txrecovery.domain.address.model.AddressStatus;
import com.stablebridge.txrecovery.domain.address.model.AddressTier;
import com.stablebridge.txrecovery.domain.address.model.ChainFamily;
import com.stablebridge.txrecovery.domain.address.model.PooledAddress;
import com.stablebridge.txrecovery.domain.address.port.AddressPoolRepository;
import com.stablebridge.txrecovery.domain.address.port.NonceManager;
import com.stablebridge.txrecovery.domain.address.port.PoolExhaustedAlertPublisher;
import com.stablebridge.txrecovery.testutil.PgTest;

@PgTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class AddressPoolRepositoryAdapterIntegrationTest {

    @Autowired
    private AddressPoolRepository addressPoolRepository;

    @Autowired
    private AddressPoolJpaRepository jpaRepository;

    @MockitoBean
    private NonceManager nonceManager;

    @MockitoBean
    private PoolExhaustedAlertPublisher poolExhaustedAlertPublisher;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAll();
    }

    @Nested
    class FindBestCandidate {

        @Test
        void shouldReturnCandidateWithLowestInFlightCount() {
            // given
            var highInFlight = saveAddress("0xhigh", AddressTier.HOT, 5, null);
            var lowInFlight = saveAddress("0xlow", AddressTier.HOT, 1, null);

            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().address()).isEqualTo("0xlow");
        }

        @Test
        void shouldBreakTieByLeastRecentlyUsed() {
            // given
            var recentlyUsed = saveAddress("0xrecent", AddressTier.HOT, 2,
                    Instant.parse("2026-01-02T00:00:00Z"));
            var leastRecentlyUsed = saveAddress("0xlru", AddressTier.HOT, 2,
                    Instant.parse("2026-01-01T00:00:00Z"));

            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().address()).isEqualTo("0xlru");
        }

        @Test
        void shouldPreferNullLastUsedAtOverNonNull() {
            // given
            var used = saveAddress("0xused", AddressTier.HOT, 2,
                    Instant.parse("2026-01-01T00:00:00Z"));
            var neverUsed = saveAddress("0xnever", AddressTier.HOT, 2, null);

            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().address()).isEqualTo("0xnever");
        }

        @Test
        void shouldExcludeAddressesAtMaxPipelineDepth() {
            // given
            var atMax = saveAddress("0xfull", AddressTier.HOT, 5, null);

            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 5);

            // then
            assertThat(result).isEmpty();
        }

        @Test
        void shouldFilterByTier() {
            // given
            var hotAddress = saveAddress("0xhot", AddressTier.HOT, 0, null);
            var priorityAddress = saveAddress("0xpriority", AddressTier.PRIORITY, 0, null);

            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.PRIORITY, AddressStatus.ACTIVE, 20);

            // then
            assertThat(result).isPresent();
            assertThat(result.get().address()).isEqualTo("0xpriority");
        }

        @Test
        void shouldReturnEmptyWhenNoCandidates() {
            // when
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class InFlightCountOperations {

        @Test
        void shouldIncrementInFlightCount() {
            // given
            saveAddress("0xaddr", AddressTier.HOT, 0, null);

            // when
            addressPoolRepository.incrementInFlightCount("0xaddr", "ethereum");

            // then
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);
            assertThat(result).isPresent();
            assertThat(result.get().inFlightCount()).isEqualTo(1);
            assertThat(result.get().lastUsedAt()).isNotNull();
        }

        @Test
        void shouldDecrementInFlightCount() {
            // given
            saveAddress("0xaddr", AddressTier.HOT, 3, null);

            // when
            addressPoolRepository.decrementInFlightCount("0xaddr", "ethereum");

            // then
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);
            assertThat(result).isPresent();
            assertThat(result.get().inFlightCount()).isEqualTo(2);
        }

        @Test
        void shouldNotDecrementBelowZero() {
            // given
            saveAddress("0xaddr", AddressTier.HOT, 0, null);

            // when
            addressPoolRepository.decrementInFlightCount("0xaddr", "ethereum");

            // then
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);
            assertThat(result).isPresent();
            assertThat(result.get().inFlightCount()).isEqualTo(0);
        }
    }

    @Nested
    class Save {

        @Test
        void shouldSaveAndRetrieveAddress() {
            // given
            var address = PooledAddress.builder()
                    .address("0xnew")
                    .chain("ethereum")
                    .chainFamily(ChainFamily.EVM)
                    .tier(AddressTier.HOT)
                    .status(AddressStatus.ACTIVE)
                    .currentNonce(0)
                    .inFlightCount(0)
                    .signerEndpoint("http://signer:8080")
                    .registeredAt(Instant.parse("2026-01-01T00:00:00Z"))
                    .build();

            // when
            var saved = addressPoolRepository.save(address);

            // then
            var result = addressPoolRepository.findBestCandidate(
                    "ethereum", AddressTier.HOT, AddressStatus.ACTIVE, 20);
            assertThat(result).isPresent();

            var expected = address.toBuilder().build();
            assertThat(result.get())
                    .usingRecursiveComparison()
                    .ignoringFields("id", "lastUsedAt")
                    .isEqualTo(expected);
        }
    }

    private PooledAddress saveAddress(String address, AddressTier tier, int inFlightCount, Instant lastUsedAt) {
        var pooledAddress = PooledAddress.builder()
                .address(address)
                .chain("ethereum")
                .chainFamily(ChainFamily.EVM)
                .tier(tier)
                .status(AddressStatus.ACTIVE)
                .currentNonce(0)
                .inFlightCount(inFlightCount)
                .signerEndpoint("http://signer:8080")
                .registeredAt(Instant.parse("2026-01-01T00:00:00Z"))
                .lastUsedAt(lastUsedAt)
                .build();
        return addressPoolRepository.save(pooledAddress);
    }
}
