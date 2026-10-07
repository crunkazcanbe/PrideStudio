package com.dogpound.pridestudio.edit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each player's undo / redo list (last 50 edits). */
public final class History {
    private History() {}
    private static final Map<UUID, List<EditSession>> DONE = new HashMap<>(), UNDONE = new HashMap<>();
    private static final int KEEP = 50;

    public static void push(UUID p, EditSession s) {
        if (s.size() == 0) return;
        List<EditSession> d = DONE.computeIfAbsent(p, k -> new ArrayList<>());
        d.add(s);
        while (d.size() > KEEP) d.remove(0);
        UNDONE.computeIfAbsent(p, k -> new ArrayList<>()).clear();
    }

    private static final Map<UUID, Integer> STROKE = new HashMap<>();

    /** Editor-mode brush dabs: every dab with the same stroke id (one mouse drag) joins one undo entry. */
    public static void pushStroke(UUID p, EditSession s, int stroke) {
        if (s.size() == 0) return;
        List<EditSession> d = DONE.get(p);
        Integer last = STROKE.get(p);
        if (stroke != 0 && last != null && last == stroke && d != null && !d.isEmpty()) {
            d.get(d.size() - 1).absorb(s);
            return;
        }
        push(p, s);
        STROKE.put(p, stroke);
    }

    public static EditSession undo(UUID p) {
        STROKE.remove(p);
        List<EditSession> d = DONE.get(p);
        if (d == null || d.isEmpty()) return null;
        EditSession s = d.remove(d.size() - 1);
        s.undo();
        UNDONE.computeIfAbsent(p, k -> new ArrayList<>()).add(s);
        return s;
    }

    public static EditSession redo(UUID p) {
        List<EditSession> u = UNDONE.get(p);
        if (u == null || u.isEmpty()) return null;
        EditSession s = u.remove(u.size() - 1);
        s.redo();
        DONE.computeIfAbsent(p, k -> new ArrayList<>()).add(s);
        return s;
    }

    public static int redoCount(UUID p) { return UNDONE.getOrDefault(p, new ArrayList<>()).size(); }
    public static void clear(UUID p) { DONE.remove(p); UNDONE.remove(p); }

    public static List<String> labels(UUID p) {
        List<String> out = new ArrayList<>();
        for (EditSession s : DONE.getOrDefault(p, new ArrayList<>())) out.add(s.label + " (" + s.size() + ")");
        return out;
    }
}
