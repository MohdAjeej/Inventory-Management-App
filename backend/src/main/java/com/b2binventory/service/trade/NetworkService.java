package com.b2binventory.service.trade;

import com.b2binventory.dto.TradeDtos.*;
import com.b2binventory.entity.Business;
import com.b2binventory.entity.BusinessConnection;
import com.b2binventory.entity.ConnectionStatus;
import com.b2binventory.exception.BadRequestException;
import com.b2binventory.exception.ConflictException;
import com.b2binventory.exception.ForbiddenException;
import com.b2binventory.exception.NotFoundException;
import com.b2binventory.repository.BusinessConnectionRepository;
import com.b2binventory.repository.BusinessRepository;
import com.b2binventory.security.CurrentUser;
import com.b2binventory.security.Permission;
import com.b2binventory.security.Permissions;
import com.b2binventory.service.AuditService;
import com.b2binventory.service.notification.NotificationService;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Finding other businesses on the platform and connecting with them so they can trade. */
@Service
public class NetworkService {

    public static final String CONNECTION_REQUEST = "CONNECTION_REQUEST";
    public static final String CONNECTION_ACCEPTED = "CONNECTION_ACCEPTED";

    private static final Set<ConnectionStatus> LIVE = EnumSet.of(ConnectionStatus.PENDING, ConnectionStatus.ACCEPTED);

    private final BusinessConnectionRepository connections;
    private final BusinessRepository businesses;
    private final NotificationService notifications;
    private final AuditService audit;
    private final EntityManager em;

    public NetworkService(BusinessConnectionRepository connections, BusinessRepository businesses,
                          NotificationService notifications, AuditService audit, EntityManager em) {
        this.connections = connections;
        this.businesses = businesses;
        this.notifications = notifications;
        this.audit = audit;
        this.em = em;
    }

    // ------------------------------------------------------------------ search

    /**
     * Finds businesses by name, exact business code or exact phone number. Returns only what is needed to
     * recognise a business (name, type, city, code), never contact details or anything about its inventory.
     */
    @Transactional(readOnly = true)
    public List<SearchResult> search(String query) {
        requireView();
        Long me = CurrentUser.get().businessId();
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) throw new BadRequestException("Validation failed", Map.of("q", "Type at least 2 characters"));
        String digits = q.replaceAll("[^0-9]", "");
        List<Business> found = em.createQuery("select b from Business b where b.id <> ?1 and b.discoverable = true and ("
                        + "lower(b.name) like ?2 or upper(b.businessCode) = ?3 "
                        + "or (?4 <> '' and length(?4) >= 6 and function('regexp_replace', coalesce(b.phone, ''), '[^0-9]', '', 'g') = ?4)) "
                        + "order by case when lower(b.name) like ?5 then 0 else 1 end, lower(b.name)", Business.class)
                .setParameter(1, me).setParameter(2, "%" + q.toLowerCase(Locale.ROOT) + "%")
                .setParameter(3, q.toUpperCase(Locale.ROOT)).setParameter(4, digits)
                .setParameter(5, q.toLowerCase(Locale.ROOT) + "%")
                .setMaxResults(20).getResultList();
        List<SearchResult> results = new ArrayList<>();
        for (Business b : found) {
            Optional<BusinessConnection> c = connections.findBetween(me, b.getId(), LIVE);
            results.add(new SearchResult(card(b), c.map(BusinessConnection::getId).orElse(null),
                    c.map(BusinessConnection::getStatus).orElse(null), c.map(x -> direction(x, me)).orElse(null)));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public MyBusinessCode myCode() {
        Business b = business(CurrentUser.get().businessId());
        return new MyBusinessCode(b.getBusinessCode(), b.getName(), b.isDiscoverable());
    }

    @Transactional
    public MyBusinessCode setDiscoverable(boolean discoverable) {
        Permissions.require(Permission.BUSINESS_SETTINGS);
        CurrentUser me = CurrentUser.get();
        Business b = business(me.businessId());
        b.setDiscoverable(discoverable);
        audit.record(me.businessId(), me.membershipId(), "NETWORK_DISCOVERABLE", "Business", b.getId(),
                Map.of("discoverable", discoverable));
        return new MyBusinessCode(b.getBusinessCode(), b.getName(), b.isDiscoverable());
    }

    // ------------------------------------------------------------------ connections

    /** status: null for everything live (pending and accepted), or one status. */
    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(ConnectionStatus status) {
        requireView();
        Long me = CurrentUser.get().businessId();
        Set<ConnectionStatus> statuses = status == null ? LIVE : EnumSet.of(status);
        List<BusinessConnection> list = connections.findForBusiness(me, statuses);
        Map<Long, Business> others = businessesById(list.stream().map(c -> c.otherThan(me)).toList());
        return list.stream().map(c -> response(c, me, others.get(c.otherThan(me)))).toList();
    }

    @Transactional
    public ConnectionResponse request(ConnectionRequest r) {
        Permissions.require(Permission.BUSINESS_SETTINGS);
        CurrentUser me = CurrentUser.get();
        if (r.businessId().equals(me.businessId())) throw new BadRequestException("You cannot connect with your own business");
        Business other = businesses.findById(r.businessId())
                .filter(Business::isDiscoverable)
                .orElseThrow(() -> new NotFoundException("Business", r.businessId()));

        Optional<BusinessConnection> existing = connections.findBetween(me.businessId(), other.getId(), LIVE);
        if (existing.isPresent()) {
            BusinessConnection c = existing.get();
            if (c.getStatus() == ConnectionStatus.ACCEPTED) throw new ConflictException("You are already connected with " + other.getName());
            // They already asked us: asking back simply accepts their request.
            if (c.getAddresseeBusinessId().equals(me.businessId())) return accept(c.getId());
            throw new ConflictException("A request to " + other.getName() + " is already waiting for an answer");
        }

        BusinessConnection c = new BusinessConnection();
        c.setRequesterBusinessId(me.businessId());
        c.setAddresseeBusinessId(other.getId());
        c.setMessage(blankToNull(r.message()));
        c.setRequestedBy(me.membershipId());
        try {
            connections.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A connection with " + other.getName() + " already exists");
        }
        Business mine = business(me.businessId());
        notifications.notify(other.getId(), null, CONNECTION_REQUEST, mine.getName() + " wants to connect",
                "Accept to send and receive invoices with " + mine.getName() + ".",
                Map.of("connectionId", c.getId(), "businessId", mine.getId()), "CONN:" + c.getId(), Duration.ofDays(30));
        audit.record(me.businessId(), me.membershipId(), "CONNECTION_REQUESTED", "BusinessConnection", c.getId(),
                Map.of("businessId", other.getId()));
        return response(c, me.businessId(), other);
    }

    @Transactional
    public ConnectionResponse accept(Long id) {
        return respond(id, true);
    }

    @Transactional
    public ConnectionResponse reject(Long id) {
        return respond(id, false);
    }

    /** Either side can remove a connection; the requester can also withdraw a pending request. */
    @Transactional
    public ConnectionResponse remove(Long id) {
        Permissions.require(Permission.BUSINESS_SETTINGS);
        CurrentUser me = CurrentUser.get();
        BusinessConnection c = connections.findForBusiness(id, me.businessId())
                .orElseThrow(() -> new NotFoundException("Connection", id));
        if (!LIVE.contains(c.getStatus())) throw new ConflictException("This connection is already closed");
        if (c.getStatus() == ConnectionStatus.PENDING && !c.getRequesterBusinessId().equals(me.businessId())) {
            throw new ConflictException("Accept or reject this request instead");
        }
        c.setStatus(ConnectionStatus.REMOVED);
        c.setRespondedBy(me.membershipId());
        c.setRespondedAt(Instant.now());
        audit.record(me.businessId(), me.membershipId(), "CONNECTION_REMOVED", "BusinessConnection", c.getId(), Map.of());
        return response(c, me.businessId(), business(c.otherThan(me.businessId())));
    }

    private ConnectionResponse respond(Long id, boolean accept) {
        Permissions.require(Permission.BUSINESS_SETTINGS);
        CurrentUser me = CurrentUser.get();
        BusinessConnection c = connections.findForBusiness(id, me.businessId())
                .orElseThrow(() -> new NotFoundException("Connection", id));
        if (!c.getAddresseeBusinessId().equals(me.businessId())) {
            throw new ForbiddenException("Only the business that received the request can answer it");
        }
        if (c.getStatus() != ConnectionStatus.PENDING) throw new ConflictException("This request has already been answered");
        c.setStatus(accept ? ConnectionStatus.ACCEPTED : ConnectionStatus.REJECTED);
        c.setRespondedBy(me.membershipId());
        c.setRespondedAt(Instant.now());
        Business mine = business(me.businessId());
        if (accept) {
            notifications.notify(c.getRequesterBusinessId(), null, CONNECTION_ACCEPTED, mine.getName() + " accepted your request",
                    "You can now send invoices to and receive invoices from " + mine.getName() + ".",
                    Map.of("connectionId", c.getId(), "businessId", mine.getId()), "CONN-OK:" + c.getId(), Duration.ofDays(30));
        }
        audit.record(me.businessId(), me.membershipId(), accept ? "CONNECTION_ACCEPTED" : "CONNECTION_REJECTED",
                "BusinessConnection", c.getId(), Map.of());
        return response(c, me.businessId(), business(c.getRequesterBusinessId()));
    }

    // ------------------------------------------------------------------ used by trade invoices

    /** Throws unless the two businesses have an accepted connection. */
    public void requireConnected(Long a, Long b) {
        boolean ok = connections.findBetween(a, b, EnumSet.of(ConnectionStatus.ACCEPTED)).isPresent();
        if (!ok) throw new ConflictException("You are not connected with this business. Connect first in Business network.");
    }

    public long connectedCount(Long businessId) {
        return connections.findForBusiness(businessId, EnumSet.of(ConnectionStatus.ACCEPTED)).size();
    }

    public long incomingCount(Long businessId) {
        return connections.countIncoming(businessId);
    }

    /** Businesses whose name contains the text; used only to filter this business's own invoices by party. */
    public List<Business> searchByName(String lowerCaseText) {
        return em.createQuery("select b from Business b where lower(b.name) like ?1", Business.class)
                .setParameter(1, "%" + lowerCaseText + "%").setMaxResults(200).getResultList();
    }

    public Business business(Long id) {
        return businesses.findById(id).orElseThrow(() -> new NotFoundException("Business", id));
    }

    public Map<Long, Business> businessesById(Collection<Long> ids) {
        Map<Long, Business> map = new HashMap<>();
        businesses.findAllById(new HashSet<>(ids)).forEach(b -> map.put(b.getId(), b));
        return map;
    }

    public static BusinessCard card(Business b) {
        return new BusinessCard(b.getId(), b.getName(), b.getBusinessType(), b.getCity(), b.getBusinessCode());
    }

    public static PartyDetails party(Business b) {
        return new PartyDetails(b.getId(), b.getName(), b.getBusinessType(), b.getBusinessCode(), b.getPhone(), b.getEmail(),
                b.getAddress(), b.getCity(), b.getState(), b.getPostalCode(), b.getTaxId());
    }

    private static String direction(BusinessConnection c, Long me) {
        return c.getRequesterBusinessId().equals(me) ? "OUTGOING" : "INCOMING";
    }

    private static ConnectionResponse response(BusinessConnection c, Long me, Business other) {
        return new ConnectionResponse(c.getId(), c.getStatus(), direction(c, me), card(other), c.getMessage(),
                c.getCreatedAt(), c.getRespondedAt());
    }

    private static void requireView() {
        Permissions.requireAny(Permission.SALE_RECORD, Permission.PURCHASE_MANAGE, Permission.BUSINESS_SETTINGS);
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
