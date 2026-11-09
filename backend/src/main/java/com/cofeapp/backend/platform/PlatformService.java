package com.cofeapp.backend.platform;

import com.cofeapp.backend.billing.BillingService;
import com.cofeapp.backend.billing.BillingState;
import com.cofeapp.backend.business.BusinessRepository;
import com.cofeapp.backend.common.exception.NotFoundException;
import com.cofeapp.backend.domain.Business;
import com.cofeapp.backend.domain.PlatformAction;
import com.cofeapp.backend.domain.PlatformAdmin;
import com.cofeapp.backend.domain.enums.BusinessPlan;
import com.cofeapp.backend.domain.enums.BusinessStatus;
import com.cofeapp.backend.platform.dto.BusinessDetailResponse;
import com.cofeapp.backend.platform.dto.BusinessSummaryResponse;
import com.cofeapp.backend.platform.dto.UpdateBusinessStatusRequest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panel de superadmin (V15): todos los cafés clientes, su actividad, y
 * activar / suspender / dar de baja / cambiar de plan.
 *
 * <p>Las requests del superadmin no abren contexto de café, así que acá no
 * se aplica el filtro de aislamiento: las consultas ven TODOS los cafés a
 * propósito (y {@code SecurityConfig} solo deja entrar al superadmin a
 * {@code /platform/**}, nunca a las pantallas de un café).
 *
 * <p>Qué ve Fran de cada café: datos de alta, dueño, cantidad de locales y
 * empleados, y actividad (pedidos de los últimos 30 días y el último). No
 * ve montos de venta: son del café.
 */
@Service
@RequiredArgsConstructor
public class PlatformService {

  static final Map<BusinessStatus, String> STATUS_LABELS =
      Map.of(
          BusinessStatus.TRIAL, "En prueba",
          BusinessStatus.ACTIVE, "Activo",
          BusinessStatus.SUSPENDED, "Suspendido",
          BusinessStatus.CANCELLED, "Dado de baja");

  static final Map<BusinessPlan, String> PLAN_LABELS =
      Map.of(
          BusinessPlan.TRIAL, "Prueba",
          BusinessPlan.BASIC, "Básico",
          BusinessPlan.PRO, "Pro",
          BusinessPlan.ENTERPRISE, "Empresa");

  private final EntityManager em;
  private final BusinessRepository businessRepository;
  private final PlatformActionRepository actionRepository;
  private final PlatformAdminRepository adminRepository;
  private final BillingService billingService;

  @Transactional(readOnly = true)
  public List<BusinessSummaryResponse> findAll() {
    List<Business> businesses = businessRepository.findAll();
    Stats stats = stats();
    return businesses.stream()
        .sorted(Comparator.comparing(Business::getCreatedAt).reversed())
        .map(business -> summary(business, stats))
        .toList();
  }

  @Transactional(readOnly = true)
  public BusinessDetailResponse findOne(String id) {
    Business business = findOrThrow(id);
    List<BusinessDetailResponse.Member> users =
        em.createQuery(
                "select u.firstName, u.lastName, u.email, u.role, u.isActive from User u"
                    + " where u.businessId = :id order by u.createdAt asc",
                Object[].class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(
                row ->
                    new BusinessDetailResponse.Member(
                        (row[0] + " " + row[1]).trim(), (String) row[2], String.valueOf(row[3]), Boolean.TRUE.equals(row[4])))
            .toList();
    List<BusinessDetailResponse.Place> locations =
        em.createQuery(
                "select l.name, l.address, l.isActive from Location l where l.businessId = :id order by l.createdAt asc",
                Object[].class)
            .setParameter("id", id)
            .getResultList()
            .stream()
            .map(row -> new BusinessDetailResponse.Place((String) row[0], (String) row[1], Boolean.TRUE.equals(row[2])))
            .toList();

    Map<String, String> adminEmails = new HashMap<>();
    adminRepository.findAll().forEach(admin -> adminEmails.put(admin.getId(), admin.getEmail()));
    List<BusinessDetailResponse.HistoryEntry> history =
        actionRepository.findTop50ByBusinessIdOrderByCreatedAtDesc(id).stream()
            .map(
                action ->
                    new BusinessDetailResponse.HistoryEntry(
                        action.getAction(), action.getDetail(), adminEmails.get(action.getAdminId()), action.getCreatedAt()))
            .toList();

    return new BusinessDetailResponse(
        summary(business, stats()), business.getAdminNotes(), users, locations, history, billingService.status(id));
  }

  /**
   * Cambia estado, plan o notas. Suspender o dar de baja corta al instante
   * las sesiones del café ({@code SessionValidator} mira el estado en cada
   * request) y bloquea el login; reactivarlo lo deja entrar de nuevo.
   */
  @Transactional
  public BusinessDetailResponse update(String id, UpdateBusinessStatusRequest request, String adminId) {
    Business business = findOrThrow(id);
    List<PlatformAction> actions = new ArrayList<>();

    if (request.getStatus() != null && request.getStatus() != business.getStatus()) {
      String reason = blankToNull(request.getStatusReason());
      String detail = STATUS_LABELS.get(business.getStatus()) + " → " + STATUS_LABELS.get(request.getStatus());
      if (reason != null) detail += ": " + reason;
      business.setStatus(request.getStatus());
      // V16: un cambio a mano de Fran manda sobre la suspensión automática por falta de pago.
      business.setBillingSuspended(false);
      business.setStatusReason(
          request.getStatus() == BusinessStatus.SUSPENDED || request.getStatus() == BusinessStatus.CANCELLED ? reason : null);
      actions.add(action(adminId, id, "STATUS", detail));
    } else if (request.getStatusReason() != null && business.isBlocked()) {
      business.setStatusReason(blankToNull(request.getStatusReason()));
    }

    if (request.getPlan() != null && request.getPlan() != business.getPlan()) {
      actions.add(action(adminId, id, "PLAN", PLAN_LABELS.get(business.getPlan()) + " → " + PLAN_LABELS.get(request.getPlan())));
      business.setPlan(request.getPlan());
    }

    if (request.getAdminNotes() != null && !Objects.equals(blankToNull(request.getAdminNotes()), business.getAdminNotes())) {
      business.setAdminNotes(blankToNull(request.getAdminNotes()));
      actions.add(action(adminId, id, "NOTES", "Notas actualizadas"));
    }

    // V16 — cobro.
    if (request.getBillingExempt() != null) {
      String detail = billingService.setExempt(business, request.getBillingExempt());
      if (detail != null) actions.add(action(adminId, id, "BILLING", detail));
    }
    if (request.getExtendTrialDays() != null) {
      actions.add(action(adminId, id, "BILLING", billingService.extendTrial(business, request.getExtendTrialDays())));
    }

    businessRepository.save(business);
    actionRepository.saveAll(actions);
    return findOne(id);
  }

  public PlatformAdmin me(String adminId) {
    return adminRepository.findById(adminId).orElseThrow(() -> new NotFoundException("No se encontró tu cuenta."));
  }

  // ---------------------------------------------------------------------

  /** Números de todos los cafés, en unas pocas consultas agrupadas. */
  private record Stats(
      Map<String, Long> locations,
      Map<String, Long> activeUsers,
      Map<String, Long> recentOrders,
      Map<String, Instant> lastOrder,
      Map<String, String[]> owners,
      int graceDays) {}

  private Stats stats() {
    Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
    Map<String, Long> locations =
        counts("select l.businessId, count(l) from Location l group by l.businessId", null);
    Map<String, Long> users =
        counts("select u.businessId, count(u) from User u where u.isActive = true group by u.businessId", null);
    Map<String, Long> recent =
        counts("select o.businessId, count(o) from Order o where o.createdAt >= :since group by o.businessId", since);
    Map<String, Instant> last = new HashMap<>();
    for (Object[] row :
        em.createQuery("select o.businessId, max(o.createdAt) from Order o group by o.businessId", Object[].class)
            .getResultList()) {
      last.put((String) row[0], (Instant) row[1]);
    }
    Map<String, String[]> owners = new HashMap<>();
    for (Object[] row :
        em.createQuery(
                "select u.businessId, u.firstName, u.lastName, u.email from User u"
                    + " where u.role = com.cofeapp.backend.domain.enums.UserRole.OWNER order by u.createdAt asc",
                Object[].class)
            .getResultList()) {
      owners.putIfAbsent((String) row[0], new String[] {(row[1] + " " + row[2]).trim(), (String) row[3]});
    }
    return new Stats(locations, users, recent, last, owners, billingService.graceDays());
  }

  private Map<String, Long> counts(String jpql, Instant since) {
    var query = em.createQuery(jpql, Object[].class);
    if (since != null) query.setParameter("since", since);
    Map<String, Long> result = new HashMap<>();
    for (Object[] row : query.getResultList()) result.put((String) row[0], ((Number) row[1]).longValue());
    return result;
  }

  private static BusinessSummaryResponse summary(Business business, Stats stats) {
    BillingState billing = BillingState.of(business, stats.graceDays(), Instant.now());
    String[] owner = stats.owners().getOrDefault(business.getId(), new String[] {null, null});
    return new BusinessSummaryResponse(
        business.getId(),
        business.getLegalName(),
        business.getTradeName(),
        business.getTaxId(),
        business.getFiscalCondition(),
        business.getPlan(),
        business.getStatus(),
        business.getStatusReason(),
        business.getCreatedAt(),
        owner[0],
        owner[1],
        stats.locations().getOrDefault(business.getId(), 0L),
        stats.activeUsers().getOrDefault(business.getId(), 0L),
        stats.recentOrders().getOrDefault(business.getId(), 0L),
        stats.lastOrder().get(business.getId()),
        billing.state(),
        billing.accessUntil(),
        billing.suspendsAt(),
        Boolean.TRUE.equals(business.getBillingExempt()),
        business.getMpPreapprovalStatus());
  }

  private static PlatformAction action(String adminId, String businessId, String action, String detail) {
    PlatformAction entry = new PlatformAction();
    entry.setAdminId(adminId);
    entry.setBusinessId(businessId);
    entry.setAction(action);
    entry.setDetail(detail.length() > 500 ? detail.substring(0, 500) : detail);
    return entry;
  }

  private Business findOrThrow(String id) {
    return businessRepository.findById(id).orElseThrow(() -> new NotFoundException("No se encontró el café " + id + "."));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
