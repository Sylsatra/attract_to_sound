package com.example.soundattract.spatial;

import com.example.soundattract.spatial.SpatialContracts.InterestTicket;
import com.example.soundattract.spatial.SpatialContracts.TileKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InterestRegistry {
    private final int maxTickets;
    private final Map<UUID, InterestTicket> ticketsById = new LinkedHashMap<>();
    private final Map<String, UUID> ownerIndex = new HashMap<>();
    private long currentTick;
    private long currentEpoch;

    public InterestRegistry(int maxTickets) {
        this.maxTickets = Math.max(1, maxTickets);
    }

    public synchronized InterestTicket register(String ownerKey, String interestType, TileKey tileKey, long ttlTicks) {
        evictExpired(currentTick);
        UUID ticketId = UUID.nameUUIDFromBytes((ownerKey + "|" + interestType + "|" + currentTick + "|" + currentEpoch).getBytes());
        InterestTicket ticket = new InterestTicket(ticketId, ownerKey, interestType, tileKey, currentTick, currentTick + Math.max(1L, ttlTicks), currentEpoch);
        cancel(ownerKey);
        ticketsById.put(ticketId, ticket);
        ownerIndex.put(ownerKey, ticketId);
        evictOverflow();
        return ticket;
    }

    public synchronized void cancel(String ownerKey) {
        if (ownerKey == null) {
            return;
        }
        UUID ticketId = ownerIndex.remove(ownerKey);
        if (ticketId != null) {
            ticketsById.remove(ticketId);
        }
    }

    public synchronized void tick(long gameTick) {
        currentTick = gameTick;
        evictExpired(gameTick);
    }

    public synchronized void invalidateEpoch() {
        currentEpoch++;
        ticketsById.clear();
        ownerIndex.clear();
    }

    public synchronized List<InterestTicket> snapshot() {
        ArrayList<InterestTicket> snapshot = new ArrayList<>(ticketsById.values());
        snapshot.sort(Comparator.comparingLong(InterestTicket::createdTick).thenComparing(t -> t.ticketId().toString()));
        return List.copyOf(snapshot);
    }

    public synchronized int size() {
        return ticketsById.size();
    }

    private void evictExpired(long tick) {
        ticketsById.entrySet().removeIf(entry -> {
            InterestTicket ticket = entry.getValue();
            boolean expired = ticket.expiresTick() <= tick || ticket.epoch() != currentEpoch;
            if (expired) {
                ownerIndex.remove(ticket.ownerKey(), ticket.ticketId());
            }
            return expired;
        });
    }

    private void evictOverflow() {
        while (ticketsById.size() > maxTickets) {
            UUID oldest = ticketsById.keySet().iterator().next();
            InterestTicket removed = ticketsById.remove(oldest);
            if (removed != null) {
                ownerIndex.remove(removed.ownerKey(), removed.ticketId());
            }
        }
    }
}