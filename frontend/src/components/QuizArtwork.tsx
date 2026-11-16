/** Original decorative artwork for activity types; never photos or claims about real people. */
export function QuizArtwork({ type }: { type: string }) {
  return (
    <div
      className={"quiz-thumbnail thumb-" + type.toLowerCase()}
      aria-hidden="true"
    >
      {type === "KNOWLEDGE" ? (
        <svg viewBox="0 0 160 144" fill="none">
          <path
            d="M80 45C60 31 39 32 22 39v63c19-7 39-5 58 7 19-12 39-14 58-7V39c-17-7-38-8-58 6Z"
            fill="var(--art-paper)"
            stroke="var(--art-ink)"
            strokeWidth="3"
            strokeLinejoin="round"
          />
          <path
            d="M80 45v64M34 51c12-2 21-1 33 5M34 63c12-2 21-1 33 5M34 75c10-2 19-1 29 3M94 56c10-5 20-7 31-5M94 68c10-5 20-7 31-5M94 80c9-4 18-5 27-5"
            stroke="var(--art-secondary)"
            strokeWidth="3"
            strokeLinecap="round"
          />
          <path
            d="M122 17v12m-6-6h12M36 14l3 8 8 3-8 3-3 8-3-8-8-3 8-3 3-8Z"
            stroke="var(--art-surface)"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
          <path
            d="M43 124h75"
            stroke="var(--art-surface)"
            strokeWidth="3"
            strokeLinecap="round"
          />
          <path
            d="M60 132h45"
            stroke="var(--art-ink)"
            strokeWidth="3"
            strokeLinecap="round"
          />
        </svg>
      ) : type === "OUTCOME" ? (
        <svg viewBox="0 0 160 144" fill="none">
          <path
            d="m28 27 34-9 32 30-9 57-35 14-26-27 4-65Z"
            fill="var(--art-surface)"
            stroke="var(--art-detail)"
            strokeWidth="2.5"
            strokeLinejoin="round"
            transform="rotate(-6 60 70)"
          />
          <path
            d="M84 24c35-5 57 17 57 46s-18 51-43 46-32-27-29-47S68 29 84 24Z"
            fill="var(--art-paper)"
            stroke="var(--art-detail)"
            strokeWidth="2.5"
          />
          <path
            d="M39 76q11 12 22-1M89 78q14 17 29-2"
            stroke="var(--art-ink)"
            strokeWidth="3"
            strokeLinecap="round"
          />
          <g fill="var(--art-detail)">
            <circle cx="41" cy="60" r="2.7" />
            <circle cx="58" cy="58" r="2.7" />
            <circle cx="91" cy="61" r="3" />
            <circle cx="113" cy="59" r="3" />
          </g>
          <path
            d="m127 12 3 8 8 3-8 3-3 8-3-8-8-3 8-3 3-8Z"
            fill="var(--art-surface)"
          />
          <path
            d="M59 129h55"
            stroke="var(--art-surface)"
            strokeWidth="3"
            strokeLinecap="round"
          />
        </svg>
      ) : (
        <svg viewBox="0 0 160 144" fill="none">
          <path
            d="M80 61C29 8 8 40 62 78 8 106 42 139 80 89c34 50 70 18 18-11 54-37 27-70-18-17Z"
            fill="var(--art-ink)"
          />
          <path
            d="M80 61C29 8 8 40 62 78L80 75V61Z"
            fill="var(--art-secondary)"
          />
          <path
            d="M80 89c34 50 70 18 18-11L80 78v11Z"
            fill="var(--art-detail)"
          />
          <circle cx="80" cy="77" r="17" fill="var(--art-paper)" />
          <path
            d="m112 19 3 7 8 3-8 3-3 8-3-8-7-3 7-3 3-7Z"
            stroke="var(--art-secondary)"
            strokeWidth="2.5"
          />
          <path
            d="M26 117h17m-8-8v17"
            stroke="var(--art-ink)"
            strokeWidth="2.5"
            strokeLinecap="round"
          />
        </svg>
      )}
    </div>
  );
}
