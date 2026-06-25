import { useEffect, useRef, useState, useCallback } from "react";
import { api, getToken } from "../api";
import type { MusicRequest, ServiceLink, StatsOut } from "../types";
import { MediaRow } from "../components/MediaRow";
import { StatusChip } from "../components/StatusChip";
import { Pipeline } from "../components/Pipeline";
import { EmptyState, Spinner } from "../components/States";

export function Requests() {
  const [items, setItems] = useState<MusicRequest[]>([]);
  const [services, setServices] = useState<ServiceLink[]>([]);
  const [stats, setStats] = useState<StatsOut | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [searchingId, setSearchingId] = useState<number | null>(null);
  const [clearing, setClearing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState<MusicRequest | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const first = useRef(true);

  const errText = (e: unknown, fallback: string) =>
    e instanceof Error && e.message ? e.message : fallback;

  const refresh = useCallback(async () => {
    try {
      const [list, s] = await Promise.all([api.requests(), api.stats()]);
      setItems(list);
      setStats(s);
      setError(null);
    } catch (e: unknown) {
      setError(errText(e, "Couldn't load requests"));
    } finally {
      if (first.current) {
        setLoading(false);
        first.current = false;
      }
    }
  }, []);

  useEffect(() => {
    api.services().then(setServices).catch(() => {});
    refresh();

    // SSE: instant updates when request statuses change.
    const token = getToken();
    const es = new EventSource(`/api/events?token=${encodeURIComponent(token ?? "")}`);
    es.addEventListener("status_change", () => { refresh(); });
    es.addEventListener("requests_cleared", () => { refresh(); });
    // On SSE error fall back to polling — the interval below covers it.
    es.onerror = () => { es.close(); };

    // Fallback poll every 15s (reduced from 8s since SSE handles most updates).
    const t = setInterval(refresh, 15000);
    return () => {
      clearInterval(t);
      es.close();
    };
  }, [refresh]);

  // Auto-dismiss transient feedback.
  useEffect(() => {
    if (!actionMessage) return;
    const t = setTimeout(() => setActionMessage(null), 4000);
    return () => clearTimeout(t);
  }, [actionMessage]);

  const retry = async (id: number) => {
    setRetryingId(id);
    try {
      const res = await api.retry(id);
      setActionMessage(res.message ?? "Retrying…");
    } catch (e: unknown) {
      setActionMessage(`Couldn't retry: ${errText(e, "try again")}`);
    }
    setRetryingId(null);
    refresh();
  };

  const remove = async (id: number) => {
    setConfirmRemove(null);
    try {
      const res = await api.deleteRequest(id);
      setActionMessage(res.message ?? "Removed.");
    } catch (e: unknown) {
      setActionMessage(`Couldn't remove: ${errText(e, "try again")}`);
    }
    refresh();
  };

  const searchNow = async (id: number) => {
    setSearchingId(id);
    try {
      const res = await api.searchRequestNow(id);
      setActionMessage(res.message ?? "Searching now…");
    } catch {
      setActionMessage("Couldn't start a search right now.");
    } finally {
      setSearchingId(null);
      refresh();
    }
  };

  const clearAvailable = async () => {
    setClearing(true);
    try {
      const res = await api.clearAvailable();
      setActionMessage(res.message ?? "Cleared.");
    } catch (e: unknown) {
      setActionMessage(`Couldn't clear: ${errText(e, "try again")}`);
    }
    setClearing(false);
    refresh();
  };

  if (loading) return <Spinner />;
  if (error && items.length === 0) return <EmptyState icon="☁️" title="Couldn't load requests" message={error} />;
  if (items.length === 0)
    return (
      <EmptyState
        icon="📥"
        title="No requests yet"
        message="Albums and artists you request will appear here, with live download status."
      />
    );

  return (
    <>
      {actionMessage && <div className="toast" style={{ marginBottom: 10 }}>{actionMessage}</div>}

      {stats && (
        <div className="stats-bar">
          {stats.available > 0 && <span className="stat available">{stats.available} available</span>}
          {stats.downloading > 0 && <span className="stat downloading">{stats.downloading} downloading</span>}
          {stats.pending > 0 && <span className="stat pending">{stats.pending} pending</span>}
          {stats.failed > 0 && <span className="stat failed">{stats.failed} failed</span>}
          {stats.available > 0 && (
            <button
              className="btn small ghost"
              onClick={clearAvailable}
              disabled={clearing}
              style={{ marginLeft: "auto" }}
            >
              {clearing ? "Clearing…" : `Clear ${stats.available} fulfilled`}
            </button>
          )}
        </div>
      )}

      <div className="stack">
        {items.map((req) => {
          const subtitle = [
            req.artist,
            req.type === "artist" ? "Full discography" : null,
            req.requestedBy ? `by ${req.requestedBy}` : null,
          ]
            .filter(Boolean)
            .join(" · ");
          const isOpen = expanded === req.id;
          return (
            <div className="card" key={req.id}>
              <MediaRow
                imageUrl={req.imageUrl}
                title={req.title || req.foreignId}
                subtitle={subtitle}
                onClick={() => setExpanded(isOpen ? null : req.id)}
                trailing={<StatusChip status={req.status} />}
              />
              {isOpen && req.pipeline && (
                <Pipeline
                  pipeline={req.pipeline}
                  services={services}
                  retrying={retryingId === req.id}
                  searching={searchingId === req.id}
                  onRetry={() => retry(req.id)}
                  onSearchNow={() => searchNow(req.id)}
                  onRemove={() => setConfirmRemove(req)}
                />
              )}
            </div>
          );
        })}
      </div>

      {confirmRemove && (
        <div className="backdrop" onClick={() => setConfirmRemove(null)}>
          <div className="dialog" onClick={(e) => e.stopPropagation()}>
            <h3>Remove request?</h3>
            <p className="muted">
              "{confirmRemove.title || confirmRemove.foreignId}" will be removed from your requests and
              unmonitored in Lidarr. This can't be undone.
            </p>
            <div className="actions">
              <button className="btn ghost" onClick={() => setConfirmRemove(null)}>
                Cancel
              </button>
              <button className="btn danger" onClick={() => remove(confirmRemove.id)}>
                Remove
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
