import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api";
import type { DiscoverCategories, SearchResult } from "../types";
import { MediaRow } from "../components/MediaRow";
import { RequestButton, type ReqState } from "../components/RequestButton";
import { EmptyState, Spinner } from "../components/States";

export function Discover() {
  const [searchParams, setSearchParams] = useSearchParams();
  // Filter state lives in the URL so navigating away and back restores the selection.
  const genre = searchParams.get("genre");
  const decadeParam = searchParams.get("decade");
  const decade = decadeParam ? parseInt(decadeParam, 10) : null;

  const [items, setItems] = useState<SearchResult[]>([]);
  const [cats, setCats] = useState<DiscoverCategories>({ genres: [], decades: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reqState, setReqState] = useState<Record<string, ReqState>>({});

  const filtered = genre !== null || decade !== null;

  const setFilter = (g: string | null, d: number | null) => {
    const params: Record<string, string> = {};
    if (g) params.genre = g;
    if (d != null) params.decade = String(d);
    setSearchParams(params, { replace: true });
  };

  useEffect(() => {
    let alive = true;
    setLoading(true);
    Promise.all([api.discover(genre, decade), api.discoverCategories(genre, decade)])
      .then(([list, c]) => {
        if (!alive) return;
        setItems(list);
        setCats(c);
        setError(null);
      })
      .catch((e: unknown) => alive && setError(e instanceof Error ? e.message : "Couldn't load Discover"))
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [genre, decade]);

  const request = async (r: SearchResult) => {
    setReqState((s) => ({ ...s, [r.foreignId]: "loading" }));
    try {
      const res = await api.request({ type: "album", foreignId: r.foreignId });
      setReqState((s) => ({ ...s, [r.foreignId]: res.status === "error" ? "error" : "done" }));
    } catch {
      setReqState((s) => ({ ...s, [r.foreignId]: "error" }));
    }
  };

  return (
    <>
      <div className="chips" style={{ marginBottom: 14 }}>
        <button className={"chip" + (!filtered ? " on" : "")} onClick={() => setFilter(null, null)}>
          New
        </button>
        {cats.decades.map((d) => (
          <button key={"d" + d} className={"chip" + (decade === d ? " on" : "")} onClick={() => setFilter(genre, decade === d ? null : d)}>
            {d}s
          </button>
        ))}
        {cats.genres.map((g) => (
          <button key={"g" + g} className={"chip" + (genre === g ? " on" : "")} onClick={() => setFilter(genre === g ? null : g, decade)}>
            {g}
          </button>
        ))}
      </div>

      {loading ? (
        <Spinner />
      ) : error && items.length === 0 ? (
        <EmptyState icon="☁️" title="Couldn't load Discover" message={error} />
      ) : items.length === 0 ? (
        <EmptyState
          icon="🧭"
          title={filtered ? "Nothing in this category" : "Nothing new yet"}
          message={
            filtered
              ? "No unowned albums here right now. Try another genre or decade."
              : "New releases from artists in your library will show up here."
          }
        />
      ) : (
        <div className="card stack" style={{ padding: 0 }}>
          {items.map((a) => (
            <MediaRow
              key={a.foreignId}
              imageUrl={a.imageUrl}
              title={a.title}
              subtitle={[a.artist, a.year].filter(Boolean).join(" · ")}
              trailing={
                a.inLibrary ? (
                  <span className="statuschip available">In library</span>
                ) : (
                  <RequestButton
                    inLibrary={false}
                    requested={a.requested}
                    state={reqState[a.foreignId] ?? "idle"}
                    onClick={() => request(a)}
                  />
                )
              }
            />
          ))}
        </div>
      )}
    </>
  );
}
