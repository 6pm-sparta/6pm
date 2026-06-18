package com.fandom.order_service.order.application;

import com.fandom.common.exception.CommonErrorCode;
import com.fandom.common.exception.CustomException;
import com.fandom.order_service.config.OrderProperties;
import com.fandom.order_service.order.domain.entity.Order;
import com.fandom.order_service.order.domain.entity.OrderStatus;
import com.fandom.order_service.order.domain.repository.OrderRepository;
import com.fandom.order_service.order.presentation.dto.request.CreateOrderRequest;
import com.fandom.order_service.order.presentation.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 주문 생성. 멱등성은 "holdId 기반 Redis 1차 방어 + seatId 진행중 상태 부분 UNIQUE 인덱스 2차 방어"
 *
 * Redis 정상/장애, INSERT 성공/충돌 모든 경로를 "안 되면 DB에서 진행중 주문을 찾아
 * 멱등 응답으로 돌려준다"는 한 가지 패턴(attemptInsert)으로 통일했다.
 * 분기가 줄고, check-then-act 방식의 race window도 생기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationService {

    private static final String HOLD_KEY_PREFIX = "order:hold:";
    /** SETNX 1차 클레임 마커. 실제 orderId(UUID)와 구분되는, UUID로 파싱되지 않는 문자열이면 된다. */
    private static final String CLAIM_MARKER = "CLAIMED";

    private final StringRedisTemplate redisTemplate;
    private final OrderRepository orderRepository;
    private final OrderCreationWriter orderCreationWriter;
    private final OrderProperties orderProperties;

    public OrderCreationResult createOrder(CreateOrderRequest request) {

        // Redis 키는 "order:hold:{holdId}" 형태. holdId는 Ticketing이 좌석 선점할 때 발급한 값.
        String holdKey = HOLD_KEY_PREFIX + request.holdId();
        Boolean claimed;

        try {

            // SET holdKey "CLAIMED" NX EX {claimTtlSeconds}
            // → 이 holdId로 처리 중인 게 없으면 true(내가 선점), 이미 있으면 false(누가 먼저 옴)
            claimed = redisTemplate.opsForValue().setIfAbsent(
                    holdKey, CLAIM_MARKER, Duration.ofSeconds(orderProperties.hold().claimTtlSeconds()));

        } catch (DataAccessException redisDown) {

            // Redis 커넥션 자체가 죽은 경우. 1차 방어를 포기하고 DB 쪽(2차 방어)만으로 처리.
            log.warn("[주문 생성] Redis 장애로 holdId 1차 방어를 건너뜁니다. seatId 부분 UNIQUE 인덱스로만 방어합니다. holdId={}",
                    request.holdId(), redisDown);

            return attemptInsert(request, null);
        }

        if (Boolean.TRUE.equals(claimed)) {

            // 내가 1차 방어 선점 성공 → INSERT 진행
            return attemptInsert(request, holdKey);
        }

        // 여기 도달 == 같은 holdId로 이미 누가 처리 중이거나 처리 끝남 → 신규가 아니라 멱등 응답이어야 함
        return resolveDuplicate(request, holdKey);
    }

    private OrderCreationResult attemptInsert(CreateOrderRequest request, String holdKey) {

        LocalDateTime expiredAt = LocalDateTime.now().plusMinutes(orderProperties.expirationMinutes());

        try {

            // "이미 있나?" 체크 없이 바로 시도. 동시 요청이 있어도 DB의 부분 unique 제약으로 관리
            // UNIQUE 제약이 최종 심판 역할을 함 (별도 트랜잭션 빈)
            Order order = orderCreationWriter.insertPendingOrder(
                    request.seatId(), request.userId(), request.totalAmount(), expiredAt);

            // INSERT 성공 → "CLAIMED" 마커였던 Redis 값을 실제 orderId로 덮어씀
            cacheResolvedOrderId(holdKey, order.getId());
            return new OrderCreationResult(OrderResponse.from(order), true);    // true = 신규 생성(201)

        } catch (DataIntegrityViolationException seatAlreadyActive) {

            // 부분 unique 제약 충돌 = 이 seatId로 이미 진행중 주문이 DB에 있음
            releaseClaim(holdKey);  // 잘못 선점한 Redis 클레임 보상 삭제 (DEL holdKey)

            return findActiveBySeatIdOrThrow(request, seatAlreadyActive);   // 실제 주문 찾아서 멱등 응답
        }
    }

    private OrderCreationResult resolveDuplicate(CreateOrderRequest request, String holdKey) {

        String cached = redisTemplate.opsForValue().get(holdKey);

        if (cached != null) {

            try {

                UUID cachedOrderId = UUID.fromString(cached);     // "CLAIMED"면 여기서 예외 터짐(holdId가 아직 처리 중임)
                Optional<Order> existing = orderRepository.findById(cachedOrderId);

                if (existing.isPresent()) {
                    return new OrderCreationResult(OrderResponse.from(existing.get()), false);
                }

                // 캐시엔 있는데 DB엔 없음 — 정상 흐름에선 안 생겨야 하는 데이터 불일치
                log.warn("[주문 생성] holdId 캐시는 있는데 주문이 없습니다. 데이터 불일치 의심. holdId={}, cachedOrderId={}",
                        request.holdId(), cachedOrderId);

            } catch (IllegalArgumentException stillClaimMarker) {

                // UUID.fromString이 "CLAIMED" 파싱 실패 → 동일 holdId의 다른 요청이 아직 INSERT 안 끝남(진짜 동시 요청)
                log.debug("[주문 생성] holdId={} 처리 중인 동시 요청과 충돌. DB 조회로 폴백합니다.", request.holdId());
            }
        }

        // cached==null(TTL 만료/장애)이거나 위 두 케이스 다 막혔으면 DB에서 직접 찾아봄
        return findActiveBySeatIdOrThrow(request, null);
    }

    private OrderCreationResult findActiveBySeatIdOrThrow(CreateOrderRequest request, RuntimeException cause) {

        // seatId + 진행중 상태(PENDING/PAYMENT_REQUESTED/PAID)로 실제 주문 조회.
        // attemptInsert의 충돌 케이스, resolveDuplicate의 폴백 케이스 둘 다 여기로 모임.
        return orderRepository.findFirstBySeatIdAndStatusIn(request.seatId(), OrderStatus.ACTIVE)
                .map(order -> new OrderCreationResult(OrderResponse.from(order), false))
                .orElseThrow(() -> {
                    // 여기까지 와서도 못 찾으면: 동시 요청 중 "먼저 온 쪽"이 아직 커밋 전이라 안 보이는 타이밍
                    log.error("[주문 생성] seatId={} 진행중 주문을 찾지 못함 (동시성 타이밍 또는 데이터 불일치 의심)",
                            request.seatId(), cause);
                    return cause != null ? cause : new CustomException(CommonErrorCode.INTERNAL_SERVER_ERROR);
                });
    }

    private void cacheResolvedOrderId(String holdKey, UUID orderId) {

        if (holdKey == null) {
            return; // Redis 장애 경로 — 캐시 쓰기를 시도하지 않는다.
        }
        try {

            // SET holdKey {orderId} EX {cacheTtlSeconds} — NX 없이 그냥 덮어쓰기.
            // "CLAIMED" → 실제 orderId로 바뀌면서 TTL도 (짧은 클레임용에서) 더 긴 캐시용 값으로 갈아끼움
            redisTemplate.opsForValue().set(
                    holdKey, orderId.toString(), Duration.ofSeconds(orderProperties.hold().cacheTtlSeconds()));
        } catch (DataAccessException redisDown) {

            log.warn("[주문 생성] 주문 생성 후 holdId 캐시 저장 실패(Redis 장애). 캐시 없이 진행(다음 재시도는 DB 폴백으로 처리됨). orderId={}",
                    orderId, redisDown);
        }
    }

    private void releaseClaim(String holdKey) {

        if (holdKey == null) {
            return;
        }
        try {

            // DEL holdKey — 잘못 선점한 클레임 보상 삭제
            redisTemplate.delete(holdKey);
        } catch (DataAccessException redisDown) {

            log.warn("[주문 생성] holdId 클레임 해제 실패(Redis 장애). TTL 만료까지 자동 해제됨. holdKey={}", holdKey, redisDown);
        }
    }
}
