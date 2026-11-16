export type QuizProfile = {
  displayName: string;
  gender: "woman" | "man" | "undisclosed";
};
const prefix = "zomi:quiz-profile:";
const memory = new Map<string, QuizProfile>();

export function readQuizProfile(participantId: string): QuizProfile | null {
  try {
    const value = JSON.parse(
      sessionStorage.getItem(prefix + participantId) || "null",
    );
    if (
      value &&
      typeof value.displayName === "string" &&
      value.displayName.length <= 60 &&
      ["woman", "man", "undisclosed"].includes(value.gender)
    )
      return value;
    memory.delete(participantId);
    return null;
  } catch {
    return memory.get(participantId) || null;
  }
}

export function saveQuizProfile(participantId: string, profile: QuizProfile) {
  memory.set(participantId, profile);
  try {
    sessionStorage.setItem(prefix + participantId, JSON.stringify(profile));
  } catch {
    /* Keep the choice during navigation even if browser storage is unavailable. */
  }
}

export function clearQuizProfiles() {
  memory.clear();
}
