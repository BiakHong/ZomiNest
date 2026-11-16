import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import {
  ArrowRight,
  Check,
  ChevronLeft,
  RefreshCw,
  Share2,
  X,
} from "lucide-react";
import { api, type RecordData } from "../api";
import { QuizArtwork } from "./QuizArtwork";
import {
  readQuizProfile,
  saveQuizProfile,
  type QuizProfile,
} from "../quiz-session";
import "./quiz-flow.css";

export const PERSONALITY_VERSION = "zomi-personality-60-v1";
const instructions =
  "Choose how much you agree with each statement based on how you usually feel or behave. There are no right or wrong answers.";
const labels = [
  "Strongly disagree",
  "Disagree",
  "Slightly disagree",
  "Neutral",
  "Slightly agree",
  "Agree",
  "Strongly agree",
];
type Answers = Record<string, string | number>;
export function StartForm({
  quiz,
  slug,
  defaultName = "",
  participantId = "browser",
}: {
  quiz: RecordData;
  slug: string;
  defaultName?: string;
  participantId?: string;
}) {
  const [profile, setProfile] = useState(() => readQuizProfile(participantId));
  const [name, setName] = useState("");
  const [gender, setGender] = useState<QuizProfile["gender"]>("undisclosed");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [editing, setEditing] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const nameInput = useRef<HTMLInputElement>(null);
  const startButton = useRef<HTMLButtonElement>(null);
  const navigate = useNavigate();
  const personality = quiz.questionnaireVersion === PERSONALITY_VERSION;
  const action = personality ? "Start test" : "Start quiz";

  function openProfile(edit = false) {
    const saved = readQuizProfile(participantId);
    setName(
      saved?.displayName ??
        (defaultName === "Community guest" ? "" : defaultName),
    );
    setGender(saved?.gender || "undisclosed");
    setEditing(edit);
    setError("");
    dialog.current?.showModal();
    nameInput.current?.focus({ preventScroll: true });
  }
  function closeProfile() {
    dialog.current?.close();
    startButton.current?.focus();
  }
  function remember(next: QuizProfile) {
    saveQuizProfile(participantId, next);
    setProfile(next);
  }
  async function start(next: QuizProfile) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      const attempt = await api("/quizzes/" + slug + "/start", "POST", {
        ...next,
        language: "en",
      });
      remember(next);
      closeProfile();
      navigate("/play/" + attempt.id);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  function dismiss() {
    if (busy) return;
    if (editing) closeProfile();
    else {
      const guest: QuizProfile = { displayName: "", gender: "undisclosed" };
      remember(guest);
      closeProfile();
      void start(guest);
    }
  }
  function save() {
    const displayName = name.trim();
    if (
      displayName &&
      (displayName.length > 60 ||
        !/^[\p{L}\p{M}\p{N} .,'’‘ʼʻ\-‐‑–·]+$/u.test(displayName) ||
        !/[\p{L}\p{N}]/u.test(displayName))
    ) {
      setError(
        "Use up to 60 characters with letters, spaces or normal name punctuation.",
      );
      return;
    }
    const next = { displayName, gender };
    if (editing) {
      remember(next);
      closeProfile();
    } else void start(next);
  }
  return (
    <div className="attempt-start">
      {personality && (
        <div className="personality-intro">
          <p>{instructions}</p>
          <p className="muted">
            A self-reflection quiz using Zomi Nest’s proposed scoring system.
            Take it at your own pace.
          </p>
        </div>
      )}
      <button
        ref={startButton}
        disabled={busy}
        onClick={() => {
          const saved = readQuizProfile(participantId);
          if (saved) void start(saved);
          else openProfile();
        }}
      >
        {busy ? "Starting…" : action}
        <ArrowRight size={18} />
      </button>
      {profile && (
        <p className="session-player">
          Playing as <strong>{profile.displayName || "Guest"}</strong>
          <button
            className="session-change"
            onClick={() => openProfile(true)}
            disabled={busy}
          >
            Change
          </button>
        </p>
      )}
      {error && !dialog.current?.open && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      <dialog
        ref={dialog}
        className="player-dialog"
        aria-labelledby="player-dialog-title"
        aria-describedby="player-dialog-intro"
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const controls = Array.from(
            event.currentTarget.querySelectorAll<
              HTMLButtonElement | HTMLInputElement
            >("button:not(:disabled), input:not(:disabled)"),
          );
          const first = controls[0],
            last = controls[controls.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }}
        onCancel={(e) => {
          e.preventDefault();
          dismiss();
        }}
      >
        <button
          type="button"
          className="player-dialog-close"
          aria-label={
            editing ? "Close player details" : "Close and play as guest"
          }
          onClick={dismiss}
          disabled={busy}
        >
          <X size={20} />
        </button>
        <h2 id="player-dialog-title">Na min bang?</h2>
        <p id="player-dialog-intro" className="muted">
          Leave your name if you’d like. We’ll remember your choice for this
          session.
        </p>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            save();
          }}
        >
          <label htmlFor="attempt-name" className="sr-only">
            Na min bang?
          </label>
          <input
            ref={nameInput}
            id="attempt-name"
            name="displayName"
            autoComplete="nickname"
            placeholder="Na min gelh dih aw"
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={60}
            aria-describedby={
              error ? "name-privacy player-error" : "name-privacy"
            }
            disabled={busy}
          />
          <p id="name-privacy" className="field-help">
            Your name will appear on your results. Sharing with the community is
            optional.
          </p>
          <fieldset className="player-gender" aria-describedby="gender-privacy">
            <legend>
              Gender <span className="muted">(optional)</span>
            </legend>
            <label className={gender === "woman" ? "chosen" : ""}>
              <input
                type="radio"
                name="gender"
                value="woman"
                checked={gender === "woman"}
                onChange={() => setGender("woman")}
                disabled={busy}
              />
              Numei
            </label>
            <label className={gender === "man" ? "chosen" : ""}>
              <input
                type="radio"
                name="gender"
                value="man"
                checked={gender === "man"}
                onChange={() => setGender("man")}
                disabled={busy}
              />
              Pasal
            </label>
          </fieldset>
          <p id="gender-privacy" className="field-help">
            Kept private. Gender does not affect your result.
          </p>
          {error && (
            <p id="player-error" className="notice error" role="alert">
              {error}
            </p>
          )}
          <div className="player-dialog-actions">
            <button disabled={busy}>
              {busy ? "Starting…" : editing ? "Save changes" : "Save & start"}
              <ArrowRight size={17} />
            </button>
            {!editing && (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={dismiss}
              >
                Continue as guest
              </button>
            )}
          </div>
        </form>
      </dialog>
    </div>
  );
}

function restore(attempt: RecordData) {
  const personality = attempt.quiz.questionnaireVersion === PERSONALITY_VERSION;
  const pages = personality ? 12 : attempt.quiz.questions.length;
  let saved: RecordData | null = null;
  try {
    saved = JSON.parse(localStorage.getItem("attempt:" + attempt.id) || "null");
  } catch {
    /* Use the server copy. */
  }
  const compatible =
    saved?.edition === attempt.edition &&
    saved?.version === (attempt.quiz.questionnaireVersion || null);
  const answers: Answers = compatible ? saved!.answers : attempt.answers;
  const valid: Answers = {};
  for (const q of attempt.quiz.questions) {
    const value = answers?.[q.id];
    if (
      personality
        ? typeof value === "number" &&
          Number.isInteger(value) &&
          value >= -3 &&
          value <= 3
        : q.options.some((o: RecordData) => o.id === value)
    )
      valid[q.id] = value;
  }
  const page = compatible ? saved!.page : attempt.page;
  return {
    answers: valid,
    page: Number.isInteger(page) && page >= 0 && page < pages ? page : 0,
  };
}

export function ActivityRunner({ attempt: a }: { attempt: RecordData }) {
  const initial = useRef(restore(a));
  const [answers, setAnswers] = useState<Answers>(initial.current.answers);
  const [page, setPage] = useState(initial.current.page);
  const [result, setResult] = useState<RecordData | null>(a.result);
  const [missing, setMissing] = useState<string[]>([]);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(
    "Your progress is saved in this browser.",
  );
  const [busy, setBusy] = useState(false);
  const queue = useRef<Promise<unknown>>(Promise.resolve());
  const title = useRef<HTMLHeadingElement>(null);
  const personality = a.quiz.questionnaireVersion === PERSONALITY_VERSION;
  const size = personality ? 5 : 1;
  const pages = Math.ceil(a.quiz.questions.length / size);
  const questions: RecordData[] = a.quiz.questions.slice(
    page * size,
    (page + 1) * size,
  );
  function cache(next: Answers, nextPage: number) {
    try {
      localStorage.setItem(
        "attempt:" + a.id,
        JSON.stringify({
          edition: a.edition,
          version: a.quiz.questionnaireVersion || null,
          answers: next,
          page: nextPage,
        }),
      );
    } catch {
      /* Server persistence remains available. */
    }
  }
  function persist(next: Answers, nextPage: number) {
    cache(next, nextPage);
    setSaving("Saving progress…");
    const pending = queue.current
      .catch(() => {})
      .then(() =>
        api("/attempts/" + a.id, "PUT", { answers: next, page: nextPage }),
      );
    queue.current = pending;
    pending.then(
      () => setSaving("Progress saved. You can return to this attempt."),
      () =>
        setSaving(
          "Not saved to the server. Your browser copy is kept; use Save progress to retry.",
        ),
    );
    return pending;
  }
  async function move(nextPage: number) {
    if (nextPage > page) {
      const absent = questions
        .filter((q) => answers[q.id] === undefined)
        .map((q) => q.id);
      setMissing(absent);
      if (absent.length) {
        document.getElementById("statement-" + absent[0])?.focus();
        return;
      }
    }
    setBusy(true);
    setError("");
    try {
      await persist(answers, nextPage);
      setPage(nextPage);
      setMissing([]);
      requestAnimationFrame(() => {
        title.current?.focus({ preventScroll: true });
        title.current?.scrollIntoView({ block: "start", behavior: "instant" });
      });
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function finish() {
    const absent = questions
      .filter((q) => answers[q.id] === undefined)
      .map((q) => q.id);
    setMissing(absent);
    if (absent.length) {
      document.getElementById("statement-" + absent[0])?.focus();
      return;
    }
    if (Object.keys(answers).length !== a.quiz.questions.length) {
      setError(
        "Answer every statement before viewing your results. Use Back to review earlier pages.",
      );
      return;
    }
    setBusy(true);
    setError("");
    try {
      await queue.current.catch(() => {});
      const r = await api("/attempts/" + a.id + "/complete", "POST", {
        answers,
      });
      try {
        localStorage.removeItem("attempt:" + a.id);
        sessionStorage.removeItem("attempt:" + a.id);
      } catch {
        /* The server owns the completed result. */
      }
      setResult(r);
      window.scrollTo(0, 0);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (result) return <ActivityResult attempt={a} result={result} />;
  return (
    <section
      className={
        "runner activity-runner" + (personality ? " personality-runner" : "")
      }
    >
      <Link className="text-link" to={"/quizzes/" + a.slug}>
        <ChevronLeft size={17} />
        Quiz details
      </Link>
      <h1 ref={title} tabIndex={-1} className="runner-title">
        {a.quiz.title}
      </h1>
      <p className="muted">
        Taking this as <strong>{a.displayName}</strong> · Private result
      </p>
      <div className="progress-label">
        <span>
          {personality
            ? `Page ${page + 1} of ${pages}`
            : `Question ${page + 1} of ${pages}`}
        </span>
        <span>
          {Object.keys(answers).length} of {a.quiz.questions.length} answered
        </span>
      </div>
      <progress
        max={a.quiz.questions.length}
        value={Object.keys(answers).length}
      />
      {personality && <p className="scale-instructions">{instructions}</p>}
      <div className="statement-list">
        {questions.map((q, i) => (
          <fieldset
            key={q.id}
            id={"statement-" + q.id}
            tabIndex={-1}
            className={personality ? "personality-statement" : "quiz-statement"}
            aria-describedby={
              missing.includes(q.id) ? "missing-" + q.id : undefined
            }
          >
            <legend>
              {personality && (
                <span className="statement-number">
                  {page * size + i + 1}.{" "}
                </span>
              )}
              {q.text}
            </legend>
            {q.credit && <p className="muted">{q.credit}</p>}
            {personality ? (
              <>
                <div className="likert-row">
                  {labels.map((label, n) => (
                    <label className="likert-choice" key={n}>
                      <input
                        type="radio"
                        name={q.id}
                        aria-label={label}
                        value={n - 3}
                        checked={answers[q.id] === n - 3}
                        disabled={busy}
                        onChange={() => {
                          const next = { ...answers, [q.id]: n - 3 };
                          setAnswers(next);
                          setMissing((ids) => ids.filter((id) => id !== q.id));
                          void persist(next, page).catch(() => {});
                        }}
                      />
                      <span
                        className={"likert-circle circle-" + Math.abs(n - 3)}
                        aria-hidden="true"
                      >
                        <Check size={17} />
                      </span>
                    </label>
                  ))}
                </div>
                <div className="likert-labels" aria-hidden="true">
                  <span>Strongly disagree</span>
                  <span>Neutral</span>
                  <span>Strongly agree</span>
                </div>
              </>
            ) : (
              <div className="options">
                {q.options.map((o: RecordData, n: number) => (
                  <label
                    className={
                      "option " + (answers[q.id] === o.id ? "selected" : "")
                    }
                    key={o.id}
                  >
                    <input
                      type="radio"
                      name={q.id}
                      checked={answers[q.id] === o.id}
                      disabled={busy}
                      onChange={() => {
                        const next = { ...answers, [q.id]: o.id };
                        setAnswers(next);
                        setMissing([]);
                        void persist(next, page).catch(() => {});
                      }}
                    />
                    <span className="option-letter">
                      {a.quiz.type === "PERSONALITY"
                        ? n + 1
                        : String.fromCharCode(65 + n)}
                    </span>
                    <span>{o.text}</span>
                    {answers[q.id] === o.id && <Check size={20} />}
                  </label>
                ))}
              </div>
            )}
            {missing.includes(q.id) && (
              <p className="answer-required" id={"missing-" + q.id}>
                Choose one answer for this statement before continuing.
              </p>
            )}
          </fieldset>
        ))}
      </div>
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
      <div className="runner-controls">
        <button
          className="secondary"
          disabled={page === 0 || busy}
          onClick={() => move(page - 1)}
        >
          <ChevronLeft size={17} />
          Back
        </button>
        {page < pages - 1 ? (
          <button disabled={busy} onClick={() => move(page + 1)}>
            Next
            <ArrowRight size={17} />
          </button>
        ) : (
          <button disabled={busy} onClick={finish}>
            {busy ? "Preparing results…" : "See my results"}
            <ArrowRight size={17} />
          </button>
        )}
      </div>
      <div className="save-progress">
        <p className="muted" role="status">
          {saving}
        </p>
        <button
          className="text-link"
          disabled={busy}
          onClick={() => {
            void persist(answers, page).catch((e) => setError(e.message));
          }}
        >
          Save progress
        </button>
        <Link to="/results">My attempts</Link>
      </div>
    </section>
  );
}

export function ResultAvatar({ seed, name }: { seed: string; name: string }) {
  const colors = [
    ["#f4e9da", "#704f32"],
    ["#e1ebe6", "#305743"],
    ["#e5e8f4", "#48568b"],
    ["#f3e3e6", "#813f54"],
    ["#e0edf0", "#35616d"],
    ["#ece4f0", "#704779"],
  ];
  const index =
    Array.from(seed || name).reduce((n, c) => n + c.codePointAt(0)!, 0) %
    colors.length;
  return (
    <span
      className="result-avatar"
      style={{ background: colors[index][0], color: colors[index][1] }}
      aria-hidden="true"
    >
      <svg viewBox="0 0 48 48" fill="none">
        <circle cx="24" cy="18" r="8" fill="currentColor" />
        <path d="M9 43v-4c0-9 6-14 15-14s15 5 15 14v4" fill="currentColor" />
        <circle cx="21" cy="18" r="1.25" fill="white" />
        <circle cx="27" cy="18" r="1.25" fill="white" />
        <path
          d="M21 22q3 2 6 0"
          stroke="white"
          strokeWidth="1.5"
          strokeLinecap="round"
        />
      </svg>
    </span>
  );
}

export function ActivityResult({
  attempt: a,
  result: r,
}: {
  attempt: RecordData;
  result: RecordData;
}) {
  const name = a.displayName || r.displayName || "Guest";
  const [message, setMessage] = useState("");
  const personality = r.questionnaireVersion === PERSONALITY_VERSION;
  const outcome =
    r.type === "KNOWLEDGE"
      ? `${r.score} / ${r.maximum}`
      : r.type === "OUTCOME"
        ? r.outcome.name
        : personality
          ? r.balanced
            ? "Balanced profile"
            : r.code
          : r.profile?.name || r.code;
  return (
    <section className="result activity-result">
      <div className="personal-result-heading">
        <ResultAvatar seed={a.avatar} name={name} />
        <h1>
          {name}’s{" "}
          {r.type === "PERSONALITY" ? "personality result" : "quiz result"}
        </h1>
      </div>
      <div className="result-summary">
        <div>
          <h2 className="result-outcome">{outcome}</h2>
          <p className="lead">
            {personality
              ? r.description
              : r.type === "KNOWLEDGE"
                ? "Your answers, your discoveries. Review what you learned below."
                : r.type === "OUTCOME"
                  ? r.outcome.description
                  : r.profile?.overview}
          </p>
        </div>
        <QuizArtwork type={r.type} />
      </div>
      {personality && (
        <div className="personality-dimensions">
          {r.dimensions.map((d: RecordData) => (
            <article className="personality-dimension" key={d.id}>
              <h3>{d.name}</h3>
              <div className="trait-poles">
                <span>
                  <strong>{d.displayPositive}%</strong> {d.positivePole}
                </span>
                <span>
                  <strong>{d.displayNegative}%</strong> {d.negativePole}
                </span>
              </div>
              <meter
                min="0"
                max="100"
                value={d.positivePercentage}
                aria-label={`${d.name}: ${d.displayPositive}% ${d.positivePole}, ${d.displayNegative}% ${d.negativePole}`}
              />
              <p className="trait-strength">
                {d.strength}
                {d.closeToMidpoint ? " · close to the midpoint" : ""}
              </p>
              <p>{d.explanation}</p>
            </article>
          ))}
          <p className="result-method">
            These percentages describe the direction and strength of your
            responses on this self-reflection quiz. They are not accuracy
            scores, probabilities, population rankings, or statistical
            confidence. Preferences can change with context and time.
          </p>
        </div>
      )}
      {!personality && r.traits && (
        <div className="traits">
          {r.traits.map((t: RecordData) => (
            <div key={t.name}>
              <div className="split">
                <strong>{t.name}</strong>
                <span>
                  {Math.round(t.value)}% {t.balanced && "· Balanced"}
                </span>
              </div>
              <meter min="0" max="100" value={t.value} />
            </div>
          ))}
        </div>
      )}
      {!personality && r.profile && (
        <Link className="text-link" to={"/profiles/" + r.code}>
          Read this profile
          <ArrowRight size={17} />
        </Link>
      )}
      <div className="actions">
        <Link
          className="button"
          to={
            "/quizzes/" +
            (a.slug === "community-rhythm" ? "discover-personality" : a.slug)
          }
        >
          {a.quiz?.randomQuestions
            ? "Play another round"
            : `Retake ${r.type === "PERSONALITY" ? "test" : "quiz"}`}
          <ArrowRight size={17} />
        </Link>
        <Link to="/results">My results</Link>
        <button
          className="secondary"
          onClick={async () => {
            try {
              await navigator.clipboard.writeText(
                location.origin + "/quizzes/" + a.slug,
              );
              setMessage(
                "Quiz link copied. Your personal result stays private unless you share it below.",
              );
            } catch {
              setMessage(
                "Quiz link: " + location.origin + "/quizzes/" + a.slug,
              );
            }
          }}
        >
          <Share2 size={17} />
          Copy quiz link
        </button>
      </div>
      {message && (
        <p className="notice" role="status">
          {message}
        </p>
      )}
      <CommunityResults attempt={a} result={r} />
      {r.explanations && (
        <div className="answer-review">
          <h2>Your answers, explained</h2>
          {r.explanations.map((e: RecordData, i: number) => (
            <article className="explanation" key={i}>
              <span className="tag">
                {e.correct ? "Correct" : "Keep learning"}
              </span>
              <h3>
                {i + 1}. {e.question}
              </h3>
              <p>Your answer: {e.answer}</p>
              {!e.correct && <p>Correct answer: {e.correctAnswer}</p>}
              <p>{e.explanation}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

function CommunityResults({
  attempt: a,
  result: r,
}: {
  attempt: RecordData;
  result: RecordData;
}) {
  const [shared, setShared] = useState(Boolean(a.shared));
  const [consent, setConsent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [status, setStatus] = useState("");
  const [filter, setFilter] = useState("all");
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<RecordData | null>(null);
  const [loadError, setLoadError] = useState("");
  const personality = r.questionnaireVersion === PERSONALITY_VERSION;
  const main = r.code?.split("-")[0] || "";
  const resolved = /^[EI][NS][TF][JP]$/.test(main);
  useEffect(() => {
    let live = true;
    setData(null);
    setLoadError("");
    api(
      `/editions/${a.edition}/community?page=${page}${filter === "same" && resolved ? "&personality=" + main : ""}`,
    ).then(
      (d) => {
        if (live) setData(d);
      },
      (e) => {
        if (live) setLoadError(e.message);
      },
    );
    return () => {
      live = false;
    };
  }, [a.edition, page, filter, revision, main, resolved]);
  async function changeSharing(next: boolean) {
    if (next && !consent) return;
    setBusy(true);
    setError("");
    try {
      const response = await api("/attempts/" + a.id + "/sharing", "PUT", {
        shared: next,
      });
      setShared(response.shared);
      setConsent(false);
      setPage(0);
      setRevision((v) => v + 1);
      setStatus(
        next
          ? "Your display name and result are now shared with the community."
          : "Your result is private and has been removed from community listings.",
      );
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="community-results">
      <div className="result-sharing">
        <h2>{shared ? "You shared this result" : "Your result is private"}</h2>
        {shared ? (
          <>
            <p>
              Other quiz takers can see your display name and result. You can
              make it private at any time.
            </p>
            <button
              className="secondary"
              disabled={busy}
              onClick={() => changeSharing(false)}
            >
              {busy ? "Updating…" : "Make private"}
            </button>
          </>
        ) : (
          <>
            <label className="check">
              <input
                type="checkbox"
                checked={consent}
                onChange={(e) => setConsent(e.target.checked)}
                disabled={busy}
                aria-describedby="sharing-explanation"
              />
              Na min midangte muh ding na phal dia.
            </label>
            <p id="sharing-explanation" className="field-help">
              Other quiz takers will be able to see your display name and
              result.
            </p>
            <button
              disabled={!consent || busy}
              onClick={() => changeSharing(true)}
            >
              <Share2 size={17} />
              {busy ? "Sharing…" : "Share my result"}
            </button>
          </>
        )}
        {error && (
          <p className="notice error" role="alert">
            {error}
          </p>
        )}
        {status && (
          <p className="sharing-status" role="status">
            {status}
          </p>
        )}
      </div>
      <div className="community-heading">
        <h2>Community results</h2>
        <button
          className="text-link"
          onClick={() => setRevision((v) => v + 1)}
          aria-label="Refresh community results"
        >
          <RefreshCw size={16} />
          Refresh
        </button>
      </div>
      <p className="muted">
        Only results people chose to share, from this quiz edition.
      </p>
      {personality && (
        <>
          <div
            className="community-filters"
            role="group"
            aria-label="Community result filters"
          >
            <button
              className={filter === "all" ? "selected" : "secondary"}
              aria-pressed={filter === "all"}
              onClick={() => {
                setFilter("all");
                setPage(0);
              }}
            >
              All shared results
            </button>
            <button
              className={filter === "same" ? "selected" : "secondary"}
              aria-pressed={filter === "same"}
              disabled={!resolved}
              onClick={() => {
                setFilter("same");
                setPage(0);
              }}
            >
              Same personality as me
            </button>
          </div>
          {!resolved && (
            <p className="field-help">
              Your profile has balanced main dimensions. It is shown among all
              shared results; matching is available when all four main letters
              are resolved.
            </p>
          )}
        </>
      )}
      {loadError ? (
        <p className="notice error" role="alert">
          {loadError} Use Refresh to try again.
        </p>
      ) : !data ? (
        <p role="status">Loading shared results…</p>
      ) : data.entries.length ? (
        <>
          <ul className="community-list">
            {data.entries.map((entry: RecordData) => (
              <li key={entry.id}>
                <ResultAvatar seed={entry.id} name={entry.name} />
                <div>
                  <strong>{entry.name}</strong>
                  {filter === "same" && <small>Same personality</small>}
                </div>
                <span className="community-outcome">
                  {entry.type === "KNOWLEDGE"
                    ? `${entry.score} / ${entry.maximum}`
                    : entry.type === "OUTCOME"
                      ? entry.outcome
                      : entry.code === "XXXX-X"
                        ? "Balanced profile"
                        : entry.code}
                </span>
              </li>
            ))}
          </ul>
          {data.total > 20 && (
            <nav
              className="community-pagination"
              aria-label="Community result pages"
            >
              <button
                className="secondary"
                disabled={page === 0}
                onClick={() => setPage((v) => v - 1)}
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {Math.ceil(data.total / 20)}
              </span>
              <button
                className="secondary"
                disabled={(page + 1) * 20 >= data.total}
                onClick={() => setPage((v) => v + 1)}
              >
                Next results
              </button>
            </nav>
          )}
        </>
      ) : (
        <div className="community-empty">
          <h3>
            {filter === "same"
              ? "No matches shared yet."
              : "A little quiet here, for now."}
          </h3>
          <p>
            {filter === "same"
              ? "No one with your four main personality letters has shared a result in this edition yet. You can still explore all shared results."
              : "No one has shared a result in this edition yet. Sharing yours is always your choice."}
          </p>
        </div>
      )}
    </div>
  );
}
