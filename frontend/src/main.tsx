import profileDrafts from "./profile-drafts.json";
import { copy } from "./copy";
import React, { createContext, useContext, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  BrowserRouter,
  Routes,
  Route,
  Link,
  NavLink,
  useNavigate,
  useParams,
  useLocation,
} from "react-router-dom";
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Menu,
  X,
  Check,
  ChevronLeft,
  Flag,
  Plus,
  Trash2,
  MoveUp,
  MoveDown,
  Trophy,
  Sparkles,
  Search,
  ChevronDown,
} from "lucide-react";
import { api, clearSession, type RecordData } from "./api";
import { ui } from "./i18n";
import "./style.css";
import "./feed.css";
import { QuizArtwork } from "./components/QuizArtwork";
import { Guestbook, GuestbookModeration } from "./components/Guestbook";
import { ActivityRunner, StartForm } from "./components/QuizFlow";
type Auth = { user: RecordData | null; refresh: () => Promise<void> };
const AuthContext = createContext<Auth>({
  user: null,
  refresh: async () => {},
});
function useLoad(path: string) {
  const [data, set] = useState<any>(null),
    [error, setError] = useState("");
  const [version, reload] = useState(0);
  useEffect(() => {
    let live = true;
    set(null);
    setError("");
    api(path)
      .then((x) => {
        if (live) set(x);
      })
      .catch((e) => {
        if (live) setError(e.message);
      });
    return () => {
      live = false;
    };
  }, [path, version]);
  return { data, error, reload: () => reload((v) => v + 1) };
}
function Notice({
  children,
  error = false,
  className = "",
}: {
  children: React.ReactNode;
  error?: boolean;
  className?: string;
}) {
  return (
    <div
      className={"notice " + className + " " + (error ? "error" : "")}
      role={error ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
function Loading({ error }: { error?: string }) {
  return <Notice error={!!error}>{error || ui.loading}</Notice>;
}
function BrandLogo() {
  return (
    <span className="brand-art" aria-hidden="true">
      <span className="logo-mark">
        <img
          src="/brand/zomi-nest-logo.png"
          alt=""
          width="1536"
          height="1024"
          decoding="async"
        />
      </span>
      <span className="logo-wordmark">
        <img
          src="/brand/zomi-nest-logo.png"
          alt=""
          width="1536"
          height="1024"
          decoding="async"
        />
      </span>
    </span>
  );
}
function App() {
  const [user, setUser] = useState<RecordData | null>(null),
    [ready, setReady] = useState(false),
    [open, setOpen] = useState(false);
  const nav = useNavigate(),
    location = useLocation();
  const refresh = async () => {
    const s = await api("/session");
    setUser(s.user);
    setReady(true);
  };
  useEffect(() => {
    refresh().catch(() => setReady(true));
  }, []);
  useEffect(() => {
    setOpen(false);
    window.scrollTo(0, 0);
    document.title =
      (location.pathname === "/"
        ? copy.text1
        : location.pathname.split("/")[1].replaceAll("-", copy.text2)) +
      copy.text3 +
      ui.title;
  }, [location.pathname]);
  async function logout() {
    await api("/logout", "POST");
    clearSession();
    setUser(null);
    await refresh();
    nav("/");
  }
  return (
    <AuthContext.Provider value={{ user, refresh }}>
      <a className="skip" href="#main">
        {copy.text4}
      </a>
      <header>
        <Link className="brand" to="/" aria-label={ui.title}>
          <BrandLogo />
        </Link>
        <button
          className="menu icon"
          aria-label={copy.text5}
          onClick={() => setOpen(!open)}
        >
          {open ? <X /> : <Menu />}
        </button>
        <nav className={open ? "open" : ""}>
          <NavLink to="/explore">{copy.text6}</NavLink>
          <NavLink to="/quizzes/discover-personality">{copy.text7}</NavLink>
          <NavLink to="/contribute">{copy.text8}</NavLink>
          <NavLink to="/submissions">{copy.myIdeas}</NavLink>
          <NavLink to="/results">{ui.results}</NavLink>
          <NavLink className="my-space" to="/account">
            {user?.name && user.name !== "Community guest"
              ? user.name
              : copy.mySpace}
            <ArrowUpRight size={16} />
          </NavLink>
          {user && !user.visitor && (
            <>
              {user.role === "OWNER" && (
                <NavLink to="/owner">{copy.text10}</NavLink>
              )}
              <button className="plain" onClick={logout}>
                {ui.logout}
              </button>
            </>
          )}
        </nav>
      </header>
      <main id="main" key={user?.id || "guest"}>
        {ready ? (
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/explore" element={<Catalog />} />
            <Route
              path="/guestbook"
              element={<Guestbook initialName={user?.name} />}
            />
            <Route path="/quizzes/:slug" element={<Detail />} />
            <Route path="/play/:id" element={<Runner />} />
            <Route path="/boards/:slug" element={<Board />} />
            <Route path="/profiles" element={<Profiles />} />
            <Route path="/profiles/:code" element={<Profile />} />
            <Route path="/login" element={<AccountForm />} />
            <Route path="/register" element={<AccountForm register />} />
            <Route path="/recovery" element={<Recovery />} />
            <Route path="/reset" element={<Recovery reset />} />
            <Route path="/account" element={<Account />} />
            <Route path="/results" element={<Results />} />
            <Route path="/contribute" element={<Editor />} />
            <Route path="/submissions" element={<Submissions />} />
            <Route path="/owner" element={<Owner />} />
            <Route
              path="*"
              element={
                <section>
                  <h1>{copy.text11}</h1>
                  <Link to="/">{copy.text12}</Link>
                </section>
              }
            />
          </Routes>
        ) : (
          <Loading />
        )}
      </main>
      <footer>
        <Link className="brand" to="/" aria-label={ui.title}>
          <BrandLogo />
        </Link>
        <p>{copy.text13}</p>
        <span>{ui.language}</span>
        <small>{copy.text14}</small>
        <Link to="/guestbook">{copy.guestbookLink}</Link>
        <Link className="owner-access" to="/login">
          {copy.ownerAccess}
        </Link>
      </footer>
    </AuthContext.Provider>
  );
}
const typeLabel: Record<string, string> = {
  KNOWLEDGE: copy.text15,
  OUTCOME: copy.text16,
  PERSONALITY: "Self-discovery",
};
const tones: Record<string, string> = {
  KNOWLEDGE: "peach",
  OUTCOME: "lavender",
  PERSONALITY: "sage",
};
function Activity({ q }: { q: RecordData }) {
  const count = q.questionsPerAttempt || q.questions.length;
  return (
    <article className="quiz-row-shell">
      <Link
        className="quiz-row"
        to={"/quizzes/" + q.slug}
        aria-label={copy.playQuiz + ": " + q.title}
      >
        <QuizArtwork type={q.type} />
        <div className="quiz-row-copy">
          <div className="quiz-row-heading">
            <div className="quiz-row-labels">
              <span className={"quiz-kind kind-" + q.type.toLowerCase()}>
                {typeLabel[q.type]}
              </span>
              {q.demo && <span className="demo-label">{ui.demo}</span>}
            </div>
            <h2>{q.title}</h2>
          </div>
          {q.description && (
            <p className="quiz-row-description">{q.description}</p>
          )}
          <div className="quiz-row-meta">
            <span>
              <BookOpen size={14} />
              {count}{" "}
              {q.randomQuestions
                ? "random questions"
                : count === 1
                  ? copy.questionSingular
                  : copy.recentQuestions}
            </span>
            {q.category && <span>{q.category}</span>}
          </div>
          {q.credit && (
            <p className="quiz-credit">
              {copy.byContributor} {q.credit}
            </p>
          )}
        </div>
        <span className="quiz-row-action">
          {copy.playQuiz}
          <ArrowRight size={17} />
        </span>
      </Link>
    </article>
  );
}
function Warmup({ quiz }: { quiz: RecordData | undefined }) {
  const navigate = useNavigate();
  if (!quiz) return null;
  return (
    <details className="quick-start">
      <summary>
        <Sparkles size={17} />
        <span>{copy.quickQuestion}</span>
        <ChevronDown size={16} />
      </summary>
      <div className="quick-start-content">
        <div className="quiz-row-labels">
          <span className="quiz-kind">{typeLabel[quiz.type]}</span>
          {quiz.demo && <span className="demo-label">{ui.demo}</span>}
        </div>
        <h2>{quiz.questions[0].text}</h2>
        <div className="warmup-options">
          {quiz.questions[0].options
            .slice(0, 3)
            .map((o: RecordData, i: number) => (
              <button
                key={o.id}
                className="sample-answer"
                onClick={() => navigate("/quizzes/" + quiz.slug)}
              >
                <span>{String.fromCharCode(65 + i)}</span>
                {o.text}
                <ArrowRight size={16} />
              </button>
            ))}
        </div>
        <p className="muted" role="status">
          {copy.warmupNote}
        </p>
      </div>
    </details>
  );
}
function Discovery({ home = false }: { home?: boolean }) {
  const { user } = useContext(AuthContext);
  const { data, error } = useLoad("/quizzes");
  const location = useLocation();
  const [search, setSearch] = useState(""),
    [category, setCategory] = useState(
      new URLSearchParams(location.search).get("category") || "",
    ),
    [type, setType] = useState("");
  useEffect(() => {
    setCategory(new URLSearchParams(location.search).get("category") || "");
  }, [location.search]);
  const categories = Array.from(
    new Set<string>(data?.map((q: RecordData) => q.category) || []),
  );
  const filtered = data?.filter(
    (q: RecordData) =>
      (
        q.title +
        " " +
        q.description +
        " " +
        q.category +
        " " +
        (q.tags || []).join(" ")
      )
        .toLowerCase()
        .includes(search.toLowerCase()) &&
      (!category || q.category === category) &&
      (!type || q.type === type),
  );
  const knowledge = data?.find((q: RecordData) => q.type === "KNOWLEDGE");
  function clear() {
    setSearch("");
    setCategory("");
    setType("");
  }
  return (
    <section className="discovery-page">
      <div className="discovery-welcome">
        <div>
          <h1>{home ? copy.heroLine1 : copy.text40}</h1>
          <p>{home ? copy.feedWelcome : copy.feedExploreIntro}</p>
        </div>
        <span className="browse-invitation">
          <Check size={15} />
          {copy.noSignup}
        </span>
      </div>
      <div className="discovery-controls">
        <label className="sr-only" htmlFor="quiz-search">
          {copy.text49}
        </label>
        <div className="quiz-search">
          <Search size={21} aria-hidden="true" />
          <input
            type="search"
            id="quiz-search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={copy.feedSearchPlaceholder}
            autoComplete="off"
          />
          {search && (
            <button
              className="clear-search"
              onClick={() => setSearch("")}
              aria-label={copy.clearSearch}
            >
              <X size={17} />
            </button>
          )}
        </div>
        <div className="browse-filters">
          <div className="category-pills" role="group" aria-label={copy.text51}>
            <button
              aria-pressed={!category}
              className={!category ? "selected" : ""}
              onClick={() => setCategory("")}
            >
              {copy.allActivities}
            </button>
            {categories.map((c) => (
              <button
                key={c}
                aria-pressed={category === c}
                className={category === c ? "selected" : ""}
                onClick={() => setCategory(c)}
              >
                {c}
              </button>
            ))}
          </div>
        </div>
      </div>
      {data ? (
        <>
          <div className="feed-heading">
            <div>
              <h2>{copy.quizFeedTitle}</h2>
              <span aria-live="polite">
                {filtered.length}{" "}
                {filtered.length === 1
                  ? copy.activitySingular
                  : copy.activitiesPlural}
              </span>
            </div>
            <div className="type-pill">
              <label className="sr-only" htmlFor="quiz-type">
                {copy.text53}
              </label>
              <select
                id="quiz-type"
                value={type}
                onChange={(e) => setType(e.target.value)}
              >
                <option value="">{copy.text54}</option>
                {Object.entries(typeLabel).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
              <ChevronDown size={15} aria-hidden="true" />
            </div>
          </div>
          <div className="quiz-feed">
            {filtered.map((q: RecordData) => (
              <Activity key={q.id} q={q} />
            ))}
          </div>
          {!filtered.length && (
            <div className="feed-empty">
              <Search size={27} />
              <h2>{copy.feedEmptyTitle}</h2>
              <p>{copy.text56}</p>
              <button className="secondary" onClick={clear}>
                {copy.clearFilters}
              </button>
            </div>
          )}
        </>
      ) : (
        <Loading error={error} />
      )}
      <div className="browse-footer">
        <div>
          <h2>{copy.feedContributeTitle}</h2>
          <p>{copy.feedContributeIntro}</p>
        </div>
        <Link className="button secondary" to="/contribute">
          {copy.text45}
          <Plus size={17} />
        </Link>
      </div>
      {home && <Warmup quiz={knowledge} />}
      {home && <Guestbook embedded initialName={user?.name} />}
    </section>
  );
}
function Home() {
  return <Discovery home />;
}
function Catalog() {
  return <Discovery />;
}

function Detail() {
  const { slug } = useParams(),
    { data: q, error } = useLoad("/quizzes/" + slug),
    { user } = useContext(AuthContext);
  const [report, setReport] = useState(""),
    [message, setMessage] = useState("");
  useEffect(() => {
    if (q) document.title = q.title + " · " + ui.title;
  }, [q]);
  if (!q)
    return (
      <section>
        <Loading error={error} />
      </section>
    );
  return (
    <section className="detail">
      <Link to="/explore" className="text-link">
        <ChevronLeft size={18} />
        {copy.text57}
      </Link>
      <div className="detail-layout">
        <div>
          <span className="tag">{typeLabel[q.type]}</span>
          <h1>{q.title}</h1>
          <p className="lead">{q.description}</p>
          <p>
            {q.questionsPerAttempt || q.questions.length}
            {q.randomQuestions
              ? ` random questions from ${q.questionBankSize} proverbs · About `
              : copy.text58}
            {Math.ceil((q.questionsPerAttempt || q.questions.length) * 0.3)}
            {copy.text59}
            {q.number}
          </p>
          {q.credit && (
            <p>
              {copy.text60}
              {q.credit}
            </p>
          )}
          {q.demo && <Notice>{copy.text61}</Notice>}
          {q.randomQuestions && (
            <p className="muted">
              Each new round draws a fresh random set. Your questions stay the
              same when you return to a round.
            </p>
          )}
          <StartForm
            key={slug}
            quiz={q}
            slug={slug!}
            defaultName={user?.name || ""}
            participantId={user?.id || "browser"}
          />
          {q.type === "KNOWLEDGE" && (
            <p>
              <Link to={"/boards/" + slug}>
                Community leaderboard <Trophy size={16} />
              </Link>
            </p>
          )}
          <p className="muted">{copy.text67}</p>
        </div>
        <div className={"detail-poster " + tones[q.type]}>
          <QuizArtwork type={q.type} />
          <h2>{q.type === "PERSONALITY" ? copy.text68 : copy.text69}</h2>
          <span>{copy.text70}</span>
        </div>
      </div>
      <details>
        <summary>
          <Flag size={16} />
          {copy.text71}
        </summary>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              await api("/quizzes/" + slug + "/reports", "POST", {
                reason: report,
              });
              setMessage(copy.text72);
              setReport("");
            } catch (e) {
              setMessage((e as Error).message);
            }
          }}
        >
          <label>
            {copy.text73}
            <textarea
              required
              maxLength={2000}
              value={report}
              onChange={(e) => setReport(e.target.value)}
            />
          </label>
          <button>{copy.text74}</button>
        </form>
        {message && <Notice>{message}</Notice>}
      </details>
    </section>
  );
}
function Runner() {
  const { id } = useParams(),
    { data: attempt, error } = useLoad("/attempts/" + id);
  if (!attempt)
    return (
      <section>
        <Loading error={error} />
      </section>
    );
  return <ActivityRunner key={id} attempt={attempt} />;
}
function BoardEntries({
  edition,
  page = 0,
  onTotal,
}: {
  edition: string;
  page?: number;
  onTotal?: (total: number) => void;
}) {
  const { data, error } = useLoad("/boards/" + edition + "?page=" + page);
  useEffect(() => {
    if (data && onTotal) onTotal(data.total);
  }, [data, onTotal]);
  if (!data) return <Loading error={error} />;
  return (
    <>
      <h2>{copy.text105}</h2>
      <p>{copy.text106}</p>
      {data.entries.length ? (
        <table>
          <thead>
            <tr>
              <th>{copy.text107}</th>
              <th>{copy.text108}</th>
              <th>{copy.text109}</th>
            </tr>
          </thead>
          <tbody>
            {data.entries.map((r: RecordData, i: number) => (
              <tr key={i}>
                <td>{r.rank}</td>
                <td>{r.name}</td>
                <td>
                  {r.score}
                  {copy.text110}
                  {r.maximum}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <Notice>{copy.text111}</Notice>
      )}
      {data.mine.map((r: RecordData) => (
        <Notice key="mine" className="player-rank">
          {copy.text112}
          {r.rank}
          {copy.text3}
          {r.score}
          {copy.text110}
          {r.maximum}
        </Notice>
      ))}
      <p>
        {data.total}
        {copy.text113}
      </p>
    </>
  );
}
function Board() {
  const { slug } = useParams(),
    { data, error } = useLoad("/quizzes/" + slug + "/editions");
  const [selected, setSelected] = useState(""),
    [page, setPage] = useState(0),
    [total, setTotal] = useState(0);
  return (
    <section>
      <h1>{copy.text114}</h1>
      <Link to={"/quizzes/" + slug}>{copy.text115}</Link>
      {data ? (
        <>
          <label>
            {copy.text116}
            <select
              value={selected || data[0]?.id}
              onChange={(e) => {
                setSelected(e.target.value);
                setPage(0);
              }}
            >
              {data.map((e: RecordData) => (
                <option key={e.id} value={e.id}>
                  {copy.text117}
                  {e.number}
                </option>
              ))}
            </select>
          </label>
          {data.length > 0 && (
            <BoardEntries
              edition={selected || data[0].id}
              page={page}
              onTotal={setTotal}
            />
          )}
          <div className="actions">
            <button disabled={!page} onClick={() => setPage(page - 1)}>
              {copy.text118}
            </button>
            <span>
              {copy.text119}
              {page + 1}
            </span>
            <button
              disabled={(page + 1) * 10 >= total}
              onClick={() => setPage(page + 1)}
            >
              {copy.text120}
            </button>
          </div>
        </>
      ) : (
        <Loading error={error} />
      )}
    </section>
  );
}
function Profiles() {
  const { data, error } = useLoad("/profiles");
  return (
    <section>
      <h1>{copy.text121}</h1>
      <p className="lead">{copy.text122}</p>
      <Link className="button" to="/explore">
        {copy.text123}
        <ArrowRight size={18} />
      </Link>
      {data ? (
        <div className="profile-grid">
          {data.map((p: RecordData) => (
            <Link to={"/profiles/" + p.code} key={p.code}>
              <span>{p.code}</span>
              <h2>{p.name}</h2>
              <p>{p.overview}</p>
              <ArrowUpRight size={20} />
            </Link>
          ))}
        </div>
      ) : (
        <Loading error={error} />
      )}
    </section>
  );
}
function Profile() {
  const { code } = useParams(),
    { data: p, error } = useLoad("/profiles/" + code);
  useEffect(() => {
    if (p) document.title = p.name + " · " + ui.title;
  }, [p]);
  return (
    <section className="reading">
      <Link to="/profiles">{copy.text124}</Link>
      {p ? (
        <>
          <h1>{p.name}</h1>
          <Notice>{copy.text125}</Notice>
          {[
            ["overview", "Overview"],
            ["strengths", copy.text126],
            ["difficulties", copy.text127],
            ["communication", "Communication"],
            ["relationships", copy.text128],
            ["work", copy.text129],
          ].map(([k, t]) => (
            <article key={k}>
              <h2>{t}</h2>
              <p>{p[k]}</p>
            </article>
          ))}
        </>
      ) : (
        <Loading error={error} />
      )}
    </section>
  );
}
function Require({
  children,
  owner = false,
}: {
  children: React.ReactNode;
  owner?: boolean;
}) {
  const { user } = useContext(AuthContext);
  if (!user)
    return (
      <section>
        <h1>{copy.text130}</h1>
        <p>{copy.text131}</p>
        <Link className="button" to="/login">
          {copy.text132}
        </Link>
        <Link className="text-link" to="/register">
          {copy.text133}
        </Link>
      </section>
    );
  if (owner && user.role !== "OWNER")
    return (
      <section>
        <Notice error>{copy.text134}</Notice>
      </section>
    );
  return <>{children}</>;
}
function AccountForm({ register = false }: { register?: boolean }) {
  const { refresh } = useContext(AuthContext),
    nav = useNavigate();
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  return (
    <section className="form-page">
      <h1>{register ? copy.text135 : copy.text136}</h1>
      <p>{register ? copy.text137 : copy.text138}</p>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError("");
          const f = new FormData(e.currentTarget);
          try {
            clearSession();
            await api(
              register ? "/register" : "/login",
              "POST",
              Object.fromEntries(f),
            );
            await refresh();
            nav("/");
          } catch (e) {
            setError((e as Error).message);
          } finally {
            setBusy(false);
          }
        }}
      >
        {register && (
          <label>
            {copy.text139}
            <input
              name="name"
              required
              maxLength={60}
              autoComplete="nickname"
            />
          </label>
        )}
        <label>
          {copy.text140}
          <input name="email" type="email" required autoComplete="email" />
        </label>
        <label>
          {copy.text141}
          <input
            name="password"
            type="password"
            required
            minLength={register ? 12 : 1}
            maxLength={128}
            autoComplete={register ? "new-password" : "current-password"}
          />
          {register && <small>{copy.text142}</small>}
        </label>
        {error && <Notice error>{error}</Notice>}
        <button disabled={busy}>
          {busy ? copy.text143 : register ? copy.text144 : copy.text132}
          <ArrowRight size={18} />
        </button>
      </form>
      <p>
        <Link to={register ? "/login" : "/register"}>
          {register ? copy.text145 : copy.text146}
        </Link>
      </p>
      <Link to="/recovery">{copy.text147}</Link>
    </section>
  );
}
function Recovery({ reset = false }: { reset?: boolean }) {
  const [message, setMessage] = useState(""),
    [error, setError] = useState("");
  const nav = useNavigate();
  return (
    <section className="form-page">
      <h1>{reset ? copy.text148 : copy.text149}</h1>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setError("");
          try {
            const f = Object.fromEntries(new FormData(e.currentTarget));
            if (reset) {
              await api("/reset", "POST", {
                ...f,
                token: new URLSearchParams(location.search).get("token"),
              });
              clearSession();
              nav("/login");
            } else {
              const r = await api("/recovery", "POST", f);
              setMessage(r.message);
            }
          } catch (e) {
            setError((e as Error).message);
          }
        }}
      >
        <label>
          {reset ? copy.text150 : copy.text151}
          <input
            name={reset ? "password" : "email"}
            type={reset ? "password" : "email"}
            minLength={reset ? 12 : undefined}
            required
          />
        </label>
        <button>{reset ? copy.text152 : copy.text153}</button>
      </form>
      {message && <Notice>{message}</Notice>}
      {error && <Notice error>{error}</Notice>}
    </section>
  );
}
function Account() {
  const { user, refresh } = useContext(AuthContext);
  const [message, setMessage] = useState("");
  return (
    <Require>
      <section className="form-page">
        <h1>{copy.text154}</h1>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              const f = new FormData(e.currentTarget);
              await api("/account", "PUT", {
                name: f.get("name"),
              });
              await refresh();
              setMessage(copy.text155);
            } catch (e) {
              setMessage((e as Error).message);
            }
          }}
        >
          <label>
            {copy.text139}
            <input
              name="name"
              defaultValue={user?.name}
              required
              maxLength={60}
            />
          </label>
          <p>
            Each result stays private until you choose to share it from its
            result page. Changing this name does not change names on earlier
            attempts.
          </p>
          <button>{copy.text158}</button>
        </form>
        <button
          className="secondary"
          onClick={async () => {
            try {
              await api("/account", "PUT", {
                name: user?.name,
                publicScore: false,
              });
              await refresh();
              setMessage("All your results are now private.");
            } catch (e) {
              setMessage((e as Error).message);
            }
          }}
        >
          Make all my results private
        </button>
        {message && <Notice>{message}</Notice>}
        <p>
          <Link to="/submissions">{copy.text159}</Link>
        </p>
        {user?.visitor ? (
          <p className="browser-note">{copy.browserNote}</p>
        ) : (
          <Link to="/recovery">{copy.text160}</Link>
        )}
      </section>
    </Require>
  );
}
function Results() {
  const { user } = useContext(AuthContext);
  return <Require>{user && <ResultList />}</Require>;
}
function ResultList() {
  const { data, error } = useLoad("/results");
  return (
    <section>
      <h1>{copy.text161}</h1>
      <p>{copy.browserResults}</p>
      {data ? (
        data.length ? (
          data.map((a: RecordData) => (
            <Link className="result-row" key={a.id} to={"/play/" + a.id}>
              <div>
                <h2>{a.result?.title || a.title}</h2>
                <p>
                  {a.display_name || "Guest"} ·{" "}
                  {a.shared ? "Shared" : "Private"}
                </p>
                <span>
                  {new Date(a.finished_at || a.started_at).toLocaleDateString()}
                  {copy.text3}
                  {!a.result
                    ? "In progress · Resume attempt"
                    : a.result.type === "KNOWLEDGE"
                      ? `${a.result.score}/${a.result.maximum}`
                      : a.result.type === "OUTCOME"
                        ? a.result.outcome.name
                        : a.result.balanced
                          ? "Balanced profile"
                          : a.result.code || a.result.profile?.name}
                </span>
              </div>
              <ArrowUpRight />
            </Link>
          ))
        ) : (
          <Notice>
            {copy.text163}
            <Link to="/explore">{copy.text164}</Link>
          </Notice>
        )
      ) : (
        <Loading error={error} />
      )}
    </section>
  );
}
function blankQuestion(type: string, i = 0): RecordData {
  return {
    id: crypto.randomUUID(),
    text: "",
    explanation: "",
    trait: type === "PERSONALITY" ? Math.floor(i / 8) : null,
    reverse: false,
    options: (type === "PERSONALITY"
      ? [copy.text165, "Disagree", copy.text166, "Agree", copy.text167]
      : ["", ""]
    ).map((text, j) => ({
      id: crypto.randomUUID(),
      text,
      correct: type === "KNOWLEDGE" ? Number(j === 0) : null,
      weights:
        type === "OUTCOME"
          ? { first: Number(j === 0), second: Number(j === 1) }
          : {},
    })),
  };
}
function blankQuiz(type = "KNOWLEDGE"): RecordData {
  return {
    title: "",
    description: "",
    category: copy.text47,
    type,
    credit: "",
    demo: false,
    profiles:
      type === "PERSONALITY"
        ? Object.fromEntries(profileDrafts.map((p) => [p.code, p]))
        : {},
    questions: Array.from({ length: type === "PERSONALITY" ? 32 : 1 }, (_, i) =>
      blankQuestion(type, i),
    ),
    outcomes:
      type === "OUTCOME"
        ? [
            { id: "first", name: "", description: "" },
            { id: "second", name: "", description: "" },
          ]
        : [],
    tieOrder: type === "OUTCOME" ? ["first", "second"] : [],
  };
}
function Editor() {
  const { user } = useContext(AuthContext);
  return <Require>{user && <EditorForm />}</Require>;
}
function EditorForm() {
  const search = new URLSearchParams(useLocation().search),
    editId = search.get("edit");
  const { data: catalog } = useLoad("/quizzes");
  const [q, setQ] = useState<RecordData>(blankQuiz()),
    [kind, setKind] = useState("QUIZ"),
    [target, setTarget] = useState(""),
    [id, setId] = useState<string | null>(editId),
    [message, setMessage] = useState(""),
    [error, setError] = useState(""),
    [preview, setPreview] = useState(false),
    [busy, setBusy] = useState(false),
    [source, setSource] = useState("");
  useEffect(() => {
    if (editId)
      api("/submissions")
        .then((rows) => {
          const r = rows.find((x: RecordData) => x.id === editId);
          if (r) {
            setQ(r.content);
            setKind(r.kind);
            setTarget(r.target_id || "");
          } else setError(copy.text168);
        })
        .catch((e) => setError(e.message));
  }, [editId]);
  const change = (k: string, v: unknown) => setQ({ ...q, [k]: v });
  function question(index: number, k: string, v: unknown) {
    setQ({
      ...q,
      questions: q.questions.map((x: RecordData, i: number) =>
        i === index ? { ...x, [k]: v } : x,
      ),
    });
  }
  function move(i: number, d: number) {
    const xs = [...q.questions];
    [xs[i], xs[i + d]] = [xs[i + d], xs[i]];
    change("questions", xs);
  }
  async function save(submit = false) {
    setBusy(true);
    setError("");
    try {
      const body = { kind, targetId: target || null, content: q };
      let current = id;
      if (current) await api("/submissions/" + current, "PUT", body);
      else {
        const r = await api("/submissions", "POST", body);
        current = r.id;
        setId(current);
      }
      if (submit) await api("/submissions/" + current + "/submit", "POST");
      setMessage(submit ? copy.text169 : copy.text170);
      if (submit) location.assign("/submissions");
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="editor">
      <div className="section-heading">
        <div>
          <h1>{copy.text171}</h1>
          <p>{copy.text172}</p>
        </div>
        <Link to="/submissions">{copy.text159}</Link>
      </div>
      {error && <Notice error>{error}</Notice>}
      {message && <Notice>{message}</Notice>}
      <div className="filters">
        <label>
          {copy.text173}
          <select
            value={kind}
            onChange={(e) => {
              setKind(e.target.value);
              if (e.target.value === "QUESTION") setQ(blankQuiz());
            }}
          >
            <option value="QUIZ">{copy.text174}</option>
            <option value="QUESTION">{copy.text175}</option>
          </select>
        </label>
        <label>
          {copy.text176}
          <select
            value={q.type}
            disabled={kind === "QUESTION"}
            onChange={async (e) => {
              const next = blankQuiz(e.target.value);
              if (next.type === "PERSONALITY") {
                try {
                  const ps = await api("/profiles");
                  if (ps.length === 16)
                    next.profiles = Object.fromEntries(
                      ps.map((p: RecordData) => [p.code, p]),
                    );
                } catch (e) {
                  setError((e as Error).message);
                }
              }
              setQ(next);
            }}
          >
            {Object.entries(typeLabel).map(([k, v]) => (
              <option value={k} key={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label>
          {kind === "QUESTION" ? copy.text177 : copy.text178}
          <select
            value={target}
            onChange={(e) => setTarget(e.target.value)}
            required={kind === "QUESTION"}
          >
            <option value="">
              {kind === "QUESTION" ? copy.text179 : copy.text180}
            </option>
            {catalog
              ?.filter((x: RecordData) => x.type === q.type)
              .map((x: RecordData) => (
                <option key={x.id} value={x.id}>
                  {x.title}
                </option>
              ))}
          </select>
        </label>
      </div>
      <label>
        {copy.text181}
        <input
          required
          value={q.title}
          maxLength={160}
          onChange={(e) => change("title", e.target.value)}
        />
      </label>
      <label>
        {copy.text182}
        <textarea
          value={q.description}
          onChange={(e) => change("description", e.target.value)}
        />
      </label>
      <div className="filters">
        <label>
          {copy.text51}
          <input
            value={q.category}
            onChange={(e) => change("category", e.target.value)}
          />
        </label>
        <label>
          {copy.text183}
          <input
            value={q.credit || ""}
            onChange={(e) => change("credit", e.target.value)}
          />
        </label>
      </div>
      <div className="filters">
        <label>
          {copy.text184}
          <input
            value={q.titleEn || ""}
            onChange={(e) => change("titleEn", e.target.value)}
          />
        </label>
        <label>
          {copy.text185}
          <input
            value={(q.tags || []).join(", ")}
            onChange={(e) =>
              change(
                "tags",
                e.target.value.split(",").map((t) => t.trim()),
              )
            }
          />
        </label>
      </div>
      <label>
        {copy.text186}
        <textarea
          value={q.descriptionEn || ""}
          onChange={(e) => change("descriptionEn", e.target.value)}
        />
      </label>
      <label className="check">
        <input
          type="checkbox"
          checked={q.demo}
          onChange={(e) => change("demo", e.target.checked)}
        />
        {copy.text187}
      </label>
      <Notice>{copy.text188}</Notice>
      {q.type === "OUTCOME" && (
        <article className="editor-part">
          <h2>{copy.text189}</h2>
          <p>{copy.text190}</p>
          {q.outcomes.map((o: RecordData, i: number) => (
            <div className="outcome-edit" key={o.id}>
              <label>
                {copy.text191}
                {i + 1}
                {copy.text192}
                <input
                  value={o.name}
                  onChange={(e) =>
                    change(
                      "outcomes",
                      q.outcomes.map((v: RecordData, j: number) =>
                        j === i ? { ...v, name: e.target.value } : v,
                      ),
                    )
                  }
                />
              </label>
              <label>
                {copy.text193}
                <textarea
                  value={o.description}
                  onChange={(e) =>
                    change(
                      "outcomes",
                      q.outcomes.map((v: RecordData, j: number) =>
                        j === i ? { ...v, description: e.target.value } : v,
                      ),
                    )
                  }
                />
              </label>
            </div>
          ))}
          <button
            className="secondary"
            onClick={() => {
              const oid = crypto.randomUUID();
              setQ({
                ...q,
                outcomes: [
                  ...q.outcomes,
                  { id: oid, name: "", description: "" },
                ],
                tieOrder: [...q.tieOrder, oid],
              });
            }}
          >
            <Plus size={17} />
            {copy.text194}
          </button>
        </article>
      )}
      {q.type === "PERSONALITY" && (
        <details>
          <summary>{copy.text195}</summary>
          {Object.entries(q.profiles || {}).map(([code, value]) => {
            const p = value as RecordData;
            return (
              <article className="editor-part" key={code}>
                <h3>{code}</h3>
                {[
                  "name",
                  "overview",
                  "strengths",
                  "difficulties",
                  "communication",
                  "relationships",
                  "work",
                ].map((k) => (
                  <label key={k}>
                    {k}
                    <textarea
                      value={p[k] || ""}
                      onChange={(e) =>
                        change("profiles", {
                          ...q.profiles,
                          [code]: { ...p, [k]: e.target.value },
                        })
                      }
                    />
                  </label>
                ))}
              </article>
            );
          })}
        </details>
      )}
      {q.questions.map((x: RecordData, i: number) => (
        <article className="editor-part" key={x.id}>
          <div className="split">
            <h2>
              {copy.text79}
              {i + 1}
            </h2>
            <div className="actions">
              <button
                className="icon secondary"
                aria-label={copy.text196 + (i + 1) + copy.text197}
                disabled={i === 0}
                onClick={() => move(i, -1)}
              >
                <MoveUp size={17} />
              </button>
              <button
                className="icon secondary"
                aria-label={copy.text196 + (i + 1) + copy.text198}
                disabled={i === q.questions.length - 1}
                onClick={() => move(i, 1)}
              >
                <MoveDown size={17} />
              </button>
              <button
                className="icon secondary"
                aria-label={copy.text199 + (i + 1)}
                disabled={q.questions.length === 1}
                onClick={() =>
                  change(
                    "questions",
                    q.questions.filter((_: unknown, j: number) => j !== i),
                  )
                }
              >
                <Trash2 size={17} />
              </button>
            </div>
          </div>
          <label>
            {copy.text200}
            <textarea
              value={x.text}
              onChange={(e) => question(i, "text", e.target.value)}
            />
          </label>
          {x.options.map((o: RecordData, j: number) => (
            <div className="option-edit" key={o.id}>
              <label>
                {copy.text201}
                {j + 1}
                <input
                  value={o.text}
                  onChange={(e) =>
                    question(
                      i,
                      "options",
                      x.options.map((v: RecordData, k: number) =>
                        k === j ? { ...v, text: e.target.value } : v,
                      ),
                    )
                  }
                />
              </label>
              {q.type === "KNOWLEDGE" && (
                <label className="check">
                  <input
                    type="radio"
                    name={"correct" + x.id}
                    checked={o.correct === 1}
                    onChange={() =>
                      question(
                        i,
                        "options",
                        x.options.map((v: RecordData, k: number) => ({
                          ...v,
                          correct: Number(k === j),
                        })),
                      )
                    }
                  />
                  {copy.text202}
                </label>
              )}
              {q.type === "OUTCOME" &&
                q.outcomes.map((out: RecordData) => (
                  <label key={out.id}>
                    {out.name || copy.text203}
                    {copy.text204}
                    <input
                      type="number"
                      min="-100"
                      max="100"
                      value={o.weights[out.id] || 0}
                      onChange={(e) =>
                        question(
                          i,
                          "options",
                          x.options.map((v: RecordData, k: number) =>
                            k === j
                              ? {
                                  ...v,
                                  weights: {
                                    ...v.weights,
                                    [out.id]: Number(e.target.value),
                                  },
                                }
                              : v,
                          ),
                        )
                      }
                    />
                  </label>
                ))}
            </div>
          ))}
          {q.type !== "PERSONALITY" && (
            <button
              className="secondary"
              disabled={x.options.length >= 8}
              onClick={() =>
                question(i, "options", [
                  ...x.options,
                  {
                    id: crypto.randomUUID(),
                    text: "",
                    correct: 0,
                    weights: {},
                  },
                ])
              }
            >
              <Plus size={16} />
              {copy.text205}
            </button>
          )}
          {q.type === "KNOWLEDGE" && (
            <>
              <label>
                {copy.text206}
                <textarea
                  value={x.explanation || ""}
                  onChange={(e) => question(i, "explanation", e.target.value)}
                />
              </label>
              <label>
                {copy.text207}
                <input
                  value={x.source || ""}
                  onChange={(e) => question(i, "source", e.target.value)}
                />
              </label>
              <label>
                {copy.text208}
                <textarea
                  value={x.explanationEn || ""}
                  onChange={(e) => question(i, "explanationEn", e.target.value)}
                />
              </label>
            </>
          )}
          {q.type === "PERSONALITY" && (
            <div className="filters">
              <label>
                {copy.text209}
                <select
                  value={x.trait}
                  onChange={(e) => question(i, "trait", Number(e.target.value))}
                >
                  {[copy.text210, copy.text211, copy.text212, copy.text213].map(
                    (t, j) => (
                      <option key={j} value={j}>
                        {t}
                      </option>
                    ),
                  )}
                </select>
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={x.reverse}
                  onChange={(e) => question(i, "reverse", e.target.checked)}
                />
                {copy.text214}
              </label>
            </div>
          )}
        </article>
      ))}
      {kind !== "QUESTION" && (
        <button
          className="secondary"
          onClick={() =>
            change("questions", [
              ...q.questions,
              blankQuestion(q.type, q.questions.length),
            ])
          }
        >
          <Plus size={17} />
          {copy.text215}
        </button>
      )}
      <details>
        <summary>{copy.text216}</summary>
        <p>{copy.text217}</p>
        <label>
          {copy.text218}
          <textarea
            className="json-editor"
            value={source}
            placeholder={copy.text219}
            onChange={(e) => setSource(e.target.value)}
          />
        </label>
        <button
          className="secondary"
          onClick={() => {
            try {
              const v = JSON.parse(source);
              if (!v.questions || !v.type) throw Error(copy.text220);
              setQ(v);
              setError("");
            } catch (e) {
              setError((e as Error).message);
            }
          }}
        >
          {copy.text221}
        </button>
      </details>
      <div className="sticky-actions">
        <button className="secondary" onClick={() => setPreview(!preview)}>
          {copy.text222}
        </button>
        <button disabled={busy} onClick={() => save()}>
          {ui.save}
        </button>
        <button disabled={busy} onClick={() => save(true)}>
          {ui.submit}
          <ArrowRight size={17} />
        </button>
      </div>
      {preview && <Snapshot q={q} />}
    </section>
  );
}
function Snapshot({ q }: { q: RecordData }) {
  return (
    <div className="snapshot">
      <h2>{q.title || copy.text223}</h2>
      <p>{q.description}</p>
      <p>
        {q.type}
        {copy.text3}
        {q.category}
        {copy.text3}
        {q.questions?.length}
        {copy.text224}
      </p>
      {q.questions?.map((x: RecordData, i: number) => (
        <article key={i}>
          <h3>
            {i + 1}
            {copy.text101}
            {x.text}
          </h3>
          <ol>
            {x.options?.map((o: RecordData, j: number) => (
              <li key={j}>
                {o.text}
                {o.correct === 1 ? copy.text225 : ""}
                {q.type === "OUTCOME"
                  ? copy.text226 + JSON.stringify(o.weights)
                  : ""}
              </li>
            ))}
          </ol>
          <p>{x.explanation}</p>
          {q.type === "PERSONALITY" && (
            <p>
              {copy.text227}
              {x.trait}
              {copy.text228}
              {String(x.reverse)}
            </p>
          )}
        </article>
      ))}
      {q.outcomes?.map((o: RecordData) => (
        <p key={o.id}>
          <strong>
            {o.name}
            {copy.text229}
          </strong>
          {o.description}
        </p>
      ))}
      {q.type === "OUTCOME" && (
        <p>
          {copy.text230}
          {q.tieOrder.join(copy.text231)}
        </p>
      )}
    </div>
  );
}
function Submissions() {
  const { user } = useContext(AuthContext);
  return <Require>{user && <SubmissionList />}</Require>;
}
function SubmissionList() {
  const { data, error, reload } = useLoad("/submissions");
  const [err, setErr] = useState("");
  return (
    <section>
      <div className="section-heading">
        <h1>{copy.text232}</h1>
        <Link className="button" to="/contribute">
          {copy.text233}
          <Plus size={18} />
        </Link>
      </div>
      {err && <Notice error>{err}</Notice>}
      {data ? (
        data.length ? (
          data.map((s: RecordData) => (
            <article className="submission" key={s.id}>
              <span className="tag">
                {s.status.replaceAll("_", copy.text2)}
              </span>
              <h2>{s.content.title || copy.text223}</h2>
              <p>
                {copy.text234}
                {s.revision}
                {copy.text3}
                {s.kind === "QUESTION" ? copy.text235 : copy.text174}
              </p>
              {s.note && (
                <Notice>
                  {copy.text236}
                  {s.note}
                </Notice>
              )}
              <div className="actions">
                {[
                  "DRAFT",
                  "CHANGES_REQUESTED",
                  "WITHDRAWN",
                  "REJECTED",
                ].includes(s.status) && (
                  <Link
                    className="button secondary"
                    to={"/contribute?edit=" + s.id}
                  >
                    {copy.text237}
                  </Link>
                )}
                {s.status === "SUBMITTED" && (
                  <button
                    className="secondary"
                    onClick={async () => {
                      try {
                        await api("/submissions/" + s.id + "/withdraw", "POST");
                        reload();
                      } catch (e) {
                        setErr((e as Error).message);
                      }
                    }}
                  >
                    {copy.text238}
                  </button>
                )}
              </div>
              <details>
                <summary>{copy.text239}</summary>
                <Snapshot q={s.content} />
              </details>
            </article>
          ))
        ) : (
          <Notice>{copy.text240}</Notice>
        )
      ) : (
        <Loading error={error} />
      )}
    </section>
  );
}
function Owner() {
  return (
    <Require owner>
      <OwnerDashboard />
    </Require>
  );
}
function OwnerDashboard() {
  const { user } = useContext(AuthContext);
  const {
    data: d,
    error,
    reload,
  } = useLoad(user?.role === "OWNER" ? "/owner" : "/session");
  const [err, setErr] = useState(""),
    [note, setNote] = useState<Record<string, string>>({}),
    [filter, setFilter] = useState("SUBMITTED"),
    [bank, setBank] = useState<string[]>([]),
    [qid, setQid] = useState(""),
    [preview, setPreview] = useState<RecordData | null>(null),
    [profileSource, setProfileSource] = useState(""),
    [profileCode, setProfileCode] = useState("AAAA");
  async function action(path: string, method: string, body?: unknown) {
    setErr("");
    try {
      await api(path, method, body);
      reload();
    } catch (e) {
      setErr((e as Error).message);
    }
  }
  if (!d?.submissions)
    return (
      <section>
        <Loading error={error} />
      </section>
    );
  return (
    <section className="owner">
      <h1>{copy.text241}</h1>
      <p>{copy.text242}</p>
      {err && <Notice error>{err}</Notice>}
      <div className="metrics">
        {d.metrics.map((m: RecordData) => (
          <span key={m.name}>
            <strong>{m.value}</strong> {m.name}
          </span>
        ))}
        <span>
          <strong>{Math.round(d.pendingHours)}</strong>
          {copy.text243}
        </span>
      </div>
      <details className="guestbook-owner-section">
        <summary>{copy.guestbookModeration}</summary>
        <GuestbookModeration />
      </details>
      <h2>{copy.text244}</h2>
      <label>
        {copy.text245}
        <select value={filter} onChange={(e) => setFilter(e.target.value)}>
          {[
            "ALL",
            "SUBMITTED",
            "CHANGES_REQUESTED",
            "PUBLISHED",
            "APPROVED_IN_BANK",
            "REJECTED",
            "WITHDRAWN",
            "DRAFT",
          ].map((s) => (
            <option key={s}>{s}</option>
          ))}
        </select>
      </label>
      {d.submissions
        .filter((s: RecordData) => filter === "ALL" || s.status === filter)
        .map((s: RecordData) => (
          <article className="submission" key={s.id}>
            <span className="tag">{s.status}</span>
            <h3>
              {s.content.title}
              {copy.text246}
              {s.revision}
            </h3>
            <p>
              {copy.text247}
              {s.contributor}
              {copy.text3}
              {s.kind}
            </p>
            <details>
              <summary>
                {copy.text248}
                {s.revision}
              </summary>
              <Snapshot q={s.content} />
            </details>
            {s.status === "SUBMITTED" && (
              <>
                <label>
                  {copy.text249}
                  <textarea
                    value={note[s.id] || ""}
                    onChange={(e) =>
                      setNote({ ...note, [s.id]: e.target.value })
                    }
                  />
                </label>
                <div className="actions">
                  {[
                    [
                      "APPROVE",
                      s.kind === "QUESTION" ? copy.text250 : copy.text251,
                    ],
                    ["CHANGES_REQUESTED", copy.text252],
                    ["REJECTED", "Reject"],
                  ].map(([decision, label]) => (
                    <button
                      key={decision}
                      className={decision === "APPROVE" ? "" : "secondary"}
                      onClick={() =>
                        action(
                          "/owner/submissions/" + s.id + "/review",
                          "POST",
                          {
                            revision: s.revision,
                            decision,
                            note: note[s.id] || "",
                          },
                        )
                      }
                    >
                      {label}
                    </button>
                  ))}
                </div>
              </>
            )}
          </article>
        ))}
      <h2>{copy.text253}</h2>
      <label>
        {copy.text254}
        <select
          value={qid}
          onChange={(e) => {
            setQid(e.target.value);
            setBank([]);
            setPreview(null);
          }}
        >
          <option value="">{copy.text255}</option>
          {d.quizzes.map((q: RecordData) => (
            <option key={q.id} value={q.id}>
              {q.slug}
            </option>
          ))}
        </select>
      </label>
      {d.bank
        .filter((b: RecordData) => b.target_id === qid && !b.published_id)
        .map((b: RecordData) => (
          <label className="check" key={b.id}>
            <input
              type="checkbox"
              checked={bank.includes(b.id)}
              onChange={(e) =>
                setBank(
                  e.target.checked
                    ? [...bank, b.id]
                    : bank.filter((id) => id !== b.id),
                )
              }
            />
            {b.content.title}
          </label>
        ))}
      <button
        disabled={!bank.length}
        onClick={async () => {
          try {
            setPreview(
              await api(
                "/owner/bank/" + qid + "/preview?entries=" + bank.join(","),
              ),
            );
          } catch (e) {
            setErr((e as Error).message);
          }
        }}
      >
        {copy.text256}
      </button>
      {preview && (
        <>
          <Snapshot q={preview} />
          <button
            onClick={async () => {
              await action("/owner/bank/publish", "POST", {
                quizId: qid,
                entries: bank,
                expectedEdition: preview?.baseEdition,
              });
              setPreview(null);
              setBank([]);
            }}
          >
            {copy.text257}
          </button>
        </>
      )}
      <h2>{copy.text258}</h2>
      {d.quizzes.map((q: RecordData) => (
        <article className="control-row" key={q.id}>
          <Link to={"/quizzes/" + q.slug}>{q.slug}</Link>
          <label className="check">
            <input
              type="checkbox"
              checked={q.published}
              onChange={(e) =>
                action("/owner/quizzes/" + q.id, "PUT", {
                  published: e.target.checked,
                  featured: q.featured,
                })
              }
            />
            {copy.text259}
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={q.featured}
              onChange={(e) =>
                action("/owner/quizzes/" + q.id, "PUT", {
                  published: q.published,
                  featured: e.target.checked,
                })
              }
            />
            {copy.text260}
          </label>
        </article>
      ))}
      <h2>{copy.text261}</h2>
      {!d.reports.length && <p>{copy.text262}</p>}
      {d.reports.map((r: RecordData) => (
        <article className="control-row" key={r.id}>
          <div>
            <strong>{r.slug}</strong>
            <p>{r.reason}</p>
          </div>
          <button
            disabled={r.resolved}
            onClick={() => action("/owner/reports/" + r.id, "PUT")}
          >
            {r.resolved ? "Resolved" : copy.text263}
          </button>
        </article>
      ))}
      <h2>{copy.text264}</h2>
      <p>{copy.text265}</p>
      {d.users.map((u: RecordData) => (
        <form
          className="control-row"
          key={u.id}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            action("/owner/users/" + u.id, "PUT", {
              name: f.get("name"),
              restricted: f.get("restricted") === "on",
              reason: f.get("reason"),
            });
          }}
        >
          <label>
            {copy.text266}
            <input name="name" required maxLength={60} defaultValue={u.name} />
          </label>
          <label className="check">
            <input
              type="checkbox"
              name="restricted"
              defaultChecked={u.restricted}
            />
            {copy.text267}
          </label>
          <label>
            {copy.text268}
            <input name="reason" required />
          </label>
          <button>{copy.text269}</button>
        </form>
      ))}
      <h2>{copy.text270}</h2>
      {d.boards.map((b: RecordData) => (
        <form
          className="control-row"
          key={b.id}
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            action("/owner/boards/" + b.id, "PUT", {
              hidden: !b.hidden,
              reason: f.get("reason"),
            });
          }}
        >
          <span>
            {b.name}
            {copy.text3}
            {b.score}
            {copy.text271}
            {b.hidden ? "Hidden" : "Visible"}
          </span>
          <label>
            {copy.text272}
            <input name="reason" required defaultValue={b.reason || ""} />
          </label>
          <button>{b.hidden ? copy.text273 : copy.text274}</button>
        </form>
      ))}
      <h2>{copy.text275}</h2>
      <p>{copy.text276}</p>
      <div className="actions">
        <label>
          {copy.text277}
          <input
            maxLength={4}
            value={profileCode}
            onChange={(e) => setProfileCode(e.target.value.toUpperCase())}
          />
        </label>
        <button
          onClick={async () => {
            try {
              setProfileSource(
                JSON.stringify(await api("/profiles/" + profileCode), null, 2),
              );
            } catch (e) {
              setErr((e as Error).message);
            }
          }}
        >
          {copy.text278}
        </button>
      </div>
      <label>
        {copy.text279}
        <textarea
          className="json-editor"
          value={profileSource}
          onChange={(e) => setProfileSource(e.target.value)}
        />
      </label>
      <button
        onClick={() => {
          try {
            action(
              "/owner/profiles/" + profileCode,
              "PUT",
              JSON.parse(profileSource),
            );
          } catch (e) {
            setErr((e as Error).message);
          }
        }}
      >
        {copy.text280}
      </button>
    </section>
  );
}
createRoot(document.getElementById("root")!).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
