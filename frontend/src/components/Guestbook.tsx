import { useEffect, useState, type FormEvent } from "react";
import { MessageSquare, PenLine, RefreshCw } from "lucide-react";
import { api } from "../api";
import { copy } from "../copy";
import "./guestbook.css";

type Entry = {
  id: string;
  name: string;
  message: string;
  createdAt: string;
  mine: boolean;
  hidden?: boolean;
};
type Feed = { entries: Entry[]; total: number; page: number };

function useGuestbookFeed(owner = false) {
  const [data, setData] = useState<Feed | null>(null);
  const [page, setPage] = useState(0);
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  useEffect(() => {
    let live = true;
    setLoading(true);
    setError("");
    api((owner ? "/owner/guestbook" : "/guestbook") + "?page=" + page)
      .then((feed: Feed) => {
        if (!live) return;
        if (!feed.entries.length && page > 0) setPage(page - 1);
        else setData(feed);
      })
      .catch((e: Error) => {
        if (live) setError(e.message);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [owner, page, version]);
  return {
    data,
    page,
    setPage,
    loading,
    error,
    reload: () => setVersion((v) => v + 1),
  };
}

function MessageDate({ value }: { value: string }) {
  return (
    <time dateTime={value}>
      {new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))}
    </time>
  );
}

function Pagination({ feed }: { feed: ReturnType<typeof useGuestbookFeed> }) {
  if (!feed.data || (!feed.page && feed.data.total <= 10)) return null;
  return (
    <nav className="guestbook-pagination" aria-label={copy.guestbookPagination}>
      <button
        className="secondary"
        disabled={feed.loading || feed.page === 0}
        onClick={() => feed.setPage(feed.page - 1)}
      >
        {copy.guestbookNewer}
      </button>
      <span>
        {copy.guestbookPage} {feed.page + 1}
      </span>
      <button
        className="secondary"
        disabled={feed.loading || (feed.page + 1) * 10 >= feed.data.total}
        onClick={() => feed.setPage(feed.page + 1)}
      >
        {copy.guestbookOlder}
      </button>
    </nav>
  );
}

export function Guestbook({
  embedded = false,
  initialName = "",
}: {
  embedded?: boolean;
  initialName?: string;
}) {
  const feed = useGuestbookFeed();
  const [name, setName] = useState(
    initialName === "Community guest" ? "" : initialName,
  );
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [removing, setRemoving] = useState("");
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const headingId = embedded ? "home-guestbook-title" : "guestbook-title";
  async function sign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setStatus("");
    try {
      await api("/guestbook", "POST", {
        name: name.trim(),
        message: message.trim(),
      });
      setMessage("");
      setStatus(copy.guestbookPosted);
      feed.setPage(0);
      feed.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function remove(entry: Entry) {
    setRemoving(entry.id);
    setError("");
    setStatus("");
    try {
      await api("/guestbook/" + entry.id, "DELETE");
      setStatus(copy.guestbookRemoved);
      feed.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setRemoving("");
    }
  }
  return (
    <section
      id="guestbook"
      className={
        "guestbook " + (embedded ? "guestbook-embedded" : "guestbook-page")
      }
      aria-labelledby={headingId}
    >
      <div className="guestbook-intro">
        <MessageSquare size={26} aria-hidden="true" />
        <div>
          {embedded ? (
            <h2 id={headingId}>{copy.guestbookTitle}</h2>
          ) : (
            <h1 id={headingId}>{copy.guestbookTitle}</h1>
          )}
          <p>{copy.guestbookIntro}</p>
        </div>
      </div>
      <div className="guestbook-layout">
        <form
          className="guestbook-form"
          onSubmit={sign}
          aria-label={copy.guestbookSign}
        >
          <h3>{copy.guestbookFormTitle}</h3>
          <p id="guestbook-public-note">{copy.guestbookPublic}</p>
          <label htmlFor="guestbook-name">{copy.guestbookName}</label>
          <input
            id="guestbook-name"
            name="name"
            autoComplete="nickname"
            required
            maxLength={60}
            value={name}
            onChange={(e) => setName(e.target.value)}
            aria-describedby="guestbook-public-note"
          />
          <label htmlFor="guestbook-message">{copy.guestbookMessage}</label>
          <textarea
            id="guestbook-message"
            name="message"
            required
            maxLength={1000}
            rows={4}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            aria-describedby="guestbook-public-note guestbook-character-count"
          />
          <span
            className="guestbook-character-count"
            id="guestbook-character-count"
          >
            {message.length} / 1000
          </span>
          <button disabled={busy}>
            <PenLine size={17} aria-hidden="true" />
            {busy ? copy.guestbookPosting : copy.guestbookSign}
          </button>
          {error && (
            <p className="notice error" role="alert">
              {error}
            </p>
          )}
          {status && (
            <p className="notice" role="status">
              {status}
            </p>
          )}
        </form>
        <div className="guestbook-visitors">
          <div className="guestbook-list-heading">
            <h3>{copy.guestbookVisitors}</h3>
            <button
              className="plain guestbook-refresh"
              disabled={feed.loading}
              onClick={feed.reload}
              aria-label={copy.guestbookRefresh}
            >
              <RefreshCw size={16} aria-hidden="true" />
              {copy.guestbookRefreshShort}
            </button>
          </div>
          {feed.error ? (
            <p className="notice error" role="alert">
              {feed.error}{" "}
              <button className="plain" onClick={feed.reload}>
                {copy.guestbookRetry}
              </button>
            </p>
          ) : feed.loading ? (
            <p className="notice" role="status">
              {copy.guestbookLoading}
            </p>
          ) : (
            feed.data && (
              <>
                {feed.data.entries.length ? (
                  <ul className="guestbook-messages">
                    {feed.data.entries.map((entry) => (
                      <li key={entry.id}>
                        <article className="guestbook-entry">
                          <div className="guestbook-entry-heading">
                            <span
                              className="guestbook-initial"
                              aria-hidden="true"
                            >
                              {Array.from(entry.name)[0]?.toUpperCase()}
                            </span>
                            <div>
                              <h4>{entry.name}</h4>
                              <MessageDate value={entry.createdAt} />
                            </div>
                          </div>
                          <p>{entry.message}</p>
                          {entry.mine && (
                            <button
                              className="plain guestbook-remove"
                              disabled={!!removing}
                              onClick={() => remove(entry)}
                            >
                              {removing === entry.id
                                ? copy.guestbookRemoving
                                : copy.guestbookRemove}
                            </button>
                          )}
                        </article>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <div className="guestbook-empty">
                    <MessageSquare size={30} aria-hidden="true" />
                    <h4>{copy.guestbookEmpty}</h4>
                    <p>{copy.guestbookEmptyIntro}</p>
                  </div>
                )}
                <Pagination feed={feed} />
              </>
            )
          )}
        </div>
      </div>
    </section>
  );
}

export function GuestbookModeration() {
  const feed = useGuestbookFeed(true);
  const [busy, setBusy] = useState("");
  const [error, setError] = useState("");
  async function visibility(entry: Entry) {
    setBusy(entry.id);
    setError("");
    try {
      await api("/owner/guestbook/" + entry.id, "PUT", {
        hidden: !entry.hidden,
      });
      feed.reload();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy("");
    }
  }
  return (
    <div className="guestbook-moderation">
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      {feed.error ? (
        <p className="notice error" role="alert">
          {feed.error}{" "}
          <button className="plain" onClick={feed.reload}>
            {copy.guestbookRetry}
          </button>
        </p>
      ) : feed.loading ? (
        <p className="notice" role="status">
          {copy.guestbookLoading}
        </p>
      ) : (
        feed.data && (
          <>
            {!feed.data.entries.length && <p>{copy.guestbookNoEntries}</p>}
            {feed.data.entries.map((entry) => (
              <article className="guestbook-entry" key={entry.id}>
                <div className="guestbook-entry-heading">
                  <strong>{entry.name}</strong>
                  <span className="tag">
                    {entry.hidden
                      ? copy.guestbookHidden
                      : copy.guestbookVisible}
                  </span>
                  <MessageDate value={entry.createdAt} />
                </div>
                <p>{entry.message}</p>
                <button
                  className="secondary"
                  disabled={!!busy}
                  onClick={() => visibility(entry)}
                >
                  {entry.hidden ? copy.guestbookRestore : copy.guestbookHide}
                </button>
              </article>
            ))}
            <Pagination feed={feed} />
          </>
        )
      )}
    </div>
  );
}
