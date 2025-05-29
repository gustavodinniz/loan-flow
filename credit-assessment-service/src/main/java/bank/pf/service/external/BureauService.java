package bank.pf.service.external;

import bank.pf.config.WireMockSetupConfig;
import bank.pf.entity.BureauScore;
import bank.pf.exception.BureauApiException;
import bank.pf.exception.BureauNotFoundException;
import bank.pf.exception.BureauNullResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BureauService {

    private final WireMockSetupConfig wireMockSetupConfig;
    private final RedisTemplate<String, BureauScore> bureauScoreRedisTemplate;
    private static final String BUREAU_SCORE_CACHE_PREFIX = "bureauScore:";
    private static final long CACHE_TTL_HOURS = 24;

    public BureauScore getScore(String cpf) {
        log.info("WiremockBureauService.getScore called with cpf: {}", cpf);
        String cacheKey = BUREAU_SCORE_CACHE_PREFIX + cpf;

        try {
            var cachedScore = bureauScoreRedisTemplate.opsForValue().get(cacheKey);
            if (cachedScore != null) {
                log.info("Bureau score for CPF {} found in cache.", cpf);
                return cachedScore;
            }
        } catch (Exception e) {
            log.warn("Error accessing Redis cache for bureau score (CPF: {}): {}", cpf, e.getMessage());
        }

        log.info("Fetching bureau score for CPF {} from external service.", cpf);
        try {
            BureauScore score = wireMockSetupConfig.getRestClient().get()
                    .uri("/api/bureau/score/{cpf}", cpf)
                    .retrieve()
                    .body(BureauScore.class);

            if (score != null) {
                log.info("Successfully fetched bureau score for CPF {}: {}", cpf, score);
                savingBureauScoreInCache(cpf, cacheKey, score);
                return score;
            }
            throw new BureauNullResponseException(cpf);
        } catch (BureauNullResponseException e) {
            log.error("Received null response from bureau service for CPF {}: {}", cpf, e.getMessage());
            throw e;
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Bureau score not found for CPF {}: {}", cpf, e.getStatusCode());
            throw new BureauNotFoundException(cpf, e);
        } catch (Exception e) {
            log.error("Error fetching bureau score for CPF {}: {}", cpf, e.getMessage(), e);
            throw new BureauApiException(cpf, e);
        }
    }

    private void savingBureauScoreInCache(String cpf, String cacheKey, BureauScore score) {
        try {
            bureauScoreRedisTemplate.opsForValue().set(cacheKey, score, CACHE_TTL_HOURS, TimeUnit.HOURS);
            log.info("Bureau score for CPF {} saved to cache.", cpf);
        } catch (Exception e) {
            log.warn("Error saving bureau score to Redis cache (CPF: {}): {}", cpf, e.getMessage());
        }
    }


}
