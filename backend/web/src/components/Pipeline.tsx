import type { Pipeline as PipelineT, ServiceLink } from "../types";

// Only render service links that are real http(s) URLs — a SERVICE_LINKS entry
// with a javascript:/data: scheme must never become a live href.
function safeHref(url: string): string | null {
  try {
    const u = new URL(url);
    return u.protocol === "http:" || u.protocol === "https:" ? url : null;
  } catch {
    return null;
  }
}

const STAGE_LABELS: Record<string, string> = {
  requested: "Requested",
  searching: "Searching",
  downloading: "Downloading",
  importing: "Importing",
  available: "Available",
};

export function Pipeline({
  pipeline,
  services,
  onRetry,
  onSearchNow,
  onRemove,
  retrying,
  searching,
}: {
  pipeline: PipelineT;
  services: ServiceLink[];
  onRetry: () => void;
  onSearchNow: () => void;
  onRemove: () => void;
  retrying: boolean;
  searching: boolean;
}) {
  const showBar =
    pipeline.stage === "downloading" ||
    pipeline.stage === "importing" ||
    (pipeline.percent > 0 && pipeline.percent < 100);
  const canSearch = pipeline.stage !== "available" && !pipeline.failed && !pipeline.stuck;
  const isAvailable = pipeline.stage === "available";

  return (
    <div className="pipeline">
      <div className="stepper">
        {pipeline.stages.map((key, i) => {
          const cls = i < pipeline.stageIndex ? "done" : i === pipeline.stageIndex ? "current" : "";
          return (
            <div className={`step ${cls}`} key={key}>
              <div className="dot">{i < pipeline.stageIndex ? "✓" : i + 1}</div>
              <div className="label">{STAGE_LABELS[key] ?? key}</div>
            </div>
          );
        })}
      </div>

      {pipeline.detail && (
        <div className={"detail" + (pipeline.failed ? " fail" : "")}>{pipeline.detail}</div>
      )}

      {showBar && (
        <div className="bar">
          <div style={{ width: `${Math.min(100, Math.max(0, pipeline.percent))}%` }} />
        </div>
      )}

      {/* Service links shown prominently when available, inline otherwise */}
      {isAvailable && services.length > 0 && (
        <div className="linkchips" style={{ marginTop: 10 }}>
          <span className="muted" style={{ alignSelf: "center", fontSize: 12 }}>Play in</span>
          {services.map((s) => {
            const href = safeHref(s.url);
            if (!href) return null;
            return (
              <a className="chip on" key={s.name} href={href} target="_blank" rel="noreferrer">
                {s.name} ↗
              </a>
            );
          })}
        </div>
      )}

      <div className="btnrow">
        {(pipeline.failed || pipeline.stuck) && (
          <button className="btn small primary" onClick={onRetry} disabled={retrying}>
            {retrying ? "Retrying…" : "Retry"}
          </button>
        )}
        {canSearch && (
          <button className="btn small" onClick={onSearchNow} disabled={searching}>
            {searching ? "Starting…" : "Search now"}
          </button>
        )}
        <button className="btn small danger" onClick={onRemove}>
          Remove
        </button>
      </div>

      {!isAvailable && services.length > 0 && (
        <div className="linkchips">
          <span className="muted" style={{ alignSelf: "center", fontSize: 12 }}>Open in</span>
          {services.map((s) => {
            const href = safeHref(s.url);
            if (!href) return null;
            return (
              <a className="chip" key={s.name} href={href} target="_blank" rel="noreferrer">
                {s.name} ↗
              </a>
            );
          })}
        </div>
      )}
    </div>
  );
}
