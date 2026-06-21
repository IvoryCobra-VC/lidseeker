import { useEffect, useRef, useState } from "react";
import { api } from "../api";
import type { MusicRequest, ServiceLink } from "../types";
import { MediaRow } from "../components/MediaRow";
import { StatusChip } from "../components/StatusChip";
import { Pipeline } from "../components/Pipeline";
import { EmptyState, Spinner } from "../components/States";

export function Requests() {
  const [items, setItems] = useState<MusicRequest[]>([]);
  const [services, setServices] = useState<ServiceLink[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [retryingId, setRetryingId] = useState<number | null>(null);
  const [searching, setSearching] = useState(false);
  const [searchMessage, setSearchMessage] = useState<string | null>(null);
  const [confirmRemove, setConfirmRemove] = useState<MusicRequest | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const first = useRef(true);

  const errText = (e: unknown, fallback: string) =>
    e instanceof Error && e.message ? e.message : fallback;

  const refresh = async () => {
    try {
      const list = await api.requests();
      setItems(list);
      setError(null);
    } catch (e: any) {
      setError(e.message || "Couldn't load requests");
    } finally {
      if (first.current) {
        setLoading(false);
        first.current = false;
      }
    }
  };

  useEffect(() => {
    api.services().then(setServices).catch(() => {});
    refresh();
    const t = setInterval(refresh, 8000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Auto-dismiss transient retry/remove feedback.
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
    } catch (e) {
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
    } catch (e) {
      setActionMessage(`Couldn't remove: ${errText(e, "try again")}`);
    }
    refresh();
  };

  const searchNow = async () => {
    setSearching(true);
    setSearchMessage(null);
    try {
      const res = await api.searchNow();
      setSearchMessage(res.message ?? "Searching now…");
    } catch {
      setSearchMessage("Couldn't start a search right now.");
    } finally {
      setSearching(false);
      refresh();
    }
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
      {actionMessage && <div className="toast">{actionMessage}</div>}
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
                  searching={searching}
                  searchMessage={searchMessage}
                  onRetry={() => retry(req.id)}
                  onSearchNow={searchNow}
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
              “{confirmRemove.title || confirmRemove.foreignId}” will be removed from your requests and
              unmonitored in Lidarr. This can’t be undone.
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
