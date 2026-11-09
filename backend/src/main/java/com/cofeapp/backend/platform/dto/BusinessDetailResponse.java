package com.cofeapp.backend.platform.dto;

import com.cofeapp.backend.billing.dto.BillingStatusResponse;
import java.time.Instant;
import java.util.List;

/** Detalle de un café en el panel de superadmin (V15): lo de la lista + equipo, locales, notas e historial. */
public record BusinessDetailResponse(
    BusinessSummaryResponse summary,
    String adminNotes,
    List<Member> users,
    List<Place> locations,
    List<HistoryEntry> history,
    // V16 — la suscripción del café (lo mismo que ve el dueño en su pantalla).
    BillingStatusResponse billing) {

  public record Member(String name, String email, String role, boolean isActive) {}

  public record Place(String name, String address, boolean isActive) {}

  public record HistoryEntry(String action, String detail, String adminEmail, Instant createdAt) {}
}
