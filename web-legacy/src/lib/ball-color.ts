export const BALL_BANDS = [
  { max: 10, className: "",           bg: "#f5c842", fg: "#1d1a17" },
  { max: 20, className: "ball-blue",  bg: "#3a5fa0", fg: "#ffffff" },
  { max: 30, className: "ball-red",   bg: "#c94f24", fg: "#ffffff" },
  { max: 40, className: "ball-gray",  bg: "#7a7068", fg: "#ffffff" },
  { max: 45, className: "ball-green", bg: "#3a7d44", fg: "#ffffff" },
] as const;

export function ballColorClass(n: number): string {
  return (BALL_BANDS.find((b) => n <= b.max) ?? BALL_BANDS[4]).className;
}
